"""HT-08: la API se identifica ante Integraciones con el token de servicio (sin red: transporte simulado)."""
import httpx

from app import integraciones

RESPUESTA = {"ruc": "20606456892", "razon_social": "SEVENS INGENIEROS SAC", "estado_sunat": "ACTIVO",
             "condicion": "HABIDO", "apto_para_comprar": True}


def _cliente(vistas: list):
    def responder(req: httpx.Request) -> httpx.Response:
        vistas.append(req)
        return httpx.Response(200, json=RESPUESTA)
    return httpx.Client(transport=httpx.MockTransport(responder))


def test_envia_el_token_de_servicio(monkeypatch):
    monkeypatch.setenv("SIGRO_INTEGRACIONES_URL", "http://integraciones:8000")
    monkeypatch.setenv("SIGRO_SERVICIO_TOKEN", "token-de-prueba-0123456789-0123456789")
    vistas: list = []
    r = integraciones.consultar_ruc("20606456892", cliente=_cliente(vistas))
    assert r is not None and r.razon_social == "SEVENS INGENIEROS SAC"
    assert vistas[0].headers["X-Servicio-Token"] == "token-de-prueba-0123456789-0123456789"
    assert str(vistas[0].url) == "http://integraciones:8000/ruc/20606456892"


def test_sin_token_configurado_no_envia_cabecera(monkeypatch):
    monkeypatch.setenv("SIGRO_INTEGRACIONES_URL", "http://integraciones:8000")
    monkeypatch.delenv("SIGRO_SERVICIO_TOKEN", raising=False)
    vistas: list = []
    integraciones.consultar_ruc("20606456892", cliente=_cliente(vistas))
    assert "X-Servicio-Token" not in vistas[0].headers


def test_integraciones_rechaza_el_token_devuelve_none(monkeypatch):
    """Si Integraciones responde 401/403, la compra no se bloquea: el proveedor queda sin validar."""
    monkeypatch.setenv("SIGRO_INTEGRACIONES_URL", "http://integraciones:8000")
    monkeypatch.setenv("SIGRO_SERVICIO_TOKEN", "mal")
    c = httpx.Client(transport=httpx.MockTransport(lambda req: httpx.Response(401, json={"detail": "token inválido"})))
    assert integraciones.consultar_ruc("20606456892", cliente=c) is None
