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


# --------------------------------------------------------------------------- HU-31: qué dice SUNAT del proveedor
ESTADOS_VALIDACION = ("validado", "sin_validar", "con_alerta_sunat")


def clasificar_consulta_sunat(ruc: str, respuesta) -> tuple[str, str | None, str | None]:
    """(estado, alerta_sunat, advertencia). Función pura: no consulta red ni base.

    - DNI (8 dígitos) y RUC con dígito verificador malo: nunca se consultan; quedan «sin_validar» (por validar).
    - SUNAT/Integraciones no responden (`respuesta is None`): «sin_validar», se reintenta; no bloquea.
    - SUNAT responde y es activo y habido: «validado».
    - SUNAT responde y NO es activo y habido: «con_alerta_sunat» con lo que informó; **avisa, no bloquea** (D12).
    """
    if len(ruc) == 8:
        return "sin_validar", None, "Es un DNI (persona natural): queda sin validar; solo los RUC se validan en SUNAT."
    if not ruc_valido(ruc):
        return "sin_validar", None, "El dígito verificador del RUC no coincide: revisa que esté bien escrito."
    if respuesta is None:
        return "sin_validar", None, "No se pudo consultar a SUNAT; el proveedor queda por validar y se puede reintentar."
    if respuesta.apto_para_comprar:
        return "validado", None, None
    alerta = f"{respuesta.estado_sunat} / {respuesta.condicion}"
    return "con_alerta_sunat", alerta, f"SUNAT lo informa {alerta}: puedes comprarle, pero la compra queda marcada para el residente."


# --------------------------------------------------------------------------- HU-34: comprobantes adjuntos
MAX_COMPROBANTE_BYTES = 10 * 1024 * 1024          # D15/ADR-11 (la base lo repite en ck_compra_adjunto_tamano)

_FIRMAS = (
    (b"%PDF-", "application/pdf"),
    (b"\xff\xd8\xff", "image/jpeg"),
    (b"\x89PNG\r\n\x1a\n", "image/png"),
)


def tipo_real(contenido: bytes) -> str | None:
    """El tipo lo dicen los primeros bytes del archivo, no la extensión ni el `Content-Type` que mande el navegador:
    un .exe renombrado a .pdf no pasa. Devuelve el tipo MIME admitido o None."""
    for firma, mime in _FIRMAS:
        if contenido.startswith(firma):
            return mime
    return None


def nombre_seguro(nombre: str | None, mime: str) -> str:
    """Solo el nombre del archivo (sin rutas del equipo del usuario), sin caracteres de control y con un máximo de 150."""
    base = (nombre or "").replace("\\", "/").rsplit("/", 1)[-1]
    base = "".join(ch for ch in base if ch.isprintable() and ch not in '<>:"|?*').strip(" .")
    if not base:
        base = "comprobante" + {"application/pdf": ".pdf", "image/jpeg": ".jpg", "image/png": ".png"}[mime]
    if len(base) > 150:
        raiz, punto, ext = base.rpartition(".")
        base = (raiz[: 150 - len(ext) - 1] + "." + ext) if punto and len(ext) <= 5 else base[:150]
    return base
