"""Ambiente de PRUEBAS en un solo nodo y en UN solo proceso Python (ADR-10, revisión del 04/11/2026).

Render gratis da 512 MB y mataba `sigro-api` por OOM («Ran out of memory (used over 512MB)») con un intérprete por
servicio. Aquí todas las apps ASGI corren en el MISMO event loop, cada una con su propio `uvicorn.Server`:

    gateway        0.0.0.0:$PORT (público)   obras          127.0.0.1:8001
    catalogo       127.0.0.1:8002            compras        127.0.0.1:8003
    tareo          127.0.0.1:8004            almacen        127.0.0.1:8005
    costo          127.0.0.1:8006            integraciones  127.0.0.1:8007
    notificaciones 127.0.0.1:8008 (extra)    relevo         tarea asyncio, sin puerto (extra)

La arquitectura lógica no cambia:
- cada servicio sigue siendo su app, con su puerto, y se hablan por HTTP a 127.0.0.1 (`/interno` con
  `X-Servicio-Token`) o por eventos (outbox): el código de los servicios no sabe que comparte proceso;
- cada servicio se conecta con SU usuario de base: `SIGRO_DB_<SERVICIO>`, elegido por `contexto.SERVICIO` de la
  petición en curso (`app.config.url_de_base`); sin servicio o sin su variable, falla en vez de usar otra;
- producción sigue con un contenedor por servicio (`api/Dockerfile`, `docker-compose.yml`): este archivo solo se
  usa en `api/Dockerfile.pruebas`.

Lo que se acepta a cambio (solo en pruebas): un fallo que tumbe el proceso tumba todo el ambiente. Si un servidor
cae, se registra y el proceso entero termina con código 1 para que Render lo reinicie limpio.

Variables: `PORT` (10000), `SIGRO_UN_NODO_EXTRAS` (1 = notificaciones y relevo), `SIGRO_UN_NODO_PUERTO_BASE`
(8001), `SIGRO_HILOS` (60, hilos para los endpoints síncronos de TODAS las apps), `SIGRO_RELEVO_INTERVALO` (3 s),
`SIGRO_LOG_NIVEL` (warning, el de uvicorn).

    python /app/infra/un_nodo/un_proceso.py
"""
from __future__ import annotations

import asyncio
import contextlib
import importlib
import importlib.util
import logging
import os
import signal
import sys
import traceback
from collections.abc import Awaitable, Callable
from dataclasses import dataclass

API = os.environ.get("SIGRO_UN_NODO_API", "/app/api")
RAIZ = os.environ.get("SIGRO_UN_NODO_RAIZ", "/app")

CON_BASE = ("obras", "catalogo", "compras", "tareo", "almacen", "costo")
_ORDEN = (*CON_BASE, "integraciones", "notificaciones")      # puerto = base + posición (8001 … 8008)

log = logging.getLogger("sigro.un_nodo")


def puertos(entorno: dict[str, str]) -> dict[str, int]:
    base = int(entorno.get("SIGRO_UN_NODO_PUERTO_BASE", "8001"))
    return {s: base + i for i, s in enumerate(_ORDEN)}


def _extras(entorno: dict[str, str]) -> bool:
    return entorno.get("SIGRO_UN_NODO_EXTRAS", "0") in ("1", "si", "sí", "true")


@dataclass(frozen=True)
class App:
    nombre: str
    host: str
    puerto: int
    falta: str | None = None          # variable obligatoria ausente: no se levanta (el gateway la verá «no_responde»)


