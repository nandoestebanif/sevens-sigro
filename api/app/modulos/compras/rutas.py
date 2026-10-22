"""Módulo compras: proveedores, compras con su comprobante y solicitudes de materiales.

HU-29 consultar proveedores · HU-30 registrar proveedor · HU-31 validar RUC en SUNAT ·
HU-32 registrar compra · CR-04 solicitud de materiales.

Registrar una compra NO mueve stock: este módulo no conoce los movimientos. La recepción (HU-35)
es de almacén (Henry) y es la que genera el ingreso.
"""
from __future__ import annotations

from decimal import ROUND_HALF_UP, Decimal

from fastapi import APIRouter, Depends, Query
from sqlalchemy import text
from sqlalchemy.orm import Session

from app import eventos, integraciones
from app.dependencias import Actor, exigir, exigir_en_alguna_obra
from app.errores import ErrorSigro
from app.modulos.compras import esquemas as e
from app.modulos.compras.dominio import ruc_valido
from app.modulos.obra.rutas import fila, filas

proveedores = APIRouter(prefix="/proveedores", tags=["compras · proveedores"])
compras = APIRouter(prefix="/obras/{obra_id}", tags=["compras"])

IGV = Decimal("0.18")
CENTAVO = Decimal("0.01")


def _redondear(x: Decimal) -> Decimal:
    return x.quantize(CENTAVO, rounding=ROUND_HALF_UP)


# ------------------------------------------------------------------ proveedores
_SQL_PROV = (
    "SELECT p.id, p.ruc, p.razon_social, "
    "CASE WHEN p.estado = 'baja' THEN 'de_baja' ELSE p.estado_validacion END AS estado, "
    "(SELECT count(*) FROM compras.compra c WHERE c.proveedor_id = p.id AND c.estado = 'registrada') AS compras_registradas, "
    "(SELECT max(c.fecha) FROM compras.compra c WHERE c.proveedor_id = p.id AND c.estado = 'registrada') AS ultima_compra "
    "FROM compras.proveedor p "
)


def _validar_en_sunat(ruc: str) -> tuple[bool, str | None]:
    """(validado, advertencia). Si SUNAT/el micro no responden, queda sin validar y NO se bloquea (HU-31)."""
    if len(ruc) == 8:       # D-C5: persona natural con DNI. No es un RUC: no se consulta a SUNAT y queda sin validar.
        return False, "Es un DNI (persona natural): queda sin validar; solo los RUC se validan en SUNAT."
    if not ruc_valido(ruc):
        return False, "El dígito verificador del RUC no coincide: revisa que esté bien escrito."
    r = integraciones.consultar_ruc(ruc)
    if r is None:
        return False, "No se pudo consultar a SUNAT; el proveedor queda sin validar."
    if not r.apto_para_comprar:
        return False, f"SUNAT lo informa {r.estado_sunat} / {r.condicion}; queda sin validar."
    return True, None


@proveedores.get("", response_model=list[e.ProveedorOut])
def listar_proveedores(
    q: str = Query("", max_length=80), solo_para_comprar: bool = Query(False),
    a: Actor = Depends(exigir_en_alguna_obra("proveedor.consultar")),
):
    """HU-29: se busca por razón social o por RUC. Un proveedor de baja no aparece para compras nuevas."""
    return filas(
        a.sesion,
        _SQL_PROV + "WHERE (:q = '' OR lower(p.razon_social) LIKE '%' || lower(:q) || '%' OR p.ruc LIKE :q || '%') "
        "AND (NOT :c OR p.estado = 'activo') ORDER BY p.razon_social",
        q=q.strip(), c=solo_para_comprar,
    )


@proveedores.post("", response_model=e.ProveedorOut, status_code=201)
def crear_proveedor(datos: e.ProveedorIn, a: Actor = Depends(exigir_en_alguna_obra("proveedor.registrar"))):
    validado, advertencia = _validar_en_sunat(datos.ruc)
    r = fila(
        a.sesion,
        "INSERT INTO compras.proveedor (ruc, razon_social, estado_validacion, validado_en) "
        "VALUES (:r, :n, CASE WHEN :v THEN 'validado' ELSE 'sin_validar' END, CASE WHEN :v THEN now() END) RETURNING id",
        r=datos.ruc, n=datos.razon_social, v=validado,
    )
    out = fila(a.sesion, _SQL_PROV + "WHERE p.id = :i", i=r["id"])
    out["advertencia"] = advertencia
    return out


