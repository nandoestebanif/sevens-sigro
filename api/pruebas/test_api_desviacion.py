"""HU-57 · consumo contra lo planificado, por insumo, en cantidad, importe y porcentaje."""
from __future__ import annotations

from datetime import date, timedelta
from decimal import Decimal as D

import pytest

from app.modulos.costo.rutas import avance_plazo, comparar, ordenar
from pruebas.test_api_almacen import ingreso, salida  # noqa: F401

U = "/api/v1"
HOY = date.today()


# ---------------------------------------------------------------- dominio puro (sin base)
def test_avance_del_plazo_es_lineal_y_se_acota():
    i, f = date(2026, 10, 1), date(2026, 10, 21)
    assert avance_plazo(i, f, date(2026, 9, 1)) == 0 and avance_plazo(i, f, date(2026, 10, 11)) == D("0.5")
    assert avance_plazo(i, f, date(2027, 1, 1)) == 1 and avance_plazo(i, i, i) == 0


def test_la_desviacion_se_parte_en_efecto_cantidad_y_efecto_precio():
    # planificado 1000 kg a S/5; a mitad del plazo se esperan 500 kg (S/2 500); se consumieron 600 kg que costaron S/3 300
    d = comparar({"cantidad": D("1000"), "precio": D("5")}, {"cantidad": D("600"), "importe": D("3300")}, D("0.5"), D("10"))
    assert d["esperado_cantidad"] == 500 and d["esperado_importe"] == D("2500.00")
    assert d["desviacion_cantidad"] == 100 and d["desviacion_importe"] == D("800.00") and d["desviacion_pct"] == D("32.00")
    assert d["efecto_cantidad"] == D("500.00") and d["efecto_precio"] == D("300.00")        # 100 kg de más × S/5 · (5.5 − 5) × 600
    assert d["efecto_cantidad"] + d["efecto_precio"] == d["desviacion_importe"]
    assert d["consumido_pct_planificado"] == D("60.00") and d["ritmo_pt"] == D("10.00") and d["lectura"] == "sobre_lo_esperado"
    assert d["costo_real"] == D("5.5")


@pytest.mark.parametrize("consumido,lectura", [("540", "en_rango"), ("700", "sobre_lo_esperado"), ("300", "bajo_lo_esperado")])
def test_la_tolerancia_decide_la_lectura(consumido, lectura):
    d = comparar({"cantidad": D("1000"), "precio": D("5")}, {"cantidad": D(consumido), "importe": D(consumido) * 5}, D("0.5"), D("10"))
    assert d["lectura"] == lectura


def test_sin_consumo_o_sin_avance_no_se_divide_por_cero():
    d = comparar({"cantidad": D("100"), "precio": D("2")}, None, D("0"), D("10"))
    assert d["desviacion_pct"] is None and d["costo_real"] is None and d["lectura"] == "en_rango" and d["desviacion_importe"] == 0


def test_el_orden_por_importe_pone_primero_la_mayor_desviacion_sea_exceso_o_ahorro():
    xs = [{"insumo_codigo": c, "desviacion_importe": D(v), "desviacion_pct": D(p), "desviacion_cantidad": D(v)}
          for c, v, p in (("A", "100", "5"), ("B", "-900", "-40"), ("C", "500", "20"))]
    assert [x["insumo_codigo"] for x in ordenar(xs, "importe")] == ["B", "C", "A"]
    assert [x["insumo_codigo"] for x in ordenar(xs, "codigo")] == ["A", "B", "C"]


# ---------------------------------------------------------------- por la API, contra PostgreSQL
@pytest.fixture
def ob(mundo):
    e = mundo.f.obra_completa(inicio=HOY - timedelta(days=10), fin=HOY + timedelta(days=10))      # hoy = 50 % del plazo
    e["cem"] = mundo.f.insumo("0221000000", unidad="bls")
    e["acero"] = mundo.f.insumo("0203020004", unidad="kg")
    e["clavos"] = mundo.f.insumo("0202010001", unidad="kg")
    e["central"] = mundo.f.almacen(e["proyecto"], "Central", "central")
    e["cel"] = mundo.f.celular(e["proyecto"], e["almacenero"], e["admin"])
    mundo.http.post(f"{U}/obras/{e['proyecto']}/requerimiento/importar", headers=mundo.con(e["residente"]), json={"filas": [
        {"insumo_codigo": "0221000000", "cantidad": "1000", "precio": "25"},
        {"insumo_codigo": "0203020004", "cantidad": "5000", "precio": "4.5"}]})
    return e


