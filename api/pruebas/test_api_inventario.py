"""HU-56 · inventario valorizado: cantidad, costo promedio y valor por insumo, por almacén y consolidado, a una fecha de corte.

Almacén reconstruye el saldo del libro (R2) y costo agrupa y calcula pesos. Todo se prueba por el gateway.
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from decimal import Decimal as D

import pytest

from app.errores import ErrorSigro
from app.modulos.costo.rutas import corte_de
from pruebas.test_api_almacen import ingreso, ob, salida  # noqa: F401  (fixture ob)

U = "/api/v1"


def inventario(mundo, ob, quien="gerente", **params):
    return mundo.http.get(f"{U}/obras/{ob['proyecto']}/costo/inventario", headers=mundo.con(ob[quien]), params=params)


# ---------------------------------------------------------------- dominio puro (sin base)
def test_el_corte_sin_zona_es_hora_de_lima_y_un_dia_solo_es_el_fin_de_ese_dia():
    assert corte_de("2026-11-12T10:30") == datetime(2026, 11, 12, 15, 30, tzinfo=timezone.utc)
    assert corte_de("2026-11-12") == datetime(2026, 11, 13, 4, 59, 59, tzinfo=timezone.utc)
    assert corte_de(None) is None and corte_de("  ") is None


def test_un_corte_ilegible_se_rechaza_en_vez_de_tomar_ahora():
    with pytest.raises(ErrorSigro) as e:
        corte_de("ayer")
    assert e.value.status_code == 422


# ---------------------------------------------------------------- valorización
def test_valor_es_saldo_por_promedio_y_el_consolidado_suma_los_almacenes(mundo, ob):
    frente = mundo.f.almacen(ob["proyecto"], "Frente A", "frente")
    assert ingreso(mundo, ob, 100, 20).status_code == 201
    assert ingreso(mundo, ob, 50, 26).status_code == 201                      # promedio central = 22
    assert ingreso(mundo, ob, 10, 30, almacen=frente).status_code == 201      # otro almacén, otro promedio
    assert ingreso(mundo, ob, 40, 5, insumo=ob["acero"]).status_code == 201
    r = inventario(mundo, ob)
    assert r.status_code == 200, r.text
    d = r.json()
    cem = next(x for x in d["por_insumo"] if x["unidad"] == "bls")
    assert D(cem["cantidad"]) == 160 and D(cem["valor"]) == D("3600.00") and D(cem["costo_promedio"]) == D("22.5")   # 3300 + 300
    assert D(d["valor_total"]) == D("3800.00") and D(cem["porcentaje"]) == D("94.74")
    por = {x["almacen"]: x for x in d["por_almacen"]}
    assert D(por["Central"]["valor"]) == D("3500.00") and D(por["Frente A"]["valor"]) == D("300.00")
    assert sum(D(x["porcentaje"]) for x in d["por_insumo"]) == D("100.00")
    assert [x["insumo_codigo"] for x in d["por_insumo"]] == ["0221000000", "0203020004"]      # de más a menos valor
    assert "no su costo total" in d["declaracion"] and "NIC 2" in d["criterio"] and d["corte"] and not d["corte_ajustado"]


def test_por_almacen_solo_cuenta_ese_almacen(mundo, ob):
    frente = mundo.f.almacen(ob["proyecto"], "Frente A", "frente")
    ingreso(mundo, ob, 100, 20)
    ingreso(mundo, ob, 10, 30, almacen=frente)
    d = inventario(mundo, ob, almacen=str(frente)).json()
    assert D(d["valor_total"]) == D("300.00") and [x["almacen"] for x in d["por_almacen"]] == ["Frente A"]
    assert D(d["por_insumo"][0]["porcentaje"]) == D("100.00")


def test_la_salida_baja_el_valor_al_promedio_congelado_y_el_reverso_lo_devuelve(mundo, ob):
    ingreso(mundo, ob, 100, 20)
    ingreso(mundo, ob, 50, 26)
    s = salida(mundo, ob, 30).json()
    d = inventario(mundo, ob).json()
    assert D(d["por_insumo"][0]["cantidad"]) == 120 and D(d["valor_total"]) == D("2640.00")      # 150×22 − 30×22
    mundo.http.post(f"{U}/obras/{ob['proyecto']}/almacen/movimientos/{s['id']}/reverso", headers=mundo.con(ob["almacenero"]),
                    json={"uuid_cliente": "00000000-0000-4000-8000-000000000001", "motivo": "Eran 10, no 30"})
    assert D(inventario(mundo, ob).json()["valor_total"]) == D("3300.00")


def test_un_insumo_sin_saldo_no_aparece_ni_pesa(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    salida(mundo, ob, 10)
    d = inventario(mundo, ob).json()
    assert d["por_insumo"] == [] and D(d["valor_total"]) == 0 and D(d["por_almacen"][0]["valor"]) == 0 and D(d["por_almacen"][0]["porcentaje"]) == 0


# ---------------------------------------------------------------- fecha de corte
def test_con_corte_pasado_se_reconstruye_el_libro_a_esa_fecha(mundo, ob):
    """Dos ingresos; el primero «ocurrió» hace 3 días (se reescribe su hora con el libro destapado, solo dentro de la prueba)."""
    ingreso(mundo, ob, 100, 20)
    ingreso(mundo, ob, 50, 26)
    c = mundo.f.c
    c.execute("ALTER TABLE almacen.movimiento DISABLE TRIGGER USER")
    c.execute("UPDATE almacen.movimiento SET fecha_servidor = now() - interval '3 days' "
              "WHERE id = (SELECT min(id) FROM almacen.movimiento WHERE proyecto_id = %s)", (ob["proyecto"],))
    c.execute("ALTER TABLE almacen.movimiento ENABLE TRIGGER USER")
    ayer = (datetime.now(timezone.utc) - timedelta(days=1)).astimezone(timezone(timedelta(hours=-5))).strftime("%Y-%m-%dT%H:%M")
    antes = inventario(mundo, ob, corte=ayer).json()
    assert D(antes["valor_total"]) == D("2000.00") and D(antes["por_insumo"][0]["costo_promedio"]) == D("20")     # solo el primero
    hace_5 = (datetime.now(timezone.utc) - timedelta(days=5)).strftime("%Y-%m-%d")
    assert inventario(mundo, ob, corte=hace_5).json()["por_insumo"] == []
    ahora = inventario(mundo, ob).json()
    assert D(ahora["valor_total"]) == D("3300.00") and D(ahora["por_insumo"][0]["costo_promedio"]) == D("22")


def test_un_corte_futuro_se_ajusta_a_ahora_y_se_dice(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    d = inventario(mundo, ob, corte="2099-01-01").json()
    assert d["corte_ajustado"] is True and D(d["valor_total"]) == D("200.00")
    assert datetime.fromisoformat(d["corte"]) <= datetime.now(timezone.utc)


def test_parametros_invalidos_422_y_almacen_ajeno_404(mundo, ob):
    assert inventario(mundo, ob, corte="ayer").status_code == 422
    assert inventario(mundo, ob, almacen="central").status_code == 422
    otra = mundo.f.obra_completa()
    ajeno = mundo.f.almacen(otra["proyecto"], "Ajeno", "central")
    assert inventario(mundo, ob, almacen=str(ajeno)).status_code == 404


# ---------------------------------------------------------------- permisos y alcance (R9, en el servidor)
def test_lo_ven_residente_administrador_y_gerente_y_nadie_mas(mundo, ob):
    for quien, estado in (("residente", 200), ("admin", 200), ("gerente", 200), ("almacenero", 403), ("ssoma", 403)):
        assert inventario(mundo, ob, quien).status_code == estado, quien


def test_otra_obra_no_ve_este_inventario(mundo, ob):
    ingreso(mundo, ob, 10, 20)
    otra = mundo.f.obra_completa()
    assert mundo.http.get(f"{U}/obras/{ob['proyecto']}/costo/inventario", headers=mundo.con(otra["gerente"])).status_code == 403
    assert D(inventario(mundo, otra).json()["valor_total"]) == 0