@proveedores.post("/{proveedor_id}/validar", response_model=e.ProveedorOut)
def validar_proveedor(proveedor_id: int, a: Actor = Depends(exigir_en_alguna_obra("proveedor.registrar"))):
    """HU-31: vuelve a consultar SUNAT; deja en evidencia a los que no pasan (los datos sucios del cliente)."""
    p = fila(a.sesion, "SELECT ruc, estado, estado_validacion FROM compras.proveedor WHERE id = :i", i=proveedor_id)
    if p is None:
        raise ErrorSigro(404, "no_existe", "Ese proveedor no existe.")
    validado, advertencia = _validar_en_sunat(p["ruc"])
    if validado and p["estado_validacion"] != "validado":
        a.sesion.execute(
            text("UPDATE compras.proveedor SET estado_validacion = 'validado', validado_en = now() WHERE id = :i"), {"i": proveedor_id}
        )
    out = fila(a.sesion, _SQL_PROV + "WHERE p.id = :i", i=proveedor_id)
    out["advertencia"] = advertencia
    return out


@proveedores.post("/{proveedor_id}/baja", response_model=e.ProveedorOut)
def dar_de_baja_proveedor(proveedor_id: int, a: Actor = Depends(exigir_en_alguna_obra("proveedor.registrar"))):
    """Nada se borra: la baja es un estado con fecha. Sus compras históricas se conservan."""
    r = a.sesion.execute(
        text("UPDATE compras.proveedor SET estado = 'baja', fecha_baja = now() WHERE id = :i AND estado = 'activo' RETURNING id"),
        {"i": proveedor_id},
    ).first()
    if r is None:
        raise ErrorSigro(404, "no_existe", "Ese proveedor no existe o ya estaba de baja.")
    return fila(a.sesion, _SQL_PROV + "WHERE p.id = :i", i=proveedor_id)


# ------------------------------------------------------------------ compras (HU-32)
_SQL_COMPRA = (
    "SELECT c.id, c.proveedor_id, p.razon_social AS proveedor_razon_social, c.tipo_comprobante, c.numero_comprobante, "
    "c.fecha, c.moneda, c.estado, c.motivo_anulacion, c.solicitud_id, c.base_imponible, c.igv, c.total, "
    "c.registrado_por, c.creado_en "
    "FROM compras.compra c JOIN compras.proveedor p ON p.id = c.proveedor_id "
)


def _compra_out(s: Session, filas_compra: list[dict]) -> list[dict]:
    if not filas_compra:
        return []
    ids = [c["id"] for c in filas_compra]
    lineas = filas(
        s,
        "SELECT d.compra_id, i.codigo AS insumo_codigo, i.descripcion, d.cantidad, d.unidad AS unidad_compra, d.precio_unitario "
        "FROM compras.compra_detalle d JOIN catalogo.insumo i ON i.id = d.insumo_id "
        "WHERE d.compra_id = ANY(:ids) ORDER BY d.id",
        ids=ids,
    )
    por_compra: dict[int, list[dict]] = {}
    for l in lineas:
        por_compra.setdefault(l.pop("compra_id"), []).append(l)
    out = []
    for c in filas_compra:
        c = dict(c)
        c["totales"] = {"base": c.pop("base_imponible"), "igv": c.pop("igv"), "total": c.pop("total")}
        c["lineas"] = por_compra.get(c["id"], [])
        out.append(c)
    return out


@compras.post("/compras", response_model=e.CompraOut, status_code=201)
def registrar_compra(datos: e.CompraIn, a: Actor = Depends(exigir("compra.registrar"))):
    """Totales los calcula el servidor desde las líneas (el cliente no manda números derivados).

    El motor sostiene: proveedor de baja, comprobante repetido del mismo proveedor, obra cerrada,
    solicitud ajena o anulada, y que total = base + IGV.
    """
    s = a.sesion
    codigos = [l.insumo_codigo.upper() for l in datos.lineas]
    insumos = {
        r["codigo"]: r["id"]
        for r in filas(s, "SELECT id, codigo FROM catalogo.insumo WHERE upper(codigo) = ANY(:c) AND activo", c=codigos)
    }
    faltan = [c for c in codigos if c not in {k.upper() for k in insumos}]
    if faltan:
        raise ErrorSigro(422, "insumo_no_existe", f"No existen en el catálogo (o están inactivos): {', '.join(faltan)}.")
    por_codigo = {k.upper(): v for k, v in insumos.items()}

    bruto = sum((l.cantidad * l.precio_unitario for l in datos.lineas), Decimal(0))
    if datos.precios_incluyen_igv:
        total = _redondear(bruto)
        base = _redondear(total / (1 + IGV))
        igv = total - base
    else:
        base = _redondear(bruto)
        igv = _redondear(base * IGV)
        total = base + igv

    c = fila(
        s,
        "INSERT INTO compras.compra (proyecto_id, proveedor_id, tipo_comprobante, numero_comprobante, fecha, moneda, "
        "base_imponible, igv, total, registrado_por, solicitud_id) "
        "VALUES (:p, :pv, :t, :n, :f, :m, :b, :i, :tt, :u, :sol) RETURNING id",
        p=a.obra_id, pv=datos.proveedor_id, t=datos.tipo_comprobante, n=datos.numero_comprobante.upper(), f=datos.fecha,
        m=datos.moneda, b=base, i=igv, tt=total, u=a.usuario_id, sol=datos.solicitud_id,
    )
    for l, codigo in zip(datos.lineas, codigos):
        s.execute(
            text(
                "INSERT INTO compras.compra_detalle (compra_id, insumo_id, cantidad, unidad, precio_unitario) "
                "VALUES (:c, :i, :q, :u, :pr)"
            ),
            {"c": c["id"], "i": por_codigo[codigo], "q": l.cantidad, "u": l.unidad_compra, "pr": l.precio_unitario},
        )
    return _compra_out(s, filas(s, _SQL_COMPRA + "WHERE c.id = :i", i=c["id"]))[0]


