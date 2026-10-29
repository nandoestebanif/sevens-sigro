"""Contratos del catálogo de insumos."""
from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Tipo = Literal["material", "mano_obra", "equipo", "subcontrato"]


class Estricto(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class InsumoOut(BaseModel):
    id: int
    codigo: str
    descripcion: str
    unidad_s10: str
    unidad_compra: str
    factor_conversion: Decimal
    tipo: Tipo
    activo: bool
    verificado: bool


class InsumoIn(Estricto):
    """Alta de un insumo que no estaba en el catálogo. El código lo genera el servidor, nunca el cliente."""
    descripcion: str = Field(min_length=3, max_length=200)
    unidad: str = Field(min_length=1, max_length=20, description="Unidad en que se compra (kg, bls, m3…)")
    tipo: Tipo = "material"


class FactorIn(Estricto):
    factor: Decimal = Field(gt=0, max_digits=18, decimal_places=6)
    motivo: str = Field(min_length=3, max_length=300)


class FactorHistorialOut(BaseModel):
    id: int
    factor_anterior: Decimal
    factor_nuevo: Decimal
    motivo: str
    cambiado_por: int
    cambiado_en: datetime


class FilaS10(Estricto):
    """Una fila de lo que devolvió Integraciones (POST /s10/relacion-insumos). Cantidades y precios NO entran:
    son de la obra (HU-19), el catálogo solo comparte código, nombre y unidad entre obras."""
    codigo: str = Field(min_length=1, max_length=30)
    descripcion: str = Field(min_length=1, max_length=300)
    unidad: str = Field(min_length=1, max_length=20)
    tipo: Tipo


class ImportarS10In(Estricto):
    insumos: list[FilaS10] = Field(min_length=1, max_length=5000)


class ResultadoImportacionOut(BaseModel):
    creados: int
    actualizados: int
    sin_cambios: int
    advertencias: list[str]


# ---------------------------------------------------------------- lectura del PDF del S10 (lo devuelve Integraciones)
class InsumoLeidoS10(BaseModel):
    codigo: str
    descripcion: str
    unidad: str
    tipo: str
    cantidad: float
    precio: float
    parcial: float
    accion: Literal["crear", "actualizar"] = Field(description="Según los códigos que ya tiene el catálogo")
    advertencia: str | None


class ResumenLecturaS10(BaseModel):
    leidos: int
    a_crear: int
    a_actualizar: int
    fallidos: int
    por_tipo: dict[str, int]
    parcial_total: float


class LecturaS10Out(BaseModel):
    """Lo que Integraciones leyó del PDF, tal cual. Todavía no toca el catálogo: eso es POST /catalogo/insumos/importar-s10."""
    obra: str | None
    subpresupuesto: str | None
    fecha: str | None
    resumen: ResumenLecturaS10
    insumos: list[InsumoLeidoS10]
    fallidos: list[dict]
