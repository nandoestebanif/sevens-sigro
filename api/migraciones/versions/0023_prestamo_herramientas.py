"""Migración 0023 (va DESPUÉS de la 0022). Préstamo y devolución de herramientas y equipos (25/11).

Una herramienta o equipo (insumo de tipo «equipo» en el catálogo S10) se presta a un trabajador de la obra y se
devuelve después. El préstamo NO es un movimiento: la herramienta sigue en la obra y su saldo no cambia; lo que
cambia es quién la tiene. «Disponible en almacén» = saldo − lo prestado sin devolver.

Reglas en la base (como el resto del almacén):
- R1/R10: un préstamo no se borra ni se edita; solo se registra UNA vez su devolución (SIGRO-H1).
- R3: uuid_cliente UNIQUE: reenviar el mismo préstamo no lo duplica.
- La devolución es completa (bueno, dañado o perdido) y la registra alguien con su fecha.

No toca datos. La redacta Claude a pedido del equipo (Fernando Istaña) el 25/11/2026. Revisar antes de aplicar.

Revision ID: 0023
Revises: 0022
"""
from alembic import op

revision = "0023"
down_revision = "0022"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("""
        CREATE TABLE almacen.prestamo (                    -- herramientas y equipos
          id                bigint GENERATED ALWAYS AS IDENTITY,
          uuid_cliente      uuid          NOT NULL,
          proyecto_id       bigint        NOT NULL,
          almacen_id        bigint        NOT NULL,
          insumo_id         bigint        NOT NULL,
          cantidad          numeric(18,4) NOT NULL,
          trabajador_id     bigint,                         -- ref. lógica a tareo.trabajador (se valida por el servicio)
          responsable       text          NOT NULL,         -- nombre de quien la lleva (queda aunque cambie el trabajador)
          devolver_el       date,                           -- opcional: para avisar si se pasa
          nota              text,
          registrado_por    bigint        NOT NULL,
          dispositivo_id    bigint,
          fecha_dispositivo timestamptz,
          prestado_en       timestamptz   NOT NULL DEFAULT now(),
          devuelto_en       timestamptz,
          devuelto_por      bigint,
          estado_devolucion text,
          nota_devolucion   text,
          CONSTRAINT pk_prestamo PRIMARY KEY (id),
          CONSTRAINT uq_prestamo_uuid_cliente UNIQUE (uuid_cliente),
          CONSTRAINT fk_prestamo_almacen FOREIGN KEY (almacen_id, proyecto_id) REFERENCES almacen.almacen_ref (id, proyecto_id),
          CONSTRAINT fk_prestamo_insumo FOREIGN KEY (insumo_id) REFERENCES almacen.insumo_ref (id),
          CONSTRAINT ck_prestamo_cantidad CHECK (cantidad > 0),
          CONSTRAINT ck_prestamo_responsable CHECK (btrim(responsable) <> '' AND length(responsable) <= 120),
          CONSTRAINT ck_prestamo_nota CHECK (nota IS NULL OR length(nota) <= 300),
          CONSTRAINT ck_prestamo_estado CHECK (estado_devolucion IS NULL OR estado_devolucion IN ('bueno', 'danado', 'perdido')),
          CONSTRAINT ck_prestamo_devolucion CHECK (
            (devuelto_en IS NULL AND devuelto_por IS NULL AND estado_devolucion IS NULL AND nota_devolucion IS NULL)
            OR (devuelto_en IS NOT NULL AND devuelto_por IS NOT NULL AND estado_devolucion IS NOT NULL)),
          -- dañado o perdido exige decir qué pasó: queda como constancia
          CONSTRAINT ck_prestamo_nota_devolucion CHECK (
            coalesce(estado_devolucion, 'bueno') = 'bueno' OR (nota_devolucion IS NOT NULL AND btrim(nota_devolucion) <> '')),
          CONSTRAINT ck_prestamo_nota_devolucion_largo CHECK (nota_devolucion IS NULL OR length(nota_devolucion) <= 300)
        )""")
    op.execute("CREATE INDEX ix_prestamo_abierto ON almacen.prestamo (proyecto_id, almacen_id, insumo_id) WHERE devuelto_en IS NULL")
    op.execute("""
        CREATE FUNCTION almacen.tg_prestamo_cambios() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF OLD.devuelto_en IS NOT NULL THEN
            RAISE EXCEPTION 'SIGRO-H1: el préstamo % ya se devolvió', OLD.id USING ERRCODE = 'restrict_violation';
          END IF;
          IF (to_jsonb(NEW) - ARRAY['devuelto_en', 'devuelto_por', 'estado_devolucion', 'nota_devolucion']) IS DISTINCT FROM
             (to_jsonb(OLD) - ARRAY['devuelto_en', 'devuelto_por', 'estado_devolucion', 'nota_devolucion']) THEN
            RAISE EXCEPTION 'SIGRO-H1: de un préstamo solo se registra su devolución' USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$""")
    op.execute("CREATE TRIGGER tg_prestamo_cambios BEFORE UPDATE ON almacen.prestamo FOR EACH ROW EXECUTE FUNCTION almacen.tg_prestamo_cambios()")
    op.execute("CREATE TRIGGER tg_prestamo_borrado BEFORE DELETE ON almacen.prestamo FOR EACH ROW EXECUTE FUNCTION util.rechazar_borrado()")
    # diccionario de datos (0019): toda columna dice qué guarda
    op.execute("COMMENT ON COLUMN almacen.prestamo.id IS 'Identificador interno.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.uuid_cliente IS 'Identificador que genera el cliente (celular o web): reenviar el mismo préstamo no lo duplica (R3).'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.proyecto_id IS 'Obra donde se presta la herramienta.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.almacen_id IS 'Almacén de la obra de donde sale la herramienta.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.insumo_id IS 'Herramienta o equipo prestado (insumo de tipo «equipo» del catálogo S10).'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.cantidad IS 'Cuántas unidades se prestan, en la unidad del S10.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.trabajador_id IS 'Trabajador de la obra que la lleva, si está registrado (referencia lógica a tareo.trabajador).'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.responsable IS 'Nombre de quien se la lleva, tal como se registró (queda aunque cambie el trabajador).'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.devolver_el IS 'Fecha en que debe devolverla; si pasa sin devolución, el préstamo se ve «atrasado».'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.nota IS 'Para qué se la llevan u otra observación al prestarla.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.registrado_por IS 'Usuario que registró el préstamo.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.dispositivo_id IS 'Celular autorizado desde el que se registró, si vino de la app.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.fecha_dispositivo IS 'Hora del celular al registrarlo (puede diferir de la del servidor).'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.prestado_en IS 'Hora del servidor en que se registró el préstamo.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.devuelto_en IS 'Hora del servidor en que se registró la devolución; vacío mientras no se devuelve.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.devuelto_por IS 'Usuario que registró la devolución.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.estado_devolucion IS 'Cómo volvió: bueno, danado (dañado) o perdido.'")
    op.execute("COMMENT ON COLUMN almacen.prestamo.nota_devolucion IS 'Qué pasó con la herramienta; obligatoria si volvió dañada o se perdió.'")
    op.execute("GRANT SELECT, INSERT ON almacen.prestamo TO sigro_svc_almacen")
    op.execute("GRANT UPDATE (devuelto_en, devuelto_por, estado_devolucion, nota_devolucion) ON almacen.prestamo TO sigro_svc_almacen")
    op.execute("COMMENT ON TABLE almacen.prestamo IS 'Préstamo de herramientas y equipos a trabajadores. No mueve el saldo: "
               "disponible = saldo - prestado sin devolver. Solo se registra una vez su devolución (SIGRO-H1).'")


def downgrade() -> None:
    # R10: si ya hay préstamos registrados, bajar la migración los borraría. Se detiene antes.
    op.execute("""
        DO $$ BEGIN
          IF EXISTS (SELECT 1 FROM almacen.prestamo) THEN
            RAISE EXCEPTION 'SIGRO-M23: hay préstamos registrados; bajar la 0023 los borraría';
          END IF;
        END $$""")
    op.execute("DROP TABLE almacen.prestamo")
    op.execute("DROP FUNCTION almacen.tg_prestamo_cambios()")
