"""PROPUESTA (no aplicada) · Microservicios almacen y costo: modelo físico de movimientos, saldo, kárdex y costo promedio.

Estado: BORRADOR para revisión de Adiel (solo él crea migraciones). Vive en `migraciones/propuestas/`, fuera de
`versions/`, así que Alembic no la aplica. Para adoptarla: moverla a `versions/` (y renumerar si hace falta).
Se probó contra una base migrada hasta 0009 con `docs/propuestas/probar_0010.py` (escenarios de las HU 35 a 47).

Decisiones físicas (cada una sostiene una regla del CLAUDE.md):
  R1  movimiento, recepción, traslado, líneas de conteo: libros (solo INSERT, disparador + permisos).
  R2  NO hay columna de saldo ni de costo promedio: ambos se derivan de los movimientos (vistas `saldo`, `kardex`).
  R3  `uuid_cliente` NOT NULL y UNIQUE en todo lo que nace en un dispositivo o en un formulario.
  R4  la salida toma el promedio ponderado vigente y lo CONGELA en su fila; el disparador lo calcula bajo un
      candado por (almacén, insumo), así dos salidas simultáneas no pueden dejar el saldo en negativo.
  R5  el dispositivo se valida en el servicio (obras: POST /interno/autorizar con `dispositivo`); aquí queda su id.
  R9  todo lleva proyecto_id; (almacen_id, proyecto_id) es FK compuesta a la réplica local de almacenes.

Microservicios sin claves foráneas hacia otros esquemas DESDE EL DÍA 1 (la fase 2 del ADR-09 aplicada de entrada):
  lo que viene de otro servicio se guarda como réplica local (`almacen_ref`, `insumo_ref`) que el servicio llena al
  validar contra obras y catálogo por /interno (hoy) o por eventos (fase 2). compra_id, periodo_id, dispositivo_id y
  registrado_por son referencias lógicas: se validan por API y no se declaran FK.

El servicio costo es un MODELO DE LECTURA: no decide nada, recibe hechos ya valorizados (salidas con su costo
congelado, compras, horas) y responde los reportes de valorización y gasto (HU-55 a HU-59). El promedio NO vive en
costo: tiene que calcularse en la misma transacción que la salida, y esa transacción es de almacén.

Revision ID: 0010
Revises: 0009
"""
from alembic import op

from migraciones._utiles import sin_borrado, solo_insertar

revision = "0010"
down_revision = "0009"
branch_labels = None
depends_on = None

ORIGENES_SIN_COMPRA = ("saldo_otra_obra", "devolucion_cuadrilla", "prestamo", "sobrante", "otro")
TIPOS = ("ingreso_compra", "ingreso_sin_compra", "salida", "traslado_salida", "traslado_ingreso", "ajuste_conteo", "reverso")


def _lista(valores) -> str:
    return ", ".join(f"'{v}'" for v in valores)


