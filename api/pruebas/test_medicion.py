"""Medición por petición (docs/rendimiento-render.md): Server-Timing, CF-Ray en el log y pool de la base.

Ninguna levanta PostgreSQL: el contador de consultas se prueba con SQLite en memoria (los eventos de SQLAlchemy
son los mismos) y el gateway, con un servicio falso detrás de un transporte httpx.
"""
from __future__ import annotations

import logging
import re

import httpx
import pytest
from fastapi import APIRouter
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text

from app import db, internos, medicion
from app.servicios import gateway
from app.servicios.base import crear_servicio


def _entradas(valor: str) -> dict[str, tuple[float, str | None]]:
    """'a;dur=1.5;desc="x", b;dur=2' → {'a': (1.5, 'x'), 'b': (2.0, None)}"""
    salida = {}
    for parte in valor.split(", "):
        nombre, *atrs = parte.split(";")
        dur = next(float(a[4:]) for a in atrs if a.startswith("dur="))
        desc = next((a[6:-1] for a in atrs if a.startswith("desc=")), None)
        salida[nombre] = (dur, desc)
    return salida


@pytest.fixture
def servicio_con_base():
    """Un servicio de SIGRO de verdad (crear_servicio) cuyo endpoint SÍNCRONO consulta una base SQLite instrumentada:
    así se prueba también que lo sumado en el hilo del threadpool llega a la cabecera."""
    motor = db.instrumentar(create_engine("sqlite://"))
    r = APIRouter()

    @r.get("/tres-consultas")
    def tres():
        with motor.connect() as c:
            for _ in range(3):
                c.execute(text("SELECT 1"))
        return {"ok": True}

    @r.get("/llama-a-obras")
    def llama():
        internos.json_de("obras", "GET", "/interno/eco")
        return {"ok": True}

    return TestClient(crear_servicio("compras", "prueba", [r]))


def test_el_servicio_devuelve_server_timing_con_total_base_y_conexion(servicio_con_base):
    r = servicio_con_base.get("/api/v1/tres-consultas")
    assert r.status_code == 200
    e = _entradas(r.headers["server-timing"])
    assert set(e) == {"compras", "compras-db", "compras-db-con", "compras-interno"}
    assert e["compras-db"][1] == "3 consultas"
    assert e["compras-db-con"][1] == "1 conexiones nuevas"
    assert e["compras-interno"][1] == "0 llamadas"
    assert e["compras"][0] >= e["compras-db"][0]        # el total incluye a la base


def test_cuenta_las_llamadas_internas_y_les_pasa_el_ray(servicio_con_base, monkeypatch):
    vistos = []

    def eco(request: httpx.Request) -> httpx.Response:
        vistos.append(request.headers.get("x-sigro-ray"))
        return httpx.Response(200, json={"ok": True})

    internos.registrar_cliente_de_prueba("obras", httpx.Client(transport=httpx.MockTransport(eco), base_url="http://obras"))
    try:
        r = servicio_con_base.get("/api/v1/llama-a-obras", headers={"x-sigro-ray": "8f00aa-SCL"})
    finally:
        internos.registrar_cliente_de_prueba("obras", None)
    assert _entradas(r.headers["server-timing"])["compras-interno"][1] == "1 llamadas"
    assert vistos == ["8f00aa-SCL"]


def test_el_log_lleva_el_ray_y_los_tiempos(servicio_con_base, caplog):
    with caplog.at_level(logging.INFO, logger="sigro.medicion"):
        medicion.log.propagate = True
        try:
            servicio_con_base.get("/api/v1/tres-consultas", headers={"cf-ray": "a546f3f5ec0638b6-SCL"})
        finally:
            medicion.log.propagate = False
    linea = next(r.getMessage() for r in caplog.records if "tres-consultas" in r.getMessage())
    assert "servicio=compras" in linea and "ray=a546f3f5ec0638b6-SCL" in linea
    assert re.search(r"db=[\d.]+ms/3 ", linea)


def test_sin_server_timing_si_se_apaga(servicio_con_base, monkeypatch):
    monkeypatch.setenv("SIGRO_SERVER_TIMING", "0")
    assert "server-timing" not in servicio_con_base.get("/api/v1/tres-consultas").headers


# ------------------------------------------------------------------------------------------------- gateway
@pytest.fixture
def gateway_con_obras(monkeypatch):
    monkeypatch.setenv("SIGRO_URL_OBRAS", "http://obras.interno")
    recibido = {}

    class Transporte(httpx.AsyncBaseTransport):
        async def handle_async_request(self, request):
            recibido["ray"] = request.headers.get("x-sigro-ray")
            return httpx.Response(200, json=[], headers={"server-timing": 'obras;dur=40.0, obras-db;dur=30.0;desc="2 consultas"'})

    monkeypatch.setattr(gateway, "_cliente", httpx.AsyncClient(transport=Transporte()))
    return TestClient(gateway.crear_app()), recibido


def test_el_gateway_suma_lo_suyo_y_conserva_lo_del_servicio(gateway_con_obras):
    cliente, _ = gateway_con_obras
    e = _entradas(cliente.get("/api/v1/obras").headers["server-timing"])
    assert list(e)[:2] == ["gw", "gw-up"]
    assert e["obras-db"] == (30.0, "2 consultas")
    assert e["gw"][0] >= e["gw-up"][0]


def test_el_gateway_reenvia_el_cf_ray_y_no_cree_un_ray_de_afuera(gateway_con_obras):
    cliente, recibido = gateway_con_obras
    cliente.get("/api/v1/obras", headers={"cf-ray": "a546f4ef8a38e9ae-SCL", "x-sigro-ray": "inventado"})
    assert recibido["ray"] == "a546f4ef8a38e9ae-SCL"
    cliente.get("/api/v1/obras", headers={"x-sigro-ray": "inventado"})     # sin Cloudflare (local): no se reenvía
    assert recibido["ray"] is None


# ------------------------------------------------------------------------------------------------- pool
def test_con_la_cadena_pooler_de_neon_se_apagan_los_prepared_statements():
    pooler = "postgresql+psycopg://sigro_obras:x@ep-frio-123-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require"
    directa = "postgresql+psycopg://sigro_obras:x@ep-frio-123.us-east-2.aws.neon.tech/neondb?sslmode=require"
    assert db.es_pooler(pooler) and not db.es_pooler(directa)
    assert db.opciones_motor(pooler)["connect_args"] == {"prepare_threshold": None}
    assert "connect_args" not in db.opciones_motor(directa)


def test_el_pool_es_chico_persistente_y_se_renueva_antes_que_neon_lo_corte(monkeypatch):
    for v in ("SIGRO_DB_POOL", "POOL_MAX", "SIGRO_DB_RECICLAR_S"):
        monkeypatch.delenv(v, raising=False)
    op = db.opciones_motor("postgresql+psycopg://u:x@localhost/sigro")
    assert (op["pool_size"], op["max_overflow"]) == (1, 2)          # 1 abierta, hasta 3 en picos (POOL_MAX)
    assert op["pool_recycle"] < 300 and op["pool_pre_ping"] is True


def test_las_llamadas_internas_reusan_un_solo_cliente():
    assert internos._cliente_compartido() is internos._cliente_compartido()
