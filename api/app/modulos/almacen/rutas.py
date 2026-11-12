"""Servicio almacén (migración 0010): salidas, ingresos, recepciones, traslados, reversos, conteos, stock y kárdex.

Reglas del CLAUDE.md que se sostienen aquí y en el motor:
  R1  el movimiento es un libro: el servicio ni siquiera tiene permiso de UPDATE/DELETE; corregir es un reverso.
  R2  el saldo no es un campo: sale de las vistas `saldo`, `saldo_obra` y `kardex`; la respuesta trae el del servidor.
  R3  un reenvío con el mismo `uuid_cliente` no crea nada y devuelve el original (200).
  R4  la salida toma el promedio vigente de ESE almacén y lo congela (disparador, con candado por almacén e insumo).
  R5  la salida exige el celular autorizado de esa persona en esa obra (lo decide obras en /interno/autorizar).
  R9  rol y obra se verifican en el servidor en cada petición.
Lo ajeno se pide por API interna: almacenes y periodos a obras, insumos a catálogo, compras a compras. Lo que hace
falta para validar se guarda en réplicas locales (`almacen_ref`, `insumo_ref`).
"""
from __future__ import annotations

import json
from collections.abc import Iterable
from datetime import datetime, timezone
from decimal import Decimal
from uuid import UUID

from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy import text
from sqlalchemy.exc import DBAPIError
from sqlalchemy.orm import Session

from app import contexto, internos
from app.dependencias import Actor, exigir
from app.errores import ErrorSigro
from app.modulos.almacen import dominio as dom
from app.modulos.almacen import esquemas as e
from app.permisos import ACCIONES, TODOS
from app.sql import fila, filas

router = APIRouter(prefix="/obras/{obra_id}/almacen", tags=["almacén"])

CRITERIO = ("Costo promedio ponderado por almacén (NIC 2): cada ingreso recalcula el promedio del insumo en ese almacén; "
            "cada salida congela el promedio vigente en el instante en que se registró. Importes sin IGV.")

_COLS = ("m.id, m.uuid_cliente, m.tipo, m.sentido, m.proyecto_id, m.almacen_id, m.insumo_id, i.codigo AS insumo_codigo, "
         "i.unidad, m.cantidad, m.costo_unitario, round(m.cantidad * m.costo_unitario, 2) AS importe, m.moneda, m.motivo, "
         "m.quien_retira, m.frente, m.origen, m.origen_detalle, m.recepcion_id, m.traslado_id, m.conteo_id, m.revierte_a_id, "
         "m.periodo_id, m.dispositivo_id, m.registrado_por, m.fecha_dispositivo, m.fecha_servidor")


def _error(r: dom.ReglaAlmacen) -> ErrorSigro:
    return ErrorSigro(r.estado, r.codigo, r.mensaje)


# ------------------------------------------------------------------ lo que se pregunta a otros servicios
def _dispositivo(a: Actor, identificador: str | None, accion: str, obligatorio: bool = False) -> int | None:
    """Regla 5: obras dice si ese celular está ACTIVO para esta persona en esta obra (403 si no)."""
    if identificador is None:
        if obligatorio:
            raise ErrorSigro(403, "dispositivo_no_autorizado", "Este registro solo se acepta desde un celular autorizado.")
        return None
    r = internos.json_de(contexto.OBRAS, "POST", "/interno/autorizar", json={
        "usuario_id": a.usuario_id, "obra_id": a.obra_id, "roles": sorted(ACCIONES[accion]), "dispositivo": identificador})
    return r["dispositivo_id"]


def _asignado_en(a: Actor, obra_id: int) -> None:
    """Regla 9 para la obra de destino de un traslado: quien registra también debe estar asignado allí."""
    internos.json_de(contexto.OBRAS, "POST", "/interno/autorizar",
                     json={"usuario_id": a.usuario_id, "obra_id": obra_id, "roles": sorted(TODOS)})


def _almacen(s: Session, obra_id: int, almacen_id: int | None) -> dict:
    """Pide a obras los almacenes de la obra, refresca la réplica local y devuelve el pedido (activo)."""
    lista = internos.json_de(contexto.OBRAS, "GET", f"/interno/obras/{obra_id}/almacenes")
    for x in lista:
        s.execute(text(
            "INSERT INTO almacen.almacen_ref (id, proyecto_id, nombre, tipo, estado) VALUES (:i, :p, :n, :t, :e) "
            "ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, tipo = EXCLUDED.tipo, estado = EXCLUDED.estado, "
            "sincronizado_en = now()"), {"i": x["id"], "p": x["proyecto_id"], "n": x["nombre"], "t": x["tipo"], "e": x["estado"]})
    activos = [x for x in lista if x["estado"] == "activo"]
    if almacen_id is None:
        if len(activos) == 1:
            return activos[0]
        raise ErrorSigro(422, "almacen_requerido",
                         "Indica el almacén (almacen_id): la obra no tiene uno solo activo." if activos
                         else "La obra no tiene almacenes activos; regístralo primero.")
    elegido = next((x for x in activos if x["id"] == almacen_id), None)
    if elegido is None:
        raise ErrorSigro(404, "almacen_no_existe", "Ese almacén no existe o no está activo en esa obra.")
    return elegido


