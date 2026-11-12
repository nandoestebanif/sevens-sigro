"""Migración 0010 y servicio costo: dominio sin base, motor (concurrencia real), outbox de integración, gasto (HU-58)
y la migración sobre base limpia y con datos, con su vuelta atrás. Reemplaza a docs/propuestas/probar_0010.py."""
from __future__ import annotations

import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timezone
from decimal import Decimal as D
from threading import Barrier

import psycopg
import pytest
from psycopg.rows import dict_row
from sqlalchemy.orm import Session

from app.modulos.almacen import dominio as dom
from pruebas.conftest import CHECK, SIN_PERMISO, alembic, dsn, eliminar_base, rechaza, recrear_base

U = "/api/v1"
HASH = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"


# ═══════════════════════════════════ dominio, sin base
def test_promedio_ponderado_por_almacen_espejo_del_disparador():
    movs = [(1, D(100), D(20)), (1, D(50), D(26))]
    assert dom.promedio_ponderado(movs) == (D(150), D("22.000000"))
    movs.append((-1, D(30), D(22)))                 # la salida sale al promedio: no lo cambia
    assert dom.promedio_ponderado(movs) == (D(120), D("22.000000"))
    movs.append((1, D(80), D(30)))
    assert dom.promedio_ponderado(movs) == (D(200), D("25.200000"))
    assert dom.promedio_ponderado([(1, D(5), D(3)), (-1, D(5), D(3))]) == (D(0), None)


def test_dia_del_hecho_y_periodo():
    ahora = datetime(2026, 11, 1, 3, 0, tzinfo=timezone.utc)                  # 31/10 22:00 en Lima
    assert dom.fecha_del_hecho(None, ahora) == date(2026, 10, 31)
    futuro = int(datetime(2027, 1, 1, tzinfo=timezone.utc).timestamp() * 1000)
    assert dom.fecha_del_hecho(futuro, ahora) == date(2026, 10, 31)          # nunca un día futuro
    pasado = int(datetime(2026, 10, 20, 15, tzinfo=timezone.utc).timestamp() * 1000)
    assert dom.fecha_del_hecho(pasado, ahora) == date(2026, 10, 20)
    periodos = [{"id": 1, "numero": 1, "fecha_inicio": "2026-10-19", "fecha_fin": "2026-10-25", "estado": "cerrado"},
                {"id": 2, "numero": 2, "fecha_inicio": date(2026, 10, 26), "fecha_fin": date(2026, 11, 1), "estado": "abierto"}]
    assert dom.periodo_de(periodos, date(2026, 10, 20))["id"] == 1 and dom.periodo_de(periodos, date(2026, 12, 1)) is None
    with pytest.raises(dom.ReglaAlmacen, match="cerrado"):
        dom.exigir_periodo_abierto(periodos[0], es_reverso=False)
    dom.exigir_periodo_abierto(periodos[0], es_reverso=True)
    dom.exigir_periodo_abierto(None, es_reverso=False)


def test_unidades_costo_sin_igv_y_uuid_derivado():
    acero = {"codigo": "0203020004", "unidad_s10": "kg", "unidad_compra": "var", "factor_conversion": "7.12"}
    assert dom.a_unidad_s10(D(10), "var", acero) == D("71.2000") and dom.a_unidad_s10(D(3), None, acero) == D(3)
    with pytest.raises(dom.ReglaAlmacen):
        dom.a_unidad_s10(D(1), "m3", acero)
    assert dom.costo_recepcion(D("35.40"), D(1), D("7.12")) == D("4.971910")
    assert dom.costo_recepcion(D("29.23"), D("9908.47") / D("11692.00"), D(1)) == D("24.771175")
    u = uuid.uuid4()
    assert dom.uuid_derivado(u, "salida", 3) == dom.uuid_derivado(str(u), "salida", 3) != dom.uuid_derivado(u, "ingreso", 3)


