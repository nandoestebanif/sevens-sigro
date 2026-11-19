"""Migración 0018 (va DESPUÉS de la 0017). Insumos críticos de la obra (HU-28, T-342 / D14).

El filtro «Solo críticos» de reposición salía vacío porque nada en el sistema marcaba un insumo como crítico. Esta
migración agrega el registro de esas marcas:

  - compras.insumo_critico: libro solo-agregar. Marcar o desmarcar es una fila nueva (proyecto, insumo, critico true/false,
    quién y cuándo); la anterior no se pisa (regla de las vigencias, igual que parametro_reposicion). Nada se borra.
  - compras.insumo_critico_vigente: la última marca de cada insumo en cada obra.

El control del costo sigue siendo por insumo (regla 6): la marca es por obra e insumo, no por partida.

Base con datos: la tabla nace vacía; ningún insumo es crítico hasta que el residente lo marque.
Vuelta atrás: si ya hay marcas, el downgrade NO las borra (regla 10: nada se borra) y se detiene con un mensaje.

Autorizada por el PO (Víctor Adiel Luján) el 18/11/2026; la redacta Claude a su pedido. Revisar antes de aplicar.

Revision ID: 0018
Revises: 0017
"""
from alembic import op

from migraciones._utiles import conceder, solo_insertar

revision = "0018"
down_revision = "0017"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE compras.insumo_critico (
          id             bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id    bigint      NOT NULL,
          insumo_id      bigint      NOT NULL,
          critico        boolean     NOT NULL,
          registrado_por bigint      NOT NULL,
          registrado_en  timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_insumo_critico PRIMARY KEY (id),
          CONSTRAINT fk_insumo_critico_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_insumo_critico_insumo_id_insumo FOREIGN KEY (insumo_id) REFERENCES catalogo.insumo (id),
          CONSTRAINT fk_insumo_critico_registrado_por_usuario FOREIGN KEY (registrado_por) REFERENCES obra.usuario (id)
        )
        """
    )
    op.execute("CREATE INDEX ix_insumo_critico_vigencia ON compras.insumo_critico (proyecto_id, insumo_id, id DESC)")
    solo_insertar("compras.insumo_critico")
    op.execute(
        """
        CREATE VIEW compras.insumo_critico_vigente AS
        SELECT DISTINCT ON (proyecto_id, insumo_id) id, proyecto_id, insumo_id, critico, registrado_por, registrado_en
          FROM compras.insumo_critico
         ORDER BY proyecto_id, insumo_id, id DESC
        """
    )
    conceder("compras.insumo_critico")
    conceder("compras.insumo_critico_vigente", "SELECT")
    # la 0009 copió los privilegios una sola vez: lo nuevo se concede también al rol del servicio
    op.execute("GRANT SELECT, INSERT ON compras.insumo_critico TO sigro_svc_compras")
    op.execute("GRANT SELECT ON compras.insumo_critico_vigente TO sigro_svc_compras")


def downgrade() -> None:
    op.execute(
        """
        DO $$
        BEGIN
          IF EXISTS (SELECT 1 FROM compras.insumo_critico) THEN
            RAISE EXCEPTION 'Ya hay marcas de insumos críticos registradas: no se borran (regla 10). Decide qué hacer con ellas antes de bajar la 0018.';
          END IF;
        END $$
        """
    )
    op.execute("DROP VIEW compras.insumo_critico_vigente")
    op.execute("DROP TABLE compras.insumo_critico")
