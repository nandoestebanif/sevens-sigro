"""Infraestructura de pruebas contra PostgreSQL REAL.

Las restricciones de SIGRO viven en el motor (índices únicos, CHECK, disparadores, permisos),
así que se prueban contra el motor, no contra un doble. Cada prueba corre dentro de una
transacción que se revierte; la base de pruebas se crea y se migra una vez por sesión.
"""
from __future__ import annotations

import os
import subprocess
import sys
from contextlib import contextmanager
from pathlib import Path

import psycopg
import pytest
from psycopg.rows import dict_row

RAIZ = Path(__file__).resolve().parents[1]
SERVIDOR = os.environ.get("SIGRO_TEST_SERVIDOR", "sigro_owner:sigro_owner_dev@localhost:5432")

# SQLSTATE que importan
CHECK = "23514"
RESTRICT = "23001"      # restrict_violation: nada se borra / libro que no se modifica
UNICO = "23505"
FORANEA = "23503"
EXCLUSION = "23P01"
SIN_PERMISO = "42501"


def dsn(base: str) -> str:
    return f"postgresql://{SERVIDOR}/{base}"


def url_sqlalchemy(base: str) -> str:
    return f"postgresql+psycopg://{SERVIDOR}/{base}"


def alembic(base: str, *args: str) -> str:
    env = {**os.environ, "SIGRO_DATABASE_URL": url_sqlalchemy(base)}
    r = subprocess.run([sys.executable, "-m", "alembic", *args], cwd=RAIZ, env=env, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"alembic {' '.join(args)} falló:\n{r.stderr}")
    return r.stdout + r.stderr


def recrear_base(nombre: str) -> None:
    with psycopg.connect(dsn("postgres"), autocommit=True) as c:
        c.execute(f"DROP DATABASE IF EXISTS {nombre} WITH (FORCE)")
        c.execute(f"CREATE DATABASE {nombre}")


def eliminar_base(nombre: str) -> None:
    with psycopg.connect(dsn("postgres"), autocommit=True) as c:
        c.execute(f"DROP DATABASE IF EXISTS {nombre} WITH (FORCE)")


@pytest.fixture(scope="session")
def base_pruebas():
    recrear_base("sigro_test")
    alembic("sigro_test", "upgrade", "head")
    yield "sigro_test"
    eliminar_base("sigro_test")


@pytest.fixture
def db(base_pruebas):
    conn = psycopg.connect(dsn(base_pruebas), row_factory=dict_row)
    conn.execute("SELECT 1")      # abre la transacción: lo que sigue usa SAVEPOINT, nunca COMMIT
    yield conn
    conn.rollback()
    conn.close()


@contextmanager
def rechaza(conn, sqlstate: str, contiene: str | None = None):
    """La base DEBE rechazar lo que ocurra dentro del bloque, con ese SQLSTATE."""
    try:
        with conn.transaction():
            yield
    except psycopg.Error as e:
        assert e.sqlstate == sqlstate, f"se esperaba {sqlstate} y llegó {e.sqlstate}: {e}"
        if contiene:
            assert contiene in str(e), f"el mensaje no contiene {contiene!r}: {e}"
    else:
        raise AssertionError("la base aceptó lo que debía rechazar")


def como_aplicacion(conn) -> None:
    """Desde aquí, y hasta el fin de la transacción, se actúa con el usuario de la aplicación."""
    conn.execute("SET LOCAL ROLE sigro_app")


@pytest.fixture
def f(db):
    from pruebas.fabrica import Fabrica
    return Fabrica(db)


# ───────────────────────── API: el cliente HTTP corre contra la base real, dentro de una transacción ─────────────────────────
class _ConexionFabrica:
    """Deja que la Fabrica (SQL con %s y filas tipo dict) use LA MISMA transacción que la API."""

    def __init__(self, conn_sqlalchemy):
        self._raw = conn_sqlalchemy.connection.driver_connection

    def execute(self, sql, params=()):
        cur = self._raw.cursor(row_factory=dict_row)
        cur.execute(sql, params)
        return cur


class Mundo:
    """Un cliente HTTP, la Fabrica y la sesión de prueba. Todo se revierte al terminar la prueba."""

    def __init__(self, cliente, fabrica, conn):
        self.http = cliente
        self.f = fabrica
        self.conn = conn

    def token(self, usuario_id: int) -> dict:
        from app.seguridad import crear_token
        return {"Authorization": f"Bearer {crear_token(usuario_id)[0]}"}

    def con(self, usuario_id: int) -> dict:
        return self.token(usuario_id)


@pytest.fixture
def mundo(base_pruebas):
    from fastapi.testclient import TestClient
    from sqlalchemy import create_engine, text
    from sqlalchemy.orm import Session

    from app.db import obtener_sesion
    from app.main import crear_app
    from pruebas.fabrica import Fabrica

    motor = create_engine(url_sqlalchemy(base_pruebas))
    conn = motor.connect()
    transaccion = conn.begin()
    app = crear_app()

    def sesion_de_prueba():
        conn.execute(text("SET ROLE sigro_app"))          # la API actúa SIN poder de dueño: lo que vale en producción
        with Session(bind=conn, join_transaction_mode="create_savepoint") as s:
            try:
                yield s
                s.commit()
            except BaseException:
                s.rollback()
                raise
            finally:
                pass
        conn.execute(text("RESET ROLE"))

    app.dependency_overrides[obtener_sesion] = sesion_de_prueba
    with TestClient(app, raise_server_exceptions=False) as cliente:
        yield Mundo(cliente, Fabrica(_ConexionFabrica(conn)), conn)
    transaccion.rollback()
    conn.close()
    motor.dispose()