# ═══════════════════════════════════ motor: concurrencia real (commits de verdad, varias conexiones)
def _preparar(base) -> dict:
    sufijo = uuid.uuid4().hex[:8]
    with psycopg.connect(dsn(base), row_factory=dict_row) as c:
        adm = c.execute("INSERT INTO obra.usuario (nombre,usuario,hash_clave) VALUES ('Adm',%s,%s) RETURNING id", (f"a{sufijo}", HASH)).fetchone()["id"]
        p = c.execute("INSERT INTO obra.proyecto (nombre,fecha_inicio,fecha_fin,creado_por) VALUES (%s,'2026-10-19','2026-12-06',%s) RETURNING id",
                      (f"Obra concurrente {sufijo}", adm)).fetchone()["id"]
        alm = c.execute("INSERT INTO obra.almacen (proyecto_id,nombre) VALUES (%s,'Central') RETURNING id", (p,)).fetchone()["id"]
        c.execute("INSERT INTO almacen.almacen_ref (id, proyecto_id, nombre, tipo, estado) VALUES (%s,%s,'Central','frente','activo')", (alm, p))
        ins = c.execute("INSERT INTO almacen.insumo_ref (id, codigo, descripcion, unidad, tipo, activo) "
                        "VALUES ((SELECT coalesce(max(id),0)+1000 FROM almacen.insumo_ref), %s, 'Cemento', 'bls', 'material', true) RETURNING id",
                        (f"C{sufijo}",)).fetchone()["id"]
        c.execute("SET ROLE sigro_svc_almacen")
        c.execute("INSERT INTO almacen.movimiento (uuid_cliente, proyecto_id, almacen_id, insumo_id, tipo, sentido, cantidad, costo_unitario, "
                  "origen, registrado_por) VALUES (%s,%s,%s,%s,'ingreso_sin_compra',1,10,20,'sobrante',%s)", (uuid.uuid4(), p, alm, ins, adm))
        c.commit()
    return {"p": p, "alm": alm, "ins": ins, "adm": adm}


def test_dos_salidas_simultaneas_nunca_dejan_el_saldo_en_negativo(base_pruebas):
    """R4: sin el candado por (almacén, insumo), las dos verían 10 disponibles y saldrían 14."""
    for _ in range(6):
        o = _preparar(base_pruebas)
        barrera = Barrier(2)

        def sacar(_):
            with psycopg.connect(dsn(base_pruebas)) as c:
                c.execute("SET ROLE sigro_svc_almacen")
                barrera.wait()
                try:
                    c.execute("INSERT INTO almacen.movimiento (uuid_cliente, proyecto_id, almacen_id, insumo_id, tipo, sentido, cantidad, "
                              "costo_unitario, motivo, quien_retira, registrado_por) VALUES (%s,%s,%s,%s,'salida',-1,7,0,'m','q',%s)",
                              (uuid.uuid4(), o["p"], o["alm"], o["ins"], o["adm"]))
                    c.commit()
                    return "ok"
                except psycopg.Error as e:
                    return e.sqlstate

        with ThreadPoolExecutor(2) as ex:
            res = sorted(ex.map(sacar, range(2)))
        assert res == [CHECK, "ok"], res
        with psycopg.connect(dsn(base_pruebas)) as c:
            assert c.execute("SELECT cantidad FROM almacen.saldo WHERE almacen_id = %s AND insumo_id = %s", (o["alm"], o["ins"])).fetchone()[0] == 3


def test_el_rol_de_almacen_no_edita_ni_borra_y_no_lee_otros_esquemas(db):
    db.execute("SET LOCAL ROLE sigro_svc_almacen")
    for sql in ("UPDATE almacen.movimiento SET cantidad = 1", "DELETE FROM almacen.movimiento", "DELETE FROM almacen.recepcion",
                "SELECT * FROM obra.proyecto", "SELECT * FROM compras.compra", "SELECT payload FROM outbox.evento", "SELECT * FROM costo.hecho"):
        with rechaza(db, SIN_PERMISO):
            db.execute(sql)


def test_el_dueno_tampoco_edita_el_libro(db):
    from pruebas.conftest import RESTRICT
    for tabla in ("almacen.conteo_linea", "costo.hecho"):          # sin filas, UPDATE/DELETE no disparan: TRUNCATE sí
        with rechaza(db, RESTRICT):
            db.execute(f"TRUNCATE {tabla}")


def test_outbox_de_integracion_sin_destinatarios_y_de_aviso_con_ellos(db, f):
    p = f.proyecto()
    db.execute("INSERT INTO outbox.evento (tipo, proyecto_id, payload, destino) VALUES ('almacen.salida_registrada', %s, '{\"hechos\": []}', 'integracion')", (p,))
    with rechaza(db, CHECK, "ck_evento_destinatarios"):
        db.execute("INSERT INTO outbox.evento (tipo, proyecto_id, payload) VALUES ('compra.anulada', %s, '{\"hechos\": []}')", (p,))
    with rechaza(db, CHECK, "ck_evento_destino"):
        db.execute("INSERT INTO outbox.evento (tipo, proyecto_id, payload, destino) VALUES ('a.b', %s, '{}', 'otro')", (p,))


