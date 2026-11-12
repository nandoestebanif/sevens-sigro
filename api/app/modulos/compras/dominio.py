"""Lógica de dominio de compras, sin base de datos.

`ruc_valido` es la misma regla que compras.ruc_valido() en PostgreSQL (dígito
verificador módulo 11 de SUNAT). La suite compara ambas.
"""
from __future__ import annotations

import re

_PESOS = (5, 4, 3, 2, 7, 6, 5, 4, 3, 2)
_FORMATO = re.compile(r"^(10|15|16|17|20)[0-9]{9}$")


def digito_verificador_ruc(primeros_diez: str) -> int:
    """Dígito verificador de un RUC a partir de sus 10 primeros dígitos."""
    if not re.fullmatch(r"[0-9]{10}", primeros_diez):
        raise ValueError("se esperan exactamente 10 dígitos")
    suma = sum(int(d) * p for d, p in zip(primeros_diez, _PESOS))
    return (11 - suma % 11) % 10


def ruc_valido(ruc: str) -> bool:
    if not _FORMATO.match(ruc):
        return False
    return digito_verificador_ruc(ruc[:10]) == int(ruc[10])


# ------------------------------------------------------------------ HU-33 precio contra lo habitual
from decimal import ROUND_HALF_UP, Decimal  # noqa: E402

D6 = Decimal("0.000001")


def referencia_en_unidad_de_compra(promedio_s10: Decimal | None, factor_conversion: Decimal) -> Decimal | None:
    """El promedio pagado viene por unidad del S10 (kg) y el precio de la compra por unidad de compra (varilla): una
    varilla trae `factor_conversion` kg, así que su precio habitual es promedio × factor. `None` si aún no hay promedio."""
    if promedio_s10 is None:
        return None
    return (Decimal(str(promedio_s10)) * Decimal(str(factor_conversion))).quantize(D6, rounding=ROUND_HALF_UP)


def desviacion_pct(precio_sin_igv: Decimal, referencia: Decimal | None) -> Decimal | None:
    """Cuánto se aparta el precio pagado (sin IGV, unidad de compra) de su referencia, en %. `None` sin referencia."""
    if referencia is None or Decimal(str(referencia)) <= 0:
        return None
    return ((Decimal(str(precio_sin_igv)) / Decimal(str(referencia)) - 1) * 100).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def fuera_de_lo_habitual(desviacion: Decimal | None, limite_pct: Decimal) -> bool:
    """Avisa, no bloquea (D13): fuera si se aparta MÁS que el límite, hacia arriba o hacia abajo."""
    return desviacion is not None and abs(desviacion) > Decimal(str(limite_pct))
