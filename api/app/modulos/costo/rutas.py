"""Servicio costo: el gasto en RECURSOS de la obra (HU-58), no su costo total.

Es un modelo de lectura: los hechos de almacén llegan ya valorizados (al promedio ponderado congelado en cada salida)
por eventos; lo comprado se pide a compras y las horas a tareo por su API interna. Cada cifra declara su definición y
su fecha de corte para no confundirse con las demás.
"""
from __future__ import annotations

from decimal import Decimal

from fastapi import APIRouter, Depends
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
    cifras: list[CifraOut]
    por_familia: list[FamiliaOut]
    horas_hombre: HorasOut


_DEF_COMPRADO = "Valor de las compras registradas (base imponible, sin IGV); las anuladas no cuentan."


def _texto(v) -> str | None:
    return None if v is None else str(v)


@router.get("/gasto", response_model=GastoOut)
def gasto(a: Actor = Depends(exigir("costo.consultar"))):
    fam = filas(a.sesion, "SELECT familia, recibido, consumido, en_stock, hasta::text AS hasta FROM costo.gasto_por_obra "
                          "WHERE proyecto_id = :p AND moneda = 'PEN' ORDER BY familia", p=a.obra_id)
    tot = fila(a.sesion, "SELECT coalesce(sum(recibido), 0) AS recibido, coalesce(sum(consumido), 0) AS consumido, "
                         "coalesce(sum(en_stock), 0) AS en_stock, max(hasta)::text AS hasta FROM costo.gasto_por_obra "
                         "WHERE proyecto_id = :p AND moneda = 'PEN'", p=a.obra_id)
    corte = tot["hasta"]
    try:
        c = internos.json_de(contexto.COMPRAS, "GET", f"/interno/obras/{a.obra_id}/comprado")
        comprado = CifraOut(clave="comprado", valor=Decimal(str(c["base_imponible"])), corte=_texto(c["corte"]), definicion=_DEF_COMPRADO)
    except ErrorSigro:
        comprado = CifraOut(clave="comprado", valor=None, corte=None, definicion=_DEF_COMPRADO,
                            nota="Compras no respondió; inténtalo en un momento.")
    try:
        h = internos.json_de(contexto.TAREO, "GET", f"/interno/obras/{a.obra_id}/horas")
        horas = HorasOut(horas_normales=h["horas_normales"], horas_extra=h["horas_extra"], corte=_texto(h["hasta"]))
    except ErrorSigro:
        horas = HorasOut(horas_normales=None, horas_extra=None, corte=None)
    cifras = [
        comprado,
        CifraOut(clave="recibido", valor=tot["recibido"], corte=corte,
                 definicion="Valor de lo efectivamente ingresado al almacén (recepciones e ingresos sin compra, netos de reversos)."),
        CifraOut(clave="consumido", valor=tot["consumido"], corte=corte,
                 definicion="Valor de las salidas de material, al costo promedio ponderado congelado en cada salida (netas de reversos)."),
        CifraOut(clave="en_stock", valor=tot["en_stock"], corte=corte,
                 definicion="Valor del inventario que queda en los almacenes de la obra (ingresos - salidas, con traslados y ajustes)."),
        CifraOut(clave="horas_hombre", valor=None, corte=horas.corte,
                 definicion="Valor de las jornadas registradas, con sus horas extra aparte.",
                 nota="Falta la tarifa por categoría (HU-65): se informan las horas en horas_hombre, sin valorizar."),
    ]
    return GastoOut(declaracion=DECLARACION, criterio=CRITERIO, moneda="PEN", cifras=cifras, por_familia=fam, horas_hombre=horas)


routers = [router]
