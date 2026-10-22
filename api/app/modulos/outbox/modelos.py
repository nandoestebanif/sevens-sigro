"""Bandeja de salida: eventos que el microservicio de Notificaciones debe recibir (ver migración 0006)."""
from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Identity, Index, SmallInteger, Text, func, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.base import Base


class Evento(Base):
    __tablename__ = "evento"
    __table_args__ = (
        Index("ix_evento_pendientes", "siguiente_intento", "id", postgresql_where=text("enviado_en IS NULL")),
        Index("ix_evento_proyecto_id", "proyecto_id", "id"),
        {"schema": "outbox"},
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    tipo: Mapped[str] = mapped_column(Text)
    proyecto_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("obra.proyecto.id", name="fk_evento_proyecto_id_proyecto"))
    payload: Mapped[dict] = mapped_column(JSONB)
    creado_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    enviado_en: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    intentos: Mapped[int] = mapped_column(SmallInteger, server_default="0")
    siguiente_intento: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    ultimo_error: Mapped[str | None] = mapped_column(Text)
