"""Requerimiento (HU-19), saldo por comprar (HU-24) y parámetros de reposición (HU-25)."""
from __future__ import annotations

import psycopg
import pytest

U = "/api/v1"


@pytest.fixture
def eq(mundo):
    e = mundo.f.obra_completa()
    e["cem"] = mundo.f.insumo("0221000000", unidad="bls")
    e["fierro"] = mundo.f.insumo("0203020004", unidad="kg")
    return e


def post(mundo, ruta, uid, **cuerpo):
    return mundo.http.post(f"{U}{ruta}", headers=mundo.con(uid), json=cuerpo)


def get(mundo, ruta, uid):
    return mundo.http.get(f"{U}{ruta}", headers=mundo.con(uid))


def fila(codigo, cantidad="400", precio="29.23"):
    return {"insumo_codigo": codigo, "cantidad": cantidad, "precio": precio}


def importar(mundo, eq, filas, quien="residente"):
    return post(mundo, f"/obras/{eq['proyecto']}/requerimiento/importar", eq[quien], filas=filas)


def comprar(mundo, eq, insumo, cantidad, unidad="bls", anular=False):
    prov = mundo.f.proveedor()
    c = mundo.f.compra(eq["proyecto"], prov, eq["residente"])
    mundo.f.detalle(c, insumo, cantidad=cantidad, unidad=unidad)
    if anular:
        mundo.f.q1("UPDATE compras.compra SET estado='anulada', motivo_anulacion='x', anulada_por=%s, anulada_en=now() WHERE id=%s RETURNING id",
                   (eq["residente"], c))
    return c


# ═══════════════════════════════════ HU-19 requerimiento
def test_importar_crea_el_requerimiento_con_su_costo_planificado(mundo, eq):
    r = importar(mundo, eq, [fila("0221000000"), fila("0203020004", "36357", "4.54")])
    assert r.status_code == 200 and r.json()["creados"] == 2 and r.json()["fallidos"] == []
    v = get(mundo, f"/obras/{eq['proyecto']}/requerimiento", eq["gerente"]).json()
    assert {x["insumo_codigo"]: x["costo_planificado"] for x in v} == {"0221000000": "11692.00", "0203020004": "165060.78"}


def test_insumo_fuera_del_catalogo_es_fallido_y_el_resto_entra(mundo, eq):
    r = importar(mundo, eq, [fila("0221000000"), fila("9999999999")]).json()
    assert r["creados"] == 1 and r["fallidos"] == [{"insumo_codigo": "9999999999", "motivo": "El insumo no está en el catálogo."}]


def test_repetir_la_importacion_no_duplica_ni_pisa(mundo, eq):
    importar(mundo, eq, [fila("0221000000")])
    r = importar(mundo, eq, [fila("0221000000")]).json()
    assert r["creados"] == 0 and r["sin_cambios"] == 1 and r["advertencias"] == []
    r2 = importar(mundo, eq, [fila("0221000000", cantidad="999")]).json()
    assert r2["creados"] == 0 and "No se pisó" in r2["advertencias"][0]
    assert get(mundo, f"/obras/{eq['proyecto']}/requerimiento", eq["residente"]).json()[0]["cantidad_planificada"] == "400.0000"


def test_el_requerimiento_es_de_cada_obra(mundo, eq):
    otra = mundo.f.obra_completa()
    importar(mundo, eq, [fila("0221000000")])
    assert get(mundo, f"/obras/{otra['proyecto']}/requerimiento", otra["residente"]).json() == []
    assert get(mundo, f"/obras/{otra['proyecto']}/requerimiento", eq["residente"]).status_code == 403


def test_ajustar_guarda_el_historico_con_motivo_y_autor(mundo, eq):
    importar(mundo, eq, [fila("0221000000")])
    rid = get(mundo, f"/obras/{eq['proyecto']}/requerimiento", eq["residente"]).json()[0]["id"]
    r = post(mundo, f"/obras/{eq['proyecto']}/requerimiento/{rid}/ajustar", eq["residente"], cantidad="450", motivo="Adicional de obra")
    assert r.status_code == 200 and r.json()["cantidad_planificada"] == "450.0000" and r.json()["costo_planificado"] == "13153.50"
    h = get(mundo, f"/obras/{eq['proyecto']}/requerimiento/{rid}/ajustes", eq["gerente"]).json()
    assert len(h) == 1 and h[0]["cantidad_anterior"] == "400.0000" and h[0]["cantidad_nueva"] == "450.0000"
    assert h[0]["motivo"] == "Adicional de obra" and h[0]["ajustado_por"] == eq["residente"]


