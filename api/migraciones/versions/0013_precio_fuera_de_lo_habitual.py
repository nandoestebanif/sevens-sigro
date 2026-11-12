"""Precio pagado contra lo habitual (HU-33): cada línea de compra guarda con qué promedio se comparó y si se salió.

  - precio_referencia: el promedio ponderado pagado del insumo en ESTA obra al momento de registrar la compra, en la
    unidad de compra y sin IGV (para comparar lo mismo con lo mismo). NULL si aún no había recepciones: no hay con qué
    comparar y nada se marca.
  - fuera_de_lo_habitual: el precio pagado (sin IGV) se apartó del promedio más que el límite configurado (hoy ±10 %).
    Avisa, no bloquea (decisión D13 del PO, 11/11/2026).
  - nota_precio: nota opcional de quien registró, hasta 300 caracteres.

La línea de compra sigue siendo un libro de solo-insertar (0004): estas columnas se escriben al crear la línea y no se
editan. Las líneas anteriores quedan con referencia NULL y sin marca (no se recalcula el pasado).

Autorizada por el PO (Víctor Adiel Luján) el 12/11/2026 10:25; la redacta Nicolás Pachamora.

Revision ID: 0013
Revises: 0012
"""
from alembic import op

revision = "0013"
down_revision = "0012"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(
        """
        ALTER TABLE compras.compra_detalle
          ADD COLUMN precio_referencia     numeric(18, 6),
          ADD COLUMN fuera_de_lo_habitual  boolean NOT NULL DEFAULT false,
          ADD COLUMN nota_precio           text,
          ADD CONSTRAINT ck_compra_detalle_precio_referencia CHECK (precio_referencia IS NULL OR precio_referencia > 0),
          ADD CONSTRAINT ck_compra_detalle_fuera_con_referencia CHECK (NOT fuera_de_lo_habitual OR precio_referencia IS NOT NULL),
          ADD CONSTRAINT ck_compra_detalle_nota_precio CHECK (nota_precio IS NULL OR (btrim(nota_precio) <> '' AND length(nota_precio) <= 300))
        """
    )


def downgrade() -> None:
    op.execute(
        """
        ALTER TABLE compras.compra_detalle
          DROP CONSTRAINT IF EXISTS ck_compra_detalle_nota_precio,
          DROP CONSTRAINT IF EXISTS ck_compra_detalle_fuera_con_referencia,
          DROP CONSTRAINT IF EXISTS ck_compra_detalle_precio_referencia,
          DROP COLUMN IF EXISTS nota_precio,
          DROP COLUMN IF EXISTS fuera_de_lo_habitual,
          DROP COLUMN IF EXISTS precio_referencia
        """
    )
