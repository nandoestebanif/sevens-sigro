"""Entorno de Alembic de SIGRO.

La URL sale de SIGRO_DATABASE_URL. Las migraciones corren con el usuario DUEÑO del
esquema (nunca con el usuario de la aplicación: ese no tiene permiso para modificar
tablas append-only, y eso es justamente lo que queremos).
"""
from __future__ import annotations

import os
from logging.config import fileConfig

from alembic import context
from sqlalchemy import create_engine, pool

from app.base import Base
import app.modulos.catalogo.modelos  # noqa: F401  (registra tablas en Base.metadata)
import app.modulos.compras.modelos  # noqa: F401
import app.modulos.obra.modelos  # noqa: F401
import app.modulos.outbox.modelos  # noqa: F401
import app.modulos.tareo.modelos  # noqa: F401

config = context.config
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

URL = os.environ.get(
    "SIGRO_DATABASE_URL",
    "postgresql+psycopg://sigro_owner:sigro_owner_dev@localhost:5432/sigro_dev",
)

target_metadata = Base.metadata

# Esquemas que este repositorio administra. almacen y costo son de Henry y no se
# comparan aquí: si aparecieran en la base, `alembic check` no debe tocarlos.
ESQUEMAS = {"obra", "catalogo", "compras", "tareo", "outbox"}


def incluir(obj, name, type_, reflected, compare_to):
    if type_ == "table":
        return getattr(obj, "schema", None) in ESQUEMAS
    return True


def run_migrations_offline() -> None:
    context.configure(
        url=URL,
        target_metadata=target_metadata,
        literal_binds=True,
        include_schemas=True,
        include_object=incluir,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    engine = create_engine(URL, poolclass=pool.NullPool)
    with engine.connect() as conn:
        context.configure(
            connection=conn,
            target_metadata=target_metadata,
            include_schemas=True,
            include_object=incluir,
            compare_type=True,
        )
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
