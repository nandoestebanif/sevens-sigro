"""GET /salud/servicios del gateway: una sola llamada de la web despierta a todos los servicios y dice cuáles están listos.

No necesita la base: los servicios son aplicaciones de eco detrás de un transporte httpx falso.
"""
from __future__ import annotations

import asyncio
import time

import httpx
import pytest
from fastapi.testclient import TestClient

from app.servicios import gateway

BASE = ("obras", "catalogo", "compras", "tareo")


@pytest.fixture
def red(monkeypatch):
    """Configura las direcciones y devuelve el registro de qué servicio respondió y cómo."""
    for s in BASE:
        monkeypatch.setenv(f"SIGRO_URL_{s.upper()}", f"http://{s}.interno")
    monkeypatch.setenv("SIGRO_INTEGRACIONES_URL", "http://integraciones.interno")   # nombre de render.yaml
    for s in ("SIGRO_URL_ALMACEN", "SIGRO_URL_COSTO", "SIGRO_URL_INTEGRACIONES"):
        monkeypatch.delenv(s, raising=False)
    estado = {"caidos": set(), "con_error": {}, "demora": 0.0, "pedidos": []}

    class Transporte(httpx.AsyncBaseTransport):
        async def handle_async_request(self, request):
            destino = request.url.host.split(".")[0]
            estado["pedidos"].append((destino, request.url.path))
            if estado["demora"]:
                await asyncio.sleep(estado["demora"])
            if destino in estado["caidos"]:
                raise httpx.ConnectError("despertando")
            codigo = estado["con_error"].get(destino, 200)
            return httpx.Response(codigo, json={"estado": "ok", "servicio": destino})

    monkeypatch.setattr(gateway, "_cliente", httpx.AsyncClient(transport=Transporte()))
    return estado


def _cliente() -> TestClient:
    return TestClient(gateway.crear_app())


def _por_servicio(r) -> dict[str, dict]:
    return {s["servicio"]: s for s in r.json()["servicios"]}


def test_con_todos_arriba_responde_200_y_listo(red):
    r = _cliente().get("/salud/servicios")
    assert r.status_code == 200
    assert r.json()["listo"] is True
    assert set(_por_servicio(r)) == {*BASE, "integraciones"}
    assert all(s["listo"] and s["estado"] == "ok" for s in r.json()["servicios"])
    assert all(ruta == "/salud" for _, ruta in red["pedidos"])        # solo toca /salud de cada uno
    assert r.headers["cache-control"] == "no-store"


def test_si_uno_no_responde_dice_cual_y_responde_503(red):
    red["caidos"].add("compras")
    red["con_error"]["tareo"] = 500
    r = _cliente().get("/salud/servicios")
    assert r.status_code == 503 and r.json()["listo"] is False
    s = _por_servicio(r)
    assert s["compras"] == {**s["compras"], "listo": False, "estado": "no_responde"}
    assert s["tareo"]["estado"] == "error_500" and not s["tareo"]["listo"]
    assert s["obras"]["listo"] and s["catalogo"]["listo"] and s["integraciones"]["listo"]


def test_llama_a_todos_en_paralelo_no_en_cadena(red):
    red["demora"] = 0.4                                                # cada /salud tarda 0,4 s
    inicio = time.perf_counter()
    r = _cliente().get("/salud/servicios")
    transcurrido = time.perf_counter() - inicio
    assert r.status_code == 200
    assert len(red["pedidos"]) == 5
    assert transcurrido < 1.2, f"tardó {transcurrido:.2f} s: en cadena serían ≥ 2 s"


def test_almacen_y_costo_solo_si_estan_configurados(red, monkeypatch):
    assert {"almacen", "costo"}.isdisjoint(_por_servicio(_cliente().get("/salud/servicios")))
    monkeypatch.setenv("SIGRO_URL_ALMACEN", "http://almacen.interno")
    monkeypatch.setenv("SIGRO_URL_COSTO", "http://costo.interno")
    red["caidos"].add("costo")
    r = _cliente().get("/salud/servicios")
    s = _por_servicio(r)
    assert s["almacen"]["listo"] and s["costo"]["estado"] == "no_responde"
    assert r.status_code == 503


def test_un_servicio_de_base_sin_direccion_no_cuenta_como_listo(red, monkeypatch):
    monkeypatch.delenv("SIGRO_INTEGRACIONES_URL")
    r = _cliente().get("/salud/servicios")
    assert r.status_code == 503
    assert _por_servicio(r)["integraciones"] == {"servicio": "integraciones", "listo": False, "estado": "no_configurado", "ms": 0}


def test_respeta_la_espera_maxima_por_servicio(red, monkeypatch):
    """Un servicio que se cuelga no cuelga la respuesta: la web vuelve a preguntar a los 2 s."""
    monkeypatch.setenv("SIGRO_SALUD_ESPERA", "0.2")

    class Colgado(httpx.AsyncBaseTransport):
        async def handle_async_request(self, request):
            if request.url.host.startswith("tareo"):
                await asyncio.sleep(5)
            return httpx.Response(200, json={"estado": "ok"})

    monkeypatch.setattr(gateway, "_cliente", httpx.AsyncClient(transport=Colgado()))
    inicio = time.perf_counter()
    r = _cliente().get("/salud/servicios")
    assert time.perf_counter() - inicio < 2
    assert _por_servicio(r)["tareo"]["estado"] == "no_responde"


def test_la_web_puede_consultarlo_desde_otro_origen(red, monkeypatch):
    monkeypatch.setenv("SIGRO_CORS", "https://sigro-web.onrender.com")
    r = _cliente().get("/salud/servicios", headers={"Origin": "https://sigro-web.onrender.com"})
    assert r.headers["access-control-allow-origin"] == "https://sigro-web.onrender.com"
