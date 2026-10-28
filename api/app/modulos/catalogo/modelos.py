"""Catálogo MÍNIMO y PROVISIONAL (de Fernando). Ver migraciones/versions/0002_catalogo_minimo.py."""
from __future__ import annotations

from datetime import datetime
from decimal import Decimal

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Identity, Index, Numeric, Text, UniqueConstraint, func, text
from sqlalchemy.orm import Mapped, mapped_column

from app.base import Base


class Insumo(Base):
    __tablename__ = "insumo"
    __table_args__ = (UniqueConstraint("codigo", name="uq_insumo_codigo"), {"schema": "catalogo"})

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    codigo: Mapped[str] = mapped_column(Text)
    descripcion: Mapped[str] = mapped_column(Text)
    unidad_s10: Mapped[str] = mapped_column(Text)
    unidad_compra: Mapped[str] = mapped_column(Text)
    factor_conversion: Mapped[Decimal] = mapped_column(Numeric(18, 6), server_default="1")
    tipo: Mapped[str] = mapped_column(Text)
    activo: Mapped[bool] = mapped_column(Boolean, server_default=text("true"))
    verificado: Mapped[bool] = mapped_column(Boolean, server_default=text("false"))
    creado_por: Mapped[int | None] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_insumo_creado_por_usuario"))
    creado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class InsumoNombreComercial(Base):
    __tablename__ = "insumo_nombre_comercial"
    __table_args__ = (
        Index("uq_insumo_nombre_comercial_insumo_nombre", "insumo_id", text("lower(nombre)"), unique=True),
        {"schema": "catalogo"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    insumo_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("catalogo.insumo.id", name="fk_insumo_nombre_comercial_insumo_id_insumo")
    )
    nombre: Mapped[str] = mapped_column(Text)


class InsumoFactorHistorial(Base):
    """Libro solo-agregar de los cambios del factor de conversión (HU-17, migración 0007)."""
    __tablename__ = "insumo_factor_historial"
    __table_args__ = (Index("ix_insumo_factor_historial_insumo_id", "insumo_id", "id"), {"schema": "catalogo"})

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    insumo_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("catalogo.insumo.id", name="fk_insumo_factor_historial_insumo_id_insumo")
    )
    factor_anterior: Mapped[Decimal] = mapped_column(Numeric(18, 6))
    factor_nuevo: Mapped[Decimal] = mapped_column(Numeric(18, 6))
    motivo: Mapped[str] = mapped_column(Text)
    cambiado_por: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("obra.usuario.id", name="fk_insumo_factor_historial_cambiado_por_usuario")
    )
    cambiado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
