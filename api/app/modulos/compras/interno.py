"""API interna del servicio compras: lo que almacén y costo le preguntan (migración 0010).

Almacén registra la recepción (HU-35) y necesita saber qué se compró, a cuánto y en qué unidad; costo informa lo
comprado (HU-58). Ninguno lee el esquema `compras`: preguntan aquí, con el token de servicio.
"""
from __future__ import annotations

from decimal import Decimal

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import obtener_sesion
from app.errores import ErrorSigro
from app.internos import exigir_servicio
from app.sql import fila, filas

interno = APIRouter(prefix="/interno", tags=["interno"], dependencies=[Depends(exigir_servicio)])


@interno.get("/compras/{compra_id}")
def compra(compra_id: int, s: Session = Depends(obtener_sesion, scope="function")):
    """Cabecera y líneas. `factor_sin_igv` = base imponible / suma de líneas: lleva el precio de cada línea a su valor
    sin IGV tanto si los precios lo incluían como si no (D-C3). El IGV recuperable no es costo del insumo (NIC 2)."""
    c = fila(s, "SELECT id, proyecto_id, estado, moneda, fecha, base_imponible, igv, total FROM compras.compra WHERE id = :i", i=compra_id)
    if c is None:
        raise ErrorSigro(404, "compra_no_existe", "Esa compra no existe.")
    c["lineas"] = filas(s, "SELECT insumo_id, cantidad, unidad, precio_unitario FROM compras.compra_detalle "
                           "WHERE compra_id = :i ORDER BY id", i=compra_id)
    bruto = sum((l["cantidad"] * l["precio_unitario"] for l in c["lineas"]), Decimal(0))
    c["factor_sin_igv"] = (c["base_imponible"] / bruto) if bruto else Decimal(1)
    return c


@interno.get("/obras/{obra_id}/comprado")
def comprado(obra_id: int, s: Session = Depends(obtener_sesion, scope="function")):
    """HU-58 «Comprado»: valor de las compras registradas (las anuladas no cuentan, D-R2), sin y con IGV."""
    return fila(s, "SELECT count(*) AS compras, coalesce(sum(base_imponible), 0) AS base_imponible, coalesce(sum(igv), 0) AS igv, "
                   "coalesce(sum(total), 0) AS total, max(fecha) AS hasta, max(creado_en) AS corte "
                   "FROM compras.compra WHERE proyecto_id = :p AND estado = 'registrada'", p=obra_id)


@interno.get("/obras/{obra_id}/parametros-reposicion")
def parametros(obra_id: int, s: Session = Depends(obtener_sesion, scope="function")):
    """HU-43: el punto de reposición vigente de cada insumo (en unidad de compra), para señalar lo que está por debajo."""
    return filas(s, "SELECT insumo_id, stock_seguridad, punto_reposicion FROM compras.parametro_reposicion_vigente "
                    "WHERE proyecto_id = :p", p=obra_id)


routers = [interno]
