"""Configuración por variables de entorno. Ningún secreto vive en el código ni en el repositorio."""
from __future__ import annotations

import os
from dataclasses import dataclass

_DEV_SECRET = "solo-para-desarrollo-no-usar-en-produccion-0123456789"


@dataclass(frozen=True)
class Config:
    # La aplicación se conecta como un usuario MIEMBRO de sigro_app (sin UPDATE/DELETE sobre libros).
    database_url: str
    jwt_secret: str
    jwt_horas: int
    entorno: str
    integraciones_url: str | None
    notificaciones_url: str | None
    servicio_token: str | None
    intentos_max: int
    bloqueo_minutos: int

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
        database_url=os.environ.get(
            "SIGRO_APP_DATABASE_URL",
            "postgresql+psycopg://sigro_api:sigro_api_dev@localhost:5432/sigro_dev",
        ),
        jwt_secret=secreto,
        jwt_horas=int(os.environ.get("SIGRO_JWT_HORAS", "8")),
        entorno=entorno,
        integraciones_url=os.environ.get("SIGRO_INTEGRACIONES_URL") or None,
        notificaciones_url=os.environ.get("SIGRO_NOTIFICACIONES_URL") or None,
        servicio_token=os.environ.get("SIGRO_SERVICIO_TOKEN") or None,
        intentos_max=int(os.environ.get("SIGRO_INTENTOS_MAX", "5")),
        bloqueo_minutos=int(os.environ.get("SIGRO_BLOQUEO_MINUTOS", "15")),
    )
