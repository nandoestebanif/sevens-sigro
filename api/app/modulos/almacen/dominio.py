"""Reglas de almacén que se pueden probar sin base de datos.

El motor (migración 0010) sostiene las mismas reglas con su disparador; aquí viven las decisiones del servicio
(qué periodo toca, qué unidad, qué costo trae una recepción) y un espejo del cálculo del promedio para probarlo.
"""
from __future__ import annotations

import uuid
from collections.abc import Iterable
from datetime import date, datetime, timedelta, timezone
from decimal import ROUND_HALF_UP, Decimal

LIMA = timezone(timedelta(hours=-5))          # Perú no tiene horario de verano
D4 = Decimal("0.0001")
D6 = Decimal("0.000001")

# Tipo de movimiento → clase del hecho que recibe costo (HU-58)
CLASE = {
    "ingreso_compra": "ingreso",
    "ingreso_sin_compra": "ingreso",
    "salida": "consumo",
    "traslado_salida": "traslado",
    "traslado_ingreso": "traslado",
    "ajuste_conteo": "ajuste",
}
FAMILIAS = {"material": "material", "equipo": "equipo"}     # lo que pasa por almacén (mano de obra y subcontratos no)
ORIGENES = ("saldo_otra_obra", "devolucion_cuadrilla", "prestamo", "sobrante", "otro")


class ReglaAlmacen(Exception):
    """Una regla de negocio rechazó el registro: (estado HTTP, código, mensaje)."""

    def __init__(self, estado: int, codigo: str, mensaje: str):
        super().__init__(mensaje)
        self.estado, self.codigo, self.mensaje = estado, codigo, mensaje


def promedio_ponderado(movimientos: Iterable[tuple[int, Decimal, Decimal]]) -> tuple[Decimal, Decimal | None]:
    """Espejo del disparador: (saldo, promedio vigente) de un insumo en UN almacén (regla 4, D1).

    Cada movimiento es (sentido, cantidad, costo_unitario). Una salida no cambia el promedio: sale al promedio.
    Sin saldo no hay promedio (None).
    """
    cant = valor = Decimal(0)
    for sentido, cantidad, costo in movimientos:
        cant += sentido * cantidad
        valor += sentido * cantidad * costo
    return cant, ((valor / cant).quantize(D6, rounding=ROUND_HALF_UP) if cant > 0 else None)


def fecha_del_hecho(hora_dispositivo_ms: int | None, ahora: datetime) -> date:
    """Día (hora de Lima) en que ocurrió el movimiento: el del celular si lo trae (HU-37, se registró sin señal),
    nunca uno futuro respecto del servidor. La hora que vale para auditar sigue siendo la del servidor (HU-38)."""
    momento = ahora
    if hora_dispositivo_ms is not None:
        dispositivo = datetime.fromtimestamp(hora_dispositivo_ms / 1000, tz=timezone.utc)
        momento = min(dispositivo, ahora)
    return momento.astimezone(LIMA).date()


def periodo_de(periodos: list[dict], dia: date) -> dict | None:
    for p in periodos:
        if _a_fecha(p["fecha_inicio"]) <= dia <= _a_fecha(p["fecha_fin"]):
            return p
    return None


def exigir_periodo_abierto(periodo: dict | None, es_reverso: bool) -> None:
    """D4: en un periodo cerrado solo entran reversos (como en tareo)."""
    if periodo is not None and periodo["estado"] == "cerrado" and not es_reverso:
        raise ReglaAlmacen(409, "periodo_cerrado",
                           f"El periodo {periodo['numero']} está cerrado: solo admite correcciones con reverso.")


def a_unidad_s10(cantidad: Decimal, unidad: str | None, insumo: dict) -> Decimal:
    """Los movimientos se cuentan en la unidad del S10. Si llega en unidad de compra (p. ej. varillas de acero),
    se convierte con el factor de conversión, nunca a mano."""
    if unidad is None or unidad == insumo["unidad_s10"]:
        return cantidad
    if unidad == insumo["unidad_compra"]:
        return (cantidad * Decimal(str(insumo["factor_conversion"]))).quantize(D4, rounding=ROUND_HALF_UP)
    raise ReglaAlmacen(422, "unidad_invalida",
                       f"{insumo['codigo']} se registra en {insumo['unidad_s10']} (o en {insumo['unidad_compra']}, su unidad de compra).")


def a_costo_s10(costo: Decimal, unidad: str | None, insumo: dict) -> Decimal:
    """El costo viaja en la MISMA unidad que la cantidad (HU-41): S/ por bolsa, por m³ o por varilla. El libro guarda cantidad
    y costo en la unidad del S10, así que un costo dado por unidad de compra se divide entre el factor de conversión; si no,
    100 varillas a S/ 35.40 valdrían 7,12 veces lo que costaron."""
    costo = Decimal(str(costo))
    if unidad is None or unidad == insumo["unidad_s10"]:
        return costo
    if unidad == insumo["unidad_compra"]:
        return (costo / Decimal(str(insumo["factor_conversion"]))).quantize(D6, rounding=ROUND_HALF_UP)
    raise ReglaAlmacen(422, "unidad_invalida",
                       f"{insumo['codigo']} se registra en {insumo['unidad_s10']} (o en {insumo['unidad_compra']}, su unidad de compra).")


def costo_recepcion(precio_unitario: Decimal, factor_sin_igv: Decimal, factor_conversion: Decimal) -> Decimal:
    """Costo por unidad del S10 de lo recibido: precio de la línea sin IGV (NIC 2) entre el factor de conversión."""
    return (Decimal(str(precio_unitario)) * Decimal(str(factor_sin_igv)) / Decimal(str(factor_conversion))).quantize(D6, rounding=ROUND_HALF_UP)


def uuid_derivado(base: uuid.UUID | str, *partes: object) -> uuid.UUID:
    """Identificador estable de cada movimiento de una operación compuesta (recepción, traslado, conteo): un reenvío de
    la operación produce los mismos uuid y por tanto no duplica nada (regla 3)."""
    return uuid.uuid5(uuid.UUID(str(base)), ":".join(str(p) for p in partes))


def normalizar(texto: str) -> str:
    return " ".join(texto.split()).casefold()


def _a_fecha(v) -> date:
    return v if isinstance(v, date) else date.fromisoformat(str(v)[:10])


def exceso_sobre_requerido(requerido: Decimal | None, ya_recibido: Decimal, con_esta: Decimal) -> Decimal | None:
    """HU-36. Cuánto pasa lo recibido de lo requerido por el S10 (HU-19), todo en la unidad del S10.

    `None` si el insumo no está en el requerimiento: no hay nada que exceder y la recepción sigue normal. El requerimiento
    avisa, no bloquea (regla 7): solo se exige el motivo, nunca se impide recibir."""
    if requerido is None:
        return None
    return max(Decimal(str(ya_recibido)) + Decimal(str(con_esta)) - Decimal(str(requerido)), Decimal(0)).quantize(D4)
