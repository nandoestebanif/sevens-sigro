"""Módulo tareo: trabajador y jornada (libro de asistencia que no se borra).

Reglas que sostiene
  R1  la jornada es un libro: corregir inserta una jornada nueva que apunta a la anterior
  R3  `uuid_cliente` opcional y único: un reenvío de la app no duplica
  R10 un trabajador se da de baja con su fecha de salida; sus jornadas se conservan

Decisión de diseño que NO está en modelo-datos.md y hay que ratificar (D-T1):
  `jornada.categoria` guarda la categoría del trabajador EN EL MOMENTO del registro.
  Sin eso, ascender a un peón a oficial reprecia hacia atrás todas sus horas pasadas,
  porque el costo por hora sale de la categoría (el modelo decidió no congelar el costo
  por hora). Con la categoría congelada, el pasado no cambia aunque la persona sí.

Revision ID: 0003
Revises: 0002
"""
from alembic import op

from migraciones._utiles import conceder, sin_borrado, solo_insertar

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("CREATE SCHEMA tareo")
    op.execute("GRANT USAGE ON SCHEMA tareo TO sigro_app")

    # --------------------------------------------------------------- trabajador
    op.execute(
        """
        CREATE TABLE tareo.trabajador (
          id             bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id    bigint      NOT NULL,
          nombre         text        NOT NULL,
          tipo_documento text        NOT NULL DEFAULT 'DNI',
          documento      text        NOT NULL,
          categoria      text        NOT NULL,
          cuadrilla      text        NOT NULL,
          fecha_ingreso  date        NOT NULL,
          fecha_salida   date,
          registrado_por bigint      NOT NULL,
          creado_en      timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_trabajador PRIMARY KEY (id),
          CONSTRAINT fk_trabajador_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_trabajador_registrado_por_usuario FOREIGN KEY (registrado_por) REFERENCES obra.usuario (id),
          -- Un mismo trabajador puede estar en más de una obra, pero no dos veces en la misma.
          CONSTRAINT uq_trabajador_proyecto_id_documento UNIQUE (proyecto_id, documento),
          CONSTRAINT uq_trabajador_id_proyecto_id UNIQUE (id, proyecto_id),
          CONSTRAINT ck_trabajador_nombre CHECK (btrim(nombre) <> ''),
          CONSTRAINT ck_trabajador_tipo_documento CHECK (tipo_documento IN ('DNI', 'CE', 'PASAPORTE')),
          CONSTRAINT ck_trabajador_documento CHECK (
            (tipo_documento = 'DNI' AND documento ~ '^[0-9]{8}$')
            OR (tipo_documento <> 'DNI' AND documento ~ '^[A-Za-z0-9]{6,20}$')
          ),
          CONSTRAINT ck_trabajador_categoria CHECK (categoria IN ('operario', 'oficial', 'peon')),
          CONSTRAINT ck_trabajador_cuadrilla CHECK (btrim(cuadrilla) <> ''),
          CONSTRAINT ck_trabajador_salida CHECK (fecha_salida IS NULL OR fecha_salida >= fecha_ingreso)
        )
        """
    )
    op.execute("CREATE INDEX ix_trabajador_proyecto_id_cuadrilla ON tareo.trabajador (proyecto_id, cuadrilla)")
    sin_borrado("tareo.trabajador")

    # --------------------------------------------------------------- jornada
    op.execute(
        """
        CREATE TABLE tareo.jornada (
          id               bigint GENERATED ALWAYS AS IDENTITY,
          uuid_cliente     uuid,
          proyecto_id      bigint        NOT NULL,
          trabajador_id    bigint        NOT NULL,
          fecha            date          NOT NULL,
          estado           text          NOT NULL,
          horas_normales   numeric(4, 2) NOT NULL DEFAULT 0,
          horas_extra      numeric(4, 2) NOT NULL DEFAULT 0,
          categoria        text          NOT NULL,   -- la que tenía al registrarse (D-T1)
          periodo_id       bigint        NOT NULL,   -- lo asigna el servidor desde la fecha
          registrado_por   bigint        NOT NULL,
          fecha_servidor   timestamptz   NOT NULL DEFAULT now(),
          corrige_a_id     bigint,
          motivo_correccion text,
          CONSTRAINT pk_jornada PRIMARY KEY (id),
          CONSTRAINT fk_jornada_trabajador_id_trabajador FOREIGN KEY (trabajador_id, proyecto_id)
            REFERENCES tareo.trabajador (id, proyecto_id),
          CONSTRAINT fk_jornada_periodo_id_periodo FOREIGN KEY (periodo_id, proyecto_id)
            REFERENCES obra.periodo (id, proyecto_id),
          CONSTRAINT fk_jornada_registrado_por_usuario FOREIGN KEY (registrado_por) REFERENCES obra.usuario (id),
          -- La corrección pertenece al mismo trabajador y a la misma fecha que lo corregido.
          CONSTRAINT uq_jornada_id_trabajador_fecha UNIQUE (id, trabajador_id, fecha),
          CONSTRAINT fk_jornada_corrige_a_id_jornada FOREIGN KEY (corrige_a_id, trabajador_id, fecha)
            REFERENCES tareo.jornada (id, trabajador_id, fecha),
          CONSTRAINT ck_jornada_estado CHECK (estado IN ('presente', 'ausente', 'permiso')),
          CONSTRAINT ck_jornada_categoria CHECK (categoria IN ('operario', 'oficial', 'peon')),
          CONSTRAINT ck_jornada_horas_rango CHECK (
            horas_normales BETWEEN 0 AND 24 AND horas_extra BETWEEN 0 AND 24 AND horas_normales + horas_extra <= 24
          ),
          CONSTRAINT ck_jornada_horas_segun_estado CHECK (
            (estado = 'presente' AND horas_normales + horas_extra > 0)
            OR (estado <> 'presente' AND horas_normales = 0 AND horas_extra = 0)
          ),
          CONSTRAINT ck_jornada_correccion CHECK (
            (corrige_a_id IS NULL AND motivo_correccion IS NULL)
            OR (corrige_a_id IS NOT NULL AND btrim(coalesce(motivo_correccion, '')) <> '')
          )
        )
        """
    )
    # Una sola jornada ORIGINAL por trabajador y día, y cada jornada se corrige a lo sumo
    # una vez: así la historia de un día es una cadena lineal y la "vigente" es su último
    # eslabón. Es lo que modelo-datos llama UNIQUE (trabajador_id, fecha) entre las vigentes.
    op.execute(
        "CREATE UNIQUE INDEX uq_jornada_original ON tareo.jornada (trabajador_id, fecha) WHERE corrige_a_id IS NULL"
    )
    op.execute(
        "CREATE UNIQUE INDEX uq_jornada_correccion_unica ON tareo.jornada (corrige_a_id) WHERE corrige_a_id IS NOT NULL"
    )
    op.execute("CREATE UNIQUE INDEX uq_jornada_uuid_cliente ON tareo.jornada (uuid_cliente) WHERE uuid_cliente IS NOT NULL")
    op.execute("CREATE INDEX ix_jornada_proyecto_id_fecha ON tareo.jornada (proyecto_id, fecha)")
    op.execute("CREATE INDEX ix_jornada_periodo_id ON tareo.jornada (periodo_id)")
    solo_insertar("tareo.jornada")

    op.execute(
        """
        CREATE FUNCTION tareo.tg_jornada_validar() RETURNS trigger LANGUAGE plpgsql AS $$
        DECLARE
          t   tareo.trabajador%ROWTYPE;
          p   obra.proyecto%ROWTYPE;
          per obra.periodo%ROWTYPE;
        BEGIN
          SELECT * INTO t FROM tareo.trabajador WHERE id = NEW.trabajador_id;
          SELECT * INTO p FROM obra.proyecto WHERE id = NEW.proyecto_id;
          IF t.id IS NULL OR p.id IS NULL THEN
            RETURN NEW;   -- las claves foráneas informarán el error con su nombre
          END IF;

          IF NEW.fecha < p.fecha_inicio OR NEW.fecha > p.fecha_fin THEN
            RAISE EXCEPTION 'SIGRO-T1: la fecha % está fuera del plazo de la obra (% a %)', NEW.fecha, p.fecha_inicio, p.fecha_fin
              USING ERRCODE = 'check_violation';
          END IF;
          IF NEW.fecha < t.fecha_ingreso OR (t.fecha_salida IS NOT NULL AND NEW.fecha > t.fecha_salida) THEN
            RAISE EXCEPTION 'SIGRO-T2: el trabajador no estaba en la obra el % (ingreso %, salida %)',
              NEW.fecha, t.fecha_ingreso, coalesce(t.fecha_salida::text, 'sin baja')
              USING ERRCODE = 'check_violation';
          END IF;

          SELECT * INTO per FROM obra.periodo
           WHERE proyecto_id = NEW.proyecto_id AND NEW.fecha BETWEEN fecha_inicio AND fecha_fin;
          IF NOT FOUND THEN
            RAISE EXCEPTION 'SIGRO-T3: no existe un periodo de la obra que contenga el %', NEW.fecha
              USING ERRCODE = 'check_violation';
          END IF;
          -- Un periodo cerrado no admite jornadas nuevas, pero SÍ correcciones con su motivo:
          -- corregir es justamente lo que se hace después del cierre y deja rastro (D-T2).
          IF NEW.corrige_a_id IS NULL AND per.estado = 'cerrado' THEN
            RAISE EXCEPTION 'SIGRO-T4: el periodo % está cerrado; solo admite correcciones de jornadas ya registradas', per.numero
              USING ERRCODE = 'check_violation';
          END IF;

          NEW.periodo_id := per.id;       -- el servidor decide; el cliente no elige el periodo
          NEW.categoria := t.categoria;   -- congelada al registrar (D-T1)
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_jornada_validar BEFORE INSERT ON tareo.jornada "
        "FOR EACH ROW EXECUTE FUNCTION tareo.tg_jornada_validar()"
    )

    op.execute(
        """
        CREATE FUNCTION tareo.tg_trabajador_validar() RETURNS trigger LANGUAGE plpgsql AS $$
        DECLARE
          p        obra.proyecto%ROWTYPE;
          ultima   date;
        BEGIN
          SELECT * INTO p FROM obra.proyecto WHERE id = NEW.proyecto_id;
          IF TG_OP = 'INSERT' THEN
            IF p.id IS NOT NULL AND (NEW.fecha_ingreso < p.fecha_inicio OR NEW.fecha_ingreso > p.fecha_fin) THEN
              RAISE EXCEPTION 'SIGRO-T5: la fecha de ingreso % está fuera del plazo de la obra', NEW.fecha_ingreso
                USING ERRCODE = 'check_violation';
            END IF;
            RETURN NEW;
          END IF;

          IF NEW.proyecto_id <> OLD.proyecto_id OR NEW.tipo_documento <> OLD.tipo_documento
             OR NEW.documento <> OLD.documento OR NEW.fecha_ingreso <> OLD.fecha_ingreso
             OR NEW.registrado_por <> OLD.registrado_por THEN
            RAISE EXCEPTION 'SIGRO-T5: obra, documento, fecha de ingreso y autor del registro no se editan'
              USING ERRCODE = 'restrict_violation';
          END IF;
          -- La categoría sí puede cambiar (ascenso): las jornadas pasadas conservan la suya (D-T1).
          IF OLD.fecha_salida IS NOT NULL AND NEW.fecha_salida IS DISTINCT FROM OLD.fecha_salida THEN
            RAISE EXCEPTION 'SIGRO-R10: la baja ya está registrada con fecha de salida %', OLD.fecha_salida
              USING ERRCODE = 'restrict_violation';
          END IF;
          IF NEW.fecha_salida IS NOT NULL AND OLD.fecha_salida IS NULL THEN
            SELECT max(fecha) INTO ultima FROM tareo.jornada WHERE trabajador_id = NEW.id;
            IF ultima IS NOT NULL AND NEW.fecha_salida < ultima THEN
              RAISE EXCEPTION 'SIGRO-T6: no se puede dar de baja el % porque tiene jornadas registradas hasta el %', NEW.fecha_salida, ultima
                USING ERRCODE = 'check_violation';
            END IF;
            IF NEW.fecha_salida > p.fecha_fin THEN
              RAISE EXCEPTION 'SIGRO-T6: la fecha de salida % excede el plazo de la obra', NEW.fecha_salida
                USING ERRCODE = 'check_violation';
            END IF;
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_trabajador_validar BEFORE INSERT OR UPDATE ON tareo.trabajador "
        "FOR EACH ROW EXECUTE FUNCTION tareo.tg_trabajador_validar()"
    )

    # ------------------------------------------------------------------- vistas
    op.execute(
        """
        CREATE VIEW tareo.jornada_vigente AS
        SELECT j.*
          FROM tareo.jornada j
         WHERE NOT EXISTS (SELECT 1 FROM tareo.jornada c WHERE c.corrige_a_id = j.id)
        """
    )
    # Horas acumuladas (HU-64). Solo cuenta lo vigente. No une con otros módulos: el
    # nombre del periodo y el costo los compone el servicio.
    op.execute(
        """
        CREATE VIEW tareo.horas_acumuladas AS
        SELECT j.proyecto_id, j.periodo_id, j.trabajador_id, t.cuadrilla, j.categoria,
               count(*) FILTER (WHERE j.estado = 'presente')  AS dias_presente,
               count(*) FILTER (WHERE j.estado = 'ausente')   AS dias_ausente,
               count(*) FILTER (WHERE j.estado = 'permiso')   AS dias_permiso,
               coalesce(sum(j.horas_normales), 0)             AS horas_normales,
               coalesce(sum(j.horas_extra), 0)                AS horas_extra
          FROM tareo.jornada_vigente j
          JOIN tareo.trabajador t ON t.id = j.trabajador_id
         GROUP BY j.proyecto_id, j.periodo_id, j.trabajador_id, t.cuadrilla, j.categoria
        """
    )

    # ------------------------------------------------------------------ permisos
    conceder("tareo.trabajador", columnas_update="nombre, categoria, cuadrilla, fecha_salida")
    conceder("tareo.jornada")
    conceder("tareo.jornada_vigente", "SELECT")
    conceder("tareo.horas_acumuladas", "SELECT")


def downgrade() -> None:
    op.execute("DROP SCHEMA tareo CASCADE")
