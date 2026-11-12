"""Arranque del sistema: crea el PRIMER administrador y su primera obra.

Hace falta porque todo permiso vive en una asignación a una obra (R9): sin una primera obra y un
primer administrador asignado, nadie podría crear nada. Después, todo se hace por la API.

    python -m app.cli primer-admin --usuario adiel --nombre "Víctor Adiel Luján" \\
        --obra "Multifamiliar Flores" --inicio 2026-10-19 --fin 2027-10-17

Usuarios de prueba (idempotente: repetirlo no duplica, informa lo que ya existía):

    SIGRO_USUARIO_CLAVE='…' python -m app.cli crear-usuario --usuario henry --nombre "Henry Ticona" \
        --rol residente --obra "Multifamiliar Flores"

La contraseña se pide por teclado (o SIGRO_ADMIN_CLAVE / SIGRO_USUARIO_CLAVE); nunca como argumento, para que no
quede en el historial.
"""
from __future__ import annotations

import argparse
import getpass
import os
import sys
from dataclasses import dataclass
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


ROLES = ("almacenero", "ssoma", "residente", "administrador", "gerente")


@dataclass(frozen=True)
class Resultado:
    usuario_id: int
    obra_id: int
    usuario_creado: bool
    asignacion_creada: bool
    rol: str

    def resumen(self, usuario: str, obra: str) -> str:
        quien = f"usuario «{usuario}» #{self.usuario_id} " + ("creado" if self.usuario_creado else "ya existía (no se tocó su clave)")
        donde = (f"asignado como {self.rol} a «{obra}» #{self.obra_id}" if self.asignacion_creada
                 else f"ya estaba asignado como {self.rol} a «{obra}» #{self.obra_id}")
        return f"{quien}; {donde}."


def crear_usuario(usuario: str, nombre: str, clave: str | None, rol: str, obra: str, asignado_por: str | None = None) -> Resultado:
    """Crea (si falta) el usuario y su asignación vigente a la obra con ese rol. Repetirlo no duplica nada.

    - Si el usuario ya existe no se cambia su nombre ni su clave (para eso está la API).
    - Si ya tiene una asignación vigente en la obra con OTRO rol, no se pisa: se informa y no se escribe nada.
    - Quién asigna: `asignado_por` (usuario) o, si no se indica, un administrador vigente de esa obra.
    """
    if rol not in ROLES:
        raise SystemExit(f"Rol desconocido: {rol} (opciones: {', '.join(ROLES)}).")
    with motor().begin() as c:
        pid = c.execute(text("SELECT id FROM obra.proyecto WHERE nombre = :n"), {"n": obra}).scalar()
        if pid is None:
            raise SystemExit(f"No existe la obra «{obra}». Créala primero (primer-admin o la web).")
        u = c.execute(text("SELECT id, estado FROM obra.usuario WHERE usuario = :u"), {"u": usuario}).mappings().first()
        creado = u is None
        if creado:
            if not clave:
                raise SystemExit("Falta la contraseña del usuario nuevo (SIGRO_USUARIO_CLAVE).")
            motivo = clave_aceptable(clave)
            if motivo:
                raise SystemExit(motivo)
            uid = c.execute(
                text("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES (:n, :u, :h) RETURNING id"),
                {"n": nombre, "u": usuario, "h": hashear(clave)},
            ).scalar()
        else:
            if u["estado"] != "activo":
                raise SystemExit(f"El usuario «{usuario}» está de baja: no se reactiva desde aquí (regla 10).")
            uid = u["id"]
        vigente = c.execute(
            text("SELECT rol FROM obra.asignacion WHERE usuario_id = :u AND proyecto_id = :p AND fecha_retiro IS NULL"),
            {"u": uid, "p": pid},
        ).scalar()
        if vigente is not None and vigente != rol:
            raise SystemExit(f"«{usuario}» ya es {vigente} en «{obra}»; retira esa asignación (web) antes de darle otro rol.")
        if vigente is None:
            if asignado_por:
                por = c.execute(text("SELECT id FROM obra.usuario WHERE usuario = :u AND estado = 'activo'"), {"u": asignado_por}).scalar()
                if por is None:
                    raise SystemExit(f"No existe un usuario activo «{asignado_por}» para registrar la asignación.")
            else:
                por = c.execute(text(
                    "SELECT a.usuario_id FROM obra.asignacion a JOIN obra.usuario u ON u.id = a.usuario_id "
                    "WHERE a.proyecto_id = :p AND a.rol = 'administrador' AND a.fecha_retiro IS NULL AND u.estado = 'activo' "
                    "ORDER BY a.id LIMIT 1"), {"p": pid}).scalar()
                if por is None:
                    raise SystemExit(f"La obra «{obra}» no tiene un administrador vigente; indica --asignado-por.")
            c.execute(
                text("INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (:u, :p, :r, :por)"),
                {"u": uid, "p": pid, "r": rol, "por": por},
            )
    return Resultado(uid, pid, creado, vigente is None, rol)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="orden", required=True)
    p = sub.add_parser("primer-admin")
    p.add_argument("--usuario", required=True)
    p.add_argument("--nombre", required=True)
    p.add_argument("--obra", required=True)
    p.add_argument("--inicio", type=date.fromisoformat, required=True)
    p.add_argument("--fin", type=date.fromisoformat, required=True)
    q = sub.add_parser("crear-usuario", help="usuario de prueba asignado a una obra con un rol (idempotente)")
    q.add_argument("--usuario", required=True)
    q.add_argument("--nombre", required=True)
    q.add_argument("--rol", required=True, choices=ROLES)
    q.add_argument("--obra", required=True)
    q.add_argument("--asignado-por", default=None, help="usuario que registra la asignación (por defecto, un administrador de la obra)")
    a = ap.parse_args(argv)

    if a.orden == "crear-usuario":
        clave = os.environ.get("SIGRO_USUARIO_CLAVE") or None
        try:
            r = crear_usuario(a.usuario, a.nombre, clave, a.rol, a.obra, a.asignado_por)
        except IntegrityError as e:
            print(f"No se pudo crear: {getattr(e.orig, 'diag', None) and e.orig.diag.message_primary}", file=sys.stderr)
            return 1
        print(r.resumen(a.usuario, a.obra))
        return 0

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
