"""Supervisor del ambiente de PRUEBAS en un solo nodo (ADR-10). Solo biblioteca estándar.

Levanta cada microservicio como un PROCESO APARTE dentro del mismo contenedor:

    gateway        $PORT (público)          obras          127.0.0.1:8001
    catalogo       127.0.0.1:8002           compras        127.0.0.1:8003
    tareo          127.0.0.1:8004           almacen        127.0.0.1:8005
    costo          127.0.0.1:8006           integraciones  127.0.0.1:8007
    notificaciones 127.0.0.1:8008 (extra)   relevo         sin puerto (extra)

La arquitectura no cambia: cada servicio sigue siendo su propio proceso, con su usuario y su esquema de base.
Cada uno recibe SOLO su cadena de conexión (SIGRO_DB_OBRAS → SIGRO_APP_DATABASE_URL de obras, y así), nunca la de
otro; el gateway e integraciones no reciben ninguna cadena ni el secreto JWT.

**Memoria (512 MB en Render gratis).** Diez intérpretes con FastAPI, pydantic y SQLAlchemy cargados por separado no
caben: medido en CI, el núcleo mataba procesos al arrancar (código -9). Por eso, en Linux, el supervisor carga UNA vez
las bibliotecas comunes del venv de la API y crea cada servicio con fork() (como `gunicorn --preload`): cada servicio
sigue siendo un proceso aparte, con su PID, su entorno, su conexión y sus fallos, pero comparte con los demás las
páginas de esas bibliotecas (copia al escribir). El código de cada servicio (`app.*`) se importa recién en el hijo.
Integraciones y notificaciones fijan otras versiones de FastAPI: se lanzan con su propio intérprete (fork + exec).
En Windows (prueba local sin Docker) no hay fork: todos se lanzan como subproceso, sin precarga.

- Si un proceso se cae, se reinicia con espera creciente (1, 2, 4 … 30 s); si llevaba 60 s arriba, la espera vuelve a 1 s.
- Si falta la variable de base de un servicio, ese servicio no arranca (el gateway lo mostrará «no_responde») y el
  resto sigue: un olvido en el panel se ve en /salud/servicios y no tumba todo el ambiente.
- SIGTERM (Render al redesplegar o dormir) se reenvía a todos y se espera hasta 10 s antes de matar.
- Cada línea de log sale con el nombre del proceso: «[obras] …».
- Relevo y notificaciones son «extras»: se levantan si SIGRO_UN_NODO_EXTRAS=1.

    python /app/infra/un_nodo/supervisor.py
"""
from __future__ import annotations

import os
import selectors
import signal
import subprocess
import sys
import threading
import time
from dataclasses import dataclass, field

API = os.environ.get("SIGRO_UN_NODO_API", "/app/api")
VENV_API = os.environ.get("SIGRO_UN_NODO_VENV_API", "/opt/venv-api")
VENV_INTEGRACIONES = os.environ.get("SIGRO_UN_NODO_VENV_INTEGRACIONES", "/opt/venv-integraciones")
VENV_NOTIFICACIONES = os.environ.get("SIGRO_UN_NODO_VENV_NOTIFICACIONES", "/opt/venv-notificaciones")
RAIZ = os.environ.get("SIGRO_UN_NODO_RAIZ", "/app")

# servicio → puerto interno. El orden es el de ADR-10 y el de las variables SIGRO_DB_*.
PUERTOS = {"obras": 8001, "catalogo": 8002, "compras": 8003, "tareo": 8004,
           "almacen": 8005, "costo": 8006, "integraciones": 8007, "notificaciones": 8008}
CON_BASE = ("obras", "catalogo", "compras", "tareo", "almacen", "costo")

# Variables que NO pasan a un proceso que no es su dueño.
_DE_BASE = tuple(f"SIGRO_DB_{s.upper()}" for s in (*CON_BASE, "relevo")) + ("SIGRO_APP_DATABASE_URL", "SIGRO_DATABASE_URL")
_SECRETOS_API = ("SIGRO_JWT_SECRET", "FACTILIZA_TOKEN")
_MINIMAS = ("PATH", "HOME", "LANG", "LC_ALL", "TZ", "PYTHONUNBUFFERED", "PYTHONDONTWRITEBYTECODE", "SSL_CERT_FILE",
            "MALLOC_ARENA_MAX", "SYSTEMROOT", "TEMP", "TMP", "PYTHONIOENCODING")  # Windows: sin SYSTEMROOT no hay red

