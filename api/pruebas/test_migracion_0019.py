"""Migración 0019: el diccionario de datos vive en la base. Toda tabla y toda columna de los esquemas de los servicios
tiene descripción (una columna nueva sin COMMENT hace fallar esta prueba), sube sobre una base con datos sin tocarlos y
la vuelta atrás deja la base como estaba."""
import psycopg
import pytest
from psycopg.rows import dict_row

from pruebas.conftest import alembic, dsn, eliminar_base, recrear_base

BASE = "sigro_migra_0019"
ESQUEMAS = ("obra", "catalogo", "compras", "tareo", "almacen", "costo", "outbox")
HASH = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"

SIN_DESCRIPCION = """
    SELECT n.nspname || '.' || c.relname || '.' || a.attname AS columna
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
      JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
     WHERE c.relkind = 'r' AND n.nspname = ANY(%s) AND col_description(c.oid, a.attnum) IS NULL
     ORDER BY 1"""
TABLAS_SIN_DESCRIPCION = """
    SELECT n.nspname || '.' || c.relname AS tabla FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.relkind = 'r' AND n.nspname = ANY(%s) AND obj_description(c.oid, 'pg_class') IS NULL"""


@pytest.fixture
def base_0019():
    recrear_base(BASE)
    yield BASE
    eliminar_base(BASE)


def con(base):
    return psycopg.connect(dsn(base), row_factory=dict_row, autocommit=True)


def test_toda_tabla_y_columna_tiene_descripcion(base_pruebas):
    with con(base_pruebas) as c:
        assert [f["columna"] for f in c.execute(SIN_DESCRIPCION, (list(ESQUEMAS),))] == []
        assert [f["tabla"] for f in c.execute(TABLAS_SIN_DESCRIPCION, (list(ESQUEMAS),))] == []
        d = c.execute("SELECT col_description('almacen.movimiento'::regclass, 1) AS d").fetchone()["d"]
        assert d == "Identificador interno."


def test_0019_sobre_base_con_datos_y_vuelta_atras(base_0019):
    alembic(base_0019, "upgrade", "0018")
    with con(base_0019) as c:
        c.execute("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES ('Adm','adm',%s)", (HASH,))
    alembic(base_0019, "upgrade", "0019")
    with con(base_0019) as c:
        assert c.execute("SELECT count(*) AS n FROM obra.usuario").fetchone()["n"] == 1          # los datos no se tocan
        assert c.execute("SELECT to_regclass('almacen.ix_movimiento_conteo_id') AS i").fetchone()["i"] is not None
        assert "Argon2id" in c.execute("SELECT col_description('obra.usuario'::regclass, 4) AS d").fetchone()["d"]
    alembic(base_0019, "downgrade", "0018")
    with con(base_0019) as c:
        assert c.execute("SELECT to_regclass('almacen.ix_movimiento_conteo_id') AS i").fetchone()["i"] is None
        assert len(c.execute(SIN_DESCRIPCION, (list(ESQUEMAS),)).fetchall()) > 300
        assert c.execute("SELECT count(*) AS n FROM obra.usuario").fetchone()["n"] == 1
