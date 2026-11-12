"""Migración 0016 (HT-11: D07, D08, D09): base con datos, reglas nuevas en el motor y vuelta atrás sin pérdida."""
import psycopg
import pytest
from psycopg.rows import dict_row

from pruebas.conftest import CHECK, UNICO, alembic, dsn, eliminar_base, recrear_base

BASE = "sigro_migra_0016"
HASH = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"


@pytest.fixture
def base_0016():
    recrear_base(BASE)
    yield BASE
    eliminar_base(BASE)


def con(base):
    return psycopg.connect(dsn(base), row_factory=dict_row, autocommit=True)


def rechaza(c, sqlstate, sql, params=()):
    with pytest.raises(psycopg.Error) as e:
        c.execute(sql, params)
    assert e.value.sqlstate == sqlstate, e.value


def puede(c, rol, privilegio, objeto, columna=None):
    if columna:
        return c.execute("SELECT has_column_privilege(%s, %s, %s, %s) AS p", (rol, objeto, columna, privilegio)).fetchone()["p"]
    return c.execute("SELECT has_table_privilege(%s, %s, %s) AS p", (rol, objeto, privilegio)).fetchone()["p"]


def test_0016_sobre_base_con_datos_aplica_las_reglas_y_vuelve_atras_sin_perder_nada(base_0016):
    alembic(base_0016, "upgrade", "0015")
    with con(base_0016) as c:
        adm = c.execute("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES ('Adm','adm',%s) RETURNING id", (HASH,)).fetchone()["id"]
        p = c.execute("INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, creado_por) VALUES ('Multifamiliar Flores','2026-10-19','2027-10-17',%s) RETURNING id",
                      (adm,)).fetchone()["id"]
        # dos insumos con el mismo nombre (catálogo del cliente y S10 sin emparejar): la migración NO debe fallar por ellos
        a = c.execute("INSERT INTO catalogo.insumo (codigo,descripcion,unidad_s10,unidad_compra,tipo) VALUES ('0221000000','CEMENTO PORTLAND TIPO I','bol','bol','material') RETURNING id").fetchone()["id"]
        c.execute("INSERT INTO catalogo.insumo (codigo,descripcion,unidad_s10,unidad_compra,tipo) VALUES ('C-000123','cemento  portland tipo i','bol','bol','material')")
        c.execute("INSERT INTO compras.parametro_reposicion (proyecto_id, insumo_id, stock_seguridad, punto_reposicion, registrado_por) VALUES (%s,%s,20,50,%s)", (p, a, adm))
        insumos_antes = c.execute("SELECT id, codigo, descripcion FROM catalogo.insumo ORDER BY id").fetchall()
        param_antes = c.execute("SELECT * FROM compras.parametro_reposicion_vigente").fetchall()

    alembic(base_0016, "upgrade", "0016")
    with con(base_0016) as c:
        # lo existente queda igual y con los campos nuevos vacíos: no se inventa un máximo ni se parte un nombre
        assert c.execute("SELECT id, codigo, descripcion FROM catalogo.insumo ORDER BY id").fetchall() == insumos_antes
        assert c.execute("SELECT count(*) AS n FROM catalogo.insumo WHERE medida IS NOT NULL OR norma IS NOT NULL OR marca IS NOT NULL").fetchone()["n"] == 0
        vig = c.execute("SELECT * FROM compras.parametro_reposicion_vigente").fetchall()
        assert len(vig) == 1 and vig[0].pop("nivel_maximo") is None and vig == param_antes

        # D07: el máximo es opcional, pero si se da va por encima del punto de reposición; cambiarlo = fila nueva
        rechaza(c, CHECK, "INSERT INTO compras.parametro_reposicion (proyecto_id, insumo_id, stock_seguridad, punto_reposicion, nivel_maximo, registrado_por) "
                          "VALUES (%s,%s,20,50,50,%s)", (p, a, adm))
        c.execute("INSERT INTO compras.parametro_reposicion (proyecto_id, insumo_id, stock_seguridad, punto_reposicion, nivel_maximo, registrado_por) "
                  "VALUES (%s,%s,20,50,120,%s)", (p, a, adm))
        assert c.execute("SELECT nivel_maximo FROM compras.parametro_reposicion_vigente WHERE insumo_id=%s", (a,)).fetchone()["nivel_maximo"] == 120
        assert c.execute("SELECT count(*) AS n FROM compras.parametro_reposicion").fetchone()["n"] == 2      # historial intacto

        # D08 y D09: medida y marca en su campo; el mismo material con otra caja o espacios es el mismo insumo
        c.execute("INSERT INTO catalogo.insumo (codigo,descripcion,unidad_s10,unidad_compra,tipo,medida,norma,marca) "
                  "VALUES ('C-000200','Tubo PVC','und','und','material','4\"','NTP 399.003','Pavco')")
        rechaza(c, UNICO, "INSERT INTO catalogo.insumo (codigo,descripcion,unidad_s10,unidad_compra,tipo,medida,marca) "
                          "VALUES ('C-000201','  TUBO   pvc ','und','und','material',' 4\" ','PAVCO')")
        # otra medida u otra marca sí es otro insumo
        c.execute("INSERT INTO catalogo.insumo (codigo,descripcion,unidad_s10,unidad_compra,tipo,medida,marca) VALUES ('C-000202','Tubo PVC','und','und','material','2\"','Pavco')")
        c.execute("INSERT INTO catalogo.insumo (codigo,descripcion,unidad_s10,unidad_compra,tipo,medida,marca) VALUES ('C-000203','Tubo PVC','und','und','material','4\"','Nicoll')")
        # un campo en blanco no vale: o tiene texto o queda vacío (NULL)
        rechaza(c, CHECK, "INSERT INTO catalogo.insumo (codigo,descripcion,unidad_s10,unidad_compra,tipo,marca) VALUES ('C-000204','Codo PVC','und','und','material','  ')")
        assert c.execute("SELECT catalogo.normalizar('  Tubo   PVC  ') AS n").fetchone()["n"] == "tubo pvc"

        # permisos: la aplicación y el servicio catálogo corrigen los campos nuevos como la descripción; compras lee la vista
        for rol in ("sigro_app", "sigro_svc_catalogo"):
            for col in ("medida", "norma", "marca"):
                assert puede(c, rol, "UPDATE", "catalogo.insumo", col), (rol, col)
            assert not puede(c, rol, "UPDATE", "catalogo.insumo", "codigo"), rol          # el código no se toca jamás
        for rol in ("sigro_app", "sigro_svc_compras"):
            assert puede(c, rol, "SELECT", "compras.parametro_reposicion_vigente"), rol

    alembic(base_0016, "downgrade", "0015")                     # vuelta atrás con datos nuevos encima
    with con(base_0016) as c:
        cols = {r["column_name"] for r in c.execute("SELECT column_name FROM information_schema.columns WHERE table_schema='catalogo' AND table_name='insumo'")}
        assert not {"medida", "norma", "marca"} & cols
        assert c.execute("SELECT count(*) AS n FROM catalogo.insumo").fetchone()["n"] == 5
        assert c.execute("SELECT count(*) AS n FROM compras.parametro_reposicion").fetchone()["n"] == 2
        assert "nivel_maximo" not in c.execute("SELECT * FROM compras.parametro_reposicion_vigente LIMIT 1").fetchone()
        for rol in ("sigro_app", "sigro_svc_compras"):
            assert puede(c, rol, "SELECT", "compras.parametro_reposicion_vigente"), rol    # la vista recreada conserva quién la lee

    alembic(base_0016, "upgrade", "head")
    assert "No new upgrade operations detected" in alembic(base_0016, "check")