def _insumos(s: Session, ids: Iterable[int], para_registrar: bool = True) -> dict[int, dict]:
    """Datos del catálogo (código, unidades, factor, tipo) y réplica local. Lo que no existe no viene."""
    ids = sorted(set(ids))
    if not ids:
        return {}
    lista = internos.json_de(contexto.CATALOGO, "POST", "/interno/insumos/buscar", json={"ids": ids})
    salida = {x["id"]: x for x in lista}
    if para_registrar:
        faltan = [i for i in ids if i not in salida or not salida[i]["activo"]]
        if faltan:
            raise ErrorSigro(422, "insumo_no_existe", f"No existen en el catálogo (o están inactivos): {', '.join(map(str, faltan))}.")
        otros = [salida[i]["codigo"] for i in ids if salida[i]["tipo"] not in dom.FAMILIAS]
        if otros:
            raise ErrorSigro(422, "insumo_no_almacenable", f"Mano de obra y subcontratos no pasan por almacén: {', '.join(otros)}.")
        for x in (salida[i] for i in ids):
            s.execute(text(
                "INSERT INTO almacen.insumo_ref (id, codigo, descripcion, unidad, tipo, activo) VALUES (:i, :c, :d, :u, :t, :a) "
                "ON CONFLICT (id) DO UPDATE SET codigo = EXCLUDED.codigo, descripcion = EXCLUDED.descripcion, "
                "unidad = EXCLUDED.unidad, tipo = EXCLUDED.tipo, activo = EXCLUDED.activo, sincronizado_en = now()"),
                {"i": x["id"], "c": x["codigo"], "d": x["descripcion"], "u": x["unidad_s10"], "t": x["tipo"], "a": x["activo"]})
    return salida


def _periodo(obra_id: int, hora_ms: int | None, es_reverso: bool, dia=None) -> int | None:
    """D4: el periodo lo fija el servidor según el día del hecho; uno cerrado solo admite reversos."""
    periodos = internos.json_de(contexto.OBRAS, "GET", f"/interno/obras/{obra_id}/periodos")
    p = dom.periodo_de(periodos, dia or dom.fecha_del_hecho(hora_ms, datetime.now(timezone.utc)))
    try:
        dom.exigir_periodo_abierto(p, es_reverso)
    except dom.ReglaAlmacen as r:
        raise _error(r)
    return p["id"] if p else None


def _fecha_dispositivo(hora_ms: int | None) -> datetime | None:
    return datetime.fromtimestamp(hora_ms / 1000, tz=timezone.utc) if hora_ms is not None else None


# ------------------------------------------------------------------ el libro
def _movimiento(s: Session, mid: int) -> dict:
    m = fila(s, f"SELECT {_COLS}, (SELECT sum(x.sentido * x.cantidad) FROM almacen.movimiento x "
                "WHERE x.almacen_id = m.almacen_id AND x.insumo_id = m.insumo_id AND x.id <= m.id) AS saldo "
                "FROM almacen.movimiento m JOIN almacen.insumo_ref i ON i.id = m.insumo_id WHERE m.id = :i", i=mid)
    return m


def _por_uuid(s: Session, obra_id: int, uuid: UUID, tipos: set[str]) -> dict | None:
    m = fila(s, "SELECT id, proyecto_id, tipo FROM almacen.movimiento WHERE uuid_cliente = :u", u=str(uuid))
    if m is None:
        return None
    if m["proyecto_id"] != obra_id or m["tipo"] not in tipos:
        raise ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otro movimiento.")
    return _movimiento(s, m["id"])


class _Duplicado(Exception):
    pass


def _insertar(s: Session, **m) -> dict:
    """Inserta UN movimiento en su propio punto de guardado. El disparador fija la hora del servidor, valida el stock y
    congela el costo; aquí solo se traducen sus rechazos."""
    m.setdefault("costo_unitario", Decimal(0))
    m["uuid_cliente"] = str(m["uuid_cliente"])
    cols = ", ".join(m)
    try:
        with s.begin_nested():
            mid = s.execute(text(f"INSERT INTO almacen.movimiento ({cols}) VALUES ({', '.join(':' + k for k in m)}) RETURNING id"),
                            m).scalar()
    except DBAPIError as ex:
        diag = getattr(ex.orig, "diag", None)
        restriccion = getattr(diag, "constraint_name", None)
        mensaje = getattr(diag, "message_primary", "") or ""
        if restriccion == "uq_movimiento_uuid_cliente":
            raise _Duplicado() from ex
        if mensaje.startswith("SIGRO-A1"):
            raise ErrorSigro(422, "stock_insuficiente", mensaje.split(": ", 1)[1].capitalize() + ".") from ex
        if mensaje.startswith("SIGRO-A4"):
            raise ErrorSigro(409, "reverso_de_reverso", "Un reverso no se revierte: registra el movimiento correcto.") from ex
        if restriccion == "uq_movimiento_revierte_a":
            raise ErrorSigro(409, "ya_revertido", "Ese movimiento ya fue revertido.") from ex
        raise
    return _movimiento(s, mid)


def _publicar(s: Session, tipo: str, obra_id: int, movimientos: list[dict]) -> None:
    """Hechos ya valorizados hacia costo, en la MISMA transacción (outbox, destino 'integracion', D2)."""
    if not movimientos:
        return
    ids = [m["id"] for m in movimientos]
    rows = filas(s, "SELECT m.id, m.proyecto_id, m.tipo, coalesce(o.tipo, m.tipo) AS tipo_efectivo, m.sentido, m.cantidad, "
                    "m.costo_unitario, m.moneda, (m.fecha_servidor AT TIME ZONE 'America/Lima')::date AS fecha, "
                    "i.codigo, i.unidad, i.tipo AS familia "
                    "FROM almacen.movimiento m JOIN almacen.insumo_ref i ON i.id = m.insumo_id "
                    "LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id WHERE m.id = ANY(:ids) ORDER BY m.id", ids=ids)
    hechos = [{"proyecto_id": r["proyecto_id"], "familia": dom.FAMILIAS[r["familia"]], "clase": dom.CLASE[r["tipo_efectivo"]],
               "insumo_codigo": r["codigo"], "cantidad": str(r["sentido"] * r["cantidad"]), "unidad": r["unidad"],
               "costo_unitario": str(r["costo_unitario"]), "moneda": r["moneda"], "fecha": r["fecha"].isoformat(),
               "referencia": f"almacen.movimiento:{r['id']}"} for r in rows]
    s.execute(text("INSERT INTO outbox.evento (tipo, proyecto_id, payload, destino) VALUES (:t, :p, CAST(:j AS jsonb), 'integracion') "
                   "RETURNING id"), {"t": tipo, "p": obra_id, "j": json.dumps({"hechos": hechos}, ensure_ascii=False)})


