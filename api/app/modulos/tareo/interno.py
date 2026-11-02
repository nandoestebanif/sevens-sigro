"""API interna del servicio tareo: costo le pregunta las horas hombre registradas (HU-58), sin leer el esquema `tareo`."""
from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import obtener_sesion
from app.internos import exigir_servicio
from app.sql import fila

interno = APIRouter(prefix="/interno", tags=["interno"], dependencies=[Depends(exigir_servicio)])


@interno.get("/obras/{obra_id}/horas")
def horas(obra_id: int, s: Session = Depends(obtener_sesion, scope="function")):
    """Horas de las jornadas vigentes de la obra, con las extra aparte, y la fecha de la última jornada (corte)."""
    return fila(s, "SELECT coalesce(sum(horas_normales), 0) AS horas_normales, coalesce(sum(horas_extra), 0) AS horas_extra, "
                   "max(fecha) AS hasta FROM tareo.jornada_vigente WHERE proyecto_id = :p", p=obra_id)


routers = [interno]
