"""Lo que todo microservicio de SIGRO trae igual: errores {codigo, mensaje}, /salud, CORS y su nombre."""
from __future__ import annotations

import os
from collections.abc import Iterable

from fastapi import APIRouter, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from starlette.types import ASGIApp, Receive, Scope, Send

from app import contexto, errores

PREFIJO = "/api/v1"


class _MarcaServicio:
    """Fija `contexto.SERVICIO` durante toda la petición (también en el hilo donde corre el endpoint)."""

    def __init__(self, app: ASGIApp, nombre: str):
        self.app, self.nombre = app, nombre

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        marca = contexto.SERVICIO.set(self.nombre)
        try:
            await self.app(scope, receive, send)
        finally:
            contexto.SERVICIO.reset(marca)


def crear_servicio(nombre: str, descripcion: str, publicos: Iterable[APIRouter], internos: Iterable[APIRouter] = ()) -> FastAPI:
    app = FastAPI(title=f"SIGRO · servicio {nombre}", version="0.2.0", description=descripcion)
    app.state.servicio = nombre
    origenes = [o for o in os.environ.get("SIGRO_CORS", "http://localhost:5173").split(",") if o]
    app.add_middleware(
        CORSMiddleware, allow_origins=origenes, allow_methods=["GET", "POST"], allow_headers=["Authorization", "Content-Type"]
    )
    app.add_middleware(_MarcaServicio, nombre=nombre)
    errores.instalar(app)

    @app.get("/salud", tags=["sistema"])
    def salud():
        return {"estado": "ok", "servicio": nombre}

    for r in publicos:
        app.include_router(r, prefix=PREFIJO)
    for r in internos:
        app.include_router(r)          # /interno/...: sin prefijo público; el gateway no lo enruta
    return app
