"""Nodo único en UN proceso (ADR-10, 04/11/2026): todas las apps en un event loop sin mezclar sus bases.

Sin base ni contenedor. Lo que se prueba:
- cada servicio toma SU cadena (SIGRO_DB_<SERVICIO>) según la petición en curso, y sin servicio no toma ninguna;
- un motor y un pool por servicio, chico (1..POOL_MAX);
- el lanzador: direcciones, variables que quita, y que si un servidor cae el proceso entero termina con código 1;
- /salud/servicios informa la memoria del proceso;
- argon2 no corre más de SIGRO_ARGON2_SIMULTANEOS verificaciones a la vez (64 MiB cada una).
"""
from __future__ import annotations

import importlib.util
import os
import socket
import subprocess
import sys
import threading
import time
import urllib.request
from pathlib import Path

import pytest
from starlette.testclient import TestClient

from app import config, contexto, db, seguridad
from app.servicios import gateway

RAIZ = Path(__file__).resolve().parents[2]
_RUTA = RAIZ / "infra" / "un_nodo" / "un_proceso.py"
_spec = importlib.util.spec_from_file_location("un_proceso_sigro", _RUTA)
up = importlib.util.module_from_spec(_spec)
sys.modules[_spec.name] = up
_spec.loader.exec_module(up)

CON_BASE = ("obras", "catalogo", "compras", "tareo", "almacen", "costo")


def _cadena(s: str) -> str:
    return f"postgresql+psycopg://sigro_{s}:clave-{s}@127.0.0.1:5432/sigro"


@pytest.fixture
def un_proceso(monkeypatch):
    monkeypatch.setenv("SIGRO_UN_PROCESO", "1")
    monkeypatch.delenv("SIGRO_APP_DATABASE_URL", raising=False)
    for s in (*CON_BASE, "relevo"):
        monkeypatch.setenv(f"SIGRO_DB_{s.upper()}", _cadena(s))


def _en(servicio: str | None, f):
    marca = contexto.SERVICIO.set(servicio)
    try:
        return f()
    finally:
        contexto.SERVICIO.reset(marca)


# ── la base de cada servicio ─────────────────────────────────────────────────────────────────────────────────────

def test_cada_servicio_usa_su_propia_cadena(un_proceso):
    for s in (*CON_BASE, "relevo"):
        assert _en(s, config.url_de_base) == _cadena(s)
        assert _en(s, lambda: config.cargar().database_url) == _cadena(s)


def test_sin_servicio_en_curso_no_se_entrega_ninguna_cadena(un_proceso):
    with pytest.raises(RuntimeError) as e:
        _en(None, config.url_de_base)
    assert "postgresql" not in str(e.value)


def test_sin_su_variable_falla_y_no_toma_la_de_otro(un_proceso, monkeypatch):
    monkeypatch.delenv("SIGRO_DB_COMPRAS")
    with pytest.raises(RuntimeError) as e:
        _en("compras", config.url_de_base)
    assert "SIGRO_DB_COMPRAS" in str(e.value) and "clave-" not in str(e.value)


def test_cargar_la_configuracion_no_exige_servicio(un_proceso):
    assert _en(None, config.cargar).jwt_horas > 0            # el JWT y lo demás no dependen de la base


def test_con_un_proceso_por_servicio_sigue_siendo_sigro_app_database_url(monkeypatch):
    monkeypatch.delenv("SIGRO_UN_PROCESO", raising=False)
    monkeypatch.setenv("SIGRO_APP_DATABASE_URL", _cadena("obras"))
    assert _en("catalogo", config.url_de_base) == _cadena("obras")   # producción: la única que el proceso recibe


def test_un_motor_por_servicio_y_el_mismo_en_cada_peticion(un_proceso, monkeypatch):
    monkeypatch.setattr(db, "_motores", {})
    obras, catalogo = _en("obras", db.motor), _en("catalogo", db.motor)
    assert obras is not catalogo
    assert obras is _en("obras", db.motor)
    assert obras.url.username == "sigro_obras" and catalogo.url.username == "sigro_catalogo"
    for m in db._motores.values():
        m.dispose()