def _registrar_uno(s: Session, respuesta: Response, obra_id: int, tipo_evento: str, **m) -> dict:
    """Un movimiento simple, idempotente aunque dos reenvíos lleguen a la vez."""
    try:
        nuevo = _insertar(s, **m)
    except _Duplicado:
        respuesta.status_code = 200
        return _por_uuid(s, obra_id, m["uuid_cliente"], {m["tipo"]})
    _publicar(s, tipo_evento, obra_id, [nuevo])
    return nuevo


# ------------------------------------------------------------------ HU-40 salida
@router.post("/salidas", response_model=e.MovimientoOut, status_code=201)
def registrar_salida(d: e.SalidaIn, respuesta: Response, a: Actor = Depends(exigir("salida.registrar"))):
    """Exige almacén, insumo, cantidad, motivo y quién retira; el frente es opcional. Nunca más de lo disponible en ese
    almacén. El costo lo pone el servidor: el promedio vigente en ese instante, congelado. Reenvío ⇒ 200 con el original."""
    s = a.sesion
    previo = _por_uuid(s, a.obra_id, d.uuid_cliente, {"salida"})
    if previo is not None:
        respuesta.status_code = 200
        return previo
    disp = _dispositivo(a, d.dispositivo, "salida.registrar", obligatorio=True)
    alm = _almacen(s, a.obra_id, d.almacen_id)
    ins = _insumos(s, [d.insumo_id])[d.insumo_id]
    try:
        cantidad = dom.a_unidad_s10(d.cantidad, d.unidad, ins)
    except dom.ReglaAlmacen as r:
        raise _error(r)
    return _registrar_uno(
        s, respuesta, a.obra_id, "almacen.salida_registrada",
        uuid_cliente=d.uuid_cliente, proyecto_id=a.obra_id, almacen_id=alm["id"], insumo_id=d.insumo_id, tipo="salida",
        sentido=-1, cantidad=cantidad, motivo=d.motivo, quien_retira=d.quien_retira, frente=d.frente or None,
        periodo_id=_periodo(a.obra_id, d.hora_dispositivo_ms, False), dispositivo_id=disp, registrado_por=a.usuario_id,
        fecha_dispositivo=_fecha_dispositivo(d.hora_dispositivo_ms))


# ------------------------------------------------------------------ HU-41 ingreso sin compra
@router.post("/ingresos", response_model=e.MovimientoOut, status_code=201)
def registrar_ingreso(d: e.IngresoIn, respuesta: Response, a: Actor = Depends(exigir("ingreso.registrar"))):
    """Origen declarado y costo unitario obligatorios; queda distinguible (tipo `ingreso_sin_compra`) de una recepción.
    **Unidad del costo**: va en la misma unidad que la cantidad (`unidad`; si se omite, la del S10). Ej. acero: 100 var a
    S/ 35.40 la varilla → el libro guarda 712 kg a S/ 4.971910 el kg; 712 kg a S/ 4.97 el kg es lo mismo, sin factor.
    «Saldo de otra obra» solo si esa obra no está en SIGRO (D5): si está, se usa el traslado (HU-42)."""
    s = a.sesion
    previo = _por_uuid(s, a.obra_id, d.uuid_cliente, {"ingreso_sin_compra"})
    if previo is not None:
        respuesta.status_code = 200
        return previo
    if d.origen == "saldo_otra_obra":
        existentes = internos.json_de(contexto.OBRAS, "GET", "/interno/obras", params={"nombre": d.origen_detalle})
        if existentes:
            raise ErrorSigro(422, "usar_traslado", f"La obra «{existentes[0]['nombre']}» está registrada en SIGRO: "
                                                   "registra un traslado desde esa obra en vez de un ingreso sin compra.")
    disp = _dispositivo(a, d.dispositivo, "ingreso.registrar")
    alm = _almacen(s, a.obra_id, d.almacen_id)
    ins = _insumos(s, [d.insumo_id])[d.insumo_id]
    try:
        cantidad = dom.a_unidad_s10(d.cantidad, d.unidad, ins)
        costo = dom.a_costo_s10(d.costo_unitario, d.unidad, ins)          # el costo sigue a la cantidad: misma unidad
    except dom.ReglaAlmacen as r:
        raise _error(r)
    return _registrar_uno(
        s, respuesta, a.obra_id, "almacen.ingreso_registrado",
        uuid_cliente=d.uuid_cliente, proyecto_id=a.obra_id, almacen_id=alm["id"], insumo_id=d.insumo_id,
        tipo="ingreso_sin_compra", sentido=1, cantidad=cantidad, costo_unitario=costo, origen=d.origen,
        origen_detalle=d.origen_detalle, periodo_id=_periodo(a.obra_id, d.hora_dispositivo_ms, False), dispositivo_id=disp,
        registrado_por=a.usuario_id, fecha_dispositivo=_fecha_dispositivo(d.hora_dispositivo_ms))


