"""Migración 0017 (T-340): lo existente queda como «s10», lo manual exige motivo, nada de eso se edita y la vuelta
atrás no borra líneas manuales."""
import psycopg
import pytest
from psycopg.rows import dict_row

from pruebas.conftest import CHECK, RESTRICT, alembic, dsn, eliminar_base, recrear_base

BASE = "sigro_migra_0017"
HASH = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"


@pytest.fixture
def base_0017():
    recrear_base(BASE)
    yield BASE
    eliminar_base(BASE)


def con(base):
    return psycopg.connect(dsn(base), row_factory=dict_row, autocommit=True)


def rechaza(c, sqlstate, sql, params=()):
    with pytest.raises(psycopg.Error) as e:
        c.execute(sql, params)
    assert e.value.sqlstate == sqlstate, e.value


def test_0017_sobre_base_con_datos_marca_lo_importado_y_protege_lo_manual(base_0017):
    alembic(base_0017, "upgrade", "0016")
    with con(base_0017) as c:
        adm = c.execute("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES ('Adm','adm',%s) RETURNING id", (HASH,)).fetchone()["id"]
        p = c.execute("INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, creado_por) VALUES ('Multifamiliar Flores','2026-10-19','2027-10-17',%s) RETURNING id",
                      (adm,)).fetchone()["id"]
        ins = [c.execute("INSERT INTO catalogo.insumo (codigo,descripcion,unidad_s10,unidad_compra,tipo) VALUES (%s,%s,'und','und','material') RETURNING id",
                         (cod, des)).fetchone()["id"] for cod, des in (("0221000000", "CEMENTO"), ("0203020004", "ACERO"), ("C-000300", "MALLA RASCHEL"))]
        c.execute("INSERT INTO compras.requerimiento (proyecto_id, insumo_id, cantidad_planificada, precio_planificado, creado_por) VALUES (%s,%s,400,29.23,%s)",
                  (p, ins[0], adm))
        antes = c.execute("SELECT id, cantidad_planificada, precio_planificado FROM compras.requerimiento ORDER BY id").fetchall()

    alembic(base_0017, "upgrade", "0017")
    with con(base_0017) as c:
        # lo que ya estaba vino del S10 (no había otra vía) y no cambia en nada más
        assert c.execute("SELECT id, cantidad_planificada, precio_planificado FROM compras.requerimiento ORDER BY id").fetchall() == antes
        assert c.execute("SELECT origen, motivo_alta FROM compras.requerimiento").fetchall() == [{"origen": "s10", "motivo_alta": None}]
        base = "INSERT INTO compras.requerimiento (proyecto_id, insumo_id, cantidad_planificada, precio_planificado, creado_por, origen, motivo_alta) VALUES (%s,%s,10,1,%s,%s,%s)"
        rechaza(c, CHECK, base, (p, ins[1], adm, "manual", None))            # manual sin motivo
        rechaza(c, CHECK, base, (p, ins[1], adm, "manual", "   "))           # ni en blanco
        rechaza(c, CHECK, base, (p, ins[1], adm, "s10", "por si acaso"))     # lo del S10 no lleva motivo de alta
        rechaza(c, CHECK, base, (p, ins[1], adm, "excel", None))             # solo dos orígenes
        rid = c.execute(base + " RETURNING id", (p, ins[1], adm, "manual", "Partida adicional")).fetchone()["id"]
        # el origen y el motivo no se editan, ni siquiera con permisos de dueño
        rechaza(c, RESTRICT, "UPDATE compras.requerimiento SET origen='s10', motivo_alta=NULL WHERE id=%s", (rid,))
        rechaza(c, RESTRICT, "UPDATE compras.requerimiento SET motivo_alta='otro' WHERE id=%s", (rid,))
        # la regla de la 0008 sigue: cambiar la cantidad exige el ajuste
        rechaza(c, RESTRICT, "UPDATE compras.requerimiento SET cantidad_planificada=20 WHERE id=%s", (rid,))

    # con una línea manual, bajar la 0017 se detiene: no la confunde con lo del S10 ni la borra
    with pytest.raises(RuntimeError) as e:
        alembic(base_0017, "downgrade", "0016")
    assert "SIGRO-M17" in str(e.value)
    with con(base_0017) as c:
        assert c.execute("SELECT count(*) AS n FROM compras.requerimiento WHERE origen='manual'").fetchone()["n"] == 1


def test_0017_sin_lineas_manuales_vuelve_atras_limpio(base_0017):
    alembic(base_0017, "upgrade", "0017")
    alembic(base_0017, "downgrade", "0016")
    with con(base_0017) as c:
        cols = {r["column_name"] for r in c.execute(
            "SELECT column_name FROM information_schema.columns WHERE table_schema='compras' AND table_name='requerimiento'")}
        assert not {"origen", "motivo_alta"} & cols
    alembic(base_0017, "upgrade", "head")
    assert "No new upgrade operations detected" in alembic(base_0017, "check")