# ═══════════════════════════════════ costo: relevo → costo (idempotente por evento_id) → gasto (HU-58)
@pytest.fixture
def ob(mundo):
    e = mundo.f.obra_completa()
    e["central"] = mundo.f.almacen(e["proyecto"], "Central", "central")
    e["cem"] = mundo.f.insumo("0221000000", unidad="bls")
    e["cel"] = mundo.f.celular(e["proyecto"], e["almacenero"], e["admin"])
    return e


def _entregar(mundo, monkeypatch):
    from app import internos, relay
    monkeypatch.setenv("SIGRO_URL_COSTO", "http://costo.interno")
    monkeypatch.setenv("SIGRO_SERVICIO_TOKEN", "token-de-servicio-de-pruebas-0123456789")
    costo = internos._clientes_prueba["costo"]
    mundo.conn.exec_driver_sql("SET ROLE sigro_svc_relevo")
    enviados = []

    def enviar(ev):
        if ev["destino"] != "integracion":
            raise RuntimeError("los avisos no son de esta prueba")
        relay.enviar_http(ev, cliente=costo)
        enviados.append(ev["id"])

    relay.procesar(lambda: Session(bind=mundo.conn, join_transaction_mode="create_savepoint"), enviar)
    mundo.conn.exec_driver_sql("RESET ROLE")
    return enviados


def test_el_gasto_en_recursos_llega_por_eventos_y_un_reenvio_no_duplica(mundo, ob, monkeypatch):
    p = ob["proyecto"]
    h = mundo.con(ob["almacenero"])
    mundo.http.post(f"{U}/obras/{p}/almacen/ingresos", headers=h, json={"uuid_cliente": str(uuid.uuid4()), "insumo_id": ob["cem"],
                    "cantidad": "100", "costo_unitario": "20", "origen": "sobrante"})
    s = mundo.http.post(f"{U}/obras/{p}/almacen/salidas", headers=h, json={"uuid_cliente": str(uuid.uuid4()), "insumo_id": ob["cem"],
                        "cantidad": "30", "motivo": "Losa", "quien_retira": "Capataz", "dispositivo": ob["cel"]}).json()
    mundo.http.post(f"{U}/obras/{p}/almacen/movimientos/{s['id']}/reverso", headers=h,
                    json={"uuid_cliente": str(uuid.uuid4()), "motivo": "Eran 10, no 30"})
    mundo.http.post(f"{U}/obras/{p}/almacen/salidas", headers=h, json={"uuid_cliente": str(uuid.uuid4()), "insumo_id": ob["cem"],
                    "cantidad": "10", "motivo": "Losa", "quien_retira": "Capataz", "dispositivo": ob["cel"]})
    prov = mundo.f.proveedor()
    mundo.f.compra(p, prov, ob["residente"], base="1000.00", igv="180.00")
    t = mundo.f.trabajador(p, ob["almacenero"])
    mundo.f.jornada(p, t, ob["almacenero"], normales="8", extra="2")
    enviados = _entregar(mundo, monkeypatch)
    assert len(enviados) == 4
    g = mundo.http.get(f"{U}/obras/{p}/costo/gasto", headers=mundo.con(ob["gerente"]))
    assert g.status_code == 200, g.text
    d = g.json()
    cif = {c["clave"]: c for c in d["cifras"]}
    assert D(cif["comprado"]["valor"]) == D("1000.00")
    assert D(cif["recibido"]["valor"]) == D("2000.00") and D(cif["consumido"]["valor"]) == D("200.00")
    assert D(cif["en_stock"]["valor"]) == D("1800.00")
    assert cif["horas_hombre"]["valor"] is None and "HU-65" in cif["horas_hombre"]["nota"]
    assert D(d["horas_hombre"]["horas_normales"]) == 8 and D(d["horas_hombre"]["horas_extra"]) == 2
    assert "no su costo total" in d["declaracion"] and "subcontratos" in d["declaracion"]
    assert all(c["definicion"] for c in d["cifras"]) and cif["recibido"]["corte"]
    # el relevo entrega «al menos una vez»: costo deduplica por evento_id
    from app import internos
    ev = mundo.f.c.execute("SELECT id, tipo, proyecto_id, payload FROM outbox.evento WHERE id = %s", (enviados[0],)).fetchone()
    r = internos.json_de("costo", "POST", "/interno/eventos", json={"evento_id": ev["id"], "tipo": ev["tipo"], "proyecto_id": ev["proyecto_id"],
                                                                     "datos": ev["payload"]})
    assert r["duplicado"] is True
    assert D(mundo.http.get(f"{U}/obras/{p}/costo/gasto", headers=mundo.con(ob["gerente"])).json()["cifras"][1]["valor"]) == D("2000.00")