# ------------------------------------------------------------------ HU-45 reverso
@router.post("/movimientos/{movimiento_id}/reverso", response_model=e.MovimientoOut, status_code=201)
def revertir(movimiento_id: int, d: e.ReversoIn, respuesta: Response, a: Actor = Depends(exigir("movimiento.revertir"))):
    """Corregir = un movimiento inverso que apunta al original, con autor, fecha y motivo. El original no se toca; un
    original se revierte una sola vez y un reverso no se revierte. Se admite en un periodo cerrado (D4)."""
    s = a.sesion
    previo = _por_uuid(s, a.obra_id, d.uuid_cliente, {"reverso"})
    if previo is not None:
        respuesta.status_code = 200
        return previo
    o = fila(s, "SELECT * FROM almacen.movimiento WHERE id = :i AND proyecto_id = :p", i=movimiento_id, p=a.obra_id)
    if o is None:
        raise ErrorSigro(404, "no_existe", "Ese movimiento no existe en esta obra.")
    if o["tipo"] in ("traslado_salida", "traslado_ingreso"):
        raise ErrorSigro(409, "usar_anular_traslado", "Un traslado se anula entero (sus dos movimientos), no por una mitad.")
    disp = _dispositivo(a, d.dispositivo, "movimiento.revertir")
    return _registrar_uno(
        s, respuesta, a.obra_id, "almacen.movimiento_revertido",
        uuid_cliente=d.uuid_cliente, proyecto_id=a.obra_id, almacen_id=o["almacen_id"], insumo_id=o["insumo_id"], tipo="reverso",
        sentido=-o["sentido"], cantidad=o["cantidad"], costo_unitario=o["costo_unitario"], motivo=d.motivo,
        revierte_a_id=o["id"], periodo_id=_periodo(a.obra_id, d.hora_dispositivo_ms, True), dispositivo_id=disp,
        registrado_por=a.usuario_id, fecha_dispositivo=_fecha_dispositivo(d.hora_dispositivo_ms))


# ------------------------------------------------------------------ HU-35 recepción de compra
def _estado_recepcion(s: Session, compra: dict, insumos: dict[int, dict]) -> list[dict]:
    recibido = {r["insumo_id"]: r["recibido"] for r in filas(
        s, "SELECT insumo_id, recibido FROM almacen.recibido_por_compra WHERE compra_id = :c", c=compra["id"])}
    salida = []
    for l in compra["lineas"]:
        factor = Decimal(str(insumos[l["insumo_id"]]["factor_conversion"]))
        pedido = Decimal(str(l["cantidad"])).quantize(dom.D4)
        rec = (recibido.get(l["insumo_id"], Decimal(0)) / factor).quantize(dom.D4)
        salida.append({"insumo_id": l["insumo_id"], "unidad_compra": l["unidad"], "pedido": pedido, "recibido": rec,
                       "pendiente": max(pedido - rec, Decimal(0))})
    return salida


def _compra_de_la_obra(a: Actor, compra_id: int) -> dict:
    c = internos.json_de(contexto.COMPRAS, "GET", f"/interno/compras/{compra_id}")
    if c["proyecto_id"] != a.obra_id:
        raise ErrorSigro(404, "compra_no_existe", "Esa compra no existe en esta obra.")
    return c


def _recepcion_out(s: Session, rec: dict, compra: dict, insumos: dict[int, dict], advertencias=()) -> dict:
    movs = [_movimiento(s, r["id"]) for r in filas(s, "SELECT id FROM almacen.movimiento WHERE recepcion_id = :r "
                                                     "AND tipo = 'ingreso_compra' ORDER BY id", r=rec["id"])]
    return {"id": rec["id"], "uuid_cliente": rec["uuid_cliente"], "compra_id": compra["id"], "movimientos": movs,
            "lineas": _estado_recepcion(s, compra, insumos), "advertencias": list(advertencias)}


def _pedido(estado: dict[int, dict], insumos: dict[int, dict], lineas) -> dict[int, Decimal]:
    """Qué se va a recibir, en unidad de compra: lo pendiente si no hay líneas (recepción total), o lo que pide cada línea.
    Las reglas de HU-35 (no recibir más de lo comprado, sin repetir insumos) viven aquí para la recepción y su verificación."""
    if lineas is None:
        pedir = {i: x["pendiente"] for i, x in estado.items() if x["pendiente"] > 0}
        if not pedir:
            raise ErrorSigro(409, "compra_ya_recibida", "Esa compra ya se recibió completa.")
        return pedir
    pedir: dict[int, Decimal] = {}
    for l in lineas:
        if l.insumo_id not in estado:
            raise ErrorSigro(422, "insumo_no_comprado", f"El insumo {l.insumo_id} no está en esa compra.")
        if l.insumo_id in pedir:
            raise ErrorSigro(422, "datos_invalidos", "Un insumo aparece dos veces en la recepción.")
        if l.cantidad > estado[l.insumo_id]["pendiente"]:
            x = estado[l.insumo_id]
            raise ErrorSigro(422, "recepcion_excede_compra", f"{insumos[l.insumo_id]['codigo']}: se compró {x['pedido']} "
                             f"{x['unidad_compra']}, ya se recibió {x['recibido']} y quedan {x['pendiente']}; no se puede recibir {l.cantidad}.")
        pedir[l.insumo_id] = l.cantidad
    return pedir


def _verificar(s: Session, obra_id: int, insumos: dict[int, dict], pedir: dict[int, Decimal]) -> list[dict]:
    """HU-36: por insumo, lo requerido por el S10, lo ya recibido en la obra (de todas las compras, neto de reversos) y cuánto
    pasaría con esta recepción. El servidor lo calcula siempre; la app solo avisa con lo que él devuelve (regla 2)."""
    requerido = {r["insumo_id"]: Decimal(str(r["requerido"])) for r in
                 internos.json_de(contexto.COMPRAS, "GET", f"/interno/obras/{obra_id}/requerido")}
    ya = {r["insumo_id"]: r["recibido"] for r in filas(
        s, """SELECT m.insumo_id, coalesce(sum(m.sentido * m.cantidad), 0) AS recibido
                FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
               WHERE m.proyecto_id = :p AND m.insumo_id = ANY(:ids) AND coalesce(o.tipo, m.tipo) = 'ingreso_compra'
               GROUP BY m.insumo_id""", p=obra_id, ids=list(pedir))}
    salida = []
    for insumo_id, cant in pedir.items():
        ins = insumos[insumo_id]
        ahora = (cant * Decimal(str(ins["factor_conversion"]))).quantize(dom.D4)      # a la unidad del S10
        antes = Decimal(str(ya.get(insumo_id, 0))).quantize(dom.D4)
        req = requerido.get(insumo_id)
        exceso = dom.exceso_sobre_requerido(req, antes, ahora)
        salida.append({"insumo_id": insumo_id, "insumo_codigo": ins["codigo"], "unidad": ins["unidad_s10"],
                       "requerido": req.quantize(dom.D4) if req is not None else None, "ya_recibido": antes,
                       "con_esta": antes + ahora, "exceso": exceso, "excede": bool(exceso and exceso > 0)})
    return salida