def test_ajustar_exige_motivo_cantidad_positiva_y_distinta(mundo, eq):
    importar(mundo, eq, [fila("0221000000")])
    rid = get(mundo, f"/obras/{eq['proyecto']}/requerimiento", eq["residente"]).json()[0]["id"]
    ruta = f"/obras/{eq['proyecto']}/requerimiento/{rid}/ajustar"
    assert post(mundo, ruta, eq["residente"], cantidad="450").status_code == 422
    assert post(mundo, ruta, eq["residente"], cantidad="0", motivo="cero").status_code == 422
    assert post(mundo, ruta, eq["residente"], cantidad="400", motivo="mismo valor").status_code == 422


def test_el_motor_rechaza_cambiar_la_cantidad_sin_ajuste(mundo, eq):
    importar(mundo, eq, [fila("0221000000")])
    with pytest.raises(psycopg.errors.Error) as e:
        mundo.f.q1("UPDATE compras.requerimiento SET cantidad_planificada = 1 RETURNING 1")
    assert "SIGRO-Q2" in str(e.value) or "permission" in str(e.value).lower()


def test_solo_residente_y_administrador_importan_y_ajustan(mundo, eq):
    for rol in ("almacenero", "ssoma", "gerente"):
        assert importar(mundo, eq, [fila("0221000000")], quien=rol).status_code == 403, rol
    assert importar(mundo, eq, [fila("0221000000")], quien="admin").status_code == 200


def test_importar_rechaza_vacio_negativos_y_campos_de_mas(mundo, eq):
    assert importar(mundo, eq, []).status_code == 422
    assert importar(mundo, eq, [fila("0221000000", cantidad="-1")]).status_code == 422
    assert importar(mundo, eq, [{**fila("0221000000"), "x": 1}]).status_code == 422


# ═══════════════════════════════════ HU-24 saldo por comprar
def test_saldo_es_requerido_menos_comprado(mundo, eq):
    importar(mundo, eq, [fila("0221000000", "400")])
    comprar(mundo, eq, eq["cem"], "150")
    s = get(mundo, f"/obras/{eq['proyecto']}/saldo-por-comprar", eq["residente"]).json()[0]
    assert (s["requerido"], s["comprado"], s["saldo"], s["exceso"], s["estado"]) == ("400.0000", "150.0000", "250.0000", "0.0000", "pendiente")


def test_compras_anuladas_no_cuentan(mundo, eq):
    importar(mundo, eq, [fila("0221000000", "400")])
    comprar(mundo, eq, eq["cem"], "150")
    comprar(mundo, eq, eq["cem"], "100", anular=True)
    assert get(mundo, f"/obras/{eq['proyecto']}/saldo-por-comprar", eq["residente"]).json()[0]["comprado"] == "150.0000"


def test_completo_y_excedido(mundo, eq):
    importar(mundo, eq, [fila("0221000000", "400"), fila("0203020004", "100", "4")])
    comprar(mundo, eq, eq["cem"], "400")
    comprar(mundo, eq, eq["fierro"], "130", unidad="kg")
    est = {s["insumo_codigo"]: (s["estado"], s["saldo"], s["exceso"]) for s in get(mundo, f"/obras/{eq['proyecto']}/saldo-por-comprar", eq["residente"]).json()}
    assert est["0221000000"] == ("completo", "0.0000", "0.0000") and est["0203020004"] == ("excedido", "0.0000", "30.0000")


