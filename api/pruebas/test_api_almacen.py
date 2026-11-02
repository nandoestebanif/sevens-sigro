"""Servicio almacén (0010) por el gateway, cada servicio con SU usuario de base: HU-35, 40, 41, 42, 43, 45, 46.

Reglas críticas que se prueban aquí: idempotencia (R3), el libro no se borra ni se edita (R1), el saldo lo calcula el
servidor (R2), el costo promedio se congela en la salida (R4), dispositivo autorizado (R5) y acceso rechazado desde el
servidor (R9).
"""
from __future__ import annotations

import uuid
from decimal import Decimal as D

import pytest

U = "/api/v1"


@pytest.fixture
def ob(mundo):
    e = mundo.f.obra_completa()
    p = e["proyecto"]
    e["central"] = mundo.f.almacen(p, "Central", "central")
    e["cem"] = mundo.f.insumo("0221000000", unidad="bls")
    e["acero"] = mundo.f.insumo("0203020004", unidad="kg")
    mundo.f.q1("UPDATE catalogo.insumo SET factor_conversion = 7.12, unidad_compra = 'var' WHERE id = %s RETURNING id", (e["acero"],))
    e["cel"] = mundo.f.celular(p, e["almacenero"], e["admin"])
    return e


def post(mundo, ruta, uid, **cuerpo):
    return mundo.http.post(f"{U}{ruta}", headers=mundo.con(uid), json=cuerpo)


def get(mundo, ruta, uid, **params):
    return mundo.http.get(f"{U}{ruta}", headers=mundo.con(uid), params=params)


def ingreso(mundo, e, cantidad, costo, insumo=None, almacen=None, **kw):
    cuerpo = {"uuid_cliente": str(uuid.uuid4()), "almacen_id": almacen or e["central"], "insumo_id": insumo or e["cem"],
              "cantidad": str(cantidad), "costo_unitario": str(costo), "origen": "sobrante", **kw}
    return post(mundo, f"/obras/{e['proyecto']}/almacen/ingresos", e["almacenero"], **cuerpo)


def salida(mundo, e, cantidad, uid=None, quien=None, **kw):
    cuerpo = {"uuid_cliente": str(uid or uuid.uuid4()), "almacen_id": e["central"], "insumo_id": e["cem"], "cantidad": str(cantidad),
              "motivo": "Vaciado de losa", "quien_retira": "Capataz Juan Quispe", "hora_dispositivo_ms": 1793206800000,
              "dispositivo": e["cel"], **kw}
    return post(mundo, f"/obras/{e['proyecto']}/almacen/salidas", quien or e["almacenero"], **cuerpo)


def stock(mundo, e, quien="residente"):
    return get(mundo, f"/obras/{e['proyecto']}/almacen/stock", e[quien]).json()


def saldo(mundo, e, insumo=None, almacen=None):
    s = stock(mundo, e)
    fila = next((r for r in s["por_almacen"] if r["insumo_id"] == (insumo or e["cem"]) and r["almacen_id"] == (almacen or e["central"])), None)
    return fila and (D(fila["cantidad"]), fila["costo_promedio"] and D(fila["costo_promedio"]))


# ═══════════════════════════════════ HU-40 salida · R4 promedio ponderado congelado
def test_la_salida_toma_el_promedio_vigente_y_un_ingreso_posterior_no_la_cambia(mundo, ob):
    assert ingreso(mundo, ob, 100, 20).status_code == 201
    assert ingreso(mundo, ob, 50, 26).status_code == 201
    assert saldo(mundo, ob) == (D("150"), D("22"))                         # (100×20 + 50×26) / 150
    r = salida(mundo, ob, 30)
    assert r.status_code == 201, r.text
    s = r.json()
    assert D(s["costo_unitario"]) == D("22") and D(s["importe"]) == D("660.00") and D(s["saldo"]) == D("120")
    assert s["fecha_servidor"] and s["fecha_dispositivo"] and s["dispositivo_id"] and s["periodo_id"]
    ingreso(mundo, ob, 80, 30)
    k = get(mundo, f"/obras/{ob['proyecto']}/almacen/kardex", ob["gerente"], insumo_id=ob["cem"]).json()
    assert D(next(x for x in k if x["id"] == s["id"])["costo_unitario"]) == D("22")    # congelado (NIC 2)
    assert saldo(mundo, ob) == (D("200"), D("25.2"))                       # (120×22 + 80×30) / 200


