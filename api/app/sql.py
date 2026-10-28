"""Ayudas mínimas para SQL con nombre: cada servicio las usa sobre SU base, nunca sobre la de otro."""
from __future__ import annotations

from sqlalchemy import text
from sqlalchemy.orm import Session


def filas(s: Session, sql: str, /, **p) -> list[dict]:
    return [dict(r) for r in s.execute(text(sql), p).mappings().all()]


def fila(s: Session, sql: str, /, **p) -> dict | None:
    r = s.execute(text(sql), p).mappings().first()
    return dict(r) if r else None
