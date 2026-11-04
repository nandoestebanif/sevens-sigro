"""Contraseñas (Argon2id) y tokens (JWT). El token dice QUIÉN es; nunca qué puede hacer:
el rol y la obra se consultan en la base en cada petición (regla 9)."""
from __future__ import annotations

import os
import threading
from datetime import datetime, timedelta, timezone

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError

from app.config import cargar

_hasher = PasswordHasher()   # Argon2id con parámetros por defecto de la librería (OWASP)
# Cada hash o verificación reserva memory_cost (64 MiB con los parámetros por defecto) mientras dura. Los logins
# corren en el threadpool: 20 a la vez serían 1,3 GB y Render gratis (512 MB) mata el proceso. Se limita cuántos
# corren A LA VEZ (SIGRO_ARGON2_SIMULTANEOS, 2 = 128 MiB como mucho); los demás esperan su turno. Los parámetros
# del hash no cambian: la contraseña queda igual de protegida y los hashes guardados siguen valiendo.
_turnos = threading.BoundedSemaphore(max(int(os.environ.get("SIGRO_ARGON2_SIMULTANEOS", "2")), 1))
# Hash de relleno: verificar contra algo cuando el usuario no existe evita distinguir los casos por tiempo.
_RELLENO = _hasher.hash("relleno-para-igualar-tiempos")
ALGORITMO = "HS256"


def hashear(clave: str) -> str:
    with _turnos:
        return _hasher.hash(clave)


def verificar(clave: str, hash_guardado: str | None) -> bool:
    try:
        with _turnos:
            return _hasher.verify(hash_guardado or _RELLENO, clave) and hash_guardado is not None
    except (VerificationError, InvalidHashError):
        return False


def clave_aceptable(clave: str) -> str | None:
    """Devuelve el motivo del rechazo, o None si sirve."""
    if len(clave) < 10:
        return "La contraseña debe tener al menos 10 caracteres."
    if clave.lower() == clave or clave.upper() == clave or not any(c.isdigit() for c in clave):
        return "La contraseña debe mezclar mayúsculas, minúsculas y al menos un número."
    return None


def crear_token(usuario_id: int) -> tuple[str, int]:
    cfg = cargar()
    vence = datetime.now(timezone.utc) + timedelta(hours=cfg.jwt_horas)
    token = jwt.encode({"sub": str(usuario_id), "exp": vence}, cfg.jwt_secret, algorithm=ALGORITMO)
    return token, cfg.jwt_horas * 3600


def leer_token(token: str) -> int | None:
    try:
        datos = jwt.decode(token, cargar().jwt_secret, algorithms=[ALGORITMO], options={"require": ["exp", "sub"]})
        return int(datos["sub"])
    except (jwt.PyJWTError, ValueError):
        return None
