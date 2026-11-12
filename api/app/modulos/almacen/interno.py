"""API interna del servicio almacén: lo que compras (avance, HU-23) le pregunta sin leer el esquema `almacen`."""
from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.db import obtener_sesion
from app.internos import exigir_servicio
from app.sql import fila, filas

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


@interno.get("/obras/{obra_id}/resumen-valorizado")
def resumen_valorizado(obra_id: int, corte: datetime | None = Query(None), s: Session = Depends(obtener_sesion, scope="function")):
    """HU-58: recibido, consumido y en stock de la obra A LA FECHA DE CORTE, valorizados desde el libro (sin IGV).
    `recibido` = ingresos (con y sin compra) y `consumido` = salidas, ambos netos de sus reversos (un reverso cuenta como
    el movimiento que corrige); `otros` = traslados entre obras y ajustes de conteo, que también mueven el stock.
    Siempre se cumple recibido − consumido + otros = en_stock: si no, algo del libro está mal."""
    ahora = datetime.now(timezone.utc)
    if corte is not None and corte.tzinfo is None:
        corte = corte.replace(tzinfo=timezone.utc)
    efectivo = min(corte, ahora) if corte is not None else ahora
    r = fila(s, """
        SELECT coalesce(sum(m.sentido * m.cantidad * m.costo_unitario) FILTER (WHERE coalesce(o.tipo, m.tipo) IN ('ingreso_compra', 'ingreso_sin_compra')), 0) AS recibido,
               coalesce(-sum(m.sentido * m.cantidad * m.costo_unitario) FILTER (WHERE coalesce(o.tipo, m.tipo) = 'salida'), 0) AS consumido,
               coalesce(sum(m.sentido * m.cantidad * m.costo_unitario), 0) AS en_stock,
               max(m.fecha_servidor) AS ultimo_movimiento_en
          FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
         WHERE m.proyecto_id = :p AND m.fecha_servidor <= :c""", p=obra_id, c=efectivo)
    r = {k: (round(v, 2) if k != "ultimo_movimiento_en" else v) for k, v in r.items()}
    r["otros"] = round(r["en_stock"] - r["recibido"] + r["consumido"], 2)
    r["corte"] = efectivo
    return r


@interno.get("/obras/{obra_id}/consumo-por-insumo")
def consumo_por_insumo(obra_id: int, corte: datetime | None = Query(None), s: Session = Depends(obtener_sesion, scope="function")):
    """HU-57: por insumo, lo consumido (salidas, netas de sus reversos) hasta la fecha de corte: cantidad en la unidad del
    S10 e importe al costo promedio congelado en cada salida (R4). Solo insumos con consumo distinto de cero."""
    ahora = datetime.now(timezone.utc)
    if corte is not None and corte.tzinfo is None:
        corte = corte.replace(tzinfo=timezone.utc)
    efectivo = min(corte, ahora) if corte is not None else ahora
    rows = filas(s, """
        SELECT m.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad, i.tipo,
               -sum(m.sentido * m.cantidad) AS cantidad,
               round(-sum(m.sentido * m.cantidad * m.costo_unitario), 2) AS importe
          FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
          JOIN almacen.insumo_ref i ON i.id = m.insumo_id
         WHERE m.proyecto_id = :p AND m.fecha_servidor <= :c AND coalesce(o.tipo, m.tipo) = 'salida'
         GROUP BY m.insumo_id, i.codigo, i.descripcion, i.unidad, i.tipo
        HAVING sum(m.sentido * m.cantidad) <> 0 ORDER BY i.codigo""", p=obra_id, c=efectivo)
    return {"corte": efectivo, "filas": rows}


routers = [interno]
