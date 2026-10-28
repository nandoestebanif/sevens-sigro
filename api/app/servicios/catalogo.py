"""Servicio catálogo: el código único de insumo (regla 8), su verificación, el factor de conversión y la
importación del catálogo del S10. Dueño del esquema `catalogo`."""
from __future__ import annotations

from app.modulos.catalogo.interno import routers as r_interno
from app.modulos.catalogo.rutas import routers as r_catalogo
from app.servicios.base import crear_servicio

NOMBRE = "catalogo"


def crear_app():
    return crear_servicio(NOMBRE, "Catálogo único de insumos para todas las obras.", r_catalogo, r_interno)


app = crear_app()
