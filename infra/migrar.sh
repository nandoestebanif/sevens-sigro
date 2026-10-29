#!/bin/sh
# Un solo paso, repetible: migra la base y deja listo UN USUARIO POR MICROSERVICIO.
# Corre con el usuario DUEÑO; ningún servicio lo usa después.
#   SIGRO_DATABASE_URL      conexión SQLAlchemy del dueño (la lee Alembic)
#   SIGRO_OWNER_DSN         la misma conexión en formato libpq
#   SIGRO_CLAVE_<SERVICIO>  contraseña del usuario de cada servicio (OBRAS, CATALOGO, COMPRAS, TAREO, RELEVO)
set -eu
alembic upgrade head
python - <<'PY'
import os

import psycopg
from psycopg import sql

# usuario con LOGIN → rol con los privilegios SOLO de su esquema (migración 0009)
SERVICIOS = ["obras", "catalogo", "compras", "tareo", "relevo"]
with psycopg.connect(os.environ["SIGRO_OWNER_DSN"], autocommit=True) as c:
    for s in SERVICIOS:
        usuario, rol, clave = f"sigro_{s}", f"sigro_svc_{s}", os.environ[f"SIGRO_CLAVE_{s.upper()}"]
        existe = c.execute("SELECT 1 FROM pg_roles WHERE rolname = %s", (usuario,)).fetchone()
        c.execute(sql.SQL("{} ROLE {} LOGIN PASSWORD {}").format(
            sql.SQL("ALTER" if existe else "CREATE"), sql.Identifier(usuario), sql.Literal(clave)))
        c.execute(sql.SQL("GRANT {} TO {}").format(sql.Identifier(rol), sql.Identifier(usuario)))
        c.execute(sql.SQL("ALTER ROLE {} SET role = {}").format(sql.Identifier(usuario), sql.Identifier(rol)))
print("Base lista: migraciones aplicadas y un usuario por servicio (cada uno ve solo su esquema).")
PY
