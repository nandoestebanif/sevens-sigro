"""Sesión de base de datos. Una transacción por petición: confirma si todo salió bien, revierte si no.

Pool chico pero persistente (docs/rendimiento-render.md): en Render cada servicio corre en UN proceso con pocas
peticiones a la vez; lo caro es abrir la conexión (TCP + TLS + autenticación con Neon), no tenerla abierta.

- `SIGRO_DB_POOL` (1) conexión se queda abierta; en picos se abren más hasta `POOL_MAX` (3) en total por servicio.
  Neon -pooler (PgBouncer) ya multiplexa del otro lado: más conexiones por servicio solo gastan memoria.
- Un motor por cadena de conexión. Con un proceso por servicio hay uno solo; en el nodo único (ADR-10, un proceso)
  hay uno por servicio, cada uno con SU usuario de base (`config.url_de_base`): nunca se comparte un pool.
- `pool_recycle` (240 s) por debajo de los 5 min en que Neon suspende el cómputo: una conexión vieja se renueva
  antes de que el servidor la corte. `pool_pre_ping` se mantiene: si Neon la cortó igual, se descarta sin error.
- Con la cadena **-pooler** de Neon (PgBouncer en modo transacción) se apagan los prepared statements
  automáticos de psycopg: una sentencia preparada en una conexión física no existe en la siguiente. Las
  migraciones no pasan por aquí: usan SIGRO_DATABASE_URL, la cadena directa.
"""
from __future__ import annotations

import os
import time
from collections.abc import Iterator
import threading
from urllib.parse import urlsplit

from sqlalchemy import create_engine, event
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session

from app import medicion
from app.config import cargar


def es_pooler(url: str) -> bool:
    """La cadena con pooling de Neon tiene «-pooler» en el host (ep-xxx-pooler.us-east-2.aws.neon.tech)."""
    return "-pooler" in (urlsplit(url).hostname or "")


def opciones_motor(url: str) -> dict:
    """Parámetros del pool. Función aparte para probarla sin base."""
    minimo = max(int(os.environ.get("SIGRO_DB_POOL", "1")), 1)
    maximo = max(int(os.environ.get("POOL_MAX", "3")), minimo)
    opciones: dict = {
        "pool_pre_ping": True,
        "pool_size": minimo,
        "max_overflow": max(maximo - minimo, 0),
        "pool_recycle": int(os.environ.get("SIGRO_DB_RECICLAR_S", "240")),
        "pool_timeout": 10,
    }
    if es_pooler(url):
        opciones["connect_args"] = {"prepare_threshold": None}
    return opciones


def instrumentar(m: Engine) -> Engine:
    """Suma a la petición en curso el tiempo de cada consulta y de cada conexión nueva (Server-Timing)."""

    @event.listens_for(m, "before_cursor_execute")
    def _antes(conn, cursor, statement, parameters, context, executemany):  # noqa: ANN001
        conn.info.setdefault("sigro_t", []).append(time.perf_counter())

    @event.listens_for(m, "after_cursor_execute")
    def _despues(conn, cursor, statement, parameters, context, executemany):  # noqa: ANN001
        pila = conn.info.get("sigro_t")
        if pila:
            medicion.sumar_db((time.perf_counter() - pila.pop()) * 1000)

    @event.listens_for(m, "handle_error")
    def _error(ctx):  # noqa: ANN001
        pila = ctx.connection.info.get("sigro_t") if ctx.connection is not None else None
        if pila:
            medicion.sumar_db((time.perf_counter() - pila.pop()) * 1000)

    @event.listens_for(m, "do_connect")
    def _conectar(dialect, conn_rec, cargs, cparams):  # noqa: ANN001
        inicio = time.perf_counter()
        try:
            return dialect.connect(*cargs, **cparams)
        finally:
            medicion.sumar_conexion((time.perf_counter() - inicio) * 1000)

    return m


_motores: dict[str, Engine] = {}
_candado = threading.Lock()


def motor() -> Engine:
    url = cargar().database_url
    m = _motores.get(url)
    if m is None:
        with _candado:                       # los endpoints síncronos corren en hilos: se crea una sola vez
            m = _motores.get(url)
            if m is None:
                op = opciones_motor(url)
                medicion.log.info("sigro base: usuario=%s pooler=%s pool=%s..%s", urlsplit(url).username,
                                  "sí" if es_pooler(url) else "no", op["pool_size"], op["pool_size"] + op["max_overflow"])
                m = _motores[url] = instrumentar(create_engine(url, **op))
    return m


def obtener_sesion() -> Iterator[Session]:
    with Session(motor()) as s:
        try:
            yield s
            s.commit()
        except BaseException:
            s.rollback()
            raise
