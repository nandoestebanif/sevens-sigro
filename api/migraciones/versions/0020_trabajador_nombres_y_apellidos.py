"""Migración 0020 (va DESPUÉS de la 0019). Nombres y apellidos del trabajador por separado (HU-62).

Hasta ahora el trabajador tenía un solo texto «nombre». Para ordenar el tareo por apellido, buscar sin ambigüedad y
llenarlo desde RENIEC (la consulta de DNI de integraciones devuelve nombres, apellido paterno y materno), se agregan:

  - tareo.trabajador.nombres, apellido_paterno, apellido_materno (texto, nulos para los registrados antes de esta
    migración: su nombre completo no se puede partir sin adivinar, y adivinar sería inventar datos).
  - Regla: si se registran por separado, nombres y apellido paterno son obligatorios; el materno puede faltar
    (hay personas con un solo apellido).
  - «nombre» se queda y la API lo sigue llenando: «Apellido paterno Apellido materno, Nombres». Así la web, los
    reportes y la app 1.0.0 (que solo conocen «nombre») siguen funcionando sin cambios.

Base con datos: no toca filas existentes. Vuelta atrás: si ya hay trabajadores con las partes llenas, el downgrade
NO borra esos datos (regla 10: nada se borra) y se detiene con un mensaje.

La redacta Claude a pedido del equipo (Fernando Istaña) el 25/11/2026. Revisar antes de aplicar.

Revision ID: 0020
Revises: 0019
"""
from alembic import op
from sqlalchemy import text

revision = "0020"
down_revision = "0019"
branch_labels = None
depends_on = None

COLUMNAS = {
    "nombres": "Nombres de pila (de RENIEC si se consultó el DNI); nulo en los registrados antes de la 0020.",
    "apellido_paterno": "Apellido paterno; obligatorio si se registran nombres por separado.",
    "apellido_materno": "Apellido materno; puede faltar (personas con un solo apellido).",
}


def upgrade() -> None:
    op.execute(
        """
        ALTER TABLE tareo.trabajador
          ADD COLUMN nombres          text,
          ADD COLUMN apellido_paterno text,
          ADD COLUMN apellido_materno text,
          ADD CONSTRAINT ck_trabajador_nombres_y_apellidos CHECK (
            nombres IS NULL
            OR (btrim(nombres) <> '' AND apellido_paterno IS NOT NULL AND btrim(apellido_paterno) <> '')
          )
        """
    )
    for col, desc in COLUMNAS.items():
        op.execute(f"COMMENT ON COLUMN tareo.trabajador.{col} IS '{desc}'")
    op.execute(
        "COMMENT ON COLUMN tareo.trabajador.nombre IS "
        "'Nombre completo para mostrar; desde la 0020 la API lo arma como «Paterno Materno, Nombres».'"
    )


def downgrade() -> None:
    con = op.get_bind()
    n = con.execute(text("SELECT count(*) FROM tareo.trabajador WHERE nombres IS NOT NULL")).scalar()
    if n:
        raise RuntimeError(
            f"Hay {n} trabajadores con nombres y apellidos por separado: bajar de la 0020 los perdería (regla 10)."
        )
    op.execute("COMMENT ON COLUMN tareo.trabajador.nombre IS 'Nombre completo.'")
    op.execute(
        """
        ALTER TABLE tareo.trabajador
          DROP CONSTRAINT ck_trabajador_nombres_y_apellidos,
          DROP COLUMN apellido_materno,
          DROP COLUMN apellido_paterno,
          DROP COLUMN nombres
        """
    )
