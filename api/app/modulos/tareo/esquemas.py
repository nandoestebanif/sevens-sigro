"""Contratos del módulo tareo."""
from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

Categoria = Literal["operario", "oficial", "peon"]
EstadoJornada = Literal["presente", "ausente", "permiso"]


class Estricto(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class TrabajadorIn(Estricto):
    nombre: str = Field(min_length=3, max_length=120)
    tipo_documento: Literal["DNI", "CE", "PASAPORTE"] = "DNI"
    documento: str = Field(min_length=6, max_length=20)
    categoria: Categoria
    cuadrilla: str = Field(min_length=1, max_length=60)
    fecha_ingreso: date


class TrabajadorOut(BaseModel):
    id: int
    nombre: str
    tipo_documento: str
    documento: str
    categoria: str
    cuadrilla: str
    fecha_ingreso: date
    fecha_salida: date | None


class BajaIn(Estricto):
    fecha_salida: date


class JornadaIn(Estricto):
    trabajador_id: int = Field(ge=1)
    fecha: date
    estado: EstadoJornada
    horas_normales: Decimal = Field(default=Decimal("0"), ge=0, le=24, max_digits=4, decimal_places=2)
    horas_extra: Decimal = Field(default=Decimal("0"), ge=0, le=24, max_digits=4, decimal_places=2)
    uuid_cliente: UUID | None = Field(default=None, description="Idempotencia: un reenvío con el mismo uuid no duplica (R3).")


class CorreccionIn(Estricto):
    estado: EstadoJornada
    horas_normales: Decimal = Field(default=Decimal("0"), ge=0, le=24, max_digits=4, decimal_places=2)
    horas_extra: Decimal = Field(default=Decimal("0"), ge=0, le=24, max_digits=4, decimal_places=2)
    motivo: str = Field(min_length=3, max_length=300)


class JornadaOut(BaseModel):
    id: int
    uuid_cliente: UUID | None
    trabajador_id: int
    fecha: date
    estado: str
    horas_normales: Decimal
    horas_extra: Decimal
    categoria: str
    periodo_id: int
    registrado_por: int
    fecha_servidor: datetime
    corrige_a_id: int | None
    motivo_correccion: str | None


class HorasOut(BaseModel):
    periodo_id: int
    periodo_numero: int
    trabajador_id: int
    trabajador: str
    cuadrilla: str
    categoria: str
    dias_presente: int
    dias_ausente: int
    dias_permiso: int
    horas_normales: Decimal
    horas_extra: Decimal
