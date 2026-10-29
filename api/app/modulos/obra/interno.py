"""API interna del servicio obras: lo que los demás microservicios le preguntan.

Obras es el dueño de la identidad y de los accesos (usuarios, asignaciones por obra, dispositivos).
Ningún otro servicio lee su base: preguntan aquí. Todas estas rutas exigen el token de servicio y el
gateway no las expone hacia afuera.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.db import obtener_sesion
from app.dependencias import rol_en_alguna_obra, rol_en_obra
from app.errores import ErrorSigro
from app.eventos import destinatarios_con_rol
from app.internos import exigir_servicio
from app.sql import fila, filas

interno = APIRouter(prefix="/interno", tags=["interno"], dependencies=[Depends(exigir_servicio)])


class AutorizarIn(BaseModel):
    usuario_id: int
    obra_id: int | None = None
    roles: list[str] | None = None     # None: solo comprobar que la persona sigue activa


class AutorizarOut(BaseModel):
    usuario_id: int
    nombre: str
    obra_id: int | None = None
    rol: str | None = None


@interno.post("/autorizar", response_model=AutorizarOut)
def autorizar(d: AutorizarIn, s: Session = Depends(obtener_sesion, scope="function")):
    """Regla 9 para los demás servicios: 401 si la persona no existe o está de baja; 403 si su rol vigente no alcanza."""
    u = fila(s, "SELECT id, nombre, estado FROM obra.usuario WHERE id = :i", i=d.usuario_id)
    if u is None or u["estado"] != "activo":
        raise ErrorSigro(401, "no_autenticado", "Inicia sesión para continuar.")
    if d.roles is None:
        return AutorizarOut(usuario_id=u["id"], nombre=u["nombre"])
    roles = frozenset(d.roles)
    sin_permiso = ErrorSigro(403, "sin_permiso", "No tienes permiso para esta operación en esta obra.")
    if d.obra_id is not None:
        rol = rol_en_obra(s, u["id"], d.obra_id)
        if rol is None or rol not in roles:
            raise sin_permiso
        return AutorizarOut(usuario_id=u["id"], nombre=u["nombre"], obra_id=d.obra_id, rol=rol)
    r = rol_en_alguna_obra(s, u["id"], roles)
    if r is None:
        raise sin_permiso
    return AutorizarOut(usuario_id=u["id"], nombre=u["nombre"], obra_id=r[0], rol=r[1])


@interno.get("/obras/{obra_id}")
def obra_resumen(obra_id: int, s: Session = Depends(obtener_sesion, scope="function")):
    o = fila(s, "SELECT id, nombre, fecha_inicio, fecha_fin, estado FROM obra.proyecto WHERE id = :i", i=obra_id)
    if o is None:
        raise ErrorSigro(404, "no_existe", "Esa obra no existe.")
    return o


@interno.get("/obras/{obra_id}/destinatarios", response_model=list[int])
def destinatarios(
    obra_id: int, roles: str = Query(..., min_length=1), excepto: int | None = Query(None),
    s: Session = Depends(obtener_sesion, scope="function"),
):
    """Usuarios activos con alguno de esos roles vigentes en la obra (a quién avisar de un evento)."""
    return destinatarios_con_rol(s, obra_id, [r for r in roles.split(",") if r], excepto=excepto)


@interno.get("/obras/{obra_id}/periodos")
def periodos(obra_id: int, s: Session = Depends(obtener_sesion, scope="function")):
    return filas(s, "SELECT id, numero, fecha_inicio, fecha_fin, estado FROM obra.periodo WHERE proyecto_id = :p ORDER BY numero",
                 p=obra_id)


routers = [interno]