def test_pool_de_1_a_pool_max(monkeypatch):
    for v in ("SIGRO_DB_POOL", "POOL_MAX"):
        monkeypatch.delenv(v, raising=False)
    op = db.opciones_motor("postgresql+psycopg://u:x@localhost/sigro")
    assert (op["pool_size"], op["pool_size"] + op["max_overflow"]) == (1, 3)
    monkeypatch.setenv("POOL_MAX", "5")
    op = db.opciones_motor("postgresql+psycopg://u:x@localhost/sigro")
    assert (op["pool_size"], op["max_overflow"]) == (1, 4)
    monkeypatch.setenv("SIGRO_DB_POOL", "4")
    monkeypatch.setenv("POOL_MAX", "2")                      # un máximo menor que el mínimo no deja pool negativo
    op = db.opciones_motor("postgresql+psycopg://u:x@localhost/sigro")
    assert (op["pool_size"], op["max_overflow"]) == (4, 0)


# ── el lanzador ──────────────────────────────────────────────────────────────────────────────────────────────────

def _entorno(**extra: str) -> dict[str, str]:
    e = {f"SIGRO_DB_{s.upper()}": _cadena(s) for s in (*CON_BASE, "relevo")}
    e |= {"PORT": "10000", "SIGRO_SERVICIO_TOKEN": "token", "SIGRO_APP_DATABASE_URL": _cadena("obras"),
          "SIGRO_DATABASE_URL": "postgresql+psycopg://sigro_owner:x@neon/db",
          "SIGRO_URL_OBRAS": "https://sigro-obras.onrender.com"}           # una URL vieja del panel no debe ganar
    return e | extra


def test_el_proceso_quita_las_cadenas_globales_y_habla_por_127_0_0_1():
    fijar, quitar = up.entorno_del_proceso(_entorno())
    assert set(quitar) == {"SIGRO_APP_DATABASE_URL", "SIGRO_DATABASE_URL"}
    assert fijar["SIGRO_UN_PROCESO"] == "1"
    urls = {k: v for k, v in fijar.items() if "URL" in k}
    assert urls and all(v.startswith("http://127.0.0.1:") for v in urls.values())
    assert fijar["SIGRO_URL_OBRAS"] == "http://127.0.0.1:8001"
    assert fijar["SERVICIO_TOKEN"] == fijar["NOTIF_TOKEN"] == "token"
    assert not any("postgresql" in v for v in fijar.values())


def test_solo_el_gateway_es_publico_y_cada_servicio_en_su_puerto():
    a = {x.nombre: x for x in up.apps(_entorno())}
    assert list(a) == ["gateway", *CON_BASE, "integraciones"]
    assert (a["gateway"].host, a["gateway"].puerto) == ("0.0.0.0", 10000)
    for s, puerto in zip((*CON_BASE, "integraciones"), range(8001, 8008)):
        assert (a[s].host, a[s].puerto, a[s].falta) == ("127.0.0.1", puerto, None)


def test_extras_y_servicios_sin_base():
    e = _entorno(SIGRO_UN_NODO_EXTRAS="1")
    del e["SIGRO_DB_TAREO"]
    a = {x.nombre: x for x in up.apps(e)}
    assert a["tareo"].falta == "SIGRO_DB_TAREO" and a["notificaciones"].puerto == 8008
    assert up.con_relevo(e) and not up.con_relevo(_entorno())
    del e["SIGRO_DB_RELEVO"]
    assert not up.con_relevo(e)


def _puertos_libres(n: int) -> int:
    for base in range(23000, 60000, 37):
        socks = []
        try:
            for p in range(base, base + n):
                s = socket.socket()
                s.bind(("127.0.0.1", p))
                socks.append(s)
            return base
        except OSError:
            continue
        finally:
            for s in socks:
                s.close()
    raise RuntimeError("sin puertos libres")


