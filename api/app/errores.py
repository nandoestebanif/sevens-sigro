"""Errores con la forma que espera el cliente web: {codigo, mensaje} (ver ErrorApi en cliente.ts).

El motor de base de datos es quien sostiene las reglas; aquí solo se traduce su rechazo a algo
que una persona entiende, sin esconder el motivo real.
"""
from __future__ import annotations

import re

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.exc import DBAPIError


class ErrorSigro(HTTPException):
    def __init__(self, estado: int, codigo: str, mensaje: str):
        super().__init__(status_code=estado, detail={"codigo": codigo, "mensaje": mensaje})


# SQLSTATE -> (HTTP, codigo)
_SQLSTATE = {
    "23505": (409, "duplicado"),
    "23503": (422, "referencia_invalida"),
    "23514": (422, "regla_incumplida"),
    "23001": (409, "operacion_no_permitida"),
    "23P01": (409, "conflicto"),
    "23502": (422, "dato_obligatorio"),
    "22P02": (422, "formato_invalido"),
    "22003": (422, "valor_fuera_de_rango"),
    "22007": (422, "fecha_invalida"),
    "22008": (422, "fecha_invalida"),
}


# Restricciones de la base -> frase para una persona. El motor sostiene la regla; aquí solo se explica.
AMIGABLES = {
    "uq_dispositivo_activo_por_usuario": "Esa persona ya tiene un dispositivo autorizado en esta obra; revócalo antes de autorizar otro.",
    "uq_dispositivo_activo_identificador": "Ese dispositivo ya está autorizado en esta obra.",
    "uq_usuario_usuario": "Ese nombre de usuario ya existe.",
    "uq_proyecto_nombre": "Ya existe una obra con ese nombre.",
    "uq_almacen_proyecto_nombre": "Ya existe un almacén con ese nombre en esta obra.",
    "uq_asignacion_vigente": "Esa persona ya tiene una asignación vigente en esta obra; retírala antes de cambiar su rol.",
    "uq_trabajador_proyecto_id_documento": "Ya hay un trabajador con ese documento en esta obra.",
    "uq_jornada_original": "Ya existe una jornada de ese trabajador en esa fecha; corrígela en vez de registrar otra.",
    "uq_jornada_correccion_unica": "Esa jornada ya fue corregida; corrige la versión vigente.",
    "uq_proveedor_ruc": "Ya existe un proveedor con ese RUC.",
    "uq_compra_proveedor_id_tipo_comprobante_numero_comprobante": "Ese comprobante ya está registrado para este proveedor.",
    "uq_periodo_proyecto_id_numero": "Ese periodo ya existe.",
    "ex_periodo_sin_solape": "Los periodos de una obra no pueden solaparse.",
    "ck_usuario_usuario": "El usuario solo admite letras, números, punto, guion y guion bajo (3 a 40 caracteres).",
    "ck_proyecto_plazo": "La fecha de fin no puede ser anterior a la de inicio.",
    "ck_jornada_horas_segun_estado": "Un día presente necesita horas; un día ausente o con permiso no lleva horas.",
    "ck_jornada_horas_rango": "Las horas del día no pueden pasar de 24.",
    "ck_trabajador_documento": "El documento no tiene un formato válido (el DNI son 8 dígitos).",
    "ck_trabajador_salida": "La fecha de salida no puede ser anterior a la de ingreso.",
    "ck_compra_numero_comprobante": "El comprobante debe ser serie-número, por ejemplo F001-4512.",
    "ck_compra_detalle_cantidad": "La cantidad de cada línea debe ser mayor que cero.",
    "ck_proveedor_validado_ruc_correcto": "Solo se valida un RUC con dígito verificador correcto.",
    "fk_compra_solicitud_id_solicitud": "Esa solicitud no pertenece a esta obra.",
    "fk_compra_proveedor_id_proveedor": "Ese proveedor no existe.",
}
_GENERICO = {
    "23505": "Ya existe un registro con esos datos.",
    "23503": "Un dato hace referencia a algo que no existe en esta obra.",
    "23514": "Un dato no cumple una regla del sistema.",
    "23001": "Esa operación no está permitida: nada se borra ni se edita.",
    "23P01": "Ese registro choca con otro existente.",
    "23502": "Falta un dato obligatorio.",
}


def _mensaje(orig, restriccion: str | None, sqlstate: str | None) -> str:
    """Mensajes propios del motor («SIGRO-xx: …») salen tal cual; las restricciones conocidas, en castellano claro."""
    texto = getattr(getattr(orig, "diag", None), "message_primary", None) or str(orig)
    m = re.match(r"SIGRO[-\w]*:\s*(.*)", texto)
    if m:
        return m.group(1)
    if restriccion in AMIGABLES:
        return AMIGABLES[restriccion]
    return _GENERICO.get(sqlstate or "", "El dato recibido no es válido.")


def traducir(exc: DBAPIError) -> ErrorSigro:
    orig = exc.orig
    sqlstate = getattr(orig, "sqlstate", None)
    http, codigo = _SQLSTATE.get(sqlstate, (None, None))
    if http is None:
        # Un error que no sabemos explicar (p. ej. 42501 sin permiso) es un defecto nuestro: no se filtra al cliente.
        return ErrorSigro(500, "error_interno", "No se pudo completar la operación.")
    restriccion = getattr(getattr(orig, "diag", None), "constraint_name", None)
    return ErrorSigro(http, restriccion or codigo, _mensaje(orig, restriccion, sqlstate))


def instalar(app: FastAPI) -> None:
    @app.exception_handler(HTTPException)
    async def _http(_: Request, exc: HTTPException):
        d = exc.detail
        cuerpo = d if isinstance(d, dict) and "codigo" in d else {"codigo": f"http_{exc.status_code}", "mensaje": str(d)}
        return JSONResponse(cuerpo, status_code=exc.status_code, headers=getattr(exc, "headers", None))

    @app.exception_handler(DBAPIError)
    async def _db(_: Request, exc: DBAPIError):
        e = traducir(exc)
        return JSONResponse(e.detail, status_code=e.status_code)

    @app.exception_handler(RequestValidationError)
    async def _validacion(_: Request, exc: RequestValidationError):
        primero = exc.errors()[0] if exc.errors() else {}
        campo = ".".join(str(p) for p in primero.get("loc", ()) if p != "body")
        return JSONResponse(
            {"codigo": "datos_invalidos", "mensaje": f"{campo}: {primero.get('msg', 'dato inválido')}".strip(": ")},
            status_code=422,
        )
