"""Arranque del sistema: crea el PRIMER administrador y su primera obra.

Hace falta porque todo permiso vive en una asignación a una obra (R9): sin una primera obra y un
primer administrador asignado, nadie podría crear nada. Después, todo se hace por la API.

    python -m app.cli primer-admin --usuario adiel --nombre "Víctor Adiel Luján" \\
        --obra "Multifamiliar Flores" --inicio 2026-10-19 --fin 2027-10-17

La contraseña se pide por teclado (o SIGRO_ADMIN_CLAVE); nunca como argumento, para que no quede en el historial.
"""
from __future__ import annotations

import argparse
import getpass
import os
import sys
from datetime import date

from sqlalchemy import text
from sqlalchemy.exc import IntegrityError

from app.db import motor
from app.seguridad import clave_aceptable, hashear


def primer_admin(usuario: str, nombre: str, clave: str, obra: str, inicio: date, fin: date) -> tuple[int, int]:
    motivo = clave_aceptable(clave)
    if motivo:
        raise SystemExit(motivo)
    with motor().begin() as c:
        if c.execute(text("SELECT count(*) FROM obra.usuario")).scalar():
            raise SystemExit("Ya hay usuarios: el primer administrador solo se crea en un sistema vacío.")
        uid = c.execute(
            text("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES (:n, :u, :h) RETURNING id"),
            {"n": nombre, "u": usuario, "h": hashear(clave)},
        ).scalar()
        pid = c.execute(
            text("INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, creado_por) VALUES (:n, :i, :f, :u) RETURNING id"),
            {"n": obra, "i": inicio, "f": fin, "u": uid},
        ).scalar()
        c.execute(
            text("INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (:u, :p, 'administrador', :u)"),
            {"u": uid, "p": pid},
        )
    return uid, pid


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="orden", required=True)
    p = sub.add_parser("primer-admin")
    p.add_argument("--usuario", required=True)
    p.add_argument("--nombre", required=True)
    p.add_argument("--obra", required=True)
    p.add_argument("--inicio", type=date.fromisoformat, required=True)
    p.add_argument("--fin", type=date.fromisoformat, required=True)
    a = ap.parse_args(argv)

    clave = os.environ.get("SIGRO_ADMIN_CLAVE") or getpass.getpass("Contraseña del administrador: ")
    try:
        uid, pid = primer_admin(a.usuario, a.nombre, clave, a.obra, a.inicio, a.fin)
    except IntegrityError as e:
        print(f"No se pudo crear: {getattr(e.orig, 'diag', None) and e.orig.diag.message_primary}", file=sys.stderr)
        return 1
    print(f"Listo: administrador #{uid} asignado a la obra #{pid}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
