"""Comprobantes de compra (HU-34): anulación y dónde se guarda el archivo (D15, ADR-11).

La tabla `compras.compra_adjunto` ya existe desde la 0004 (huella sha256 única por compra, tipo, tamaño). NO se crea otra.
Esta migración agrega solo lo que faltaba:

  1. Anulación (estado E12: adjunto → anulado). El comprobante nunca se borra ni se reemplaza: si se subió mal, se anula
     con su motivo y se sube el correcto. `anulado_por`, `anulado_en` y `motivo_anulacion` van juntos o ninguno, y el motivo
     tiene al menos 5 caracteres. La tabla deja de ser «solo insertar» para admitir exactamente eso: un UPDATE que toque solo
     esas tres columnas y solo una vez; cualquier otro cambio, el DELETE y el TRUNCATE siguen rechazados por el motor.
  2. `almacenamiento` ('bd' hoy, 's3' cuando se migre) para que el día del cambio cada archivo diga dónde está; `archivo`
     sigue siendo la clave dentro de ese almacenamiento.
  3. `compras.compra_adjunto_contenido(adjunto_id, contenido bytea)`: los bytes, en su propia tabla para que listar
     comprobantes nunca arrastre archivos (ADR-11, opción 1: Neon ahora, S3/R2 después). Solo insertar, sin borrado.
  4. El tamaño máximo baja de 15 MB a 10 MB (decisión del PO, 12/11/2026): una foto de factura pesa 1–4 MB y el plan gratuito
     de Neon tiene 0,5 GB. Si hubiera filas anteriores de más de 10 MB la migración se detiene y lo dice (en la práctica no
     hay: hasta la 0014 ningún endpoint subía comprobantes).

Permisos: la 0009 copió los permisos de sigro_app al rol del servicio; lo nuevo se concede a los dos (como en 0012 y 0014).

Revision ID: 0015
Revises: 0014
"""
from alembic import op

from migraciones._utiles import sin_borrado, solo_insertar

revision = "0015"
down_revision = "0014"
branch_labels = None
depends_on = None

COLUMNAS_ANULACION = "anulado_por, anulado_en, motivo_anulacion"


