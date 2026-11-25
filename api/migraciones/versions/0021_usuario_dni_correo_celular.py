"""Migración 0021 (va DESPUÉS de la 0020). DNI, correo y celular del usuario (HU-13).

El usuario solo tenía nombre, usuario y clave. Para identificar a la persona sin ambigüedad y poder ubicarla se
agregan a obra.usuario:

  - dni: 8 dígitos; único entre los usuarios (dos cuentas no pueden ser de la misma persona). Con él la web trae el
    nombre de RENIEC, como ya se hace con los trabajadores (0020).
  - correo: único sin importar mayúsculas; formato básico nombre@dominio.
  - celular: número móvil peruano de 9 dígitos que empieza en 9.

Los tres son opcionales: los usuarios ya creados no los tienen y no se inventan. El administrador los completa.

Base con datos: no toca filas existentes. Vuelta atrás: si ya hay usuarios con alguno de estos datos, el downgrade
NO los borra (regla 10: nada se borra) y se detiene con un mensaje.

La redacta Claude a pedido del equipo (Fernando Istaña) el 25/11/2026. Revisar antes de aplicar.

Revision ID: 0021
Revises: 0020
"""
from alembic import op
from sqlalchemy import text

revision = "0021"
down_revision = "0020"
branch_labels = None
depends_on = None

COLUMNAS = {
    "dni": "DNI de la persona (8 dígitos); único entre los usuarios; nulo si no se registró.",
    "correo": "Correo de contacto; único sin importar mayúsculas; nulo si no se registró.",
    "celular": "Celular de contacto (9 dígitos, empieza en 9); nulo si no se registró.",
}


def upgrade() -> None:
    op.execute(
        r"""
        ALTER TABLE obra.usuario
          ADD COLUMN dni     text,
          ADD COLUMN correo  text,
          ADD COLUMN celular text,
          ADD CONSTRAINT ck_usuario_dni     CHECK (dni IS NULL OR dni ~ '^\d{8}$'),
          ADD CONSTRAINT ck_usuario_correo  CHECK (correo IS NULL OR correo ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
          ADD CONSTRAINT ck_usuario_celular CHECK (celular IS NULL OR celular ~ '^9\d{8}$')
        """
    )
    op.execute("CREATE UNIQUE INDEX uq_usuario_dni ON obra.usuario (dni) WHERE dni IS NOT NULL")
    op.execute("CREATE UNIQUE INDEX uq_usuario_correo ON obra.usuario (lower(correo)) WHERE correo IS NOT NULL")
    for col, desc in COLUMNAS.items():
        op.execute(f"COMMENT ON COLUMN obra.usuario.{col} IS '{desc}'")
    # el administrador los completa o corrige: UPDATE solo de estas columnas (igual que la 0014)
    op.execute("GRANT UPDATE (dni, correo, celular) ON obra.usuario TO sigro_app")
    op.execute("GRANT UPDATE (dni, correo, celular) ON obra.usuario TO sigro_svc_obras")


def downgrade() -> None:
    con = op.get_bind()
    n = con.execute(text(
        "SELECT count(*) FROM obra.usuario WHERE dni IS NOT NULL OR correo IS NOT NULL OR celular IS NOT NULL"
    )).scalar()
    if n:
        raise RuntimeError(f"Hay {n} usuarios con DNI, correo o celular: bajar de la 0021 los perdería (regla 10).")
    op.execute("REVOKE UPDATE (dni, correo, celular) ON obra.usuario FROM sigro_svc_obras")
    op.execute("REVOKE UPDATE (dni, correo, celular) ON obra.usuario FROM sigro_app")
    op.execute("DROP INDEX obra.uq_usuario_correo")
    op.execute("DROP INDEX obra.uq_usuario_dni")
    op.execute(
        """
        ALTER TABLE obra.usuario
          DROP CONSTRAINT ck_usuario_celular,
          DROP CONSTRAINT ck_usuario_correo,
          DROP CONSTRAINT ck_usuario_dni,
          DROP COLUMN celular,
          DROP COLUMN correo,
          DROP COLUMN dni
        """
    )
