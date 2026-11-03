"""Los microservicios de SIGRO que viven en esta carpeta de código.

Cada uno es una aplicación FastAPI independiente: su propio proceso, su propio contenedor, su propio
usuario de base de datos con acceso SOLO a su esquema, y su API. Se hablan por HTTP (API interna) y
por eventos (outbox). El cliente (web y app) entra siempre por el gateway.

    uvicorn app.servicios.obras:app       identidad, obras, accesos, dispositivos, notificaciones del usuario
    uvicorn app.servicios.catalogo:app    catálogo único de insumos
    uvicorn app.servicios.compras:app     proveedores, compras, solicitudes, requerimiento y reposición
    uvicorn app.servicios.tareo:app       trabajadores y asistencia (horas hombre)
    uvicorn app.servicios.almacen:app     movimientos, saldo, kárdex y costo promedio ponderado (0010)
    uvicorn app.servicios.costo:app       gasto en recursos de la obra (modelo de lectura, 0010)
    uvicorn app.servicios.gateway:app     puerta de entrada única /api/v1

Además, fuera de esta carpeta: integraciones/ (S10, SUNAT, RENIEC) y notificaciones/ (avisos).
"""
from __future__ import annotations

from collections.abc import Callable

from fastapi import FastAPI


def fabricas() -> dict[str, Callable[[], FastAPI]]:
    """Nombre → función que crea la app del servicio (para pruebas y herramientas)."""
    from app.servicios import almacen, catalogo, compras, costo, obras, tareo
    return {"obras": obras.crear_app, "catalogo": catalogo.crear_app, "compras": compras.crear_app, "tareo": tareo.crear_app,
            "almacen": almacen.crear_app, "costo": costo.crear_app}