def entorno_del_proceso(entorno: dict[str, str]) -> tuple[dict[str, str], list[str]]:
    """Variables que se fijan y que se quitan antes de importar las apps. Función pura: se prueba sin servidor."""
    p = puertos(entorno)
    fijar = {f"SIGRO_URL_{s.upper()}": f"http://127.0.0.1:{p[s]}" for s in CON_BASE}
    fijar["SIGRO_INTEGRACIONES_URL"] = fijar["SIGRO_URL_INTEGRACIONES"] = f"http://127.0.0.1:{p['integraciones']}"
    fijar["SIGRO_NOTIFICACIONES_URL"] = f"http://127.0.0.1:{p['notificaciones']}"
    fijar["SIGRO_UN_PROCESO"] = "1"                           # app.config: la base sale de SIGRO_DB_<SERVICIO>
    token = entorno.get("SIGRO_SERVICIO_TOKEN", "")
    fijar["SERVICIO_TOKEN"] = entorno.get("SERVICIO_TOKEN") or token        # nombre que usa integraciones
    fijar["NOTIF_TOKEN"] = entorno.get("NOTIF_TOKEN") or token              # nombre que usa notificaciones
    fijar["NOTIF_DB"] = entorno.get("NOTIF_DB", "/tmp/notificaciones.db")   # efímero en Render gratis
    # Una cadena «global» no puede existir: sería la de un servicio a la vista de todos, y la de dueño (migraciones)
    # nunca la necesita la aplicación.
    quitar = [k for k in ("SIGRO_APP_DATABASE_URL", "SIGRO_DATABASE_URL", "SIGRO_OWNER_DSN") if k in entorno]
    return fijar, quitar


def apps(entorno: dict[str, str]) -> list[App]:
    """Qué servidores se levantan, en qué dirección, y cuáles no por falta de su variable de base."""
    p = puertos(entorno)
    lista = [App("gateway", "0.0.0.0", int(entorno.get("PORT", "10000")))]
    for s in CON_BASE:
        variable = f"SIGRO_DB_{s.upper()}"
        lista.append(App(s, "127.0.0.1", p[s], None if entorno.get(variable) else variable))
    lista.append(App("integraciones", "127.0.0.1", p["integraciones"]))
    if _extras(entorno):
        lista.append(App("notificaciones", "127.0.0.1", p["notificaciones"]))
    return lista


def con_relevo(entorno: dict[str, str]) -> bool:
    return _extras(entorno) and bool(entorno.get("SIGRO_DB_RELEVO"))


# ── carga de las apps ────────────────────────────────────────────────────────────────────────────────────────────

def _app_de_carpeta(nombre: str, carpeta: str):
    """integraciones/ y notificaciones/ son un `main.py` suelto cada uno: se cargan con nombre propio para que no
    choquen entre sí, y su carpeta va al final de sys.path para sus módulos hermanos (s10, almacen)."""
    if carpeta not in sys.path:
        sys.path.append(carpeta)
    spec = importlib.util.spec_from_file_location(f"sigro_{nombre}_main", os.path.join(carpeta, "main.py"))
    modulo = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = modulo
    spec.loader.exec_module(modulo)
    return modulo.app


def cargar_app(nombre: str):
    if nombre == "gateway":
        return importlib.import_module("app.servicios.gateway").app
    if nombre in CON_BASE:
        return importlib.import_module(f"app.servicios.{nombre}").app
    return _app_de_carpeta(nombre, os.path.join(RAIZ, nombre))


# ── ejecución ────────────────────────────────────────────────────────────────────────────────────────────────────

def _servidor_sin_senales():
    import uvicorn

    class Servidor(uvicorn.Server):
        """uvicorn instala sus manejadores de SIGTERM/SIGINT por servidor y con varios en un proceso se pisan:
        aquí las señales las atiende el lanzador, que detiene a todos a la vez."""

        @contextlib.contextmanager
        def capture_signals(self):
            yield

    return Servidor


async def _relevo(parar: asyncio.Event) -> None:
    """El relevo del outbox como tarea: cada vuelta corre en un hilo (es síncrono) con contexto «relevo», así
    `app.db` usa SIGRO_DB_RELEVO y no la base de otro servicio."""
    from app import contexto
    from app.relay import enviar_http, procesar
    from sqlalchemy.orm import Session

    from app.db import motor

    contexto.SERVICIO.set("relevo")          # esta tarea tiene su propio contexto; asyncio.to_thread lo copia
    intervalo = float(os.environ.get("SIGRO_RELEVO_INTERVALO", "3"))
    espera = intervalo
    while not parar.is_set():
        try:
            r = await asyncio.to_thread(procesar, lambda: Session(motor()), enviar_http)
        except Exception as e:  # noqa: BLE001 — la base despertando no tumba el ambiente: se reintenta (1, 2 … 30 s)
            espera = min(max(espera * 2, 1.0), 30.0)
            log.warning("relevo: la vuelta falló (%s); se reintenta en %.0f s", type(e).__name__, espera)
        else:
            espera = intervalo
            if r.enviados or r.fallidos:
                log.warning("relevo: %s entregados, %s pendientes de reintento", r.enviados, r.fallidos)
        with contextlib.suppress(asyncio.TimeoutError):
            await asyncio.wait_for(parar.wait(), espera)