# Bibliotecas que comparten todos los procesos del venv de la API. Solo de terceros: nada de `app.*`, que lee el
# entorno y es de cada servicio. Si una no está, se sigue sin ella (la precarga es una optimización, no un requisito).
PRECARGA = ("fastapi", "fastapi.routing", "fastapi.responses", "starlette.applications", "starlette.middleware.cors",
            "pydantic", "email_validator", "sqlalchemy", "sqlalchemy.orm", "sqlalchemy.dialects.postgresql.psycopg",
            "psycopg", "httpx", "uvicorn", "uvicorn.config", "uvicorn.server", "uvicorn.protocols.http.h11_impl",
            "uvicorn.lifespan.on", "uvicorn.loops.asyncio", "jwt", "argon2", "multipart", "anyio", "h11")

ESPERA_MAX_S = 30.0
ESTABLE_S = 60.0


@dataclass
class Proceso:
    nombre: str
    comando: list[str]                                # para lanzarlo como subproceso (otro venv, o Windows)
    cwd: str
    entorno: dict[str, str] = field(default_factory=dict)
    falta: str | None = None                          # variable obligatoria ausente: no se arranca
    objetivo: tuple[str, ...] | None = None           # venv de la API: ("uvicorn", "mod:app", host, puerto) o ("relevo",)


def urls_internas() -> dict[str, str]:
    """Cómo se encuentran entre sí: siempre por 127.0.0.1, sin Cloudflare ni TLS."""
    u = {f"SIGRO_URL_{s.upper()}": f"http://127.0.0.1:{p}" for s, p in PUERTOS.items() if s in CON_BASE}
    u["SIGRO_INTEGRACIONES_URL"] = f"http://127.0.0.1:{PUERTOS['integraciones']}"
    u["SIGRO_URL_INTEGRACIONES"] = u["SIGRO_INTEGRACIONES_URL"]
    u["SIGRO_NOTIFICACIONES_URL"] = f"http://127.0.0.1:{PUERTOS['notificaciones']}"
    return u


def _sin_ajenas(entorno: dict[str, str]) -> dict[str, str]:
    return {k: v for k, v in entorno.items() if k not in _DE_BASE and not k.startswith("SIGRO_DB_")}


def _python(venv: str) -> str:
    """En el contenedor, <venv>/bin/python; en Windows (prueba local sin Docker), <venv>/Scripts/python.exe."""
    return f"{venv}/Scripts/python.exe" if os.name == "nt" else f"{venv}/bin/python"


def _uvicorn(venv: str, modulo: str, puerto: int | str, host: str = "127.0.0.1") -> list[str]:
    return [_python(venv), "-m", "uvicorn", modulo, "--host", host, "--port", str(puerto),
            "--no-access-log", "--timeout-graceful-shutdown", "5"]


