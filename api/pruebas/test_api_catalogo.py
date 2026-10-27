"""API del catálogo: alta con código único (HU-08), factor de conversión con histórico (HU-17), importación S10 (HU-07)."""
from __future__ import annotations

import pytest

U = "/api/v1/catalogo/insumos"


@pytest.fixture
def eq(mundo):
    return mundo.f.obra_completa()


def post(mundo, ruta, uid, **cuerpo):
    return mundo.http.post(f"{U}{ruta}", headers=mundo.con(uid), json=cuerpo)


def get(mundo, ruta, uid):
    return mundo.http.get(f"{U}{ruta}", headers=mundo.con(uid))


def alta(mundo, uid, descripcion="Cemento Portland Tipo I", unidad="bls", **kw):
    return post(mundo, "", uid, descripcion=descripcion, unidad=unidad, **kw)


# ═════════════════════════════════════════ HU-08 alta
def test_el_almacenero_crea_un_insumo_que_nace_por_verificar_con_codigo_del_servidor(mundo, eq):
    r = alta(mundo, eq["almacenero"])
    assert r.status_code == 201, r.text
    d = r.json()
    assert d["codigo"].startswith("SIGRO-") and d["verificado"] is False and d["unidad_compra"] == "bls" and d["unidad_s10"] == "bls"


def test_los_codigos_generados_son_correlativos(mundo, eq):
    a = alta(mundo, eq["residente"], "Clavos 3 pulgadas", "kg").json()["codigo"]
    b = alta(mundo, eq["residente"], "Alambre N° 16", "kg").json()["codigo"]
    assert int(b[6:]) == int(a[6:]) + 1


def test_el_cliente_no_puede_elegir_el_codigo(mundo, eq):
    r = alta(mundo, eq["residente"], codigo="0221000000")
    assert r.status_code == 422


def test_nombre_repetido_se_rechaza_aunque_cambien_tildes_mayusculas_y_espacios(mundo, eq):
    c = alta(mundo, eq["residente"], "Fierro corrugado ½ pulgada", "var").json()["codigo"]
    r = alta(mundo, eq["almacenero"], "  FIERRO  CORRUGADO ½ PULGADA ", "VAR")
    assert r.status_code == 409 and r.json()["codigo"] == "insumo_duplicado" and c in r.json()["mensaje"]


def test_misma_descripcion_con_otra_unidad_es_otro_insumo(mundo, eq):
    alta(mundo, eq["residente"], "Arena gruesa", "m3")
    assert alta(mundo, eq["residente"], "Arena gruesa", "kg").status_code == 201


def test_gerente_y_ssoma_no_crean_insumos(mundo, eq):
    assert alta(mundo, eq["gerente"]).status_code == 403
    assert alta(mundo, eq["ssoma"]).status_code == 403


def test_sin_sesion_no_hay_acceso(mundo):
    assert mundo.http.get(U).status_code == 401 and mundo.http.post(U, json={}).status_code == 401


# ═════════════════════════════════════════ verificar y consultar
def test_el_residente_verifica_y_el_almacenero_no(mundo, eq):
    i = alta(mundo, eq["almacenero"]).json()
    assert post(mundo, f"/{i['id']}/verificar", eq["almacenero"]).status_code == 403
    r = post(mundo, f"/{i['id']}/verificar", eq["residente"])
    assert r.status_code == 200 and r.json()["verificado"] is True


def test_verificar_un_insumo_inexistente_da_404(mundo, eq):
    assert post(mundo, "/999999999/verificar", eq["residente"]).status_code == 404


def test_listar_busca_por_texto_y_filtra_por_verificado(mundo, eq):
    alta(mundo, eq["almacenero"], "Zapapico reforzado XYZ", "und")
    r = get(mundo, "?q=zapapico", eq["gerente"])
    assert r.status_code == 200 and len(r.json()) == 1 and r.json()[0]["verificado"] is False
    assert get(mundo, "?q=zapapico&verificado=true", eq["gerente"]).json() == []


def test_listar_acepta_todos_los_roles_de_la_obra(mundo, eq):
    for rol in ("almacenero", "ssoma", "residente", "admin", "gerente"):
        assert get(mundo, "", eq[rol]).status_code == 200, rol


# ═════════════════════════════════════════ HU-17 factor de conversión
def test_cambiar_el_factor_guarda_el_historico_con_quien_cuando_y_por_que(mundo, eq):
    i = alta(mundo, eq["residente"], "Fierro corrugado fy=4200", "var").json()
    r = post(mundo, f"/{i['id']}/factor-conversion", eq["residente"], factor="7.12", motivo="Peso de la varilla de 9 m, ½ pulgada")
    assert r.status_code == 200 and r.json()["factor_conversion"] == "7.120000"
    r2 = post(mundo, f"/{i['id']}/factor-conversion", eq["admin"], factor="7.5", motivo="Corrección por ficha técnica")
    assert r2.status_code == 200
    h = get(mundo, f"/{i['id']}/factor-conversion/historial", eq["gerente"]).json()
    assert [(x["factor_anterior"], x["factor_nuevo"]) for x in h] == [("1.000000", "7.120000"), ("7.120000", "7.500000")]
    assert h[0]["cambiado_por"] == eq["residente"] and h[1]["cambiado_por"] == eq["admin"]


