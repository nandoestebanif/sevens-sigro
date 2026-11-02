"""Contratos de la API de almacén. Cantidades como texto decimal (como el resto de la API); snake_case."""
from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.modulos.almacen.dominio import ORIGENES

Cantidad = Field(gt=0, max_digits=18, decimal_places=4)
Texto = Field(min_length=1, max_length=300)


class Estricto(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class DesdeDispositivo(Estricto):
    uuid_cliente: UUID                                   # regla 3: clave de idempotencia nacida en el dispositivo
    hora_dispositivo_ms: int | None = Field(None, ge=0)  # HU-37: solo informativa; la que vale es la del servidor
    dispositivo: str | None = Field(None, min_length=8, max_length=128)   # regla 5


class SalidaIn(DesdeDispositivo):
    """El contrato que ya usa la app (android/LEEME.md) más `almacen_id`."""
    almacen_id: int | None = Field(None, ge=1)           # opcional solo si la obra tiene un único almacén activo
    insumo_id: int = Field(ge=1)
    cantidad: Decimal = Cantidad
    unidad: str | None = Field(None, max_length=20)      # None = unidad del S10; o su unidad de compra (se convierte)
    motivo: str = Texto
    quien_retira: str = Field(min_length=1, max_length=120)
    frente: str | None = Field(None, max_length=120)
    dispositivo: str = Field(min_length=8, max_length=128)  # la salida solo se registra desde un celular autorizado


class IngresoIn(DesdeDispositivo):
    """HU-41: ingreso que no viene de una compra."""
    almacen_id: int | None = Field(None, ge=1)
    insumo_id: int = Field(ge=1)
    cantidad: Decimal = Cantidad
    unidad: str | None = Field(None, max_length=20)
    costo_unitario: Decimal = Field(gt=0, max_digits=18, decimal_places=6)   # por unidad del S10, sin IGV
    origen: Literal[ORIGENES]  # type: ignore[valid-type]
    origen_detalle: str | None = Field(None, max_length=300)                 # «otro» y «saldo de otra obra» lo exigen

    @model_validator(mode="after")
    def _detalle(self):
        if self.origen in ("otro", "saldo_otra_obra") and not (self.origen_detalle or "").strip():
            raise ValueError("Ese origen exige su detalle (para «saldo de otra obra», el nombre de la obra).")
        return self


class LineaCantidad(Estricto):
    insumo_id: int = Field(ge=1)
    cantidad: Decimal = Cantidad


class RecepcionIn(DesdeDispositivo):
    """HU-35. Sin líneas = recepción TOTAL de lo pendiente (el caso habitual en SEVENS). Cantidades en unidad de compra."""
    compra_id: int = Field(ge=1)
    almacen_id: int | None = Field(None, ge=1)
    lineas: list[LineaCantidad] | None = Field(None, min_length=1, max_length=500)
    motivo_exceso: str | None = Field(None, max_length=300)


class TrasladoIn(DesdeDispositivo):
    """HU-42. Destino: otro almacén de esta obra u otra obra (donde quien registra también esté asignado, regla 9)."""
    origen_almacen_id: int | None = Field(None, ge=1)
    destino_obra_id: int | None = Field(None, ge=1)      # None = esta misma obra
    destino_almacen_id: int = Field(ge=1)
    motivo: str = Texto
    lineas: list[LineaCantidad] = Field(min_length=1, max_length=500)

    @model_validator(mode="after")
    def _sin_repetir(self):
        if len({l.insumo_id for l in self.lineas}) != len(self.lineas):
            raise ValueError("Un insumo aparece dos veces en el traslado.")
        return self


class AnularIn(DesdeDispositivo):
    motivo: str = Field(min_length=3, max_length=300)


class ReversoIn(AnularIn):
    """HU-45: la corrección es un movimiento inverso que apunta al original, con autor, fecha y motivo."""


class ConteoIn(DesdeDispositivo):
    almacen_id: int | None = Field(None, ge=1)
    fecha: date


class ConteoLineaIn(Estricto):
    insumo_id: int = Field(ge=1)
    cantidad_contada: Decimal = Field(ge=0, max_digits=18, decimal_places=4)
    motivo: str | None = Field(None, max_length=300)


# ------------------------------------------------------------------ salidas
class MovimientoOut(BaseModel):
    id: int
    uuid_cliente: UUID
    tipo: str
    sentido: int
    proyecto_id: int
    almacen_id: int
    insumo_id: int
    insumo_codigo: str | None = None
    unidad: str | None = None
    cantidad: Decimal
    costo_unitario: Decimal          # en salidas: el promedio vigente en ese instante, congelado (regla 4)
    importe: Decimal
    moneda: str
    motivo: str | None
    quien_retira: str | None
    frente: str | None
    origen: str | None
    origen_detalle: str | None
    recepcion_id: int | None
    traslado_id: int | None
    conteo_id: int | None
    revierte_a_id: int | None
    periodo_id: int | None
    dispositivo_id: int | None
    registrado_por: int
    fecha_dispositivo: datetime | None
    fecha_servidor: datetime
    saldo: Decimal | None = None     # saldo del insumo en ese almacén DESPUÉS de este movimiento (lo calcula el servidor)


class OperacionOut(BaseModel):
    id: int
    uuid_cliente: UUID
    movimientos: list[MovimientoOut]


class RecepcionLineaOut(BaseModel):
    insumo_id: int
    unidad_compra: str
    pedido: Decimal
    recibido: Decimal
    pendiente: Decimal


class RecepcionOut(OperacionOut):
    compra_id: int
    lineas: list[RecepcionLineaOut]
    advertencias: list[str] = []


class StockFilaOut(BaseModel):
    almacen_id: int | None
    almacen: str | None
    insumo_id: int
    insumo_codigo: str
    descripcion: str
    unidad: str
    cantidad: Decimal
    costo_promedio: Decimal | None
    valor: Decimal
    ultimo_movimiento_en: datetime
    punto_reposicion: Decimal | None = None     # en unidad de compra (HU-25)
    bajo_punto_reposicion: bool | None = None   # None: compras no respondió o el insumo no tiene parámetro


class StockOut(BaseModel):
    criterio: str
    corte: datetime | None
    por_almacen: list[StockFilaOut]
    consolidado: list[StockFilaOut]
    advertencias: list[str] = []


class KardexFilaOut(MovimientoOut):
    revertido_por_id: int | None
    saldo_despues: Decimal
    promedio_despues: Decimal | None


class ConteoLineaOut(BaseModel):
    insumo_id: int
    saldo_teorico: Decimal
    cantidad_contada: Decimal
    diferencia: Decimal
    motivo: str | None


class ConteoOut(BaseModel):
    id: int
    uuid_cliente: UUID
    almacen_id: int
    fecha: date
    estado: str
    registrado_por: int
    confirmado_por: int | None
    confirmado_en: datetime | None
    lineas: list[ConteoLineaOut]
    ajustes: list[MovimientoOut] = []
