"""API de compras: proveedores (HU-29/30/31), compra con comprobante (HU-32) y solicitud de materiales (CR-04)."""
from __future__ import annotations

import pytest

from app import integraciones
from pruebas.fabrica import ruc_nuevo

U = "/api/v1"
RUC_SEVENS = "20606456892"


@pytest.fixture
def eq(mundo):
    e = mundo.f.obra_completa()
    mundo.f.insumo("0221000000", unidad="bls")
    mundo.f.insumo("0203020004", unidad="kg")
    mundo.f.insumo("0205010033", unidad="m3")
    return e


def post(mundo, ruta, uid, **cuerpo):
    return mundo.http.post(f"{U}{ruta}", headers=mundo.con(uid), json=cuerpo)


def get(mundo, ruta, uid):
    return mundo.http.get(f"{U}{ruta}", headers=mundo.con(uid))


@pytest.fixture
def sunat(monkeypatch):
    """Simula el microservicio de integraciones: nada de red en las pruebas."""
    respuesta = {"valor": None}
    monkeypatch.setattr(integraciones, "consultar_ruc", lambda ruc, cliente=None: respuesta["valor"])
    return respuesta


def ruc_sunat(ruc, apto=True, estado="ACTIVO", condicion="HABIDO"):
    return integraciones.RucSunat(ruc, "RAZON SUNAT SAC", estado, condicion, apto)


def proveedor(mundo, eq, ruc=None, razon="Dinoselva Iquitos S.A.C."):
    r = post(mundo, "/proveedores", eq["residente"], ruc=ruc or ruc_nuevo(), razon_social=razon)
    assert r.status_code == 201, r.text
    return r.json()


def linea(codigo="0221000000", cantidad="400", unidad="bls", precio="29.23"):
    return {"insumo_codigo": codigo, "cantidad": cantidad, "unidad_compra": unidad, "precio_unitario": precio}


def comprar(mundo, eq, prov, numero="F001-4512", lineas=None, quien="residente", **kw):
    cuerpo = dict(proveedor_id=prov["id"], tipo_comprobante="factura", numero_comprobante=numero, fecha="2026-10-18",
                  lineas=lineas if lineas is not None else [linea()])
    cuerpo.update(kw)
    return post(mundo, f"/obras/{eq['proyecto']}/compras", eq[quien], **cuerpo)


# ══════════════════════════════════════════════ proveedores
def test_el_ruc_de_sevens_pasa_el_digito_verificador():
    from app.modulos.compras.dominio import ruc_valido
    assert ruc_valido(RUC_SEVENS) and not ruc_valido("20606456893")


def test_proveedor_sin_servicio_de_sunat_queda_sin_validar_y_no_se_bloquea(mundo, eq, sunat):
    sunat["valor"] = None
    p = proveedor(mundo, eq)
    assert p["estado"] == "sin_validar" and "No se pudo consultar a SUNAT" in p["advertencia"]


def test_proveedor_activo_y_habido_en_sunat_queda_validado(mundo, eq, sunat):
    ruc = ruc_nuevo()
    sunat["valor"] = ruc_sunat(ruc)
    p = proveedor(mundo, eq, ruc)
    assert p["estado"] == "validado" and p["advertencia"] is None
    fila = mundo.f.c.execute("SELECT validado_en FROM compras.proveedor WHERE id=%s", (p["id"],)).fetchone()
    assert fila["validado_en"] is not None


def test_proveedor_dado_de_baja_en_sunat_se_registra_sin_validar_con_aviso(mundo, eq, sunat):
    ruc = ruc_nuevo()
    sunat["valor"] = ruc_sunat(ruc, apto=False, estado="BAJA DEFINITIVA", condicion="NO HABIDO")
    p = proveedor(mundo, eq, ruc)
    assert p["estado"] == "sin_validar" and "BAJA DEFINITIVA" in p["advertencia"]


def test_ruc_con_digito_verificador_malo_no_se_consulta_y_queda_sin_validar(mundo, eq, sunat):
    sunat["valor"] = ruc_sunat("20606456893")           # si se consultara, "validaría": no debe llegar a eso
    p = proveedor(mundo, eq, "20606456893")
    assert p["estado"] == "sin_validar" and "dígito verificador" in p["advertencia"]


@pytest.mark.parametrize("ruc", ["1234567890", "123456789012", "2060645689A", "", " "])
def test_el_ruc_nuevo_debe_tener_once_digitos(mundo, eq, ruc):
    assert post(mundo, "/proveedores", eq["residente"], ruc=ruc, razon_social="Algo SAC").status_code == 422


