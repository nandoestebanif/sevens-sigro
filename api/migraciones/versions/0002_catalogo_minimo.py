"""Catálogo MÍNIMO: insumo y nombre comercial.

PROVISIONAL, de Fernando. Existe aquí solo porque compras y solicitud necesitan una
clave foránea hacia `catalogo.insumo`. Fernando amplía este módulo (parámetros de
reposición, requerimiento, historial del factor de conversión) con sus propias
migraciones encima de esta, coordinándose con Henry.

Reglas que sostiene
  R8  el mismo insumo lleva el mismo código en todas las obras: `codigo` único global
      y, una vez emitido, no se edita

Revision ID: 0002
Revises: 0001
"""
from alembic import op

from migraciones._utiles import conceder, sin_borrado

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("CREATE SCHEMA catalogo")
    op.execute("GRANT USAGE ON SCHEMA catalogo TO sigro_app")

    op.execute(
        """
        CREATE TABLE catalogo.insumo (
          id                bigint GENERATED ALWAYS AS IDENTITY,
          codigo            text           NOT NULL,
          descripcion       text           NOT NULL,
          unidad_s10        text           NOT NULL,
          unidad_compra     text           NOT NULL,
          factor_conversion numeric(18, 6) NOT NULL DEFAULT 1,
          tipo              text           NOT NULL,
          activo            boolean        NOT NULL DEFAULT true,
          -- Falso hasta que el residente revise la descripción de un insumo que el almacenero
          -- creó en obra. La importación del S10 lo manda explícitamente en true.
          verificado        boolean        NOT NULL DEFAULT false,
          creado_por        bigint,
          creado_en         timestamptz    NOT NULL DEFAULT now(),
          CONSTRAINT pk_insumo PRIMARY KEY (id),
          CONSTRAINT fk_insumo_creado_por_usuario FOREIGN KEY (creado_por) REFERENCES obra.usuario (id),
          CONSTRAINT uq_insumo_codigo UNIQUE (codigo),
          CONSTRAINT ck_insumo_codigo CHECK (btrim(codigo) <> ''),
          CONSTRAINT ck_insumo_descripcion CHECK (btrim(descripcion) <> ''),
          CONSTRAINT ck_insumo_unidades CHECK (btrim(unidad_s10) <> '' AND btrim(unidad_compra) <> ''),
          CONSTRAINT ck_insumo_factor CHECK (factor_conversion > 0),
          CONSTRAINT ck_insumo_tipo CHECK (tipo IN ('material', 'mano_obra', 'equipo', 'subcontrato'))
        )
        """
    )
    sin_borrado("catalogo.insumo")
    op.execute(
        """
        CREATE FUNCTION catalogo.tg_insumo_codigo_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF NEW.codigo <> OLD.codigo THEN
            RAISE EXCEPTION 'SIGRO-R8: el código de insumo % no se edita una vez emitido', OLD.codigo
              USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_insumo_codigo_inmutable BEFORE UPDATE ON catalogo.insumo "
        "FOR EACH ROW EXECUTE FUNCTION catalogo.tg_insumo_codigo_inmutable()"
    )

    op.execute(
        """
        CREATE TABLE catalogo.insumo_nombre_comercial (
          id        bigint GENERATED ALWAYS AS IDENTITY,
          insumo_id bigint NOT NULL,
          nombre    text   NOT NULL,
          CONSTRAINT pk_insumo_nombre_comercial PRIMARY KEY (id),
          CONSTRAINT fk_insumo_nombre_comercial_insumo_id_insumo FOREIGN KEY (insumo_id) REFERENCES catalogo.insumo (id),
          CONSTRAINT ck_insumo_nombre_comercial_nombre CHECK (btrim(nombre) <> '')
        )
        """
    )
    op.execute(
        "CREATE UNIQUE INDEX uq_insumo_nombre_comercial_insumo_nombre "
        "ON catalogo.insumo_nombre_comercial (insumo_id, lower(nombre))"
    )
    sin_borrado("catalogo.insumo_nombre_comercial")

    conceder(
        "catalogo.insumo",
        columnas_update="descripcion, unidad_compra, factor_conversion, activo, verificado",
    )
    conceder("catalogo.insumo_nombre_comercial")


def downgrade() -> None:
    op.execute("DROP SCHEMA catalogo CASCADE")
