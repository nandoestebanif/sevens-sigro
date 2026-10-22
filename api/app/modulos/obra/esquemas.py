"""Contratos de entrada y salida del módulo obra (Pydantic v2)."""
from __future__ import annotations

from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Rol = Literal["almacenero", "ssoma", "residente", "administrador", "gerente"]


class Estricto(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


# --- sesión
class LoginIn(Estricto):
    usuario: str = Field(min_length=1, max_length=60)
    clave: str = Field(min_length=1, max_length=200)


class UsuarioMin(BaseModel):
    id: int
    nombre: str
    usuario: str


class ObraRol(BaseModel):
    id: int
    nombre: str
    rol: Rol


class SesionOut(BaseModel):
    token: str
    tipo: str = "bearer"
    expira_en: int
    usuario: UsuarioMin
    obras: list[ObraRol]


class YoOut(BaseModel):
    usuario: UsuarioMin
    obras: list[ObraRol]


class CambiarClaveIn(Estricto):
    clave_actual: str = Field(max_length=200)
    clave_nueva: str = Field(max_length=200)


# --- usuarios
class UsuarioIn(Estricto):
    nombre: str = Field(min_length=1, max_length=120)
    usuario: str = Field(pattern=r"^[A-Za-z0-9._-]{3,40}$")
    clave: str = Field(max_length=200)


class UsuarioOut(BaseModel):
    id: int
    nombre: str
    usuario: str
    estado: str
    creado_en: datetime


# --- proyecto
class ObraIn(Estricto):
    nombre: str = Field(min_length=1, max_length=120)
    fecha_inicio: date
    fecha_fin: date
    dia_corte: int = Field(default=7, ge=1, le=7, description="ISO: 1 lunes … 7 domingo. No se edita después (D-P2).")


class ObraOut(BaseModel):
    id: int
    nombre: str
    fecha_inicio: date
    fecha_fin: date
    dia_corte: int
    estado: str
    semana: int
    semanas_totales: int
    rol: str | None = None


class AmpliarPlazoIn(Estricto):
    nueva_fecha_fin: date
    motivo: str = Field(min_length=3, max_length=500)


class PlazoHistorialOut(BaseModel):
    id: int
    fecha_fin_anterior: date
    fecha_fin_nueva: date
    motivo: str
    autor: str
    fecha: datetime


class PeriodoOut(BaseModel):
    id: int
    numero: int
    fecha_inicio: date
    fecha_fin: date
    estado: str


class AlmacenIn(Estricto):
    nombre: str = Field(min_length=1, max_length=80)
    tipo: Literal["central", "frente"] = "frente"


class AlmacenOut(BaseModel):
    id: int
    nombre: str
    tipo: str
    estado: str


# --- asignación y dispositivos
class AsignacionIn(Estricto):
    usuario_id: int = Field(ge=1)
    rol: Rol


class AsignacionOut(BaseModel):
    id: int
    usuario_id: int
    usuario_nombre: str
    usuario: str
    rol: Rol
    fecha_asignacion: datetime
    fecha_retiro: datetime | None


class DispositivoIn(Estricto):
    identificador: str = Field(pattern=r"^[A-Za-z0-9._:-]{8,128}$")


class RevocarIn(Estricto):
    motivo: str = Field(min_length=3, max_length=300)


class DispositivoOut(BaseModel):
    id: int
    usuario_id: int
    usuario_nombre: str
    identificador: str
    estado: str
    solicitado_en: datetime
    fecha_autorizacion: datetime | None
    fecha_revocacion: datetime | None
    motivo_revocacion: str | None
