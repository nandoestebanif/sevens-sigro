"""API interna del servicio catálogo: los demás servicios le piden los datos de los insumos.

Compras guarda el `insumo_id`; el código, la descripción, las unidades y el factor de conversión son
del catálogo y se piden aquí (nunca con un JOIN a una base ajena).
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.db import obtener_sesion
from app.internos import exigir_servicio
from app.sql import filas

interno = APIRouter(prefix="/interno", tags=["interno"], dependencies=[Depends(exigir_servicio)])

COLUMNAS = "id, codigo, descripcion, unidad_s10, unidad_compra, factor_conversion, tipo, activo, verificado"


class BuscarIn(BaseModel):
    ids: list[int] = Field(default_factory=list, max_length=5000)
    codigos: list[str] = Field(default_factory=list, max_length=5000)
    solo_activos: bool = False


@interno.post("/insumos/buscar")
def buscar(d: BuscarIn, s: Session = Depends(obtener_sesion, scope="function")):
    """Insumos por id o por código (sin distinguir mayúsculas). Lo que no existe simplemente no viene."""
    if not d.ids and not d.codigos:
        return []
    return filas(
        s,
        f"SELECT {COLUMNAS} FROM catalogo.insumo "
        "WHERE (id = ANY(:ids) OR upper(codigo) = ANY(:cods)) AND (NOT :act OR activo) ORDER BY codigo",
        ids=d.ids, cods=[c.upper() for c in d.codigos], act=d.solo_activos,
    )


routers = [interno]
