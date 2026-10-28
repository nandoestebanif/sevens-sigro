"""Que SIGRO sea de verdad un sistema de microservicios, y no un monolito partido en carpetas.

Se prueba lo que define a un microservicio:
  1. datos propios: el usuario de base de datos de cada servicio NO puede leer el esquema de otro;
  2. código propio: los módulos de un servicio no importan los de otro;
  3. comunicación por API: lo ajeno se pide por /interno (con token de servicio) y el gateway no lo expone;
  4. una sola puerta: toda ruta pública está en la tabla del gateway y va a su dueño;
  5. fallos aislados: si un servicio cae, solo fallan las rutas que lo necesitan.
"""
from __future__ import annotations

import ast
from pathlib import Path

import httpx
import pytest

from app import internos
from app.enrutado import servicio_para
from app.servicios import fabricas
from pruebas.conftest import SIN_PERMISO, rechaza

APP = Path(__file__).resolve().parents[1] / "app"

# servicio → (su esquema, módulos de código que le pertenecen)
DUENOS = {
    "obras": ("obra", {"obra", "notificaciones"}),
    "catalogo": ("catalogo", {"catalogo"}),
    "compras": ("compras", {"compras"}),
    "tareo": ("tareo", {"tareo"}),
}
ESQUEMAS = {"obra": "obra.usuario", "catalogo": "catalogo.insumo", "compras": "compras.proveedor", "tareo": "tareo.trabajador"}


# ---------------------------------------------------------------- 1. datos propios
@pytest.mark.parametrize("servicio", DUENOS)
@pytest.mark.parametrize("esquema_ajeno", ESQUEMAS)
def test_cada_servicio_solo_lee_su_propio_esquema(db, servicio, esquema_ajeno):
    propio = DUENOS[servicio][0]
    db.execute(f"SET LOCAL ROLE sigro_svc_{servicio}")
    if esquema_ajeno == propio:
        with db.transaction():
            db.execute(f"SELECT count(*) FROM {ESQUEMAS[esquema_ajeno]}")
    else:
        with rechaza(db, SIN_PERMISO):
            db.execute(f"SELECT count(*) FROM {ESQUEMAS[esquema_ajeno]}")


def test_publicar_un_evento_no_permite_leer_los_eventos_de_otros(db):
    db.execute("SET LOCAL ROLE sigro_svc_compras")
    with rechaza(db, SIN_PERMISO):
        db.execute("SELECT payload FROM outbox.evento")


# ---------------------------------------------------------------- 2. código propio
def _imports_de(carpeta: Path) -> set[str]:
    salida = set()
    for archivo in carpeta.rglob("*.py"):
        for nodo in ast.walk(ast.parse(archivo.read_text(encoding="utf-8"))):
            if isinstance(nodo, ast.ImportFrom) and nodo.module:
                salida.add(nodo.module)
            elif isinstance(nodo, ast.Import):
                salida.update(a.name for a in nodo.names)
    return salida


@pytest.mark.parametrize("servicio", DUENOS)
def test_el_codigo_de_un_servicio_no_importa_el_de_otro(servicio):
    propios = DUENOS[servicio][1]
    ajenos = {m for s, (_, mods) in DUENOS.items() if s != servicio for m in mods}
    for modulo in propios:
        usados = _imports_de(APP / "modulos" / modulo)
        prohibidos = {u for u in usados for a in ajenos if u.startswith(f"app.modulos.{a}")}
        assert not prohibidos, f"{modulo} ({servicio}) importa código de otro servicio: {sorted(prohibidos)}"


# ---------------------------------------------------------------- 3 y 4. una sola puerta, API interna cerrada
def _rutas_publicas():
    for nombre, fabrica in fabricas().items():
        app = fabrica()
        for incl in app.routes:
            router = getattr(incl, "original_router", None)
            prefijo = incl.include_context.prefix if hasattr(incl, "include_context") else ""
            if router is None or not prefijo.startswith("/api/v1"):
                continue
            for r in router.routes:
                yield nombre, prefijo + r.path


