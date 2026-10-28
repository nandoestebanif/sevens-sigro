"""Requerimiento de la obra (HU-19), lo que falta comprar (HU-24) y parámetros de reposición (HU-25).

El requerimiento es lo planificado en el S10 para ESTA obra. Importarlo NO pisa lo ya cargado: una cantidad distinta se
avisa y se corrige con un ajuste que lleva motivo y autor. Todas las cantidades del requerimiento y del saldo están en la
unidad del S10; el stock de reposición, en unidad de compra. Factor de conversión = unidades del S10 por unidad de compra.
"""
from __future__ import annotations

from decimal import Decimal
from typing import Literal

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import text

from app.dependencias import Actor, exigir
from app.errores import ErrorSigro
from app.modulos.obra.rutas import fila, filas

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


# ------------------------------------------------------------------ requerimiento (HU-19)
_REQ = ("SELECT r.id, r.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad_s10, r.cantidad_planificada, "
        "r.precio_planificado, round(r.cantidad_planificada * r.precio_planificado, 2) AS costo_planificado "
        "FROM compras.requerimiento r JOIN catalogo.insumo i ON i.id = r.insumo_id ")


@router.post("/requerimiento/importar", response_model=ResultadoRequerimientoOut)
def importar_requerimiento(d: ImportarRequerimientoIn, a: Actor = Depends(exigir("requerimiento.importar"))):
    """Carga lo planificado. Insumo que el catálogo no conoce → fallido con motivo (importa primero el catálogo).
    Insumo ya cargado con otra cantidad → se avisa y NO se pisa: eso es un ajuste con motivo."""
    creados = sin_cambios = 0
    fallidos: list[FallidoOut] = []
    avisos: list[str] = []
    vistos: set[str] = set()
    for f in d.filas:
        if f.insumo_codigo in vistos:
            fallidos.append(FallidoOut(insumo_codigo=f.insumo_codigo, motivo="Aparece repetido en el archivo."))
            continue
        vistos.add(f.insumo_codigo)
        ins = fila(a.sesion, "SELECT id FROM catalogo.insumo WHERE codigo = :c", c=f.insumo_codigo)
        if ins is None:
            fallidos.append(FallidoOut(insumo_codigo=f.insumo_codigo, motivo="El insumo no está en el catálogo."))
            continue
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
    cond, p = "", {"p": a.obra_id}
    if q:
        cond = " AND (i.codigo ILIKE :q OR i.descripcion ILIKE :q)"
        p["q"] = f"%{q.strip()}%"
    return filas(a.sesion, _REQ + f"WHERE r.proyecto_id = :p{cond} ORDER BY i.codigo", **p)


def _req_de_la_obra(a: Actor, req_id: int) -> dict:
    r = fila(a.sesion, _REQ + "WHERE r.id = :i AND r.proyecto_id = :p", i=req_id, p=a.obra_id)
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
    cond, p = "", {"p": a.obra_id}
    if solo_pendientes:
        cond += " AND s.saldo > 0"
    if q:
        cond += " AND (i.codigo ILIKE :q OR i.descripcion ILIKE :q)"
        p["q"] = f"%{q.strip()}%"
    rows = filas(
        a.sesion,
        "SELECT s.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad_s10, i.unidad_compra, round(s.requerido, 4) AS requerido, round(s.comprado, 4) AS comprado, "
        "round(s.saldo, 4) AS saldo, round(s.saldo / i.factor_conversion, 4) AS saldo_en_unidad_compra, round(s.exceso, 4) AS exceso "
        "FROM compras.saldo_por_comprar s JOIN catalogo.insumo i ON i.id = s.insumo_id "
        f"WHERE s.proyecto_id = :p{cond} ORDER BY s.saldo DESC, i.codigo", **p)
    for r in rows:
        r["estado"] = "excedido" if r["exceso"] > 0 else ("completo" if r["saldo"] == 0 else "pendiente")
    return rows


# ------------------------------------------------------------------ parámetros de reposición (HU-25)
_PAR = ("SELECT v.id, v.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad_compra, v.stock_seguridad, "
        "v.punto_reposicion, v.registrado_por, v.vigente_desde::text AS vigente_desde ")


@router.post("/parametros-reposicion", response_model=ParametroOut, status_code=201)
def registrar_parametro(d: ParametroIn, a: Actor = Depends(exigir("reposicion.registrar"))):
    """Fija el stock de seguridad y el punto de reposición. Cambiarlos agrega una fila con su vigencia; la anterior queda."""
    ins = fila(a.sesion, "SELECT id FROM catalogo.insumo WHERE codigo = :c", c=d.insumo_codigo)
    if ins is None:
        raise ErrorSigro(404, "insumo_no_existe", "Ese insumo no está en el catálogo.")
    nuevo = fila(
        a.sesion,
        "INSERT INTO compras.parametro_reposicion (proyecto_id, insumo_id, stock_seguridad, punto_reposicion, registrado_por) "
        "VALUES (:p, :i, :s, :pr, :u) RETURNING id",
        p=a.obra_id, i=ins["id"], s=d.stock_seguridad, pr=d.punto_reposicion, u=a.usuario_id)
    return fila(a.sesion, _PAR + "FROM compras.parametro_reposicion v JOIN catalogo.insumo i ON i.id = v.insumo_id WHERE v.id = :i", i=nuevo["id"])


@router.get("/parametros-reposicion", response_model=list[ParametroOut])
def ver_parametros(a: Actor = Depends(exigir("reposicion.consultar"))):
    """Los vigentes: el último de cada insumo."""
    return filas(a.sesion, _PAR + "FROM compras.parametro_reposicion_vigente v JOIN catalogo.insumo i ON i.id = v.insumo_id "
                                  "WHERE v.proyecto_id = :p ORDER BY i.codigo", p=a.obra_id)


@router.get("/parametros-reposicion/{insumo_id}/historial", response_model=list[ParametroOut])
def historial_parametros(insumo_id: int, a: Actor = Depends(exigir("reposicion.consultar"))):
    return filas(a.sesion, _PAR + "FROM compras.parametro_reposicion v JOIN catalogo.insumo i ON i.id = v.insumo_id "
                                  "WHERE v.proyecto_id = :p AND v.insumo_id = :i ORDER BY v.id DESC", p=a.obra_id, i=insumo_id)


routers = [router]
