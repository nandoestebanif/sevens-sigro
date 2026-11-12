"""HU-34 · comprobante adjunto de la compra (y HU-61 · descargarlo).

Una compra admite varios archivos (JPG, PNG o PDF, hasta 10 MB cada uno). El comprobante **nunca se borra ni se
reemplaza** (estado E12): si se subió mal se anula con su motivo y se sube el correcto. Cada archivo guarda su huella
sha256; al descargar se vuelve a calcular y se compara, para probar que no cambió.

Dónde viven los bytes (ADR-11, D15): en `compras.compra_adjunto_contenido` (Neon) hoy; la columna `almacenamiento` deja
escrito el día en que pasen a S3/R2. Listar comprobantes nunca toca esa tabla.

El alcance es la obra: la compra se busca con `proyecto_id = obra de la petición` (regla 9), así que el comprobante de una
compra de otra obra da 404 aunque se conozca su id.
"""
from __future__ import annotations

import hashlib
from urllib.parse import quote

from fastapi import APIRouter, Depends, File, Response, UploadFile
from sqlalchemy import text

from app import eventos
from app.dependencias import Actor, exigir
from app.errores import ErrorSigro
from app.modulos.compras import dominio as dom
from app.modulos.compras import esquemas as e
from app.sql import fila, filas

adjuntos = APIRouter(prefix="/obras/{obra_id}/compras/{compra_id}/comprobantes", tags=["compras · comprobantes"])

_SQL_ADJUNTO = (
    "SELECT j.id, j.compra_id, j.nombre_original AS nombre, j.tipo_mime, j.tamano_bytes, j.sha256, "
    "j.subido_por, j.subido_en, "
    "CASE WHEN j.anulado_en IS NULL THEN 'vigente' ELSE 'anulado' END AS estado, "
    "j.anulado_por, j.anulado_en, j.motivo_anulacion "
    "FROM compras.compra_adjunto j "
)


def _compra(a: Actor, compra_id: int) -> dict:
    c = fila(a.sesion, "SELECT id, estado, numero_comprobante FROM compras.compra WHERE id = :i AND proyecto_id = :p",
             i=compra_id, p=a.obra_id)
    if c is None:
        raise ErrorSigro(404, "no_existe", "Esa compra no existe en esta obra.")
    return c


def _adjunto(a: Actor, compra_id: int, adjunto_id: int) -> dict:
    j = fila(a.sesion, _SQL_ADJUNTO + "WHERE j.id = :a AND j.compra_id = :c", a=adjunto_id, c=compra_id)
    if j is None:
        raise ErrorSigro(404, "no_existe", "Ese comprobante no existe en esta compra.")
    return j


@adjuntos.post("", response_model=e.ComprobanteOut, status_code=201)
def subir_comprobante(
    compra_id: int,
    archivo: UploadFile = File(..., description="Factura, boleta o guía: PDF, JPG o PNG, hasta 10 MB."),
    a: Actor = Depends(exigir("compra.registrar")),
):
    """Sube un comprobante a la compra. El tipo se decide por el contenido (firma del archivo), no por la extensión.

    413 si pasa de 10 MB · 415 si no es PDF/JPG/PNG · 422 si llega vacío · 409 si la compra está anulada o si ese mismo
    archivo (misma huella) ya está adjunto a la compra."""
    compra = _compra(a, compra_id)
    if compra["estado"] != "registrada":
        raise ErrorSigro(409, "compra_anulada", "La compra está anulada: ya no admite comprobantes nuevos.")
    contenido = archivo.file.read(dom.MAX_COMPROBANTE_BYTES + 1)
    if len(contenido) > dom.MAX_COMPROBANTE_BYTES:
        raise ErrorSigro(413, "archivo_muy_grande", "El comprobante supera los 10 MB; reduce la foto o comprime el PDF.")
    if not contenido:
        raise ErrorSigro(422, "archivo_vacio", "El archivo llegó vacío.")
    mime = dom.tipo_real(contenido)
    if mime is None:
        raise ErrorSigro(415, "tipo_no_admitido", "Solo se admiten PDF, JPG o PNG. Revisa que el archivo no esté dañado.")
    huella = hashlib.sha256(contenido).hexdigest()
    repetido = fila(a.sesion, "SELECT id, anulado_en FROM compras.compra_adjunto WHERE compra_id = :c AND sha256 = :h",
                    c=compra_id, h=huella)
    if repetido:
        raise ErrorSigro(409, "comprobante_repetido",
                         "Ese mismo archivo ya se adjuntó a esta compra" + (" y fue anulado." if repetido["anulado_en"] else "."))
    nombre = dom.nombre_seguro(archivo.filename, mime)
    r = fila(
        a.sesion,
        "INSERT INTO compras.compra_adjunto (compra_id, archivo, nombre_original, tipo_mime, tamano_bytes, sha256, subido_por, almacenamiento) "
        "VALUES (:c, :k, :n, :m, :t, :h, :u, 'bd') RETURNING id",
        c=compra_id, k="bd:" + huella, n=nombre, m=mime, t=len(contenido), h=huella, u=a.usuario_id,
    )
    a.sesion.execute(text("INSERT INTO compras.compra_adjunto_contenido (adjunto_id, contenido) VALUES (:i, :b)"),
                     {"i": r["id"], "b": contenido})
    return _adjunto(a, compra_id, r["id"])