def upgrade() -> None:
    op.execute(
        """
        DO $$ BEGIN
          IF EXISTS (SELECT 1 FROM compras.compra_adjunto WHERE tamano_bytes > 10 * 1024 * 1024) THEN
            RAISE EXCEPTION 'Hay comprobantes de más de 10 MB; decide qué hacer con ellos antes de bajar el límite (no se borran).';
          END IF;
        END $$
        """
    )
    # 1) La tabla pasa de «solo insertar» a «solo insertar + anular una vez».
    op.execute("DROP TRIGGER tg_compras_compra_adjunto_append_only ON compras.compra_adjunto")
    op.execute(
        """
        ALTER TABLE compras.compra_adjunto
          ADD COLUMN almacenamiento   text   NOT NULL DEFAULT 'bd',
          ADD COLUMN anulado_por      bigint,
          ADD COLUMN anulado_en       timestamptz,
          ADD COLUMN motivo_anulacion text,
          ADD CONSTRAINT fk_compra_adjunto_anulado_por_usuario FOREIGN KEY (anulado_por) REFERENCES obra.usuario (id),
          ADD CONSTRAINT ck_compra_adjunto_almacenamiento CHECK (almacenamiento IN ('bd', 's3')),
          ADD CONSTRAINT ck_compra_adjunto_anulacion CHECK (
            (anulado_por IS NULL AND anulado_en IS NULL AND motivo_anulacion IS NULL)
            OR (anulado_por IS NOT NULL AND anulado_en IS NOT NULL AND length(btrim(coalesce(motivo_anulacion, ''))) >= 5)
          ),
          DROP CONSTRAINT ck_compra_adjunto_tamano,
          ADD CONSTRAINT ck_compra_adjunto_tamano CHECK (tamano_bytes > 0 AND tamano_bytes <= 10 * 1024 * 1024)
        """
    )
    op.execute(
        f"""
        CREATE FUNCTION compras.tg_compra_adjunto_anular() RETURNS trigger LANGUAGE plpgsql AS $$
        DECLARE
          columnas text[] := ARRAY['anulado_por', 'anulado_en', 'motivo_anulacion'];
        BEGIN
          IF OLD.anulado_en IS NOT NULL THEN
            RAISE EXCEPTION 'SIGRO-A1: el comprobante ya está anulado' USING ERRCODE = 'restrict_violation';
          END IF;
          IF (to_jsonb(NEW) - columnas) IS DISTINCT FROM (to_jsonb(OLD) - columnas) THEN
            RAISE EXCEPTION 'SIGRO-R1: un comprobante no se edita ni se reemplaza; se anula con su motivo y se sube el correcto'
              USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_compra_adjunto_anular BEFORE UPDATE ON compras.compra_adjunto "
        "FOR EACH ROW EXECUTE FUNCTION compras.tg_compra_adjunto_anular()"
    )
    op.execute(
        "CREATE TRIGGER tg_compra_adjunto_no_borrar BEFORE DELETE ON compras.compra_adjunto "
        "FOR EACH ROW EXECUTE FUNCTION util.rechazar_borrado()"
    )
    op.execute(f"GRANT UPDATE ({COLUMNAS_ANULACION}) ON compras.compra_adjunto TO sigro_app")
    op.execute(f"GRANT UPDATE ({COLUMNAS_ANULACION}) ON compras.compra_adjunto TO sigro_svc_compras")

    # 2) Los bytes, aparte (ADR-11).
    op.execute(
        """
        CREATE TABLE compras.compra_adjunto_contenido (
          adjunto_id bigint NOT NULL,
          contenido  bytea  NOT NULL,
          CONSTRAINT pk_compra_adjunto_contenido PRIMARY KEY (adjunto_id),
          CONSTRAINT fk_compra_adjunto_contenido_adjunto_id FOREIGN KEY (adjunto_id) REFERENCES compras.compra_adjunto (id),
          CONSTRAINT ck_compra_adjunto_contenido_tamano CHECK (length(contenido) BETWEEN 1 AND 10 * 1024 * 1024)
        )
        """
    )
    solo_insertar("compras.compra_adjunto_contenido")
    op.execute("GRANT SELECT, INSERT ON compras.compra_adjunto_contenido TO sigro_app")
    op.execute("GRANT SELECT, INSERT ON compras.compra_adjunto_contenido TO sigro_svc_compras")


def downgrade() -> None:
    # Nada se borra: si ya hay archivos guardados, volver atrás los destruiría. Se detiene y lo dice.
    op.execute(
        """
        DO $$ BEGIN
          IF EXISTS (SELECT 1 FROM compras.compra_adjunto_contenido) THEN
            RAISE EXCEPTION 'Hay comprobantes guardados en compras.compra_adjunto_contenido: volver atrás los perdería. Exporta los archivos antes.';
          END IF;
        END $$
        """
    )
    op.execute("DROP TABLE compras.compra_adjunto_contenido")
    op.execute("DROP TRIGGER tg_compra_adjunto_no_borrar ON compras.compra_adjunto")
    op.execute("DROP TRIGGER tg_compra_adjunto_anular ON compras.compra_adjunto")
    op.execute("DROP FUNCTION compras.tg_compra_adjunto_anular()")
    op.execute(
        """
        ALTER TABLE compras.compra_adjunto
          DROP CONSTRAINT ck_compra_adjunto_tamano,
          ADD CONSTRAINT ck_compra_adjunto_tamano CHECK (tamano_bytes > 0 AND tamano_bytes <= 15 * 1024 * 1024),
          DROP CONSTRAINT ck_compra_adjunto_anulacion,
          DROP CONSTRAINT ck_compra_adjunto_almacenamiento,
          DROP CONSTRAINT fk_compra_adjunto_anulado_por_usuario,
          DROP COLUMN motivo_anulacion,
          DROP COLUMN anulado_en,
          DROP COLUMN anulado_por,
          DROP COLUMN almacenamiento
        """
    )
    op.execute(
        "CREATE TRIGGER tg_compras_compra_adjunto_append_only BEFORE UPDATE OR DELETE ON compras.compra_adjunto "
        "FOR EACH ROW EXECUTE FUNCTION util.rechazar_modificacion()"
    )