@compras.get("/compras", response_model=list[e.CompraOut])
def listar_compras(
    estado: str | None = Query(None, pattern="^(registrada|anulada)$"),
    proveedor_id: int | None = Query(None, ge=1),
    a: Actor = Depends(exigir("compra.consultar")),
):
    cabeceras = filas(
        a.sesion,
        _SQL_COMPRA + "WHERE c.proyecto_id = :p AND (CAST(:e AS text) IS NULL OR c.estado = :e) "
        "AND (CAST(:pv AS bigint) IS NULL OR c.proveedor_id = :pv) ORDER BY c.fecha DESC, c.id DESC",
        p=a.obra_id, e=estado, pv=proveedor_id,
    )
    return _compra_out(a.sesion, cabeceras)


@compras.get("/compras/{compra_id}", response_model=e.CompraOut)
def ver_compra(compra_id: int, a: Actor = Depends(exigir("compra.consultar"))):
    c = filas(a.sesion, _SQL_COMPRA + "WHERE c.id = :i AND c.proyecto_id = :p", i=compra_id, p=a.obra_id)
    if not c:
        raise ErrorSigro(404, "no_existe", "Esa compra no existe en esta obra.")
    return _compra_out(a.sesion, c)[0]


@compras.post("/compras/{compra_id}/anular", response_model=e.CompraOut)
def anular_compra(compra_id: int, datos: e.AnularIn, a: Actor = Depends(exigir("compra.registrar"))):
    """R10: una compra registrada no se edita ni se borra; se anula con su motivo y se registra la correcta."""
    previa = fila(
        a.sesion, "SELECT numero_comprobante FROM compras.compra WHERE id = :i AND proyecto_id = :p", i=compra_id, p=a.obra_id
    )
    if previa is None:
        raise ErrorSigro(404, "no_existe", "Esa compra no existe en esta obra.")
    a.sesion.execute(
        text(
            "UPDATE compras.compra SET estado = 'anulada', motivo_anulacion = :m, anulada_por = :u, anulada_en = now() "
            "WHERE id = :i"
        ),
        {"m": datos.motivo, "u": a.usuario_id, "i": compra_id},
    )
    eventos.publicar(
        a.sesion, "compra.anulada", a.obra_id,
        eventos.destinatarios_con_rol(a.sesion, a.obra_id, ["administrador", "residente"], excepto=a.usuario_id),
        "Compra anulada", f"{a.nombre} anuló la compra {previa['numero_comprobante']}: {datos.motivo}",
        {"compra_id": compra_id},
    )
    return _compra_out(a.sesion, filas(a.sesion, _SQL_COMPRA + "WHERE c.id = :i", i=compra_id))[0]


# ------------------------------------------------------------ solicitud de materiales
def _solicitud_out(s: Session, ids: list[int]) -> list[dict]:
    if not ids:
        return []
    cab = filas(
        s,
        "SELECT s.id, s.numero, s.fecha, s.motivo_anulacion, s.emitida_por, v.estado_derivado AS estado "
        "FROM compras.solicitud s JOIN compras.solicitud_estado v ON v.solicitud_id = s.id "
        "WHERE s.id = ANY(:ids) ORDER BY s.numero DESC",
        ids=ids,
    )
    lin = filas(
        s,
        "SELECT l.solicitud_id, i.codigo AS insumo_codigo, coalesce(i.descripcion, l.descripcion_libre) AS descripcion, "
        "l.unidad_compra, l.cantidad AS cantidad_solicitada, a.cantidad_comprada, a.cantidad_pendiente "
        "FROM compras.solicitud_linea l JOIN compras.solicitud_linea_avance a ON a.linea_id = l.id "
        "LEFT JOIN catalogo.insumo i ON i.id = l.insumo_id WHERE l.solicitud_id = ANY(:ids) ORDER BY l.id",
        ids=ids,
    )
    agrupadas: dict[int, list[dict]] = {}
    for l in lin:
        agrupadas.setdefault(l.pop("solicitud_id"), []).append(l)
    for c in cab:
        c["lineas"] = agrupadas.get(c["id"], [])
    return cab


