"""Rendimiento: cuántas sentencias SQL cuesta cada petición (N+1) y paginación opcional compatible hacia atrás.

Un contador en el evento `before_cursor_execute` de SQLAlchemy cuenta las sentencias que los servicios mandan a
PostgreSQL durante UNA petición (las de infraestructura de la prueba —SET ROLE, SAVEPOINT— no cuentan). La regla:
el costo de una petición NO crece con el número de filas. Antes de este cambio, importar el S10 o el requerimiento
costaba un SELECT por fila (284 con la Relación de insumos real) y listar obras, 2 por obra.
"""
from __future__ import annotations

import re
from contextlib import contextmanager
from datetime import date

import pytest
from sqlalchemy import event

U = "/api/v1"
_INFRA = re.compile(r"^\s*(SET ROLE|SAVEPOINT|RELEASE SAVEPOINT|ROLLBACK TO SAVEPOINT|SELECT current_user)", re.I)


@contextmanager
def contar(mundo):
    """Cuenta sentencias (un executemany cuenta como 1: es un solo viaje con psycopg 3)."""
    total: list[str] = []

    def al_ejecutar(conn, cursor, sql, params, contexto, varias):
        if not _INFRA.match(sql):
            total.append(sql)

    motor = mundo.conn.engine
    event.listen(motor, "before_cursor_execute", al_ejecutar)
    try:
        yield total
    finally:
        event.remove(motor, "before_cursor_execute", al_ejecutar)


def _con(mundo, uid):
    return mundo.con(uid)


# ───────────────────────────────────────────── N+1: el costo no depende del número de filas
def _filas_s10(n, desde=0):
    return [{"codigo": f"99{desde + i:08d}", "descripcion": f"INSUMO S10 {desde + i}", "unidad": "und", "tipo": "material"}
            for i in range(n)]


def test_importar_s10_cuesta_lo_mismo_con_5_que_con_200_filas(mundo):
    eq = mundo.f.obra_completa()
    costos = {}
    for n, desde in ((5, 0), (200, 1000)):
        with contar(mundo) as q:
            r = mundo.http.post(f"{U}/catalogo/insumos/importar-s10", headers=_con(mundo, eq["residente"]), json={"insumos": _filas_s10(n, desde)})
        assert r.status_code == 200 and r.json()["creados"] == n
        costos[n] = len(q)
    assert costos[5] == costos[200], costos
    # repetir (todo «sin cambios» o «actualizar») tampoco escala
    filas = _filas_s10(200, 1000)
    filas[0]["descripcion"] = "OTRA DESCRIPCION"
    with contar(mundo) as q:
        r = mundo.http.post(f"{U}/catalogo/insumos/importar-s10", headers=_con(mundo, eq["residente"]), json={"insumos": filas})
    assert r.json()["actualizados"] == 1 and r.json()["sin_cambios"] == 199
    assert len(q) <= costos[200] + 1


def test_importar_requerimiento_cuesta_lo_mismo_con_5_que_con_150_filas(mundo):
    eq = mundo.f.obra_completa()
    codigos = [f"RQ{i:06d}" for i in range(160)]
    for c in codigos:
        mundo.f.insumo(c)
    costos = {}
    for lote in (codigos[:5], codigos[5:155]):
        filas = [{"insumo_codigo": c, "cantidad": "10", "precio": "2.5"} for c in lote]
        with contar(mundo) as q:
            r = mundo.http.post(f"{U}/obras/{eq['proyecto']}/requerimiento/importar", headers=_con(mundo, eq["residente"]), json={"filas": filas})
        assert r.status_code == 200 and r.json()["creados"] == len(lote)
        costos[len(lote)] = len(q)
    assert costos[5] == costos[150], costos


