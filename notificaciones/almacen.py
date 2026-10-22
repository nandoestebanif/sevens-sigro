"""Almacenamiento propio del microservicio (SQLite). Es SU base: no comparte tablas con la API (ADR-01).

Dos tablas
  evento        lo recibido, deduplicado por `evento_id` (la clave de idempotencia que manda la API)
  notificacion  una fila por destinatario: lo que cada persona ve en su campana, con su marca de leída
"""
from __future__ import annotations

import json
import os
import sqlite3
from contextlib import contextmanager

RUTA = os.environ.get("NOTIF_DB", "notificaciones.db")

ESQUEMA = """
CREATE TABLE IF NOT EXISTS evento (
  evento_id   TEXT PRIMARY KEY,
  tipo        TEXT NOT NULL,
  proyecto_id INTEGER NOT NULL,
  creado_en   TEXT NOT NULL,
  recibido_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE TABLE IF NOT EXISTS notificacion (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  evento_id   TEXT NOT NULL REFERENCES evento (evento_id),
  usuario_id  INTEGER NOT NULL,
  proyecto_id INTEGER NOT NULL,
  tipo        TEXT NOT NULL,
  titulo      TEXT NOT NULL,
  mensaje     TEXT NOT NULL,
  datos       TEXT NOT NULL DEFAULT '{}',
  creada_en   TEXT NOT NULL,
  leida_en    TEXT,
  UNIQUE (evento_id, usuario_id)
);
CREATE INDEX IF NOT EXISTS ix_notificacion_usuario ON notificacion (usuario_id, id DESC);
CREATE INDEX IF NOT EXISTS ix_notificacion_no_leidas ON notificacion (usuario_id) WHERE leida_en IS NULL;
"""


@contextmanager
def conexion(ruta: str | None = None):
    c = sqlite3.connect(ruta or RUTA, timeout=10, isolation_level=None)   # transacciones explícitas
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA journal_mode=WAL")
    c.execute("PRAGMA foreign_keys=ON")
    try:
        yield c
    finally:
        c.close()


def iniciar(ruta: str | None = None) -> None:
    with conexion(ruta) as c:
        c.executescript(ESQUEMA)


def registrar(c: sqlite3.Connection, e: dict) -> tuple[bool, int]:
    """(es_nuevo, notificaciones_creadas). Un evento repetido no crea nada: devuelve (False, 0)."""
    c.execute("BEGIN IMMEDIATE")
    try:
        cur = c.execute(
            "INSERT OR IGNORE INTO evento (evento_id, tipo, proyecto_id, creado_en) VALUES (?, ?, ?, ?)",
            (e["evento_id"], e["tipo"], e["proyecto_id"], e["creado_en"]),
        )
        if cur.rowcount == 0:
            c.execute("COMMIT")
            return False, 0
        for uid in e["destinatarios"]:
            c.execute(
                "INSERT INTO notificacion (evento_id, usuario_id, proyecto_id, tipo, titulo, mensaje, datos, creada_en) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (e["evento_id"], uid, e["proyecto_id"], e["tipo"], e["titulo"], e["mensaje"],
                 json.dumps(e["datos"], ensure_ascii=False), e["creado_en"]),
            )
        c.execute("COMMIT")
        return True, len(e["destinatarios"])
    except BaseException:
        c.execute("ROLLBACK")
        raise


def listar(c: sqlite3.Connection, usuario_id: int, solo_no_leidas: bool, limite: int) -> list[dict]:
    filas = c.execute(
        "SELECT id, proyecto_id, tipo, titulo, mensaje, datos, creada_en, leida_en FROM notificacion "
        "WHERE usuario_id = ? AND (? = 0 OR leida_en IS NULL) ORDER BY id DESC LIMIT ?",
        (usuario_id, int(solo_no_leidas), limite),
    ).fetchall()
    return [{**dict(f), "datos": json.loads(f["datos"]), "leida": f["leida_en"] is not None} for f in filas]


def contar_no_leidas(c: sqlite3.Connection, usuario_id: int) -> int:
    return c.execute("SELECT count(*) FROM notificacion WHERE usuario_id = ? AND leida_en IS NULL", (usuario_id,)).fetchone()[0]


def marcar_leida(c: sqlite3.Connection, notificacion_id: int, usuario_id: int) -> bool:
    """Solo la marca su dueño; marcar dos veces no cambia la primera hora."""
    cur = c.execute(
        "UPDATE notificacion SET leida_en = COALESCE(leida_en, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) "
        "WHERE id = ? AND usuario_id = ?",
        (notificacion_id, usuario_id),
    )
    return cur.rowcount == 1