@compras.post("/solicitudes", response_model=e.SolicitudOut, status_code=201)
def emitir_solicitud(datos: e.SolicitudIn, a: Actor = Depends(exigir("solicitud.emitir"))):
    """El número correlativo por obra lo asigna el motor, sin huecos y sin repetirse aunque lleguen dos a la vez."""
    s = a.sesion
    codigos = [l.insumo_codigo.upper() for l in datos.lineas if l.insumo_codigo]
    por_codigo = {
        r["codigo"].upper(): r["id"]
        for r in filas(s, "SELECT id, codigo FROM catalogo.insumo WHERE upper(codigo) = ANY(:c) AND activo", c=codigos)
    }
    faltan = [c for c in codigos if c not in por_codigo]
    if faltan:
        raise ErrorSigro(422, "insumo_no_existe", f"No existen en el catálogo (o están inactivos): {', '.join(faltan)}.")
    if len(set(codigos)) != len(codigos):
        raise ErrorSigro(422, "insumo_repetido", "Un insumo aparece dos veces; suma las cantidades en una sola línea.")
    sol = fila(s, "INSERT INTO compras.solicitud (proyecto_id, emitida_por) VALUES (:p, :u) RETURNING id", p=a.obra_id, u=a.usuario_id)
    for l in datos.lineas:
        s.execute(
            text(
                "INSERT INTO compras.solicitud_linea (solicitud_id, insumo_id, descripcion_libre, cantidad, unidad_compra) "
                "VALUES (:s, :i, :d, :q, :u)"
            ),
            {"s": sol["id"], "i": por_codigo[l.insumo_codigo.upper()] if l.insumo_codigo else None,
             "d": l.descripcion_libre, "q": l.cantidad, "u": l.unidad_compra},
        )
    numero = s.execute(text("SELECT numero FROM compras.solicitud WHERE id = :i"), {"i": sol["id"]}).scalar()
    eventos.publicar(
        s, "solicitud.emitida", a.obra_id, eventos.destinatarios_con_rol(s, a.obra_id, ["administrador"], excepto=a.usuario_id),
        "Nueva solicitud de materiales", f"{a.nombre} emitió la solicitud N.º {numero} con {len(datos.lineas)} línea(s).",
        {"solicitud_id": sol["id"], "numero": numero},
    )
    return _solicitud_out(s, [sol["id"]])[0]


@compras.get("/solicitudes", response_model=list[e.SolicitudOut])
def listar_solicitudes(a: Actor = Depends(exigir("solicitud.consultar"))):
    ids = [r["id"] for r in filas(a.sesion, "SELECT id FROM compras.solicitud WHERE proyecto_id = :p", p=a.obra_id)]
    return _solicitud_out(a.sesion, ids)


@compras.get("/solicitudes/{solicitud_id}", response_model=e.SolicitudOut)
def ver_solicitud(solicitud_id: int, a: Actor = Depends(exigir("solicitud.consultar"))):
    ok = fila(a.sesion, "SELECT id FROM compras.solicitud WHERE id = :i AND proyecto_id = :p", i=solicitud_id, p=a.obra_id)
    if ok is None:
        raise ErrorSigro(404, "no_existe", "Esa solicitud no existe en esta obra.")
    return _solicitud_out(a.sesion, [solicitud_id])[0]


@compras.post("/solicitudes/{solicitud_id}/anular", response_model=e.SolicitudOut)
def anular_solicitud(solicitud_id: int, datos: e.AnularIn, a: Actor = Depends(exigir("solicitud.emitir"))):
    if fila(a.sesion, "SELECT 1 AS x FROM compras.solicitud WHERE id = :i AND proyecto_id = :p", i=solicitud_id, p=a.obra_id) is None:
        raise ErrorSigro(404, "no_existe", "Esa solicitud no existe en esta obra.")
    a.sesion.execute(
        text(
            "UPDATE compras.solicitud SET estado = 'anulada', motivo_anulacion = :m, anulada_por = :u, anulada_en = now() WHERE id = :i"
        ),
        {"m": datos.motivo, "u": a.usuario_id, "i": solicitud_id},
    )
    return _solicitud_out(a.sesion, [solicitud_id])[0]


routers = [proveedores, compras]
