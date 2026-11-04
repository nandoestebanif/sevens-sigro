"""Partidas y análisis de precios unitarios (HU-16)."""
from __future__ import annotations

import psycopg
import pytest

U = "/api/v1"


@pytest.fixture
def eq(mundo):
    e = mundo.f.obra_completa()
    e["cem"] = mundo.f.insumo("0221000000", unidad="bls")
    e["are"] = mundo.f.insumo("0238000003", unidad="m3")
    return e


def fa(partida="01.01.01", desc="Concreto f'c=210", insumo="0221000000", cant="9.73", unidad="bls", pun="m3"):
    return {"partida_codigo": partida, "partida_descripcion": desc, "partida_unidad": pun,
            "insumo_codigo": insumo, "cantidad_por_unidad": cant, "unidad": unidad}


def imp(mundo, eq, filas, quien="residente"):
    return mundo.http.post(f"{U}/obras/{eq['proyecto']}/partidas/importar-apu", headers=mundo.con(eq[quien]), json={"filas": filas})


def ver(mundo, eq, ruta, quien="residente"):
    return mundo.http.get(f"{U}/obras/{eq['proyecto']}{ruta}", headers=mundo.con(eq[quien]))


def test_importar_guarda_partida_y_recursos(mundo, eq):
    r = imp(mundo, eq, [fa(), fa(insumo="0238000003", cant="0.54", unidad="m3")]).json()
    assert (r["partidas_creadas"], r["recursos_creados"], r["fallidos"], r["advertencias"]) == (1, 2, [], [])
    p = ver(mundo, eq, "/partidas", "gerente").json()
    assert len(p) == 1 and p[0]["recursos"] == 2 and p[0]["unidad"] == "m3"
    rec = ver(mundo, eq, f"/partidas/{p[0]['id']}/recursos").json()
    assert {x["insumo_codigo"]: x["cantidad_por_unidad"] for x in rec} == {"0221000000": "9.730000", "0238000003": "0.540000"}


def test_recurso_desconocido_se_reporta_y_no_detiene(mundo, eq):
    r = imp(mundo, eq, [fa(insumo="9999999999"), fa()]).json()
    assert r["recursos_creados"] == 1
    assert r["fallidos"] == [{"partida_codigo": "01.01.01", "insumo_codigo": "9999999999", "motivo": "El recurso no está en el catálogo."}]


def test_codigo_truncado_se_distingue_por_descripcion(mundo, eq):
    """El reporte trunca el código: mismo código + descripción distinta = otra partida."""
    r = imp(mundo, eq, [fa(partida="01.01", desc="Excavación manual"), fa(partida="01.01", desc="Excavación con máquina")]).json()
    assert r["partidas_creadas"] == 2
    assert len(ver(mundo, eq, "/partidas").json()) == 2


def test_misma_partida_con_otra_mayuscula_o_espacios_es_la_misma(mundo, eq):
    imp(mundo, eq, [fa(desc="Concreto f'c=210")])
    r = imp(mundo, eq, [fa(desc="  CONCRETO  f'c=210 ".strip())]).json()
    assert r["partidas_creadas"] == 0 and r["sin_cambios"] == 1


def test_repetir_no_duplica_ni_pisa(mundo, eq):
    imp(mundo, eq, [fa()])
    r = imp(mundo, eq, [fa()]).json()
    assert (r["partidas_creadas"], r["recursos_creados"], r["sin_cambios"], r["advertencias"]) == (0, 0, 1, [])
    r2 = imp(mundo, eq, [fa(cant="10")]).json()
    assert r2["recursos_creados"] == 0 and "No se pisó" in r2["advertencias"][0]
    pid = ver(mundo, eq, "/partidas").json()[0]["id"]
    assert ver(mundo, eq, f"/partidas/{pid}/recursos").json()[0]["cantidad_por_unidad"] == "9.730000"


def test_recurso_repetido_en_el_mismo_archivo_es_fallido(mundo, eq):
    r = imp(mundo, eq, [fa(), fa(cant="5")]).json()
    assert r["recursos_creados"] == 1 and r["fallidos"][0]["motivo"].startswith("El recurso aparece repetido")


def test_unidad_distinta_a_la_del_catalogo_avisa_pero_guarda(mundo, eq):
    r = imp(mundo, eq, [fa(unidad="kg")]).json()
    assert r["recursos_creados"] == 1 and "catálogo" in r["advertencias"][0]


def test_cada_obra_tiene_sus_partidas(mundo, eq):
    otra = mundo.f.obra_completa()
    imp(mundo, eq, [fa()])
    assert ver(mundo, otra, "/partidas", "residente").json() == []
    pid = ver(mundo, eq, "/partidas").json()[0]["id"]
    assert ver(mundo, otra, f"/partidas/{pid}/recursos").status_code == 404
    assert ver(mundo, otra, "/partidas", "residente").status_code == 200
    assert mundo.http.get(f"{U}/obras/{otra['proyecto']}/partidas", headers=mundo.con(eq["residente"])).status_code == 403


def test_partidas_que_usan_un_insumo(mundo, eq):
    imp(mundo, eq, [fa(), fa(partida="02.01", desc="Solado", insumo="0221000000", cant="2"), fa(partida="03.01", desc="Arena", insumo="0238000003", cant="1", unidad="m3")])
    r = ver(mundo, eq, f"/insumos/{eq['cem']}/partidas").json()
    assert [x["codigo"] for x in r] == ["01.01.01", "02.01"]


def test_busqueda_por_codigo_o_descripcion(mundo, eq):
    imp(mundo, eq, [fa(), fa(partida="02.01", desc="Solado", insumo="0238000003", cant="1", unidad="m3")])
    assert [x["codigo"] for x in ver(mundo, eq, "/partidas?q=solad").json()] == ["02.01"]


def test_validaciones_de_entrada(mundo, eq):
    assert imp(mundo, eq, []).status_code == 422
    assert imp(mundo, eq, [fa(cant="0")]).status_code == 422
    assert imp(mundo, eq, [fa(cant="-1")]).status_code == 422
    assert imp(mundo, eq, [{**fa(), "x": 1}]).status_code == 422
    assert imp(mundo, eq, [fa(desc="")]).status_code == 422


def test_permisos(mundo, eq):
    for rol in ("almacenero", "ssoma", "gerente"):
        assert imp(mundo, eq, [fa()], quien=rol).status_code == 403, rol
    for rol, esp in (("residente", 200), ("admin", 200), ("gerente", 200), ("almacenero", 403), ("ssoma", 403)):
        assert ver(mundo, eq, "/partidas", rol).status_code == esp, rol
    assert imp(mundo, eq, [fa()], quien="admin").status_code == 200


def test_el_apu_importado_es_un_libro_inmutable(mundo, eq):
    imp(mundo, eq, [fa()])
    for sql in ("UPDATE compras.partida_recurso SET cantidad_por_unidad = 1", "DELETE FROM compras.partida_recurso",
                "UPDATE compras.partida SET unidad = 'x'", "DELETE FROM compras.partida"):
        with pytest.raises(psycopg.errors.Error):
            mundo.f.q1(sql + " RETURNING id")
