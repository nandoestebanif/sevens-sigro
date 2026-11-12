"""Servicio costo: el gasto en RECURSOS de la obra (HU-58), no su costo total.

Es un modelo de lectura: los hechos de almacén llegan ya valorizados (al promedio ponderado congelado en cada salida)
por eventos; lo comprado se pide a compras y las horas a tareo por su API interna. Cada cifra declara su definición y
su fecha de corte para no confundirse con las demás.
"""
from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
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


# ------------------------------------------------------------------ HU-57 · consumo contra lo planificado
AVISO_PLANIFICADO = ("Lo planificado en el S10 incluye desperdicio y reserva: una desviación moderada es esperable y no es, por sí sola, "
                     "una falla. La comparación se hace por insumo, no por partida.")
SUPUESTO = ("Lo esperado a la fecha de corte es lo planificado repartido de forma lineal en el plazo de la obra; el S10 no trae "
            "un calendario de consumo por insumo.")
ORDENES = ("importe", "porcentaje", "cantidad", "codigo")


class DesviacionInsumoOut(BaseModel):
    insumo_codigo: str
    descripcion: str
    unidad: str
    tipo: str
    planificado_cantidad: Decimal
    precio_planificado: Decimal
    planificado_importe: Decimal
    esperado_cantidad: Decimal          # planificado × avance del plazo
    esperado_importe: Decimal
    consumido_cantidad: Decimal
    consumido_importe: Decimal          # al costo promedio congelado en cada salida
    costo_real: Decimal | None          # S/ por unidad, el promedio de lo consumido
    consumido_pct_planificado: Decimal  # % de lo planificado que ya se consumió
    ritmo_pt: Decimal                   # consumido_pct_planificado − avance del plazo, en puntos
    desviacion_cantidad: Decimal        # consumido − esperado
    desviacion_importe: Decimal         # consumido_importe − esperado_importe = efecto_cantidad + efecto_precio
    desviacion_pct: Decimal | None      # sobre lo esperado; None si aún no se espera nada
    efecto_cantidad: Decimal            # (consumido − esperado) × precio planificado: se consumió más o menos de lo esperado
    efecto_precio: Decimal              # (costo real − precio planificado) × consumido: se pagó más o menos de lo planificado
    lectura: str                        # sobre_lo_esperado | en_rango | bajo_lo_esperado


class NoPlanificadoOut(BaseModel):
    insumo_codigo: str
    descripcion: str
    unidad: str
    tipo: str
    consumido_cantidad: Decimal
    consumido_importe: Decimal


class TotalesDesviacionOut(BaseModel):
    planificado_importe: Decimal
    esperado_importe: Decimal
    consumido_importe: Decimal
    desviacion_importe: Decimal
    desviacion_pct: Decimal | None


class DesviacionOut(BaseModel):
    declaracion: str
    aviso: str
    supuesto: str
    moneda: str
    corte: datetime
    avance_plazo_pct: Decimal
    tolerancia_pct: Decimal
    orden: str
    totales: TotalesDesviacionOut
    insumos: list[DesviacionInsumoOut]
    no_planificados: list[NoPlanificadoOut]     # aparte: no entran en los totales ni en la desviación
    no_planificados_importe: Decimal


def _q2(v: Decimal) -> Decimal:
    return v.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def avance_plazo(inicio: date, fin: date, hoy: date) -> Decimal:
    """Fracción (0 a 1) del plazo transcurrida a `hoy`. Antes del inicio 0; desde el fin, 1; un plazo de un solo día cuenta como 1 al terminar."""
    if hoy <= inicio:
        return Decimal("0")
    if hoy >= fin:
        return Decimal("1")
    return Decimal((hoy - inicio).days) / Decimal((fin - inicio).days)


def comparar(plan: dict, consumo: dict | None, avance: Decimal, tolerancia: Decimal) -> dict:
    """Desviación de un insumo (puro, sin base). `plan`: cantidad y precio planificados; `consumo`: cantidad e importe reales."""
    cant_plan, precio = plan["cantidad"], plan["precio"]
    cons_c = consumo["cantidad"] if consumo else Decimal("0")
    cons_i = consumo["importe"] if consumo else Decimal("0")
    esp_c = cant_plan * avance
    esp_i = esp_c * precio
    desv_c = cons_c - esp_c
    desv_i = cons_i - esp_i
    efecto_c = desv_c * precio
    pct_plan = cons_c * 100 / cant_plan if cant_plan else Decimal("0")
    desv_pct = desv_i * 100 / esp_i if esp_i else None
    if desv_pct is None:
        lectura = "sobre_lo_esperado" if cons_i > 0 else "en_rango"       # se consumió algo que aún no se esperaba
    else:
        lectura = "sobre_lo_esperado" if desv_pct > tolerancia else "bajo_lo_esperado" if desv_pct < -tolerancia else "en_rango"
    return {
        "planificado_cantidad": cant_plan, "precio_planificado": precio, "planificado_importe": _q2(cant_plan * precio),
        "esperado_cantidad": esp_c.quantize(Decimal("0.0001")), "esperado_importe": _q2(esp_i),
        "consumido_cantidad": cons_c, "consumido_importe": _q2(cons_i),
        "costo_real": (cons_i / cons_c).quantize(Decimal("0.000001")) if cons_c else None,
        "consumido_pct_planificado": _q2(pct_plan), "ritmo_pt": _q2(pct_plan - avance * 100),
        "desviacion_cantidad": desv_c.quantize(Decimal("0.0001")), "desviacion_importe": _q2(desv_i),
        "desviacion_pct": None if desv_pct is None else _q2(desv_pct),
        "efecto_cantidad": _q2(efecto_c), "efecto_precio": _q2(desv_i - efecto_c), "lectura": lectura,
    }


