"""Migración 0025 (va DESPUÉS de la 0024). Relevo del outbox hacia costo dentro del backend Spring (25/11).

En el backend Spring todo corre en un proceso y no hay relevo aparte: los hechos valorizados de almacén quedaban en
outbox.evento sin entregar y «Gasto en recursos» decía «faltan eventos por entregar» (y su gráfico por familia, vacío).
Ahora el módulo costo consume SU bandeja: lee los eventos con destino 'integracion', guarda los hechos y los marca como
enviados en la misma transacción. Para eso su rol necesita leer la bandeja y actualizar SOLO las columnas de entrega.
No puede insertar ni borrar eventos, ni cambiar su contenido.

No toca datos. Al desplegar, el relevo entrega los eventos que ya estaban pendientes. La redacta Claude a pedido del
equipo (Fernando Istaña) el 25/11/2026. Revisar antes de aplicar.

Revision ID: 0025
Revises: 0024
"""
from alembic import op

revision = "0025"
down_revision = "0024"
branch_labels = None
depends_on = None

COLUMNAS_LECTURA = "id, tipo, proyecto_id, payload, creado_en, enviado_en, intentos, siguiente_intento, destino"
COLUMNAS_ENTREGA = "enviado_en, intentos, siguiente_intento, ultimo_error"


def upgrade() -> None:
    op.execute("GRANT USAGE ON SCHEMA outbox TO sigro_svc_costo")
    op.execute(f"GRANT SELECT ({COLUMNAS_LECTURA}) ON outbox.evento TO sigro_svc_costo")
    op.execute(f"GRANT UPDATE ({COLUMNAS_ENTREGA}) ON outbox.evento TO sigro_svc_costo")


def downgrade() -> None:
    op.execute(f"REVOKE UPDATE ({COLUMNAS_ENTREGA}) ON outbox.evento FROM sigro_svc_costo")
    op.execute(f"REVOKE SELECT ({COLUMNAS_LECTURA}) ON outbox.evento FROM sigro_svc_costo")
    op.execute("REVOKE USAGE ON SCHEMA outbox FROM sigro_svc_costo")
