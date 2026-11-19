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


# ═══════════════════════════════════ T-340 · insumo que el S10 no trae, agregado a mano
def agregar(mundo, eq, codigo="0203020004", cantidad="120", precio="4.54", motivo="Partida adicional de muro", quien="residente"):
    return post(mundo, f"/obras/{eq['proyecto']}/requerimiento", eq[quien],
                insumo_codigo=codigo, cantidad=cantidad, precio=precio, motivo=motivo)


def test_agregar_a_mano_queda_marcado_con_su_motivo_y_autor(mundo, eq):
    importar(mundo, eq, [fila("0221000000")])
    r = agregar(mundo, eq)
    assert r.status_code == 201, r.text
    j = r.json()
    assert j["origen"] == "manual" and j["motivo_alta"] == "Partida adicional de muro" and j["costo_planificado"] == "544.80"
    v = {x["insumo_codigo"]: x for x in get(mundo, f"/obras/{eq['proyecto']}/requerimiento", eq["gerente"]).json()}
    assert v["0221000000"]["origen"] == "s10" and v["0221000000"]["motivo_alta"] is None
    assert v["0203020004"]["origen"] == "manual"
    assert mundo.f.q1("SELECT creado_por AS id FROM compras.requerimiento WHERE id=%s", (j["id"],)) == eq["residente"]
    # lo agregado a mano cuenta para el saldo por comprar como cualquier otra línea
    s = {x["insumo_codigo"]: x for x in get(mundo, f"/obras/{eq['proyecto']}/saldo-por-comprar", eq["residente"]).json()}
    assert s["0203020004"]["requerido"] == "120.0000"


def test_agregar_no_duplica_lo_ya_planificado_ni_acepta_insumos_fuera_del_catalogo(mundo, eq):
    importar(mundo, eq, [fila("0221000000")])
    r = agregar(mundo, eq, codigo="0221000000")
    assert r.status_code == 409 and r.json()["codigo"] == "ya_planificado"
    assert agregar(mundo, eq, codigo="9999999999").json()["codigo"] == "insumo_desconocido"
    assert agregar(mundo, eq).status_code == 201
    assert agregar(mundo, eq).status_code == 409                                    # dos veces, tampoco
    assert len(get(mundo, f"/obras/{eq['proyecto']}/requerimiento", eq["residente"]).json()) == 2


def test_agregar_exige_motivo_cantidad_positiva_y_no_acepta_campos_de_mas(mundo, eq):
    assert agregar(mundo, eq, motivo="").status_code == 422
    assert agregar(mundo, eq, cantidad="0").status_code == 422
    assert agregar(mundo, eq, precio="-1").status_code == 422
    r = post(mundo, f"/obras/{eq['proyecto']}/requerimiento", eq["residente"], insumo_codigo="0203020004", cantidad="1",
             precio="1", motivo="Partida nueva", origen="s10")
    assert r.status_code == 422


def test_lo_agregado_a_mano_se_ajusta_con_motivo_y_reimportar_el_s10_no_lo_pisa(mundo, eq):
    rid = agregar(mundo, eq).json()["id"]
    r = post(mundo, f"/obras/{eq['proyecto']}/requerimiento/{rid}/ajustar", eq["admin"], cantidad="150", motivo="Metrado corregido")
    assert r.status_code == 200 and r.json()["origen"] == "manual"
    imp = importar(mundo, eq, [fila("0203020004", "36357", "4.54")]).json()
    assert imp["creados"] == 0 and "No se pisó" in imp["advertencias"][0]
    assert get(mundo, f"/obras/{eq['proyecto']}/requerimiento", eq["residente"]).json()[0]["cantidad_planificada"] == "150.0000"


def test_agregar_solo_residente_y_administrador_y_en_su_obra(mundo, eq):
    for rol in ("almacenero", "ssoma", "gerente"):
        assert agregar(mundo, eq, quien=rol).status_code == 403, rol
    otra = mundo.f.obra_completa()
    assert post(mundo, f"/obras/{otra['proyecto']}/requerimiento", eq["residente"], insumo_codigo="0203020004",
                cantidad="1", precio="1", motivo="Partida nueva").status_code == 403
    assert agregar(mundo, eq, quien="admin").status_code == 201