def ordenar(items: list[dict], orden: str) -> list[dict]:
    """`importe` (por defecto) y `porcentaje`: la mayor desviación primero, en valor absoluto, sin importar el signo."""
    if orden == "importe":
        return sorted(items, key=lambda x: (-abs(x["desviacion_importe"]), x["insumo_codigo"]))
    if orden == "porcentaje":
        return sorted(items, key=lambda x: (x["desviacion_pct"] is None, -abs(x["desviacion_pct"] or 0), x["insumo_codigo"]))
    if orden == "cantidad":
        return sorted(items, key=lambda x: (-abs(x["desviacion_cantidad"]), x["insumo_codigo"]))
    return sorted(items, key=lambda x: x["insumo_codigo"])


@router.get("/desviacion", response_model=DesviacionOut)
def desviacion(orden: str = Query("importe", description="importe (por defecto) | porcentaje | cantidad | codigo"),
               corte: str | None = Query(None, description="AAAA-MM-DD o AAAA-MM-DDTHH:MM, hora de Lima; por defecto, ahora"),
               tolerancia: Decimal = Query(Decimal("10"), ge=0, le=100, description="% de desviación que aún se lee «en rango»"),
               a: Actor = Depends(exigir("costo.consultar"))):
    """HU-57. Por insumo: consumo real (cantidad e importe al costo congelado) contra lo planificado por el S10, en cantidad,
    importe y porcentaje, comparado con el avance del plazo. Los insumos consumidos que el S10 no planificó van aparte."""
    if orden not in ORDENES:
        raise ErrorSigro(422, "orden_invalido", f"El orden es uno de: {', '.join(ORDENES)}.")
    pedido = corte_de(corte)
    params = {"corte": pedido.isoformat()} if pedido else {}
    obra = internos.json_de(contexto.OBRAS, "GET", f"/interno/obras/{a.obra_id}")
    req = internos.json_de(contexto.COMPRAS, "GET", f"/interno/obras/{a.obra_id}/requerido")
    cons = internos.json_de(contexto.ALMACEN, "GET", f"/interno/obras/{a.obra_id}/consumo-por-insumo", params=params)
    efectivo = datetime.fromisoformat(cons["corte"])
    avance = avance_plazo(date.fromisoformat(str(obra["fecha_inicio"])), date.fromisoformat(str(obra["fecha_fin"])),
                          efectivo.astimezone(LIMA).date())
    ids = sorted({r["insumo_id"] for r in req})
    catalogo = {x["id"]: x for x in internos.json_de(contexto.CATALOGO, "POST", "/interno/insumos/buscar", json={"ids": ids})} if ids else {}
    consumo = {c["insumo_id"]: {**c, "cantidad": Decimal(str(c["cantidad"])), "importe": Decimal(str(c["importe"]))} for c in cons["filas"]}
    tol = Decimal(str(tolerancia))
    items, planificados = [], set()
    for r in req:
        x = catalogo.get(r["insumo_id"])
        if x is None or x["tipo"] not in ("material", "equipo"):      # mano de obra y subcontratos no pasan por almacén: aquí no hay consumo que comparar
            continue
        planificados.add(r["insumo_id"])
        d = comparar({"cantidad": Decimal(str(r["requerido"])), "precio": Decimal(str(r["precio_planificado"]))}, consumo.get(r["insumo_id"]), avance, tol)
        items.append({"insumo_codigo": x["codigo"], "descripcion": x["descripcion"], "unidad": x["unidad_s10"], "tipo": x["tipo"], **d})
    items = ordenar(items, orden)
    sin_plan = [NoPlanificadoOut(insumo_codigo=c["insumo_codigo"], descripcion=c["descripcion"], unidad=c["unidad"], tipo=c["tipo"],
                                 consumido_cantidad=c["cantidad"], consumido_importe=c["importe"])
                for i, c in sorted(consumo.items(), key=lambda kv: kv[1]["insumo_codigo"]) if i not in planificados]
    plan_t = sum((i["planificado_importe"] for i in items), Decimal("0"))
    esp_t = sum((i["esperado_importe"] for i in items), Decimal("0"))
    con_t = sum((i["consumido_importe"] for i in items), Decimal("0"))
    return DesviacionOut(
        declaracion=DECLARACION, aviso=AVISO_PLANIFICADO, supuesto=SUPUESTO, moneda="PEN", corte=efectivo,
        avance_plazo_pct=_q2(avance * 100), tolerancia_pct=tol, orden=orden,
        totales=TotalesDesviacionOut(planificado_importe=plan_t, esperado_importe=esp_t, consumido_importe=con_t, desviacion_importe=con_t - esp_t,
                                     desviacion_pct=_q2((con_t - esp_t) * 100 / esp_t) if esp_t else None),
        insumos=items, no_planificados=sin_plan, no_planificados_importe=sum((n.consumido_importe for n in sin_plan), Decimal("0")))


routers = [router]
