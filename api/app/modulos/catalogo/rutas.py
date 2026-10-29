"""Catálogo de insumos: HU-08 alta con código único y «por verificar», HU-17 factor de conversión con histórico,
HU-07 aplicar la importación del S10.

El catálogo es compartido entre obras: solo se comparte el NOMBRE y el CÓDIGO de cada insumo, para que un mismo
material se llame igual en todas. La relación de insumos con cantidades y precios es de cada obra (HU-19).
Importar NUNCA toca saldos: el catálogo no es el almacén.
"""
from __future__ import annotations

import re
import unicodedata

from fastapi import APIRouter, Depends, File, Query, UploadFile
from sqlalchemy import text

from app import integraciones
from app.dependencias import Actor, exigir_en_alguna_obra
from app.errores import ErrorSigro
from app.modulos.catalogo import esquemas as e
from app.sql import fila, filas

insumos = APIRouter(prefix="/catalogo/insumos", tags=["catálogo"])
importaciones = APIRouter(prefix="/catalogo/importaciones-s10", tags=["catálogo"])

_COLS = "id, codigo, descripcion, unidad_s10, unidad_compra, factor_conversion, tipo, activo, verificado"
_PREFIJO = "SIGRO-"
_MAX_PDF = 15 * 1024 * 1024     # la Relación de insumos real pesa unos cientos de KB


def normalizar(t: str) -> str:
    """Sin tildes, minúsculas y espacios simples: «Cemento  Portland» == «cemento portland»."""
    sin = "".join(c for c in unicodedata.normalize("NFD", t) if unicodedata.category(c) != "Mn")
    return re.sub(r"\s+", " ", sin).strip().lower()


def _existente(a: Actor, insumo_id: int) -> dict:
    r = fila(a.sesion, f"SELECT {_COLS} FROM catalogo.insumo WHERE id = :i", i=insumo_id)
    if r is None:
        raise ErrorSigro(404, "no_existe", "Ese insumo no existe en el catálogo.")
    return r


@insumos.get("", response_model=list[e.InsumoOut])
def listar(
    q: str | None = Query(None, max_length=100, description="Busca en código y descripción"),
    tipo: e.Tipo | None = None,
    verificado: bool | None = None,
    a: Actor = Depends(exigir_en_alguna_obra("insumo.consultar")),
):
    cond, p = ["true"], {}
    if q:
        cond.append("(codigo ILIKE :q OR descripcion ILIKE :q)")
        p["q"] = f"%{q.strip()}%"
    if tipo:
        cond.append("tipo = :t")
        p["t"] = tipo
    if verificado is not None:
        cond.append("verificado = :v")
        p["v"] = verificado
    return filas(a.sesion, f"SELECT {_COLS} FROM catalogo.insumo WHERE {' AND '.join(cond)} ORDER BY codigo LIMIT 3000", **p)


@insumos.post("", response_model=e.InsumoOut, status_code=201)
def registrar(d: e.InsumoIn, a: Actor = Depends(exigir_en_alguna_obra("insumo.registrar"))):
    """HU-08: el código lo asigna el servidor; un nombre repetido se rechaza señalando el insumo que ya existe;
    nace «por verificar» hasta que el residente lo revise."""
    # el código sale de un contador: dos altas simultáneas se serializan con un candado de transacción
    a.sesion.execute(text("SELECT pg_advisory_xact_lock(70801)"))
    clave = normalizar(d.descripcion)
    for x in filas(a.sesion, "SELECT codigo, descripcion, unidad_compra FROM catalogo.insumo"):
        if normalizar(x["descripcion"]) == clave and normalizar(x["unidad_compra"]) == normalizar(d.unidad):
            raise ErrorSigro(409, "insumo_duplicado",
                             f"Ya existe ese insumo en el catálogo: {x['codigo']} · {x['descripcion']}. Úsalo en lugar de crear otro.")
    n = a.sesion.execute(
        text("SELECT coalesce(max(substring(codigo from 7)::int), 0) + 1 FROM catalogo.insumo WHERE codigo ~ '^SIGRO-[0-9]+$'")
    ).scalar()
    r = fila(
        a.sesion,
        "INSERT INTO catalogo.insumo (codigo, descripcion, unidad_s10, unidad_compra, tipo, verificado, creado_por) "
        f"VALUES (:c, :d, :u, :u, :t, false, :por) RETURNING {_COLS}",
        c=f"{_PREFIJO}{n:05d}", d=d.descripcion, u=d.unidad, t=d.tipo, por=a.usuario_id,
    )
    return r


@insumos.post("/{insumo_id}/verificar", response_model=e.InsumoOut)
def verificar(insumo_id: int, a: Actor = Depends(exigir_en_alguna_obra("insumo.verificar"))):
    """El residente confirma la descripción de un insumo que el almacenero creó en obra."""
    _existente(a, insumo_id)
    return fila(a.sesion, f"UPDATE catalogo.insumo SET verificado = true WHERE id = :i RETURNING {_COLS}", i=insumo_id)