def test_el_gasto_lo_ven_residente_administrador_y_gerente_y_nadie_mas(mundo, ob):
    ruta = f"{U}/obras/{ob['proyecto']}/costo/gasto"
    for quien, estado in (("residente", 200), ("admin", 200), ("gerente", 200), ("almacenero", 403), ("ssoma", 403)):
        assert mundo.http.get(ruta, headers=mundo.con(ob[quien])).status_code == estado, quien
    otra = mundo.f.obra_completa()
    assert mundo.http.get(ruta, headers=mundo.con(otra["gerente"])).status_code == 403


# ═══════════════════════════════════ la migración: base limpia, base con datos, vuelta atrás
BASE = "sigro_migra_0010"


@pytest.fixture
def base_0010():
    recrear_base(BASE)
    yield BASE
    eliminar_base(BASE)


def test_0010_sobre_base_con_datos_baja_a_0009_y_vuelve_a_subir(base_0010):
    alembic(base_0010, "upgrade", "0009")
    with psycopg.connect(dsn(base_0010), row_factory=dict_row, autocommit=True) as c:
        adm = c.execute("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES ('Adm','adm',%s) RETURNING id", (HASH,)).fetchone()["id"]
        p = c.execute("INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, creado_por) VALUES ('Multifamiliar Flores','2026-10-19','2027-10-17',%s) RETURNING id",
                      (adm,)).fetchone()["id"]
        c.execute("INSERT INTO outbox.evento (tipo, proyecto_id, payload) VALUES ('dispositivo.solicitado', %s, %s)",
                  (p, '{"destinatarios": [1], "titulo": "t", "mensaje": "m", "datos": {}}'))
        antes = c.execute("SELECT * FROM outbox.evento").fetchall()
    alembic(base_0010, "upgrade", "head")
    with psycopg.connect(dsn(base_0010), row_factory=dict_row, autocommit=True) as c:
        ev = c.execute("SELECT * FROM outbox.evento").fetchall()
        assert len(ev) == 1 and ev[0].pop("destino") == "aviso" and ev == antes        # lo existente queda como aviso
        alm = c.execute("INSERT INTO obra.almacen (proyecto_id, nombre) VALUES (%s, 'Central') RETURNING id", (p,)).fetchone()["id"]
        c.execute("INSERT INTO almacen.almacen_ref VALUES (%s, %s, 'Central', 'frente', 'activo')", (alm, p))
        c.execute("INSERT INTO almacen.insumo_ref (id, codigo, descripcion, unidad, tipo, activo) VALUES (1, '0221000000', 'Cemento', 'bls', 'material', true)")
        c.execute("INSERT INTO almacen.movimiento (uuid_cliente, proyecto_id, almacen_id, insumo_id, tipo, sentido, cantidad, costo_unitario, origen, registrado_por) "
                  "VALUES (%s, %s, %s, 1, 'ingreso_sin_compra', 1, 5, 21, 'sobrante', %s)", (uuid.uuid4(), p, alm, adm))
        c.execute("INSERT INTO outbox.evento (tipo, proyecto_id, payload, destino) VALUES ('almacen.ingreso_registrado', %s, '{\"hechos\": []}', 'integracion')", (p,))
    alembic(base_0010, "downgrade", "0009")                       # con un evento de integración encima: no se pierde nada
    with psycopg.connect(dsn(base_0010), row_factory=dict_row, autocommit=True) as c:
        assert c.execute("SELECT to_regnamespace('almacen') IS NULL AND to_regnamespace('costo') IS NULL AS ok").fetchone()["ok"]
        assert c.execute("SELECT count(*) AS n FROM outbox.evento").fetchone()["n"] == 2
        assert "destino" not in c.execute("SELECT * FROM outbox.evento LIMIT 1").fetchone()
    alembic(base_0010, "upgrade", "head")
    assert "No new upgrade operations detected" in alembic(base_0010, "check")
    alembic(base_0010, "downgrade", "base")
    alembic(base_0010, "upgrade", "head")
