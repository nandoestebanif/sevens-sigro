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



@interno.get("/obras/{obra_id}/costo-pagado")
def costo_pagado(obra_id: int, s: Session = Depends(obtener_sesion, scope="function")):
    """HU-33: por insumo, el promedio ponderado de lo PAGADO en esta obra, en la unidad del S10 y sin IGV: suma de
    cantidad × costo de los ingresos por compra, entre la cantidad, netos de sus reversos. Solo cuentan las recepciones
    (no los ingresos sin compra ni los traslados): es lo que se pagó a proveedores."""
    return filas(s, """
        SELECT m.insumo_id, sum(m.sentido * m.cantidad) AS cantidad,
               round(sum(m.sentido * m.cantidad * m.costo_unitario) / nullif(sum(m.sentido * m.cantidad), 0), 6) AS promedio_pagado
          FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
         WHERE m.proyecto_id = :p AND coalesce(o.tipo, m.tipo) = 'ingreso_compra'
         GROUP BY m.insumo_id HAVING sum(m.sentido * m.cantidad) > 0 ORDER BY m.insumo_id""", p=obra_id)


routers = [interno]
