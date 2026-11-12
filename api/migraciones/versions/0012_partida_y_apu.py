"""Partidas de la obra y su análisis de precios unitarios (HU-16): partida y partida_recurso.

  - partida: una partida del presupuesto de ESTA obra. El reporte del S10 trunca el código, así que la partida se identifica
    por código Y descripción (sin distinguir mayúsculas ni espacios de más): dos partidas con el mismo código truncado y
    descripciones distintas son dos partidas.
  - partida_recurso: cuánto de un recurso (insumo del catálogo) exige UNA unidad de la partida, en la unidad del S10.
    Un insumo una sola vez por partida.

Ambas son libros de solo-agregar: el APU importado es un registro de lo que dijo el S10 ese día; no se pisa (decisión D-P2).

Revision ID: 0012
Revises: 0011
"""
from alembic import op

from migraciones._utiles import conceder, solo_insertar

revision = "0012"
down_revision = "0011"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE compras.partida (
          id          bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id bigint      NOT NULL,
          codigo      text        NOT NULL,
          descripcion text        NOT NULL,
          unidad      text        NOT NULL,
          creado_por  bigint      NOT NULL,
          creado_en   timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_partida PRIMARY KEY (id),
          CONSTRAINT fk_partida_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_partida_creado_por_usuario FOREIGN KEY (creado_por) REFERENCES obra.usuario (id),
          CONSTRAINT uq_partida_proyecto_id_id UNIQUE (proyecto_id, id),
          CONSTRAINT ck_partida_textos CHECK (btrim(codigo) <> '' AND btrim(descripcion) <> '' AND btrim(unidad) <> '')
        )
        """
    )
    op.execute(
        "CREATE UNIQUE INDEX uq_partida_codigo_descripcion ON compras.partida (proyecto_id, codigo, lower(btrim(descripcion)))"
    )
    solo_insertar("compras.partida")

    op.execute(
        """
        CREATE TABLE compras.partida_recurso (
          id                  bigint GENERATED ALWAYS AS IDENTITY,
          partida_id          bigint         NOT NULL,
          insumo_id           bigint         NOT NULL,
          cantidad_por_unidad numeric(18, 6) NOT NULL,
          unidad              text           NOT NULL,
          creado_en           timestamptz    NOT NULL DEFAULT now(),
          CONSTRAINT pk_partida_recurso PRIMARY KEY (id),
          CONSTRAINT fk_partida_recurso_partida_id_partida FOREIGN KEY (partida_id) REFERENCES compras.partida (id),
          CONSTRAINT fk_partida_recurso_insumo_id_insumo FOREIGN KEY (insumo_id) REFERENCES catalogo.insumo (id),
          CONSTRAINT uq_partida_recurso_partida_id_insumo_id UNIQUE (partida_id, insumo_id),
          CONSTRAINT ck_partida_recurso_cantidad CHECK (cantidad_por_unidad > 0),
          CONSTRAINT ck_partida_recurso_unidad CHECK (btrim(unidad) <> '')
        )
        """
    )
    op.execute("CREATE INDEX ix_partida_recurso_insumo_id ON compras.partida_recurso (insumo_id)")
    solo_insertar("compras.partida_recurso")

    conceder("compras.partida")
    conceder("compras.partida_recurso")
    # Microservicios (0009): la 0009 copió los permisos de sigro_app a cada rol de servicio; una tabla nueva de compras
    # hay que concederla también a SU rol, o el servicio recibe «permiso denegado» (el mismo mínimo: solo agregar).
    op.execute("GRANT SELECT, INSERT ON compras.partida, compras.partida_recurso TO sigro_svc_compras")


def downgrade() -> None:
    op.execute("DROP TABLE compras.partida_recurso")
    op.execute("DROP TABLE compras.partida")
