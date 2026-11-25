"""Prueba de paridad: las MISMAS pruebas de la API, por HTTP real, contra el backend Spring Boot.

    SIGRO_SPRING_URL=http://localhost:8080 python -m pytest pruebas/test_api_obra.py

Diferencias con el `mundo` en proceso (que corre todo en una transacción que se revierte):
- El backend Spring corre aparte y confirma sus transacciones; por eso cada prueba recibe una base NUEVA,
  clonada de la base migrada (`CREATE DATABASE … TEMPLATE`), y la Fabrica escribe en modo autocommit.
- Después de clonar se le pide al backend que renueve sus conexiones (/interno/pruebas/reiniciar, solo existe
  con SIGRO_PRUEBAS=1).
"""
from __future__ import annotations

import dataclasses
import email
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import httpx
import psycopg
import pytest

from pruebas.conftest import Mundo, _ConexionFabrica, dsn, url_sqlalchemy

BASE = os.environ.get("SIGRO_SPRING_BASE", "sigro_spring")
PUERTO_INTEGRACIONES = int(os.environ.get("SIGRO_SPRING_INTEGRACIONES_PUERTO", "8099"))


class _IntegracionesFalsas(BaseHTTPRequestHandler):
    """Hace de Integraciones para el backend Spring (SIGRO_INTEGRACIONES_URL=http://localhost:8099): responde llamando a
    las funciones de app.integraciones, así los monkeypatch de cada prueba valen igual que en el mundo en proceso."""

    def log_message(self, *a):  # noqa: D401
        pass

    def _json(self, estado, cuerpo=None):
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else b""
        self.send_response(estado)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(datos)))
        self.end_headers()
        self.wfile.write(datos)

    def do_GET(self):
        if self.path.startswith("/notificaciones"):
            return self._notificaciones("GET")
        from app import integraciones
        partes = self.path.strip("/").split("/")
        if len(partes) == 2 and partes[0] in ("dni", "ruc"):
            r = (integraciones.consultar_dni if partes[0] == "dni" else integraciones.consultar_ruc)(partes[1])
            return self._json(404, {"detail": "no"}) if r is None else self._json(200, dataclasses.asdict(r))
        self._json(404, {"detail": "no"})

    def _notificaciones(self, metodo):
        from urllib.parse import parse_qsl, urlsplit

        from app.errores import ErrorSigro
        from app.modulos.notificaciones import rutas
        u = urlsplit(self.path)
        params = {k: (int(v) if v.isdigit() else v) for k, v in parse_qsl(u.query)}
        try:
            r = rutas._llamar(metodo, u.path, **params)
        except ErrorSigro as e:
            return self._json(e.status_code, e.detail)
        if r.status_code == 204:
            self.send_response(204)
            self.end_headers()
            return None
        return self._json(r.status_code, r.json())

    def do_POST(self):
        if self.path.startswith("/notificaciones"):
            return self._notificaciones("POST")
        from app import integraciones
        from app.errores import ErrorSigro
        if not self.path.startswith("/s10/relacion-insumos"):
            return self._json(404, {"detail": "no"})
        largo = int(self.headers.get("Content-Length", "0"))
        msg = email.message_from_bytes(b"Content-Type: " + self.headers["Content-Type"].encode() + b"\r\n\r\n" + self.rfile.read(largo))
        nombre, contenido, codigos = "archivo.pdf", b"", ""
        for parte in msg.get_payload():
            if parte.get_param("name", header="content-disposition") == "archivo":
                nombre, contenido = parte.get_filename() or nombre, parte.get_payload(decode=True)
            else:
                codigos = parte.get_payload(decode=True).decode()
        try:
            r = integraciones.leer_relacion_s10(nombre, contenido, [c for c in codigos.split(",") if c])
        except ErrorSigro as e:
            codigo = e.detail["codigo"]
            if codigo == "integraciones_no_disponible":          # caído: se corta la conexión sin responder
                self.close_connection = True
                self.connection.shutdown(2)
                return None
            estado = {"archivo_s10_invalido": 422, "integraciones_rechazo_el_servicio": 401}.get(codigo, 500)
            return self._json(estado, {"detail": e.detail["mensaje"]})
        self._json(200, r)


# Pruebas que cambian la CONFIGURACIÓN del proceso Python (variables de entorno del servicio): por HTTP no hay
# forma de cambiar la del backend Spring en caliente. Su equivalente se prueba en las pruebas JUnit del backend.
NO_APLICAN = {
    "test_sin_direccion_de_integraciones_es_503": "configuración del proceso (SIGRO_INTEGRACIONES_URL)",
    "test_avance_declara_lo_que_depende_de_almacen_si_no_responde_en_vez_de_inventarlo":
        "simula la caída de la API interna de almacén; en el backend Spring los módulos se llaman en proceso",
    "test_recibido_y_consumido_por_insumo_para_el_avance":
        "llama directo a la API /interno del servicio Python; en Spring es Almacen.recibidoConsumido (lo usa /avance)",
}