def test_toda_ruta_publica_esta_en_el_gateway_y_va_a_su_dueno():
    rutas = list(_rutas_publicas())
    assert len(rutas) >= 40
    for dueno, ruta in rutas:
        concreta = ruta.replace("{obra_id}", "7")
        import re
        concreta = re.sub(r"\{[^}]+\}", "1", concreta)
        assert servicio_para(concreta) == dueno, f"{ruta} es de {dueno} pero el gateway la manda a {servicio_para(concreta)}"


def test_el_gateway_no_expone_la_api_interna(mundo):
    r = mundo.http.post("/interno/autorizar", json={"usuario_id": 1})
    assert r.status_code == 404
    r = mundo.http.post("/api/v1/interno/autorizar", json={"usuario_id": 1})
    assert r.status_code == 404


def test_la_api_interna_exige_el_token_de_servicio(monkeypatch):
    from fastapi.testclient import TestClient
    monkeypatch.setenv("SIGRO_SERVICIO_TOKEN", "t" * 32)
    obras = TestClient(fabricas()["obras"](), raise_server_exceptions=False)
    sin = obras.post("/interno/autorizar", json={"usuario_id": 1})
    malo = obras.post("/interno/autorizar", json={"usuario_id": 1}, headers={"X-Servicio-Token": "x" * 32})
    assert sin.status_code == 401 and malo.status_code == 401
    assert sin.json()["codigo"] == "servicio_no_autorizado"


# ---------------------------------------------------------------- comunicación real entre servicios
@pytest.fixture
def llamadas(monkeypatch):
    """Anota cada llamada interna: (servicio destino, ruta)."""
    registro: list[tuple[str, str]] = []
    original = internos.llamar

    def espia(servicio, metodo, ruta, **kw):
        registro.append((servicio, ruta))
        return original(servicio, metodo, ruta, **kw)

    monkeypatch.setattr(internos, "llamar", espia)
    return registro


def test_obras_responde_con_su_propia_base_sin_llamar_a_nadie(mundo, llamadas):
    adm = mundo.f.usuario()
    obra = mundo.f.proyecto(por=adm)
    mundo.f.asignar(adm, obra, "administrador")
    assert mundo.http.get("/api/v1/obras", headers=mundo.con(adm)).status_code == 200
    assert llamadas == []


def test_compras_pregunta_a_obras_quien_puede_y_al_catalogo_por_los_insumos(mundo, llamadas):
    res = mundo.f.usuario()
    obra = mundo.f.proyecto(por=res)
    mundo.f.asignar(res, obra, "residente")
    r = mundo.http.get(f"/api/v1/obras/{obra}/requerimiento", headers=mundo.con(res))
    assert r.status_code == 200, r.text
    assert ("obras", "/interno/autorizar") in llamadas
    assert all(s in ("obras", "catalogo") for s, _ in llamadas)


def test_si_cae_el_catalogo_solo_falla_lo_que_lo_necesita(mundo, monkeypatch):
    res = mundo.f.usuario()
    obra = mundo.f.proyecto(por=res)
    mundo.f.asignar(res, obra, "residente")
    monkeypatch.delenv("SIGRO_URL_CATALOGO", raising=False)
    cliente = internos._clientes_prueba.pop("catalogo")
    try:
        r = mundo.http.post(f"/api/v1/obras/{obra}/parametros-reposicion", headers=mundo.con(res),
                            json={"insumo_codigo": "X-1", "stock_seguridad": 1, "punto_reposicion": 2})
        assert r.status_code == 503 and r.json()["codigo"] == "servicio_no_configurado"
        assert mundo.http.get(f"/api/v1/obras/{obra}", headers=mundo.con(res)).status_code == 200     # obras sigue
        assert mundo.http.get(f"/api/v1/obras/{obra}/trabajadores", headers=mundo.con(res)).status_code == 200  # tareo sigue
    finally:
        internos.registrar_cliente_de_prueba("catalogo", cliente)


