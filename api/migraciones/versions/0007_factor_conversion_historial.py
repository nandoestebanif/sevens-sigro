"""Histórico del factor de conversión de un insumo (HU-17): cada cambio queda con quién, cuándo y por qué.

El factor convierte la unidad del S10 a la unidad de compra (kg ↔ varillas). Cambiarlo cambia cómo se
lee lo comprado frente a lo planificado, así que el valor anterior NO se pisa en silencio: el libro es
solo-agregar (no admite UPDATE ni DELETE) y el valor vigente sigue en catalogo.insumo.factor_conversion.

Revision ID: 0007
Revises: 0006
"""
from alembic import op

from migraciones._utiles import conceder, solo_insertar

revision = "0007"
down_revision = "0006"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE catalogo.insumo_factor_historial (
          id              bigint GENERATED ALWAYS AS IDENTITY,
          insumo_id       bigint         NOT NULL,
          factor_anterior numeric(18, 6) NOT NULL,
          factor_nuevo    numeric(18, 6) NOT NULL,
          motivo          text           NOT NULL,
          cambiado_por    bigint         NOT NULL,
          cambiado_en     timestamptz    NOT NULL DEFAULT now(),
          CONSTRAINT pk_insumo_factor_historial PRIMARY KEY (id),
          CONSTRAINT fk_insumo_factor_historial_insumo_id_insumo FOREIGN KEY (insumo_id) REFERENCES catalogo.insumo (id),
          CONSTRAINT fk_insumo_factor_historial_cambiado_por_usuario FOREIGN KEY (cambiado_por) REFERENCES obra.usuario (id),
          CONSTRAINT ck_insumo_factor_historial_positivos CHECK (factor_anterior > 0 AND factor_nuevo > 0),
          CONSTRAINT ck_insumo_factor_historial_cambia CHECK (factor_nuevo <> factor_anterior),
          CONSTRAINT ck_insumo_factor_historial_motivo CHECK (btrim(motivo) <> '')
        )
        """
    )
    op.execute("CREATE INDEX ix_insumo_factor_historial_insumo_id ON catalogo.insumo_factor_historial (insumo_id, id)")
    solo_insertar("catalogo.insumo_factor_historial")
    conceder("catalogo.insumo_factor_historial")


def downgrade() -> None:
    op.execute("DROP TABLE catalogo.insumo_factor_historial")
