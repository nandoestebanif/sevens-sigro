"""Solicitud de materiales (CR-04, Sprint 2): solicitud, líneas y enlace desde la compra.

NO es el `requerimiento` del S10 (el total de obra, tope de control). Es el pedido
periódico que el residente emite y que origina las compras.

Dos reglas del modelo que esta migración hace cumplir en el motor
  - El estado NO es un campo que alguien escriba. Aquí solo se guarda si la solicitud
    está emitida o anulada; los estados solicitada / comprada en parte / comprada se
    DERIVAN de las compras en la vista `compras.solicitud_estado`.
  - El número es correlativo por obra, sin huecos, y lo asigna el servidor.

Limitación conocida (decisión D-S1): «recibida en parte» y «recibida» dependen de las
recepciones, que viven en almacén (Henry). Esta vista no cruza de módulo; el servicio
de compras compone ese último tramo llamando a la interfaz de almacén.

Revision ID: 0005
Revises: 0004
"""
from alembic import op

from migraciones._utiles import conceder, sin_borrado, solo_insertar

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE compras.solicitud_correlativo (
          proyecto_id bigint  NOT NULL,
          ultimo      integer NOT NULL DEFAULT 0,
          CONSTRAINT pk_solicitud_correlativo PRIMARY KEY (proyecto_id),
          CONSTRAINT fk_solicitud_correlativo_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT ck_solicitud_correlativo_ultimo CHECK (ultimo >= 0)
        )
        """
    )
    sin_borrado("compras.solicitud_correlativo")

    op.execute(
        """
        CREATE TABLE compras.solicitud (
          id               bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id      bigint      NOT NULL,
          numero           integer     NOT NULL,
          fecha            date        NOT NULL DEFAULT current_date,
          estado           text        NOT NULL DEFAULT 'emitida',
          motivo_anulacion text,
          anulada_por      bigint,
          anulada_en       timestamptz,
          emitida_por      bigint      NOT NULL,
          creado_en        timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_solicitud PRIMARY KEY (id),
          CONSTRAINT fk_solicitud_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_solicitud_emitida_por_usuario FOREIGN KEY (emitida_por) REFERENCES obra.usuario (id),
          CONSTRAINT fk_solicitud_anulada_por_usuario FOREIGN KEY (anulada_por) REFERENCES obra.usuario (id),
          CONSTRAINT uq_solicitud_proyecto_id_numero UNIQUE (proyecto_id, numero),
          CONSTRAINT uq_solicitud_id_proyecto_id UNIQUE (id, proyecto_id),
          CONSTRAINT ck_solicitud_numero CHECK (numero > 0),
          CONSTRAINT ck_solicitud_estado CHECK (estado IN ('emitida', 'anulada')),
          CONSTRAINT ck_solicitud_anulacion CHECK (
            (estado = 'emitida' AND motivo_anulacion IS NULL AND anulada_por IS NULL AND anulada_en IS NULL)
            OR (estado = 'anulada' AND btrim(coalesce(motivo_anulacion, '')) <> ''
                AND anulada_por IS NOT NULL AND anulada_en IS NOT NULL)
          )
        )
        """
    )
    sin_borrado("compras.solicitud")

    # El correlativo se asigna dentro de la misma transacción que inserta la solicitud: el
    # UPDATE bloquea la fila del correlativo, así dos solicitudes simultáneas de una misma
    # obra se serializan, y si la transacción se revierte el número también (sin huecos).
    op.execute(
        """
        CREATE FUNCTION compras.tg_solicitud_numero() RETURNS trigger
        LANGUAGE plpgsql SECURITY DEFINER SET search_path = compras, pg_temp AS $$
        BEGIN
          INSERT INTO compras.solicitud_correlativo AS c (proyecto_id, ultimo)
          VALUES (NEW.proyecto_id, 1)
          ON CONFLICT (proyecto_id) DO UPDATE SET ultimo = c.ultimo + 1
          RETURNING c.ultimo INTO NEW.numero;     -- siempre el del servidor, nunca el del cliente
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_solicitud_numero BEFORE INSERT ON compras.solicitud "
        "FOR EACH ROW EXECUTE FUNCTION compras.tg_solicitud_numero()"
    )
    op.execute(
        """
        CREATE FUNCTION compras.tg_solicitud_cambios() RETURNS trigger LANGUAGE plpgsql AS $$
        DECLARE
          columnas_de_anulacion text[] := ARRAY['estado', 'motivo_anulacion', 'anulada_por', 'anulada_en'];
        BEGIN
          IF OLD.estado = 'anulada' THEN
            RAISE EXCEPTION 'SIGRO-S1: la solicitud ya está anulada' USING ERRCODE = 'restrict_violation';
          END IF;
          IF (to_jsonb(NEW) - columnas_de_anulacion) IS DISTINCT FROM (to_jsonb(OLD) - columnas_de_anulacion) THEN
            RAISE EXCEPTION 'SIGRO-S1: una solicitud emitida no se edita; se anula con su motivo y se emite otra'
              USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_solicitud_cambios BEFORE UPDATE ON compras.solicitud "
        "FOR EACH ROW EXECUTE FUNCTION compras.tg_solicitud_cambios()"
    )

    op.execute(
        """
        CREATE TABLE compras.solicitud_linea (
          id               bigint GENERATED ALWAYS AS IDENTITY,
          solicitud_id     bigint         NOT NULL,
          insumo_id        bigint,                 -- opcional: puede ser un insumo aún no catalogado
          descripcion_libre text,
          cantidad         numeric(18, 4) NOT NULL, -- en UNIDAD DE COMPRA, ya desagregada por el residente
          unidad_compra    text           NOT NULL,
          CONSTRAINT pk_solicitud_linea PRIMARY KEY (id),
          CONSTRAINT fk_solicitud_linea_solicitud_id_solicitud FOREIGN KEY (solicitud_id) REFERENCES compras.solicitud (id),
          CONSTRAINT fk_solicitud_linea_insumo_id_insumo FOREIGN KEY (insumo_id) REFERENCES catalogo.insumo (id),
          CONSTRAINT ck_solicitud_linea_cantidad CHECK (cantidad > 0),
          CONSTRAINT ck_solicitud_linea_unidad CHECK (btrim(unidad_compra) <> ''),
          CONSTRAINT ck_solicitud_linea_describe CHECK (
            insumo_id IS NOT NULL OR btrim(coalesce(descripcion_libre, '')) <> ''
          )
        )
        """
    )
    op.execute(
        "CREATE UNIQUE INDEX uq_solicitud_linea_solicitud_insumo ON compras.solicitud_linea (solicitud_id, insumo_id) "
        "WHERE insumo_id IS NOT NULL"
    )
    op.execute("CREATE INDEX ix_solicitud_linea_insumo_id ON compras.solicitud_linea (insumo_id) WHERE insumo_id IS NOT NULL")
    solo_insertar("compras.solicitud_linea")
    op.execute(
        """
        CREATE FUNCTION compras.tg_solicitud_linea_vigente() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF (SELECT estado FROM compras.solicitud WHERE id = NEW.solicitud_id) = 'anulada' THEN
            RAISE EXCEPTION 'SIGRO-S2: no se agregan líneas a una solicitud anulada' USING ERRCODE = 'check_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_solicitud_linea_vigente BEFORE INSERT ON compras.solicitud_linea "
        "FOR EACH ROW EXECUTE FUNCTION compras.tg_solicitud_linea_vigente()"
    )

    # ------------------------------------- la compra se enlaza con la solicitud (opcional)
    op.execute("ALTER TABLE compras.compra ADD COLUMN solicitud_id bigint")
    op.execute(
        "ALTER TABLE compras.compra ADD CONSTRAINT fk_compra_solicitud_id_solicitud "
        "FOREIGN KEY (solicitud_id, proyecto_id) REFERENCES compras.solicitud (id, proyecto_id)"
    )
    op.execute("CREATE INDEX ix_compra_solicitud_id ON compras.compra (solicitud_id) WHERE solicitud_id IS NOT NULL")
    op.execute(
        """
        CREATE FUNCTION compras.tg_compra_solicitud_vigente() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF NEW.solicitud_id IS NOT NULL
             AND (SELECT estado FROM compras.solicitud WHERE id = NEW.solicitud_id) = 'anulada' THEN
            RAISE EXCEPTION 'SIGRO-S3: la solicitud está anulada y no admite compras' USING ERRCODE = 'check_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_compra_solicitud_vigente BEFORE INSERT ON compras.compra "
        "FOR EACH ROW EXECUTE FUNCTION compras.tg_compra_solicitud_vigente()"
    )

    # ------------------------------------------------------- estado DERIVADO (vistas)
    op.execute(
        """
        CREATE VIEW compras.solicitud_linea_avance AS
        SELECT l.id            AS linea_id,
               l.solicitud_id,
               l.insumo_id,
               l.cantidad      AS cantidad_solicitada,
               l.unidad_compra,
               coalesce(c.comprado, 0)                              AS cantidad_comprada,
               greatest(l.cantidad - coalesce(c.comprado, 0), 0)    AS cantidad_pendiente
          FROM compras.solicitud_linea l
          LEFT JOIN LATERAL (
                SELECT sum(d.cantidad) AS comprado
                  FROM compras.compra co
                  JOIN compras.compra_detalle d ON d.compra_id = co.id
                 WHERE co.solicitud_id = l.solicitud_id
                   AND co.estado = 'registrada'
                   AND d.insumo_id = l.insumo_id
          ) c ON l.insumo_id IS NOT NULL
        """
    )
    op.execute(
        """
        CREATE VIEW compras.solicitud_estado AS
        SELECT s.id AS solicitud_id, s.proyecto_id, s.numero,
               CASE
                 WHEN s.estado = 'anulada'                                   THEN 'anulada'
                 WHEN count(a.linea_id) > 0
                      AND bool_and(a.cantidad_comprada >= a.cantidad_solicitada) THEN 'comprada'
                 WHEN coalesce(bool_or(a.cantidad_comprada > 0), false)      THEN 'comprada_en_parte'
                 ELSE 'solicitada'
               END AS estado_derivado
          FROM compras.solicitud s
          LEFT JOIN compras.solicitud_linea_avance a ON a.solicitud_id = s.id
         GROUP BY s.id
        """
    )

    # ------------------------------------------------------------------ permisos
    conceder("compras.solicitud", columnas_update="estado, motivo_anulacion, anulada_por, anulada_en")
    conceder("compras.solicitud_linea")
    conceder("compras.solicitud_linea_avance", "SELECT")
    conceder("compras.solicitud_estado", "SELECT")
    # el correlativo lo toca solo la función SECURITY DEFINER: la aplicación no tiene permiso directo


def downgrade() -> None:
    op.execute("DROP VIEW compras.solicitud_estado")
    op.execute("DROP VIEW compras.solicitud_linea_avance")
    op.execute("DROP TRIGGER tg_compra_solicitud_vigente ON compras.compra")
    op.execute("DROP FUNCTION compras.tg_compra_solicitud_vigente()")
    op.execute("ALTER TABLE compras.compra DROP COLUMN solicitud_id")   # arrastra su FK e índice
    op.execute("DROP TABLE compras.solicitud_linea")
    op.execute("DROP TABLE compras.solicitud")
    op.execute("DROP TABLE compras.solicitud_correlativo")
    op.execute("DROP FUNCTION compras.tg_solicitud_numero()")
    op.execute("DROP FUNCTION compras.tg_solicitud_cambios()")
    op.execute("DROP FUNCTION compras.tg_solicitud_linea_vigente()")