def procesos(entorno: dict[str, str]) -> list[Proceso]:
    """Arma la lista de procesos y el entorno de cada uno. Función pura: se prueba sin contenedor."""
    comun = {**_sin_ajenas(entorno), **urls_internas()}
    extras = entorno.get("SIGRO_UN_NODO_EXTRAS", "0") in ("1", "si", "sí", "true")
    puerto_gw = entorno.get("PORT", "10000")
    lista = [Proceso("gateway", _uvicorn(VENV_API, "app.servicios.gateway:app", puerto_gw, "0.0.0.0"), API,
                     {k: v for k, v in comun.items() if k not in _SECRETOS_API} | {"SIGRO_SERVICIO": "gateway"},
                     objetivo=("uvicorn", "app.servicios.gateway:app", "0.0.0.0", str(puerto_gw)))]
    for s in CON_BASE:
        variable = f"SIGRO_DB_{s.upper()}"
        p = Proceso(s, _uvicorn(VENV_API, f"app.servicios.{s}:app", PUERTOS[s]), API,
                    {k: v for k, v in comun.items() if k != "FACTILIZA_TOKEN"} | {"SIGRO_SERVICIO": s},
                    objetivo=("uvicorn", f"app.servicios.{s}:app", "127.0.0.1", str(PUERTOS[s])))
        if entorno.get(variable):
            p.entorno["SIGRO_APP_DATABASE_URL"] = entorno[variable]
        else:
            p.falta = variable
        lista.append(p)

    minimas = {k: entorno[k] for k in _MINIMAS if k in entorno}
    token = entorno.get("SIGRO_SERVICIO_TOKEN", "")
    integ = {**minimas, "SERVICIO_TOKEN": entorno.get("SERVICIO_TOKEN") or token}
    if entorno.get("FACTILIZA_TOKEN"):
        integ["FACTILIZA_TOKEN"] = entorno["FACTILIZA_TOKEN"]
    lista.append(Proceso("integraciones", _uvicorn(VENV_INTEGRACIONES, "main:app", PUERTOS["integraciones"]),
                         f"{RAIZ}/integraciones", integ))

    if extras:
        lista.append(Proceso("notificaciones", _uvicorn(VENV_NOTIFICACIONES, "main:app", PUERTOS["notificaciones"]),
                             f"{RAIZ}/notificaciones",
                             {**minimas, "NOTIF_TOKEN": token,
                              "NOTIF_DB": entorno.get("NOTIF_DB", "/tmp/notificaciones.db")}))  # efímero en Render gratis
        # app.config exige SIGRO_JWT_SECRET fuera de desarrollo aunque el relevo no firme ni valide sesiones
        relevo = Proceso("relevo", [_python(VENV_API), "-m", "app.relay"], API,
                         {k: v for k, v in comun.items() if k != "FACTILIZA_TOKEN"} | {"SIGRO_SERVICIO": "relevo"},
                         objetivo=("relevo",))
        if entorno.get("SIGRO_DB_RELEVO"):
            relevo.entorno["SIGRO_APP_DATABASE_URL"] = entorno["SIGRO_DB_RELEVO"]
        else:
            relevo.falta = "SIGRO_DB_RELEVO"
        lista.append(relevo)
    return lista


def siguiente_espera(actual: float, duro_s: float) -> float:
    """Espera antes de reiniciar: se duplica en cada caída seguida; si el proceso aguantó 60 s, vuelve a 1 s."""
    if duro_s >= ESTABLE_S:
        return 1.0
    return min(max(actual * 2, 1.0), ESPERA_MAX_S)


# ── ejecución ───────────────────────────────────────────────────────────────────────────────────────────────────

_salida = threading.Lock()


def _log(nombre: str, texto: str) -> None:
    with _salida:
        sys.stdout.write(f"[{nombre}] {texto}\n")
        sys.stdout.flush()


def precargar() -> int:
    """Importa una vez las bibliotecas comunes y congela el recolector: los hijos las comparten sin copiarlas."""
    import gc
    import importlib

    cargadas = 0
    for modulo in PRECARGA:
        try:
            importlib.import_module(modulo)
            cargadas += 1
        except ImportError:
            pass
    gc.collect()
    gc.freeze()          # objetos ya creados fuera del recolector: el GC del hijo no los toca ni copia sus páginas
    return cargadas


def _correr_en_hijo(p: Proceso) -> int:
    """Lo que hace el proceso hijo tras fork(): su entorno, su carpeta y su servicio. Nunca vuelve al supervisor."""
    for s in (signal.SIGTERM, signal.SIGINT, signal.SIGCHLD):
        signal.signal(s, signal.SIG_DFL)
    try:                                                # en `top` y en `ps -o comm` se ve «sigro-obras», no «python»
        with open("/proc/self/comm", "w") as comm:
            comm.write(f"sigro-{p.nombre}"[:15])
    except OSError:
        pass
    os.environ.clear()
    os.environ.update(p.entorno)
    os.chdir(p.cwd)
    if p.cwd not in sys.path:
        sys.path.insert(0, p.cwd)
    sys.argv = [p.nombre]
    if p.objetivo[0] == "relevo":
        from app.relay import main as relevo  # noqa: PLC0415 — se importa en el hijo, con su entorno
        return int(relevo([]) or 0)
    import uvicorn  # noqa: PLC0415 — ya precargado
    _, app, host, puerto = p.objetivo
    uvicorn.run(app, host=host, port=int(puerto), access_log=False, timeout_graceful_shutdown=5)
    return 0


