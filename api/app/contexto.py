"""Qué microservicio atiende la petición en curso.

Cada servicio lo fija al recibir la petición (middleware en app.servicios.base). El código común
(control de acceso, eventos) lo consulta para decidir si un dato es SUYO —y lo lee de su base— o es
de otro servicio —y lo pide por su API interna—. Nunca se lee la base de otro servicio.
"""
from __future__ import annotations

from contextvars import ContextVar

SERVICIO: ContextVar[str | None] = ContextVar("sigro_servicio", default=None)

OBRAS = "obras"          # identidad, obras, accesos y dispositivos (dueño de quién puede qué)
CATALOGO = "catalogo"
COMPRAS = "compras"
TAREO = "tareo"


def actual() -> str | None:
    return SERVICIO.get()


def es(servicio: str) -> bool:
    return SERVICIO.get() == servicio
