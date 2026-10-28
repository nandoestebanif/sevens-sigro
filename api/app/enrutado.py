"""Tabla de enrutado del gateway: qué microservicio atiende cada ruta pública de /api/v1.

Una sola fuente de verdad: la usan el gateway en producción y las pruebas. Agregar un servicio
(almacén, costo) es agregar su línea aquí.
"""
from __future__ import annotations

import re

_OBRA = r"^/api/v1/obras/\d+/"

# (patrón, servicio) — se evalúan en orden; la primera que calza gana.
TABLA: list[tuple[re.Pattern[str], str]] = [
    (re.compile(_OBRA + r"(compras|solicitudes|requerimiento|saldo-por-comprar|parametros-reposicion)(/|$)"), "compras"),
    (re.compile(_OBRA + r"(trabajadores|jornadas|horas-acumuladas)(/|$)"), "tareo"),
    (re.compile(r"^/api/v1/proveedores(/|$)"), "compras"),
    (re.compile(r"^/api/v1/catalogo(/|$)"), "catalogo"),
    (re.compile(r"^/api/v1/(auth|usuarios|obras|notificaciones)(/|$)"), "obras"),
]


def servicio_para(ruta: str) -> str | None:
    """None: ninguna ruta pública calza (incluye /interno/…, que nunca se expone)."""
    for patron, servicio in TABLA:
        if patron.match(ruta):
            return servicio
    return None
