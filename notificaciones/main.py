"""SIGRO · Notificaciones. Microservicio que recibe eventos de la API y los entrega a cada persona.

    NOTIF_TOKEN=un-secreto-largo uvicorn main:app --port 8002

Frontera (ADR-01): no lee ni escribe la base de la API. La API le manda el evento ya con sus destinatarios
(solo ella sabe quién es qué en cada obra) y él guarda su propia copia. Si este servicio se cae, la API sigue
funcionando y los eventos esperan en su bandeja de salida (outbox) hasta que vuelva.

Autenticación entre servicios: cabecera `X-Servicio-Token`. Las personas NO llaman aquí: la web llama a la API y
la API pregunta a este servicio por el usuario del token, así nadie lee las notificaciones de otro.
"""
from __future__ import annotations

import hmac
import os
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Response
from pydantic import BaseModel, ConfigDict, Field

import almacen


@asynccontextmanager
async def _vida(_: FastAPI):
    almacen.iniciar()
    yield


app = FastAPI(title="SIGRO · Notificaciones", version="0.1.0", lifespan=_vida)


def servicio(x_servicio_token: str | None = Header(default=None)) -> None:
    esperado = os.environ.get("NOTIF_TOKEN")
    if not esperado:
        raise HTTPException(503, "NOTIF_TOKEN no está configurado.")
    if x_servicio_token is None or not hmac.compare_digest(x_servicio_token.encode(), esperado.encode()):
        raise HTTPException(401, "Token de servicio inválido.")


class EventoIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    evento_id: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9._:-]+$", description="Clave de idempotencia.")
    tipo: str = Field(pattern=r"^[a-z]+(\.[a-z_]+)+$", max_length=60)
    proyecto_id: int = Field(ge=1)
    creado_en: datetime
    destinatarios: list[int] = Field(min_length=1, max_length=200)
    titulo: str = Field(min_length=1, max_length=120)
    mensaje: str = Field(min_length=1, max_length=500)
    datos: dict = Field(default_factory=dict)


class RecibidoOut(BaseModel):
    nuevo: bool
    notificaciones: int


class NotificacionOut(BaseModel):
    id: int
    proyecto_id: int
    tipo: str
    titulo: str
    mensaje: str
    datos: dict
    creada_en: str
    leida: bool
    leida_en: str | None


class ListaOut(BaseModel):
    no_leidas: int
    notificaciones: list[NotificacionOut]


@app.get("/salud")
def salud() -> dict:
    return {"servicio": "notificaciones", "token_configurado": bool(os.environ.get("NOTIF_TOKEN"))}


@app.post("/eventos", response_model=RecibidoOut, dependencies=[Depends(servicio)])
def recibir(e: EventoIn, respuesta: Response) -> RecibidoOut:
    """Idempotente: el mismo `evento_id` dos veces crea las notificaciones una sola vez (201 la primera, 200 después)."""
    datos = e.model_dump()
    datos["creado_en"] = e.creado_en.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
    datos["destinatarios"] = sorted(set(e.destinatarios))
    with almacen.conexion() as c:
        nuevo, n = almacen.registrar(c, datos)
    respuesta.status_code = 201 if nuevo else 200
    return RecibidoOut(nuevo=nuevo, notificaciones=n)


@app.get("/notificaciones", response_model=ListaOut, dependencies=[Depends(servicio)])
def listar(usuario_id: int = Query(ge=1), solo_no_leidas: bool = False, limite: int = Query(50, ge=1, le=200)) -> ListaOut:
    with almacen.conexion() as c:
        return ListaOut(
            no_leidas=almacen.contar_no_leidas(c, usuario_id),
            notificaciones=almacen.listar(c, usuario_id, solo_no_leidas, limite),
        )


@app.post("/notificaciones/{notificacion_id}/leida", status_code=204, dependencies=[Depends(servicio)])
def marcar_leida(notificacion_id: int, usuario_id: int = Query(ge=1)) -> Response:
    with almacen.conexion() as c:
        if not almacen.marcar_leida(c, notificacion_id, usuario_id):
            raise HTTPException(404, "Esa notificación no existe.")
    return Response(status_code=204)
