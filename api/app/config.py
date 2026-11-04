"""Configuración por variables de entorno. Ningún secreto vive en el código ni en el repositorio."""
from __future__ import annotations

import os
from dataclasses import dataclass

from app import contexto

_DEV_SECRET = "solo-para-desarrollo-no-usar-en-produccion-0123456789"
_DEV_DB = "postgresql+psycopg://sigro_api:sigro_api_dev@localhost:5432/sigro_dev"


def un_proceso() -> bool:
    """Ambiente de pruebas en un nodo (ADR-10): todos los servicios son apps ASGI dentro del MISMO proceso."""
    return os.environ.get("SIGRO_UN_PROCESO") == "1"


def url_de_base() -> str:
    """La cadena de conexión del servicio que atiende la petición en curso.

    Con un proceso por servicio (producción, docker-compose) es `SIGRO_APP_DATABASE_URL`, la única que ese proceso
    recibe. En el nodo único todas las apps comparten el entorno: cada una usa `SIGRO_DB_<SERVICIO>` según
    `contexto.SERVICIO` (lo fija el middleware de cada app, y el relevo para el suyo). Sin servicio, o sin su
    variable, falla: nunca se cae a la cadena de otro servicio. El mensaje no incluye la cadena.
    """
    if not un_proceso():
        return os.environ.get("SIGRO_APP_DATABASE_URL", _DEV_DB)
    servicio = contexto.actual()
    if not servicio:
        raise RuntimeError("nodo único: se pidió la base fuera de un servicio (contexto.SERVICIO vacío)")
    variable = f"SIGRO_DB_{servicio.upper()}"
    url = os.environ.get(variable)
    if not url:
        raise RuntimeError(f"nodo único: falta {variable} para el servicio {servicio}")
    return url


@dataclass(frozen=True)
class Config:
    jwt_secret: str
    jwt_horas: int
    entorno: str
    integraciones_url: str | None
    notificaciones_url: str | None
    servicio_token: str | None
    intentos_max: int
    bloqueo_minutos: int

    @property
    def database_url(self) -> str:
        """Se resuelve al usarse (no al cargar): en el nodo único depende del servicio de la petición en curso.
        La aplicación se conecta como un usuario MIEMBRO de sigro_app (sin UPDATE/DELETE sobre libros)."""
        return url_de_base()

    @property
    def es_dev(self) -> bool:
        return self.entorno == "dev"


def cargar() -> Config:
    entorno = os.environ.get("SIGRO_ENTORNO", "dev")
    secreto = os.environ.get("SIGRO_JWT_SECRET")
    if not secreto:
        if entorno != "dev":
            raise RuntimeError("SIGRO_JWT_SECRET es obligatorio fuera de desarrollo")
        secreto = _DEV_SECRET
    if entorno != "dev" and len(secreto) < 32:
        raise RuntimeError("SIGRO_JWT_SECRET debe tener al menos 32 caracteres")
    return Config(
        jwt_secret=secreto,
        jwt_horas=int(os.environ.get("SIGRO_JWT_HORAS", "8")),
        entorno=entorno,
        integraciones_url=os.environ.get("SIGRO_INTEGRACIONES_URL") or None,
        notificaciones_url=os.environ.get("SIGRO_NOTIFICACIONES_URL") or None,
        servicio_token=os.environ.get("SIGRO_SERVICIO_TOKEN") or None,
        intentos_max=int(os.environ.get("SIGRO_INTENTOS_MAX", "5")),
        bloqueo_minutos=int(os.environ.get("SIGRO_BLOQUEO_MINUTOS", "15")),
    )
