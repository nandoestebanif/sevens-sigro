"""Requerimiento por obra (HU-19), saldo por comprar (HU-24) y parámetros de reposición (HU-25).

  - requerimiento: lo planificado en el S10 para ESTA obra, por insumo (cantidad y precio). El ajuste de la cantidad solo
    se admite con un renglón en requerimiento_ajuste (con motivo y autor) escrito en la MISMA transacción: lo impone un
    disparador, no solo la API.
  - requerimiento_ajuste: libro solo-agregar.
  - parametro_reposicion: stock de seguridad y punto de reposición por obra e insumo. Libro solo-agregar: un cambio es una
    fila nueva con su vigencia; la anterior no se pisa (regla de las vigencias).
  - vistas saldo_por_comprar (requerido − comprado, en la unidad del S10) y parametro_reposicion_vigente.

Supuestos (decisiones D-R1..D-R3 del LEEME): el factor de conversión es «unidades del S10 por unidad de compra»; lo comprado
cuenta solo compras `registrada`; los parámetros de reposición se expresan en unidad de compra.

Revision ID: 0008
Revises: 0007
"""
from alembic import op

from migraciones._utiles import conceder, sin_borrado, solo_insertar

revision = "0008"
down_revision = "0007"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE compras.requerimiento (
          id                   bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id          bigint         NOT NULL,
          insumo_id            bigint         NOT NULL,
          cantidad_planificada numeric(18, 4) NOT NULL,
          precio_planificado   numeric(18, 4) NOT NULL,
          creado_por           bigint         NOT NULL,
          creado_en            timestamptz    NOT NULL DEFAULT now(),
          CONSTRAINT pk_requerimiento PRIMARY KEY (id),
          CONSTRAINT fk_requerimiento_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_requerimiento_insumo_id_insumo FOREIGN KEY (insumo_id) REFERENCES catalogo.insumo (id),
          CONSTRAINT fk_requerimiento_creado_por_usuario FOREIGN KEY (creado_por) REFERENCES obra.usuario (id),
          CONSTRAINT uq_requerimiento_proyecto_id_insumo_id UNIQUE (proyecto_id, insumo_id),
          CONSTRAINT ck_requerimiento_cantidad CHECK (cantidad_planificada > 0),
          CONSTRAINT ck_requerimiento_precio CHECK (precio_planificado >= 0)
        )
        """
    )
    op.execute("CREATE INDEX ix_requerimiento_insumo_id ON compras.requerimiento (insumo_id)")
    sin_borrado("compras.requerimiento")

    op.execute(
        """
        CREATE TABLE compras.requerimiento_ajuste (
          id                bigint GENERATED ALWAYS AS IDENTITY,
          requerimiento_id  bigint         NOT NULL,
          cantidad_anterior numeric(18, 4) NOT NULL,
          cantidad_nueva    numeric(18, 4) NOT NULL,
          motivo            text           NOT NULL,
          ajustado_por      bigint         NOT NULL,
          ajustado_en       timestamptz    NOT NULL DEFAULT now(),
          CONSTRAINT pk_requerimiento_ajuste PRIMARY KEY (id),
          CONSTRAINT fk_requerimiento_ajuste_requerimiento_id_requerimiento FOREIGN KEY (requerimiento_id) REFERENCES compras.requerimiento (id),
          CONSTRAINT fk_requerimiento_ajuste_ajustado_por_usuario FOREIGN KEY (ajustado_por) REFERENCES obra.usuario (id),
          CONSTRAINT ck_requerimiento_ajuste_cantidades CHECK (cantidad_anterior > 0 AND cantidad_nueva > 0 AND cantidad_nueva <> cantidad_anterior),
          CONSTRAINT ck_requerimiento_ajuste_motivo CHECK (btrim(motivo) <> '')
        )
        """
    )
    op.execute("CREATE INDEX ix_requerimiento_ajuste_requerimiento_id ON compras.requerimiento_ajuste (requerimiento_id, id)")
    solo_insertar("compras.requerimiento_ajuste")

    # La cantidad planificada solo cambia junto con su ajuste (mismo instante de transacción) y nada más se edita.
    op.execute(
        """
        CREATE FUNCTION compras.tg_requerimiento_ajuste_obligatorio() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF (NEW.proyecto_id, NEW.insumo_id, NEW.precio_planificado, NEW.creado_por, NEW.creado_en)
             IS DISTINCT FROM (OLD.proyecto_id, OLD.insumo_id, OLD.precio_planificado, OLD.creado_por, OLD.creado_en) THEN
            RAISE EXCEPTION 'SIGRO-Q1: del requerimiento solo se ajusta la cantidad' USING ERRCODE = 'restrict_violation';
          END IF;
          IF NEW.cantidad_planificada <> OLD.cantidad_planificada AND NOT EXISTS (
              SELECT 1 FROM compras.requerimiento_ajuste a
               WHERE a.requerimiento_id = NEW.id AND a.cantidad_anterior = OLD.cantidad_planificada
                 AND a.cantidad_nueva = NEW.cantidad_planificada AND a.ajustado_en = now()) THEN
            RAISE EXCEPTION 'SIGRO-Q2: ajustar la cantidad exige registrar el ajuste con su motivo' USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_requerimiento_ajuste_obligatorio BEFORE UPDATE ON compras.requerimiento "
        "FOR EACH ROW EXECUTE FUNCTION compras.tg_requerimiento_ajuste_obligatorio()"
    )

    op.execute(
        """
        CREATE TABLE compras.parametro_reposicion (
          id              bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id     bigint         NOT NULL,
          insumo_id       bigint         NOT NULL,
          stock_seguridad numeric(18, 4) NOT NULL,
          punto_reposicion numeric(18, 4) NOT NULL,
          registrado_por  bigint         NOT NULL,
          vigente_desde   timestamptz    NOT NULL DEFAULT now(),
          CONSTRAINT pk_parametro_reposicion PRIMARY KEY (id),
          CONSTRAINT fk_parametro_reposicion_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_parametro_reposicion_insumo_id_insumo FOREIGN KEY (insumo_id) REFERENCES catalogo.insumo (id),
          CONSTRAINT fk_parametro_reposicion_registrado_por_usuario FOREIGN KEY (registrado_por) REFERENCES obra.usuario (id),
          CONSTRAINT ck_parametro_reposicion_valores CHECK (stock_seguridad >= 0 AND punto_reposicion >= stock_seguridad)
        )
        """
    )
    op.execute("CREATE INDEX ix_parametro_reposicion_vigencia ON compras.parametro_reposicion (proyecto_id, insumo_id, id DESC)")
    solo_insertar("compras.parametro_reposicion")

    op.execute(
        """
        CREATE VIEW compras.parametro_reposicion_vigente AS
        SELECT DISTINCT ON (proyecto_id, insumo_id) id, proyecto_id, insumo_id, stock_seguridad, punto_reposicion,
               registrado_por, vigente_desde
          FROM compras.parametro_reposicion
         ORDER BY proyecto_id, insumo_id, id DESC
        """
    )
    op.execute(
        """
        CREATE VIEW compras.saldo_por_comprar AS
        SELECT r.id AS requerimiento_id, r.proyecto_id, r.insumo_id,
               r.cantidad_planificada AS requerido,
               coalesce(c.comprado_unidad_compra, 0) * i.factor_conversion AS comprado,
               greatest(r.cantidad_planificada - coalesce(c.comprado_unidad_compra, 0) * i.factor_conversion, 0) AS saldo,
               greatest(coalesce(c.comprado_unidad_compra, 0) * i.factor_conversion - r.cantidad_planificada, 0) AS exceso
          FROM compras.requerimiento r
          JOIN catalogo.insumo i ON i.id = r.insumo_id
          LEFT JOIN (
                SELECT co.proyecto_id, d.insumo_id, sum(d.cantidad) AS comprado_unidad_compra
                  FROM compras.compra co JOIN compras.compra_detalle d ON d.compra_id = co.id
                 WHERE co.estado = 'registrada'
                 GROUP BY co.proyecto_id, d.insumo_id
          ) c ON c.proyecto_id = r.proyecto_id AND c.insumo_id = r.insumo_id
        """
    )

    conceder("compras.requerimiento", columnas_update="cantidad_planificada")
    conceder("compras.requerimiento_ajuste")
    conceder("compras.parametro_reposicion")
    conceder("compras.parametro_reposicion_vigente", "SELECT")
    conceder("compras.saldo_por_comprar", "SELECT")


def downgrade() -> None:
    op.execute("DROP VIEW compras.saldo_por_comprar")
    op.execute("DROP VIEW compras.parametro_reposicion_vigente")
    op.execute("DROP TABLE compras.parametro_reposicion")
    op.execute("DROP TABLE compras.requerimiento_ajuste")
    op.execute("DROP TABLE compras.requerimiento")
    op.execute("DROP FUNCTION compras.tg_requerimiento_ajuste_obligatorio()")