def test_registrar_compra_cuesta_lo_mismo_con_1_que_con_30_lineas(mundo):
    eq = mundo.f.obra_completa()
    prov = mundo.f.proveedor()
    codigos = [f"CL{i:06d}" for i in range(31)]
    for c in codigos:
        mundo.f.insumo(c)
    costos = {}
    for i, lote in enumerate((codigos[:1], codigos[1:31])):
        cuerpo = {"proveedor_id": prov, "tipo_comprobante": "factura", "numero_comprobante": f"F001-{i + 1}", "fecha": "2026-10-20",
                  "moneda": "PEN", "precios_incluyen_igv": False,
                  "lineas": [{"insumo_codigo": c, "cantidad": "2", "unidad_compra": "kg", "precio_unitario": "3.10"} for c in lote]}
        with contar(mundo) as q:
            r = mundo.http.post(f"{U}/obras/{eq['proyecto']}/compras", headers=_con(mundo, eq["residente"]), json=cuerpo)
        assert r.status_code == 201, r.text
        costos[len(lote)] = len(q)
    assert costos[1] == costos[30], costos


def test_listar_obras_cuesta_lo_mismo_con_1_que_con_6_obras(mundo):
    u = mundo.f.usuario()
    otro = mundo.f.usuario()
    costos = {}
    p = mundo.f.proyecto(otro)
    mundo.f.asignar(u, p, "residente", otro)
    with contar(mundo) as q:
        r1 = mundo.http.get(f"{U}/obras", headers=_con(mundo, u))
    costos[1] = len(q)
    for k in range(5):
        p = mundo.f.proyecto(otro, inicio=date(2026, 9, 1 + k))
        mundo.f.asignar(u, p, "residente", otro)
    with contar(mundo) as q:
        r6 = mundo.http.get(f"{U}/obras", headers=_con(mundo, u))
    costos[6] = len(q)
    assert len(r1.json()) == 1 and len(r6.json()) == 6
    assert costos[1] == costos[6], costos


def test_listar_obras_devuelve_lo_mismo_que_ver_cada_obra(mundo):
    """La consulta única de la lista calcula semana y semanas igual que GET /obras/{id}."""
    eq = mundo.f.obra_completa()
    lista = mundo.http.get(f"{U}/obras", headers=_con(mundo, eq["residente"])).json()
    una = mundo.http.get(f"{U}/obras/{eq['proyecto']}", headers=_con(mundo, eq["residente"])).json()
    assert [o for o in lista if o["id"] == eq["proyecto"]] == [una]


# ───────────────────────────────────────────── paginación opcional: sin parámetros, nada cambia
@pytest.fixture
def con_datos(mundo):
    eq = mundo.f.obra_completa()
    for i in range(7):
        prov = mundo.f.proveedor(razon=f"PROVEEDOR PAGINA {i:02d} SAC")
        t = mundo.f.trabajador(eq["proyecto"], eq["residente"])
        mundo.f.jornada(eq["proyecto"], t, eq["residente"], fecha=date(2026, 10, 20 + i % 3))
        c = mundo.f.compra(eq["proyecto"], prov, eq["residente"])
        mundo.f.detalle(c, mundo.f.insumo())
        s = mundo.f.solicitud(eq["proyecto"], eq["residente"])
        mundo.f.linea(s, mundo.f.insumo())
        mundo.f.linea(s, None, libre="Clavos de 3 pulgadas")
    return eq


@pytest.mark.parametrize("ruta", [
    "/proveedores?q=PROVEEDOR PAGINA",
    "/obras/{p}/trabajadores",
    "/catalogo/insumos",
    "/obras/{p}/compras",
    "/obras/{p}/solicitudes",
    "/obras/{p}/jornadas",
])
def test_paginas_concatenadas_son_la_lista_completa(mundo, con_datos, ruta):
    eq = con_datos
    url = U + ruta.format(p=eq["proyecto"])
    cab = _con(mundo, eq["residente"])
    sep = "&" if "?" in url else "?"
    completa = mundo.http.get(url, headers=cab)
    assert completa.status_code == 200 and "x-siguiente-desde" not in completa.headers
    juntas, desde = [], 0
    for _ in range(50):
        r = mundo.http.get(f"{url}{sep}limite=3&desde={desde}", headers=cab)
        assert r.status_code == 200
        juntas += r.json()
        if "x-siguiente-desde" not in r.headers:
            break
        desde = int(r.headers["x-siguiente-desde"])
        assert len(r.json()) == 3
    assert juntas == completa.json()


def test_limite_fuera_de_rango_es_422(mundo, con_datos):
    r = mundo.http.get(f"{U}/proveedores?limite=0", headers=_con(mundo, con_datos["residente"]))
    assert r.status_code == 422
