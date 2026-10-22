"""Publicación de eventos en la bandeja de salida (outbox).

Se llama DENTRO de la misma transacción que el hecho que origina el evento: si el hecho se revierte, el evento no
existe; si se confirma, no se pierde aunque Notificaciones esté caído. La API resuelve los destinatarios aquí
porque solo ella sabe quién tiene qué rol en cada obra: el microservicio no lee esta base.
"""
from __future__ import annotations

import json
from collections.abc import Iterable

from sqlalchemy import text
from sqlalchemy.orm import Session


def destinatarios_con_rol(s: Session, proyecto_id: int, roles: Iterable[str], excepto: int | None = None) -> list[int]:
    filas = s.execute(
        text(
            "SELECT DISTINCT a.usuario_id FROM obra.asignacion a JOIN obra.usuario u ON u.id = a.usuario_id "
            "WHERE a.proyecto_id = :p AND a.fecha_retiro IS NULL AND u.estado = 'activo' AND a.rol = ANY(:r) ORDER BY 1"
        ),
        {"p": proyecto_id, "r": sorted(roles)},
    ).scalars().all()
    return [u for u in filas if u != excepto]


def publicar(
    s: Session, tipo: str, proyecto_id: int, destinatarios: Iterable[int], titulo: str, mensaje: str, datos: dict | None = None
) -> int | None:
    """Encola el evento. Sin destinatarios no hay a quién avisar: no se publica nada (devuelve None)."""
    dest = sorted(set(destinatarios))
    if not dest:
        return None
    payload = {"destinatarios": dest, "titulo": titulo, "mensaje": mensaje, "datos": datos or {}}
    return s.execute(
        text("INSERT INTO outbox.evento (tipo, proyecto_id, payload) VALUES (:t, :p, CAST(:j AS jsonb)) RETURNING id"),
        {"t": tipo, "p": proyecto_id, "j": json.dumps(payload, ensure_ascii=False)},
    ).scalar()


def nombre_obra(s: Session, proyecto_id: int) -> str:
    return s.execute(text("SELECT nombre FROM obra.proyecto WHERE id = :p"), {"p": proyecto_id}).scalar() or f"obra {proyecto_id}"