@router.post("/recepciones/verificacion", response_model=e.VerificacionOut)
def verificar_recepcion(d: e.VerificacionIn, a: Actor = Depends(exigir("recepcion.registrar"))):
    """HU-36: avisa ANTES de guardar si lo que se va a recibir pasa lo requerido. No guarda nada; avisa, no bloquea (regla 7):
    si hay exceso, `POST /recepciones` pide `motivo_exceso`."""
    s = a.sesion
    compra = _compra_de_la_obra(a, d.compra_id)
    insumos = _insumos(s, [l["insumo_id"] for l in compra["lineas"]], para_registrar=False)
    estado = {x["insumo_id"]: x for x in _estado_recepcion(s, compra, insumos)}
    lineas = _verificar(s, a.obra_id, insumos, _pedido(estado, insumos, d.lineas))
    return {"compra_id": compra["id"], "hay_exceso": any(x["excede"] for x in lineas), "lineas": lineas}


@router.post("/recepciones", response_model=e.RecepcionOut, status_code=201)
def registrar_recepcion(d: e.RecepcionIn, respuesta: Response, a: Actor = Depends(exigir("recepcion.registrar"))):
    """Total o parcial; varias recepciones hasta completar lo comprado y nunca más de lo comprado de cada insumo. Cada
    línea genera su ingreso al costo de la compra sin IGV. Sin líneas se recibe todo lo pendiente (caso habitual)."""
    s = a.sesion
    previa = fila(s, "SELECT id, uuid_cliente, proyecto_id, compra_id FROM almacen.recepcion WHERE uuid_cliente = :u", u=str(d.uuid_cliente))
    compra = _compra_de_la_obra(a, d.compra_id)
    insumos = _insumos(s, [l["insumo_id"] for l in compra["lineas"]], para_registrar=previa is None)
    if previa is not None:
        if previa["proyecto_id"] != a.obra_id or previa["compra_id"] != d.compra_id:
            raise ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otra recepción.")
        respuesta.status_code = 200
        return _recepcion_out(s, previa, compra, insumos)
    if compra["estado"] != "registrada":
        raise ErrorSigro(409, "compra_anulada", "Esa compra está anulada: no se puede recibir.")
    disp = _dispositivo(a, d.dispositivo, "recepcion.registrar")
    alm = _almacen(s, a.obra_id, d.almacen_id)
    s.execute(text("SELECT pg_advisory_xact_lock(hashtext('sigro.recepcion'), CAST(:c AS int))"), {"c": d.compra_id})
    estado = {x["insumo_id"]: x for x in _estado_recepcion(s, compra, insumos)}
    pedir = _pedido(estado, insumos, d.lineas)
    verificacion = _verificar(s, a.obra_id, insumos, pedir)
    advertencias: list[str] = []
    if any(x["excede"] for x in verificacion):
        codigos = ", ".join(x["insumo_codigo"] for x in verificacion if x["excede"])
        if not (d.motivo_exceso and d.motivo_exceso.strip()):
            raise ErrorSigro(422, "exceso_sin_motivo", f"Con esta recepción se pasa lo requerido ({codigos}): indica el motivo del exceso "
                             "o recibe solo hasta lo requerido.")
        advertencias.append(f"Se recibió más de lo requerido ({codigos}); queda el motivo en el kárdex.")
    periodo = _periodo(a.obra_id, d.hora_dispositivo_ms, False)
    rec = fila(s, "INSERT INTO almacen.recepcion (uuid_cliente, proyecto_id, almacen_id, compra_id, motivo_exceso, registrado_por, "
                  "dispositivo_id, fecha_dispositivo) VALUES (:u, :p, :a, :c, :m, :r, :d, :f) RETURNING id, uuid_cliente",
               u=str(d.uuid_cliente), p=a.obra_id, a=alm["id"], c=d.compra_id, m=d.motivo_exceso, r=a.usuario_id, d=disp,
               f=_fecha_dispositivo(d.hora_dispositivo_ms))
    lineas = {l["insumo_id"]: l for l in compra["lineas"]}
    nuevos = []
    for insumo_id, cant in pedir.items():
        ins, l = insumos[insumo_id], lineas[insumo_id]
        factor = Decimal(str(ins["factor_conversion"]))
        nuevos.append(_insertar(
            s, uuid_cliente=dom.uuid_derivado(d.uuid_cliente, "recepcion", insumo_id), proyecto_id=a.obra_id, almacen_id=alm["id"],
            insumo_id=insumo_id, tipo="ingreso_compra", sentido=1, cantidad=(cant * factor).quantize(dom.D4),
            costo_unitario=dom.costo_recepcion(Decimal(str(l["precio_unitario"])), Decimal(str(compra["factor_sin_igv"])), factor),
            recepcion_id=rec["id"], periodo_id=periodo, dispositivo_id=disp, registrado_por=a.usuario_id,
            fecha_dispositivo=_fecha_dispositivo(d.hora_dispositivo_ms)))
    _publicar(s, "almacen.recepcion_registrada", a.obra_id, nuevos)
    return _recepcion_out(s, rec, compra, insumos, advertencias)


@router.get("/compras/{compra_id}/recepcion", response_model=list[e.RecepcionLineaOut])
def estado_recepcion(compra_id: int, a: Actor = Depends(exigir("stock.consultar"))):
    """HU-35: pedido, recibido y pendiente por insumo (en unidad de compra)."""
    compra = _compra_de_la_obra(a, compra_id)
    return _estado_recepcion(a.sesion, compra, _insumos(a.sesion, [l["insumo_id"] for l in compra["lineas"]], para_registrar=False))