def test_el_factor_convierte_la_unidad_de_compra_a_la_del_s10(mundo, eq):
    """Fierro: el S10 lo pide en kg y se compra por varilla; factor = kg por varilla."""
    mundo.f.q1("UPDATE catalogo.insumo SET factor_conversion = 7.12 WHERE id = %s RETURNING id", (eq["fierro"],))
    importar(mundo, eq, [fila("0203020004", "712", "4.54")])
    comprar(mundo, eq, eq["fierro"], "50", unidad="var")
    s = get(mundo, f"/obras/{eq['proyecto']}/saldo-por-comprar", eq["residente"]).json()[0]
    assert s["comprado"] == "356.0000" and s["saldo"] == "356.0000" and s["saldo_en_unidad_compra"] == "50.0000"


def test_solo_pendientes_filtra_y_otra_obra_no_mezcla(mundo, eq):
    importar(mundo, eq, [fila("0221000000", "400"), fila("0203020004", "100", "4")])
    comprar(mundo, eq, eq["cem"], "400")
    ruta = f"/obras/{eq['proyecto']}/saldo-por-comprar?solo_pendientes=true"
    assert [s["insumo_codigo"] for s in get(mundo, ruta, eq["residente"]).json()] == ["0203020004"]
    otra = mundo.f.obra_completa()
    assert get(mundo, f"/obras/{otra['proyecto']}/saldo-por-comprar", otra["residente"]).json() == []


def test_saldo_permisos(mundo, eq):
    for rol, esperado in (("residente", 200), ("admin", 200), ("gerente", 200), ("almacenero", 403), ("ssoma", 403)):
        assert get(mundo, f"/obras/{eq['proyecto']}/saldo-por-comprar", eq[rol]).status_code == esperado, rol


# ═══════════════════════════════════ HU-25 parámetros de reposición
def param(mundo, eq, codigo="0221000000", seg="50", punto="120", quien="residente"):
    return post(mundo, f"/obras/{eq['proyecto']}/parametros-reposicion", eq[quien], insumo_codigo=codigo, stock_seguridad=seg, punto_reposicion=punto)


def test_registrar_parametro_y_verlo_vigente(mundo, eq):
    r = param(mundo, eq)
    assert r.status_code == 201 and r.json()["stock_seguridad"] == "50.0000" and r.json()["punto_reposicion"] == "120.0000"
    v = get(mundo, f"/obras/{eq['proyecto']}/parametros-reposicion", eq["gerente"]).json()
    assert len(v) == 1 and v[0]["insumo_codigo"] == "0221000000" and v[0]["unidad_compra"] == "bls"


def test_cambiar_un_parametro_agrega_vigencia_y_no_pisa_la_anterior(mundo, eq):
    param(mundo, eq, seg="50", punto="120")
    param(mundo, eq, seg="80", punto="200", quien="admin")
    vigentes = get(mundo, f"/obras/{eq['proyecto']}/parametros-reposicion", eq["residente"]).json()
    assert len(vigentes) == 1 and vigentes[0]["punto_reposicion"] == "200.0000"
    h = get(mundo, f"/obras/{eq['proyecto']}/parametros-reposicion/{eq['cem']}/historial", eq["residente"]).json()
    assert [x["punto_reposicion"] for x in h] == ["200.0000", "120.0000"]


def test_punto_menor_que_el_seguridad_se_rechaza(mundo, eq):
    assert param(mundo, eq, seg="100", punto="50").status_code == 422


def test_parametro_negativo_o_insumo_desconocido(mundo, eq):
    assert param(mundo, eq, seg="-1", punto="5").status_code == 422
    r = param(mundo, eq, codigo="NOEXISTE")
    assert r.status_code == 404 and r.json()["codigo"] == "insumo_no_existe"


def test_el_parametro_es_de_cada_obra(mundo, eq):
    otra = mundo.f.obra_completa()
    param(mundo, eq)
    assert get(mundo, f"/obras/{otra['proyecto']}/parametros-reposicion", otra["residente"]).json() == []


def test_parametros_permisos_y_libro_inmutable(mundo, eq):
    for rol in ("almacenero", "ssoma", "gerente"):
        assert param(mundo, eq, quien=rol).status_code == 403, rol
    param(mundo, eq)
    for sql in ("UPDATE compras.parametro_reposicion SET stock_seguridad = 1", "DELETE FROM compras.parametro_reposicion"):
        with pytest.raises(psycopg.errors.Error):
            mundo.f.q1(sql + " RETURNING 1")
