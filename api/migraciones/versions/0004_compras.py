"""Módulo compras: proveedor, compra, detalle y adjuntos.

Reglas que sostiene
  - `proveedor.ruc` único (HU-30). Los 12 documentos inválidos de la lista real del cliente
    se cargan con estado_validacion = 'sin_validar'; solo 'validado' exige RUC con dígito
    verificador correcto, y solo se marca validado cuando SUNAT respondió.
  - Registrar la compra NO mueve stock. Aquí no hay ninguna referencia a movimientos: el
    stock lo mueve la recepción, en almacén (HU-32, HU-35).
  - Una compra registrada no se edita: se anula con su motivo (R10) y se registra otra.
  - Un comprobante (proveedor, tipo, número) no se registra dos veces (HU-32).

Revision ID: 0004
Revises: 0003
"""
from alembic import op

from migraciones._utiles import conceder, sin_borrado, solo_insertar

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("CREATE SCHEMA compras")
    op.execute("GRANT USAGE ON SCHEMA compras TO sigro_app")

    # Dígito verificador del RUC (módulo 11, pesos 5-4-3-2-7-6-5-4-3-2). Comprobado contra
    # el RUC de SEVENS INGENIEROS SAC (20606456892).
    op.execute(
        """
        CREATE FUNCTION compras.ruc_valido(p text) RETURNS boolean
        LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
          SELECT p ~ '^(10|15|16|17|20)[0-9]{9}$'
             AND ((11 - (SELECT sum(substr(p, t.i::int, 1)::int * t.w)
                           FROM unnest(ARRAY[5,4,3,2,7,6,5,4,3,2]) WITH ORDINALITY AS t(w, i)) % 11) % 10)
                 = substr(p, 11, 1)::int
        $$
        """
    )

    # ---------------------------------------------------------------- proveedor
    op.execute(
        """
        CREATE TABLE compras.proveedor (
          id                bigint GENERATED ALWAYS AS IDENTITY,
          ruc               text        NOT NULL,   -- en la semilla del cliente: numero_documento
          razon_social      text        NOT NULL,
          estado_validacion text        NOT NULL DEFAULT 'sin_validar',
          validado_en       timestamptz,
          estado            text        NOT NULL DEFAULT 'activo',
          fecha_baja        timestamptz,
          creado_en         timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_proveedor PRIMARY KEY (id),
          CONSTRAINT uq_proveedor_ruc UNIQUE (ruc),
          CONSTRAINT ck_proveedor_ruc_formato CHECK (ruc ~ '^[0-9A-Za-z-]{4,20}$'),
          CONSTRAINT ck_proveedor_razon_social CHECK (btrim(razon_social) <> ''),
          CONSTRAINT ck_proveedor_estado_validacion CHECK (estado_validacion IN ('validado', 'sin_validar')),
          CONSTRAINT ck_proveedor_validacion_con_fecha CHECK ((estado_validacion = 'validado') = (validado_en IS NOT NULL)),
          CONSTRAINT ck_proveedor_validado_ruc_correcto CHECK (estado_validacion = 'sin_validar' OR compras.ruc_valido(ruc)),
          CONSTRAINT ck_proveedor_estado CHECK (estado IN ('activo', 'baja')),
          CONSTRAINT ck_proveedor_baja_con_fecha CHECK ((estado = 'baja') = (fecha_baja IS NOT NULL))
        )
        """
    )
    op.execute("CREATE INDEX ix_proveedor_razon_social ON compras.proveedor (lower(razon_social) text_pattern_ops)")
    sin_borrado("compras.proveedor")
    op.execute(
        """
        CREATE FUNCTION compras.tg_proveedor_cambios() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF NEW.ruc <> OLD.ruc THEN
            RAISE EXCEPTION 'SIGRO: el RUC de un proveedor no se edita; si estaba mal, se registra el proveedor correcto'
              USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_proveedor_cambios BEFORE UPDATE ON compras.proveedor "
        "FOR EACH ROW EXECUTE FUNCTION compras.tg_proveedor_cambios()"
    )

    # ------------------------------------------------------------------- compra
    op.execute(
        """
        CREATE TABLE compras.compra (
          id                 bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id        bigint         NOT NULL,
          proveedor_id       bigint         NOT NULL,
          tipo_comprobante   text           NOT NULL,
          numero_comprobante text           NOT NULL,
          fecha              date           NOT NULL,
          moneda             text           NOT NULL DEFAULT 'PEN',
          base_imponible     numeric(14, 2) NOT NULL,
          igv                numeric(14, 2) NOT NULL DEFAULT 0,
          total              numeric(14, 2) NOT NULL,
          estado             text           NOT NULL DEFAULT 'registrada',
          motivo_anulacion   text,
          anulada_por        bigint,
          anulada_en         timestamptz,
          registrado_por     bigint         NOT NULL,
          creado_en          timestamptz    NOT NULL DEFAULT now(),
          CONSTRAINT pk_compra PRIMARY KEY (id),
          CONSTRAINT fk_compra_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_compra_proveedor_id_proveedor FOREIGN KEY (proveedor_id) REFERENCES compras.proveedor (id),
          CONSTRAINT fk_compra_anulada_por_usuario FOREIGN KEY (anulada_por) REFERENCES obra.usuario (id),
          CONSTRAINT fk_compra_registrado_por_usuario FOREIGN KEY (registrado_por) REFERENCES obra.usuario (id),
          CONSTRAINT uq_compra_proveedor_id_tipo_comprobante_numero_comprobante
            UNIQUE (proveedor_id, tipo_comprobante, numero_comprobante),
          CONSTRAINT uq_compra_id_proyecto_id UNIQUE (id, proyecto_id),
          CONSTRAINT ck_compra_tipo_comprobante CHECK (tipo_comprobante IN ('factura', 'boleta', 'nota_venta', 'otro')),
          CONSTRAINT ck_compra_numero_comprobante CHECK (numero_comprobante ~ '^[A-Za-z0-9]{1,4}-[0-9]{1,10}$'),
          CONSTRAINT ck_compra_moneda CHECK (moneda IN ('PEN', 'USD')),
          CONSTRAINT ck_compra_montos CHECK (base_imponible >= 0 AND igv >= 0 AND total = base_imponible + igv),
          CONSTRAINT ck_compra_estado CHECK (estado IN ('registrada', 'anulada')),
          CONSTRAINT ck_compra_anulacion CHECK (
            (estado = 'registrada' AND motivo_anulacion IS NULL AND anulada_por IS NULL AND anulada_en IS NULL)
            OR (estado = 'anulada' AND btrim(coalesce(motivo_anulacion, '')) <> ''
                AND anulada_por IS NOT NULL AND anulada_en IS NOT NULL)
          )
        )
        """
    )
    op.execute("CREATE INDEX ix_compra_proyecto_id_fecha ON compras.compra (proyecto_id, fecha DESC)")
    op.execute("CREATE INDEX ix_compra_proveedor_id ON compras.compra (proveedor_id)")
    sin_borrado("compras.compra")

    op.execute(
        """
        CREATE FUNCTION compras.tg_compra_validar() RETURNS trigger LANGUAGE plpgsql AS $$
        DECLARE
          prov compras.proveedor%ROWTYPE;
          proy obra.proyecto%ROWTYPE;
          columnas_de_anulacion text[] := ARRAY['estado', 'motivo_anulacion', 'anulada_por', 'anulada_en'];
        BEGIN
          IF TG_OP = 'INSERT' THEN
            SELECT * INTO prov FROM compras.proveedor WHERE id = NEW.proveedor_id;
            IF prov.id IS NOT NULL AND prov.estado <> 'activo' THEN
              RAISE EXCEPTION 'SIGRO-C1: el proveedor % está dado de baja y no admite compras nuevas', prov.ruc
                USING ERRCODE = 'check_violation';
            END IF;
            SELECT * INTO proy FROM obra.proyecto WHERE id = NEW.proyecto_id;
            IF proy.id IS NOT NULL AND proy.estado <> 'en_ejecucion' THEN
              RAISE EXCEPTION 'SIGRO-C2: la obra está cerrada y no admite compras nuevas' USING ERRCODE = 'check_violation';
            END IF;
            RETURN NEW;
          END IF;

          -- UPDATE: solo se admite la anulación (registrada -> anulada), nada más.
          IF OLD.estado = 'anulada' THEN
            RAISE EXCEPTION 'SIGRO-C3: la compra ya está anulada' USING ERRCODE = 'restrict_violation';
          END IF;
          IF (to_jsonb(NEW) - columnas_de_anulacion) IS DISTINCT FROM (to_jsonb(OLD) - columnas_de_anulacion) THEN
            RAISE EXCEPTION 'SIGRO-R10: una compra registrada no se edita; se anula con su motivo y se registra la correcta'
              USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_compra_validar BEFORE INSERT OR UPDATE ON compras.compra "
        "FOR EACH ROW EXECUTE FUNCTION compras.tg_compra_validar()"
    )

    # ----------------------------------------------------------- compra_detalle
    op.execute(
        """
        CREATE TABLE compras.compra_detalle (
          id              bigint GENERATED ALWAYS AS IDENTITY,
          compra_id       bigint         NOT NULL,
          insumo_id       bigint         NOT NULL,
          cantidad        numeric(18, 4) NOT NULL,
          unidad          text           NOT NULL,   -- unidad de compra al momento de comprar
          precio_unitario numeric(18, 4) NOT NULL,
          CONSTRAINT pk_compra_detalle PRIMARY KEY (id),
          CONSTRAINT fk_compra_detalle_compra_id_compra FOREIGN KEY (compra_id) REFERENCES compras.compra (id),
          CONSTRAINT fk_compra_detalle_insumo_id_insumo FOREIGN KEY (insumo_id) REFERENCES catalogo.insumo (id),
          CONSTRAINT uq_compra_detalle_compra_id_insumo_id UNIQUE (compra_id, insumo_id),
          CONSTRAINT ck_compra_detalle_cantidad CHECK (cantidad > 0),
          CONSTRAINT ck_compra_detalle_unidad CHECK (btrim(unidad) <> ''),
          CONSTRAINT ck_compra_detalle_precio CHECK (precio_unitario >= 0)
        )
        """
    )
    op.execute("CREATE INDEX ix_compra_detalle_insumo_id ON compras.compra_detalle (insumo_id)")
    solo_insertar("compras.compra_detalle")

    # ------------------------------------------------------------ compra_adjunto
    op.execute(
        """
        CREATE TABLE compras.compra_adjunto (
          id              bigint GENERATED ALWAYS AS IDENTITY,
          compra_id       bigint      NOT NULL,
          archivo         text        NOT NULL,   -- clave en el almacenamiento de objetos
          nombre_original text        NOT NULL,
          tipo_mime       text        NOT NULL,
          tamano_bytes    bigint      NOT NULL,
          sha256          char(64)    NOT NULL,
          subido_por      bigint      NOT NULL,
          subido_en       timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_compra_adjunto PRIMARY KEY (id),
          CONSTRAINT fk_compra_adjunto_compra_id_compra FOREIGN KEY (compra_id) REFERENCES compras.compra (id),
          CONSTRAINT fk_compra_adjunto_subido_por_usuario FOREIGN KEY (subido_por) REFERENCES obra.usuario (id),
          CONSTRAINT uq_compra_adjunto_compra_id_sha256 UNIQUE (compra_id, sha256),
          CONSTRAINT ck_compra_adjunto_archivo CHECK (btrim(archivo) <> ''),
          CONSTRAINT ck_compra_adjunto_tipo_mime CHECK (tipo_mime IN ('application/pdf', 'image/jpeg', 'image/png')),
          CONSTRAINT ck_compra_adjunto_tamano CHECK (tamano_bytes > 0 AND tamano_bytes <= 15 * 1024 * 1024),
          CONSTRAINT ck_compra_adjunto_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$')
        )
        """
    )
    solo_insertar("compras.compra_adjunto")

    # ------------------------------------------------------------------ permisos
    conceder("compras.proveedor", columnas_update="razon_social, estado_validacion, validado_en, estado, fecha_baja")
    conceder("compras.compra", columnas_update="estado, motivo_anulacion, anulada_por, anulada_en")
    conceder("compras.compra_detalle")
    conceder("compras.compra_adjunto")
    op.execute("GRANT EXECUTE ON FUNCTION compras.ruc_valido(text) TO sigro_app")


def downgrade() -> None:
    op.execute("DROP SCHEMA compras CASCADE")
