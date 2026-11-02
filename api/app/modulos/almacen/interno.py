"""API interna del servicio almacén: lo que compras (avance, HU-23) le pregunta sin leer el esquema `almacen`."""
from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import obtener_sesion
from app.internos import exigir_servicio
from app.sql import filas

interno = APIRouter(prefix="/interno", tags=["interno"], dependencies=[Depends(exigir_servicio)])


@interno.get("/obras/{obra_id}/recibido-consumido")
def recibido_consumido(obra_id: int, s: Session = Depends(obtener_sesion, scope="function")):
    """Por insumo, en la unidad del S10: lo recibido de compras y lo consumido por salidas, netos de sus reversos."""
    return filas(s, """
        SELECT m.insumo_id,
               coalesce(sum(m.sentido * m.cantidad) FILTER (WHERE coalesce(o.tipo, m.tipo) = 'ingreso_compra'), 0) AS recibido,
               coalesce(-sum(m.sentido * m.cantidad) FILTER (WHERE coalesce(o.tipo, m.tipo) = 'salida'), 0)        AS consumido
          FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
         WHERE m.proyecto_id = :p GROUP BY m.insumo_id ORDER BY m.insumo_id""", p=obra_id)


routers = [interno]
