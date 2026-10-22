"""Control de acceso en el servidor (regla 9): rol y alcance se verifican contra la base, en cada petición.

Que la pantalla esconda un botón no es control de acceso. Cada endpoint declara la acción que
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


def usuario_autenticado(
    cred: HTTPAuthorizationCredentials | None = Depends(_bearer),
    s: Session = Depends(obtener_sesion, scope="function"),
) -> Actor:
    """El token identifica; la base decide si esa persona sigue activa (una baja corta el acceso al instante)."""
    if cred is None:
        raise _no_autenticado()
    uid = leer_token(cred.credentials)
    if uid is None:
        raise _no_autenticado()
    fila = s.execute(text("SELECT id, nombre, estado FROM obra.usuario WHERE id = :i"), {"i": uid}).mappings().first()
    if fila is None or fila["estado"] != "activo":
        raise _no_autenticado()
    return Actor(usuario_id=fila["id"], nombre=fila["nombre"], sesion=s)


usuario_autenticado.accion = "autenticado"  # type: ignore[attr-defined]


def exigir(accion: str):
    """Dependencia para rutas con `{obra_id}`: rol vigente del usuario EN ESA obra debe admitir la acción."""
    roles = ACCIONES[accion]

    def dependencia(obra_id: int = Path(..., ge=1), actor: Actor = Depends(usuario_autenticado)) -> Actor:
        rol = actor.sesion.execute(
            text("SELECT rol FROM obra.asignacion WHERE usuario_id = :u AND proyecto_id = :p AND fecha_retiro IS NULL"),
            {"u": actor.usuario_id, "p": obra_id},
        ).scalar()
        if rol is None or rol not in roles:
            raise _sin_permiso()
        return Actor(actor.usuario_id, actor.nombre, actor.sesion, obra_id, rol)

    dependencia.accion = accion  # type: ignore[attr-defined]
    return dependencia


def exigir_en_alguna_obra(accion: str):
    """Para acciones sin obra en la ruta: el usuario debe tener, en alguna obra, un rol que la admita."""
    roles = ACCIONES[accion]

    def dependencia(actor: Actor = Depends(usuario_autenticado)) -> Actor:
        fila = actor.sesion.execute(
            text(
                "SELECT proyecto_id, rol FROM obra.asignacion WHERE usuario_id = :u AND fecha_retiro IS NULL "
                "AND rol = ANY(:roles) ORDER BY (rol = 'administrador') DESC, proyecto_id LIMIT 1"
            ),
            {"u": actor.usuario_id, "roles": sorted(roles)},
        ).first()
        if fila is None:
            raise _sin_permiso()
        return Actor(actor.usuario_id, actor.nombre, actor.sesion, fila[0], fila[1])

    dependencia.accion = accion  # type: ignore[attr-defined]
    return dependencia
