"""HU-07 por el gateway: POST /api/v1/catalogo/importaciones-s10 (incidencia del 28/10, decisión «A» del 22/10).

La web sube el PDF con su JWT; el catálogo lo reenvía a Integraciones con X-Servicio-Token. El navegador nunca ve
ese token y nunca habla con Integraciones. Lo que se prueba:
  - el permiso lo decide el servidor (residente y administrador sí; almacenero, gerente y SSOMA no, y sin llamar a nadie);
  - el catálogo manda el token y los códigos que YA tiene (no los que diga el cliente);
  - si Integraciones cae, rechaza el token o no entiende el archivo, la web recibe {codigo, mensaje} legible;
  - con el PDF real del cliente y el Integraciones real: 284 insumos, y un token equivocado se rechaza.
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import httpx
import pytest

from app import integraciones

U = "/api/v1/catalogo/importaciones-s10"
TOKEN = "token-de-servicio-de-prueba-0123456789"
RAIZ = Path(__file__).resolve().parents[2]
PDF_REAL = RAIZ / "insumos-cliente" / "5.3. Relacion de insumos.pdf"

LECTURA = {
    "obra": "MULTIFAMILIAR FLORES", "subpresupuesto": "002", "fecha": "09/01/2026",
    "resumen": {"leidos": 1, "a_crear": 1, "a_actualizar": 0, "fallidos": 0, "por_tipo": {"material": 1}, "parcial_total": 165061.0},
    "insumos": [{"codigo": "0203020003", "descripcion": "ACERO CORRUGADO FY=4200 KG/CM2 GRADO 60", "unidad": "kg",
                 "tipo": "material", "cantidad": 36357.0, "precio": 4.54, "parcial": 165060.78, "accion": "crear",
                 "advertencia": None}],
    "fallidos": [],
}


@pytest.fixture
def eq(mundo):
    return mundo.f.obra_completa()


@pytest.fixture
def entorno(monkeypatch):
    monkeypatch.setenv("SIGRO_INTEGRACIONES_URL", "http://integraciones:8000")
    monkeypatch.setenv("SIGRO_SERVICIO_TOKEN", TOKEN)


def simular(monkeypatch, responder) -> list[httpx.Request]:
    """Integraciones simulado: guarda lo que le llegó y responde con `responder`."""
    vistas: list[httpx.Request] = []

    def manejar(req: httpx.Request) -> httpx.Response:
        vistas.append(req)
        return responder(req)
    monkeypatch.setattr(integraciones, "_cliente_s10", lambda: httpx.Client(transport=httpx.MockTransport(manejar)))
    return vistas


def subir(mundo, uid, contenido=b"%PDF-1.4 relacion de insumos", nombre="5.3. Relacion de insumos.pdf"):
    return mundo.http.post(U, headers=mundo.con(uid), files={"archivo": (nombre, contenido, "application/pdf")})


# ═════════════════════════════════════════ el reenvío
def test_el_residente_sube_el_pdf_y_el_catalogo_lo_reenvia_con_el_token_de_servicio(mundo, eq, entorno, monkeypatch):
    vistas = simular(monkeypatch, lambda req: httpx.Response(200, json=LECTURA))
    r = subir(mundo, eq["residente"], b"%PDF-1.4 contenido")
    assert r.status_code == 200, r.text
    assert r.json() == LECTURA
    assert len(vistas) == 1
    req = vistas[0]
    assert str(req.url) == "http://integraciones:8000/s10/relacion-insumos"
    assert req.headers["X-Servicio-Token"] == TOKEN
    assert "authorization" not in req.headers          # el JWT del usuario no sale del catálogo
    cuerpo = req.read()
    assert b"%PDF-1.4 contenido" in cuerpo and b'filename="5.3. Relacion de insumos.pdf"' in cuerpo


def test_los_codigos_existentes_los_pone_el_servidor_desde_su_catalogo(mundo, eq, entorno, monkeypatch):
    mundo.f.insumo("0203020003")
    mundo.f.insumo("0201010001")
    vistas = simular(monkeypatch, lambda req: httpx.Response(200, json=LECTURA))
    r = mundo.http.post(U, headers=mundo.con(eq["residente"]),
                        files={"archivo": ("r.pdf", b"%PDF", "application/pdf")},
                        data={"codigos_existentes": "INVENTADO-1"})     # lo que mande el cliente no cuenta
    assert r.status_code == 200, r.text
    cuerpo = vistas[0].read().decode("latin-1")
    campo = cuerpo.split('name="codigos_existentes"')[1].split("\r\n\r\n", 1)[1].split("\r\n", 1)[0]
    assert set(campo.split(",")) >= {"0203020003", "0201010001"}
    assert "INVENTADO-1" not in campo


def test_el_administrador_asignado_tambien_puede_importar(mundo, eq, entorno, monkeypatch):
    simular(monkeypatch, lambda req: httpx.Response(200, json=LECTURA))
    assert subir(mundo, eq["admin"]).status_code == 200


# ═════════════════════════════════════════ control de acceso en el servidor
@pytest.mark.parametrize("rol", ["almacenero", "gerente", "ssoma"])
def test_quien_no_puede_importar_recibe_403_y_no_se_llama_a_integraciones(mundo, eq, entorno, monkeypatch, rol):
    vistas = simular(monkeypatch, lambda req: httpx.Response(200, json=LECTURA))
    r = subir(mundo, eq[rol])
    assert r.status_code == 403, r.text
    assert vistas == []


def test_sin_sesion_no_se_importa(mundo, eq, entorno, monkeypatch):
    vistas = simular(monkeypatch, lambda req: httpx.Response(200, json=LECTURA))
    r = mundo.http.post(U, files={"archivo": ("r.pdf", b"%PDF", "application/pdf")})
    assert r.status_code == 401
    assert vistas == []


# ═════════════════════════════════════════ el archivo
def test_sin_archivo_es_422(mundo, eq, entorno, monkeypatch):
    vistas = simular(monkeypatch, lambda req: httpx.Response(200, json=LECTURA))
    r = mundo.http.post(U, headers=mundo.con(eq["residente"]))
    assert r.status_code == 422 and vistas == []


def test_archivo_vacio_se_rechaza_sin_llamar_a_integraciones(mundo, eq, entorno, monkeypatch):
    vistas = simular(monkeypatch, lambda req: httpx.Response(200, json=LECTURA))
    r = subir(mundo, eq["residente"], b"")
    assert r.status_code == 422 and r.json()["codigo"] == "archivo_vacio"
    assert vistas == []


def test_un_archivo_de_mas_de_15_mb_se_rechaza(mundo, eq, entorno, monkeypatch):
    vistas = simular(monkeypatch, lambda req: httpx.Response(200, json=LECTURA))
    r = subir(mundo, eq["residente"], b"0" * (15 * 1024 * 1024 + 1))
    assert r.status_code == 413 and r.json()["codigo"] == "archivo_muy_grande"
    assert vistas == []


def test_si_integraciones_no_entiende_el_archivo_la_web_recibe_su_motivo(mundo, eq, entorno, monkeypatch):
    simular(monkeypatch, lambda req: httpx.Response(422, json={"detail": "Se espera el PDF exportado del S10."}))
    r = subir(mundo, eq["residente"], nombre="relacion.xlsx")
    assert r.status_code == 422
    assert r.json() == {"codigo": "archivo_s10_invalido", "mensaje": "Se espera el PDF exportado del S10."}


# ═════════════════════════════════════════ Integraciones con problemas
def test_si_integraciones_no_responde_es_503_legible(mundo, eq, entorno, monkeypatch):
    def caido(req):
        raise httpx.ConnectError("caído", request=req)
    simular(monkeypatch, caido)
    r = subir(mundo, eq["residente"])
    assert r.status_code == 503 and r.json()["codigo"] == "integraciones_no_disponible"
    assert "no se pierde" in r.json()["mensaje"]


def test_si_integraciones_rechaza_el_token_no_se_culpa_al_usuario(mundo, eq, entorno, monkeypatch):
    """Un 401 de Integraciones es un problema de configuración entre servicios: no debe cerrar la sesión de la web."""
    simular(monkeypatch, lambda req: httpx.Response(401, json={"detail": "Token de servicio inválido."}))
    r = subir(mundo, eq["residente"])
    assert r.status_code == 503 and r.json()["codigo"] == "integraciones_rechazo_el_servicio"


def test_un_error_inesperado_de_integraciones_es_503(mundo, eq, entorno, monkeypatch):
    simular(monkeypatch, lambda req: httpx.Response(500, text="Internal Server Error"))
    r = subir(mundo, eq["residente"])
    assert r.status_code == 503 and r.json()["codigo"] == "integraciones_con_error"


def test_sin_direccion_de_integraciones_es_503(mundo, eq, monkeypatch):
    monkeypatch.delenv("SIGRO_INTEGRACIONES_URL", raising=False)
    vistas = simular(monkeypatch, lambda req: httpx.Response(200, json=LECTURA))
    r = subir(mundo, eq["residente"])
    assert r.status_code == 503 and r.json()["codigo"] == "integraciones_no_configurado"
    assert vistas == []


def test_la_lectura_no_escribe_en_el_catalogo(mundo, eq, entorno, monkeypatch):
    """Leer es solo leer: crear o actualizar es el paso siguiente (POST /catalogo/insumos/importar-s10)."""
    simular(monkeypatch, lambda req: httpx.Response(200, json=LECTURA))
    antes = mundo.f.q1("SELECT count(*) AS id FROM catalogo.insumo")
    assert subir(mundo, eq["residente"]).status_code == 200
    assert mundo.f.q1("SELECT count(*) AS id FROM catalogo.insumo") == antes


# ═════════════════════════════════════════ con el PDF real y el Integraciones real
def _integraciones_real():
    pytest.importorskip("pdfplumber", reason="pdfplumber es dependencia de Integraciones, no de la API")
    if not PDF_REAL.exists():
        pytest.skip("falta el PDF real del cliente en insumos-cliente/")
    carpeta = str(RAIZ / "integraciones")
    sys.path.insert(0, carpeta)
    try:
        spec = importlib.util.spec_from_file_location("integraciones_main_prueba", Path(carpeta) / "main.py")
        modulo = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(modulo)
    finally:
        sys.path.remove(carpeta)
    return modulo.app


@pytest.fixture
def integraciones_real(entorno, monkeypatch):
    from fastapi.testclient import TestClient
    app = _integraciones_real()
    monkeypatch.setenv("SERVICIO_TOKEN", TOKEN)          # Integraciones con el token activado, como en despliegue
    monkeypatch.setattr(integraciones, "_cliente_s10", lambda: TestClient(app))
    return app


def test_el_pdf_real_del_cliente_pasa_por_el_gateway_y_da_284_insumos(mundo, eq, integraciones_real):
    r = subir(mundo, eq["residente"], PDF_REAL.read_bytes())
    assert r.status_code == 200, r.text
    d = r.json()
    assert d["resumen"]["leidos"] == 284 and d["resumen"]["fallidos"] == 0
    assert d["resumen"]["por_tipo"] == {"mano_obra": 6, "material": 237, "equipo": 18, "subcontrato": 23}
    assert d["resumen"]["a_crear"] == 284


def test_con_el_pdf_real_lo_que_ya_esta_en_el_catalogo_sale_como_actualizar(mundo, eq, integraciones_real):
    primero = subir(mundo, eq["residente"], PDF_REAL.read_bytes()).json()["insumos"][0]["codigo"]
    mundo.f.insumo(primero)
    d = subir(mundo, eq["residente"], PDF_REAL.read_bytes()).json()
    assert d["resumen"]["a_actualizar"] == 1 and d["resumen"]["a_crear"] == 283
    assert next(i for i in d["insumos"] if i["codigo"] == primero)["accion"] == "actualizar"


def test_integraciones_real_rechaza_un_token_equivocado(mundo, eq, integraciones_real, monkeypatch):
    monkeypatch.setenv("SIGRO_SERVICIO_TOKEN", "otro-token-que-integraciones-no-conoce-000")
    r = subir(mundo, eq["residente"], PDF_REAL.read_bytes())
    assert r.status_code == 503 and r.json()["codigo"] == "integraciones_rechazo_el_servicio"


def test_integraciones_real_sin_token_responde_401(integraciones_real):
    """Hecho cuando (plan, tarea 2): una llamada directa a Integraciones sin token da 401."""
    from fastapi.testclient import TestClient
    r = TestClient(integraciones_real).post("/s10/relacion-insumos",
                                            files={"archivo": ("r.pdf", PDF_REAL.read_bytes(), "application/pdf")})
    assert r.status_code == 401