class _FabricaAutocommit(_ConexionFabrica):
    """La Fabrica escribe confirmando (el backend Spring corre en otro proceso). Un SAVEPOINT de la prueba abre una
    transacción explícita y su ROLLBACK TO la revierte: el mismo efecto que dentro de la transacción de la prueba."""

    def execute(self, sql, params=()):
        t = sql.strip().upper()
        if t.startswith("SAVEPOINT"):
            sql = "BEGIN"
        elif t.startswith("ROLLBACK TO") or t.startswith("RELEASE"):
            sql = "ROLLBACK" if t.startswith("ROLLBACK") else "COMMIT"
        return super().execute(sql, params)


def pytest_collection_modifyitems(items):  # noqa: D401
    for it in items:
        if it.originalname in NO_APLICAN:
            it.add_marker(pytest.mark.skip(reason="paridad HTTP: " + NO_APLICAN[it.originalname]))


class _HaciaSpring(httpx.BaseTransport):
    """El relevo entrega a «http://costo.interno/interno/eventos»: aquí esa dirección es el backend Spring."""

    def __init__(self):
        self._t = httpx.HTTPTransport()

    def handle_request(self, request):
        destino = httpx.URL(os.environ["SIGRO_SPRING_URL"])
        request.url = request.url.copy_with(scheme=destino.scheme, host=destino.host, port=destino.port)
        request.headers["host"] = destino.netloc.decode()
        return self._t.handle_request(request)


def _entregar_a_spring(mundo, monkeypatch):
    """El MISMO relevo de Python (app.relay) entrega el outbox de la base del backend Spring a su /interno/eventos."""
    from sqlalchemy import create_engine
    from sqlalchemy.orm import Session

    from app import relay
    monkeypatch.setenv("SIGRO_URL_COSTO", "http://costo.interno")
    monkeypatch.setenv("SIGRO_SERVICIO_TOKEN", "token-de-servicio-de-pruebas-0123456789")
    motor = create_engine(url_sqlalchemy(BASE))
    enviados = []
    with httpx.Client(transport=_HaciaSpring()) as costo:
        def enviar(ev):
            if ev["destino"] != "integracion":
                raise RuntimeError("los avisos no son de esta prueba")
            relay.enviar_http(ev, cliente=costo)
            enviados.append(ev["id"])

        def sesion():
            s = Session(bind=motor)
            s.execute(__import__("sqlalchemy").text("SET ROLE sigro_svc_relevo"))
            return s
        relay.procesar(sesion, enviar)
    motor.dispose()
    return enviados


import pruebas.test_almacen_costo_0010 as _t0010  # noqa: E402
_t0010._entregar = _entregar_a_spring


@pytest.fixture(scope="session", autouse=True)
def integraciones_falsas():
    srv = ThreadingHTTPServer(("127.0.0.1", PUERTO_INTEGRACIONES), _IntegracionesFalsas)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    yield
    srv.shutdown()


class _Anidada:
    def __init__(self, conn):
        self.conn = conn

    def rollback(self):
        self.conn.exec_driver_sql("ROLLBACK")

    def commit(self):
        self.conn.exec_driver_sql("COMMIT")

    def __enter__(self):
        return self

    def __exit__(self, tipo, *_):
        (self.rollback if tipo else self.commit)()


class _ConexionPrueba:
    """`mundo.conn` en modo autocommit; `begin_nested()` abre una transacción explícita (BEGIN … ROLLBACK/COMMIT)."""

    def __init__(self, conn, motor):
        self._c, self._motor, self._rol = conn, motor, None

    def exec_driver_sql(self, sql, *a, **k):
        t = sql.strip().upper()
        if t.startswith("SET ROLE") or t.startswith("RESET ROLE"):
            self._rol = None if t.startswith("RESET") else sql
        return self._c.exec_driver_sql(sql, *a, **k)

    def connect(self):
        """Para `Session(bind=mundo.conn)` (el relevo): una conexión propia, con el mismo rol que la de la prueba."""
        c = self._motor.connect()
        if self._rol:
            c.exec_driver_sql(self._rol)
        return c

    def begin_nested(self):
        self._c.exec_driver_sql("BEGIN")
        return _Anidada(self._c)

    def __getattr__(self, nombre):
        return getattr(self._c, nombre)


@pytest.fixture
def mundo(base_pruebas):
    from sqlalchemy import create_engine

    from pruebas.fabrica import Fabrica

    with psycopg.connect(dsn("postgres"), autocommit=True) as c:
        c.execute(f"DROP DATABASE IF EXISTS {BASE} WITH (FORCE)")
        c.execute(f"CREATE DATABASE {BASE} TEMPLATE {base_pruebas}")
    url = os.environ["SIGRO_SPRING_URL"].rstrip("/")
    httpx.post(f"{url}/interno/pruebas/reiniciar", timeout=30).raise_for_status()
    motor = create_engine(url_sqlalchemy(BASE), isolation_level="AUTOCOMMIT")
    conn = motor.connect()
    from app import internos
    costo = httpx.Client(base_url=url, timeout=60)       # la API interna de costo (/interno/eventos) es la del backend Spring
    internos.registrar_cliente_de_prueba("costo", costo)
    try:
        with httpx.Client(base_url=url, timeout=60) as cliente:
            yield Mundo(cliente, Fabrica(_FabricaAutocommit(conn)), _ConexionPrueba(conn, motor))
    finally:
        internos.registrar_cliente_de_prueba("costo", None)
        costo.close()
        conn.close()
        motor.dispose()
