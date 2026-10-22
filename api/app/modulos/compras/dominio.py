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