def test_el_cliente_no_puede_mandar_el_costo_de_la_salida(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    r = salida(mundo, ob, 1, costo_unitario="999")
    assert r.status_code == 422 and r.json()["codigo"] == "datos_invalidos"   # el contrato no tiene ese campo


def test_no_se_saca_mas_de_lo_disponible_en_ese_almacen(mundo, ob):
    otro = mundo.f.almacen(ob["proyecto"], "Frente B")
    ingreso(mundo, ob, 10, 20, almacen=otro)                              # hay stock, pero en OTRO almacén
    r = salida(mundo, ob, 1)
    assert r.status_code == 422 and r.json()["codigo"] == "stock_insuficiente"
    ingreso(mundo, ob, 5, 20)
    r = salida(mundo, ob, D("5.0001"))
    assert r.status_code == 422 and "disponible 5" in r.json()["mensaje"]
    assert salida(mundo, ob, 5).status_code == 201 and saldo(mundo, ob)[0] == 0


def test_la_salida_exige_motivo_quien_retira_y_el_frente_es_opcional(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    assert salida(mundo, ob, 1, motivo="").status_code == 422
    assert salida(mundo, ob, 1, quien_retira="").status_code == 422
    r = salida(mundo, ob, 1, frente="Torre B · piso 3")
    assert r.status_code == 201 and r.json()["frente"] == "Torre B · piso 3"


def test_sin_almacen_se_usa_el_unico_activo_y_con_dos_se_exige(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    r = salida(mundo, ob, 1, almacen_id=None)
    assert r.status_code == 201 and r.json()["almacen_id"] == ob["central"]     # la app de hoy no lo manda: sigue funcionando
    mundo.f.almacen(ob["proyecto"], "Frente B")
    r = salida(mundo, ob, 1, almacen_id=None)
    assert r.status_code == 422 and r.json()["codigo"] == "almacen_requerido"


def test_la_salida_en_unidad_de_compra_se_convierte_con_el_factor(mundo, ob):
    """Acero fy=4200: el S10 lo lleva en kg; el almacenero entrega varillas de 7.12 kg (factor), nunca a mano."""
    ingreso(mundo, ob, 712, D("4.54"), insumo=ob["acero"])
    r = salida(mundo, ob, 10, insumo_id=ob["acero"], unidad="var")
    assert r.status_code == 201 and D(r.json()["cantidad"]) == D("71.2") and r.json()["unidad"] == "kg"
    assert salida(mundo, ob, 1, insumo_id=ob["acero"], unidad="m3").json()["codigo"] == "unidad_invalida"


# ═══════════════════════════════════ R3 un reenvío no duplica
def test_un_reenvio_con_el_mismo_uuid_devuelve_el_original_sin_duplicar(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    u = uuid.uuid4()
    a = salida(mundo, ob, 3, uid=u)
    b = salida(mundo, ob, 3, uid=u)
    assert (a.status_code, b.status_code) == (201, 200)
    assert a.json() == b.json()
    assert saldo(mundo, ob)[0] == D("7")
    assert mundo.f.c.execute("SELECT count(*) AS n FROM almacen.movimiento WHERE uuid_cliente = %s", (str(u),)).fetchone()["n"] == 1


def test_un_uuid_usado_en_otra_obra_o_para_otra_cosa_se_rechaza(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    u = uuid.uuid4()
    salida(mundo, ob, 1, uid=u)
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/ingresos", ob["almacenero"], uuid_cliente=str(u), insumo_id=ob["cem"],
             cantidad="1", costo_unitario="1", origen="sobrante")
    assert r.status_code == 409 and r.json()["codigo"] == "uuid_reutilizado"


# ═══════════════════════════════════ R5 dispositivo autorizado
def test_la_salida_solo_entra_desde_el_celular_autorizado_de_esa_persona_en_esa_obra(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    pendiente = mundo.f.celular(ob["proyecto"], ob["almacenero"], ob["admin"], estado="pendiente")
    ajeno = mundo.f.celular(ob["proyecto"], ob["ssoma"], ob["admin"])              # autorizado, pero de OTRA persona
    otra = mundo.f.obra_completa()
    mundo.f.asignar(ob["almacenero"], otra["proyecto"], "almacenero", otra["admin"])
    de_otra_obra = mundo.f.celular(otra["proyecto"], ob["almacenero"], otra["admin"])
    for disp in (pendiente, ajeno, de_otra_obra, "sigro-android-inventado-0001"):
        r = salida(mundo, ob, 1, dispositivo=disp)
        assert r.status_code == 403 and r.json()["codigo"] == "dispositivo_no_autorizado", disp
    assert salida(mundo, ob, 1, dispositivo=None).status_code == 422                  # el contrato lo exige
    mundo.conn.exec_driver_sql("UPDATE obra.dispositivo SET estado = 'revocado', revocado_por = %s, fecha_revocacion = now(), "
                               "motivo_revocacion = 'perdido' WHERE identificador = %s", (ob["admin"], ob["cel"]))
    assert salida(mundo, ob, 1).json()["codigo"] == "dispositivo_no_autorizado"       # revocado: corta al instante
    assert saldo(mundo, ob)[0] == D("10")


# ═══════════════════════════════════ R9 control de acceso en el servidor
def test_el_acceso_se_rechaza_desde_el_servidor(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    for quien in ("ssoma", "residente", "admin", "gerente"):
        r = salida(mundo, ob, 1, quien=ob[quien])
        assert r.status_code == 403 and r.json()["codigo"] == "sin_permiso", quien
    otra = mundo.f.obra_completa()
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/salidas", otra["almacenero"], uuid_cliente=str(uuid.uuid4()),
             insumo_id=ob["cem"], cantidad="1", motivo="x", quien_retira="y", dispositivo=ob["cel"])
    assert r.status_code == 403                                                      # almacenero de OTRA obra
    assert get(mundo, f"/obras/{ob['proyecto']}/almacen/stock", otra["gerente"]).status_code == 403
    assert mundo.http.get(f"{U}/obras/{ob['proyecto']}/almacen/stock").status_code == 401
    for quien in ("almacenero", "ssoma", "residente", "admin", "gerente"):           # consultar el stock: todos (matriz)
        assert get(mundo, f"/obras/{ob['proyecto']}/almacen/stock", ob[quien]).status_code == 200


# ═══════════════════════════════════ R1 el libro no se modifica ni se borra
def test_el_servicio_no_puede_editar_ni_borrar_el_libro(mundo, ob):
    from sqlalchemy.exc import ProgrammingError
    ingreso(mundo, ob, 10, 20)
    for sql in ("UPDATE almacen.movimiento SET cantidad = 1", "DELETE FROM almacen.movimiento"):
        sp = mundo.conn.begin_nested()
        mundo.conn.exec_driver_sql("SET LOCAL ROLE sigro_svc_almacen")
        with pytest.raises(ProgrammingError, match="permission denied|permiso denegado"):
            mundo.conn.exec_driver_sql(sql)
        sp.rollback()


# ═══════════════════════════════════ HU-45 reverso
def test_el_reverso_apunta_al_original_y_el_saldo_considera_ambos(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    s = salida(mundo, ob, 4).json()
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/movimientos/{s['id']}/reverso", ob["residente"],
             uuid_cliente=str(uuid.uuid4()), motivo="Se registró en el almacén equivocado")
    assert r.status_code == 201, r.text
    rv = r.json()
    assert (rv["tipo"], rv["sentido"], rv["revierte_a_id"], D(rv["cantidad"]), D(rv["costo_unitario"])) == ("reverso", 1, s["id"], D("4"), D("20"))
    assert rv["registrado_por"] == ob["residente"] and rv["motivo"] and rv["fecha_servidor"]
    assert saldo(mundo, ob)[0] == D("10")
    k = {x["id"]: x for x in get(mundo, f"/obras/{ob['proyecto']}/almacen/kardex", ob["almacenero"]).json()}
    assert k[s["id"]]["revertido_por_id"] == rv["id"] and k[s["id"]]["tipo"] == "salida"       # el original sigue ahí
    otra = post(mundo, f"/obras/{ob['proyecto']}/almacen/movimientos/{s['id']}/reverso", ob["almacenero"],
                uuid_cliente=str(uuid.uuid4()), motivo="otra vez")
    assert otra.status_code == 409 and otra.json()["codigo"] == "ya_revertido"
    rr = post(mundo, f"/obras/{ob['proyecto']}/almacen/movimientos/{rv['id']}/reverso", ob["almacenero"],
              uuid_cliente=str(uuid.uuid4()), motivo="deshacer")
    assert rr.status_code == 409 and rr.json()["codigo"] == "reverso_de_reverso"
    assert post(mundo, f"/obras/{ob['proyecto']}/almacen/movimientos/{s['id']}/reverso", ob["gerente"],
                uuid_cliente=str(uuid.uuid4()), motivo="x" * 5).status_code == 403


def test_revertir_un_ingreso_ya_consumido_no_deja_saldo_negativo(mundo, ob):
    i = ingreso(mundo, ob, 10, 20).json()
    salida(mundo, ob, 8)
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/movimientos/{i['id']}/reverso", ob["almacenero"],
             uuid_cliente=str(uuid.uuid4()), motivo="Ingreso duplicado")
    assert r.status_code == 422 and r.json()["codigo"] == "stock_insuficiente"


# ═══════════════════════════════════ D4 periodo cerrado: solo reversos
def test_en_un_periodo_cerrado_solo_entran_reversos(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    s = salida(mundo, ob, 2).json()
    mundo.conn.exec_driver_sql("UPDATE obra.periodo SET estado = 'cerrado', cerrado_en = now(), cerrado_por = %s WHERE id = %s",
                               (ob["admin"], s["periodo_id"]))
    r = salida(mundo, ob, 1)
    assert r.status_code == 409 and r.json()["codigo"] == "periodo_cerrado"
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/movimientos/{s['id']}/reverso", ob["almacenero"],
             uuid_cliente=str(uuid.uuid4()), motivo="Corrección después del cierre", hora_dispositivo_ms=1793206800000)
    assert r.status_code == 201


# ═══════════════════════════════════ HU-41 ingreso sin compra · D5
def test_ingreso_sin_compra_exige_origen_y_costo_y_saldo_de_otra_obra_solo_si_no_esta_en_sigro(mundo, ob):
    assert ingreso(mundo, ob, 5, 0).status_code == 422                                   # sin costo no se valoriza
    assert ingreso(mundo, ob, 5, 21, origen="otro").status_code == 422                   # «otro» exige detalle
    assert ingreso(mundo, ob, 5, 21, origen="robado").status_code == 422
    otra = mundo.f.obra_completa(nombre="Residencial  Los Álamos")
    r = ingreso(mundo, ob, 5, 21, origen="saldo_otra_obra", origen_detalle="residencial los alamos")
    assert r.status_code == 422 and r.json()["codigo"] == "usar_traslado" and otra
    r = ingreso(mundo, ob, 5, 21, origen="saldo_otra_obra", origen_detalle="Obra Surco 2024 (antes de SIGRO)")
    assert r.status_code == 201 and r.json()["tipo"] == "ingreso_sin_compra" and r.json()["origen"] == "saldo_otra_obra"


# ═══════════════════════════════════ HU-35 recepción de compra
def _compra(mundo, ob, insumo, cantidad, precio, unidad="bls", base=None, total=None):
    prov = mundo.f.proveedor()
    total = total or D(cantidad) * D(precio)
    base = base or (total / D("1.18")).quantize(D("0.01"))
    c = mundo.f.compra(ob["proyecto"], prov, ob["residente"], base=str(base), igv=str(total - base), total=str(total))
    mundo.f.detalle(c, insumo, cantidad=str(cantidad), unidad=unidad, precio=str(precio))
    return c


def test_recepcion_total_por_defecto_al_costo_sin_igv_y_no_mas_de_lo_comprado(mundo, ob):
    c = _compra(mundo, ob, ob["cem"], 400, "29.23")                    # 400 × 29.23 = 11 692.00 con IGV → base 9 908.47
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones", ob["almacenero"], uuid_cliente=str(uuid.uuid4()),
             compra_id=c, lineas=[{"insumo_id": ob["cem"], "cantidad": "150"}])
    assert r.status_code == 201, r.text
    d = r.json()
    assert d["lineas"] == [{"insumo_id": ob["cem"], "unidad_compra": "bls", "pedido": "400.0000", "recibido": "150.0000", "pendiente": "250.0000"}]
    assert D(d["movimientos"][0]["costo_unitario"]) == D("24.771175")     # 29.23 × 9908.47/11692 (sin IGV, NIC 2)
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones", ob["almacenero"], uuid_cliente=str(uuid.uuid4()),
             compra_id=c, lineas=[{"insumo_id": ob["cem"], "cantidad": "251"}])
    assert r.status_code == 422 and r.json()["codigo"] == "recepcion_excede_compra"
    u = str(uuid.uuid4())
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones", ob["almacenero"], uuid_cliente=u, compra_id=c)     # el resto
    assert r.status_code == 201 and r.json()["lineas"][0]["pendiente"] == "0.0000"
    again = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones", ob["almacenero"], uuid_cliente=u, compra_id=c)
    assert again.status_code == 200 and again.json()["id"] == r.json()["id"]                                       # R3
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones", ob["almacenero"], uuid_cliente=str(uuid.uuid4()), compra_id=c)
    assert r.status_code == 409 and r.json()["codigo"] == "compra_ya_recibida"
    assert saldo(mundo, ob)[0] == D("400")
    e = get(mundo, f"/obras/{ob['proyecto']}/almacen/compras/{c}/recepcion", ob["residente"]).json()
    assert e[0]["recibido"] == "400.0000"


def test_la_recepcion_del_acero_entra_en_kg_por_el_factor(mundo, ob):
    c = _compra(mundo, ob, ob["acero"], 100, "35.40", unidad="var", base=D("3540.00"), total=D("4177.20"))   # precios sin IGV
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones", ob["almacenero"], uuid_cliente=str(uuid.uuid4()), compra_id=c)
    m = r.json()["movimientos"][0]
    assert D(m["cantidad"]) == D("712") and D(m["costo_unitario"]) == D("4.971910")     # 35.40 / 7.12 por kg


def test_no_se_recibe_una_compra_de_otra_obra_ni_una_anulada(mundo, ob):
    otra = mundo.f.obra_completa()
    c = _compra(mundo, {"proyecto": otra["proyecto"], "residente": otra["residente"]}, ob["cem"], 10, "10")
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones", ob["almacenero"], uuid_cliente=str(uuid.uuid4()), compra_id=c)
    assert r.status_code == 404
    c2 = _compra(mundo, ob, ob["cem"], 10, "10")
    mundo.f.q1("UPDATE compras.compra SET estado='anulada', motivo_anulacion='x', anulada_por=%s, anulada_en=now() WHERE id=%s RETURNING id",
               (ob["residente"], c2))
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones", ob["almacenero"], uuid_cliente=str(uuid.uuid4()), compra_id=c2)
    assert r.status_code == 409 and r.json()["codigo"] == "compra_anulada"


# ═══════════════════════════════════ HU-42 traslado
def test_traslado_entre_obras_lleva_el_costo_y_se_anula_con_dos_reversos(mundo, ob):
    otra = mundo.f.obra_completa()
    destino = mundo.f.almacen(otra["proyecto"], "Central B", "central")
    ingreso(mundo, ob, 10, 20)
    ingreso(mundo, ob, 10, 30)                                           # promedio 25
    cuerpo = {"uuid_cliente": str(uuid.uuid4()), "origen_almacen_id": ob["central"], "destino_obra_id": otra["proyecto"],
              "destino_almacen_id": destino, "motivo": "Saldo para la obra B", "lineas": [{"insumo_id": ob["cem"], "cantidad": "6"}]}
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/traslados", ob["almacenero"], **cuerpo)
    assert r.status_code == 403                                          # regla 9: no está asignado en la obra de destino
    mundo.f.asignar(ob["almacenero"], otra["proyecto"], "almacenero", otra["admin"])
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/traslados", ob["almacenero"], **cuerpo)
    assert r.status_code == 201, r.text
    sal, ing = r.json()["movimientos"]
    assert sal["traslado_id"] == ing["traslado_id"] == r.json()["id"]
    assert (sal["tipo"], ing["tipo"], ing["proyecto_id"]) == ("traslado_salida", "traslado_ingreso", otra["proyecto"])
    assert D(sal["costo_unitario"]) == D(ing["costo_unitario"]) == D("25")
    assert post(mundo, f"/obras/{ob['proyecto']}/almacen/traslados", ob["almacenero"], **cuerpo).status_code == 200      # R3
    assert saldo(mundo, ob)[0] == D("14")
    lejos = {**cuerpo, "uuid_cliente": str(uuid.uuid4()), "lineas": [{"insumo_id": ob["cem"], "cantidad": "15"}]}
    assert post(mundo, f"/obras/{ob['proyecto']}/almacen/traslados", ob["almacenero"], **lejos).json()["codigo"] == "stock_insuficiente"
    mitad = post(mundo, f"/obras/{ob['proyecto']}/almacen/movimientos/{sal['id']}/reverso", ob["almacenero"],
                 uuid_cliente=str(uuid.uuid4()), motivo="solo la mitad")
    assert mitad.status_code == 409 and mitad.json()["codigo"] == "usar_anular_traslado"
    a = post(mundo, f"/obras/{ob['proyecto']}/almacen/traslados/{r.json()['id']}/anular", ob["almacenero"],
             uuid_cliente=str(uuid.uuid4()), motivo="Se canceló el envío")
    assert a.status_code == 201, a.text
    assert [m["tipo"] for m in a.json()["movimientos"]] == ["reverso", "reverso"]
    assert saldo(mundo, ob)[0] == D("20")
    d = get(mundo, f"/obras/{otra['proyecto']}/almacen/stock", otra["residente"]).json()["consolidado"]
    assert D(d[0]["cantidad"]) == 0


# ═══════════════════════════════════ HU-46 conteo físico
def test_conteo_teorico_del_servidor_motivo_obligatorio_y_ajuste_al_promedio(mundo, ob):
    ingreso(mundo, ob, 100, 20)
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/conteos", ob["almacenero"], uuid_cliente=str(uuid.uuid4()),
             almacen_id=ob["central"], fecha="2026-11-01")
    assert r.status_code == 201, r.text
    cid = r.json()["id"]
    sin = post(mundo, f"/obras/{ob['proyecto']}/almacen/conteos/{cid}/lineas", ob["almacenero"], insumo_id=ob["cem"], cantidad_contada="97")
    assert sin.status_code == 422 and sin.json()["codigo"] == "diferencia_sin_motivo"
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/conteos/{cid}/lineas", ob["almacenero"], insumo_id=ob["cem"],
             cantidad_contada="97", motivo="Bolsas rotas por la lluvia")
    l = r.json()["lineas"][0]
    assert (l["saldo_teorico"], l["cantidad_contada"], l["diferencia"]) == ("100.0000", "97.0000", "-3.0000")
    c = post(mundo, f"/obras/{ob['proyecto']}/almacen/conteos/{cid}/confirmar", ob["almacenero"])
    assert c.status_code == 200 and c.json()["estado"] == "confirmado"
    aj = c.json()["ajustes"]
    assert len(aj) == 1 and (aj[0]["sentido"], D(aj[0]["cantidad"]), D(aj[0]["costo_unitario"])) == (-1, D("3"), D("20"))
    assert post(mundo, f"/obras/{ob['proyecto']}/almacen/conteos/{cid}/confirmar", ob["almacenero"]).json()["ajustes"] == aj
    assert saldo(mundo, ob)[0] == D("97")


