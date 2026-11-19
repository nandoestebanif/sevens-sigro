"""Migración 0018 (HU-28): las marcas de insumos críticos son un libro que no se modifica, nace vacío sobre una base con
datos, y la vuelta atrás no borra marcas."""
import psycopg
import pytest
from psycopg.rows import dict_row

from pruebas.conftest import RESTRICT, alembic, dsn, eliminar_base, recrear_base

BASE = "sigro_migra_0018"
HASH = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"


@pytest.fixture
def base_0018():
    recrear_base(BASE)
    yield BASE
    eliminar_base(BASE)


def con(base):
    return psycopg.connect(dsn(base), row_factory=dict_row, autocommit=True)


def _semilla(c):
    adm = c.execute("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES ('Adm','adm',%s) RETURNING id", (HASH,)).fetchone()["id"]
    p = c.execute("INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, creado_por) VALUES ('Multifamiliar Flores','2026-10-19','2027-10-17',%s) RETURNING id",
                  (adm,)).fetchone()["id"]
    i = c.execute("INSERT INTO catalogo.insumo (codigo,descripcion,unidad_s10,unidad_compra,tipo) VALUES ('0221000000','CEMENTO','bls','bls','material') RETURNING id").fetchone()["id"]
    return adm, p, i


def test_0018_sobre_base_con_datos_nace_vacia_y_es_un_libro_que_no_se_modifica(base_0018):
    alembic(base_0018, "upgrade", "0017")
    with con(base_0018) as c:
        adm, p, i = _semilla(c)
    alembic(base_0018, "upgrade", "0018")
    with con(base_0018) as c:
        assert c.execute("SELECT count(*) AS n FROM compras.insumo_critico").fetchone()["n"] == 0
        c.execute("INSERT INTO compras.insumo_critico (proyecto_id, insumo_id, critico, registrado_por) VALUES (%s,%s,true,%s)", (p, i, adm))
        c.execute("INSERT INTO compras.insumo_critico (proyecto_id, insumo_id, critico, registrado_por) VALUES (%s,%s,false,%s)", (p, i, adm))
        # la vigente es la última marca
        assert c.execute("SELECT critico FROM compras.insumo_critico_vigente").fetchall() == [{"critico": False}]
        for sql in ("UPDATE compras.insumo_critico SET critico = true", "DELETE FROM compras.insumo_critico", "TRUNCATE compras.insumo_critico"):
            with pytest.raises(psycopg.Error) as e:
                c.execute(sql)
            assert e.value.sqlstate == RESTRICT, sql


def test_0018_con_marcas_no_baja_y_sin_marcas_vuelve_atras_limpia(base_0018):
    alembic(base_0018, "upgrade", "0018")
    alembic(base_0018, "downgrade", "0017")
    with con(base_0018) as c:
        assert c.execute("SELECT to_regclass('compras.insumo_critico') AS t").fetchone()["t"] is None
    alembic(base_0018, "upgrade", "head")
    with con(base_0018) as c:
        adm, p, i = _semilla(c)
        c.execute("INSERT INTO compras.insumo_critico (proyecto_id, insumo_id, critico, registrado_por) VALUES (%s,%s,true,%s)", (p, i, adm))
    with pytest.raises(RuntimeError) as e:
        alembic(base_0018, "downgrade", "0017")
    assert "regla 10" in str(e.value)
    with con(base_0018) as c:
        assert c.execute("SELECT count(*) AS n FROM compras.insumo_critico").fetchone()["n"] == 1
    assert "No new upgrade operations detected" in alembic(base_0018, "check")
