"""SIGRO · API (monolito modular). Arranque: uvicorn app.main:app

Módulos de esta carpeta: obra, tareo, compras (Adiel). catalogo es mínimo y provisional (Fernando);
almacen y costo son de Henry y se montan aquí cuando existan.
"""
from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

import os

from app import errores
from app.modulos.catalogo.rutas import routers as r_catalogo
from app.modulos.compras.rutas import routers as r_compras
from app.modulos.compras.requerimiento import routers as r_requerimiento
from app.modulos.notificaciones.rutas import routers as r_notif
from app.modulos.obra.rutas import routers as r_obra
from app.modulos.tareo.rutas import routers as r_tareo

PREFIJO = "/api/v1"


def crear_app() -> FastAPI:
    app = FastAPI(
        title="SIGRO API",
        version="0.1.0",
        description="Sistema de Gestión de Recursos de Obra · SEVENS INGENIEROS SAC. "
                    "Cada endpoint verifica rol y obra en el servidor (docs/permisos.md).",
    )
    origenes = [o for o in os.environ.get("SIGRO_CORS", "http://localhost:5173").split(",") if o]
    app.add_middleware(
        CORSMiddleware, allow_origins=origenes, allow_methods=["GET", "POST"], allow_headers=["Authorization", "Content-Type"]
    )
    errores.instalar(app)

    @app.get("/salud", tags=["sistema"])
    def salud():
        return {"estado": "ok"}

    for r in (*r_obra, *r_tareo, *r_compras, *r_requerimiento, *r_catalogo, *r_notif):
        app.include_router(r, prefix=PREFIJO)
    return app


app = crear_app()
