"""Gateway: la única puerta de entrada de la web y de la app (/api/v1).

- Enruta cada petición al microservicio dueño (tabla en app.enrutado) y le pasa el cuerpo y las cabeceras.
- NUNCA expone las rutas /interno de los servicios: esas son solo entre servicios.
- No conoce la base de datos ni el negocio: si un servicio cae, solo las rutas de ese servicio responden 503.

    SIGRO_URL_OBRAS=http://obras:8000 SIGRO_URL_CATALOGO=... uvicorn app.servicios.gateway:app
"""
from __future__ import annotations

import asyncio
import os
import time

import httpx
from starlette.applications import Starlette
from starlette.middleware import Middleware
from starlette.middleware.cors import CORSMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response
from starlette.routing import Route

from app import medicion
from app.enrutado import servicio_para

_SALTO = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers",
          "transfer-encoding", "upgrade", "host", "content-length"}
_cliente: httpx.AsyncClient | None = None


def _error(estado: int, codigo: str, mensaje: str) -> JSONResponse:
    return JSONResponse({"codigo": codigo, "mensaje": mensaje}, status_code=estado)   # mismo formato que los servicios


async def _reenviar(request: Request) -> Response:
    global _cliente
    servicio = servicio_para(request.url.path)
    if servicio is None:
        return _error(404, "no_existe", "Esa ruta no existe.")
    base = os.environ.get(f"SIGRO_URL_{servicio.upper()}")
    if not base:
        return _error(503, "servicio_no_configurado", f"El gateway no conoce la dirección del servicio {servicio}.")
    if _cliente is None:
        _cliente = httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=3.0))
    cabeceras = {k: v for k, v in request.headers.items() if k.lower() not in _SALTO | {"accept-encoding"}}
    cabeceras["x-forwarded-for"] = request.client.host if request.client else ""
    # Se pide solo gzip, que httpx sabe descomprimir: si se reenviara el «br» del navegador, Cloudflare (Render)
    # respondería en brotli, httpx lo dejaría comprimido y, al quitar Content-Encoding, el navegador recibiría basura.
    cabeceras["accept-encoding"] = "gzip"
    cabeceras.pop(medicion.CABECERA_RAY, None)
    if ray := medicion.ray_actual():
        cabeceras[medicion.CABECERA_RAY] = ray     # Cloudflare marca el salto al servicio con otro ray: este los une
    cuerpo = await request.body()
    inicio = time.perf_counter()
    try:
        r = await _cliente.request(
            request.method, f"{base.rstrip('/')}{request.url.path}", params=request.query_params,
            content=cuerpo, headers=cabeceras,
        )
    except httpx.HTTPError:
        return _error(503, "servicio_no_disponible", f"El servicio de {servicio} no responde; inténtalo en un momento.")
    finally:
        medicion.sumar_interno((time.perf_counter() - inicio) * 1000)
    salida = {k: v for k, v in r.headers.items() if k.lower() not in _SALTO | {"content-encoding"}}
    return Response(content=r.content, status_code=r.status_code, headers=salida)


async def salud(_: Request) -> JSONResponse:
    return JSONResponse({"estado": "ok", "servicio": "gateway"})


# ── /salud/servicios: una sola llamada que despierta a todos ─────────────────────────────────────────────────
# En el plan gratuito de Render cada servicio se duerme por separado y tarda 20-30 s en despertar. La web llama
# aquí al abrirse: el gateway pide el /salud de todos A LA VEZ, así se despiertan en paralelo y no en cadena.
# Siempre se consultan los cinco de base; almacén y costo, solo si el gateway conoce su dirección.
_SIEMPRE = ("obras", "catalogo", "compras", "tareo", "integraciones")
_SI_ESTAN = ("almacen", "costo")


def _url_de(servicio: str) -> str | None:
    if servicio == "integraciones":     # integraciones usa su nombre histórico de variable (render.yaml, sigro-comun)
        return os.environ.get("SIGRO_URL_INTEGRACIONES") or os.environ.get("SIGRO_INTEGRACIONES_URL") or None
    return os.environ.get(f"SIGRO_URL_{servicio.upper()}") or None


async def _salud_de(servicio: str, base: str | None, espera: float) -> dict:
    global _cliente
    if not base:
        return {"servicio": servicio, "listo": False, "estado": "no_configurado", "ms": 0}
    if _cliente is None:
        _cliente = httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=3.0))
    inicio = time.perf_counter()
    try:
        # wait_for además del timeout de httpx: el tope vale aunque el servidor acepte la conexión y no conteste
        r = await asyncio.wait_for(_cliente.get(f"{base.rstrip('/')}/salud", timeout=espera), espera)
        estado = "ok" if r.status_code == 200 else f"error_{r.status_code}"
    except (httpx.HTTPError, asyncio.TimeoutError):
        estado = "no_responde"                    # todavía despertando, o caído: la web vuelve a preguntar
    return {"servicio": servicio, "listo": estado == "ok", "estado": estado,
            "ms": round((time.perf_counter() - inicio) * 1000)}


async def salud_servicios(_: Request) -> JSONResponse:
    espera = float(os.environ.get("SIGRO_SALUD_ESPERA", "8"))   # corto: la web repite cada 2 s, no se cuelga aquí
    nombres = list(_SIEMPRE) + [s for s in _SI_ESTAN if _url_de(s)]
    servicios = await asyncio.gather(*(_salud_de(s, _url_de(s), espera) for s in nombres))
    listo = all(s["listo"] for s in servicios)
    return JSONResponse({"listo": listo, "servicios": list(servicios)}, status_code=200 if listo else 503,
                        headers={"Cache-Control": "no-store"})


def crear_app() -> Starlette:
    origenes = [o for o in os.environ.get("SIGRO_CORS", "http://localhost:5173").split(",") if o]
    return Starlette(
        routes=[
            Route("/salud", salud, methods=["GET"]),
            Route("/salud/servicios", salud_servicios, methods=["GET"]),
            Route("/api/v1/{resto:path}", _reenviar, methods=["GET", "POST", "PUT", "PATCH", "DELETE"]),
        ],
        middleware=[Middleware(medicion.MedirPeticion, nombre=medicion.GATEWAY),     # el primero es el más externo
                    Middleware(CORSMiddleware, allow_origins=origenes, allow_methods=["GET", "POST"],
                               allow_headers=["Authorization", "Content-Type"])],
    )


app = crear_app()
