"""Migración 0022 (va DESPUÉS de la 0021). Los nombres y apellidos del trabajador se pueden corregir (25/11).

La 0003 dejó corregir nombre, categoría y cuadrilla (y registrar la baja). La 0020 agregó nombres, apellido paterno
y materno, pero sin permiso de UPDATE: corregir un apellido mal escrito no era posible. Aquí se da UPDATE solo de
esas tres columnas, igual que la 0014 o la 0021. Documento, fecha de ingreso y autor siguen sin poder editarse
(el disparador tareo.tg_trabajador_validar lo impide incluso al dueño).

No toca datos. La redacta Claude a pedido del equipo (Fernando Istaña) el 25/11/2026. Revisar antes de aplicar.

Revision ID: 0022
Revises: 0021
"""
from alembic import op

revision = "0022"
down_revision = "0021"
branch_labels = None
depends_on = None

COLUMNAS = "nombres, apellido_paterno, apellido_materno"


def upgrade() -> None:
    op.execute(f"GRANT UPDATE ({COLUMNAS}) ON tareo.trabajador TO sigro_app")
    op.execute(f"GRANT UPDATE ({COLUMNAS}) ON tareo.trabajador TO sigro_svc_tareo")


def downgrade() -> None:
    op.execute(f"REVOKE UPDATE ({COLUMNAS}) ON tareo.trabajador FROM sigro_svc_tareo")
    op.execute(f"REVOKE UPDATE ({COLUMNAS}) ON tareo.trabajador FROM sigro_app")