@insumos.post("/{insumo_id}/factor-conversion", response_model=e.InsumoOut)
def cambiar_factor(insumo_id: int, d: e.FactorIn, a: Actor = Depends(exigir_en_alguna_obra("insumo.factor"))):
    """HU-17: el factor cambia con fecha, usuario y motivo; el valor anterior queda en el histórico (nunca se pisa)."""
    ins = _existente(a, insumo_id)
    if ins["factor_conversion"] == d.factor:
        raise ErrorSigro(422, "sin_cambio", "El factor ya tiene ese valor.")
    a.sesion.execute(
        text("INSERT INTO catalogo.insumo_factor_historial (insumo_id, factor_anterior, factor_nuevo, motivo, cambiado_por) "
             "VALUES (:i, :ant, :nue, :m, :u)"),
        {"i": insumo_id, "ant": ins["factor_conversion"], "nue": d.factor, "m": d.motivo, "u": a.usuario_id},
    )
    return fila(a.sesion, f"UPDATE catalogo.insumo SET factor_conversion = :f WHERE id = :i RETURNING {_COLS}", f=d.factor, i=insumo_id)


@insumos.get("/{insumo_id}/factor-conversion/historial", response_model=list[e.FactorHistorialOut])
def historial_factor(insumo_id: int, a: Actor = Depends(exigir_en_alguna_obra("insumo.consultar"))):
    _existente(a, insumo_id)
    return filas(a.sesion, "SELECT id, factor_anterior, factor_nuevo, motivo, cambiado_por, cambiado_en "
                           "FROM catalogo.insumo_factor_historial WHERE insumo_id = :i ORDER BY id", i=insumo_id)


@insumos.post("/importar-s10", response_model=e.ResultadoImportacionOut)
def importar_s10(d: e.ImportarS10In, a: Actor = Depends(exigir_en_alguna_obra("insumo.importar"))):
    """HU-07: aplica lo que leyó Integraciones. El código es la clave: repetir la importación no duplica.
    Existente → actualiza solo la descripción si cambió (el código y la unidad del S10 no se editan); nuevo → se crea
    ya verificado (viene del presupuesto). No toca saldos ni cantidades."""
    a.sesion.execute(text("SELECT pg_advisory_xact_lock(70801)"))
    vistos: set[str] = set()
    creados = actualizados = sin_cambios = 0
    avisos: list[str] = []
    for f in d.insumos:
        if f.codigo in vistos:
            avisos.append(f"{f.codigo}: aparece repetido en el archivo; se tomó la primera fila.")
            continue
        vistos.add(f.codigo)
        ex = fila(a.sesion, "SELECT id, descripcion, tipo FROM catalogo.insumo WHERE codigo = :c", c=f.codigo)
        if ex is None:
            a.sesion.execute(
                text("INSERT INTO catalogo.insumo (codigo, descripcion, unidad_s10, unidad_compra, tipo, verificado, creado_por) "
                     "VALUES (:c, :d, :u, :u, :t, true, :por)"),
                {"c": f.codigo, "d": f.descripcion, "u": f.unidad, "t": f.tipo, "por": a.usuario_id},
            )
            creados += 1
            continue
        if ex["tipo"] != f.tipo:
            avisos.append(f"{f.codigo}: el S10 lo trae como «{f.tipo}» y el catálogo como «{ex['tipo']}»; no se cambió.")
        if ex["descripcion"] != f.descripcion:
            a.sesion.execute(text("UPDATE catalogo.insumo SET descripcion = :d WHERE id = :i"), {"d": f.descripcion, "i": ex["id"]})
            actualizados += 1
        else:
            sin_cambios += 1
    return e.ResultadoImportacionOut(creados=creados, actualizados=actualizados, sin_cambios=sin_cambios, advertencias=avisos)



@importaciones.post("", response_model=e.LecturaS10Out)
def leer_pdf_s10(
    archivo: UploadFile = File(..., description="La «Relación de insumos» exportada del S10 (PDF)"),
    a: Actor = Depends(exigir_en_alguna_obra("insumo.importar")),
):
    """HU-07, paso 1: la web sube el PDF por el gateway con su JWT; el catálogo lo reenvía a Integraciones con el
    token de servicio (el navegador nunca lo ve) y devuelve lo leído. Los códigos que ya existen los pone el servidor,
    no el cliente, para marcar cada fila «crear» o «actualizar». No escribe nada: aplicar es POST /insumos/importar-s10."""
    contenido = archivo.file.read(_MAX_PDF + 1)
    if len(contenido) > _MAX_PDF:
        raise ErrorSigro(413, "archivo_muy_grande", "El PDF supera los 15 MB; exporta solo la Relación de insumos del S10.")
    if not contenido:
        raise ErrorSigro(422, "archivo_vacio", "El archivo llegó vacío.")
    existentes = [x["codigo"] for x in filas(a.sesion, "SELECT codigo FROM catalogo.insumo ORDER BY codigo")]
    return integraciones.leer_relacion_s10(archivo.filename or "relacion-insumos.pdf", contenido, existentes)


routers = [insumos, importaciones]
