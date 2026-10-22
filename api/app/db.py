"""Sesión de base de datos. Una transacción por petición: confirma si todo salió bien, revierte si no."""
from __future__ import annotations

from collections.abc import Iterator
from functools import lru_cache

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session

from app.config import cargar


@lru_cache(maxsize=1)
def motor() -> Engine:
    return create_engine(cargar().database_url, pool_pre_ping=True, pool_size=5, max_overflow=5)


def obtener_sesion() -> Iterator[Session]:
    with Session(motor()) as s:
        try:
            yield s
            s.commit()
        except BaseException:
            s.rollback()
            raise
