"""API interna del servicio tareo: costo le pregunta las horas hombre registradas (HU-58), sin leer el esquema `tareo`."""
from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.db import obtener_sesion
from app.internos import exigir_servicio
from app.sql import fila

interno = APIRouter(prefix="/interno", tags=["interno"], dependencies=[Depends(exigir_servicio)])


@interno.get("/obras/{obra_id}/horas")
def horas(obra_id: int, hasta: date | None = Query(None, description="solo jornadas con fecha <= hasta"),
          s: Session = Depends(obtener_sesion, scope="function")):
    """Horas de las jornadas vigentes de la obra, con las extra aparte, y la fecha de la última jornada (corte).
    Con `hasta`, solo las jornadas hasta ese día (fecha de corte del tablero)."""
    return fila(s, "SELECT coalesce(sum(horas_normales), 0) AS horas_normales, coalesce(sum(horas_extra), 0) AS horas_extra, "
                   "max(fecha) AS hasta FROM tareo.jornada_vigente WHERE proyecto_id = :p "
                   "AND (CAST(:h AS date) IS NULL OR fecha <= CAST(:h AS date))", p=obra_id, h=hasta)


routers = [interno]
