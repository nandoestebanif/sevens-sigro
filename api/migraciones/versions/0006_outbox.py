"""Bandeja de salida (outbox) para el microservicio de Notificaciones.

Patrón outbox: el evento se escribe EN LA MISMA TRANSACCIÓN que el hecho que lo origina (una compra anulada,
un dispositivo que pide autorización, y mañana el movimiento que deja un insumo bajo su punto de reposición,
HU-26). Si la transacción se revierte, el evento no existe; si se confirma, el evento no se pierde aunque el
microservicio esté caído. Un relevo aparte (`python -m app.relay`) lo entrega con reintentos.

Frontera (ADR-01): el microservicio NO lee ni escribe esta base. Recibe el evento por HTTP y se queda con su
propia copia. La API resuelve los destinatarios al publicar (solo ella sabe quién es qué en cada obra).

Reglas del motor
  - El evento es inmutable: tipo, obra y contenido no se editan; solo se anota el resultado de la entrega.
  - Una entrega confirmada no se «des-confirma».
  - Nada se borra (R10).

Revision ID: 0006
Revises: 0005
"""
from alembic import op

from migraciones._utiles import conceder, sin_borrado

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("CREATE SCHEMA outbox")
    op.execute("GRANT USAGE ON SCHEMA outbox TO sigro_app")
    op.execute(
        """
        CREATE TABLE outbox.evento (
          id                bigint GENERATED ALWAYS AS IDENTITY,
          tipo              text        NOT NULL,
          proyecto_id       bigint      NOT NULL,
          payload           jsonb       NOT NULL,
          creado_en         timestamptz NOT NULL DEFAULT now(),
          enviado_en        timestamptz,
          intentos          smallint    NOT NULL DEFAULT 0,
          siguiente_intento timestamptz NOT NULL DEFAULT now(),
          ultimo_error      text,
          CONSTRAINT pk_evento PRIMARY KEY (id),
          CONSTRAINT fk_evento_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT ck_evento_tipo CHECK (tipo ~ '^[a-z]+(\\.[a-z_]+)+$'),
          CONSTRAINT ck_evento_payload_objeto CHECK (jsonb_typeof(payload) = 'object'),
          -- CASE y no AND: si la clave falta, jsonb_typeof es NULL y un CHECK en NULL se ACEPTA; aquí se rechaza.
          CONSTRAINT ck_evento_destinatarios CHECK (
            CASE WHEN jsonb_typeof(payload -> 'destinatarios') = 'array'
                 THEN jsonb_array_length(payload -> 'destinatarios') > 0
                 ELSE false END
          ),
          CONSTRAINT ck_evento_intentos CHECK (intentos >= 0),
          CONSTRAINT ck_evento_enviado_tras_intentar CHECK (enviado_en IS NULL OR intentos >= 1)
        )
        """
    )
    # La consulta caliente del relevo: lo pendiente, en orden, cuando le toca.
    op.execute(
        "CREATE INDEX ix_evento_pendientes ON outbox.evento (siguiente_intento, id) WHERE enviado_en IS NULL"
    )
    op.execute("CREATE INDEX ix_evento_proyecto_id ON outbox.evento (proyecto_id, id)")
    sin_borrado("outbox.evento")

    op.execute(
        """
        CREATE FUNCTION outbox.tg_evento_cambios() RETURNS trigger LANGUAGE plpgsql AS $$
        DECLARE
          resultado_de_entrega text[] := ARRAY['enviado_en', 'intentos', 'siguiente_intento', 'ultimo_error'];
        BEGIN
          IF (to_jsonb(NEW) - resultado_de_entrega) IS DISTINCT FROM (to_jsonb(OLD) - resultado_de_entrega) THEN
            RAISE EXCEPTION 'SIGRO-O1: un evento no se edita; solo se anota el resultado de su entrega'
              USING ERRCODE = 'restrict_violation';
          END IF;
          IF OLD.enviado_en IS NOT NULL AND NEW.enviado_en IS DISTINCT FROM OLD.enviado_en THEN
            RAISE EXCEPTION 'SIGRO-O2: un evento ya entregado no se vuelve a entregar ni se des-marca'
              USING ERRCODE = 'restrict_violation';
          END IF;
          IF NEW.intentos < OLD.intentos THEN
            RAISE EXCEPTION 'SIGRO-O3: el contador de intentos no retrocede' USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_evento_cambios BEFORE UPDATE ON outbox.evento "
        "FOR EACH ROW EXECUTE FUNCTION outbox.tg_evento_cambios()"
    )
    conceder("outbox.evento", columnas_update="enviado_en, intentos, siguiente_intento, ultimo_error")


def downgrade() -> None:
    op.execute("DROP SCHEMA outbox CASCADE")
