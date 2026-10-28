"""Lo que compras necesita saber de los insumos, pedido al servicio catálogo por su API interna."""
from __future__ import annotations

from decimal import Decimal
from collections.abc import Iterable

from app import contexto, internos


def _normalizar(i: dict) -> dict:
    i = dict(i)
    if i.get("factor_conversion") is not None:
        i["factor_conversion"] = Decimal(str(i["factor_conversion"]))
    return i


def por_id(ids: Iterable[int]) -> dict[int, dict]:
    ids = sorted({int(x) for x in ids if x is not None})
    if not ids:
        return {}
    r = internos.json_de(contexto.CATALOGO, "POST", "/interno/insumos/buscar", json={"ids": ids})
    return {i["id"]: _normalizar(i) for i in r}


def por_codigo(codigos: Iterable[str], solo_activos: bool = False) -> dict[str, dict]:
    """Clave: el código en MAYÚSCULAS."""
    cods = sorted({c.upper() for c in codigos if c})
    if not cods:
        return {}
    r = internos.json_de(contexto.CATALOGO, "POST", "/interno/insumos/buscar", json={"codigos": cods, "solo_activos": solo_activos})
    return {i["codigo"].upper(): _normalizar(i) for i in r}
