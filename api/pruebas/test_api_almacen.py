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


def test_el_costo_del_ingreso_va_en_la_unidad_de_la_cantidad_y_el_acero_no_se_infla_7_veces(mundo, ob):
    """HU-41 / T-303: 100 varillas a S/ 35.40 la varilla = 712 kg = S/ 3 540. Antes el libro guardaba 712 kg a S/ 35.40 el kg."""
    r = ingreso(mundo, ob, 100, "35.40", insumo=ob["acero"], unidad="var")
    assert r.status_code == 201, r.text
    m = r.json()
    assert D(m["cantidad"]) == D("712") and m["unidad"] == "kg" and D(m["costo_unitario"]) == D("4.971910")
    assert D(m["importe"]) == D("3540.00")
    s = stock(mundo, ob)["consolidado"][0]
    assert D(s["valor"]) == D("3540.00") and D(s["cantidad"]) == D("712")
    # lo mismo dicho en kilos: 712 kg a S/ 4.97191 el kg; sin `unidad` es la del S10 y el costo no se toca
    r2 = ingreso(mundo, ob, 712, "4.97191", insumo=ob["acero"])
    assert r2.status_code == 201 and D(r2.json()["costo_unitario"]) == D("4.97191") and D(r2.json()["importe"]) == D("3540.00")


def test_el_costo_en_una_unidad_que_el_insumo_no_tiene_se_rechaza(mundo, ob):
    r = ingreso(mundo, ob, 5, 20, insumo=ob["acero"], unidad="m3")
    assert r.status_code == 422 and r.json()["codigo"] == "unidad_invalida"


def test_el_ingreso_sin_compra_se_distingue_de_la_recepcion_en_el_kardex_y_un_reenvio_no_duplica(mundo, ob):
    uid = str(uuid.uuid4())
    assert ingreso(mundo, ob, 10, 20, uuid_cliente=uid, origen="prestamo").status_code == 201
    assert ingreso(mundo, ob, 10, 20, uuid_cliente=uid, origen="prestamo").status_code == 200          # R3
    k = get(mundo, f"/obras/{ob['proyecto']}/almacen/kardex", ob["gerente"], insumo_id=ob["cem"]).json()
    assert [(x["tipo"], x["origen"]) for x in k] == [("ingreso_sin_compra", "prestamo")] and D(stock(mundo, ob)["consolidado"][0]["cantidad"]) == 10


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


# ═══════════════════════════════════ HU-36 recepción que pasa lo requerido
def _requerir(mundo, ob, insumo, cantidad):
    """Lo requerido por el S10 para la obra (HU-19), en la unidad del S10."""
    mundo.f.q1("INSERT INTO compras.requerimiento (proyecto_id, insumo_id, cantidad_planificada, precio_planificado, creado_por) "
               "VALUES (%s, %s, %s, 10, %s) RETURNING id", (ob["proyecto"], insumo, cantidad, ob["residente"]))


def _recibir(mundo, ob, c, **kw):
    return post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones", ob["almacenero"], uuid_cliente=kw.pop("uuid", str(uuid.uuid4())),
                compra_id=c, **kw)


def test_la_verificacion_avisa_el_exceso_antes_de_guardar_y_no_guarda_nada(mundo, ob):
    _requerir(mundo, ob, ob["cem"], 300)
    c = _compra(mundo, ob, ob["cem"], 400, "29.23")
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones/verificacion", ob["almacenero"], compra_id=c)
    assert r.status_code == 200, r.text
    v = r.json()
    assert v["hay_exceso"] is True and len(v["lineas"]) == 1
    l = v["lineas"][0]
    assert (D(l["requerido"]), D(l["ya_recibido"]), D(l["con_esta"]), D(l["exceso"]), l["excede"]) == (D("300"), D("0"), D("400"), D("100"), True)
    assert saldo(mundo, ob) is None                                       # verificar no mueve el libro


