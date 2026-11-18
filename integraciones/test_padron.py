"""El proveedor del padrón se elige por entorno y la respuesta de apisperu se normaliza igual que la de Factiliza."""
import asyncio
import importlib
import sys
from pathlib import Path

import httpx
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))
integ = importlib.import_module("main")


class _Resp:
    def __init__(self, cuerpo, status=200):
        self._c, self.status_code = cuerpo, status

    def json(self):
        return self._c


def _cliente_que_devuelve(cuerpo, status=200):
    class _C:
        def __init__(self, *a, **k): ...
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def get(self, url, headers=None):
            _C.url, _C.headers = url, headers
            return _Resp(cuerpo, status)
    return _C


@pytest.fixture(autouse=True)
def _limpio(monkeypatch):
    integ._cache.clear(); integ._cache_dni.clear()
    monkeypatch.setenv("PADRON_PROVEEDOR", "apisperu")
    monkeypatch.setenv("APISPERU_TOKEN", "token-de-prueba")
    monkeypatch.delenv("SERVICIO_TOKEN", raising=False)


def test_ruc_de_apisperu_se_normaliza(monkeypatch):
    C = _cliente_que_devuelve({"ruc": "20606456892", "razonSocial": "SEVENS INGENIEROS S.A.C.", "estado": "ACTIVO", "condicion": "HABIDO", "direccion": "LIMA"})
    monkeypatch.setattr(integ.httpx, "AsyncClient", C)
    r = asyncio.run(integ.consultar_ruc("20606456892"))
    assert r.razon_social == "SEVENS INGENIEROS S.A.C." and r.apto_para_comprar
    assert C.url.startswith("https://dniruc.apisperu.com/api/v1/ruc/20606456892")
    assert C.headers["Authorization"] == "Bearer token-de-prueba"


def test_respuesta_dentro_de_data_tambien_sirve(monkeypatch):
    monkeypatch.setattr(integ.httpx, "AsyncClient", _cliente_que_devuelve({"data": {"ruc": "20100130204", "razonSocial": "X SAC", "estado": "BAJA DE OFICIO", "condicion": "NO HABIDO"}}))
    r = asyncio.run(integ.consultar_ruc("20100130204"))
    assert r.razon_social == "X SAC" and not r.apto_para_comprar


def test_dni_de_apisperu(monkeypatch):
    monkeypatch.setattr(integ.httpx, "AsyncClient", _cliente_que_devuelve({"dni": "12345678", "nombres": "ANA", "apellidoPaterno": "PEREZ", "apellidoMaterno": "ROJAS"}))
    r = asyncio.run(integ.consultar_dni("12345678"))
    assert r.nombre_completo == "ANA PEREZ ROJAS" and r.apellido_paterno == "PEREZ"


def test_sin_token_responde_503_y_la_api_deja_por_validar(monkeypatch):
    monkeypatch.delenv("APISPERU_TOKEN")
    with pytest.raises(integ.HTTPException) as e:
        asyncio.run(integ.consultar_ruc("20606456892"))
    assert e.value.status_code == 503


def test_por_defecto_sigue_factiliza(monkeypatch):
    monkeypatch.delenv("PADRON_PROVEEDOR")
    assert integ._proveedor() == "factiliza"
