"""Partidas y análisis de precios unitarios (HU-16).

Esta API GUARDA lo que lee el microservicio de Integraciones del S10 (el lector del PDF del APU es de Integraciones).
Reglas: la partida se identifica por código Y descripción (el reporte trunca el código); un recurso que el catálogo no
conoce se reporta y NO detiene la importación; lo ya cargado no se pisa (se avisa si el archivo trae otra cantidad).
"""
from __future__ import annotations

from decimal import Decimal

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import text

from app.dependencias import Actor, exigir
from app.errores import ErrorSigro
from app.modulos.compras import catalogo_cliente
from app.sql import fila, filas

router = APIRouter(prefix="/obras/{obra_id}", tags=["compras · partidas y APU"])
D6 = Decimal("0.000001")


class Estricto(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class FilaApu(Estricto):
    partida_codigo: str = Field(min_length=1, max_length=40)
    partida_descripcion: str = Field(min_length=1, max_length=300)
    partida_unidad: str = Field(min_length=1, max_length=20)
    insumo_codigo: str = Field(min_length=1, max_length=30)
    cantidad_por_unidad: Decimal = Field(gt=0, max_digits=18, decimal_places=6)
    unidad: str = Field(min_length=1, max_length=20)


class ImportarApuIn(Estricto):
    filas: list[FilaApu] = Field(min_length=1, max_length=20000)


class FallidoApuOut(BaseModel):
    partida_codigo: str
    insumo_codigo: str
    motivo: str


class ResultadoApuOut(BaseModel):
    partidas_creadas: int
    recursos_creados: int
    sin_cambios: int
    fallidos: list[FallidoApuOut]
    advertencias: list[str]


class PartidaOut(BaseModel):
    id: int
    codigo: str
    descripcion: str
    unidad: str
    recursos: int


class RecursoPartidaOut(BaseModel):
    insumo_id: int
    insumo_codigo: str
    descripcion: str
    unidad_s10: str
    cantidad_por_unidad: Decimal
    unidad: str


@router.post("/partidas/importar-apu", response_model=ResultadoApuOut)
def importar_apu(d: ImportarApuIn, a: Actor = Depends(exigir("partida.importar"))):
    """Guarda partida → recurso → cantidad por unidad. Repetir la importación no duplica ni pisa."""
    p_nuevas = r_nuevos = sin_cambios = 0
    fallidos: list[FallidoApuOut] = []
    avisos: list[str] = []
    cache: dict[tuple[str, str], int] = {}
    # compras NO lee la base de catálogo (ADR-09): los insumos se piden UNA vez, por su API interna.
    del_catalogo = catalogo_cliente.por_codigo(f.insumo_codigo for f in d.filas)
    vistos: set[tuple[int, str]] = set()
    for f in d.filas:
        desc = " ".join(f.partida_descripcion.split())          # espacios de más no hacen otra partida
        clave = (f.partida_codigo, desc.lower())
        ins = del_catalogo.get(f.insumo_codigo.upper())
        if ins is None or ins["codigo"] != f.insumo_codigo:       # el código es sensible a mayúsculas (regla 8)
            fallidos.append(FallidoApuOut(partida_codigo=f.partida_codigo, insumo_codigo=f.insumo_codigo,
                                          motivo="El recurso no está en el catálogo."))
            continue
        if clave not in cache:
            ex = fila(a.sesion, "SELECT id FROM compras.partida WHERE proyecto_id = :p AND codigo = :c "
                                "AND lower(btrim(descripcion)) = lower(btrim(:d))",
                      p=a.obra_id, c=f.partida_codigo, d=desc)
            if ex is None:
                nuevo = fila(a.sesion,
                             "INSERT INTO compras.partida (proyecto_id, codigo, descripcion, unidad, creado_por) "
                             "VALUES (:p, :c, :d, :u, :us) RETURNING id",
                             p=a.obra_id, c=f.partida_codigo, d=desc, u=f.partida_unidad, us=a.usuario_id)
                cache[clave] = nuevo["id"]
                p_nuevas += 1
            else:
                cache[clave] = ex["id"]
        pid = cache[clave]
        if (pid, f.insumo_codigo) in vistos:
            fallidos.append(FallidoApuOut(partida_codigo=f.partida_codigo, insumo_codigo=f.insumo_codigo,
                                          motivo="El recurso aparece repetido en la misma partida."))
            continue
        vistos.add((pid, f.insumo_codigo))
        rc = fila(a.sesion, "SELECT cantidad_por_unidad FROM compras.partida_recurso WHERE partida_id = :p AND insumo_id = :i",
                  p=pid, i=ins["id"])
        if rc is None:
            a.sesion.execute(
                text("INSERT INTO compras.partida_recurso (partida_id, insumo_id, cantidad_por_unidad, unidad) "
                     "VALUES (:p, :i, :c, :u)"),
                {"p": pid, "i": ins["id"], "c": f.cantidad_por_unidad, "u": f.unidad})
            r_nuevos += 1
            if f.unidad.strip().lower() != ins["unidad_s10"].strip().lower():
                avisos.append(f"{f.partida_codigo} · {f.insumo_codigo}: el APU lo da en «{f.unidad}» y el catálogo en «{ins['unidad_s10']}».")
        else:
            sin_cambios += 1
            if rc["cantidad_por_unidad"] != f.cantidad_por_unidad.quantize(D6):
                avisos.append(f"{f.partida_codigo} · {f.insumo_codigo}: ya está con {rc['cantidad_por_unidad']}; el archivo trae "
                              f"{f.cantidad_por_unidad}. No se pisó.")
    return ResultadoApuOut(partidas_creadas=p_nuevas, recursos_creados=r_nuevos, sin_cambios=sin_cambios,
                           fallidos=fallidos, advertencias=avisos)


@router.get("/partidas", response_model=list[PartidaOut])
def ver_partidas(q: str | None = Query(None, max_length=100), a: Actor = Depends(exigir("partida.consultar"))):
    cond, p = "", {"p": a.obra_id}
    if q:
        cond = " AND (pa.codigo ILIKE :q OR pa.descripcion ILIKE :q)"
        p["q"] = f"%{q.strip()}%"
    return filas(a.sesion,
                 "SELECT pa.id, pa.codigo, pa.descripcion, pa.unidad, count(r.id) AS recursos FROM compras.partida pa "
                 f"LEFT JOIN compras.partida_recurso r ON r.partida_id = pa.id WHERE pa.proyecto_id = :p{cond} "
                 "GROUP BY pa.id ORDER BY pa.codigo, pa.descripcion", **p)


@router.get("/partidas/{partida_id}/recursos", response_model=list[RecursoPartidaOut])
def ver_recursos_de_partida(partida_id: int, a: Actor = Depends(exigir("partida.consultar"))):
    if fila(a.sesion, "SELECT 1 AS x FROM compras.partida WHERE id = :i AND proyecto_id = :p", i=partida_id, p=a.obra_id) is None:
        raise ErrorSigro(404, "no_existe", "Esa partida no existe en esta obra.")
    rs = filas(a.sesion, "SELECT insumo_id, cantidad_por_unidad, unidad FROM compras.partida_recurso WHERE partida_id = :i", i=partida_id)
    insumos = catalogo_cliente.por_id(r["insumo_id"] for r in rs)       # datos del catálogo por API interna, no por JOIN
    salida = [{"insumo_id": r["insumo_id"], "insumo_codigo": insumos[r["insumo_id"]]["codigo"],
               "descripcion": insumos[r["insumo_id"]]["descripcion"], "unidad_s10": insumos[r["insumo_id"]]["unidad_s10"],
               "cantidad_por_unidad": r["cantidad_por_unidad"], "unidad": r["unidad"]} for r in rs if r["insumo_id"] in insumos]
    return sorted(salida, key=lambda x: x["insumo_codigo"])


@router.get("/insumos/{insumo_id}/partidas", response_model=list[PartidaOut])
def partidas_que_usan_el_insumo(insumo_id: int, a: Actor = Depends(exigir("partida.consultar"))):
    """Trazabilidad inversa: en qué partidas de la obra se gasta este insumo."""
    return filas(a.sesion,
                 "SELECT pa.id, pa.codigo, pa.descripcion, pa.unidad, 1 AS recursos FROM compras.partida pa "
                 "JOIN compras.partida_recurso r ON r.partida_id = pa.id WHERE pa.proyecto_id = :p AND r.insumo_id = :i "
                 "ORDER BY pa.codigo", p=a.obra_id, i=insumo_id)


routers = [router]
