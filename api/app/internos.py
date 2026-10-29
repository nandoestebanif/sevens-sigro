"""Comunicación síncrona entre microservicios (REST interno).

- Cada servicio publica su API interna bajo `/interno/...`. El gateway NUNCA la expone hacia afuera.
- Quien llama manda la cabecera `X-Servicio-Token` (HT-08); quien recibe la exige con `exigir_servicio`.
- La URL de cada servicio sale de `SIGRO_URL_<SERVICIO>` (p. ej. SIGRO_URL_OBRAS=http://obras:8000).
- Si el otro servicio no responde, la petición falla con 503 y un mensaje claro: no se inventa el dato.

En pruebas, `registrar_cliente_de_prueba` cambia el transporte por la app del otro servicio en el
mismo proceso: el contrato HTTP es el mismo, solo cambia el cable.
"""
from __future__ import annotations

import os
import secrets
from typing import Any

import httpx
from fastapi import Header

from app.config import cargar
from app.errores import ErrorSigro

TIMEOUT = httpx.Timeout(5.0, connect=2.0)
_clientes_prueba: dict[str, httpx.Client] = {}


def registrar_cliente_de_prueba(servicio: str, cliente: httpx.Client | None) -> None:
    if cliente is None:
        _clientes_prueba.pop(servicio, None)
    else:
        _clientes_prueba[servicio] = cliente


def url_de(servicio: str) -> str | None:
    return os.environ.get(f"SIGRO_URL_{servicio.upper()}") or None


def _cabeceras() -> dict[str, str]:
    token = cargar().servicio_token
    return {"X-Servicio-Token": token} if token else {}


def llamar(servicio: str, metodo: str, ruta: str, **kw: Any) -> httpx.Response:
    """Llama a la API interna de otro servicio. Errores de red → ErrorSigro 503."""
    cliente = _clientes_prueba.get(servicio)
    try:
        if cliente is not None:
            return cliente.request(metodo, ruta, headers=_cabeceras(), **kw)
        base = url_de(servicio)
        if not base:
            raise ErrorSigro(503, "servicio_no_configurado", f"Falta SIGRO_URL_{servicio.upper()}: no se puede consultar a {servicio}.")
        with httpx.Client(timeout=TIMEOUT) as c:
            return c.request(metodo, f"{base.rstrip('/')}{ruta}", headers=_cabeceras(), **kw)
    except httpx.HTTPError:
        raise ErrorSigro(503, "servicio_no_disponible", f"El servicio de {servicio} no responde; inténtalo en un momento.")


def json_de(servicio: str, metodo: str, ruta: str, **kw: Any) -> Any:
    """Como `llamar`, pero devuelve el JSON y propaga tal cual un error {codigo, mensaje} del otro servicio."""
    r = llamar(servicio, metodo, ruta, **kw)
    if r.status_code >= 400:
        try:
            d = r.json().get("detail") or r.json()
            raise ErrorSigro(r.status_code, d["codigo"], d["mensaje"])
        except (ValueError, KeyError, TypeError, AttributeError):
            raise ErrorSigro(503, "servicio_con_error", f"El servicio de {servicio} respondió con un error ({r.status_code}).")
    return r.json() if r.content else None


def exigir_servicio(x_servicio_token: str | None = Header(default=None)) -> None:
    """Dependencia de las rutas /interno: solo otro servicio de SIGRO puede llamarlas.

    En desarrollo sin token configurado se permite (docker compose local y pruebas); en cualquier otro
    entorno el token es obligatorio.
    """
    cfg = cargar()
    esperado = cfg.servicio_token
    if not esperado:
        if cfg.es_dev:
            return
        raise ErrorSigro(503, "servicio_no_configurado", "SIGRO_SERVICIO_TOKEN no está definido en este servicio.")
    if not x_servicio_token or not secrets.compare_digest(x_servicio_token, esperado):
        raise ErrorSigro(401, "servicio_no_autorizado", "Esta ruta es solo para los servicios de SIGRO.")


exigir_servicio.accion = "servicio_interno"  # type: ignore[attr-defined]