def test_el_motor_no_deja_cambiar_el_origen_ni_el_motivo_de_alta(mundo, eq):
    rid = agregar(mundo, eq).json()["id"]
    for sql in ("UPDATE compras.requerimiento SET origen='s10', motivo_alta=NULL WHERE id=%s RETURNING 1",
                "UPDATE compras.requerimiento SET motivo_alta='otro' WHERE id=%s RETURNING 1"):
        mundo.f.c.execute("SAVEPOINT intento")                                        # cada intento, por separado
        with pytest.raises(psycopg.errors.Error) as e:
            mundo.f.q1(sql, (rid,))
        mundo.f.c.execute("ROLLBACK TO SAVEPOINT intento")
        assert "SIGRO-Q1" in str(e.value) or "permission" in str(e.value).lower()


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


# ═══════════════════════════════════ HU-23 avance de compra y consumo
def test_avance_en_unidad_de_compra_con_porcentaje_sobre_el_requerimiento(mundo, eq):
    """Acero fy=4200: 36 357 kg en el S10, se compra por varilla de 7.12 kg (factor). 1 000 varillas = 7 120 kg."""
    mundo.f.q1("UPDATE catalogo.insumo SET factor_conversion = 7.12 WHERE id = %s RETURNING id", (eq["fierro"],))
    importar(mundo, eq, [fila("0221000000"), fila("0203020004", "36357", "4.54")])
    comprar(mundo, eq, eq["cem"], "100")
    comprar(mundo, eq, eq["cem"], "50", anular=True)                 # anulada: no cuenta (D-R2)
    comprar(mundo, eq, eq["fierro"], "1000", unidad="var")
    r = get(mundo, f"/obras/{eq['proyecto']}/avance", eq["residente"])
    assert r.status_code == 200, r.text
    d = r.json()
    assert d["unidad"] == "unidad_compra" and d["base_porcentaje"] == "requerimiento_total"
    assert [x["insumo_codigo"] for x in d["filas"]] == ["0203020004", "0221000000"]      # ordenadas por código
    por = {x["insumo_codigo"]: x for x in d["filas"]}
    c = por["0221000000"]
    assert (c["requerido"], c["comprado"], c["saldo_por_comprar"], c["pct_comprado"]) == ("400.0000", "100.0000", "300.0000", "25.00")
    a = por["0203020004"]
    # 36 357 kg / 7.12 kg por varilla = 5 106.3202 varillas; 1 000 compradas = 19.58 %
    assert a["requerido"] == "5106.3202" and a["comprado"] == "1000.0000" and a["pct_comprado"] == "19.58"


def test_avance_sin_movimientos_trae_recibido_y_consumido_en_cero(mundo, eq):
    """Almacén responde y no hay movimientos: 0 es un dato real, no un hueco."""
    importar(mundo, eq, [fila("0221000000")])
    d = get(mundo, f"/obras/{eq['proyecto']}/avance", eq["gerente"]).json()
    assert d["pendiente"] == []
    f0 = d["filas"][0]
    assert (f0["recibido"], f0["consumido"], f0["pct_recibido"], f0["pct_consumido"]) == ("0.0000", "0.0000", "0.00", "0.00")


def test_avance_declara_lo_que_depende_de_almacen_si_no_responde_en_vez_de_inventarlo(mundo, eq, monkeypatch):
    from app import internos
    from app.errores import ErrorSigro
    real = internos.json_de

    def almacen_caido(servicio, metodo, ruta, **kw):
        if servicio == "almacen":
            raise ErrorSigro(503, "servicio_no_disponible", "almacén no respondió")
        return real(servicio, metodo, ruta, **kw)
    monkeypatch.setattr(internos, "json_de", almacen_caido)
    importar(mundo, eq, [fila("0221000000")])
    d = get(mundo, f"/obras/{eq['proyecto']}/avance", eq["gerente"]).json()
    assert d["pendiente"] and "almacén" in d["pendiente"][0]
    f0 = d["filas"][0]
    assert f0["recibido"] is None and f0["consumido"] is None and f0["pct_recibido"] is None and f0["pct_consumido"] is None


def test_avance_lo_rechaza_el_servidor_sin_rol_o_en_otra_obra(mundo, eq):
    importar(mundo, eq, [fila("0221000000")])
    ruta = f"/obras/{eq['proyecto']}/avance"
    assert get(mundo, ruta, eq["almacenero"]).status_code == 403          # el almacenero no ve el avance de compra
    assert get(mundo, ruta, eq["ssoma"]).status_code == 403
    otra = mundo.f.obra_completa()
    assert get(mundo, ruta, otra["residente"]).status_code == 403          # regla 9: residente de OTRA obra
    assert mundo.http.get(f"{U}{ruta}").status_code == 401


