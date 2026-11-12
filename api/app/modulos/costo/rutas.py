"""Servicio costo: el gasto en RECURSOS de la obra (HU-58), no su costo total.

Es un modelo de lectura: los hechos de almacén llegan ya valorizados (al promedio ponderado congelado en cada salida)
por eventos; lo comprado se pide a compras y las horas a tareo por su API interna. Cada cifra declara su definición y
su fecha de corte para no confundirse con las demás.
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from decimal import ROUND_HALF_UP, Decimal
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from app import contexto, internos
from app.dependencias import Actor, exigir
from app.errores import ErrorSigro
from app.sql import fila, filas

router = APIRouter(prefix="/obras/{obra_id}/costo", tags=["costo"])

DECLARACION = ("Informa el gasto en recursos de la obra (materiales, herramientas y equipos, horas hombre), no su costo total: "
               "quedan fuera los gastos generales, los subcontratos, los servicios y la planilla.")
CRITERIO = "Materiales y equipos al costo promedio ponderado por almacén (NIC 2), congelado en cada salida; importes sin IGV."


class CifraOut(BaseModel):
    clave: str
    valor: Decimal | None            # None: todavía no se puede calcular (ver `nota`)
    definicion: str
    corte: str | None
    nota: str | None = None


class FamiliaOut(BaseModel):
    familia: str
    recibido: Decimal
    consumido: Decimal
    en_stock: Decimal
    hasta: str | None


class HorasOut(BaseModel):
    horas_normales: Decimal | None
    horas_extra: Decimal | None
    corte: str | None


class GastoOut(BaseModel):
    declaracion: str
    criterio: str
    moneda: str
    corte: datetime                  # el mismo instante para las cinco cifras
    cifras: list[CifraOut]
    otros_movimientos: Decimal | None   # traslados entre obras y ajustes de conteo: también mueven el stock
    por_conciliar: bool              # True si las cifras no cuadran (ver `conciliacion`): no se presentan como definitivas
    conciliacion: list[str]
    por_familia: list[FamiliaOut]
    horas_hombre: HorasOut


_DEF_COMPRADO = "Valor de las compras registradas hasta la fecha de corte (base imponible, sin IGV, por fecha del comprobante); las anuladas no cuentan."
_TOLERANCIA = Decimal("0.01")


def _texto(v) -> str | None:
    return None if v is None else str(v)


@router.get("/gasto", response_model=GastoOut)
def gasto(corte: str | None = Query(None, description="AAAA-MM-DD o AAAA-MM-DDTHH:MM, hora de Lima; por defecto, ahora"),
          a: Actor = Depends(exigir("costo.consultar"))):
    """HU-58. Cinco cifras con su definición y UNA sola fecha de corte. Recibido, consumido y en stock salen del libro de
    Almacén (el mismo cálculo del inventario, HU-56); comprado de Compras y las horas de Tareo. Si no cuadran entre sí, la
    respuesta lo marca (`por_conciliar`) en vez de mostrar números que se contradicen."""
    pedido = corte_de(corte)
    params = {"corte": pedido.isoformat()} if pedido else {}
    conciliacion: list[str] = []
    try:
        lib = internos.json_de(contexto.ALMACEN, "GET", f"/interno/obras/{a.obra_id}/resumen-valorizado", params=params)
        efectivo = datetime.fromisoformat(lib["corte"])
    except ErrorSigro:
        lib, efectivo = None, min(pedido, datetime.now(timezone.utc)) if pedido else datetime.now(timezone.utc)
        conciliacion.append("Almacén no respondió: recibido, consumido y en stock no se pueden calcular ahora.")
    dia = efectivo.astimezone(LIMA).date().isoformat()
    corte_txt = efectivo.isoformat()
    try:
        c = internos.json_de(contexto.COMPRAS, "GET", f"/interno/obras/{a.obra_id}/comprado", params={"hasta": dia})
        comprado = CifraOut(clave="comprado", valor=Decimal(str(c["base_imponible"])), corte=corte_txt, definicion=_DEF_COMPRADO)
    except ErrorSigro:
        comprado = CifraOut(clave="comprado", valor=None, corte=corte_txt, definicion=_DEF_COMPRADO,
                            nota="Compras no respondió; inténtalo en un momento.")
    try:
        h = internos.json_de(contexto.TAREO, "GET", f"/interno/obras/{a.obra_id}/horas", params={"hasta": dia})
        horas = HorasOut(horas_normales=h["horas_normales"], horas_extra=h["horas_extra"], corte=corte_txt)
    except ErrorSigro:
        horas = HorasOut(horas_normales=None, horas_extra=None, corte=corte_txt)

    def cifra(clave, definicion):
        return CifraOut(clave=clave, valor=None if lib is None else Decimal(str(lib[clave])), corte=corte_txt, definicion=definicion)

    otros = None if lib is None else Decimal(str(lib["otros"]))
    if lib is not None:
        rec, con, stock = (Decimal(str(lib[k])) for k in ("recibido", "consumido", "en_stock"))
        if abs(rec - con + otros - stock) > _TOLERANCIA:                  # la identidad del libro: no debería romperse nunca
            conciliacion.append(f"Recibido − consumido + otros movimientos ({rec - con + otros}) no coincide con lo que hay en stock ({stock}).")
        if pedido is None:                                                 # contra el modelo de lectura que llega por eventos
            m = fila(a.sesion, "SELECT coalesce(sum(importe) FILTER (WHERE clase = 'ingreso'), 0) AS recibido, "
                               "coalesce(-sum(importe) FILTER (WHERE clase = 'consumo'), 0) AS consumido, "
                               "coalesce(sum(importe) FILTER (WHERE clase IN ('ingreso', 'consumo', 'traslado', 'ajuste')), 0) AS en_stock "
                               "FROM costo.hecho WHERE proyecto_id = :p AND moneda = 'PEN' AND familia <> 'mano_obra'", p=a.obra_id)
            for k, nombre in (("recibido", "Recibido"), ("consumido", "Consumido"), ("en_stock", "En stock")):
                if abs(Decimal(str(lib[k])) - m[k]) > _TOLERANCIA:
                    conciliacion.append(f"{nombre}: el libro de Almacén dice {lib[k]} y el modelo de costo {m[k]}; faltan eventos por entregar.")
    fam = filas(a.sesion, "SELECT familia, coalesce(sum(importe) FILTER (WHERE clase = 'ingreso'), 0) AS recibido, "
                          "coalesce(-sum(importe) FILTER (WHERE clase = 'consumo'), 0) AS consumido, "
                          "coalesce(sum(importe) FILTER (WHERE clase IN ('ingreso', 'consumo', 'traslado', 'ajuste')), 0) AS en_stock, "
                          "max(fecha)::text AS hasta FROM costo.hecho WHERE proyecto_id = :p AND moneda = 'PEN' AND fecha <= CAST(:d AS date) "
                          "GROUP BY familia ORDER BY familia", p=a.obra_id, d=dia)
    cifras = [
        comprado,
        cifra("recibido", "Valor de lo ingresado al almacén (recepciones e ingresos sin compra), al costo con que entró, neto de reversos."),
        cifra("consumido", "Valor de las salidas de material, al costo promedio ponderado congelado en cada salida (NIC 2), neto de reversos."),
        cifra("en_stock", "Valor del inventario que queda en los almacenes de la obra a la fecha de corte; es el total del inventario valorizado (HU-56)."),
        CifraOut(clave="horas_hombre", valor=None, corte=corte_txt,
                 definicion="Valor de las jornadas registradas (horas × tarifa de su categoría), con las horas extra aparte.",
                 nota="Falta la tarifa por categoría (HU-65): se informan las horas en horas_hombre, sin valorizar."),
    ]
    return GastoOut(declaracion=DECLARACION, criterio=CRITERIO, moneda="PEN", corte=efectivo, cifras=cifras, otros_movimientos=otros,
                    por_conciliar=bool(conciliacion), conciliacion=conciliacion, por_familia=fam, horas_hombre=horas)


# ------------------------------------------------------------------ HU-56 · inventario valorizado
LIMA = ZoneInfo("America/Lima")


def corte_de(texto: str | None) -> datetime | None:
    """`AAAA-MM-DD` o `AAAA-MM-DDTHH:MM[:SS]`. Sin zona horaria se entiende hora de Lima (la de la obra); un día solo
    significa el final de ese día. None = ahora. Un formato inválido es 422, no un «ahora» silencioso."""
    if texto is None or not texto.strip():
        return None
    try:
        c = datetime.fromisoformat(texto.strip())
    except ValueError:
        raise ErrorSigro(422, "corte_invalido", "La fecha de corte debe ser AAAA-MM-DD o AAAA-MM-DDTHH:MM.")
    if len(texto.strip()) <= 10:
        c = c + timedelta(days=1) - timedelta(seconds=1)
    if c.tzinfo is None:
        c = c.replace(tzinfo=LIMA)
    return c.astimezone(timezone.utc)


class InsumoValorizadoOut(BaseModel):
    insumo_codigo: str
    descripcion: str
    unidad: str
    tipo: str
    cantidad: Decimal
    costo_promedio: Decimal | None
    valor: Decimal
    porcentaje: Decimal             # del valor total de lo consultado
    ultimo_movimiento_en: datetime


class AlmacenValorizadoOut(BaseModel):
    almacen_id: int
    almacen: str
    insumos: int
    valor: Decimal
    porcentaje: Decimal


class InventarioOut(BaseModel):
    declaracion: str
    criterio: str
    moneda: str
    almacen: str                    # «todos» o el id consultado
    corte: datetime                 # el instante hasta el que se cuentan los movimientos
    corte_ajustado: bool = False    # True si pidieron un corte futuro y se usó «ahora»
    valor_total: Decimal
    por_almacen: list[AlmacenValorizadoOut]
    por_insumo: list[InsumoValorizadoOut]


def _pct(parte: Decimal, total: Decimal) -> Decimal:
    return (parte * 100 / total).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP) if total else Decimal("0.00")


@router.get("/inventario", response_model=InventarioOut)
def inventario(almacen: str = Query("todos", description="`todos` (consolidado de la obra) o el id de un almacén"),
               corte: str | None = Query(None, description="AAAA-MM-DD o AAAA-MM-DDTHH:MM, hora de Lima; por defecto, ahora"),
               a: Actor = Depends(exigir("costo.consultar"))):
    """Capital detenido en almacén: por insumo, cantidad × costo promedio ponderado a la fecha de corte (HU-56). Se
    reconstruye del libro de movimientos en Almacén; aquí solo se agrupa y se calcula el peso de cada insumo."""
    if almacen != "todos" and not almacen.isdigit():
        raise ErrorSigro(422, "almacen_invalido", "El almacén es `todos` o el número de un almacén de la obra.")
    pedido = corte_de(corte)
    ahora = datetime.now(timezone.utc)
    ajustado = pedido is not None and pedido > ahora
    params = {"corte": pedido.isoformat()} if pedido else {}
    d = internos.json_de(contexto.ALMACEN, "GET", f"/interno/obras/{a.obra_id}/saldos-valorizados", params=params)
    ids = {x["id"] for x in d["almacenes"]}
    if almacen != "todos" and int(almacen) not in ids:
        raise ErrorSigro(404, "no_existe", "Ese almacén no existe en esta obra.")
    rows = [r for r in d["filas"] if almacen == "todos" or r["almacen_id"] == int(almacen)]
    for r in rows:
        r["cantidad"], r["valor"] = Decimal(str(r["cantidad"])), Decimal(str(r["valor"]))
    total = sum((r["valor"] for r in rows), Decimal("0"))
    por_almacen = []
    for x in d["almacenes"]:
        if almacen != "todos" and x["id"] != int(almacen):
            continue
        mios = [r for r in rows if r["almacen_id"] == x["id"]]
        v = sum((r["valor"] for r in mios), Decimal("0"))
        por_almacen.append(AlmacenValorizadoOut(almacen_id=x["id"], almacen=x["nombre"], insumos=len(mios), valor=v, porcentaje=_pct(v, total)))
    agrupado: dict[str, dict] = {}
    for r in rows:                                  # un insumo puede estar en varios almacenes: se consolida
        g = agrupado.setdefault(r["insumo_codigo"], {"insumo_codigo": r["insumo_codigo"], "descripcion": r["descripcion"], "unidad": r["unidad"],
                                                      "tipo": r["tipo"], "cantidad": Decimal("0"), "valor": Decimal("0"), "ultimo": r["ultimo_movimiento_en"]})
        g["cantidad"] += r["cantidad"]
        g["valor"] += r["valor"]
        g["ultimo"] = max(g["ultimo"], r["ultimo_movimiento_en"])
    por_insumo = [InsumoValorizadoOut(insumo_codigo=g["insumo_codigo"], descripcion=g["descripcion"], unidad=g["unidad"], tipo=g["tipo"],
                                      cantidad=g["cantidad"], valor=g["valor"], porcentaje=_pct(g["valor"], total), ultimo_movimiento_en=g["ultimo"],
                                      costo_promedio=(g["valor"] / g["cantidad"]).quantize(Decimal("0.000001")) if g["cantidad"] else None)
                  for g in sorted(agrupado.values(), key=lambda g: (-g["valor"], g["insumo_codigo"]))]
    return InventarioOut(declaracion=DECLARACION, criterio=CRITERIO, moneda="PEN", almacen=almacen, corte=d["corte"], corte_ajustado=ajustado,
                         valor_total=total, por_almacen=por_almacen, por_insumo=por_insumo)


routers = [router]
