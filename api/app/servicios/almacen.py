"""Servicio almacén: el libro de movimientos (salidas, ingresos, recepciones, traslados, reversos, conteos), el saldo y
el kárdex derivados y el costo promedio ponderado congelado en cada salida. Dueño del esquema `almacen` (0010)."""
from __future__ import annotations

from app.modulos.almacen.interno import routers as r_interno
from app.modulos.almacen.rutas import routers as r_almacen
from app.servicios.base import crear_servicio

NOMBRE = "almacen"


def crear_app():
    return crear_servicio(NOMBRE, "Movimientos de almacén, saldo, kárdex y costo promedio ponderado.", r_almacen, r_interno)


app = crear_app()