def test_pasarse_de_lo_requerido_exige_motivo_y_con_motivo_se_recibe_y_queda_en_el_kardex(mundo, ob):
    _requerir(mundo, ob, ob["cem"], 300)
    c = _compra(mundo, ob, ob["cem"], 400, "29.23")
    r = _recibir(mundo, ob, c)
    assert r.status_code == 422 and r.json()["codigo"] == "exceso_sin_motivo"
    assert saldo(mundo, ob) is None                                       # el rechazo no deja nada a medias
    u = str(uuid.uuid4())
    r = _recibir(mundo, ob, c, uuid=u, motivo_exceso="Desperdicio por lluvia en el vaciado")
    assert r.status_code == 201, r.text
    assert r.json()["advertencias"] and saldo(mundo, ob)[0] == D("400")  # avisa, no bloquea (regla 7)
    k = get(mundo, f"/obras/{ob['proyecto']}/almacen/kardex", ob["gerente"], insumo_id=ob["cem"]).json()
    assert k[0]["recepcion_motivo_exceso"] == "Desperdicio por lluvia en el vaciado"
    otra = _recibir(mundo, ob, c, uuid=u)                                 # el reenvío de la cola no vuelve a pedir motivo (R3)
    assert otra.status_code == 200 and otra.json()["id"] == r.json()["id"]


def test_dentro_de_lo_requerido_o_sin_requerimiento_no_se_pide_motivo(mundo, ob):
    _requerir(mundo, ob, ob["cem"], 500)
    c = _compra(mundo, ob, ob["cem"], 400, "29.23")
    v = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones/verificacion", ob["almacenero"], compra_id=c).json()
    assert v["hay_exceso"] is False and D(v["lineas"][0]["exceso"]) == 0
    assert _recibir(mundo, ob, c).status_code == 201
    c2 = _compra(mundo, ob, ob["acero"], 10, "35.40", unidad="var", base=D("354.00"), total=D("417.72"))   # el acero no está en el requerimiento
    v = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones/verificacion", ob["almacenero"], compra_id=c2).json()
    assert v["hay_exceso"] is False and v["lineas"][0]["requerido"] is None and v["lineas"][0]["exceso"] is None
    assert _recibir(mundo, ob, c2).status_code == 201


def test_el_exceso_del_acero_se_mide_en_kg_aunque_se_compre_en_varillas(mundo, ob):
    _requerir(mundo, ob, ob["acero"], 5000)                               # kg del S10
    c = _compra(mundo, ob, ob["acero"], 800, "35.40", unidad="var", base=D("28320.00"), total=D("33417.60"))   # 800 var × 7,12 = 5 696 kg
    l = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones/verificacion", ob["almacenero"], compra_id=c).json()["lineas"][0]
    assert l["unidad"] == "kg" and D(l["con_esta"]) == D("5696") and D(l["exceso"]) == D("696")
    assert _recibir(mundo, ob, c, lineas=[{"insumo_id": ob["acero"], "cantidad": "700"}]).status_code == 201   # 4 984 kg: dentro


def test_lo_ya_recibido_de_otras_compras_cuenta_para_el_exceso(mundo, ob):
    _requerir(mundo, ob, ob["cem"], 300)
    assert _recibir(mundo, ob, _compra(mundo, ob, ob["cem"], 250, "29.23")).status_code == 201
    c2 = _compra(mundo, ob, ob["cem"], 100, "29.23")
    v = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones/verificacion", ob["almacenero"], compra_id=c2).json()["lineas"][0]
    assert D(v["ya_recibido"]) == D("250") and D(v["exceso"]) == D("50") and v["excede"] is True


def test_la_verificacion_se_rechaza_en_otra_obra(mundo, ob):
    otra = mundo.f.obra_completa()
    c = _compra(mundo, {"proyecto": otra["proyecto"], "residente": otra["residente"]}, ob["cem"], 10, "10")
    r = post(mundo, f"/obras/{ob['proyecto']}/almacen/recepciones/verificacion", ob["almacenero"], compra_id=c)
    assert r.status_code == 404                                           # R9: el alcance es la obra


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


# ═══════════════════════════════════ HU-33 precio pagado contra lo habitual (se apoya en las recepciones)
def _registrar_compra(mundo, ob, codigo, cantidad, unidad, precio, numero, **linea_extra):
    prov = mundo.f.proveedor(validado=True)
    return post(mundo, f"/obras/{ob['proyecto']}/compras", ob["residente"], proveedor_id=prov, tipo_comprobante="factura",
                numero_comprobante=numero, fecha="2026-10-21",
                lineas=[{"insumo_codigo": codigo, "cantidad": str(cantidad), "unidad_compra": unidad, "precio_unitario": str(precio),
                         **linea_extra}])


