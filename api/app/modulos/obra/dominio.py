"""Lógica de dominio de obra, sin base de datos ni framework.

`generar_periodos` es la misma regla que implementa obra.generar_periodos() en PostgreSQL.
Existe en Python para poder probarla sin levantar la base (Definición de Terminado) y la
suite compara ambas implementaciones: si divergen, falla.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

DOMINGO = 7


@dataclass(frozen=True)
class PeriodoCalculado:
    numero: int
    inicio: date
    fin: date

    @property
    def dias(self) -> int:
        return (self.fin - self.inicio).days + 1


def generar_periodos(inicio: date, fin: date, dia_corte: int = DOMINGO, desde_numero: int = 0) -> list[PeriodoCalculado]:
    """Parte [inicio, fin] en periodos que terminan en `dia_corte` (ISO: 1 lunes ... 7 domingo).

    - El primero puede ser corto si la obra no arranca justo después de un corte.
    - El último puede ser corto si el plazo no termina en corte (HU-03).
    - Los periodos son contiguos: no dejan días sueltos ni se solapan.
    """
    if fin < inicio:
        raise ValueError("la fecha de fin no puede ser anterior a la de inicio")
    if not 1 <= dia_corte <= 7:
        raise ValueError("el día de corte es un día ISO entre 1 (lunes) y 7 (domingo)")

    periodos: list[PeriodoCalculado] = []
    cursor, numero = inicio, desde_numero
    while cursor <= fin:
        dias_al_corte = (dia_corte - cursor.isoweekday() + 7) % 7
        fin_periodo = min(fin, cursor + timedelta(days=dias_al_corte))
        numero += 1
        periodos.append(PeriodoCalculado(numero, cursor, fin_periodo))
        cursor = fin_periodo + timedelta(days=1)
    return periodos
