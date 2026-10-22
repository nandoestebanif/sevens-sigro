"""Módulo obra: proyecto, periodos, almacenes, usuarios, asignación y dispositivos.

La fuente de verdad del esquema son las migraciones (restricciones CHECK, disparadores y
permisos viven allí). Estos modelos describen tablas, columnas, claves e índices para que
el servicio los use, y `alembic check` falla si dejan de coincidir con la base.
"""
from __future__ import annotations

from datetime import date, datetime

from sqlalchemy import (
    BigInteger, Date, DateTime, ForeignKey, ForeignKeyConstraint, Identity, Index, Integer,
    SmallInteger, Text, UniqueConstraint, func, text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.base import Base

ROLES = ("almacenero", "ssoma", "residente", "administrador", "gerente")


def _id() -> Mapped[int]:
    return mapped_column(BigInteger, Identity(always=True), primary_key=True)


class Usuario(Base):
    __tablename__ = "usuario"
    __table_args__ = (
        Index("uq_usuario_usuario", text("lower(usuario)"), unique=True),
        {"schema": "obra"},
    )

    id: Mapped[int] = _id()
    nombre: Mapped[str] = mapped_column(Text)
    usuario: Mapped[str] = mapped_column(Text)
    hash_clave: Mapped[str] = mapped_column(Text)
    estado: Mapped[str] = mapped_column(Text, server_default="activo")
    fecha_baja: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    intentos_fallidos: Mapped[int] = mapped_column(SmallInteger, server_default="0")
    bloqueado_hasta: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    creado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Proyecto(Base):
    __tablename__ = "proyecto"
    __table_args__ = (
        Index("uq_proyecto_nombre", text("lower(nombre)"), unique=True),
        {"schema": "obra"},
    )

    id: Mapped[int] = _id()
    nombre: Mapped[str] = mapped_column(Text)
    fecha_inicio: Mapped[date] = mapped_column(Date)
    fecha_fin: Mapped[date] = mapped_column(Date)
    dia_corte: Mapped[int] = mapped_column(SmallInteger, server_default="7")
    estado: Mapped[str] = mapped_column(Text, server_default="en_ejecucion")
    creado_por: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_proyecto_creado_por_usuario"))
    creado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    periodos: Mapped[list["Periodo"]] = relationship(back_populates="proyecto", order_by="Periodo.numero")


class ProyectoPlazoHistorial(Base):
    """Append-only. Se inserta SOLO a través de obra.ampliar_plazo()."""

    __tablename__ = "proyecto_plazo_historial"
    __table_args__ = (
        Index("ix_proyecto_plazo_historial_proyecto_id", "proyecto_id", "fecha"),
        {"schema": "obra"},
    )

    id: Mapped[int] = _id()
    proyecto_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("obra.proyecto.id", name="fk_proyecto_plazo_historial_proyecto_id_proyecto")
    )
    fecha_fin_anterior: Mapped[date] = mapped_column(Date)
    fecha_fin_nueva: Mapped[date] = mapped_column(Date)
    motivo: Mapped[str] = mapped_column(Text)
    autor: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_proyecto_plazo_historial_autor_usuario"))
    fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Periodo(Base):
    """Los genera obra.generar_periodos(); la aplicación no los inserta."""

    __tablename__ = "periodo"
    __table_args__ = (
        UniqueConstraint("proyecto_id", "numero", name="uq_periodo_proyecto_id_numero"),
        UniqueConstraint("id", "proyecto_id", name="uq_periodo_id_proyecto_id"),
        {"schema": "obra"},
    )

    id: Mapped[int] = _id()
    proyecto_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.proyecto.id", name="fk_periodo_proyecto_id_proyecto"))
    numero: Mapped[int] = mapped_column(Integer)
    fecha_inicio: Mapped[date] = mapped_column(Date)
    fecha_fin: Mapped[date] = mapped_column(Date)
    estado: Mapped[str] = mapped_column(Text, server_default="abierto")
    cerrado_en: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    cerrado_por: Mapped[int | None] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_periodo_cerrado_por_usuario"))

    proyecto: Mapped[Proyecto] = relationship(back_populates="periodos")


class Almacen(Base):
    __tablename__ = "almacen"
    __table_args__ = (
        UniqueConstraint("id", "proyecto_id", name="uq_almacen_id_proyecto_id"),
        Index("uq_almacen_proyecto_nombre", "proyecto_id", text("lower(nombre)"), unique=True),
        {"schema": "obra"},
    )

    id: Mapped[int] = _id()
    proyecto_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.proyecto.id", name="fk_almacen_proyecto_id_proyecto"))
    nombre: Mapped[str] = mapped_column(Text)
    tipo: Mapped[str] = mapped_column(Text, server_default="frente")
    estado: Mapped[str] = mapped_column(Text, server_default="activo")
    creado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Asignacion(Base):
    """El rol vive AQUÍ, junto a la obra (R9), no en `usuario`."""

    __tablename__ = "asignacion"
    __table_args__ = (
        Index("uq_asignacion_vigente", "usuario_id", "proyecto_id", unique=True, postgresql_where=text("fecha_retiro IS NULL")),
        Index("ix_asignacion_proyecto_id_rol", "proyecto_id", "rol", postgresql_where=text("fecha_retiro IS NULL")),
        {"schema": "obra"},
    )

    id: Mapped[int] = _id()
    usuario_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_asignacion_usuario_id_usuario"))
    proyecto_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.proyecto.id", name="fk_asignacion_proyecto_id_proyecto"))
    rol: Mapped[str] = mapped_column(Text)
    asignado_por: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_asignacion_asignado_por_usuario"))
    fecha_asignacion: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    fecha_retiro: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    retirado_por: Mapped[int | None] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_asignacion_retirado_por_usuario"))


class Dispositivo(Base):
    __tablename__ = "dispositivo"
    __table_args__ = (
        UniqueConstraint("id", "proyecto_id", name="uq_dispositivo_id_proyecto_id"),
        Index("uq_dispositivo_activo_por_usuario", "proyecto_id", "usuario_id", unique=True, postgresql_where=text("estado = 'activo'")),
        Index("uq_dispositivo_activo_identificador", "proyecto_id", "identificador", unique=True, postgresql_where=text("estado = 'activo'")),
        {"schema": "obra"},
    )

    id: Mapped[int] = _id()
    proyecto_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.proyecto.id", name="fk_dispositivo_proyecto_id_proyecto"))
    usuario_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_dispositivo_usuario_id_usuario"))
    identificador: Mapped[str] = mapped_column(Text)
    estado: Mapped[str] = mapped_column(Text, server_default="pendiente")
    solicitado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    autorizado_por: Mapped[int | None] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_dispositivo_autorizado_por_usuario"))
    fecha_autorizacion: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    revocado_por: Mapped[int | None] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_dispositivo_revocado_por_usuario"))
    fecha_revocacion: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    motivo_revocacion: Mapped[str | None] = mapped_column(Text)