def test_sin_recepciones_no_hay_precio_habitual_y_nada_se_marca(mundo, ob):
    r = get(mundo, f"/obras/{ob['proyecto']}/compras/precio-referencia", ob["residente"], insumo="0221000000")
    assert r.status_code == 200, r.text
    assert r.json()["promedio_ponderado"] is None and D(r.json()["limite_pct"]) == D("10")
    c = _registrar_compra(mundo, ob, "0221000000", 100, "bls", "29.23", "F001-7001")
    assert c.status_code == 201, c.text
    l = c.json()["lineas"][0]
    assert l["precio_referencia"] is None and l["fuera_de_lo_habitual"] is False and c.json()["advertencias"] == []


def test_el_precio_habitual_es_el_promedio_pagado_sin_igv_y_se_marca_lo_que_se_sale(mundo, ob):
    assert _recibir(mundo, ob, _compra(mundo, ob, ob["cem"], 400, "29.23")).status_code == 201   # 29.23 con IGV = 24.77 sin IGV
    ref = get(mundo, f"/obras/{ob['proyecto']}/compras/precio-referencia", ob["residente"], insumo="0221000000").json()
    assert D(ref["promedio_ponderado"]).quantize(D("0.01")) == D("24.77") and D(ref["cantidad_recibida_s10"]) == D("400")
    dentro = _registrar_compra(mundo, ob, "0221000000", 100, "bls", "30.00", "F001-7002")       # +2.6 %: dentro
    assert dentro.status_code == 201 and dentro.json()["lineas"][0]["fuera_de_lo_habitual"] is False
    caro = _registrar_compra(mundo, ob, "0221000000", 100, "bls", "33.90", "F001-7003", nota_precio="Subió en todo el mercado")
    assert caro.status_code == 201, caro.text                                                     # +16 %: avisa, no bloquea (D13)
    l = caro.json()["lineas"][0]
    assert l["fuera_de_lo_habitual"] is True and l["nota_precio"] == "Subió en todo el mercado"
    assert D(l["precio_referencia"]).quantize(D("0.01")) == D("24.77") and D(l["precio_unitario"]) == D("33.90")  # se guarda lo pagado
    assert len(caro.json()["advertencias"]) == 1 and "0221000000" in caro.json()["advertencias"][0]
    visto = get(mundo, f"/obras/{ob['proyecto']}/compras/{caro.json()['id']}", ob["gerente"]).json()["lineas"][0]
    assert visto["fuera_de_lo_habitual"] is True                                                 # el gerente también lo ve


def test_el_acero_se_compara_en_varillas_aunque_el_almacen_lleve_kg(mundo, ob):
    c = _compra(mundo, ob, ob["acero"], 100, "35.40", unidad="var", base=D("3000.00"), total=D("3540.00"))   # 30.00 sin IGV por varilla
    assert _recibir(mundo, ob, c).status_code == 201
    ref = get(mundo, f"/obras/{ob['proyecto']}/compras/precio-referencia", ob["residente"], insumo="0203020004").json()
    assert ref["unidad_compra"] == "var" and ref["unidad_s10"] == "kg"
    assert D(ref["promedio_ponderado"]).quantize(D("0.01")) == D("30.00")                        # no 4.21 (por kg)
    igual = _registrar_compra(mundo, ob, "0203020004", 50, "var", "35.40", "F001-7004")
    assert igual.json()["lineas"][0]["fuera_de_lo_habitual"] is False


def test_un_ingreso_sin_compra_no_cambia_el_precio_habitual(mundo, ob):
    assert _recibir(mundo, ob, _compra(mundo, ob, ob["cem"], 100, "29.50")).status_code == 201
    antes = get(mundo, f"/obras/{ob['proyecto']}/compras/precio-referencia", ob["residente"], insumo="0221000000").json()
    assert ingreso(mundo, ob, 10, "99.00").status_code == 201                                     # sobrante: no se le pagó a nadie
    despues = get(mundo, f"/obras/{ob['proyecto']}/compras/precio-referencia", ob["residente"], insumo="0221000000").json()
    assert despues["promedio_ponderado"] == antes["promedio_ponderado"]


def test_el_motor_no_acepta_una_marca_sin_referencia_ni_una_nota_vacia(mundo, ob):
    import psycopg
    prov = mundo.f.proveedor()
    c = mundo.f.compra(ob["proyecto"], prov, ob["residente"])
    with pytest.raises(psycopg.errors.CheckViolation):
        mundo.f.q1("INSERT INTO compras.compra_detalle (compra_id, insumo_id, cantidad, unidad, precio_unitario, fuera_de_lo_habitual) "
                   "VALUES (%s, %s, 1, 'bls', 1, true) RETURNING id", (c, ob["cem"]))
