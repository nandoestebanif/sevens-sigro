"""Validación de lo que el administrador pasa por línea de comandos a las semillas.

Las semillas no se exponen por HTTP, pero igual revisan su entrada (Sonar pythonsecurity:S8706/S8707):
- el archivo debe existir, ser un archivo normal .json y no pesar más de 20 MB (nada de rutas a carpetas,
  dispositivos o archivos del sistema);
- la conexión debe ser a PostgreSQL con psycopg y tener servidor y base (nada de otros motores u opciones raras).
"""
from __future__ import annotations

import argparse
from pathlib import Path

from sqlalchemy.engine import make_url
from sqlalchemy.exc import ArgumentError

MAX_BYTES = 20 * 1024 * 1024
EXTENSIONES = {".json"}


def archivo_de_datos(texto: str) -> Path:
    ruta = Path(texto).expanduser().resolve()
    if ruta.suffix.lower() not in EXTENSIONES:
        raise argparse.ArgumentTypeError(f"el archivo debe ser {', '.join(sorted(EXTENSIONES))}")
    if not ruta.is_file():
        raise argparse.ArgumentTypeError("no existe o no es un archivo")
    if ruta.stat().st_size > MAX_BYTES:
        raise argparse.ArgumentTypeError("el archivo pasa de 20 MB")
    return ruta


def conexion_postgres(texto: str) -> str:
    try:
        url = make_url(texto)
    except ArgumentError as e:
        raise ValueError("la conexión no es una URL válida") from e
    if url.drivername not in {"postgresql+psycopg", "postgresql"}:
        raise ValueError("la conexión debe ser a PostgreSQL (postgresql+psycopg://…)")
    if not url.host or not url.database:
        raise ValueError("la conexión debe indicar servidor y base")
    if url.drivername == "postgresql":
        url = url.set(drivername="postgresql+psycopg")
    return url.render_as_string(hide_password=False)
