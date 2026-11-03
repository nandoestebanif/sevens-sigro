"""Supervisor del ambiente de PRUEBAS en un solo nodo (ADR-10). Solo biblioteca estándar.

Levanta cada microservicio como un PROCESO APARTE dentro del mismo contenedor:

    gateway        $PORT (público)          obras          127.0.0.1:8001
    catalogo       127.0.0.1:8002           compras        127.0.0.1:8003
    tareo          127.0.0.1:8004           almacen        127.0.0.1:8005
    costo          127.0.0.1:8006           integraciones  127.0.0.1:8007
    notificaciones 127.0.0.1:8008 (extra)   relevo         sin puerto (extra)

La arquitectura no cambia: cada servicio sigue siendo su propio proceso, con su usuario y su esquema de base.
Cada uno recibe SOLO su cadena de conexión (SIGRO_DB_OBRAS → SIGRO_APP_DATABASE_URL de obras, y así), nunca la de
otro; integraciones y notificaciones no reciben ninguna cadena ni el secreto JWT.

- Si un proceso se cae, se reinicia con espera creciente (1, 2, 4 … 30 s); si llevaba 60 s arriba, la espera vuelve a 1 s.
- Si falta la variable de base de un servicio, ese servicio no arranca (el gateway lo mostrará «no_responde") y el
  resto sigue: así un olvido en el panel se ve en /salud/servicios y no tumba todo el ambiente.
- SIGTERM (Render al redesplegar o dormir) se reenvía a todos y se espera hasta 10 s antes de matar.
- Cada línea de log sale con el nombre del proceso: «[obras] …».
- Relevo y notificaciones son «extras»: se levantan si SIGRO_UN_NODO_EXTRAS=1 (ver ADR-10, memoria).

    python /app/infra/un_nodo/supervisor.py
"""
from __future__ import annotations

import asyncio
import os
import signal
import sys
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
            "SYSTEMROOT", "TEMP", "TMP", "PYTHONIOENCODING")   # las de Windows: sin SYSTEMROOT no hay red (WinError 10106)

ESPERA_MAX_S = 30.0
ESTABLE_S = 60.0


@dataclass
class Proceso:
    nombre: str
    comando: list[str]
    cwd: str
    entorno: dict[str, str] = field(default_factory=dict)
    falta: str | None = None          # variable obligatoria ausente: no se arranca


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
    lista = [Proceso("gateway", _uvicorn(VENV_API, "app.servicios.gateway:app", entorno.get("PORT", "10000"), "0.0.0.0"),
                     API, {k: v for k, v in comun.items() if k not in _SECRETOS_API} | {"SIGRO_SERVICIO": "gateway"})]
    for s in CON_BASE:
        variable = f"SIGRO_DB_{s.upper()}"
        p = Proceso(s, _uvicorn(VENV_API, f"app.servicios.{s}:app", PUERTOS[s]), API,
                    {k: v for k, v in comun.items() if k != "FACTILIZA_TOKEN"} | {"SIGRO_SERVICIO": s})
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
                         {k: v for k, v in comun.items() if k != "FACTILIZA_TOKEN"} | {"SIGRO_SERVICIO": "relevo"})
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


# ── ejecución (solo en el contenedor Linux) ─────────────────────────────────────────────────────────────────────

def _log(nombre: str, texto: str) -> None:
    sys.stdout.write(f"[{nombre}] {texto}\n")
    sys.stdout.flush()


async def _copiar(nombre: str, flujo: asyncio.StreamReader) -> None:
    while linea := await flujo.readline():
        _log(nombre, linea.decode(errors="replace").rstrip())


class Supervisor:
    def __init__(self, lista: list[Proceso]) -> None:
        self.lista = lista
        self.vivos: dict[str, asyncio.subprocess.Process] = {}
        self.parando = asyncio.Event()

    async def vigilar(self, p: Proceso) -> None:
        if p.falta:
            _log("supervisor", f"{p.nombre} NO se arranca: falta {p.falta} (el gateway lo verá «no_responde»)")
            return
        espera = 0.0
        while not self.parando.is_set():
            inicio = time.monotonic()
            hijo = await asyncio.create_subprocess_exec(
                *p.comando, cwd=p.cwd, env=p.entorno,
                stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT)
            self.vivos[p.nombre] = hijo
            _log("supervisor", f"{p.nombre} arrancó (pid {hijo.pid})")
            assert hijo.stdout is not None
            await asyncio.gather(_copiar(p.nombre, hijo.stdout), hijo.wait())
            self.vivos.pop(p.nombre, None)
            if self.parando.is_set():
                return
            duro = time.monotonic() - inicio
            espera = siguiente_espera(espera, duro)
            _log("supervisor", f"{p.nombre} terminó con código {hijo.returncode} tras {duro:.0f} s; "
                               f"se reinicia en {espera:.0f} s")
            try:
                await asyncio.wait_for(self.parando.wait(), espera)
            except asyncio.TimeoutError:
                pass

    async def parar(self) -> None:
        if self.parando.is_set():
            return
        self.parando.set()
        _log("supervisor", "parando todo")
        for h in list(self.vivos.values()):
            if h.returncode is None:
                h.terminate()
        limite = time.monotonic() + 10
        for h in list(self.vivos.values()):
            try:
                await asyncio.wait_for(h.wait(), max(0.1, limite - time.monotonic()))
            except asyncio.TimeoutError:
                h.kill()

    async def correr(self) -> None:
        bucle = asyncio.get_running_loop()
        for s in (signal.SIGTERM, signal.SIGINT):
            try:
                bucle.add_signal_handler(s, lambda: asyncio.ensure_future(self.parar()))
            except NotImplementedError:        # Windows (prueba local): Ctrl+C corta todo igual
                pass
        tareas = [asyncio.ensure_future(self.vigilar(self.lista[0]))]   # el gateway primero: Render busca $PORT
        await asyncio.sleep(0.5)
        tareas += [asyncio.ensure_future(self.vigilar(p)) for p in self.lista[1:]]
        await asyncio.gather(*tareas)


def main() -> int:
    lista = procesos(dict(os.environ))
    _log("supervisor", "procesos: " + ", ".join(p.nombre + (" (sin base)" if p.falta else "") for p in lista))
    asyncio.run(Supervisor(lista).correr())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