async def correr(lista: list[App], relevo: bool, nivel: str = "warning") -> int:
    """Levanta todo en este event loop. Devuelve 0 si se paró por señal y 1 si algún servidor cayó."""
    import anyio.to_thread
    import uvicorn

    # Un solo threadpool para los endpoints síncronos de TODAS las apps (antes, 40 hilos por proceso). Una petición
    # de almacén ocupa un hilo y su /interno/autorizar a obras otro: con pocos hilos, la carga se bloquearía sola.
    anyio.to_thread.current_default_thread_limiter().total_tokens = int(os.environ.get("SIGRO_HILOS", "60"))

    Servidor = _servidor_sin_senales()
    parar = asyncio.Event()
    cayo: list[str] = []
    loop = asyncio.get_running_loop()
    for s in (signal.SIGTERM, signal.SIGINT):
        try:
            loop.add_signal_handler(s, parar.set)
        except (NotImplementedError, RuntimeError):          # Windows: sin add_signal_handler
            signal.signal(s, lambda *_: loop.call_soon_threadsafe(parar.set))

    servidores: list[tuple[str, uvicorn.Server]] = []
    for a in lista:
        if a.falta:
            log.warning("%s NO se levanta: falta %s (el gateway lo verá «no_responde»)", a.nombre, a.falta)
            continue
        config = uvicorn.Config(cargar_app(a.nombre), host=a.host, port=a.puerto, workers=1, lifespan="on",
                                log_level=nivel, access_log=False, timeout_graceful_shutdown=5, reload=False)
        servidores.append((a.nombre, Servidor(config)))

    async def vigilar(nombre: str, tarea: Callable[[], Awaitable[None]]) -> None:
        try:
            await tarea()
            motivo = "terminó"
        except BaseException as e:            # noqa: BLE001 — uvicorn sale con SystemExit si no puede abrir el puerto
            motivo = f"falló ({type(e).__name__}: {e})"
            traceback.print_exc()
        if not parar.is_set():
            log.error("%s %s: se detiene todo el proceso para que Render lo reinicie", nombre, motivo)
            cayo.append(nombre)
            parar.set()

    async def detener() -> None:
        await parar.wait()
        log.warning("deteniendo %d servidores%s", len(servidores), " y el relevo" if relevo else "")
        for _, s in servidores:
            s.should_exit = True

    tareas = [vigilar(n, s.serve) for n, s in servidores]
    if relevo:
        tareas.append(vigilar("relevo", lambda: _relevo(parar)))
    log.warning("un proceso (pid %d): %s%s", os.getpid(), ", ".join(f"{n}:{s.config.port}" for n, s in servidores),
                " + relevo" if relevo else "")
    await asyncio.gather(detener(), *tareas)
    return 1 if cayo else 0


def main() -> int:
    logging.basicConfig(level=logging.WARNING, format="[%(name)s] %(message)s", stream=sys.stdout)
    logging.getLogger("sigro").setLevel(logging.INFO)       # «sigro servicio=… ray=…» y «sigro base: …», como antes
    entorno = dict(os.environ)
    fijar, quitar = entorno_del_proceso(entorno)
    os.environ.update(fijar)
    for k in quitar:
        os.environ.pop(k, None)
    if API not in sys.path:
        sys.path.insert(0, API)
    lista = apps(dict(os.environ))
    relevo = con_relevo(dict(os.environ))
    if _extras(entorno) and not relevo:
        log.warning("relevo NO se levanta: falta SIGRO_DB_RELEVO")
    return asyncio.run(correr(lista, relevo, os.environ.get("SIGRO_LOG_NIVEL", "warning")))


if __name__ == "__main__":
    raise SystemExit(main())