# ------------------------------------------------------------------ HU-42 traslado
def _traslado_out(s: Session, t: dict) -> dict:
    movs = [_movimiento(s, r["id"]) for r in filas(s, "SELECT id FROM almacen.movimiento WHERE traslado_id = :t ORDER BY id", t=t["id"])]
    return {"id": t["id"], "uuid_cliente": t["uuid_cliente"], "movimientos": movs}


@router.post("/traslados", response_model=e.OperacionOut, status_code=201)
def registrar_traslado(d: e.TrasladoIn, respuesta: Response, a: Actor = Depends(exigir("traslado.registrar"))):
    """Solo hasta el stock del origen. Genera una salida en origen y un ingreso en destino que comparten el id del
    traslado; el costo unitario viaja con el material (lo copia el motor)."""
    s = a.sesion
    previo = fila(s, "SELECT id, uuid_cliente, origen_proyecto_id FROM almacen.traslado WHERE uuid_cliente = :u", u=str(d.uuid_cliente))
    if previo is not None:
        if previo["origen_proyecto_id"] != a.obra_id:
            raise ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otro traslado.")
        respuesta.status_code = 200
        return _traslado_out(s, previo)
    destino_obra = d.destino_obra_id or a.obra_id
    if destino_obra != a.obra_id:
        _asignado_en(a, destino_obra)
    disp = _dispositivo(a, d.dispositivo, "traslado.registrar")
    origen = _almacen(s, a.obra_id, d.origen_almacen_id)
    destino = _almacen(s, destino_obra, d.destino_almacen_id)
    if origen["id"] == destino["id"]:
        raise ErrorSigro(422, "traslado_al_mismo_almacen", "El almacén de destino debe ser distinto del de origen.")
    _insumos(s, [l.insumo_id for l in d.lineas])
    per_o = _periodo(a.obra_id, d.hora_dispositivo_ms, False)
    per_d = per_o if destino_obra == a.obra_id else _periodo(destino_obra, d.hora_dispositivo_ms, False)
    t = fila(s, "INSERT INTO almacen.traslado (uuid_cliente, origen_proyecto_id, origen_almacen_id, destino_proyecto_id, "
                "destino_almacen_id, motivo, registrado_por) VALUES (:u, :op, :oa, :dp, :da, :m, :r) RETURNING id, uuid_cliente",
             u=str(d.uuid_cliente), op=a.obra_id, oa=origen["id"], dp=destino_obra, da=destino["id"], m=d.motivo, r=a.usuario_id)
    nuevos = []
    comunes = dict(traslado_id=t["id"], motivo=d.motivo, dispositivo_id=disp, registrado_por=a.usuario_id,
                   fecha_dispositivo=_fecha_dispositivo(d.hora_dispositivo_ms))
    for l in d.lineas:
        nuevos.append(_insertar(s, uuid_cliente=dom.uuid_derivado(d.uuid_cliente, "salida", l.insumo_id), proyecto_id=a.obra_id,
                                almacen_id=origen["id"], insumo_id=l.insumo_id, tipo="traslado_salida", sentido=-1,
                                cantidad=l.cantidad, periodo_id=per_o, **comunes))
        nuevos.append(_insertar(s, uuid_cliente=dom.uuid_derivado(d.uuid_cliente, "ingreso", l.insumo_id), proyecto_id=destino_obra,
                                almacen_id=destino["id"], insumo_id=l.insumo_id, tipo="traslado_ingreso", sentido=1,
                                cantidad=l.cantidad, periodo_id=per_d, **comunes))
    _publicar(s, "almacen.traslado_registrado", a.obra_id, nuevos)
    return _traslado_out(s, t)


@router.post("/traslados/{traslado_id}/anular", response_model=e.OperacionOut, status_code=201)
def anular_traslado(traslado_id: int, d: e.AnularIn, respuesta: Response, a: Actor = Depends(exigir("traslado.registrar"))):
    """Anular = dos reversos por insumo (primero el ingreso en destino, luego la salida en origen), nunca un borrado."""
    s = a.sesion
    t = fila(s, "SELECT id, uuid_cliente, origen_proyecto_id, destino_proyecto_id FROM almacen.traslado WHERE id = :i AND origen_proyecto_id = :p",
             i=traslado_id, p=a.obra_id)
    if t is None:
        raise ErrorSigro(404, "no_existe", "Ese traslado no existe en esta obra.")
    movs = filas(s, "SELECT m.*, r.id AS revertido_por FROM almacen.movimiento m LEFT JOIN almacen.movimiento r ON r.revierte_a_id = m.id "
                    "WHERE m.traslado_id = :t AND m.tipo IN ('traslado_salida', 'traslado_ingreso') "
                    "ORDER BY (m.tipo = 'traslado_salida'), m.id", t=traslado_id)
    ya = fila(s, "SELECT id FROM almacen.movimiento WHERE uuid_cliente = :u", u=str(dom.uuid_derivado(d.uuid_cliente, "reverso", movs[0]["id"])))
    if ya is not None:
        respuesta.status_code = 200
        return {"id": t["id"], "uuid_cliente": t["uuid_cliente"],
                "movimientos": [_movimiento(s, r["id"]) for r in filas(s, "SELECT id FROM almacen.movimiento WHERE traslado_id = :t "
                                                                       "AND tipo = 'reverso' ORDER BY id", t=traslado_id)]}
    if any(m["revertido_por"] for m in movs):
        raise ErrorSigro(409, "ya_revertido", "Ese traslado ya fue anulado.")
    if t["destino_proyecto_id"] != a.obra_id:
        _asignado_en(a, t["destino_proyecto_id"])
    disp = _dispositivo(a, d.dispositivo, "traslado.registrar")
    nuevos = []
    for m in movs:
        nuevos.append(_insertar(
            s, uuid_cliente=dom.uuid_derivado(d.uuid_cliente, "reverso", m["id"]), proyecto_id=m["proyecto_id"],
            almacen_id=m["almacen_id"], insumo_id=m["insumo_id"], tipo="reverso", sentido=-m["sentido"], cantidad=m["cantidad"],
            costo_unitario=m["costo_unitario"], motivo=d.motivo, revierte_a_id=m["id"],
            periodo_id=_periodo(m["proyecto_id"], d.hora_dispositivo_ms, True), dispositivo_id=disp, registrado_por=a.usuario_id,
            fecha_dispositivo=_fecha_dispositivo(d.hora_dispositivo_ms)))
    _publicar(s, "almacen.traslado_anulado", a.obra_id, nuevos)
    return {"id": t["id"], "uuid_cliente": t["uuid_cliente"], "movimientos": nuevos}