def test_razon_social_obligatoria(mundo, eq):
    assert post(mundo, "/proveedores", eq["residente"], ruc=ruc_nuevo(), razon_social="").status_code == 422
    assert post(mundo, "/proveedores", eq["residente"], ruc=ruc_nuevo()).status_code == 422


def test_el_ruc_es_unico_en_todo_el_sistema(mundo, eq, sunat):
    ruc = ruc_nuevo()
    proveedor(mundo, eq, ruc)
    r = post(mundo, "/proveedores", eq["admin"], ruc=ruc, razon_social="Otra razón SAC")
    assert r.status_code == 409 and r.json() == {"codigo": "uq_proveedor_ruc", "mensaje": "Ya existe un proveedor con ese RUC."}


def test_quien_puede_registrar_y_consultar_proveedores(mundo, eq, sunat):
    nuevo = lambda rol: post(mundo, "/proveedores", eq[rol], ruc=ruc_nuevo(), razon_social="Proveedor SAC")
    assert nuevo("residente").status_code == 201 and nuevo("admin").status_code == 201
    for rol in ("almacenero", "ssoma", "gerente"):
        assert nuevo(rol).status_code == 403, rol
    for rol in ("residente", "admin", "gerente"):
        assert get(mundo, "/proveedores", eq[rol]).status_code == 200
    for rol in ("almacenero", "ssoma"):
        assert get(mundo, "/proveedores", eq[rol]).status_code == 403


def test_buscar_proveedores_por_razon_social_o_ruc(mundo, eq, sunat):
    a = proveedor(mundo, eq, razon="Acero Comercial S.R.L.")
    b = proveedor(mundo, eq, razon="Dinoselva Iquitos S.A.C.")
    por_nombre = get(mundo, "/proveedores?q=acero", eq["residente"]).json()
    assert [p["id"] for p in por_nombre] == [a["id"]]
    por_ruc = get(mundo, f"/proveedores?q={b['ruc']}", eq["residente"]).json()
    assert [p["id"] for p in por_ruc] == [b["id"]]
    assert len(get(mundo, "/proveedores", eq["residente"]).json()) == 2


def test_busqueda_no_se_deja_inyectar_comodines_ni_sql(mundo, eq, sunat):
    proveedor(mundo, eq)
    for q in ("'; DROP TABLE compras.proveedor; --", "%", "' OR '1'='1"):
        r = get(mundo, f"/proveedores?q={q}", eq["residente"])
        assert r.status_code == 200
    assert mundo.f.c.execute("SELECT count(*) AS n FROM compras.proveedor").fetchone()["n"] == 1