@dataclass
class _Vivo:
    proceso: Proceso
    pid: int
    inicio: float
    popen: subprocess.Popen | None = None


class Supervisor:
    def __init__(self, lista: list[Proceso], con_fork: bool) -> None:
        self.lista = lista
        self.con_fork = con_fork
        self.vivos: dict[int, _Vivo] = {}
        self.espera: dict[str, float] = {p.nombre: 0.0 for p in lista}
        self.pendientes: dict[str, float] = {}       # nombre → cuándo volver a lanzarlo
        self.parando = False
        # Con fork no hay hilos: los logs se leen con selectors en el bucle principal (un fork con hilos vivos puede
        # dejar un candado tomado en el hijo). Sin fork (Windows), un hilo lector por proceso.
        self.selector = selectors.DefaultSelector() if con_fork else None
        self.restos: dict[int, bytes] = {}

    # -- lanzar ---------------------------------------------------------------------------------------------------
    def _leer_listos(self, espera: float) -> None:
        """Bucle principal con fork: espera hasta `espera` s a que algún hijo escriba y reparte sus líneas."""
        if not self.selector.get_map():
            time.sleep(espera)
            return
        for clave, _ in self.selector.select(espera):
            fd, nombre = clave.fd, clave.data
            try:
                bloque = os.read(fd, 65536)
            except OSError:
                bloque = b""
            datos = self.restos.pop(fd, b"") + bloque
            if not bloque:                                 # el hijo cerró su salida (terminó)
                self.selector.unregister(fd)
                os.close(fd)
                if datos:
                    _log(nombre, datos.decode(errors="replace").rstrip())
                continue
            *lineas, resto = datos.split(b"\n")
            for linea in lineas:
                _log(nombre, linea.decode(errors="replace").rstrip())
            if resto:
                self.restos[fd] = resto

    def _lector(self, nombre: str, fd: int) -> None:
        with os.fdopen(fd, "rb", buffering=0) as flujo:
            pendiente = b""
            while bloque := flujo.read(65536):
                pendiente += bloque
                *lineas, pendiente = pendiente.split(b"\n")
                for linea in lineas:
                    _log(nombre, linea.decode(errors="replace").rstrip())
            if pendiente:
                _log(nombre, pendiente.decode(errors="replace").rstrip())

    def lanzar(self, p: Proceso) -> None:
        if self.con_fork and p.objetivo is not None:
            leer, escribir = os.pipe()
            pid = os.fork()
            if pid == 0:                                 # hijo
                os.close(leer)
                for ajeno in list(self.selector.get_map()):   # las salidas de los demás no son suyas
                    os.close(ajeno)
                os.dup2(escribir, 1)
                os.dup2(escribir, 2)
                os.close(escribir)
                codigo = 1
                try:
                    codigo = _correr_en_hijo(p)
                except SystemExit as e:
                    codigo = e.code if isinstance(e.code, int) else 1
                except BaseException:                    # noqa: BLE001 — se informa y el supervisor lo reinicia
                    import traceback
                    traceback.print_exc()
                finally:
                    sys.stdout.flush()
                    sys.stderr.flush()
                    os._exit(codigo)
            os.close(escribir)
            vivo = _Vivo(p, pid, time.monotonic())
        else:
            hijo = subprocess.Popen(p.comando, cwd=p.cwd, env=p.entorno, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT)
            leer = os.dup(hijo.stdout.fileno())
            hijo.stdout.close()
            vivo = _Vivo(p, hijo.pid, time.monotonic(), hijo)
        self.vivos[vivo.pid] = vivo
        if self.selector is not None:
            os.set_blocking(leer, False)
            self.selector.register(leer, selectors.EVENT_READ, p.nombre)
        else:
            threading.Thread(target=self._lector, args=(p.nombre, leer), daemon=True).start()
        _log("supervisor", f"{p.nombre} arrancó (pid {vivo.pid}{', fork' if vivo.popen is None else ''})")

    # -- vigilar --------------------------------------------------------------------------------------------------
    def _terminados(self) -> list[tuple[_Vivo, int]]:
        listos = []
        for pid, vivo in list(self.vivos.items()):
            if vivo.popen is not None:
                codigo = vivo.popen.poll()
                if codigo is None:
                    continue
            else:
                try:
                    hecho, estado = os.waitpid(pid, os.WNOHANG)
                except ChildProcessError:
                    hecho, estado = pid, 0
                if hecho == 0:
                    continue
                codigo = os.waitstatus_to_exitcode(estado)
            del self.vivos[pid]
            listos.append((vivo, codigo))
        return listos

    def parar(self, *_: object) -> None:
        self.parando = True

    def _parar_todo(self) -> None:
        _log("supervisor", "parando todo")
        for vivo in self.vivos.values():
            try:
                os.kill(vivo.pid, signal.SIGTERM)
            except OSError:
                pass
        limite = time.monotonic() + 10
        while self.vivos and time.monotonic() < limite:
            self._terminados()
            if self.selector is not None:
                self._leer_listos(0.2)
            else:
                time.sleep(0.2)
        for vivo in self.vivos.values():
            try:
                os.kill(vivo.pid, signal.SIGKILL if hasattr(signal, "SIGKILL") else signal.SIGTERM)
            except OSError:
                pass

    def correr(self) -> None:
        for s in (signal.SIGTERM, signal.SIGINT):
            try:
                signal.signal(s, self.parar)
            except (ValueError, OSError):
                pass
        por_lanzar = [p for p in self.lista if not p.falta]
        for p in self.lista:
            if p.falta:
                _log("supervisor", f"{p.nombre} NO se arranca: falta {p.falta} (el gateway lo verá «no_responde»)")
        for i, p in enumerate(por_lanzar):
            self.lanzar(p)
            pausa = 0.5 if i == 0 else 0.2              # el gateway primero (Render busca $PORT); el resto escalonado
            if self.selector is not None:
                self._leer_listos(pausa)
            else:
                time.sleep(pausa)
        while not self.parando:
            for vivo, codigo in self._terminados():
                if self.parando:
                    break
                nombre = vivo.proceso.nombre
                duro = time.monotonic() - vivo.inicio
                self.espera[nombre] = siguiente_espera(self.espera[nombre], duro)
                self.pendientes[nombre] = time.monotonic() + self.espera[nombre]
                _log("supervisor", f"{nombre} terminó con código {codigo} tras {duro:.0f} s; "
                                   f"se reinicia en {self.espera[nombre]:.0f} s")
            ahora = time.monotonic()
            for nombre, cuando in list(self.pendientes.items()):
                if ahora >= cuando and not self.parando:
                    del self.pendientes[nombre]
                    self.lanzar(next(p for p in self.lista if p.nombre == nombre))
            if self.selector is not None:
                self._leer_listos(0.2)
            else:
                time.sleep(0.2)
        self._parar_todo()


def main() -> int:
    lista = procesos(dict(os.environ))
    con_fork = hasattr(os, "fork") and os.environ.get("SIGRO_UN_NODO_FORK", "1") != "0"
    _log("supervisor", "procesos: " + ", ".join(p.nombre + (" (sin base)" if p.falta else "") for p in lista))
    if con_fork:
        n = precargar()
        _log("supervisor", f"precarga: {n}/{len(PRECARGA)} bibliotecas comunes; los servicios de la API nacen con fork()")
    else:
        _log("supervisor", "sin fork (Windows o SIGRO_UN_NODO_FORK=0): cada servicio con su propio intérprete")
    Supervisor(lista, con_fork).correr()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
