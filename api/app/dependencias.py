"""Control de acceso en el servidor (regla 9): rol y alcance se verifican en cada petición.

Que la pantalla esconda un botón no es control de acceso.

Microservicios: quién es cada persona y qué rol tiene en cada obra es dato del servicio **obras**
(identidad y accesos). Ese servicio lo lee de su base; los demás (catálogo, compras, tareo) se lo
preguntan por su API interna (`POST /interno/autorizar`) en cada petición. Así una baja o un retiro de
la obra corta el acceso al instante en TODOS los servicios, y ninguno lee la base de otro. Cada endpoint declara la acción que
exige con `exigir("accion")` (alcance = una obra, tomada de la ruta) o `exigir_en_alguna_obra`
(acciones que no pertenecen a una obra, como crear un usuario). Sin asignación vigente en esa
obra, o con un rol que no la admite, la respuesta es 403, exista o no la obra.
"""
from __future__ import annotations

from dataclasses import dataclass

from fastapi import Depends, Path
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import text
from sqlalchemy.orm import Session

from app import contexto, internos
from app.db import obtener_sesion
from app.errores import ErrorSigro
from app.permisos import ACCIONES
from app.seguridad import leer_token

_bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class Actor:
    usuario_id: int
    nombre: str
    sesion: Session
    obra_id: int | None = None
    rol: str | None = None


def _no_autenticado() -> ErrorSigro:
    return ErrorSigro(401, "no_autenticado", "Inicia sesión para continuar.")


def _sin_permiso() -> ErrorSigro:
    return ErrorSigro(403, "sin_permiso", "No tienes permiso para esta operación en esta obra.")


def _autorizar_remoto(usuario_id: int, obra_id: int | None = None, roles: frozenset[str] | None = None) -> dict:
    """Pregunta al servicio obras quién es y qué rol tiene. 401 / 403 llegan tal cual desde allá."""
    cuerpo = {"usuario_id": usuario_id, "obra_id": obra_id, "roles": sorted(roles) if roles is not None else None}
    return internos.json_de(contexto.OBRAS, "POST", "/interno/autorizar", json=cuerpo)


def usuario_autenticado(
    cred: HTTPAuthorizationCredentials | None = Depends(_bearer),
    s: Session = Depends(obtener_sesion, scope="function"),
) -> Actor:
    """El token identifica; el dueño de la identidad decide si esa persona sigue activa (una baja corta el acceso al instante)."""
    if cred is None:
        raise _no_autenticado()
    uid = leer_token(cred.credentials)
    if uid is None:
        raise _no_autenticado()
    if not contexto.es(contexto.OBRAS):
        r = _autorizar_remoto(uid)
        return Actor(usuario_id=r["usuario_id"], nombre=r["nombre"], sesion=s)
    fila = s.execute(text("SELECT id, nombre, estado FROM obra.usuario WHERE id = :i"), {"i": uid}).mappings().first()
    if fila is None or fila["estado"] != "activo":
        raise _no_autenticado()
    return Actor(usuario_id=fila["id"], nombre=fila["nombre"], sesion=s)


usuario_autenticado.accion = "autenticado"  # type: ignore[attr-defined]


def exigir(accion: str):
    """Dependencia para rutas con `{obra_id}`: rol vigente del usuario EN ESA obra debe admitir la acción."""
    roles = ACCIONES[accion]

    def dependencia(obra_id: int = Path(..., ge=1), actor: Actor = Depends(usuario_autenticado)) -> Actor:
        if not contexto.es(contexto.OBRAS):
            r = _autorizar_remoto(actor.usuario_id, obra_id, roles)
            return Actor(actor.usuario_id, actor.nombre, actor.sesion, obra_id, r["rol"])
        rol = rol_en_obra(actor.sesion, actor.usuario_id, obra_id)
        if rol is None or rol not in roles:
            raise _sin_permiso()
        return Actor(actor.usuario_id, actor.nombre, actor.sesion, obra_id, rol)

    dependencia.accion = accion  # type: ignore[attr-defined]
    return dependencia


def exigir_en_alguna_obra(accion: str):
    """Para acciones sin obra en la ruta: el usuario debe tener, en alguna obra, un rol que la admita."""
    roles = ACCIONES[accion]

    def dependencia(actor: Actor = Depends(usuario_autenticado)) -> Actor:
        if not contexto.es(contexto.OBRAS):
            r = _autorizar_remoto(actor.usuario_id, None, roles)
            return Actor(actor.usuario_id, actor.nombre, actor.sesion, r["obra_id"], r["rol"])
        fila = rol_en_alguna_obra(actor.sesion, actor.usuario_id, roles)
        if fila is None:
            raise _sin_permiso()
        return Actor(actor.usuario_id, actor.nombre, actor.sesion, fila[0], fila[1])

    dependencia.accion = accion  # type: ignore[attr-defined]
    return dependencia


# ---------------------------------------------------------------- consultas del dueño (solo servicio obras)
def rol_en_obra(s: Session, usuario_id: int, obra_id: int) -> str | None:
    return s.execute(
        text("SELECT rol FROM obra.asignacion WHERE usuario_id = :u AND proyecto_id = :p AND fecha_retiro IS NULL"),
        {"u": usuario_id, "p": obra_id},
    ).scalar()


def rol_en_alguna_obra(s: Session, usuario_id: int, roles: frozenset[str]):
    return s.execute(
        text(
            "SELECT proyecto_id, rol FROM obra.asignacion WHERE usuario_id = :u AND fecha_retiro IS NULL "
            "AND rol = ANY(:roles) ORDER BY (rol = 'administrador') DESC, proyecto_id LIMIT 1"
        ),
        {"u": usuario_id, "roles": sorted(roles)},
    ).first()
