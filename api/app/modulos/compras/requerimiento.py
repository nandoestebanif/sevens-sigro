"""Requerimiento de la obra (HU-19), lo que falta comprar (HU-24) y parámetros de reposición (HU-25).

El requerimiento es lo planificado en el S10 para ESTA obra. Importarlo NO pisa lo ya cargado: una cantidad distinta se
avisa y se corrige con un ajuste que lleva motivo y autor. Todas las cantidades del requerimiento y del saldo están en la
unidad del S10; el stock de reposición, en unidad de compra. Factor de conversión = unidades del S10 por unidad de compra.
"""
from __future__ import annotations

from decimal import ROUND_HALF_UP, Decimal
from typing import Literal

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import text

from app.dependencias import Actor, exigir
from app.errores import ErrorSigro
from app.modulos.compras import catalogo_cliente
from app.sql import fila, filas

router = APIRouter(prefix="/obras/{obra_id}", tags=["compras · requerimiento y reposición"])
D4 = Decimal("0.0001")


class Estricto(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


# ------------------------------------------------------------------ contratos
class FilaRequerimiento(Estricto):
    insumo_codigo: str = Field(min_length=1, max_length=30)
    cantidad: Decimal = Field(gt=0, max_digits=18, decimal_places=4)
    precio: Decimal = Field(ge=0, max_digits=18, decimal_places=4)


class ImportarRequerimientoIn(Estricto):
    filas: list[FilaRequerimiento] = Field(min_length=1, max_length=5000)


class FallidoOut(BaseModel):
    insumo_codigo: str
    motivo: str


class ResultadoRequerimientoOut(BaseModel):
    creados: int
    sin_cambios: int
    fallidos: list[FallidoOut]
    advertencias: list[str]


class RequerimientoOut(BaseModel):
    id: int
    insumo_id: int
    insumo_codigo: str
    descripcion: str
    unidad_s10: str
    cantidad_planificada: Decimal
    precio_planificado: Decimal
    costo_planificado: Decimal


class AjusteIn(Estricto):
    cantidad: Decimal = Field(gt=0, max_digits=18, decimal_places=4)
    motivo: str = Field(min_length=3, max_length=300)


class AjusteOut(BaseModel):
    id: int
    cantidad_anterior: Decimal
    cantidad_nueva: Decimal
    motivo: str
    ajustado_por: int
    ajustado_en: str


class SaldoOut(BaseModel):
    insumo_id: int
    insumo_codigo: str
    descripcion: str
    unidad_s10: str
    unidad_compra: str
    requerido: Decimal
    comprado: Decimal
    saldo: Decimal
    saldo_en_unidad_compra: Decimal
    exceso: Decimal
    estado: Literal["pendiente", "completo", "excedido"]


class ParametroIn(Estricto):
    insumo_codigo: str = Field(min_length=1, max_length=30)
    stock_seguridad: Decimal = Field(ge=0, max_digits=18, decimal_places=4)
    punto_reposicion: Decimal = Field(ge=0, max_digits=18, decimal_places=4)

    @model_validator(mode="after")
    def _punto_sobre_el_seguridad(self):
        if self.punto_reposicion < self.stock_seguridad:
            raise ValueError("El punto de reposición no puede ser menor que el stock de seguridad.")
        return self


class ParametroOut(BaseModel):
    id: int
    insumo_id: int
    insumo_codigo: str
    descripcion: str
    unidad_compra: str
    stock_seguridad: Decimal
    punto_reposicion: Decimal
    registrado_por: int
    vigente_desde: str


# ------------------------------------------------- datos del insumo: son del servicio catálogo
def _con_insumo(rows: list[dict], campos: tuple[str, ...], q: str | None = None) -> list[dict]:
    """Agrega a cada fila los datos del insumo que pide el catálogo; filtra por código o descripción y ordena por código."""
    insumos = catalogo_cliente.por_id(r["insumo_id"] for r in rows)
    salida = []
    for r in rows:
        ins = insumos.get(r["insumo_id"])
        if ins is None:
            continue
        for c in campos:
            r["insumo_codigo" if c == "codigo" else c] = ins[c]
        if q and q.lower() not in ins["codigo"].lower() and q.lower() not in ins["descripcion"].lower():
            continue
        salida.append(r)
    return salida


def _id_por_codigo(codigo: str) -> int | None:
    ins = catalogo_cliente.por_codigo([codigo]).get(codigo.upper())
    return ins["id"] if ins and ins["codigo"] == codigo else None


# ------------------------------------------------------------------ requerimiento (HU-19)
_REQ = ("SELECT r.id, r.insumo_id, r.cantidad_planificada, "
        "r.precio_planificado, round(r.cantidad_planificada * r.precio_planificado, 2) AS costo_planificado "
        "FROM compras.requerimiento r ")
_CAMPOS_REQ = ("codigo", "descripcion", "unidad_s10")


@router.post("/requerimiento/importar", response_model=ResultadoRequerimientoOut)
def importar_requerimiento(d: ImportarRequerimientoIn, a: Actor = Depends(exigir("requerimiento.importar"))):
    """Carga lo planificado. Insumo que el catálogo no conoce → fallido con motivo (importa primero el catálogo).
    Insumo ya cargado con otra cantidad → se avisa y NO se pisa: eso es un ajuste con motivo."""
    creados = sin_cambios = 0
    fallidos: list[FallidoOut] = []
    avisos: list[str] = []
    vistos: set[str] = set()
    # Una sola consulta al catálogo por importación (no una por fila). El código debe coincidir exactamente.
    del_catalogo = catalogo_cliente.por_codigo(f.insumo_codigo for f in d.filas)
    conocidos = {f.insumo_codigo: del_catalogo[f.insumo_codigo.upper()]["id"] for f in d.filas
                 if f.insumo_codigo.upper() in del_catalogo and del_catalogo[f.insumo_codigo.upper()]["codigo"] == f.insumo_codigo}
    for f in d.filas:
        if f.insumo_codigo in vistos:
            fallidos.append(FallidoOut(insumo_codigo=f.insumo_codigo, motivo="Aparece repetido en el archivo."))
            continue
        vistos.add(f.insumo_codigo)
        ins_id = conocidos.get(f.insumo_codigo)
        if ins_id is None:
            fallidos.append(FallidoOut(insumo_codigo=f.insumo_codigo, motivo="El insumo no está en el catálogo."))
            continue
        ins = {"id": ins_id}
        ex = fila(a.sesion, "SELECT id, cantidad_planificada, precio_planificado FROM compras.requerimiento "
                            "WHERE proyecto_id = :p AND insumo_id = :i", p=a.obra_id, i=ins["id"])
        if ex is None:
            a.sesion.execute(
                text("INSERT INTO compras.requerimiento (proyecto_id, insumo_id, cantidad_planificada, precio_planificado, creado_por) "
                     "VALUES (:p, :i, :c, :pr, :u)"),
                {"p": a.obra_id, "i": ins["id"], "c": f.cantidad, "pr": f.precio, "u": a.usuario_id},
            )
            creados += 1
        elif ex["cantidad_planificada"] == f.cantidad.quantize(D4) and ex["precio_planificado"] == f.precio.quantize(D4):
            sin_cambios += 1
        else:
            sin_cambios += 1
            avisos.append(f"{f.insumo_codigo}: ya está cargado con {ex['cantidad_planificada']} a {ex['precio_planificado']}; "
                          f"el archivo trae {f.cantidad} a {f.precio}. No se pisó: corrígelo con un ajuste y su motivo.")
    return ResultadoRequerimientoOut(creados=creados, sin_cambios=sin_cambios, fallidos=fallidos, advertencias=avisos)


@router.get("/requerimiento", response_model=list[RequerimientoOut])
def ver_requerimiento(q: str | None = Query(None, max_length=100), a: Actor = Depends(exigir("requerimiento.consultar"))):
    rows = _con_insumo(filas(a.sesion, _REQ + "WHERE r.proyecto_id = :p", p=a.obra_id), _CAMPOS_REQ, (q or "").strip() or None)
    return sorted(rows, key=lambda r: r["insumo_codigo"])


def _req_de_la_obra(a: Actor, req_id: int) -> dict:
    r = fila(a.sesion, _REQ + "WHERE r.id = :i AND r.proyecto_id = :p", i=req_id, p=a.obra_id)
    r = (_con_insumo([r], _CAMPOS_REQ) or [None])[0] if r else None
    if r is None:
        raise ErrorSigro(404, "no_existe", "Ese requerimiento no existe en esta obra.")
    return r


@router.post("/requerimiento/{req_id}/ajustar", response_model=RequerimientoOut)
def ajustar_requerimiento(req_id: int, d: AjusteIn, a: Actor = Depends(exigir("requerimiento.ajustar"))):
    """Cambia la cantidad planificada dejando el ajuste (antes, después, motivo, quién). Sin motivo no hay ajuste."""
    r = _req_de_la_obra(a, req_id)
    if r["cantidad_planificada"] == d.cantidad.quantize(D4):
        raise ErrorSigro(422, "sin_cambio", "La cantidad ya tiene ese valor.")
    a.sesion.execute(
        text("INSERT INTO compras.requerimiento_ajuste (requerimiento_id, cantidad_anterior, cantidad_nueva, motivo, ajustado_por) "
             "VALUES (:r, :ant, :nue, :m, :u)"),
        {"r": req_id, "ant": r["cantidad_planificada"], "nue": d.cantidad, "m": d.motivo, "u": a.usuario_id},
    )
    a.sesion.execute(text("UPDATE compras.requerimiento SET cantidad_planificada = :c WHERE id = :i"), {"c": d.cantidad, "i": req_id})
    return _req_de_la_obra(a, req_id)


@router.get("/requerimiento/{req_id}/ajustes", response_model=list[AjusteOut])
def ver_ajustes(req_id: int, a: Actor = Depends(exigir("requerimiento.consultar"))):
    _req_de_la_obra(a, req_id)
    return filas(a.sesion, "SELECT id, cantidad_anterior, cantidad_nueva, motivo, ajustado_por, ajustado_en::text AS ajustado_en "
                           "FROM compras.requerimiento_ajuste WHERE requerimiento_id = :i ORDER BY id", i=req_id)


# ------------------------------------------------------------------ saldo por comprar (HU-24)
@router.get("/saldo-por-comprar", response_model=list[SaldoOut])
def saldo_por_comprar(
    solo_pendientes: bool = Query(False, description="Solo los insumos que aún falta comprar"),
    q: str | None = Query(None, max_length=100),
    a: Actor = Depends(exigir("requerimiento.consultar")),
):
    """Lo planificado menos lo comprado (solo compras registradas), por insumo, en la unidad del S10 y en unidad de compra."""
    cond = " AND s.saldo > 0" if solo_pendientes else ""
    rows = filas(
        a.sesion,
        "SELECT s.insumo_id, round(s.requerido, 4) AS requerido, round(s.comprado, 4) AS comprado, "
        "s.saldo AS saldo_exacto, round(s.saldo, 4) AS saldo, round(s.exceso, 4) AS exceso "
        f"FROM compras.saldo_por_comprar s WHERE s.proyecto_id = :p{cond}", p=a.obra_id)
    rows = _con_insumo(rows, ("codigo", "descripcion", "unidad_s10", "unidad_compra", "factor_conversion"), (q or "").strip() or None)
    for r in rows:
        factor = r.pop("factor_conversion")
        r["saldo_en_unidad_compra"] = (r.pop("saldo_exacto") / factor).quantize(D4, rounding=ROUND_HALF_UP)   # igual que round() de PostgreSQL
        r["estado"] = "excedido" if r["exceso"] > 0 else ("completo" if r["saldo"] == 0 else "pendiente")
    return sorted(rows, key=lambda r: (-r["saldo"], r["insumo_codigo"]))


# ------------------------------------------------------------------ parámetros de reposición (HU-25)
_PAR = ("SELECT v.id, v.insumo_id, v.stock_seguridad, "
        "v.punto_reposicion, v.registrado_por, v.vigente_desde::text AS vigente_desde ")
_CAMPOS_PAR = ("codigo", "descripcion", "unidad_compra")


@router.post("/parametros-reposicion", response_model=ParametroOut, status_code=201)
def registrar_parametro(d: ParametroIn, a: Actor = Depends(exigir("reposicion.registrar"))):
    """Fija el stock de seguridad y el punto de reposición. Cambiarlos agrega una fila con su vigencia; la anterior queda."""
    ins_id = _id_por_codigo(d.insumo_codigo)
    if ins_id is None:
        raise ErrorSigro(404, "insumo_no_existe", "Ese insumo no está en el catálogo.")
    ins = {"id": ins_id}
    nuevo = fila(
        a.sesion,
        "INSERT INTO compras.parametro_reposicion (proyecto_id, insumo_id, stock_seguridad, punto_reposicion, registrado_por) "
        "VALUES (:p, :i, :s, :pr, :u) RETURNING id",
        p=a.obra_id, i=ins["id"], s=d.stock_seguridad, pr=d.punto_reposicion, u=a.usuario_id)
    return _con_insumo([fila(a.sesion, _PAR + "FROM compras.parametro_reposicion v WHERE v.id = :i", i=nuevo["id"])], _CAMPOS_PAR)[0]


@router.get("/parametros-reposicion", response_model=list[ParametroOut])
def ver_parametros(a: Actor = Depends(exigir("reposicion.consultar"))):
    """Los vigentes: el último de cada insumo."""
    rows = _con_insumo(filas(a.sesion, _PAR + "FROM compras.parametro_reposicion_vigente v WHERE v.proyecto_id = :p", p=a.obra_id),
                       _CAMPOS_PAR)
    return sorted(rows, key=lambda r: r["insumo_codigo"])


@router.get("/parametros-reposicion/{insumo_id}/historial", response_model=list[ParametroOut])
def historial_parametros(insumo_id: int, a: Actor = Depends(exigir("reposicion.consultar"))):
    return _con_insumo(filas(a.sesion, _PAR + "FROM compras.parametro_reposicion v "
                                         "WHERE v.proyecto_id = :p AND v.insumo_id = :i ORDER BY v.id DESC", p=a.obra_id, i=insumo_id),
                       _CAMPOS_PAR)


routers = [router]
