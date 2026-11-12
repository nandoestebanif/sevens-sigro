"""Servicio tareo: trabajadores de la obra y su asistencia diaria (horas hombre). Dueño del esquema `tareo`."""
from __future__ import annotations

from app.modulos.tareo.interno import routers as r_interno
from app.modulos.tareo.rutas import routers as r_tareo
from app.servicios.base import crear_servicio

NOMBRE = "tareo"


def crear_app():
    return crear_servicio(NOMBRE, "Trabajadores y asistencia de la obra.", r_tareo, r_interno)


app = crear_app()
