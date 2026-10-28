"""Módulo tareo: trabajadores y jornadas (HU-62 registrar trabajador, asistencia y horas, HU-64 horas acumuladas).

La jornada es un libro (R1): no se edita ni se borra; corregir inserta otra que apunta a la anterior.
La categoría y el periodo los decide el servidor, no el cliente.
"""
from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy import text

from app.dependencias import Actor, exigir
from app.errores import ErrorSigro
from app import contexto, internos
from app.sql import fila, filas
from app.modulos.tareo import esquemas as e

router = APIRouter(prefix="/obras/{obra_id}", tags=["tareo"])

_COLS_JORNADA = (
    "id, uuid_cliente, trabajador_id, fecha, estado, horas_normales, horas_extra, categoria, periodo_id, "
    "registrado_por, fecha_servidor, corrige_a_id, motivo_correccion"
)


@router.post("/trabajadores", response_model=e.TrabajadorOut, status_code=201)
def registrar_trabajador(datos: e.TrabajadorIn, a: Actor = Depends(exigir("trabajador.registrar"))):
    return fila(
        a.sesion,
        "INSERT INTO tareo.trabajador (proyecto_id, nombre, tipo_documento, documento, categoria, cuadrilla, "
        "fecha_ingreso, registrado_por) VALUES (:p, :n, :td, :d, :c, :q, :fi, :u) "
        "RETURNING id, nombre, tipo_documento, documento, categoria, cuadrilla, fecha_ingreso, fecha_salida",
        p=a.obra_id, n=datos.nombre, td=datos.tipo_documento, d=datos.documento, c=datos.categoria,
        q=datos.cuadrilla, fi=datos.fecha_ingreso, u=a.usuario_id,
    )


@router.get("/trabajadores", response_model=list[e.TrabajadorOut])
def listar_trabajadores(solo_activos: bool = Query(False), a: Actor = Depends(exigir("trabajador.consultar"))):
    return filas(
        a.sesion,
        "SELECT id, nombre, tipo_documento, documento, categoria, cuadrilla, fecha_ingreso, fecha_salida "
        "FROM tareo.trabajador WHERE proyecto_id = :p AND (NOT :s OR fecha_salida IS NULL OR fecha_salida >= current_date) "
        "ORDER BY cuadrilla, nombre",
        p=a.obra_id, s=solo_activos,
    )


@router.post("/trabajadores/{trabajador_id}/baja", response_model=e.TrabajadorOut)
def dar_de_baja(trabajador_id: int, datos: e.BajaIn, a: Actor = Depends(exigir("trabajador.registrar"))):
    """R10: la baja es una fecha de salida; sus jornadas se conservan."""
    if fila(a.sesion, "SELECT 1 AS x FROM tareo.trabajador WHERE id = :i AND proyecto_id = :p", i=trabajador_id, p=a.obra_id) is None:
        raise ErrorSigro(404, "no_existe", "Ese trabajador no existe en esta obra.")
    return fila(
        a.sesion,
        "UPDATE tareo.trabajador SET fecha_salida = :f WHERE id = :i AND proyecto_id = :p "
        "RETURNING id, nombre, tipo_documento, documento, categoria, cuadrilla, fecha_ingreso, fecha_salida",
        f=datos.fecha_salida, i=trabajador_id, p=a.obra_id,
    )


@router.post("/jornadas", response_model=e.JornadaOut, status_code=201)
def registrar_jornada(datos: e.JornadaIn, respuesta: Response, a: Actor = Depends(exigir("jornada.registrar"))):
    """Un reenvío con el mismo `uuid_cliente` no crea nada y devuelve el original (R3)."""
    if datos.uuid_cliente is not None:
        previa = fila(
            a.sesion, f"SELECT {_COLS_JORNADA}, proyecto_id FROM tareo.jornada WHERE uuid_cliente = CAST(:u AS uuid)", u=str(datos.uuid_cliente)
        )
        if previa is not None:
            if previa.pop("proyecto_id") != a.obra_id or previa["trabajador_id"] != datos.trabajador_id or previa["fecha"] != datos.fecha:
                raise ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otra jornada.")
            respuesta.status_code = 200      # reenvío: no se crea nada, se devuelve el original
            return previa
    return fila(
        a.sesion,
        "INSERT INTO tareo.jornada (uuid_cliente, proyecto_id, trabajador_id, fecha, estado, horas_normales, horas_extra, "
        "registrado_por, categoria, periodo_id) VALUES (CAST(:uu AS uuid), :p, :t, :f, :es, :hn, :he, :u, 'peon', 0) "
        f"RETURNING {_COLS_JORNADA}",
        uu=str(datos.uuid_cliente) if datos.uuid_cliente else None, p=a.obra_id, t=datos.trabajador_id, f=datos.fecha,
        es=datos.estado, hn=datos.horas_normales, he=datos.horas_extra, u=a.usuario_id,
    )


