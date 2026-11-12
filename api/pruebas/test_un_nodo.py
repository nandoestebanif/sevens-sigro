"""Ambiente de pruebas en un solo nodo (ADR-10): el supervisor arma un proceso por servicio y cada uno ve SOLO su base.

Sin contenedor ni base: se prueba la función pura `procesos(entorno)` del supervisor.
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import pytest

_RUTA = Path(__file__).resolve().parents[2] / "infra" / "un_nodo" / "supervisor.py"
_spec = importlib.util.spec_from_file_location("supervisor_un_nodo", _RUTA)
sup = importlib.util.module_from_spec(_spec)
sys.modules[_spec.name] = sup                       # dataclass lo busca ahí
_spec.loader.exec_module(sup)

CON_BASE = ("obras", "catalogo", "compras", "tareo", "almacen", "costo")


def _entorno(**extra: str) -> dict[str, str]:
    e = {f"SIGRO_DB_{s.upper()}": f"postgresql+psycopg://sigro_{s}:clave-{s}@neon/neondb" for s in CON_BASE}
    e |= {"SIGRO_DB_RELEVO": "postgresql+psycopg://sigro_relevo:clave-relevo@neon/neondb",
          "SIGRO_JWT_SECRET": "x" * 40, "SIGRO_SERVICIO_TOKEN": "token-servicio", "FACTILIZA_TOKEN": "factiliza",
          "SIGRO_ENTORNO": "pruebas", "PATH": "/usr/bin", "PORT": "10000",
          "SIGRO_URL_OBRAS": "https://sigro-obras.onrender.com"}      # una URL vieja del panel no debe ganar
    return e | extra


def _por_nombre(lista) -> dict:
    return {p.nombre: p for p in lista}


def test_un_proceso_por_servicio_en_su_puerto():
    p = _por_nombre(sup.procesos(_entorno()))
    assert list(p) == ["gateway", *CON_BASE, "integraciones"]
    assert "10000" in p["gateway"].comando                            # $PORT de Render
    assert "0.0.0.0" in p["gateway"].comando                          # solo el gateway es público
    for s, puerto in zip(CON_BASE, range(8001, 8007)):
        assert str(puerto) in p[s].comando and "127.0.0.1" in p[s].comando
        assert f"app.servicios.{s}:app" in p[s].comando
    assert "8007" in p["integraciones"].comando and "main:app" in p["integraciones"].comando


def test_cada_servicio_recibe_solo_su_cadena_de_base():
    entorno = _entorno()
    for proceso in sup.procesos(entorno):
        vistas = [v for v in proceso.entorno.values() if "postgresql" in v]
        if proceso.nombre in CON_BASE:
            assert vistas == [entorno[f"SIGRO_DB_{proceso.nombre.upper()}"]], proceso.nombre
            assert proceso.entorno["SIGRO_APP_DATABASE_URL"].startswith(f"postgresql+psycopg://sigro_{proceso.nombre}:")
        else:
            assert vistas == [], proceso.nombre                        # gateway e integraciones: ninguna base
        assert not any(k.startswith("SIGRO_DB_") for k in proceso.entorno), proceso.nombre


def test_se_hablan_por_127_0_0_1_y_no_por_onrender():
    for proceso in sup.procesos(_entorno()):
        if proceso.nombre == "integraciones":
            continue
        assert proceso.entorno["SIGRO_URL_OBRAS"] == "http://127.0.0.1:8001"
        assert proceso.entorno["SIGRO_URL_COSTO"] == "http://127.0.0.1:8006"
        assert proceso.entorno["SIGRO_INTEGRACIONES_URL"] == "http://127.0.0.1:8007"
        assert not any("onrender.com" in v for v in proceso.entorno.values())


def test_secretos_solo_para_quien_los_usa():
    p = _por_nombre(sup.procesos(_entorno()))
    assert "SIGRO_JWT_SECRET" not in p["gateway"].entorno
    assert "FACTILIZA_TOKEN" not in p["obras"].entorno and "FACTILIZA_TOKEN" not in p["gateway"].entorno
    assert p["obras"].entorno["SIGRO_JWT_SECRET"] == "x" * 40         # obras firma las sesiones; los demás las validan
    integ = p["integraciones"].entorno
    assert integ["FACTILIZA_TOKEN"] == "factiliza"
    assert integ["SERVICIO_TOKEN"] == "token-servicio"                # exige X-Servicio-Token (HT-08)
    assert "SIGRO_JWT_SECRET" not in integ and "SIGRO_ENTORNO" not in integ


def test_sin_su_variable_de_base_el_servicio_no_arranca_y_los_demas_si():
    entorno = _entorno()
    del entorno["SIGRO_DB_COSTO"]
    p = _por_nombre(sup.procesos(entorno))
    assert p["costo"].falta == "SIGRO_DB_COSTO"
    assert "SIGRO_APP_DATABASE_URL" not in p["costo"].entorno          # nunca cae a la cadena de desarrollo
    assert all(p[s].falta is None for s in CON_BASE if s != "costo")


def test_relevo_y_notificaciones_solo_con_extras():
    assert {"relevo", "notificaciones"}.isdisjoint(_por_nombre(sup.procesos(_entorno())))
    p = _por_nombre(sup.procesos(_entorno(SIGRO_UN_NODO_EXTRAS="1")))
    assert p["relevo"].entorno["SIGRO_APP_DATABASE_URL"].startswith("postgresql+psycopg://sigro_relevo:")
    assert p["relevo"].entorno["SIGRO_NOTIFICACIONES_URL"] == "http://127.0.0.1:8008"
    assert p["relevo"].entorno["SIGRO_JWT_SECRET"]                     # app.config lo exige fuera de desarrollo
    assert "FACTILIZA_TOKEN" not in p["relevo"].entorno
    assert p["notificaciones"].entorno["NOTIF_TOKEN"] == "token-servicio"
    assert not any("postgresql" in v for v in p["notificaciones"].entorno.values())


@pytest.mark.parametrize(("actual", "duro", "esperado"), [
    (0.0, 1, 1.0), (1.0, 1, 2.0), (16.0, 1, 30.0), (30.0, 5, 30.0),   # caídas seguidas: 1, 2, 4 … tope 30 s
    (30.0, 61, 1.0),                                                    # aguantó un minuto: vuelve a 1 s
])
def test_reinicio_con_espera_creciente(actual, duro, esperado):
    assert sup.siguiente_espera(actual, duro) == esperado