def test_una_baja_corta_el_acceso_en_todos_los_servicios_al_instante(mundo):
    res = mundo.f.usuario()
    obra = mundo.f.proyecto(por=res)
    mundo.f.asignar(res, obra, "residente")
    h = mundo.con(res)
    assert mundo.http.get("/api/v1/proveedores", headers=h).status_code == 200
    mundo.conn.exec_driver_sql("UPDATE obra.usuario SET estado = 'baja', fecha_baja = now() WHERE id = %s", (res,))
    for ruta in ("/api/v1/proveedores", f"/api/v1/obras/{obra}/trabajadores", "/api/v1/catalogo/insumos", f"/api/v1/obras/{obra}"):
        assert mundo.http.get(ruta, headers=h).status_code == 401, ruta


# ---------------------------------------------------------------- 5. el gateway de producción
def _servicio_eco(nombre: str):
    from starlette.applications import Starlette
    from starlette.responses import JSONResponse
    from starlette.routing import Route

    async def eco(request):
        return JSONResponse({"servicio": nombre, "ruta": request.url.path, "metodo": request.method,
                             "auth": request.headers.get("authorization"), "cuerpo": (await request.body()).decode()},
                            status_code=201 if request.method == "POST" else 200)
    return Starlette(routes=[Route("/{r:path}", eco, methods=["GET", "POST"])])


def test_el_gateway_reenvia_al_dueno_con_cuerpo_y_cabeceras_y_aisla_caidas(monkeypatch):
    from fastapi.testclient import TestClient
    from app.servicios import gateway

    for s in ("obras", "catalogo", "compras", "tareo"):
        monkeypatch.setenv(f"SIGRO_URL_{s.upper()}", f"http://{s}.interno")
    vivos = {s: _servicio_eco(s) for s in ("obras", "catalogo", "compras")}       # tareo «caído»

    class Enrutador(httpx.AsyncBaseTransport):
        async def handle_async_request(self, request):
            destino = request.url.host.split(".")[0]
            if destino not in vivos:
                raise httpx.ConnectError("caído")
            return await httpx.ASGITransport(app=vivos[destino]).handle_async_request(request)

    monkeypatch.setattr(gateway, "_cliente", httpx.AsyncClient(transport=Enrutador()))
    c = TestClient(gateway.crear_app())
    r = c.post("/api/v1/obras/3/compras", json={"a": 1}, headers={"Authorization": "Bearer xyz"})
    assert r.status_code == 201 and r.json()["servicio"] == "compras"
    assert r.json()["auth"] == "Bearer xyz" and r.json()["cuerpo"] == '{"a":1}'
    assert c.get("/api/v1/catalogo/insumos").json()["servicio"] == "catalogo"
    assert c.get("/api/v1/obras/3").json()["servicio"] == "obras"
    caido = c.get("/api/v1/obras/3/trabajadores")
    assert caido.status_code == 503 and caido.json()["codigo"] == "servicio_no_disponible"
    assert c.get("/api/v1/interno/autorizar").status_code == 404
    assert c.get("/salud").json() == {"estado": "ok", "servicio": "gateway"}


# ---------------------------------------------------------------- la imagen de cada servicio arranca solo con su código
@pytest.mark.parametrize("servicio", ["obras", "catalogo", "compras", "tareo", "gateway"])
def test_cada_imagen_arranca_solo_con_su_codigo(servicio, tmp_path):
    """Simula el Dockerfile: copia el código, poda lo ajeno e importa la app. Si dependiera de otro servicio, falla."""
    import shutil
    import subprocess
    import sys

    raiz = APP.parent
    destino = tmp_path / "img"
    shutil.copytree(raiz / "app", destino / "app", ignore=shutil.ignore_patterns("__pycache__"))
    from app.servicios.podar import podar
    quitados = podar(servicio, destino)
    assert quitados or servicio == "gateway"
    r = subprocess.run([sys.executable, "-c", f"import app.servicios.{servicio} as m; print(type(m.app).__name__)"],
                       cwd=destino, capture_output=True, text=True, env={"PATH": "/usr/bin:/bin", "SIGRO_ENTORNO": "dev"})
    assert r.returncode == 0, r.stderr[-800:]
    restantes = {p.name for p in (destino / "app" / "modulos").iterdir() if p.is_dir() and p.name != "__pycache__"}
    from app.servicios.podar import MODULOS
    assert restantes == MODULOS[servicio]
