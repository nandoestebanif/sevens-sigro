"""Definición de Terminado: la migración corre sobre base limpia y sobre base con datos, y se revierte."""
import re

import psycopg
import pytest
from psycopg.rows import dict_row

from pruebas.conftest import alembic, dsn, eliminar_base, recrear_base

BASE = "sigro_migra"
HASH = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"


@pytest.fixture
def base_limpia():
    recrear_base(BASE)
    yield BASE
    eliminar_base(BASE)


def con(base):
    return psycopg.connect(dsn(base), row_factory=dict_row, autocommit=True)


def existe(c, esquema, tabla):
    return c.execute("SELECT to_regclass(%s) IS NOT NULL AS e", (f"{esquema}.{tabla}",)).fetchone()["e"]


def cuenta(c, tabla):
    return c.execute(f"SELECT count(*) AS n FROM {tabla}").fetchone()["n"]


def test_hay_una_sola_cabeza(base_limpia):
    salida = alembic(base_limpia, "heads")
    assert len(re.findall(r"\(head\)", salida)) == 1


def test_sube_de_cero_hasta_la_cabeza_y_los_modelos_no_se_desvian_de_la_base(base_limpia):
    alembic(base_limpia, "upgrade", "head")
    assert "No new upgrade operations detected" in alembic(base_limpia, "check")


def test_baja_hasta_cero_sin_dejar_nada_y_vuelve_a_subir(base_limpia):
    alembic(base_limpia, "upgrade", "head")
    alembic(base_limpia, "downgrade", "base")
    with con(base_limpia) as c:
        for esquema in ("obra", "catalogo", "compras", "tareo", "outbox", "util"):
            assert not c.execute("SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = %s) AS e", (esquema,)).fetchone()["e"], esquema
    alembic(base_limpia, "upgrade", "head")      # y vuelve a subir limpio


def test_migrar_una_base_con_datos_no_pierde_ni_altera_nada(base_limpia):
    """Se llena la base en 0004 (antes de la solicitud), se sube a 0005 y se vuelve a bajar."""
    alembic(base_limpia, "upgrade", "0004")
    with con(base_limpia) as c:
        adm = c.execute("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES ('Adm','adm',%s) RETURNING id", (HASH,)).fetchone()["id"]
        p = c.execute("INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, creado_por) VALUES ('Multifamiliar Flores','2026-10-19','2026-12-06',%s) RETURNING id", (adm,)).fetchone()["id"]
        t = c.execute("INSERT INTO tareo.trabajador (proyecto_id,nombre,documento,categoria,cuadrilla,fecha_ingreso,registrado_por) VALUES (%s,'Juan','45678912','peon','C1','2026-10-19',%s) RETURNING id", (p, adm)).fetchone()["id"]
        c.execute("INSERT INTO tareo.jornada (proyecto_id,trabajador_id,fecha,estado,horas_normales,categoria,periodo_id,registrado_por) VALUES (%s,%s,'2026-10-20','presente',8,'peon',1,%s)", (p, t, adm))
        prov = c.execute("INSERT INTO compras.proveedor (ruc, razon_social) VALUES ('20606456892','SEVENS SAC') RETURNING id").fetchone()["id"]
        ins = c.execute("INSERT INTO catalogo.insumo (codigo,descripcion,unidad_s10,unidad_compra,tipo) VALUES ('0201010001','Cemento','bol','bol','material') RETURNING id").fetchone()["id"]
        co = c.execute("INSERT INTO compras.compra (proyecto_id,proveedor_id,tipo_comprobante,numero_comprobante,fecha,base_imponible,igv,total,registrado_por) "
                       "VALUES (%s,%s,'factura','F001-4512','2026-10-21',1000,180,1180,%s) RETURNING id", (p, prov, adm)).fetchone()["id"]
        c.execute("INSERT INTO compras.compra_detalle (compra_id,insumo_id,cantidad,unidad,precio_unitario) VALUES (%s,%s,200,'bol',5)", (co, ins))
        antes = {t_: cuenta(c, t_) for t_ in ("obra.usuario", "obra.proyecto", "obra.periodo", "tareo.trabajador", "tareo.jornada",
                                               "compras.proveedor", "compras.compra", "compras.compra_detalle", "catalogo.insumo")}
        compra_antes = dict(c.execute("SELECT * FROM compras.compra WHERE id = %s", (co,)).fetchone())

    alembic(base_limpia, "upgrade", "head")                       # 0004 -> 0005 con datos encima
    with con(base_limpia) as c:
        assert {t_: cuenta(c, t_) for t_ in antes} == antes
        despues = dict(c.execute("SELECT * FROM compras.compra WHERE id = %s", (co,)).fetchone())
        assert despues.pop("solicitud_id") is None                # la compra existente queda sin solicitud
        assert despues == compra_antes
        # lo nuevo funciona sobre lo viejo
        s = c.execute("INSERT INTO compras.solicitud (proyecto_id, emitida_por) VALUES (%s,%s) RETURNING id", (p, adm)).fetchone()["id"]
        c.execute("INSERT INTO compras.solicitud_linea (solicitud_id,insumo_id,cantidad,unidad_compra) VALUES (%s,%s,500,'bol')", (s, ins))
        assert c.execute("SELECT estado_derivado FROM compras.solicitud_estado WHERE solicitud_id=%s", (s,)).fetchone()["estado_derivado"] == "solicitada"

    alembic(base_limpia, "downgrade", "0004")                     # vuelta atrás con datos encima
    with con(base_limpia) as c:
        assert not existe(c, "compras", "solicitud") and not existe(c, "compras", "solicitud_linea")
        assert {t_: cuenta(c, t_) for t_ in antes} == antes       # nada de lo anterior se perdió
        assert "solicitud_id" not in c.execute("SELECT * FROM compras.compra LIMIT 1").fetchone()
        assert dict(c.execute("SELECT * FROM compras.compra WHERE id = %s", (co,)).fetchone()) == compra_antes

    alembic(base_limpia, "upgrade", "head")                       # y se puede volver a subir
    with con(base_limpia) as c:
        assert existe(c, "compras", "solicitud_estado")


def test_la_migracion_se_puede_emitir_como_sql_para_que_el_dba_la_revise(base_limpia):
    sql = alembic(base_limpia, "upgrade", "head", "--sql")
    assert "CREATE TABLE obra.proyecto" in sql and "CREATE TABLE compras.solicitud " in sql
    assert "ex_periodo_sin_solape" in sql


def test_cada_migracion_declara_su_vuelta_atras():
    from pathlib import Path
    versiones = Path(__file__).resolve().parents[1] / "migraciones" / "versions"
    archivos = sorted(versiones.glob("0*.py"))
    assert len(archivos) == 8
    for archivo in archivos:
        cuerpo = archivo.read_text().split("def downgrade()", 1)[1]
        assert "op.execute" in cuerpo, f"{archivo.name} no revierte nada"
