"""Contratos del módulo compras. Los nombres siguen el modelo (snake_case); el cliente web los mapea."""
from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class Estricto(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


# --- proveedores (HU-29 · HU-30 · HU-31)
class ProveedorIn(Estricto):
    ruc: str = Field(
        pattern=r"^(\d{11}|\d{8})$",
        description="RUC (11 dígitos) o DNI (8, persona natural; queda sin validar). Único en todo el sistema (HU-30, D-C5).",
    )
    razon_social: str = Field(min_length=3, max_length=200)


class ProveedorOut(BaseModel):
    id: int
    ruc: str
    razon_social: str
    estado: Literal["validado", "sin_validar", "de_baja"]
    compras_registradas: int
    ultima_compra: date | None
    advertencia: str | None = None


# --- compras (HU-32)
class LineaCompraIn(Estricto):
    insumo_codigo: str = Field(min_length=1, max_length=30)
    cantidad: Decimal = Field(gt=0, max_digits=18, decimal_places=4)
    unidad_compra: str = Field(min_length=1, max_length=30)
    precio_unitario: Decimal = Field(gt=0, max_digits=18, decimal_places=4)


class CompraIn(Estricto):
    proveedor_id: int = Field(ge=1)
    tipo_comprobante: Literal["factura", "boleta", "nota_venta", "otro"]
    numero_comprobante: str = Field(pattern=r"^[A-Za-z0-9]{1,4}-[0-9]{1,10}$", description="Serie-número, p. ej. F001-4512.")
    fecha: date
    moneda: Literal["PEN", "USD"] = "PEN"
    precios_incluyen_igv: bool = Field(True, description="Los precios de línea ya traen IGV (18 %), como en la factura.")
    solicitud_id: int | None = Field(None, ge=1)
    lineas: list[LineaCompraIn] = Field(min_length=1, max_length=200)

    @model_validator(mode="after")
    def _sin_insumos_repetidos(self):
        codigos = [l.insumo_codigo.upper() for l in self.lineas]
        repetidos = sorted({c for c in codigos if codigos.count(c) > 1})
        if repetidos:
            raise ValueError(f"insumo repetido en la compra: {', '.join(repetidos)}; suma las cantidades en una sola línea")
        return self


class LineaCompraOut(BaseModel):
    insumo_codigo: str
    descripcion: str
    cantidad: Decimal
    unidad_compra: str
    precio_unitario: Decimal


class TotalesOut(BaseModel):
    base: Decimal
    igv: Decimal
    total: Decimal


class CompraOut(BaseModel):
    id: int
    proveedor_id: int
    proveedor_razon_social: str
    tipo_comprobante: str
    numero_comprobante: str
    fecha: date
    moneda: str
    estado: Literal["registrada", "anulada"]
    motivo_anulacion: str | None
    solicitud_id: int | None
    totales: TotalesOut
    lineas: list[LineaCompraOut]
    registrado_por: int
    creado_en: datetime


class AnularIn(Estricto):
    motivo: str = Field(min_length=5, max_length=300)


# --- solicitud de materiales (CR-04)
class LineaSolicitudIn(Estricto):
    insumo_codigo: str | None = Field(None, max_length=30)
    descripcion_libre: str | None = Field(None, max_length=200)
    cantidad: Decimal = Field(gt=0, max_digits=18, decimal_places=4)
    unidad_compra: str = Field(min_length=1, max_length=30)

    @model_validator(mode="after")
    def _algo_que_describa(self):
        if not self.insumo_codigo and not (self.descripcion_libre and self.descripcion_libre.strip()):
            raise ValueError("cada línea necesita un insumo del catálogo o una descripción")
        return self


class SolicitudIn(Estricto):
    lineas: list[LineaSolicitudIn] = Field(min_length=1, max_length=200)


class LineaSolicitudOut(BaseModel):
    insumo_codigo: str | None
    descripcion: str
    unidad_compra: str
    cantidad_solicitada: Decimal
    cantidad_comprada: Decimal
    cantidad_pendiente: Decimal


class SolicitudOut(BaseModel):
    id: int
    numero: int
    fecha: date
    estado: Literal["solicitada", "comprada_en_parte", "comprada", "anulada"]
    motivo_anulacion: str | None
    emitida_por: int
    lineas: list[LineaSolicitudOut]
