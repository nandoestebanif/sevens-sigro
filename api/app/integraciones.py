"""Cliente del microservicio de integraciones (SUNAT/RENIEC vía Factiliza, importación del S10).

Frontera ADR-01: el microservicio NO escribe en la base de la API; devuelve datos y la API decide.
Si no responde, la operación NO se bloquea: el proveedor queda «sin validar» y se avisa.
"""
from __future__ import annotations

from dataclasses import dataclass

import httpx

from app.config import cargar
from app.errores import ErrorSigro

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


TIMEOUT_S10 = httpx.Timeout(60.0, connect=3.0)     # leer las 5 páginas del PDF real toma unos segundos


def _cliente_s10() -> httpx.Client:
    """Punto de cambio para las pruebas: sin red, el transporte se reemplaza aquí."""
    return httpx.Client(timeout=TIMEOUT_S10)


def leer_relacion_s10(nombre: str, contenido: bytes, codigos_existentes: list[str]) -> dict:
    """HU-07: Integraciones lee el PDF del S10 y devuelve los insumos normalizados. Aquí no hay modo degradado:
    si no responde, la importación no puede seguir y se dice por qué (a diferencia del RUC, que no bloquea la compra)."""
    cfg = cargar()
    if not cfg.integraciones_url:
        raise ErrorSigro(503, "integraciones_no_configurado", "El servicio no conoce la dirección de Integraciones.")
    cabeceras = {"X-Servicio-Token": cfg.servicio_token} if cfg.servicio_token else {}
    try:
        with _cliente_s10() as c:
            r = c.post(
                f"{cfg.integraciones_url.rstrip('/')}/s10/relacion-insumos", headers=cabeceras,
                files={"archivo": (nombre, contenido, "application/pdf")},
                data={"codigos_existentes": ",".join(codigos_existentes)},
            )
    except httpx.HTTPError:
        raise ErrorSigro(503, "integraciones_no_disponible",
                         "El servicio que lee el PDF del S10 no responde; inténtalo en un momento. El archivo no se pierde.")
    if r.status_code == 200:
        return r.json()
    if r.status_code in (401, 403):
        raise ErrorSigro(503, "integraciones_rechazo_el_servicio",
                         "Integraciones rechazó el token de servicio del catálogo. Avisa al administrador del sistema.")
    if r.status_code == 422:
        try:
            detalle = r.json().get("detail")
        except ValueError:
            detalle = None
        raise ErrorSigro(422, "archivo_s10_invalido",
                         detalle if isinstance(detalle, str) else "No se pudo leer el archivo como una Relación de insumos del S10.")
    raise ErrorSigro(503, "integraciones_con_error", f"Integraciones respondió con un error ({r.status_code}).")
