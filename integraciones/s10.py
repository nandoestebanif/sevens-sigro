# ─────────────────────────────────────────────────────────────────────────────
# SIGRO · lector de la «Relación de insumos» del S10 (HU-07)
#
# El S10 exporta «Precios y cantidades de recursos requeridos por tipo»: un PDF
# con cuatro secciones (mano de obra, materiales, equipos, subcontratos) y una
# fila por insumo: código (10 dígitos), descripción, unidad, cantidad, precio y
# parcial. Este módulo SOLO lee y normaliza (ADR-01): no toca ninguna base de
# datos, así que importar dos veces es leer dos veces — los saldos del almacén
# viven en los movimientos y esta importación jamás los roza.
#
# Probado contra el archivo real de la obra (presupuesto 2701006, Multifamiliar
# Flores): 284 insumos, S/ 1 479 338.33. Una historia de importación probada con
# datos inventados no está terminada (CLAUDE.md).
# ─────────────────────────────────────────────────────────────────────────────
import io
import re
from dataclasses import dataclass, field

import pdfplumber

SECCIONES = {
    "MANO DE OBRA": "mano_obra",
    "MATERIALES": "material",
    "EQUIPOS": "equipo",
    "SUBCONTRATOS": "subcontrato",
}

# 0203020004  ACERO CORRUGADO FY= 4200 Kg/cm2  kg  36,357.4207  4.54  165,062.69
FILA = re.compile(
    r"^(\d{10})\s+(.+?)\s+(\S{1,8})\s+([\d,]+\.\d{4})\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})$"
)
ENCABEZADO = re.compile(r"^(S10|Página|Obra|Subpresupuesto|Fecha|Lugar|Código\s+Recurso)")


def _numero(texto: str) -> float:
    return float(texto.replace(",", ""))


@dataclass
class InsumoLeido:
    codigo: str
    descripcion: str
    unidad: str
    tipo: str
    cantidad: float
    precio: float
    parcial: float
    advertencia: str | None = None


@dataclass
class LecturaS10:
    obra: str | None = None
    subpresupuesto: str | None = None
    fecha: str | None = None
    insumos: list[InsumoLeido] = field(default_factory=list)
    fallidos: list[dict] = field(default_factory=list)


def leer_relacion_de_insumos(contenido_pdf: bytes) -> LecturaS10:
    """Lee el PDF exportado del S10 y devuelve los insumos normalizados.
    Una fila que parece de insumo pero no se entiende va a `fallidos` con su
    motivo: la importación informa, nunca descarta en silencio."""
    lectura = LecturaS10()
    tipo_actual: str | None = None

    with pdfplumber.open(io.BytesIO(contenido_pdf)) as pdf:
        for pagina in pdf.pages:
            for cruda in (pagina.extract_text() or "").splitlines():
                linea = cruda.strip()
                if not linea:
                    continue

                if lectura.obra is None:
                    m = re.match(r"^Obra\s+(\d+)\s+(.*)$", linea)
                    if m:
                        lectura.obra = f"{m.group(1)} {m.group(2).strip()}"
                        continue
                if lectura.subpresupuesto is None:
                    m = re.match(r"^Subpresupuesto\s+(\d+)\s+(.*)$", linea)
                    if m:
                        lectura.subpresupuesto = f"{m.group(1)} {m.group(2).strip()}"
                        continue
                if lectura.fecha is None:
                    m = re.match(r"^Fecha\s+(\d{2}/\d{2}/\d{4})", linea)
                    if m:
                        lectura.fecha = m.group(1)
                        continue

                if linea in SECCIONES:
                    tipo_actual = SECCIONES[linea]
                    continue
                if ENCABEZADO.match(linea) or linea.startswith("Total"):
                    continue
                # subtotal de sección: solo un importe suelto
                if re.fullmatch(r"[\d,]+\.\d{2}", linea):
                    continue

                m = FILA.match(linea)
                if m:
                    if tipo_actual is None:
                        lectura.fallidos.append(
                            {"texto": linea[:90], "error": "Fila de insumo antes de la primera sección."}
                        )
                        continue
                    cantidad = _numero(m.group(4))
                    precio = _numero(m.group(5))
                    parcial = _numero(m.group(6))
                    advertencia = None
                    # el parcial del S10 manda; si no cuadra con cantidad × precio, se avisa
                    if abs(cantidad * precio - parcial) > max(1.0, parcial * 0.01):
                        advertencia = (
                            f"cantidad × precio = {cantidad * precio:,.2f} no cuadra con el parcial"
                        )
                    lectura.insumos.append(
                        InsumoLeido(
                            codigo=m.group(1),
                            descripcion=m.group(2).strip(),
                            unidad=m.group(3),
                            tipo=tipo_actual,
                            cantidad=cantidad,
                            precio=precio,
                            parcial=parcial,
                            advertencia=advertencia,
                        )
                    )
                    continue

                # empieza como código pero no se entendió: a fallidos, con motivo
                if re.match(r"^\d{10}\s", linea):
                    lectura.fallidos.append(
                        {"texto": linea[:90], "error": "La fila no calza con el formato código/descripción/unidad/cantidad/precio/parcial."}
                    )

    return lectura
