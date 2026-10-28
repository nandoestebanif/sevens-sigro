"""Gateway: la única puerta de entrada de la web y de la app (/api/v1).

- Enruta cada petición al microservicio dueño (tabla en app.enrutado) y le pasa el cuerpo y las cabeceras.
- NUNCA expone las rutas /interno de los servicios: esas son solo entre servicios.
- No conoce la base de datos ni el negocio: si un servicio cae, solo las rutas de ese servicio responden 503.

    SIGRO_URL_OBRAS=http://obras:8000 SIGRO_URL_CATALOGO=... uvicorn app.servicios.gateway:app
"""
from __future__ import annotations

import os

import httpx
from starlette.applications import Starlette
from starlette.middleware import Middleware
from starlette.middleware.cors import CORSMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response
from starlette.routing import Route

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
    cabeceras = {k: v for k, v in request.headers.items() if k.lower() not in _SALTO}
    cabeceras["x-forwarded-for"] = request.client.host if request.client else ""
    try:
        r = await _cliente.request(
            request.method, f"{base.rstrip('/')}{request.url.path}", params=request.query_params,
            content=await request.body(), headers=cabeceras,
        )
    except httpx.HTTPError:
        return _error(503, "servicio_no_disponible", f"El servicio de {servicio} no responde; inténtalo en un momento.")
    salida = {k: v for k, v in r.headers.items() if k.lower() not in _SALTO | {"content-encoding"}}
    return Response(content=r.content, status_code=r.status_code, headers=salida)


async def salud(_: Request) -> JSONResponse:
    return JSONResponse({"estado": "ok", "servicio": "gateway"})


def crear_app() -> Starlette:
    origenes = [o for o in os.environ.get("SIGRO_CORS", "http://localhost:5173").split(",") if o]
    return Starlette(
        routes=[
            Route("/salud", salud, methods=["GET"]),
            Route("/api/v1/{resto:path}", _reenviar, methods=["GET", "POST", "PUT", "PATCH", "DELETE"]),
        ],
        middleware=[Middleware(CORSMiddleware, allow_origins=origenes, allow_methods=["GET", "POST"],
                               allow_headers=["Authorization", "Content-Type"])],
    )


app = crear_app()