@adjuntos.get("", response_model=list[e.ComprobanteOut])
def listar_comprobantes(compra_id: int, a: Actor = Depends(exigir("compra.consultar"))):
    """Los vigentes primero y luego los anulados (con su motivo): nada desaparece de la lista."""
    _compra(a, compra_id)
    return filas(a.sesion, _SQL_ADJUNTO + "WHERE j.compra_id = :c ORDER BY (j.anulado_en IS NOT NULL), j.subido_en, j.id", c=compra_id)


@adjuntos.get("/{adjunto_id}")
def descargar_comprobante(compra_id: int, adjunto_id: int, a: Actor = Depends(exigir("compra.consultar"))):
    """El archivo original, tal como se adjuntó (HU-34 c3, HU-61). También se descarga el anulado: no se pierde.
    Antes de entregarlo se recalcula la huella y se compara con la guardada."""
    _compra(a, compra_id)                      # regla 9: la compra tiene que ser de ESTA obra
    j = _adjunto(a, compra_id, adjunto_id)
    r = fila(a.sesion, "SELECT contenido FROM compras.compra_adjunto_contenido WHERE adjunto_id = :i", i=adjunto_id)
    if r is None:
        raise ErrorSigro(404, "contenido_no_disponible", "El archivo de este comprobante no está disponible.")
    contenido = bytes(r["contenido"])
    if hashlib.sha256(contenido).hexdigest() != j["sha256"]:
        raise ErrorSigro(409, "comprobante_alterado", "La huella del archivo no coincide con la que se guardó al adjuntarlo.")
    return Response(
        content=contenido, media_type=j["tipo_mime"],
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote(j['nombre'])}", "X-Sigro-Sha256": j["sha256"],
                 "Cache-Control": "private, no-store"},
    )


@adjuntos.post("/{adjunto_id}/anulacion", response_model=e.ComprobanteOut)
def anular_comprobante(compra_id: int, adjunto_id: int, datos: e.AnularIn, a: Actor = Depends(exigir("compra.registrar"))):
    """Anula el comprobante con su motivo (obligatorio). No se borra ni se reemplaza: queda en la lista como anulado."""
    compra = _compra(a, compra_id)
    j = _adjunto(a, compra_id, adjunto_id)
    if j["estado"] == "anulado":
        raise ErrorSigro(409, "ya_anulado", "Ese comprobante ya estaba anulado.")
    a.sesion.execute(
        text("UPDATE compras.compra_adjunto SET anulado_por = :u, anulado_en = now(), motivo_anulacion = :m WHERE id = :i"),
        {"u": a.usuario_id, "m": datos.motivo, "i": adjunto_id},
    )
    eventos.publicar(
        a.sesion, "comprobante.anulado", a.obra_id,
        eventos.destinatarios_con_rol(a.sesion, a.obra_id, ["administrador", "residente"], excepto=a.usuario_id),
        "Comprobante anulado", f"{a.nombre} anuló {j['nombre']} de la compra {compra['numero_comprobante']}: {datos.motivo}",
        {"compra_id": compra_id, "comprobante_id": adjunto_id},
    )
    return _adjunto(a, compra_id, adjunto_id)


routers = [adjuntos]
