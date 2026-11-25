"""Contratos del módulo tareo."""
from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator

Categoria = Literal["operario", "oficial", "peon"]
EstadoJornada = Literal["presente", "ausente", "permiso"]


class Estricto(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


def _limpio(t: str | None) -> str | None:
    """Sin espacios de más; vacío cuenta como no escrito."""
    return " ".join(t.split()) or None if t else None


class TrabajadorIn(Estricto):
    """Nombres y apellidos por separado (0020). La app 1.0.0 todavía manda solo «nombre»: se sigue aceptando."""
    nombres: str | None = Field(None, min_length=2, max_length=80)
    apellido_paterno: str | None = Field(None, min_length=1, max_length=60)
    apellido_materno: str | None = Field(None, max_length=60)
    nombre: str | None = Field(None, min_length=3, max_length=120)
    tipo_documento: Literal["DNI", "CE", "PASAPORTE"] = "DNI"
    documento: str = Field(min_length=6, max_length=20)
    categoria: Categoria
    cuadrilla: str = Field(min_length=1, max_length=60)
    fecha_ingreso: date

    @model_validator(mode="after")
    def _nombre_completo(self) -> "TrabajadorIn":
        self.nombres, self.apellido_paterno, self.apellido_materno = (
            _limpio(self.nombres), _limpio(self.apellido_paterno), _limpio(self.apellido_materno),
        )
        if self.nombres or self.apellido_paterno:
            if not (self.nombres and self.apellido_paterno):
                raise ValueError("Escribe los nombres y al menos el apellido paterno.")
            apellidos = " ".join(x for x in (self.apellido_paterno, self.apellido_materno) if x)
            self.nombre = f"{apellidos}, {self.nombres}"
        elif not self.nombre:
            raise ValueError("Escribe los nombres y apellidos del trabajador.")
        return self


class TrabajadorEditarIn(Estricto):
    """Lo que se corrige de un trabajador (25/11). Documento y fecha de ingreso NO: los protege la base (SIGRO-T5).
    La categoría puede cambiar (ascenso): las jornadas pasadas conservan la suya (D-T1)."""
    nombres: str | None = Field(None, min_length=2, max_length=80)
    apellido_paterno: str | None = Field(None, min_length=1, max_length=60)
    apellido_materno: str | None = Field(None, max_length=60)
    categoria: Categoria | None = None
    cuadrilla: str | None = Field(None, min_length=1, max_length=60)

    @model_validator(mode="after")
    def _algo_y_completo(self) -> "TrabajadorEditarIn":
        self.nombres, self.apellido_paterno, self.apellido_materno = (
            _limpio(self.nombres), _limpio(self.apellido_paterno), _limpio(self.apellido_materno),
        )
        if (self.nombres is None) != (self.apellido_paterno is None):
            raise ValueError("Para corregir el nombre envía los nombres y al menos el apellido paterno.")
        if not any((self.nombres, self.categoria, self.cuadrilla)):
            raise ValueError("Envía al menos el nombre, la categoría o la cuadrilla.")
        return self

    @property
    def nombre(self) -> str | None:
        if not self.nombres:
            return None
        apellidos = " ".join(x for x in (self.apellido_paterno, self.apellido_materno) if x)
        return f"{apellidos}, {self.nombres}"


class TrabajadorOut(BaseModel):
    id: int
    nombre: str
    nombres: str | None = None
    apellido_paterno: str | None = None
    apellido_materno: str | None = None
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
    ultima_jornada: date | None = Field(None, description="HU-64: fecha de la última jornada considerada en la suma")


class DniReniecOut(BaseModel):
    """Lo que RENIEC dice de un DNI (vía integraciones), para llenar el registro del trabajador sin errores."""
    dni: str
    nombres: str
    apellido_paterno: str
    apellido_materno: str
