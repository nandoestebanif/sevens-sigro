"""Cliente del microservicio de integraciones (SUNAT/RENIEC vía Factiliza, importación del S10).

Frontera ADR-01: el microservicio NO escribe en la base de la API; devuelve datos y la API decide.
Si no responde, la operación NO se bloquea: el proveedor queda «sin validar» y se avisa.
"""
from __future__ import annotations

from dataclasses import dataclass

import httpx

from app.config import cargar

TIMEOUT = httpx.Timeout(4.0, connect=2.0)


@dataclass(frozen=True)
class RucSunat:
    ruc: str
    razon_social: str
    estado_sunat: str
    condicion: str
    apto_para_comprar: bool


def consultar_ruc(ruc: str, cliente: httpx.Client | None = None) -> RucSunat | None:
    """None cuando no se pudo consultar (sin servicio configurado, caído, sin token o RUC que SUNAT no conoce)."""
    cfg = cargar()
    base = cfg.integraciones_url
    if not base:
        return None
    # HT-08: Integraciones exige el token de servicio (cabecera X-Servicio-Token) cuando lo tiene configurado.
    cabeceras = {"X-Servicio-Token": cfg.servicio_token} if cfg.servicio_token else {}
    try:
        propio = cliente is None
        c = cliente or httpx.Client(timeout=TIMEOUT)
        try:
            r = c.get(f"{base.rstrip('/')}/ruc/{ruc}", headers=cabeceras)
        finally:
            if propio:
                c.close()
        if r.status_code != 200:
            return None
        d = r.json()
        return RucSunat(
            ruc=d["ruc"], razon_social=d["razon_social"], estado_sunat=d["estado_sunat"],
            condicion=d["condicion"], apto_para_comprar=bool(d["apto_para_comprar"]),
        )
    except (httpx.HTTPError, KeyError, ValueError):
        return None
