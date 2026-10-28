"""Módulo compras: proveedor, compra, detalle, adjuntos y solicitud de materiales.

Registrar una compra NO mueve stock: este módulo no conoce los movimientos. El estado de
una solicitud no se guarda: se deriva en la vista `compras.solicitud_estado`.
"""
from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import (
    BigInteger, CHAR, Date, DateTime, ForeignKey, ForeignKeyConstraint, Identity, Index, Integer,
    Numeric, Text, UniqueConstraint, func, text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.base import Base


class Proveedor(Base):
    __tablename__ = "proveedor"
    __table_args__ = (
        UniqueConstraint("ruc", name="uq_proveedor_ruc"),
        Index("ix_proveedor_razon_social", text("lower(razon_social)"), postgresql_ops={"lower(razon_social)": "text_pattern_ops"}),
        {"schema": "compras"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    ruc: Mapped[str] = mapped_column(Text)
    razon_social: Mapped[str] = mapped_column(Text)
    estado_validacion: Mapped[str] = mapped_column(Text, server_default="sin_validar")
    validado_en: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    estado: Mapped[str] = mapped_column(Text, server_default="activo")
    fecha_baja: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    creado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Compra(Base):
    __tablename__ = "compra"
    __table_args__ = (
        UniqueConstraint("proveedor_id", "tipo_comprobante", "numero_comprobante",
                         name="uq_compra_proveedor_id_tipo_comprobante_numero_comprobante"),
        UniqueConstraint("id", "proyecto_id", name="uq_compra_id_proyecto_id"),
        ForeignKeyConstraint(["solicitud_id", "proyecto_id"], ["compras.solicitud.id", "compras.solicitud.proyecto_id"],
                             name="fk_compra_solicitud_id_solicitud"),
        Index("ix_compra_proyecto_id_fecha", "proyecto_id", text("fecha DESC")),
        Index("ix_compra_proveedor_id", "proveedor_id"),
        Index("ix_compra_solicitud_id", "solicitud_id", postgresql_where=text("solicitud_id IS NOT NULL")),
        {"schema": "compras"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    proyecto_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.proyecto.id", name="fk_compra_proyecto_id_proyecto"))
    proveedor_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("compras.proveedor.id", name="fk_compra_proveedor_id_proveedor"))
    tipo_comprobante: Mapped[str] = mapped_column(Text)
    numero_comprobante: Mapped[str] = mapped_column(Text)
    fecha: Mapped[date] = mapped_column(Date)
    moneda: Mapped[str] = mapped_column(Text, server_default="PEN")
    base_imponible: Mapped[Decimal] = mapped_column(Numeric(14, 2))
    igv: Mapped[Decimal] = mapped_column(Numeric(14, 2), server_default="0")
    total: Mapped[Decimal] = mapped_column(Numeric(14, 2))
    estado: Mapped[str] = mapped_column(Text, server_default="registrada")
    motivo_anulacion: Mapped[str | None] = mapped_column(Text)
    anulada_por: Mapped[int | None] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_compra_anulada_por_usuario"))
    anulada_en: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    registrado_por: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_compra_registrado_por_usuario"))
    creado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    solicitud_id: Mapped[int | None] = mapped_column(BigInteger)   # opcional: una compra puede nacer sin solicitud


class CompraDetalle(Base):
    __tablename__ = "compra_detalle"
    __table_args__ = (
        UniqueConstraint("compra_id", "insumo_id", name="uq_compra_detalle_compra_id_insumo_id"),
        Index("ix_compra_detalle_insumo_id", "insumo_id"),
        {"schema": "compras"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    compra_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("compras.compra.id", name="fk_compra_detalle_compra_id_compra"))
    insumo_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("catalogo.insumo.id", name="fk_compra_detalle_insumo_id_insumo"))
    cantidad: Mapped[Decimal] = mapped_column(Numeric(18, 4))
    unidad: Mapped[str] = mapped_column(Text)
    precio_unitario: Mapped[Decimal] = mapped_column(Numeric(18, 4))


class CompraAdjunto(Base):
    __tablename__ = "compra_adjunto"
    __table_args__ = (UniqueConstraint("compra_id", "sha256", name="uq_compra_adjunto_compra_id_sha256"), {"schema": "compras"})

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    compra_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("compras.compra.id", name="fk_compra_adjunto_compra_id_compra"))
    archivo: Mapped[str] = mapped_column(Text)
    nombre_original: Mapped[str] = mapped_column(Text)
    tipo_mime: Mapped[str] = mapped_column(Text)
    tamano_bytes: Mapped[int] = mapped_column(BigInteger)
    sha256: Mapped[str] = mapped_column(CHAR(64))
    subido_por: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_compra_adjunto_subido_por_usuario"))
    subido_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class SolicitudCorrelativo(Base):
    """Solo la función compras.tg_solicitud_numero() lo toca (SECURITY DEFINER)."""

    __tablename__ = "solicitud_correlativo"
    __table_args__ = {"schema": "compras"}

    proyecto_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("obra.proyecto.id", name="fk_solicitud_correlativo_proyecto_id_proyecto"), primary_key=True
    )
    ultimo: Mapped[int] = mapped_column(Integer, server_default="0")


class Solicitud(Base):
    __tablename__ = "solicitud"
    __table_args__ = (
        UniqueConstraint("proyecto_id", "numero", name="uq_solicitud_proyecto_id_numero"),
        UniqueConstraint("id", "proyecto_id", name="uq_solicitud_id_proyecto_id"),
        {"schema": "compras"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    proyecto_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.proyecto.id", name="fk_solicitud_proyecto_id_proyecto"))
    numero: Mapped[int] = mapped_column(Integer)          # lo asigna el servidor (correlativo por obra)
    fecha: Mapped[date] = mapped_column(Date, server_default=func.current_date())
    estado: Mapped[str] = mapped_column(Text, server_default="emitida")   # emitida | anulada (NO el avance)
    motivo_anulacion: Mapped[str | None] = mapped_column(Text)
    anulada_por: Mapped[int | None] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_solicitud_anulada_por_usuario"))
    anulada_en: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    emitida_por: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_solicitud_emitida_por_usuario"))
    creado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class SolicitudLinea(Base):
    __tablename__ = "solicitud_linea"
    __table_args__ = (
        Index("uq_solicitud_linea_solicitud_insumo", "solicitud_id", "insumo_id", unique=True,
              postgresql_where=text("insumo_id IS NOT NULL")),
        Index("ix_solicitud_linea_insumo_id", "insumo_id", postgresql_where=text("insumo_id IS NOT NULL")),
        {"schema": "compras"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    solicitud_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("compras.solicitud.id", name="fk_solicitud_linea_solicitud_id_solicitud"))
    insumo_id: Mapped[int | None] = mapped_column(BigInteger, ForeignKey("catalogo.insumo.id", name="fk_solicitud_linea_insumo_id_insumo"))
    descripcion_libre: Mapped[str | None] = mapped_column(Text)
    cantidad: Mapped[Decimal] = mapped_column(Numeric(18, 4))      # en unidad de compra
    unidad_compra: Mapped[str] = mapped_column(Text)


class Requerimiento(Base):
    """Lo planificado en el S10 para una obra (HU-19, migración 0008). La cantidad solo cambia con un ajuste motivado."""
    __tablename__ = "requerimiento"
    __table_args__ = (
        UniqueConstraint("proyecto_id", "insumo_id", name="uq_requerimiento_proyecto_id_insumo_id"),
        Index("ix_requerimiento_insumo_id", "insumo_id"),
        {"schema": "compras"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    proyecto_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.proyecto.id", name="fk_requerimiento_proyecto_id_proyecto"))
    insumo_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("catalogo.insumo.id", name="fk_requerimiento_insumo_id_insumo"))
    cantidad_planificada: Mapped[Decimal] = mapped_column(Numeric(18, 4))
    precio_planificado: Mapped[Decimal] = mapped_column(Numeric(18, 4))
    creado_por: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_requerimiento_creado_por_usuario"))
    creado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class RequerimientoAjuste(Base):
    __tablename__ = "requerimiento_ajuste"
    __table_args__ = (Index("ix_requerimiento_ajuste_requerimiento_id", "requerimiento_id", "id"), {"schema": "compras"})

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    requerimiento_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("compras.requerimiento.id", name="fk_requerimiento_ajuste_requerimiento_id_requerimiento")
    )
    cantidad_anterior: Mapped[Decimal] = mapped_column(Numeric(18, 4))
    cantidad_nueva: Mapped[Decimal] = mapped_column(Numeric(18, 4))
    motivo: Mapped[str] = mapped_column(Text)
    ajustado_por: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_requerimiento_ajuste_ajustado_por_usuario"))
    ajustado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class ParametroReposicion(Base):
    """Stock de seguridad y punto de reposición con vigencias: cambiar = fila nueva (HU-25, migración 0008)."""
    __tablename__ = "parametro_reposicion"
    __table_args__ = (
        Index("ix_parametro_reposicion_vigencia", "proyecto_id", "insumo_id", text("id DESC")),
        {"schema": "compras"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    proyecto_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.proyecto.id", name="fk_parametro_reposicion_proyecto_id_proyecto"))
    insumo_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("catalogo.insumo.id", name="fk_parametro_reposicion_insumo_id_insumo"))
    stock_seguridad: Mapped[Decimal] = mapped_column(Numeric(18, 4))
    punto_reposicion: Mapped[Decimal] = mapped_column(Numeric(18, 4))
    registrado_por: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.usuario.id", name="fk_parametro_reposicion_registrado_por_usuario"))
    vigente_desde: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
