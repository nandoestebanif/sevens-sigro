"""Servicio compras: proveedores (con SUNAT vía integraciones), compras, solicitudes de materiales,
requerimiento de la obra, saldo por comprar y parámetros de reposición. Dueño del esquema `compras`."""
from __future__ import annotations

from app.modulos.compras.requerimiento import routers as r_requerimiento
from app.modulos.compras.rutas import routers as r_compras
from app.servicios.base import crear_servicio

NOMBRE = "compras"


def crear_app():
    return crear_servicio(NOMBRE, "Proveedores, compras, solicitudes, requerimiento y reposición.", [*r_compras, *r_requerimiento])


app = crear_app()
