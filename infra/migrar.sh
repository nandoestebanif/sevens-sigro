#!/bin/sh
# Un solo paso, repetible: migra la base y deja listo el usuario de la aplicación.
# Corre con el usuario DUEÑO; la API y el relevo NUNCA lo usan.
#   SIGRO_DATABASE_URL  conexión SQLAlchemy del dueño (la lee Alembic)
#   SIGRO_OWNER_DSN     la misma conexión en formato libpq
#   SIGRO_API_CLAVE     contraseña que tendrá el usuario sigro_api
set -eu
alembic upgrade head
python - <<'PY'
import os

import psycopg
from psycopg import sql

clave = os.environ["SIGRO_API_CLAVE"]
with psycopg.connect(os.environ["SIGRO_OWNER_DSN"], autocommit=True) as c:
    existe = c.execute("SELECT 1 FROM pg_roles WHERE rolname = 'sigro_api'").fetchone()
    c.execute(sql.SQL("{} ROLE sigro_api LOGIN PASSWORD {}").format(sql.SQL("ALTER" if existe else "CREATE"), sql.Literal(clave)))
    c.execute("GRANT sigro_app TO sigro_api")
print("Base lista: migraciones aplicadas y rol sigro_api configurado.")
PY