def test_porcentaje_y_conversion_sin_base():
    from decimal import Decimal as D
    from app.modulos.compras.requerimiento import a_unidad_compra, porcentaje
    assert porcentaje(D("1"), D("3")) == D("33.33") and porcentaje(D("2"), D("3")) == D("66.67")
    assert porcentaje(D("5"), D("0")) == D("0.00") and porcentaje(None, D("10")) is None
    assert a_unidad_compra(D("36357"), D("7.12")) == D("5106.3202")


# ═══════════════════════════════════ HU-28 insumos críticos
def _marcar(mundo, eq, codigo, critico=True, quien="residente"):
    return post(mundo, f"/obras/{eq['proyecto']}/criticos", eq[quien], insumo_codigo=codigo, critico=critico)


def test_el_residente_marca_y_desmarca_y_cada_cambio_deja_su_fila(mundo, eq):
    importar(mundo, eq, [fila("0221000000"), fila("0203020004", "100", "4.54")])
    ok = _marcar(mundo, eq, "0221000000")
    assert ok.status_code == 201 and ok.json()["critico"] is True and ok.json()["insumo_codigo"] == "0221000000"
    assert [c["insumo_codigo"] for c in get(mundo, f"/obras/{eq['proyecto']}/criticos", eq["residente"]).json()] == ["0221000000"]
    assert _marcar(mundo, eq, "0221000000", critico=False).status_code == 201
    assert get(mundo, f"/obras/{eq['proyecto']}/criticos", eq["residente"]).json() == []
    # nada se borró: las dos marcas siguen en el libro
    assert mundo.f.q1("SELECT count(*) AS id FROM compras.insumo_critico WHERE proyecto_id = %s", (eq["proyecto"],)) == 2


def test_el_filtro_solo_criticos_deja_solo_los_marcados_en_requerimiento_y_avance(mundo, eq):
    importar(mundo, eq, [fila("0221000000"), fila("0203020004", "100", "4.54")])
    _marcar(mundo, eq, "0203020004")
    req = get(mundo, f"/obras/{eq['proyecto']}/requerimiento?solo_criticos=true", eq["residente"]).json()
    assert [r["insumo_codigo"] for r in req] == ["0203020004"] and req[0]["critico"] is True
    todos = get(mundo, f"/obras/{eq['proyecto']}/requerimiento", eq["residente"]).json()
    assert {r["insumo_codigo"]: r["critico"] for r in todos} == {"0221000000": False, "0203020004": True}
    av = get(mundo, f"/obras/{eq['proyecto']}/avance?solo_criticos=true", eq["residente"]).json()
    assert [f["insumo_codigo"] for f in av["filas"]] == ["0203020004"] and av["filas"][0]["critico"] is True


def test_los_candidatos_vienen_ordenados_por_valor_y_no_se_marca_nada(mundo, eq):
    importar(mundo, eq, [fila("0221000000", "10", "10"), fila("0203020004", "1000", "4.54")])   # 100 contra 4 540
    c = get(mundo, f"/obras/{eq['proyecto']}/criticos/candidatos", eq["residente"]).json()
    assert [x["insumo_codigo"] for x in c] == ["0203020004", "0221000000"] and not any(x["critico"] for x in c)
    assert get(mundo, f"/obras/{eq['proyecto']}/criticos", eq["residente"]).json() == []


def test_solo_el_residente_y_el_administrador_marcan_y_solo_en_su_obra(mundo, eq):
    importar(mundo, eq, [fila("0221000000")])
    assert _marcar(mundo, eq, "0221000000", quien="almacenero").status_code == 403
    assert _marcar(mundo, eq, "0221000000", quien="gerente").status_code == 403
    otra = mundo.f.obra_completa()
    assert post(mundo, f"/obras/{eq['proyecto']}/criticos", otra["residente"], insumo_codigo="0221000000", critico=True).status_code in (403, 404)


def test_no_se_marca_lo_que_no_esta_en_el_requerimiento(mundo, eq):
    importar(mundo, eq, [fila("0221000000")])
    r = _marcar(mundo, eq, "0203020004")
    assert r.status_code == 422 and r.json()["codigo"] == "insumo_fuera_del_requerimiento"
