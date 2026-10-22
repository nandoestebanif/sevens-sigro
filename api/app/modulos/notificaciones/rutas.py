"""Las notificaciones de CADA persona. La web llama aquí; la API pregunta al microservicio por el usuario del token.

Así nadie puede leer ni marcar las de otro, y la web nunca conoce el token de servicio. Si el microservicio no
responde, estas rutas contestan 503 y el resto del sistema sigue funcionando (los eventos esperan en el outbox).
"""
from __future__ import annotations

import httpx
from fastapi import APIRouter, Depends, Query, Response

from app.config import cargar
from app.dependencias import Actor, usuario_autenticado
from app.errores import ErrorSigro

router = APIRouter(prefix="/notificaciones", tags=["notificaciones"])
_TIMEOUT = httpx.Timeout(4.0, connect=2.0)


def _llamar(metodo: str, ruta: str, **params):
    cfg = cargar()
    if not cfg.notificaciones_url or not cfg.servicio_token:
        raise ErrorSigro(503, "notificaciones_no_disponible", "El servicio de notificaciones no está configurado.")
    try:
        with httpx.Client(timeout=_TIMEOUT) as c:
            return c.request(metodo, f"{cfg.notificaciones_url.rstrip('/')}{ruta}", params=params, headers={"X-Servicio-Token": cfg.servicio_token})
    except httpx.HTTPError:
        raise ErrorSigro(503, "notificaciones_no_disponible", "No se pudo consultar las notificaciones; inténtalo en un momento.")


@router.get("")
def mis_notificaciones(solo_no_leidas: bool = Query(False), limite: int = Query(50, ge=1, le=200), a: Actor = Depends(usuario_autenticado)):
    r = _llamar("GET", "/notificaciones", usuario_id=a.usuario_id, solo_no_leidas=str(solo_no_leidas).lower(), limite=limite)
    if r.status_code != 200:
        raise ErrorSigro(503, "notificaciones_no_disponible", "El servicio de notificaciones respondió con un error.")
    return r.json()


@router.post("/{notificacion_id}/leida", status_code=204)
def marcar_leida(notificacion_id: int, a: Actor = Depends(usuario_autenticado)):
    r = _llamar("POST", f"/notificaciones/{notificacion_id}/leida", usuario_id=a.usuario_id)
    if r.status_code == 404:
        raise ErrorSigro(404, "no_existe", "Esa notificación no existe.")
    if r.status_code != 204:
        raise ErrorSigro(503, "notificaciones_no_disponible", "El servicio de notificaciones respondió con un error.")
    return Response(status_code=204)


routers = [router]
