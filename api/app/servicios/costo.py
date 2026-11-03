"""Servicio costo: modelo de lectura del gasto en recursos de la obra (HU-58), alimentado por eventos de almacén.
Dueño del esquema `costo` (0010)."""
from __future__ import annotations

from app.modulos.costo.interno import routers as r_interno
from app.modulos.costo.rutas import routers as r_costo
from app.servicios.base import crear_servicio

NOMBRE = "costo"


def crear_app():
    return crear_servicio(NOMBRE, "Gasto en recursos de la obra (no su costo total).", r_costo, r_interno)


app = crear_app()
