"""Servicio obras: identidad (sesión, usuarios), obras y sus periodos y almacenes, asignaciones por obra,
dispositivos autorizados y las notificaciones de cada usuario. Dueño del esquema `obra` y de su outbox."""
from __future__ import annotations

from app.modulos.notificaciones.rutas import routers as r_notif
from app.modulos.obra.interno import routers as r_interno
from app.modulos.obra.rutas import routers as r_obra
from app.servicios.base import crear_servicio

NOMBRE = "obras"


def crear_app():
    return crear_servicio(NOMBRE, "Identidad, obras, accesos y dispositivos. Responde a los demás servicios quién puede qué.",
                          [*r_obra, *r_notif], r_interno)


app = crear_app()