def _lanzar(base: int, **extra: str) -> subprocess.Popen:
    entorno = {k: v for k, v in os.environ.items() if not k.startswith("SIGRO_")}
    entorno |= {f"SIGRO_DB_{s.upper()}": _cadena(s) for s in CON_BASE}
    entorno |= {"PORT": str(base), "SIGRO_UN_NODO_PUERTO_BASE": str(base + 1), "SIGRO_UN_NODO_EXTRAS": "0",
                "SIGRO_UN_NODO_API": str(RAIZ / "api"), "SIGRO_UN_NODO_RAIZ": str(RAIZ),
                "SIGRO_SERVICIO_TOKEN": "token", "PYTHONIOENCODING": "utf-8"} | extra
    return subprocess.Popen([sys.executable, str(_RUTA)], env=entorno, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, encoding="utf-8")


def _get(url: str) -> tuple[int, bytes]:
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def test_todas_las_apps_responden_desde_un_solo_proceso():
    base = _puertos_libres(9)
    p = _lanzar(base)
    try:
        fin = time.monotonic() + 60
        while True:
            try:
                codigo, cuerpo = _get(f"http://127.0.0.1:{base}/salud/servicios")
                break
            except OSError:
                assert p.poll() is None and time.monotonic() < fin, "el lanzador no arrancó"
                time.sleep(0.5)
        import json
        datos = json.loads(cuerpo)
        assert codigo == 200 and datos["listo"], datos
        assert {s["servicio"] for s in datos["servicios"]} == {*CON_BASE, "integraciones"}
        assert "memoria_mb" in datos
        # obras atiende de verdad en su puerto interno (y sin sesión responde 401 sin tocar la base)
        assert _get(f"http://127.0.0.1:{base}/api/v1/obras")[0] == 401
    finally:
        if os.name == "posix":
            p.send_signal(__import__("signal").SIGTERM)
            assert p.wait(timeout=20) == 0                   # se detiene ordenadamente con la señal de Render
        else:
            p.kill()
            p.wait(timeout=20)


def test_si_un_servidor_cae_el_proceso_entero_termina_con_codigo_1():
    base = _puertos_libres(9)
    ocupado = socket.socket()
    ocupado.bind(("127.0.0.1", base + 3))                     # el puerto de compras ya está tomado
    ocupado.listen()
    try:
        p = _lanzar(base)
        salida, _ = p.communicate(timeout=60)
    finally:
        ocupado.close()
    assert p.returncode == 1, salida[-2000:]
    assert "compras" in salida and "se detiene todo el proceso" in salida


# ── memoria y argon2 ─────────────────────────────────────────────────────────────────────────────────────────────

def test_salud_servicios_informa_la_memoria(monkeypatch):
    for s in (*CON_BASE, "integraciones"):
        monkeypatch.delenv(f"SIGRO_URL_{s.upper()}", raising=False)
    monkeypatch.delenv("SIGRO_INTEGRACIONES_URL", raising=False)
    gateway._olvidar_salud()
    try:
        datos = TestClient(gateway.crear_app()).get("/salud/servicios").json()
    finally:
        gateway._olvidar_salud()
    assert "memoria_mb" in datos
    if sys.platform.startswith("linux"):
        assert datos["memoria_mb"] > 0 and datos["memoria_actual_mb"] > 0


def test_argon2_no_corre_mas_de_dos_a_la_vez(monkeypatch):
    estado = {"ahora": 0, "max": 0}
    candado = threading.Lock()

    class Lento:
        def verify(self, _h, _c):
            with candado:
                estado["ahora"] += 1
                estado["max"] = max(estado["max"], estado["ahora"])
            time.sleep(0.05)
            with candado:
                estado["ahora"] -= 1
            return True

    monkeypatch.setattr(seguridad, "_hasher", Lento())
    monkeypatch.setattr(seguridad, "_turnos", threading.BoundedSemaphore(2))
    hilos = [threading.Thread(target=seguridad.verificar, args=("x", "h")) for _ in range(10)]
    for h in hilos:
        h.start()
    for h in hilos:
        h.join()
    assert estado["max"] == 2