def pedir(mundo, ob, quien="gerente", **params):
    return mundo.http.get(f"{U}/obras/{ob['proyecto']}/costo/desviacion", headers=mundo.con(ob[quien]), params=params)


def mover(mundo, ob, codigo, cantidad, costo, insumo):
    assert ingreso(mundo, ob, cantidad, costo, insumo=insumo).status_code == 201
    assert salida(mundo, ob, cantidad, insumo_id=insumo).status_code == 201


def test_compara_cantidad_importe_y_porcentaje_contra_lo_planificado(mundo, ob):
    mover(mundo, ob, "0221000000", 600, 25, ob["cem"])
    r = pedir(mundo, ob)
    assert r.status_code == 200, r.text
    d = r.json()
    assert D(d["avance_plazo_pct"]) == D("50.00")
    cem = next(x for x in d["insumos"] if x["insumo_codigo"] == "0221000000")
    assert D(cem["esperado_cantidad"]) == 500 and D(cem["consumido_cantidad"]) == 600 and D(cem["desviacion_cantidad"]) == 100
    assert D(cem["desviacion_importe"]) == D("2500.00") and D(cem["desviacion_pct"]) == D("20.00") and D(cem["efecto_precio"]) == 0
    assert D(cem["consumido_pct_planificado"]) == D("60.00") and D(cem["ritmo_pt"]) == D("10.00")
    assert "desperdicio y reserva" in d["aviso"] and "no su costo total" in d["declaracion"] and d["supuesto"]


def test_un_precio_mayor_al_planificado_se_ve_como_efecto_precio(mundo, ob):
    mover(mundo, ob, "0221000000", 500, 27, ob["cem"])                 # justo lo esperado, pero a S/27 en vez de S/25
    cem = next(x for x in pedir(mundo, ob).json()["insumos"] if x["insumo_codigo"] == "0221000000")
    assert D(cem["efecto_cantidad"]) == 0 and D(cem["efecto_precio"]) == D("1000.00") and D(cem["desviacion_importe"]) == D("1000.00")


def test_lo_no_planificado_va_aparte_y_no_distorsiona_los_totales(mundo, ob):
    mover(mundo, ob, "0221000000", 500, 25, ob["cem"])
    antes = pedir(mundo, ob).json()["totales"]
    mover(mundo, ob, "0202010001", 40, 8, ob["clavos"])                # el S10 no lo planificó
    d = pedir(mundo, ob).json()
    assert [n["insumo_codigo"] for n in d["no_planificados"]] == ["0202010001"] and D(d["no_planificados_importe"]) == D("320.00")
    assert d["totales"] == antes and all(x["insumo_codigo"] != "0202010001" for x in d["insumos"])


def test_ordena_por_mayor_desviacion_en_importe(mundo, ob):
    mover(mundo, ob, "0221000000", 600, 25, ob["cem"])                 # +2 500
    mover(mundo, ob, "0203020004", 100, 4.5, ob["acero"])              # −2 250 − 0 → muy por debajo de lo esperado (2 500 kg)
    assert [x["insumo_codigo"] for x in pedir(mundo, ob, orden="importe").json()["insumos"]][0] == "0203020004"
    assert [x["insumo_codigo"] for x in pedir(mundo, ob, orden="codigo").json()["insumos"]] == ["0203020004", "0221000000"]


def test_corte_pasado_orden_invalido_y_permisos(mundo, ob):
    mover(mundo, ob, "0221000000", 600, 25, ob["cem"])
    antes = pedir(mundo, ob, corte=(HOY - timedelta(days=3)).isoformat()).json()
    assert all(D(x["consumido_cantidad"]) == 0 for x in antes["insumos"]) and D(antes["avance_plazo_pct"]) < 50
    assert pedir(mundo, ob, orden="mayor").status_code == 422 and pedir(mundo, ob, corte="ayer").status_code == 422
    for quien, estado in (("residente", 200), ("admin", 200), ("gerente", 200), ("almacenero", 403), ("ssoma", 403)):
        assert pedir(mundo, ob, quien).status_code == estado, quien
    otra = mundo.f.obra_completa()
    assert mundo.http.get(f"{U}/obras/{ob['proyecto']}/costo/desviacion", headers=mundo.con(otra["gerente"])).status_code == 403