def test_factor_exige_motivo_y_valor_positivo_y_distinto(mundo, eq):
    i = alta(mundo, eq["residente"]).json()
    ruta = f"/{i['id']}/factor-conversion"
    assert post(mundo, ruta, eq["residente"], factor="0", motivo="x" * 5).status_code == 422
    assert post(mundo, ruta, eq["residente"], factor="-2", motivo="x" * 5).status_code == 422
    assert post(mundo, ruta, eq["residente"], factor="2").status_code == 422
    assert post(mundo, ruta, eq["residente"], factor="1", motivo="mismo valor").status_code == 422


def test_el_almacenero_no_cambia_factores(mundo, eq):
    i = alta(mundo, eq["residente"]).json()
    assert post(mundo, f"/{i['id']}/factor-conversion", eq["almacenero"], factor="2", motivo="no puede").status_code == 403


def test_el_historico_no_se_puede_editar_ni_borrar_en_la_base(mundo, eq):
    import psycopg
    i = alta(mundo, eq["residente"]).json()
    post(mundo, f"/{i['id']}/factor-conversion", eq["residente"], factor="3", motivo="primera vez")
    for sql in ("UPDATE catalogo.insumo_factor_historial SET motivo = 'otro'", "DELETE FROM catalogo.insumo_factor_historial"):
        with pytest.raises(psycopg.errors.Error):
            mundo.f.q1(sql + " RETURNING 1")


# ═════════════════════════════════════════ HU-07 importar S10
def fila(codigo, desc="CEMENTO PORTLAND TIPO I (42.5 kg)", unidad="bls", tipo="material"):
    return {"codigo": codigo, "descripcion": desc, "unidad": unidad, "tipo": tipo}


def importar(mundo, uid, filas):
    return post(mundo, "/importar-s10", uid, insumos=filas)


def test_importar_crea_nuevos_ya_verificados(mundo, eq):
    r = importar(mundo, eq["residente"], [fila("0221000000"), fila("0203020004", "ACERO CORRUGADO fy=4200", "kg")])
    assert r.status_code == 200 and r.json()["creados"] == 2 and r.json()["actualizados"] == 0
    cem = get(mundo, "?q=0221000000", eq["residente"]).json()[0]
    assert cem["verificado"] is True and cem["unidad_s10"] == "bls" and cem["factor_conversion"] == "1.000000"


def test_repetir_la_importacion_no_duplica(mundo, eq):
    filas = [fila("0221000000"), fila("0203020004", "ACERO CORRUGADO fy=4200", "kg")]
    importar(mundo, eq["residente"], filas)
    r = importar(mundo, eq["residente"], filas).json()
    assert r == {"creados": 0, "actualizados": 0, "sin_cambios": 2, "advertencias": []}
    assert len(get(mundo, "?q=02", eq["residente"]).json()) == 2


def test_importar_actualiza_la_descripcion_pero_no_el_codigo_ni_la_unidad_ni_el_factor(mundo, eq):
    importar(mundo, eq["residente"], [fila("0221000000")])
    i = get(mundo, "?q=0221000000", eq["residente"]).json()[0]
    post(mundo, f"/{i['id']}/factor-conversion", eq["residente"], factor="42.5", motivo="Bolsa de 42.5 kg")
    r = importar(mundo, eq["residente"], [fila("0221000000", "CEMENTO PORTLAND TIPO I", "kg")]).json()
    assert r["actualizados"] == 1
    d = get(mundo, "?q=0221000000", eq["residente"]).json()[0]
    assert d["descripcion"] == "CEMENTO PORTLAND TIPO I" and d["unidad_s10"] == "bls" and d["factor_conversion"] == "42.500000"


def test_importar_avisa_si_el_tipo_difiere_y_no_lo_cambia(mundo, eq):
    importar(mundo, eq["residente"], [fila("0221000000")])
    r = importar(mundo, eq["residente"], [fila("0221000000", tipo="equipo")]).json()
    assert any("0221000000" in a and "equipo" in a for a in r["advertencias"])
    assert get(mundo, "?q=0221000000", eq["residente"]).json()[0]["tipo"] == "material"


def test_importar_avisa_las_filas_repetidas_en_el_archivo(mundo, eq):
    r = importar(mundo, eq["residente"], [fila("0221000000"), fila("0221000000")]).json()
    assert r["creados"] == 1 and any("repetido" in a for a in r["advertencias"])


def test_importar_solo_residente_y_administrador(mundo, eq):
    assert importar(mundo, eq["almacenero"], [fila("1")]).status_code == 403
    assert importar(mundo, eq["gerente"], [fila("1")]).status_code == 403
    assert importar(mundo, eq["admin"], [fila("1")]).status_code == 200


def test_importar_rechaza_vacio_tipo_invalido_y_campos_de_mas(mundo, eq):
    assert importar(mundo, eq["residente"], []).status_code == 422
    assert importar(mundo, eq["residente"], [fila("1", tipo="otro")]).status_code == 422
    assert importar(mundo, eq["residente"], [{**fila("1"), "precio": 5}]).status_code == 422
