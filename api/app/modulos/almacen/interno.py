"""API interna del servicio almacén: lo que compras (avance, HU-23) le pregunta sin leer el esquema `almacen`."""
from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Query
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


@interno.get("/obras/{obra_id}/saldos-valorizados")
def saldos_valorizados(obra_id: int, corte: datetime | None = Query(None), s: Session = Depends(obtener_sesion, scope="function")):
    """HU-56: por almacén e insumo, el saldo y su valor A LA FECHA DE CORTE, reconstruidos del libro (R2): solo cuentan los
    movimientos con `fecha_servidor <= corte`. Valor = Σ sentido × cantidad × costo (cada salida lleva su promedio
    congelado, R4), de modo que valor / cantidad es el costo promedio ponderado vigente en ese instante. Sin `corte`
    (o con uno futuro) es «ahora». Se omiten los pares con saldo cero. Además de las filas devuelve los almacenes de la
    obra, para que quien consulta pueda distinguir «sin stock» de «ese almacén no es de esta obra»."""
    ahora = datetime.now(timezone.utc)
    if corte is not None and corte.tzinfo is None:
        corte = corte.replace(tzinfo=timezone.utc)
    efectivo = min(corte, ahora) if corte is not None else ahora
    almacenes = filas(s, "SELECT id, nombre, tipo, estado FROM almacen.almacen_ref WHERE proyecto_id = :p ORDER BY tipo, nombre", p=obra_id)
    rows = filas(s, """
        SELECT m.almacen_id, a.nombre AS almacen, m.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad, i.tipo,
               sum(m.sentido * m.cantidad) AS cantidad,
               round(sum(m.sentido * m.cantidad * m.costo_unitario), 2) AS valor,
               round(sum(m.sentido * m.cantidad * m.costo_unitario) / nullif(sum(m.sentido * m.cantidad), 0), 6) AS costo_promedio,
               max(m.fecha_servidor) AS ultimo_movimiento_en
          FROM almacen.movimiento m
          JOIN almacen.insumo_ref i ON i.id = m.insumo_id
          JOIN almacen.almacen_ref a ON a.id = m.almacen_id
         WHERE m.proyecto_id = :p AND m.fecha_servidor <= :c
         GROUP BY m.almacen_id, a.nombre, m.insumo_id, i.codigo, i.descripcion, i.unidad, i.tipo
        HAVING sum(m.sentido * m.cantidad) <> 0
         ORDER BY a.nombre, i.codigo""", p=obra_id, c=efectivo)
    return {"corte": efectivo, "almacenes": almacenes, "filas": rows}


routers = [interno]