@router.post("/jornadas/{jornada_id}/correccion", response_model=e.JornadaOut, status_code=201)
def corregir_jornada(jornada_id: int, datos: e.CorreccionIn, a: Actor = Depends(exigir("jornada.registrar"))):
    """Corregir es insertar otra jornada que apunta a la vigente, con su motivo. La original no se toca."""
    orig = fila(
        a.sesion,
        "SELECT id, trabajador_id, fecha FROM tareo.jornada WHERE id = :i AND proyecto_id = :p",
        i=jornada_id, p=a.obra_id,
    )
    if orig is None:
        raise ErrorSigro(404, "no_existe", "Esa jornada no existe en esta obra.")
    if fila(a.sesion, "SELECT 1 AS x FROM tareo.jornada WHERE corrige_a_id = :i", i=jornada_id):
        raise ErrorSigro(409, "ya_corregida", "Esa jornada ya fue corregida; corrige la versión vigente.")
    return fila(
        a.sesion,
        "INSERT INTO tareo.jornada (proyecto_id, trabajador_id, fecha, estado, horas_normales, horas_extra, "
        "registrado_por, categoria, periodo_id, corrige_a_id, motivo_correccion) "
        "VALUES (:p, :t, :f, :es, :hn, :he, :u, 'peon', 0, :c, :m) "
        f"RETURNING {_COLS_JORNADA}",
        p=a.obra_id, t=orig["trabajador_id"], f=orig["fecha"], es=datos.estado, hn=datos.horas_normales,
        he=datos.horas_extra, u=a.usuario_id, c=jornada_id, m=datos.motivo,
    )


@router.get("/jornadas", response_model=list[e.JornadaOut])
def listar_jornadas(
    fecha: date | None = Query(None),
    trabajador_id: int | None = Query(None, ge=1),
    a: Actor = Depends(exigir("trabajador.consultar")),
):
    """Solo la jornada vigente de cada día (la última de su cadena de correcciones)."""
    return filas(
        a.sesion,
        f"SELECT {_COLS_JORNADA} FROM tareo.jornada_vigente WHERE proyecto_id = :p "
        "AND (CAST(:f AS date) IS NULL OR fecha = :f) AND (CAST(:t AS bigint) IS NULL OR trabajador_id = :t) "
        "ORDER BY fecha DESC, trabajador_id",
        p=a.obra_id, f=fecha, t=trabajador_id,
    )


@router.get("/horas-acumuladas", response_model=list[e.HorasOut])
def horas_acumuladas(periodo_id: int | None = Query(None, ge=1), a: Actor = Depends(exigir("horas.consultar"))):
    rows = filas(
        a.sesion,
        "SELECT h.periodo_id, h.trabajador_id, t.nombre AS trabajador, h.cuadrilla, h.categoria, "
        "h.dias_presente, h.dias_ausente, h.dias_permiso, h.horas_normales, h.horas_extra "
        "FROM tareo.horas_acumuladas h JOIN tareo.trabajador t ON t.id = h.trabajador_id "
        "WHERE h.proyecto_id = :p AND (CAST(:pe AS bigint) IS NULL OR h.periodo_id = :pe)",
        p=a.obra_id, pe=periodo_id,
    )
    if not rows:
        return []
    # El número de cada periodo es dato del servicio obras: se pide una vez por consulta.
    numero = {p["id"]: p["numero"] for p in internos.json_de(contexto.OBRAS, "GET", f"/interno/obras/{a.obra_id}/periodos")}
    for r in rows:
        r["periodo_numero"] = numero.get(r["periodo_id"])
    return sorted(rows, key=lambda r: (r["periodo_numero"] or 0, r["cuadrilla"], r["trabajador"]))


routers = [router]
