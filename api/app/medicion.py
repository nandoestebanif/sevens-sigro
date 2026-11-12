"""Medición por petición: cabecera Server-Timing y una línea de log con el CF-Ray.

Para saber DÓNDE se va el tiempo de una petición lenta en Render sin adivinar. Cada servicio suma, por petición:

    total     tiempo dentro del proceso, desde que llega la petición hasta que sale la cabecera de respuesta
    db        tiempo esperando a PostgreSQL (consultas) y cuántas consultas fueron
    db-con    tiempo abriendo conexiones nuevas a la base (TLS con Neon); 0 si el pool ya tenía una abierta
    interno   tiempo esperando a otros servicios por /interno/... y cuántas llamadas fueron

y lo devuelve en `Server-Timing` (lo leen curl -D, las DevTools del navegador y el script de medición):

    Server-Timing: obras;dur=41.2, obras-db;dur=30.5;desc="3 consultas", obras-db-con;dur=0, obras-interno;dur=0;desc="0"

El gateway agrega lo suyo (`gw`, `gw-up` = espera al servicio) y conserva lo que respondió el servicio, así una
sola respuesta dice cuánto fue red de afuera, cuánto el salto gateway → servicio y cuánto la base.

El identificador que une las líneas de log de todos los servicios es el **CF-Ray** con que Cloudflare marcó la
petición al entrar a Render. El gateway lo reenvía como `X-Sigro-Ray` (Cloudflare vuelve a marcar cada salto
entre servicios con un ray nuevo) y cada servicio lo pasa a sus llamadas /interno.

No guarda nada ni toca la base: solo mide. `SIGRO_SERVER_TIMING=0` quita la cabecera (el log se mantiene).
"""
from __future__ import annotations

import logging
import os
import time
from contextvars import ContextVar
from dataclasses import dataclass

from starlette.types import ASGIApp, Message, Receive, Scope, Send

log = logging.getLogger("sigro.medicion")
if not log.handlers:                      # uvicorn no configura loggers ajenos: sin esto la línea no sale en Render
    _h = logging.StreamHandler()
    _h.setFormatter(logging.Formatter("%(message)s"))
    log.addHandler(_h)
    log.setLevel(logging.INFO)
    log.propagate = False

CABECERA_RAY = "x-sigro-ray"


@dataclass
class Medida:
    """Acumulador de UNA petición. Mutable a propósito: los endpoints síncronos corren en otro hilo con una copia
    del contexto, y esa copia apunta al mismo objeto, así lo que suman allá se ve aquí."""

    ray: str = "-"
    db_ms: float = 0.0
    db_n: int = 0
    con_ms: float = 0.0
    con_n: int = 0
    interno_ms: float = 0.0
    interno_n: int = 0


ACTUAL: ContextVar[Medida | None] = ContextVar("sigro_medida", default=None)


def actual() -> Medida | None:
    return ACTUAL.get()


def ray_actual() -> str | None:
    m = ACTUAL.get()
    return m.ray if m and m.ray != "-" else None


def sumar_db(ms: float) -> None:
    m = ACTUAL.get()
    if m is not None:
        m.db_ms += ms
        m.db_n += 1


def sumar_conexion(ms: float) -> None:
    m = ACTUAL.get()
    if m is not None:
        m.con_ms += ms
        m.con_n += 1


def sumar_interno(ms: float) -> None:
    m = ACTUAL.get()
    if m is not None:
        m.interno_ms += ms
        m.interno_n += 1


def _activo() -> bool:
    return os.environ.get("SIGRO_SERVER_TIMING", "1") not in ("0", "no", "false")


GATEWAY = "gw"


def _ray_de(scope: Scope, nombre_servicio: str) -> str:
    """En un servicio, el ray que puso el gateway (X-Sigro-Ray) gana sobre el CF-Ray de su propio salto.
    En el gateway manda el CF-Ray con que Cloudflare marcó la entrada (un X-Sigro-Ray de afuera no se cree)."""
    cabeceras = dict(scope.get("headers") or [])
    orden = (b"cf-ray",) if nombre_servicio == GATEWAY else (CABECERA_RAY.encode(), b"cf-ray")
    for nombre in orden:
        if v := cabeceras.get(nombre):
            return v.decode("latin-1")[:64]
    return "-"


def entradas_servicio(nombre: str, m: Medida, total_ms: float) -> list[str]:
    if nombre == GATEWAY:       # el gateway no tiene base: su «interno» es la espera al servicio dueño de la ruta
        return [f"gw;dur={total_ms:.1f}", f"gw-up;dur={m.interno_ms:.1f}"]
    return [
        f"{nombre};dur={total_ms:.1f}",
        f'{nombre}-db;dur={m.db_ms:.1f};desc="{m.db_n} consultas"',
        f'{nombre}-db-con;dur={m.con_ms:.1f};desc="{m.con_n} conexiones nuevas"',
        f'{nombre}-interno;dur={m.interno_ms:.1f};desc="{m.interno_n} llamadas"',
    ]


class MedirPeticion:
    """Middleware ASGI de cada servicio FastAPI y del gateway (`nombre=GATEWAY`)."""

    def __init__(self, app: ASGIApp, nombre: str):
        self.app, self.nombre = app, nombre

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        m = Medida(ray=_ray_de(scope, self.nombre))
        marca = ACTUAL.set(m)
        inicio = time.perf_counter()
        estado = {"codigo": 0}

        async def enviar(msg: Message) -> None:
            if msg["type"] == "http.response.start":
                estado["codigo"] = msg["status"]
                total = (time.perf_counter() - inicio) * 1000
                if _activo():
                    msg = _con_server_timing(msg, entradas_servicio(self.nombre, m, total))
                _registrar(self.nombre, scope, estado["codigo"], m, total)
            await send(msg)

        try:
            await self.app(scope, receive, enviar)
        finally:
            ACTUAL.reset(marca)


def _con_server_timing(msg: Message, propias: list[str]) -> Message:
    """Agrega las entradas propias DELANTE de las que ya traía la respuesta (las del servicio, en el gateway)."""
    cabeceras = [(k, v) for k, v in msg.get("headers", []) if k.lower() != b"server-timing"]
    previas = [v.decode("latin-1") for k, v in msg.get("headers", []) if k.lower() == b"server-timing"]
    valor = ", ".join(propias + previas)
    cabeceras.append((b"server-timing", valor.encode("latin-1")))
    origenes = os.environ.get("SIGRO_CORS", "")
    if origenes and not any(k.lower() == b"timing-allow-origin" for k, _ in cabeceras):
        # Sin esto el navegador oculta Server-Timing a la web (otro origen) en la pestaña Timing de las DevTools.
        cabeceras.append((b"timing-allow-origin", origenes.split(",")[0].encode("latin-1")))
    return {**msg, "headers": cabeceras}


def _registrar(nombre: str, scope: Scope, codigo: int, m: Medida, total: float) -> None:
    ruta = scope.get("path", "")
    if ruta == "/salud":                  # el chequeo de salud de Render llega cada pocos segundos: no ensucia el log
        return
    log.info(
        "sigro servicio=%s ray=%s %s %s -> %s total=%.1fms db=%.1fms/%d con=%.1fms/%d interno=%.1fms/%d",
        nombre, m.ray, scope.get("method", "-"), ruta, codigo, total,
        m.db_ms, m.db_n, m.con_ms, m.con_n, m.interno_ms, m.interno_n,
    )
