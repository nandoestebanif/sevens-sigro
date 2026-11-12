"""HU-58 · tablero del gasto en recursos: cinco cifras, una sola fecha de corte, «cifras por conciliar»."""
from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
from decimal import Decimal as D

from pruebas.test_almacen_costo_0010 import _entregar  # noqa: F401
from pruebas.test_api_almacen import ingreso, ob, salida  # noqa: F401  (fixture ob)

U = "/api/v1"


def gasto(mundo, ob, quien="gerente", **params):
    return mundo.http.get(f"{U}/obras/{ob['proyecto']}/costo/gasto", headers=mundo.con(ob[quien]), params=params)


def cifras(r):
    return {c["clave"]: c for c in r.json()["cifras"]}


def test_las_cinco_cifras_traen_definicion_y_la_misma_fecha_de_corte(mundo, ob):
    ingreso(mundo, ob, 100, 20)
    salida(mundo, ob, 30)
    r = gasto(mundo, ob, corte=date.today().isoformat())
    assert r.status_code == 200, r.text
    c = cifras(r)
    assert list(c) == ["comprado", "recibido", "consumido", "en_stock", "horas_hombre"]
    assert all(x["definicion"] for x in c.values()) and len({x["corte"] for x in c.values()}) == 1
    assert D(c["recibido"]["valor"]) == D("2000.00") and D(c["consumido"]["valor"]) == D("600.00") and D(c["en_stock"]["valor"]) == D("1400.00")
    d = r.json()
    assert D(d["otros_movimientos"]) == 0 and d["por_conciliar"] is False and d["conciliacion"] == []
    assert "no su costo total" in d["declaracion"]


def test_en_stock_es_el_total_del_inventario_valorizado(mundo, ob):
    ingreso(mundo, ob, 100, 20)
    ingreso(mundo, ob, 40, 5, insumo=ob["acero"])
    salida(mundo, ob, 10)
    inv = mundo.http.get(f"{U}/obras/{ob['proyecto']}/costo/inventario", headers=mundo.con(ob["gerente"])).json()
    assert D(cifras(gasto(mundo, ob))["en_stock"]["valor"]) == D(inv["valor_total"])


def test_el_corte_pasado_deja_fuera_lo_posterior(mundo, ob):
    ingreso(mundo, ob, 100, 20)
    ayer = (date.today() - timedelta(days=2)).isoformat()
    c = cifras(gasto(mundo, ob, corte=ayer))
    assert D(c["recibido"]["valor"]) == 0 and D(c["en_stock"]["valor"]) == 0
    assert D(cifras(gasto(mundo, ob))["recibido"]["valor"]) == D("2000.00")


def test_comprado_y_horas_respetan_el_corte(mundo, ob):
    p = ob["proyecto"]
    mundo.f.compra(p, mundo.f.proveedor(), ob["residente"], base="1000.00", igv="180.00")       # fecha del comprobante: hoy
    t = mundo.f.trabajador(p, ob["almacenero"])
    mundo.f.jornada(p, t, ob["almacenero"], normales="8", extra="2")
    hoy = gasto(mundo, ob).json()
    assert D(cifras(gasto(mundo, ob))["comprado"]["valor"]) == D("1000.00") and D(hoy["horas_hombre"]["horas_extra"]) == 2
    antes = gasto(mundo, ob, corte="2020-01-01")
    assert D(cifras(antes)["comprado"]["valor"]) == 0 and D(antes.json()["horas_hombre"]["horas_normales"]) == 0


def test_las_horas_no_se_valorizan_sin_tarifa_y_lo_dicen(mundo, ob):
    h = cifras(gasto(mundo, ob))["horas_hombre"]
    assert h["valor"] is None and "HU-65" in h["nota"]


def test_si_el_libro_y_el_modelo_de_costo_difieren_marca_cifras_por_conciliar(mundo, ob):
    """Un ingreso que el libro tiene pero cuyo evento aún no llegó a costo: el tablero no lo esconde."""
    ingreso(mundo, ob, 100, 20)
    d = gasto(mundo, ob).json()
    assert d["por_conciliar"] is True and any("faltan eventos" in m for m in d["conciliacion"])


def test_con_los_eventos_entregados_las_cifras_cuadran(mundo, ob, monkeypatch):
    ingreso(mundo, ob, 100, 20)
    salida(mundo, ob, 30)
    _entregar(mundo, monkeypatch)
    d = gasto(mundo, ob).json()
    assert d["por_conciliar"] is False, d["conciliacion"]


def test_el_corte_invalido_da_422_y_los_permisos_se_mantienen(mundo, ob):
    assert gasto(mundo, ob, corte="ayer").status_code == 422
    for quien, estado in (("residente", 200), ("admin", 200), ("gerente", 200), ("almacenero", 403), ("ssoma", 403)):
        assert gasto(mundo, ob, quien).status_code == estado, quien
    otra = mundo.f.obra_completa()
    assert mundo.http.get(f"{U}/obras/{ob['proyecto']}/costo/gasto", headers=mundo.con(otra["gerente"])).status_code == 403