def test_si_el_saldo_cambio_desde_el_conteo_no_se_ajusta_a_ciegas(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    cid = post(mundo, f"/obras/{ob['proyecto']}/almacen/conteos", ob["almacenero"], uuid_cliente=str(uuid.uuid4()), fecha="2026-11-01").json()["id"]
    post(mundo, f"/obras/{ob['proyecto']}/almacen/conteos/{cid}/lineas", ob["almacenero"], insumo_id=ob["cem"], cantidad_contada="9", motivo="faltante")
    salida(mundo, ob, 1)
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/conteos/{cid}/confirmar", ob["almacenero"])
    assert r.status_code == 409 and r.json()["codigo"] == "saldo_cambio"


# ═══════════════════════════════════ HU-43 stock
def test_stock_por_almacen_y_consolidado_con_hora_y_punto_de_reposicion(mundo, ob):
    frente = mundo.f.almacen(ob["proyecto"], "Frente A")
    ingreso(mundo, ob, 30, 20)
    ingreso(mundo, ob, 10, 26, almacen=frente)
    mundo.http.post(f"{U}/obras/{ob['proyecto']}/parametros-reposicion", headers=mundo.con(ob["residente"]),
                    json={"insumo_codigo": "0221000000", "stock_seguridad": 20, "punto_reposicion": 50})
    s = stock(mundo, ob, "gerente")
    assert {(r["almacen"], r["cantidad"]) for r in s["por_almacen"]} == {("Central", "30.0000"), ("Frente A", "10.0000")}
    c = s["consolidado"][0]
    assert (c["cantidad"], D(c["costo_promedio"]), c["valor"], c["bajo_punto_reposicion"]) == ("40.0000", D("21.5"), "860.00", True)
    assert s["corte"] and c["ultimo_movimiento_en"] and "NIC 2" in s["criterio"]
    solo = get(mundo, f"/obras/{ob['proyecto']}/almacen/stock", ob["almacenero"], almacen_id=frente).json()
    assert [r["almacen"] for r in solo["por_almacen"]] == ["Frente A"]


def test_no_hay_columna_de_saldo_ni_de_costo_promedio(mundo):
    r = mundo.conn.exec_driver_sql(
        "SELECT table_name, column_name FROM information_schema.columns c JOIN information_schema.tables t USING (table_schema, table_name) "
        "WHERE table_schema IN ('almacen', 'costo') AND t.table_type = 'BASE TABLE' "
        "AND (column_name LIKE '%%saldo%%' OR column_name LIKE '%%promedio%%' OR column_name = 'stock')").fetchall()
    assert [tuple(x) for x in r] == [("conteo_linea", "saldo_teorico")]      # foto del momento del conteo, no un saldo vivo


# ═══════════════════════════════════ HU-23 lo que almacén responde a compras
def test_recibido_y_consumido_por_insumo_para_el_avance(mundo, ob):
    from app import internos
    c = _compra(mundo, ob, ob["cem"], 40, "11.80")
    post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones", ob["almacenero"], uuid_cliente=str(uuid.uuid4()), compra_id=c)
    s = salida(mundo, ob, 15).json()
    salida(mundo, ob, 5)
    post(mundo, f"/obras/{ob['proyecto']}/almacen/movimientos/{s['id']}/reverso", ob["almacenero"], uuid_cliente=str(uuid.uuid4()), motivo="mal")
    r = internos.json_de("almacen", "GET", f"/interno/obras/{ob['proyecto']}/recibido-consumido")
    assert [(x["insumo_id"], D(str(x["recibido"])), D(str(x["consumido"]))) for x in r] == [(ob["cem"], D("40"), D("5"))]