def test_baja_de_proveedor_lo_saca_de_compras_nuevas_pero_conserva_su_historia(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    assert comprar(mundo, eq, p).status_code == 201
    b = post(mundo, f"/proveedores/{p['id']}/baja", eq["residente"])
    assert b.status_code == 200 and b.json()["estado"] == "de_baja" and b.json()["compras_registradas"] == 1
    assert [x["id"] for x in get(mundo, "/proveedores?solo_para_comprar=true", eq["residente"]).json()] == []
    assert len(get(mundo, "/proveedores", eq["residente"]).json()) == 1
    nueva = comprar(mundo, eq, p, numero="F001-4513")
    assert nueva.status_code == 422 and "dado de baja" in nueva.json()["mensaje"]
    assert post(mundo, f"/proveedores/{p['id']}/baja", eq["residente"]).status_code == 404


def test_revalidar_un_proveedor_sin_validar(mundo, eq, sunat):
    ruc = ruc_nuevo()
    sunat["valor"] = None
    p = proveedor(mundo, eq, ruc)
    assert p["estado"] == "sin_validar"
    sunat["valor"] = ruc_sunat(ruc)
    r = post(mundo, f"/proveedores/{p['id']}/validar", eq["admin"])
    assert r.status_code == 200 and r.json()["estado"] == "validado" and r.json()["advertencia"] is None
    assert post(mundo, "/proveedores/999999/validar", eq["admin"]).status_code == 404


def test_los_proveedores_sucios_del_cliente_quedan_en_evidencia_al_validar(mundo, eq, sunat):
    """Los 12 registros con documento inválido de la semilla del cliente se cargan «sin validar» y no pasan HU-31."""
    sucio = mundo.f.proveedor(ruc="123456", validado=False, razon="REGISTRO DE PRUEBA")
    sunat["valor"] = ruc_sunat("123456")
    r = post(mundo, f"/proveedores/{sucio}/validar", eq["residente"])
    assert r.json()["estado"] == "sin_validar" and "dígito verificador" in r.json()["advertencia"]


def test_el_ruc_de_un_proveedor_no_se_edita_por_ningun_camino(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    for metodo in ("put", "patch", "delete"):
        r = getattr(mundo.http, metodo)(f"{U}/proveedores/{p['id']}", headers=mundo.con(eq["admin"]))
        assert r.status_code in (404, 405)


# ══════════════════════════════════════════════ compras (HU-32)
def test_registrar_compra_calcula_los_totales_en_el_servidor_igual_que_la_factura(mundo, eq, sunat):
    """400 bls × S/ 29.23 con IGV incluido = S/ 11 692.00 → base 9 908.47 + IGV 1 783.53 (la factura F001-4512 de la demo)."""
    p = proveedor(mundo, eq)
    r = comprar(mundo, eq, p)
    assert r.status_code == 201
    c = r.json()
    assert c["totales"] == {"base": "9908.47", "igv": "1783.53", "total": "11692.00"}
    assert c["estado"] == "registrada" and c["registrado_por"] == eq["residente"] and c["moneda"] == "PEN"
    assert c["proveedor_razon_social"] == "Dinoselva Iquitos S.A.C." and c["numero_comprobante"] == "F001-4512"
    assert [(l["insumo_codigo"], l["cantidad"]) for l in c["lineas"]] == [("0221000000", "400.0000")]


def test_precios_sin_igv_suman_el_dieciocho_por_ciento(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    r = comprar(mundo, eq, p, lineas=[linea(cantidad="10", precio="10.00")], precios_incluyen_igv=False)
    assert r.json()["totales"] == {"base": "100.00", "igv": "18.00", "total": "118.00"}


def test_el_cliente_no_puede_mandar_totales_ni_estado(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    for extra in ({"total": "1.00"}, {"base_imponible": "1"}, {"estado": "anulada"}, {"registrado_por": 1}, {"igv": "0"}):
        assert comprar(mundo, eq, p, **extra).status_code == 422, extra


def test_el_total_es_la_suma_de_varias_lineas(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    r = comprar(mundo, eq, p, lineas=[linea(cantidad="180", precio="41.27", codigo="0203020004", unidad="kg"),
                                      linea(cantidad="15", precio="80.00", codigo="0205010033", unidad="m3")])
    t = r.json()["totales"]
    assert t["total"] == "8628.60" and float(t["base"]) + float(t["igv"]) == pytest.approx(8628.60)


def test_el_comprobante_no_se_repite_para_el_mismo_proveedor_pero_si_entre_proveedores(mundo, eq, sunat):
    a, b = proveedor(mundo, eq), proveedor(mundo, eq, razon="Acero Comercial S.R.L.")
    assert comprar(mundo, eq, a).status_code == 201
    dup = comprar(mundo, eq, a)
    assert dup.status_code == 409 and dup.json()["mensaje"] == "Ese comprobante ya está registrado para este proveedor."
    assert comprar(mundo, eq, b).status_code == 201                          # otro proveedor, mismo número
    assert comprar(mundo, eq, a, tipo_comprobante="boleta").status_code == 201   # otro tipo, mismo número


def test_comprobante_con_formato_invalido(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    for malo in ("4512", "F001_4512", "F001-", "FACTURA1-4512", "F001-12345678901"):
        assert comprar(mundo, eq, p, numero=malo).status_code == 422, malo


def test_el_numero_de_comprobante_se_guarda_en_mayusculas(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    assert comprar(mundo, eq, p, numero="f001-4512").json()["numero_comprobante"] == "F001-4512"
    assert comprar(mundo, eq, p, numero="F001-4512").status_code == 409


def test_lineas_invalidas(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    assert comprar(mundo, eq, p, lineas=[]).status_code == 422
    assert comprar(mundo, eq, p, lineas=[linea(cantidad="0")]).status_code == 422
    assert comprar(mundo, eq, p, lineas=[linea(precio="0")]).status_code == 422
    assert comprar(mundo, eq, p, lineas=[linea(cantidad="-5")]).status_code == 422
    assert comprar(mundo, eq, p, lineas=[linea(unidad="")]).status_code == 422
    rep = comprar(mundo, eq, p, lineas=[linea(), linea(cantidad="5")])
    assert rep.status_code == 422 and "repetido" in rep.json()["mensaje"]


def test_insumo_que_no_existe_en_el_catalogo(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    r = comprar(mundo, eq, p, lineas=[linea(codigo="9999999999")])
    assert r.status_code == 422 and r.json()["codigo"] == "insumo_no_existe" and "9999999999" in r.json()["mensaje"]


def test_insumo_inactivo_no_se_compra(mundo, eq, sunat):
    mundo.conn.exec_driver_sql("UPDATE catalogo.insumo SET activo=false WHERE codigo='0205010033'")
    p = proveedor(mundo, eq)
    assert comprar(mundo, eq, p, lineas=[linea(codigo="0205010033", unidad="m3")]).json()["codigo"] == "insumo_no_existe"


def test_proveedor_inexistente(mundo, eq):
    r = post(mundo, f"/obras/{eq['proyecto']}/compras", eq["residente"], proveedor_id=999999, tipo_comprobante="factura",
             numero_comprobante="F001-1", fecha="2026-10-18", lineas=[linea()])
    assert r.status_code == 422


def test_solo_residente_y_admin_registran_el_gerente_consulta(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    assert comprar(mundo, eq, p, numero="F001-1", quien="residente").status_code == 201
    assert comprar(mundo, eq, p, numero="F001-2", quien="admin").status_code == 201
    for rol in ("almacenero", "ssoma", "gerente"):
        assert comprar(mundo, eq, p, numero="F001-3", quien=rol).status_code == 403, rol
    for rol in ("residente", "admin", "gerente"):
        assert len(get(mundo, f"/obras/{eq['proyecto']}/compras", eq[rol]).json()) == 2
    for rol in ("almacenero", "ssoma"):
        assert get(mundo, f"/obras/{eq['proyecto']}/compras", eq[rol]).status_code == 403


def test_las_compras_son_de_su_obra(mundo, eq, sunat):
    otra = mundo.f.obra_completa()
    p = proveedor(mundo, eq)
    c = comprar(mundo, eq, p).json()
    assert get(mundo, f"/obras/{otra['proyecto']}/compras", otra["residente"]).json() == []
    assert get(mundo, f"/obras/{otra['proyecto']}/compras/{c['id']}", otra["residente"]).status_code == 404
    assert get(mundo, f"/obras/{eq['proyecto']}/compras/{c['id']}", otra["residente"]).status_code == 403
    assert post(mundo, f"/obras/{otra['proyecto']}/compras/{c['id']}/anular", otra["residente"], motivo="No es mía").status_code == 404


def test_listar_compras_con_filtros(mundo, eq, sunat):
    a, b = proveedor(mundo, eq), proveedor(mundo, eq, razon="Acero Comercial S.R.L.")
    c1 = comprar(mundo, eq, a, numero="F001-1").json()
    comprar(mundo, eq, b, numero="F001-2")
    post(mundo, f"/obras/{eq['proyecto']}/compras/{c1['id']}/anular", eq["residente"], motivo="Factura mal digitada")
    base = f"/obras/{eq['proyecto']}/compras"
    assert len(get(mundo, base, eq["admin"]).json()) == 2
    assert [c["id"] for c in get(mundo, base + "?estado=anulada", eq["admin"]).json()] == [c1["id"]]
    assert [c["proveedor_id"] for c in get(mundo, base + f"?proveedor_id={b['id']}", eq["admin"]).json()] == [b["id"]]
    assert get(mundo, base + "?estado=borrada", eq["admin"]).status_code == 422


def test_anular_compra_exige_motivo_deja_rastro_y_no_se_repite(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    c = comprar(mundo, eq, p).json()
    ruta = f"/obras/{eq['proyecto']}/compras/{c['id']}/anular"
    assert post(mundo, ruta, eq["residente"], motivo="no").status_code == 422
    r = post(mundo, ruta, eq["residente"], motivo="Se digitó el proveedor equivocado")
    assert r.status_code == 200 and r.json()["estado"] == "anulada" and r.json()["motivo_anulacion"] == "Se digitó el proveedor equivocado"
    fila = mundo.f.c.execute("SELECT anulada_por, anulada_en FROM compras.compra WHERE id=%s", (c["id"],)).fetchone()
    assert fila["anulada_por"] == eq["residente"] and fila["anulada_en"] is not None
    assert post(mundo, ruta, eq["residente"], motivo="Otra vez anulada").status_code == 409
    assert post(mundo, ruta, eq["gerente"], motivo="El gerente no anula").status_code == 403
    # el registro se conserva, y la correcta se registra aparte
    assert mundo.f.c.execute("SELECT count(*) AS n FROM compras.compra").fetchone()["n"] == 1
    assert comprar(mundo, eq, p, numero="F001-4513").status_code == 201


def test_una_compra_registrada_no_se_edita_ni_se_borra(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    c = comprar(mundo, eq, p).json()
    for metodo in ("put", "patch", "delete"):
        r = getattr(mundo.http, metodo)(f"{U}/obras/{eq['proyecto']}/compras/{c['id']}", headers=mundo.con(eq["admin"]))
        assert r.status_code in (404, 405)


def test_obra_cerrada_no_admite_compras_nuevas(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    mundo.conn.exec_driver_sql("UPDATE obra.proyecto SET estado='cerrado' WHERE id=%s", (eq["proyecto"],))
    r = comprar(mundo, eq, p)
    assert r.status_code == 422 and "cerrada" in r.json()["mensaje"]


def test_registrar_una_compra_es_atomico_si_una_linea_falla_no_queda_ni_la_cabecera(mundo, eq, sunat):
    p = proveedor(mundo, eq)
    r = comprar(mundo, eq, p, lineas=[linea(), linea(codigo="0203020004", unidad="kg", precio="1.00", cantidad="1")],
                solicitud_id=999999)
    assert r.status_code == 422
    assert mundo.f.c.execute("SELECT count(*) AS n FROM compras.compra").fetchone()["n"] == 0
    assert mundo.f.c.execute("SELECT count(*) AS n FROM compras.compra_detalle").fetchone()["n"] == 0


# ══════════════════════════════════════════════ solicitud de materiales (CR-04)
def solicitar(mundo, eq, lineas, quien="residente"):
    return post(mundo, f"/obras/{eq['proyecto']}/solicitudes", eq[quien], lineas=lineas)


def sl(codigo="0221000000", cantidad="400", unidad="bls"):
    return {"insumo_codigo": codigo, "cantidad": cantidad, "unidad_compra": unidad}


def test_la_solicitud_nace_solicitada_con_numero_correlativo_por_obra(mundo, eq):
    a = solicitar(mundo, eq, [sl()]).json()
    b = solicitar(mundo, eq, [sl("0203020004", "100", "kg")]).json()
    assert (a["numero"], b["numero"]) == (1, 2) and a["estado"] == "solicitada"
    otra = mundo.f.obra_completa()
    assert solicitar(mundo, otra, [sl()]).json()["numero"] == 1             # cada obra con su propio correlativo
    assert a["lineas"][0]["cantidad_pendiente"] == "400.0000" and float(a["lineas"][0]["cantidad_comprada"]) == 0


def test_el_cliente_no_elige_el_numero_ni_el_estado_de_la_solicitud(mundo, eq):
    for extra in ({"numero": 99}, {"estado": "comprada"}, {"emitida_por": 1}):
        r = post(mundo, f"/obras/{eq['proyecto']}/solicitudes", eq["residente"], lineas=[sl()], **extra)
        assert r.status_code == 422, extra


def test_solo_el_residente_emite_y_otros_roles_consultan(mundo, eq):
    assert solicitar(mundo, eq, [sl()], quien="residente").status_code == 201
    for rol in ("almacenero", "ssoma", "admin", "gerente"):
        assert solicitar(mundo, eq, [sl()], quien=rol).status_code == 403, rol
    for rol in ("residente", "admin", "gerente"):
        assert len(get(mundo, f"/obras/{eq['proyecto']}/solicitudes", eq[rol]).json()) == 1
    for rol in ("almacenero", "ssoma"):
        assert get(mundo, f"/obras/{eq['proyecto']}/solicitudes", eq[rol]).status_code == 403


def test_linea_con_insumo_no_catalogado_lleva_descripcion_libre(mundo, eq):
    r = solicitar(mundo, eq, [{"descripcion_libre": "Impermeabilizante para cisterna", "cantidad": "20", "unidad_compra": "gln"}])
    assert r.status_code == 201
    l = r.json()["lineas"][0]
    assert l["insumo_codigo"] is None and l["descripcion"] == "Impermeabilizante para cisterna"
    assert solicitar(mundo, eq, [{"cantidad": "20", "unidad_compra": "gln"}]).status_code == 422       # sin nada que la describa


def test_lineas_invalidas_de_solicitud(mundo, eq):
    assert solicitar(mundo, eq, []).status_code == 422
    assert solicitar(mundo, eq, [sl(cantidad="0")]).status_code == 422
    assert solicitar(mundo, eq, [sl(codigo="9999999999")]).json()["codigo"] == "insumo_no_existe"
    assert solicitar(mundo, eq, [sl(), sl(cantidad="5")]).json()["codigo"] == "insumo_repetido"


def test_el_estado_de_la_solicitud_se_deriva_de_las_compras_nunca_se_guarda(mundo, eq, sunat):
    s = solicitar(mundo, eq, [sl("0221000000", "400", "bls"), sl("0203020004", "100", "kg")]).json()
    p = proveedor(mundo, eq)
    ruta = f"/obras/{eq['proyecto']}/solicitudes/{s['id']}"
    assert get(mundo, ruta, eq["residente"]).json()["estado"] == "solicitada"

    parcial = comprar(mundo, eq, p, numero="F001-1", solicitud_id=s["id"], lineas=[linea(cantidad="150")])
    assert parcial.status_code == 201
    v = get(mundo, ruta, eq["residente"]).json()
    assert v["estado"] == "comprada_en_parte"
    cem = [l for l in v["lineas"] if l["insumo_codigo"] == "0221000000"][0]
    assert (cem["cantidad_comprada"], cem["cantidad_pendiente"]) == ("150.0000", "250.0000")

    comprar(mundo, eq, p, numero="F001-2", solicitud_id=s["id"], lineas=[linea(cantidad="250"), linea("0203020004", "100", "kg", "4.50")])
    assert get(mundo, ruta, eq["residente"]).json()["estado"] == "comprada"
    assert mundo.f.c.execute("SELECT estado FROM compras.solicitud WHERE id=%s", (s["id"],)).fetchone()["estado"] == "emitida"


def test_anular_una_compra_devuelve_la_solicitud_a_su_estado_anterior(mundo, eq, sunat):
    s = solicitar(mundo, eq, [sl("0221000000", "400", "bls")]).json()
    p = proveedor(mundo, eq)
    c = comprar(mundo, eq, p, solicitud_id=s["id"]).json()
    ruta = f"/obras/{eq['proyecto']}/solicitudes/{s['id']}"
    assert get(mundo, ruta, eq["residente"]).json()["estado"] == "comprada"
    post(mundo, f"/obras/{eq['proyecto']}/compras/{c['id']}/anular", eq["residente"], motivo="Factura mal digitada")
    assert get(mundo, ruta, eq["residente"]).json()["estado"] == "solicitada"


def test_comprar_contra_una_solicitud_ajena_o_anulada_se_rechaza(mundo, eq, sunat):
    otra = mundo.f.obra_completa()
    ajena = solicitar(mundo, otra, [sl()]).json()
    p = proveedor(mundo, eq)
    r = comprar(mundo, eq, p, solicitud_id=ajena["id"])
    assert r.status_code == 422 and r.json()["mensaje"] == "Esa solicitud no pertenece a esta obra."
    propia = solicitar(mundo, eq, [sl()]).json()
    post(mundo, f"/obras/{eq['proyecto']}/solicitudes/{propia['id']}/anular", eq["residente"], motivo="Ya no se necesita")
    r2 = comprar(mundo, eq, p, numero="F001-9", solicitud_id=propia["id"])
    assert r2.status_code == 422 and "anulada" in r2.json()["mensaje"]


def test_anular_solicitud_exige_motivo_y_queda_anulada(mundo, eq):
    s = solicitar(mundo, eq, [sl()]).json()
    ruta = f"/obras/{eq['proyecto']}/solicitudes/{s['id']}/anular"
    assert post(mundo, ruta, eq["residente"], motivo="").status_code == 422
    r = post(mundo, ruta, eq["residente"], motivo="Cambió el requerimiento")
    assert r.status_code == 200 and r.json()["estado"] == "anulada" and r.json()["motivo_anulacion"] == "Cambió el requerimiento"
    assert post(mundo, ruta, eq["residente"], motivo="Otra vez").status_code == 409
    assert post(mundo, ruta, eq["admin"], motivo="El admin no anula").status_code == 403


def test_una_solicitud_ajena_es_404_para_otra_obra(mundo, eq):
    otra = mundo.f.obra_completa()
    s = solicitar(mundo, otra, [sl()]).json()
    assert get(mundo, f"/obras/{eq['proyecto']}/solicitudes/{s['id']}", eq["residente"]).status_code == 404
    assert post(mundo, f"/obras/{eq['proyecto']}/solicitudes/{s['id']}/anular", eq["residente"], motivo="Ajena a mi obra").status_code == 404
