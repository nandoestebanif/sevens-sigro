"""Módulo tareo: trabajador y jornada.

`Jornada` es un libro: no se modifica ni se borra. Corregir = insertar otra con
`corrige_a_id`. La jornada VIGENTE de un día es la última de su cadena (vista
`tareo.jornada_vigente`).
"""
from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from uuid import UUID

from sqlalchemy import (
    BigInteger, Date, DateTime, ForeignKey, ForeignKeyConstraint, Identity, Index, Numeric, Text,
    UniqueConstraint, func, text,
)
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.base import Base


class Trabajador(Base):
    __tablename__ = "trabajador"
    __table_args__ = (
        UniqueConstraint("proyecto_id", "documento", name="uq_trabajador_proyecto_id_documento"),
        UniqueConstraint("id", "proyecto_id", name="uq_trabajador_id_proyecto_id"),
        Index("ix_trabajador_proyecto_id_cuadrilla", "proyecto_id", "cuadrilla"),
        {"schema": "tareo"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    proyecto_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.proyecto.id", name="fk_trabajador_proyecto_id_proyecto"))
    nombre: Mapped[str] = mapped_column(Text)
    tipo_documento: Mapped[str] = mapped_column(Text, server_default="DNI")
    documento: Mapped[str] = mapped_column(Text)
    categoria: Mapped[str] = mapped_column(Text)
    cuadrilla: Mapped[str] = mapped_column(Text)
    fecha_ingreso: Mapped[date] = mapped_column(Date)
    fecha_salida: Mapped[date | None] = mapped_column(Date)
    registrado_por: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_trabajador_registrado_por_usuario"))
    creado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Jornada(Base):
    __tablename__ = "jornada"
    __table_args__ = (
        ForeignKeyConstraint(["trabajador_id", "proyecto_id"], ["tareo.trabajador.id", "tareo.trabajador.proyecto_id"],
                             name="fk_jornada_trabajador_id_trabajador"),
        ForeignKeyConstraint(["periodo_id", "proyecto_id"], ["obra.periodo.id", "obra.periodo.proyecto_id"],
                             name="fk_jornada_periodo_id_periodo"),
        ForeignKeyConstraint(["corrige_a_id", "trabajador_id", "fecha"],
                             ["tareo.jornada.id", "tareo.jornada.trabajador_id", "tareo.jornada.fecha"],
                             name="fk_jornada_corrige_a_id_jornada"),
        UniqueConstraint("id", "trabajador_id", "fecha", name="uq_jornada_id_trabajador_fecha"),
        Index("uq_jornada_original", "trabajador_id", "fecha", unique=True, postgresql_where=text("corrige_a_id IS NULL")),
        Index("uq_jornada_correccion_unica", "corrige_a_id", unique=True, postgresql_where=text("corrige_a_id IS NOT NULL")),
        Index("uq_jornada_uuid_cliente", "uuid_cliente", unique=True, postgresql_where=text("uuid_cliente IS NOT NULL")),
        Index("ix_jornada_proyecto_id_fecha", "proyecto_id", "fecha"),
        Index("ix_jornada_periodo_id", "periodo_id"),
        {"schema": "tareo"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    uuid_cliente: Mapped[UUID | None] = mapped_column(PG_UUID(as_uuid=True))
    proyecto_id: Mapped[int] = mapped_column(BigInteger)
    trabajador_id: Mapped[int] = mapped_column(BigInteger)
    fecha: Mapped[date] = mapped_column(Date)
    estado: Mapped[str] = mapped_column(Text)
    horas_normales: Mapped[Decimal] = mapped_column(Numeric(4, 2), server_default="0")
    horas_extra: Mapped[Decimal] = mapped_column(Numeric(4, 2), server_default="0")
    categoria: Mapped[str] = mapped_column(Text)       # la fija el servidor desde el trabajador
    periodo_id: Mapped[int] = mapped_column(BigInteger)  # lo fija el servidor desde la fecha
    registrado_por: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_jornada_registrado_por_usuario"))
    fecha_servidor: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    corrige_a_id: Mapped[int | None] = mapped_column(BigInteger)
    motivo_correccion: Mapped[str | None] = mapped_column(Text)
