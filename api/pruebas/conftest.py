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


class GatewayEnProceso:
    """El gateway de producción (app.servicios.gateway) reenvía por HTTP; aquí la MISMA tabla de enrutado
    entrega la petición a la app del servicio en el mismo proceso. /interno/... no se expone, igual que allá."""

    def __init__(self, apps: dict):
        self.apps = apps

    async def __call__(self, scope, receive, send):
        from app.enrutado import servicio_para
        if scope["type"] == "lifespan":
            while True:
                m = await receive()
                if m["type"] == "lifespan.startup":
                    await send({"type": "lifespan.startup.complete"})
                elif m["type"] == "lifespan.shutdown":
                    await send({"type": "lifespan.shutdown.complete"})
                    return
        servicio = "obras" if scope["path"] == "/salud" else servicio_para(scope["path"])
        if servicio is None:
            from starlette.responses import JSONResponse
            r = JSONResponse({"codigo": "no_existe", "mensaje": "Esa ruta no existe."}, status_code=404)
            await r(scope, receive, send)
            return
        await self.apps[servicio](scope, receive, send)


@pytest.fixture
def mundo(base_pruebas):
    """Todos los microservicios en el mismo proceso, cada uno con SU usuario de base de datos.

    - La prueba habla con el gateway (mismas rutas /api/v1 que la web y la app).
    - Cada servicio actúa con su rol `sigro_svc_<servicio>`: si un servicio intentara leer el esquema de otro,
      la base lo rechazaría y la prueba fallaría. Lo que necesita de otro, lo pide por su API interna.
    - Las llamadas internas entre servicios usan el mismo contrato HTTP, con el transporte en proceso.
    - Todo corre en UNA transacción que se revierte al final.
    """
    from fastapi.testclient import TestClient
    from sqlalchemy import create_engine, text
    from sqlalchemy.orm import Session

    from app import internos
    from app.db import obtener_sesion
    from app.servicios import fabricas
    from pruebas.fabrica import Fabrica

    motor = create_engine(url_sqlalchemy(base_pruebas))
    conn = motor.connect()
    transaccion = conn.begin()

    def sesion_como(rol: str):
        def sesion_de_prueba():
            previo = conn.execute(text("SELECT current_user")).scalar()
            conn.execute(text(f"SET ROLE {rol}"))          # el servicio actúa SIN poder de dueño y solo sobre su esquema
            try:
                with Session(bind=conn, join_transaction_mode="create_savepoint") as s:
                    try:
                        yield s
                        s.commit()
                    except BaseException:
                        s.rollback()
                        raise
            finally:
                conn.execute(text(f"SET ROLE {previo}"))
        return sesion_de_prueba

    apps = {}
    for nombre, fabrica in fabricas().items():
        app = fabrica()
        app.dependency_overrides[obtener_sesion] = sesion_como(f"sigro_svc_{nombre}")
        apps[nombre] = app
    internos_clientes = {n: TestClient(a, raise_server_exceptions=False) for n, a in apps.items()}
    for n, c in internos_clientes.items():
        internos.registrar_cliente_de_prueba(n, c)
    try:
        with TestClient(GatewayEnProceso(apps), raise_server_exceptions=False) as cliente:
            yield Mundo(cliente, Fabrica(_ConexionFabrica(conn)), conn)
    finally:
        for n in internos_clientes:
            internos.registrar_cliente_de_prueba(n, None)
        transaccion.rollback()
        conn.close()
        motor.dispose()
