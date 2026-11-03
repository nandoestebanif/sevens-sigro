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


def _segundos_de(retry_after: str | None) -> float:
    try:
        return max(0.0, float(retry_after)) if retry_after else 0.0
    except ValueError:                            # Retry-After también puede venir como fecha: se ignora
        return 0.0


async def _salud_de(servicio: str, base: str | None, espera: float) -> dict:
    global _cliente
    if not base:
        return {"servicio": servicio, "listo": False, "estado": "no_configurado", "ms": 0}
    if _cliente is None:
        _cliente = httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=3.0))
    inicio = time.perf_counter()
    pausa = 0.0
    try:
        # wait_for además del timeout de httpx: el tope vale aunque el servidor acepte la conexión y no conteste
        r = await asyncio.wait_for(_cliente.get(f"{base.rstrip('/')}/salud", timeout=espera), espera)
        if r.status_code == 429:
            # Cloudflare (o el servicio) pide frenar: se informa «ocupado» y NO se reintenta; el resultado queda en
            # memoria al menos lo que diga Retry-After (ver _guardar_salud)
            estado, pausa = "ocupado", _segundos_de(r.headers.get("retry-after"))
        else:
            estado = "ok" if r.status_code == 200 else f"error_{r.status_code}"
    except (httpx.HTTPError, asyncio.TimeoutError):
        estado = "no_responde"                    # todavía despertando, o caído: la web vuelve a preguntar
    datos = {"servicio": servicio, "listo": estado == "ok", "estado": estado,
             "ms": round((time.perf_counter() - inicio) * 1000)}
    if pausa:
        datos["reintentar_en_s"] = round(pausa)
    return datos


# El resultado se guarda en memoria SIGRO_SALUD_CACHE segundos (30 por defecto) y nunca hay dos consultas a la vez:
# si una está en curso, las demás peticiones esperan ese mismo resultado. Así, por muchas pestañas o sondeos que
# lleguen, el gateway hace como mucho una ronda (7 llamadas) cada 30 s. Sin esto, Cloudflare respondía 429 a todo
# el tráfico del gateway hacia los servicios, no solo a /salud.
_salud_guardada: tuple[float, dict] | None = None     # (vence_en, cuerpo)
_salud_en_curso: asyncio.Future | None = None
_ahora = time.monotonic                               # las pruebas lo reemplazan para adelantar el reloj
_PAUSA_MAXIMA_S = 300.0                               # un Retry-After exagerado no deja la salud congelada


def _olvidar_salud() -> None:
    global _salud_guardada, _salud_en_curso
    _salud_guardada, _salud_en_curso = None, None


async def _consultar_todos() -> dict:
    espera = float(os.environ.get("SIGRO_SALUD_ESPERA", "8"))   # corto: la web vuelve a preguntar, no se cuelga aquí
    nombres = list(_SIEMPRE) + [s for s in _SI_ESTAN if _url_de(s)]
    servicios = await asyncio.gather(*(_salud_de(s, _url_de(s), espera) for s in nombres))
    return {"listo": all(s["listo"] for s in servicios), "servicios": list(servicios)}


def _guardar_salud(cuerpo: dict) -> None:
    global _salud_guardada
    vigencia = float(os.environ.get("SIGRO_SALUD_CACHE", "30"))
    pausa = max((s.get("reintentar_en_s", 0) for s in cuerpo["servicios"]), default=0)
    _salud_guardada = (_ahora() + max(vigencia, min(float(pausa), _PAUSA_MAXIMA_S)), cuerpo)


async def _ronda() -> dict:
    cuerpo = await _consultar_todos()
    _guardar_salud(cuerpo)
    return cuerpo


def _al_terminar_ronda(tarea: asyncio.Task) -> None:
    global _salud_en_curso
    if _salud_en_curso is tarea:
        _salud_en_curso = None
    if not tarea.cancelled():
        tarea.exception()                                   # marcada como vista: sin «never retrieved» en el log


async def _salud_compartida() -> dict:
    global _salud_en_curso
    if _salud_guardada and _ahora() < _salud_guardada[0]:
        return _salud_guardada[1]
    tarea = _salud_en_curso
    if tarea is None or tarea.done() or tarea.get_loop() is not asyncio.get_running_loop():
        # la ronda es una tarea aparte: si quien la pidió se desconecta, los que esperan igual reciben el resultado
        tarea = _salud_en_curso = asyncio.ensure_future(_ronda())
        tarea.add_done_callback(_al_terminar_ronda)
    return await asyncio.shield(tarea)                      # si otra petición ya está consultando, se espera esa


async def salud_servicios(_: Request) -> JSONResponse:
    cuerpo = await _salud_compartida()
    return JSONResponse(cuerpo, status_code=200 if cuerpo["listo"] else 503, headers={"Cache-Control": "no-store"})


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