# ------------------------------------------------------------------ HU-46 conteo físico
def _conteo(s: Session, obra_id: int, conteo_id: int) -> dict:
    c = fila(s, "SELECT id, uuid_cliente, almacen_id, fecha, estado, registrado_por, confirmado_por, confirmado_en "
                "FROM almacen.conteo WHERE id = :i AND proyecto_id = :p", i=conteo_id, p=obra_id)
    if c is None:
        raise ErrorSigro(404, "no_existe", "Ese conteo no existe en esta obra.")
    c["lineas"] = filas(s, "SELECT insumo_id, saldo_teorico, cantidad_contada, diferencia, motivo FROM almacen.conteo_linea "
                           "WHERE conteo_id = :i ORDER BY id", i=conteo_id)
    c["ajustes"] = [_movimiento(s, r["id"]) for r in filas(s, "SELECT id FROM almacen.movimiento WHERE conteo_id = :i ORDER BY id", i=conteo_id)]
    return c


@router.post("/conteos", response_model=e.ConteoOut, status_code=201)
def abrir_conteo(d: e.ConteoIn, respuesta: Response, a: Actor = Depends(exigir("conteo.registrar"))):
    """El conteo se registra por almacén y fecha. Uno abierto (borrador) a la vez por almacén."""
    s = a.sesion
    previo = fila(s, "SELECT id, proyecto_id FROM almacen.conteo WHERE uuid_cliente = :u", u=str(d.uuid_cliente))
    if previo is not None:
        if previo["proyecto_id"] != a.obra_id:
            raise ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otro conteo.")
        respuesta.status_code = 200
        return _conteo(s, a.obra_id, previo["id"])
    _dispositivo(a, d.dispositivo, "conteo.registrar")
    alm = _almacen(s, a.obra_id, d.almacen_id)
    abierto = fila(s, "SELECT id FROM almacen.conteo WHERE almacen_id = :a AND estado = 'borrador'", a=alm["id"])
    if abierto is not None:
        raise ErrorSigro(409, "conteo_abierto", f"Ese almacén ya tiene un conteo abierto (#{abierto['id']}): confírmalo primero.")
    c = fila(s, "INSERT INTO almacen.conteo (uuid_cliente, proyecto_id, almacen_id, fecha, registrado_por) "
                "VALUES (:u, :p, :a, :f, :r) RETURNING id", u=str(d.uuid_cliente), p=a.obra_id, a=alm["id"], f=d.fecha, r=a.usuario_id)
    return _conteo(s, a.obra_id, c["id"])


@router.post("/conteos/{conteo_id}/lineas", response_model=e.ConteoOut, status_code=201)
def contar(conteo_id: int, d: e.ConteoLineaIn, a: Actor = Depends(exigir("conteo.registrar"))):
    """El saldo teórico lo pone el servidor (R2); toda diferencia exige su motivo antes de confirmarse."""
    s = a.sesion
    c = _conteo(s, a.obra_id, conteo_id)
    if c["estado"] != "borrador":
        raise ErrorSigro(409, "conteo_confirmado", "Ese conteo ya está confirmado; registra un conteo nuevo.")
    if any(l["insumo_id"] == d.insumo_id for l in c["lineas"]):
        raise ErrorSigro(409, "insumo_ya_contado", "Ese insumo ya se contó en este conteo.")
    _insumos(s, [d.insumo_id])
    try:
        with s.begin_nested():
            s.execute(text("INSERT INTO almacen.conteo_linea (conteo_id, insumo_id, saldo_teorico, cantidad_contada, motivo) "
                           "VALUES (:c, :i, 0, :q, :m)"), {"c": conteo_id, "i": d.insumo_id, "q": d.cantidad_contada, "m": d.motivo or None})
    except DBAPIError as ex:
        if getattr(getattr(ex.orig, "diag", None), "constraint_name", None) == "ck_conteo_linea_motivo":
            teorico = fila(s, "SELECT coalesce(sum(sentido * cantidad), 0) AS q FROM almacen.movimiento WHERE almacen_id = :a AND insumo_id = :i",
                           a=c["almacen_id"], i=d.insumo_id)["q"]
            raise ErrorSigro(422, "diferencia_sin_motivo", f"El sistema tiene {teorico} y se contó {d.cantidad_contada}: "
                                                           "toda diferencia exige un motivo.") from ex
        raise
    return _conteo(s, a.obra_id, conteo_id)


@router.get("/conteos/{conteo_id}", response_model=e.ConteoOut)
def ver_conteo(conteo_id: int, a: Actor = Depends(exigir("stock.consultar"))):
    """Saldo teórico, contado y diferencia por insumo."""
    return _conteo(a.sesion, a.obra_id, conteo_id)