def upgrade() -> None:
    op.execute("CREATE SCHEMA almacen")
    op.execute("CREATE SCHEMA costo")

    # ───────────────────────────── réplicas locales (lo ajeno que almacén necesita para validar e informar)
    op.execute("""
        CREATE TABLE almacen.almacen_ref (
          id              bigint      NOT NULL,           -- = obra.almacen.id (servicio obras)
          proyecto_id     bigint      NOT NULL,
          nombre          text        NOT NULL,
          tipo            text        NOT NULL,
          estado          text        NOT NULL,
          sincronizado_en timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_almacen_ref PRIMARY KEY (id),
          CONSTRAINT uq_almacen_ref_id_proyecto UNIQUE (id, proyecto_id),
          CONSTRAINT ck_almacen_ref_tipo CHECK (tipo IN ('central', 'frente')),
          CONSTRAINT ck_almacen_ref_estado CHECK (estado IN ('activo', 'inactivo'))
        )""")
    op.execute("""
        CREATE TABLE almacen.insumo_ref (
          id              bigint      NOT NULL,           -- = catalogo.insumo.id (servicio catálogo)
          codigo          text        NOT NULL,
          descripcion     text        NOT NULL,
          unidad          text        NOT NULL,           -- unidad del S10: en ella se cuentan los movimientos
          activo          boolean     NOT NULL,
          sincronizado_en timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_insumo_ref PRIMARY KEY (id),
          CONSTRAINT uq_insumo_ref_codigo UNIQUE (codigo)
        )""")
    for t in ("almacen.almacen_ref", "almacen.insumo_ref"):
        sin_borrado(t)

    # ───────────────────────────── cabeceras de operaciones que generan varios movimientos
    op.execute("""
        CREATE TABLE almacen.recepcion (                   -- HU-35, HU-36
          id                bigint GENERATED ALWAYS AS IDENTITY,
          uuid_cliente      uuid        NOT NULL,
          proyecto_id       bigint      NOT NULL,
          almacen_id        bigint      NOT NULL,
          compra_id         bigint      NOT NULL,         -- ref. lógica a compras.compra (se valida por /interno)
          motivo_exceso     text,                         -- HU-36: si se recibe más de lo requerido
          registrado_por    bigint      NOT NULL,
          dispositivo_id    bigint,
          fecha_dispositivo timestamptz,
          fecha_servidor    timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_recepcion PRIMARY KEY (id),
          CONSTRAINT uq_recepcion_uuid_cliente UNIQUE (uuid_cliente),
          CONSTRAINT uq_recepcion_id_proyecto UNIQUE (id, proyecto_id),
          CONSTRAINT fk_recepcion_almacen FOREIGN KEY (almacen_id, proyecto_id) REFERENCES almacen.almacen_ref (id, proyecto_id),
          CONSTRAINT ck_recepcion_motivo_exceso CHECK (motivo_exceso IS NULL OR btrim(motivo_exceso) <> '')
        )""")
    op.execute("CREATE INDEX ix_recepcion_compra_id ON almacen.recepcion (compra_id)")
    op.execute("""
        CREATE TABLE almacen.traslado (                    -- HU-42, HU-47
          id                  bigint GENERATED ALWAYS AS IDENTITY,
          uuid_cliente        uuid        NOT NULL,
          origen_proyecto_id  bigint      NOT NULL,
          origen_almacen_id   bigint      NOT NULL,
          destino_proyecto_id bigint      NOT NULL,
          destino_almacen_id  bigint      NOT NULL,
          motivo              text        NOT NULL,
          es_cierre           boolean     NOT NULL DEFAULT false,   -- HU-47: saldo final al almacén central
          registrado_por      bigint      NOT NULL,
          fecha_servidor      timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_traslado PRIMARY KEY (id),
          CONSTRAINT uq_traslado_uuid_cliente UNIQUE (uuid_cliente),
          CONSTRAINT fk_traslado_origen FOREIGN KEY (origen_almacen_id, origen_proyecto_id) REFERENCES almacen.almacen_ref (id, proyecto_id),
          CONSTRAINT fk_traslado_destino FOREIGN KEY (destino_almacen_id, destino_proyecto_id) REFERENCES almacen.almacen_ref (id, proyecto_id),
          CONSTRAINT ck_traslado_distinto CHECK (origen_almacen_id <> destino_almacen_id),
          CONSTRAINT ck_traslado_motivo CHECK (btrim(motivo) <> '')
        )""")
    op.execute("""
        CREATE TABLE almacen.conteo (                      -- HU-46, HU-47
          id             bigint GENERATED ALWAYS AS IDENTITY,
          uuid_cliente   uuid        NOT NULL,
          proyecto_id    bigint      NOT NULL,
          almacen_id     bigint      NOT NULL,
          fecha          date        NOT NULL,
          estado         text        NOT NULL DEFAULT 'borrador',
          es_cierre      boolean     NOT NULL DEFAULT false,
          registrado_por bigint      NOT NULL,
          creado_en      timestamptz NOT NULL DEFAULT now(),
          confirmado_por bigint,
          confirmado_en  timestamptz,
          CONSTRAINT pk_conteo PRIMARY KEY (id),
          CONSTRAINT uq_conteo_uuid_cliente UNIQUE (uuid_cliente),
          CONSTRAINT fk_conteo_almacen FOREIGN KEY (almacen_id, proyecto_id) REFERENCES almacen.almacen_ref (id, proyecto_id),
          CONSTRAINT ck_conteo_estado CHECK (estado IN ('borrador', 'confirmado')),
          CONSTRAINT ck_conteo_confirmacion CHECK ((estado = 'borrador' AND confirmado_por IS NULL AND confirmado_en IS NULL)
                                                OR (estado = 'confirmado' AND confirmado_por IS NOT NULL AND confirmado_en IS NOT NULL))
        )""")
    op.execute("CREATE UNIQUE INDEX uq_conteo_un_borrador ON almacen.conteo (almacen_id) WHERE estado = 'borrador'")
    op.execute("""
        CREATE TABLE almacen.conteo_linea (
          id               bigint GENERATED ALWAYS AS IDENTITY,
          conteo_id        bigint        NOT NULL,
          insumo_id        bigint        NOT NULL,
          saldo_teorico    numeric(18,4) NOT NULL,       -- lo que el servidor calculó al abrir la línea
          cantidad_contada numeric(18,4) NOT NULL,
          diferencia       numeric(18,4) GENERATED ALWAYS AS (cantidad_contada - saldo_teorico) STORED,
          motivo           text,
          CONSTRAINT pk_conteo_linea PRIMARY KEY (id),
          CONSTRAINT uq_conteo_linea_insumo UNIQUE (conteo_id, insumo_id),
          CONSTRAINT fk_conteo_linea_conteo FOREIGN KEY (conteo_id) REFERENCES almacen.conteo (id),
          CONSTRAINT fk_conteo_linea_insumo FOREIGN KEY (insumo_id) REFERENCES almacen.insumo_ref (id),
          CONSTRAINT ck_conteo_linea_contada CHECK (cantidad_contada >= 0),
          CONSTRAINT ck_conteo_linea_motivo CHECK (cantidad_contada = saldo_teorico OR btrim(coalesce(motivo, '')) <> '')
        )""")

    # ───────────────────────────── el libro: movimiento
    op.execute(f"""
        CREATE TABLE almacen.movimiento (
          id                bigint GENERATED ALWAYS AS IDENTITY,
          uuid_cliente      uuid          NOT NULL,
          proyecto_id       bigint        NOT NULL,
          almacen_id        bigint        NOT NULL,
          insumo_id         bigint        NOT NULL,
          tipo              text          NOT NULL,
          sentido           smallint      NOT NULL,     -- +1 entra · -1 sale (el saldo es la suma de sentido × cantidad)
          cantidad          numeric(18,4) NOT NULL,     -- en la unidad del S10 del insumo
          costo_unitario    numeric(18,6) NOT NULL,     -- en salidas lo pone el servidor: promedio vigente, congelado
          moneda            text          NOT NULL DEFAULT 'PEN',
          motivo            text,
          quien_retira      text,                       -- HU-40
          frente            text,                       -- HU-40, opcional
          origen            text,                       -- HU-41
          origen_detalle    text,
          recepcion_id      bigint,
          traslado_id       bigint,
          conteo_id         bigint,
          revierte_a_id     bigint,                     -- HU-45
          periodo_id        bigint,                     -- ref. lógica a obra.periodo (lo fija el servicio)
          dispositivo_id    bigint,                     -- ref. lógica a obra.dispositivo (R5); NULL si viene de la web
          registrado_por    bigint        NOT NULL,     -- ref. lógica a obra.usuario
          fecha_dispositivo timestamptz,                -- HU-37: hora del celular, solo informativa
          fecha_servidor    timestamptz   NOT NULL DEFAULT now(),   -- HU-38: la que vale para auditar
          CONSTRAINT pk_movimiento PRIMARY KEY (id),
          CONSTRAINT uq_movimiento_uuid_cliente UNIQUE (uuid_cliente),
          CONSTRAINT uq_movimiento_revierte_a UNIQUE (revierte_a_id),            -- un original se revierte una sola vez
          CONSTRAINT fk_movimiento_almacen FOREIGN KEY (almacen_id, proyecto_id) REFERENCES almacen.almacen_ref (id, proyecto_id),
          CONSTRAINT fk_movimiento_insumo FOREIGN KEY (insumo_id) REFERENCES almacen.insumo_ref (id),
          CONSTRAINT fk_movimiento_recepcion FOREIGN KEY (recepcion_id, proyecto_id) REFERENCES almacen.recepcion (id, proyecto_id),
          CONSTRAINT fk_movimiento_traslado FOREIGN KEY (traslado_id) REFERENCES almacen.traslado (id),
          CONSTRAINT fk_movimiento_conteo FOREIGN KEY (conteo_id) REFERENCES almacen.conteo (id),
          CONSTRAINT fk_movimiento_revierte_a FOREIGN KEY (revierte_a_id) REFERENCES almacen.movimiento (id),
          CONSTRAINT ck_movimiento_tipo CHECK (tipo IN ({_lista(TIPOS)})),
          CONSTRAINT ck_movimiento_sentido CHECK (sentido IN (-1, 1)),
          CONSTRAINT ck_movimiento_cantidad CHECK (cantidad > 0),
          CONSTRAINT ck_movimiento_costo CHECK (costo_unitario >= 0),
          CONSTRAINT ck_movimiento_moneda CHECK (moneda IN ('PEN', 'USD')),
          CONSTRAINT ck_movimiento_por_tipo CHECK (CASE tipo
            WHEN 'ingreso_compra'     THEN sentido = 1  AND recepcion_id IS NOT NULL
            WHEN 'ingreso_sin_compra' THEN sentido = 1  AND origen IN ({_lista(ORIGENES_SIN_COMPRA)}) AND costo_unitario > 0
                                           AND (origen <> 'otro' OR btrim(coalesce(origen_detalle, '')) <> '')
            WHEN 'salida'             THEN sentido = -1 AND btrim(coalesce(motivo, '')) <> '' AND btrim(coalesce(quien_retira, '')) <> ''
            WHEN 'traslado_salida'    THEN sentido = -1 AND traslado_id IS NOT NULL
            WHEN 'traslado_ingreso'   THEN sentido = 1  AND traslado_id IS NOT NULL
            WHEN 'ajuste_conteo'      THEN conteo_id IS NOT NULL AND btrim(coalesce(motivo, '')) <> ''
            WHEN 'reverso'            THEN revierte_a_id IS NOT NULL AND btrim(coalesce(motivo, '')) <> ''
          END),
          CONSTRAINT ck_movimiento_solo_su_referencia CHECK (
            (recepcion_id IS NULL OR tipo IN ('ingreso_compra', 'reverso')) AND
            (traslado_id  IS NULL OR tipo IN ('traslado_salida', 'traslado_ingreso', 'reverso')) AND
            (conteo_id    IS NULL OR tipo = 'ajuste_conteo') AND
            (revierte_a_id IS NULL OR tipo = 'reverso'))
        )""")
    # La consulta caliente (saldo y promedio de un insumo en un almacén) se resuelve solo con el índice.
    op.execute("CREATE INDEX ix_movimiento_saldo ON almacen.movimiento (almacen_id, insumo_id) INCLUDE (sentido, cantidad, costo_unitario)")
    op.execute("CREATE INDEX ix_movimiento_proyecto_fecha ON almacen.movimiento (proyecto_id, fecha_servidor)")
    op.execute("CREATE INDEX ix_movimiento_recepcion_id ON almacen.movimiento (recepcion_id) WHERE recepcion_id IS NOT NULL")
    op.execute("CREATE INDEX ix_movimiento_traslado_id ON almacen.movimiento (traslado_id) WHERE traslado_id IS NOT NULL")

    op.execute("""
        CREATE FUNCTION almacen.tg_movimiento_validar() RETURNS trigger LANGUAGE plpgsql AS $$
        DECLARE
          cant numeric; valor numeric; promedio numeric; o almacen.movimiento%ROWTYPE; par almacen.movimiento%ROWTYPE;
        BEGIN
          NEW.fecha_servidor := now();                       -- HU-38: manda la hora del servidor

          IF NEW.tipo = 'reverso' THEN                       -- HU-45: el reverso copia al original con el signo cambiado
            SELECT * INTO o FROM almacen.movimiento WHERE id = NEW.revierte_a_id;
            IF NOT FOUND THEN RETURN NEW; END IF;            -- la FK informa el error
            IF o.tipo = 'reverso' THEN
              RAISE EXCEPTION 'SIGRO-A4: un reverso no se revierte; registra el movimiento correcto' USING ERRCODE = 'check_violation';
            END IF;
            NEW.proyecto_id := o.proyecto_id; NEW.almacen_id := o.almacen_id; NEW.insumo_id := o.insumo_id;
            NEW.cantidad := o.cantidad; NEW.costo_unitario := o.costo_unitario; NEW.moneda := o.moneda;
            NEW.sentido := -o.sentido; NEW.recepcion_id := o.recepcion_id; NEW.traslado_id := o.traslado_id;
          END IF;

          -- Candado por (almacén, insumo) hasta el fin de la transacción: saldo y promedio no cambian mientras se decide.
          PERFORM pg_advisory_xact_lock(NEW.almacen_id::int, NEW.insumo_id::int);
          SELECT coalesce(sum(sentido * cantidad), 0), coalesce(sum(sentido * cantidad * costo_unitario), 0)
            INTO cant, valor FROM almacen.movimiento WHERE almacen_id = NEW.almacen_id AND insumo_id = NEW.insumo_id;
          promedio := CASE WHEN cant > 0 THEN round(valor / cant, 6) END;

          IF NEW.sentido = -1 THEN
            IF cant < NEW.cantidad THEN                      -- HU-40, HU-42: nunca más de lo disponible
              RAISE EXCEPTION 'SIGRO-A1: stock insuficiente en el almacén: disponible %, se pide %', cant, NEW.cantidad
                USING ERRCODE = 'check_violation';
            END IF;
            IF NEW.tipo <> 'reverso' THEN
              NEW.costo_unitario := promedio;               -- R4: el costo de la salida se congela aquí
            END IF;
          ELSIF NEW.tipo = 'ajuste_conteo' THEN
            NEW.costo_unitario := coalesce(promedio, NEW.costo_unitario);   -- HU-46: al promedio vigente
          ELSIF NEW.tipo = 'traslado_ingreso' THEN           -- HU-42: el costo viaja con el material
            SELECT * INTO par FROM almacen.movimiento
             WHERE traslado_id = NEW.traslado_id AND insumo_id = NEW.insumo_id AND tipo = 'traslado_salida';
            IF NOT FOUND THEN
              RAISE EXCEPTION 'SIGRO-A2: el ingreso del traslado % necesita antes su salida en el origen', NEW.traslado_id
                USING ERRCODE = 'check_violation';
            END IF;
            NEW.cantidad := par.cantidad; NEW.costo_unitario := par.costo_unitario;
          END IF;
          RETURN NEW;
        END $$""")
    op.execute("CREATE TRIGGER tg_movimiento_validar BEFORE INSERT ON almacen.movimiento FOR EACH ROW EXECUTE FUNCTION almacen.tg_movimiento_validar()")

    op.execute("""
        CREATE FUNCTION almacen.tg_conteo_cambios() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF OLD.estado = 'confirmado' THEN
            RAISE EXCEPTION 'SIGRO-A5: un conteo confirmado no se modifica; registra un conteo nuevo' USING ERRCODE = 'restrict_violation';
          END IF;
          IF (to_jsonb(NEW) - ARRAY['estado', 'confirmado_por', 'confirmado_en']) IS DISTINCT FROM
             (to_jsonb(OLD) - ARRAY['estado', 'confirmado_por', 'confirmado_en']) THEN
            RAISE EXCEPTION 'SIGRO-A5: de un conteo solo se registra su confirmación' USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$""")
    op.execute("CREATE TRIGGER tg_conteo_cambios BEFORE UPDATE ON almacen.conteo FOR EACH ROW EXECUTE FUNCTION almacen.tg_conteo_cambios()")
    op.execute("""
        CREATE FUNCTION almacen.tg_conteo_linea_validar() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF (SELECT estado FROM almacen.conteo WHERE id = NEW.conteo_id) = 'confirmado' THEN
            RAISE EXCEPTION 'SIGRO-A5: el conteo % ya está confirmado', NEW.conteo_id USING ERRCODE = 'check_violation';
          END IF;
          -- el saldo teórico lo pone el servidor, nunca el celular (R2)
          SELECT coalesce(sum(m.sentido * m.cantidad), 0) INTO NEW.saldo_teorico
            FROM almacen.movimiento m JOIN almacen.conteo c ON c.id = NEW.conteo_id
           WHERE m.almacen_id = c.almacen_id AND m.insumo_id = NEW.insumo_id;
          RETURN NEW;
        END $$""")
    op.execute("CREATE TRIGGER tg_conteo_linea_validar BEFORE INSERT ON almacen.conteo_linea FOR EACH ROW EXECUTE FUNCTION almacen.tg_conteo_linea_validar()")

    for t in ("almacen.movimiento", "almacen.recepcion", "almacen.traslado", "almacen.conteo_linea"):
        solo_insertar(t)
    sin_borrado("almacen.conteo")

    # ───────────────────────────── vistas: el saldo y el kárdex se DERIVAN (R2)
    op.execute("""
        CREATE VIEW almacen.saldo AS                       -- HU-43 por almacén · HU-56 valorizado
        SELECT m.proyecto_id, m.almacen_id, m.insumo_id,
               sum(m.sentido * m.cantidad)                                   AS cantidad,
               round(sum(m.sentido * m.cantidad * m.costo_unitario), 2)      AS valor,
               round(sum(m.sentido * m.cantidad * m.costo_unitario) / nullif(sum(m.sentido * m.cantidad), 0), 6) AS costo_promedio,
               max(m.fecha_servidor)                                         AS ultimo_movimiento_en,
               max(m.id)                                                     AS ultimo_movimiento_id
          FROM almacen.movimiento m GROUP BY m.proyecto_id, m.almacen_id, m.insumo_id""")
    op.execute("""
        CREATE VIEW almacen.saldo_obra AS                  -- HU-43 y HU-56 consolidado por proyecto
        SELECT proyecto_id, insumo_id, sum(cantidad) AS cantidad, sum(valor) AS valor,
               round(sum(valor) / nullif(sum(cantidad), 0), 6) AS costo_promedio, max(ultimo_movimiento_en) AS ultimo_movimiento_en
          FROM almacen.saldo GROUP BY proyecto_id, insumo_id""")
    op.execute("""
        CREATE VIEW almacen.kardex AS                      -- HU-45: se ve el anulado y el que lo anuló
        SELECT m.*, r.id AS revertido_por_id,
               sum(m.sentido * m.cantidad) OVER w AS saldo_despues,
               round(sum(m.sentido * m.cantidad * m.costo_unitario) OVER w
                     / nullif(sum(m.sentido * m.cantidad) OVER w, 0), 6) AS promedio_despues
          FROM almacen.movimiento m LEFT JOIN almacen.movimiento r ON r.revierte_a_id = m.id
        WINDOW w AS (PARTITION BY m.almacen_id, m.insumo_id ORDER BY m.id)""")
    op.execute("""
        CREATE VIEW almacen.recibido_por_compra AS         -- HU-35: lo pedido lo da compras; aquí lo recibido neto
        SELECT r.compra_id, m.insumo_id, sum(m.sentido * m.cantidad) AS recibido
          FROM almacen.movimiento m JOIN almacen.recepcion r ON r.id = m.recepcion_id
         GROUP BY r.compra_id, m.insumo_id""")

    # ───────────────────────────── costo: modelo de lectura alimentado por eventos (HU-55 a HU-59)
    op.execute("""
        CREATE TABLE costo.evento_recibido (                -- idempotencia del consumidor: un evento se procesa una vez
          evento_id   bigint      NOT NULL,                 -- = outbox.evento.id del servicio que lo publicó
          tipo        text        NOT NULL,
          recibido_en timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_evento_recibido PRIMARY KEY (evento_id)
        )""")
    op.execute("""
        CREATE TABLE costo.hecho (                          -- un hecho ya valorizado por su servicio de origen
          id             bigint GENERATED ALWAYS AS IDENTITY,
          evento_id      bigint        NOT NULL,
          proyecto_id    bigint        NOT NULL,
          familia        text          NOT NULL,            -- las tres familias de recursos del CLAUDE.md
          clase          text          NOT NULL,            -- consumo · ingreso · compra · horas
          insumo_codigo  text          NOT NULL,            -- R6/R8: el control es por insumo y su código único
          cantidad       numeric(18,4) NOT NULL,
          unidad         text          NOT NULL,
          costo_unitario numeric(18,6) NOT NULL,
          importe        numeric(18,2) GENERATED ALWAYS AS (round(cantidad * costo_unitario, 2)) STORED,
          moneda         text          NOT NULL DEFAULT 'PEN',
          fecha          date          NOT NULL,
          referencia     text          NOT NULL,            -- p. ej. almacen.movimiento:123 (para volver al origen)
          CONSTRAINT pk_hecho PRIMARY KEY (id),
          CONSTRAINT fk_hecho_evento FOREIGN KEY (evento_id) REFERENCES costo.evento_recibido (evento_id),
          CONSTRAINT uq_hecho_referencia UNIQUE (referencia, clase),
          CONSTRAINT ck_hecho_familia CHECK (familia IN ('material', 'mano_obra', 'equipo')),
          CONSTRAINT ck_hecho_clase CHECK (clase IN ('consumo', 'ingreso', 'compra', 'horas')),
          CONSTRAINT ck_hecho_moneda CHECK (moneda IN ('PEN', 'USD'))
        )""")
    op.execute("CREATE INDEX ix_hecho_proyecto ON costo.hecho (proyecto_id, familia, fecha)")
    for t in ("costo.evento_recibido", "costo.hecho"):
        solo_insertar(t)
    op.execute("""
        CREATE VIEW costo.gasto_por_obra AS                 -- HU-58: gasto en RECURSOS, no costo total de la obra
        SELECT proyecto_id, familia, moneda, sum(importe) AS gasto, max(fecha) AS hasta
          FROM costo.hecho WHERE clase IN ('consumo', 'horas') GROUP BY proyecto_id, familia, moneda""")

    # ───────────────────────────── roles: cada servicio, solo su esquema
    for rol in ("sigro_svc_almacen", "sigro_svc_costo"):
        op.execute(f"DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '{rol}') THEN CREATE ROLE {rol} NOLOGIN; END IF; END $$")
    op.execute("GRANT USAGE ON SCHEMA util, almacen TO sigro_svc_almacen")
    op.execute("GRANT SELECT, INSERT ON almacen.movimiento, almacen.recepcion, almacen.traslado, almacen.conteo, almacen.conteo_linea TO sigro_svc_almacen")
    op.execute("GRANT SELECT, INSERT, UPDATE ON almacen.almacen_ref, almacen.insumo_ref TO sigro_svc_almacen")
    op.execute("GRANT UPDATE (estado, confirmado_por, confirmado_en) ON almacen.conteo TO sigro_svc_almacen")
    op.execute("GRANT SELECT ON almacen.saldo, almacen.saldo_obra, almacen.kardex, almacen.recibido_por_compra TO sigro_svc_almacen")
    op.execute("GRANT USAGE ON SCHEMA outbox TO sigro_svc_almacen")
    op.execute("GRANT INSERT ON outbox.evento TO sigro_svc_almacen")
    op.execute("GRANT SELECT (id) ON outbox.evento TO sigro_svc_almacen")
    op.execute("GRANT USAGE ON SCHEMA util, costo TO sigro_svc_costo")
    op.execute("GRANT SELECT, INSERT ON costo.evento_recibido, costo.hecho TO sigro_svc_costo")
    op.execute("GRANT SELECT ON costo.gasto_por_obra TO sigro_svc_costo")


def downgrade() -> None:
    for rol in ("sigro_svc_almacen", "sigro_svc_costo"):
        op.execute(f"DROP OWNED BY {rol}")
    op.execute("DROP SCHEMA costo CASCADE")
    op.execute("DROP SCHEMA almacen CASCADE")
