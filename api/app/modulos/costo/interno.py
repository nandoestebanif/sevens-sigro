"""API interna del servicio costo: el relevo le entrega los eventos de integración de la bandeja de salida (D2).

Idempotente por `evento_id`: el relevo entrega «al menos una vez»; si un evento llega dos veces, la segunda no
escribe nada (y responde 200 igual, para que el relevo lo dé por entregado).
"""
from __future__ import annotations

from datetime import date
from decimal import Decimal
from typing import Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.db import obtener_sesion
from app.internos import exigir_servicio

interno = APIRouter(prefix="/interno", tags=["interno"], dependencies=[Depends(exigir_servicio)])


class HechoIn(BaseModel):
    proyecto_id: int
    familia: Literal["material", "mano_obra", "equipo"]
    clase: Literal["ingreso", "consumo", "traslado", "ajuste", "compra", "horas"]
    insumo_codigo: str
    cantidad: Decimal                 # con signo: + entra al stock de la obra, − sale
    unidad: str
    costo_unitario: Decimal = Field(ge=0)
    moneda: Literal["PEN", "USD"] = "PEN"
    fecha: date
    referencia: str


class DatosIn(BaseModel):
    hechos: list[HechoIn] = Field(default_factory=list, max_length=2000)


class EventoIn(BaseModel):
    evento_id: int
    tipo: str
    proyecto_id: int
    creado_en: str | None = None
    datos: DatosIn


@interno.post("/eventos")
def recibir(ev: EventoIn, s: Session = Depends(obtener_sesion, scope="function")):
    nuevo = s.execute(text("INSERT INTO costo.evento_recibido (evento_id, tipo) VALUES (:i, :t) ON CONFLICT (evento_id) DO NOTHING "
                           "RETURNING evento_id"), {"i": ev.evento_id, "t": ev.tipo}).scalar()
    if nuevo is None:
        return {"evento_id": ev.evento_id, "duplicado": True, "hechos": 0}
    for h in ev.datos.hechos:
        s.execute(text("INSERT INTO costo.hecho (evento_id, proyecto_id, familia, clase, insumo_codigo, cantidad, unidad, costo_unitario, "
                       "moneda, fecha, referencia) VALUES (:e, :p, :f, :c, :ic, :q, :u, :cu, :m, :fe, :r)"),
                  {"e": ev.evento_id, "p": h.proyecto_id, "f": h.familia, "c": h.clase, "ic": h.insumo_codigo, "q": h.cantidad,
                   "u": h.unidad, "cu": h.costo_unitario, "m": h.moneda, "fe": h.fecha, "r": h.referencia})
    return {"evento_id": ev.evento_id, "duplicado": False, "hechos": len(ev.datos.hechos)}


routers = [interno]