@router.post("/conteos/{conteo_id}/confirmar", response_model=e.ConteoOut)
def confirmar_conteo(conteo_id: int, a: Actor = Depends(exigir("conteo.registrar"))):
    """Un movimiento de ajuste por cada diferencia, al costo promedio vigente. Si el saldo cambió desde que se contó,
    no se ajusta a ciegas: hay que contar de nuevo. Confirmar dos veces devuelve lo mismo."""
    s = a.sesion
    c = _conteo(s, a.obra_id, conteo_id)
    if c["estado"] == "confirmado":
        return c
    periodo = _periodo(a.obra_id, None, False, dia=c["fecha"])
    nuevos = []
    for l in c["lineas"]:
        if l["diferencia"] == 0:
            continue
        s.execute(text("SELECT pg_advisory_xact_lock(CAST(:a AS int), CAST(:i AS int))"), {"a": c["almacen_id"], "i": l["insumo_id"]})
        actual = fila(s, "SELECT coalesce(sum(sentido * cantidad), 0) AS q FROM almacen.movimiento WHERE almacen_id = :a AND insumo_id = :i",
                      a=c["almacen_id"], i=l["insumo_id"])["q"]
        if actual != l["saldo_teorico"]:
            raise ErrorSigro(409, "saldo_cambio", f"El saldo del insumo {l['insumo_id']} cambió desde el conteo "
                                                  f"({l['saldo_teorico']} → {actual}): registra un conteo nuevo.")
        nuevos.append(_insertar(
            s, uuid_cliente=dom.uuid_derivado(c["uuid_cliente"], "ajuste", l["insumo_id"]), proyecto_id=a.obra_id,
            almacen_id=c["almacen_id"], insumo_id=l["insumo_id"], tipo="ajuste_conteo", sentido=1 if l["diferencia"] > 0 else -1,
            cantidad=abs(l["diferencia"]), motivo=l["motivo"], conteo_id=conteo_id, periodo_id=periodo, registrado_por=a.usuario_id))
    s.execute(text("UPDATE almacen.conteo SET estado = 'confirmado', confirmado_por = :u, confirmado_en = now() WHERE id = :i"),
              {"u": a.usuario_id, "i": conteo_id})
    _publicar(s, "almacen.conteo_confirmado", a.obra_id, nuevos)
    return _conteo(s, a.obra_id, conteo_id)


# ------------------------------------------------------------------ HU-43 stock · kárdex
@router.get("/stock", response_model=e.StockOut)
def stock(almacen_id: int | None = Query(None, ge=1), a: Actor = Depends(exigir("stock.consultar"))):
    """Saldo por almacén y consolidado de la obra, con la hora del último movimiento considerado y los insumos bajo su
    punto de reposición. Lo calcula el servidor a partir del libro (R2)."""
    s = a.sesion
    por_almacen = filas(s, "SELECT s.almacen_id, r.nombre AS almacen, s.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad, "
                           "s.cantidad, s.costo_promedio, coalesce(s.valor, 0) AS valor, s.ultimo_movimiento_en "
                           "FROM almacen.saldo s JOIN almacen.insumo_ref i ON i.id = s.insumo_id "
                           "JOIN almacen.almacen_ref r ON r.id = s.almacen_id "
                           "WHERE s.proyecto_id = :p AND (CAST(:a AS bigint) IS NULL OR s.almacen_id = :a) ORDER BY r.nombre, i.codigo",
                        p=a.obra_id, a=almacen_id)
    consolidado = filas(s, "SELECT NULL::bigint AS almacen_id, NULL::text AS almacen, s.insumo_id, i.codigo AS insumo_codigo, i.descripcion, "
                           "i.unidad, s.cantidad, s.costo_promedio, coalesce(s.valor, 0) AS valor, s.ultimo_movimiento_en "
                           "FROM almacen.saldo_obra s JOIN almacen.insumo_ref i ON i.id = s.insumo_id "
                           "WHERE s.proyecto_id = :p ORDER BY i.codigo", p=a.obra_id)
    advertencias = []
    if consolidado:
        try:
            puntos = {p["insumo_id"]: Decimal(str(p["punto_reposicion"])) for p in
                      internos.json_de(contexto.COMPRAS, "GET", f"/interno/obras/{a.obra_id}/parametros-reposicion")}
            factores = {i: Decimal(str(x["factor_conversion"])) for i, x in
                        _insumos(s, [r["insumo_id"] for r in consolidado], para_registrar=False).items()}
            for r in consolidado:
                if r["insumo_id"] in puntos and r["insumo_id"] in factores:
                    r["punto_reposicion"] = puntos[r["insumo_id"]]
                    r["bajo_punto_reposicion"] = r["cantidad"] / factores[r["insumo_id"]] < puntos[r["insumo_id"]]
        except ErrorSigro:
            advertencias.append("No se pudo consultar el punto de reposición (compras o catálogo no respondió).")
    corte = max((r["ultimo_movimiento_en"] for r in consolidado), default=None)
    return {"criterio": CRITERIO, "corte": corte, "por_almacen": por_almacen, "consolidado": consolidado, "advertencias": advertencias}


@router.get("/kardex", response_model=list[e.KardexFilaOut])
def kardex(insumo_id: int | None = Query(None, ge=1), almacen_id: int | None = Query(None, ge=1),
           a: Actor = Depends(exigir("stock.consultar"))):
    """Cada movimiento con el saldo y el promedio que dejó; se ve el anulado (`revertido_por_id`) y el que lo anuló."""
    cols = _COLS.replace("m.", "k.")
    return filas(a.sesion, f"SELECT {cols}, r.motivo_exceso AS recepcion_motivo_exceso, k.revertido_por_id, k.saldo_despues, k.promedio_despues "
                           "FROM almacen.kardex k JOIN almacen.insumo_ref i ON i.id = k.insumo_id "
                           "LEFT JOIN almacen.recepcion r ON r.id = k.recepcion_id "
                           "WHERE k.proyecto_id = :p AND (CAST(:i AS bigint) IS NULL OR k.insumo_id = :i) "
                           "AND (CAST(:a AS bigint) IS NULL OR k.almacen_id = :a) ORDER BY k.id LIMIT 2000",
                 p=a.obra_id, i=insumo_id, a=almacen_id)


routers = [router]
