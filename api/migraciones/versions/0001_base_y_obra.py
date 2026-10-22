"""Base común y módulo obra (proyecto, periodos, almacenes, usuarios, asignación, dispositivos).

Reglas que sostiene esta migración
  R5  un dispositivo autorizado por responsable, máximo dos por obra
  R9  el rol vive en `asignacion`, junto a la obra, no en `usuario`
  R10 nada se borra: las personas y las obras se dan de baja con fecha

Revision ID: 0001
Revises:
"""
from alembic import op

from migraciones._utiles import conceder, sin_borrado, solo_insertar

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # ------------------------------------------------------------------ comunes
    op.execute("CREATE EXTENSION IF NOT EXISTS btree_gist")
    op.execute("CREATE SCHEMA util")
    op.execute("CREATE SCHEMA obra")
    op.execute(
        """
        DO $$ BEGIN
          IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sigro_app') THEN
            CREATE ROLE sigro_app NOLOGIN;  -- operaciones le asigna contraseña y LOGIN
          END IF;
        END $$
        """
    )
    op.execute("GRANT USAGE ON SCHEMA util, obra TO sigro_app")

    op.execute(
        """
        CREATE FUNCTION util.rechazar_borrado() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          RAISE EXCEPTION 'SIGRO-R10: % sobre %.% no está permitido: nada se borra, se da de baja con su fecha',
            TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END $$
        """
    )
    op.execute(
        """
        CREATE FUNCTION util.rechazar_modificacion() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          RAISE EXCEPTION 'SIGRO-R1: %.% es un libro que no se modifica (% rechazado); corrige insertando un registro nuevo que referencie al original',
            TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP USING ERRCODE = 'restrict_violation';
        END $$
        """
    )

    # ------------------------------------------------------------------ usuario
    op.execute(
        """
        CREATE TABLE obra.usuario (
          id                bigint GENERATED ALWAYS AS IDENTITY,
          nombre            text        NOT NULL,
          usuario           text        NOT NULL,
          hash_clave        text        NOT NULL,
          estado            text        NOT NULL DEFAULT 'activo',
          fecha_baja        timestamptz,
          intentos_fallidos smallint    NOT NULL DEFAULT 0,
          bloqueado_hasta   timestamptz,
          creado_en         timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_usuario PRIMARY KEY (id),
          CONSTRAINT ck_usuario_nombre CHECK (btrim(nombre) <> ''),
          CONSTRAINT ck_usuario_usuario CHECK (usuario ~ '^[A-Za-z0-9._-]{3,40}$'),
          -- La contraseña nunca en claro: solo se acepta un hash con prefijo argon2 o bcrypt.
          CONSTRAINT ck_usuario_hash_clave CHECK (hash_clave ~ '^\\$(argon2(id|i|d)|2[aby])\\$'),
          CONSTRAINT ck_usuario_estado CHECK (estado IN ('activo', 'baja')),
          CONSTRAINT ck_usuario_baja_con_fecha CHECK ((estado = 'baja') = (fecha_baja IS NOT NULL)),
          CONSTRAINT ck_usuario_intentos CHECK (intentos_fallidos >= 0)
        )
        """
    )
    op.execute("CREATE UNIQUE INDEX uq_usuario_usuario ON obra.usuario (lower(usuario))")
    sin_borrado("obra.usuario")

    # ----------------------------------------------------------------- proyecto
    op.execute(
        """
        CREATE TABLE obra.proyecto (
          id           bigint GENERATED ALWAYS AS IDENTITY,
          nombre       text        NOT NULL,
          fecha_inicio date        NOT NULL,
          fecha_fin    date        NOT NULL,
          dia_corte    smallint    NOT NULL DEFAULT 7,   -- ISO: 1 lunes ... 7 domingo
          estado       text        NOT NULL DEFAULT 'en_ejecucion',
          creado_por   bigint      NOT NULL,
          creado_en    timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_proyecto PRIMARY KEY (id),
          CONSTRAINT fk_proyecto_creado_por_usuario FOREIGN KEY (creado_por) REFERENCES obra.usuario (id),
          CONSTRAINT ck_proyecto_nombre CHECK (btrim(nombre) <> ''),
          CONSTRAINT ck_proyecto_plazo CHECK (fecha_fin >= fecha_inicio),
          CONSTRAINT ck_proyecto_dia_corte CHECK (dia_corte BETWEEN 1 AND 7),
          CONSTRAINT ck_proyecto_estado CHECK (estado IN ('en_ejecucion', 'cerrado'))
        )
        """
    )
    op.execute("CREATE UNIQUE INDEX uq_proyecto_nombre ON obra.proyecto (lower(nombre))")
    sin_borrado("obra.proyecto")

    op.execute(
        """
        CREATE TABLE obra.proyecto_plazo_historial (
          id                bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id       bigint      NOT NULL,
          fecha_fin_anterior date       NOT NULL,
          fecha_fin_nueva   date        NOT NULL,
          motivo            text        NOT NULL,
          autor             bigint      NOT NULL,
          fecha             timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_proyecto_plazo_historial PRIMARY KEY (id),
          CONSTRAINT fk_proyecto_plazo_historial_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_proyecto_plazo_historial_autor_usuario FOREIGN KEY (autor) REFERENCES obra.usuario (id),
          CONSTRAINT ck_proyecto_plazo_historial_amplia CHECK (fecha_fin_nueva > fecha_fin_anterior),
          CONSTRAINT ck_proyecto_plazo_historial_motivo CHECK (btrim(motivo) <> '')
        )
        """
    )
    op.execute("CREATE INDEX ix_proyecto_plazo_historial_proyecto_id ON obra.proyecto_plazo_historial (proyecto_id, fecha)")
    solo_insertar("obra.proyecto_plazo_historial")

    # ------------------------------------------------------------------ periodo
    op.execute(
        """
        CREATE TABLE obra.periodo (
          id           bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id  bigint   NOT NULL,
          numero       integer  NOT NULL,
          fecha_inicio date     NOT NULL,
          fecha_fin    date     NOT NULL,
          estado       text     NOT NULL DEFAULT 'abierto',
          cerrado_en   timestamptz,
          cerrado_por  bigint,
          CONSTRAINT pk_periodo PRIMARY KEY (id),
          CONSTRAINT fk_periodo_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_periodo_cerrado_por_usuario FOREIGN KEY (cerrado_por) REFERENCES obra.usuario (id),
          CONSTRAINT uq_periodo_proyecto_id_numero UNIQUE (proyecto_id, numero),
          -- Permite que movimiento y jornada referencien (periodo_id, proyecto_id) y el motor
          -- garantice que el periodo pertenece a la misma obra que el registro.
          CONSTRAINT uq_periodo_id_proyecto_id UNIQUE (id, proyecto_id),
          CONSTRAINT ck_periodo_numero CHECK (numero > 0),
          CONSTRAINT ck_periodo_rango CHECK (fecha_fin >= fecha_inicio),
          CONSTRAINT ck_periodo_estado CHECK (estado IN ('abierto', 'cerrado')),
          CONSTRAINT ck_periodo_cierre CHECK ((estado = 'cerrado') = (cerrado_en IS NOT NULL)),
          -- Dos periodos de una misma obra nunca se solapan.
          CONSTRAINT ex_periodo_sin_solape EXCLUDE USING gist (
            proyecto_id WITH =, daterange(fecha_inicio, fecha_fin, '[]') WITH &&
          )
        )
        """
    )
    sin_borrado("obra.periodo")

    # ------------------------------------------------------------------ almacen
    op.execute(
        """
        CREATE TABLE obra.almacen (
          id          bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id bigint      NOT NULL,
          nombre      text        NOT NULL,
          tipo        text        NOT NULL DEFAULT 'frente',
          estado      text        NOT NULL DEFAULT 'activo',
          creado_en   timestamptz NOT NULL DEFAULT now(),
          CONSTRAINT pk_almacen PRIMARY KEY (id),
          CONSTRAINT fk_almacen_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT uq_almacen_id_proyecto_id UNIQUE (id, proyecto_id),
          CONSTRAINT ck_almacen_nombre CHECK (btrim(nombre) <> ''),
          CONSTRAINT ck_almacen_tipo CHECK (tipo IN ('central', 'frente')),
          CONSTRAINT ck_almacen_estado CHECK (estado IN ('activo', 'inactivo'))
        )
        """
    )
    op.execute("CREATE UNIQUE INDEX uq_almacen_proyecto_nombre ON obra.almacen (proyecto_id, lower(nombre))")
    sin_borrado("obra.almacen")

    # --------------------------------------------------------------- asignacion
    op.execute(
        """
        CREATE TABLE obra.asignacion (
          id               bigint GENERATED ALWAYS AS IDENTITY,
          usuario_id       bigint      NOT NULL,
          proyecto_id      bigint      NOT NULL,
          rol              text        NOT NULL,
          asignado_por     bigint      NOT NULL,
          fecha_asignacion timestamptz NOT NULL DEFAULT now(),
          fecha_retiro     timestamptz,
          retirado_por     bigint,
          CONSTRAINT pk_asignacion PRIMARY KEY (id),
          CONSTRAINT fk_asignacion_usuario_id_usuario FOREIGN KEY (usuario_id) REFERENCES obra.usuario (id),
          CONSTRAINT fk_asignacion_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_asignacion_asignado_por_usuario FOREIGN KEY (asignado_por) REFERENCES obra.usuario (id),
          CONSTRAINT fk_asignacion_retirado_por_usuario FOREIGN KEY (retirado_por) REFERENCES obra.usuario (id),
          CONSTRAINT ck_asignacion_rol CHECK (rol IN ('almacenero', 'ssoma', 'residente', 'administrador', 'gerente')),
          CONSTRAINT ck_asignacion_retiro CHECK (
            (fecha_retiro IS NULL AND retirado_por IS NULL)
            OR (fecha_retiro IS NOT NULL AND retirado_por IS NOT NULL AND fecha_retiro >= fecha_asignacion)
          )
        )
        """
    )
    # Una persona tiene un solo rol vigente por obra (pero puede tener otro en otra obra).
    op.execute(
        "CREATE UNIQUE INDEX uq_asignacion_vigente ON obra.asignacion (usuario_id, proyecto_id) "
        "WHERE fecha_retiro IS NULL"
    )
    op.execute("CREATE INDEX ix_asignacion_proyecto_id_rol ON obra.asignacion (proyecto_id, rol) WHERE fecha_retiro IS NULL")
    sin_borrado("obra.asignacion")

    op.execute(
        """
        CREATE FUNCTION obra.tg_asignacion_cambios() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF NEW.usuario_id <> OLD.usuario_id OR NEW.proyecto_id <> OLD.proyecto_id
             OR NEW.rol <> OLD.rol OR NEW.asignado_por <> OLD.asignado_por
             OR NEW.fecha_asignacion <> OLD.fecha_asignacion THEN
            RAISE EXCEPTION 'SIGRO-R9: una asignación no se edita; se retira y se crea otra (queda el rastro de quién tuvo qué rol y cuándo)'
              USING ERRCODE = 'restrict_violation';
          END IF;
          IF OLD.fecha_retiro IS NOT NULL THEN
            RAISE EXCEPTION 'SIGRO-R9: la asignación ya fue retirada el %', OLD.fecha_retiro
              USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_asignacion_cambios BEFORE UPDATE ON obra.asignacion "
        "FOR EACH ROW EXECUTE FUNCTION obra.tg_asignacion_cambios()"
    )

    # -------------------------------------------------------------- dispositivo
    op.execute(
        """
        CREATE TABLE obra.dispositivo (
          id                  bigint GENERATED ALWAYS AS IDENTITY,
          proyecto_id         bigint      NOT NULL,
          usuario_id          bigint      NOT NULL,
          identificador       text        NOT NULL,
          estado              text        NOT NULL DEFAULT 'pendiente',
          solicitado_en       timestamptz NOT NULL DEFAULT now(),
          autorizado_por      bigint,
          fecha_autorizacion  timestamptz,
          revocado_por        bigint,
          fecha_revocacion    timestamptz,
          motivo_revocacion   text,
          CONSTRAINT pk_dispositivo PRIMARY KEY (id),
          CONSTRAINT fk_dispositivo_proyecto_id_proyecto FOREIGN KEY (proyecto_id) REFERENCES obra.proyecto (id),
          CONSTRAINT fk_dispositivo_usuario_id_usuario FOREIGN KEY (usuario_id) REFERENCES obra.usuario (id),
          CONSTRAINT fk_dispositivo_autorizado_por_usuario FOREIGN KEY (autorizado_por) REFERENCES obra.usuario (id),
          CONSTRAINT fk_dispositivo_revocado_por_usuario FOREIGN KEY (revocado_por) REFERENCES obra.usuario (id),
          -- Lo referencia movimiento (Henry): (dispositivo_id, proyecto_id) obliga a que el
          -- dispositivo que registró sea de la misma obra que el movimiento.
          CONSTRAINT uq_dispositivo_id_proyecto_id UNIQUE (id, proyecto_id),
          CONSTRAINT ck_dispositivo_identificador CHECK (identificador ~ '^[A-Za-z0-9._:-]{8,128}$'),
          CONSTRAINT ck_dispositivo_estado CHECK (estado IN ('pendiente', 'activo', 'revocado')),
          CONSTRAINT ck_dispositivo_autorizacion CHECK (
            (estado = 'pendiente' AND autorizado_por IS NULL AND fecha_autorizacion IS NULL)
            OR (estado = 'activo' AND autorizado_por IS NOT NULL AND fecha_autorizacion IS NOT NULL)
            OR (estado = 'revocado')
          ),
          CONSTRAINT ck_dispositivo_revocacion CHECK (
            (estado = 'revocado') = (fecha_revocacion IS NOT NULL)
          )
        )
        """
    )
    # Un dispositivo activo por responsable y obra; y el mismo equipo no se autoriza dos veces.
    op.execute(
        "CREATE UNIQUE INDEX uq_dispositivo_activo_por_usuario ON obra.dispositivo (proyecto_id, usuario_id) "
        "WHERE estado = 'activo'"
    )
    op.execute(
        "CREATE UNIQUE INDEX uq_dispositivo_activo_identificador ON obra.dispositivo (proyecto_id, identificador) "
        "WHERE estado = 'activo'"
    )
    sin_borrado("obra.dispositivo")

    op.execute(
        """
        CREATE FUNCTION obra.tg_dispositivo() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF TG_OP = 'UPDATE' THEN
            IF NEW.proyecto_id <> OLD.proyecto_id OR NEW.usuario_id <> OLD.usuario_id
               OR NEW.identificador <> OLD.identificador THEN
              RAISE EXCEPTION 'SIGRO-R5: obra, responsable e identificador del dispositivo no se editan'
                USING ERRCODE = 'restrict_violation';
            END IF;
            IF OLD.estado = 'revocado' THEN
              RAISE EXCEPTION 'SIGRO-R5: un dispositivo revocado no vuelve a activarse; se solicita uno nuevo'
                USING ERRCODE = 'restrict_violation';
            END IF;
            IF NEW.estado = 'pendiente' AND OLD.estado <> 'pendiente' THEN
              RAISE EXCEPTION 'SIGRO-R5: un dispositivo no vuelve a pendiente' USING ERRCODE = 'restrict_violation';
            END IF;
          END IF;

          IF NEW.estado = 'activo' AND (TG_OP = 'INSERT' OR OLD.estado <> 'activo') THEN
            -- Serializa las autorizaciones de una misma obra: sin esto, dos administradores
            -- simultáneos podrían activar el tercer dispositivo.
            PERFORM pg_advisory_xact_lock(hashtextextended('sigro.dispositivo.' || NEW.proyecto_id::text, 0));

            IF (SELECT count(*) FROM obra.dispositivo d
                 WHERE d.proyecto_id = NEW.proyecto_id AND d.estado = 'activo'
                   AND d.id IS DISTINCT FROM NEW.id) >= 2 THEN
              RAISE EXCEPTION 'SIGRO-R5: la obra ya tiene dos dispositivos autorizados (almacenero y SSOMA); revoca uno antes de autorizar otro'
                USING ERRCODE = 'check_violation';
            END IF;

            IF NOT EXISTS (SELECT 1 FROM obra.asignacion a
                            WHERE a.usuario_id = NEW.usuario_id AND a.proyecto_id = NEW.proyecto_id
                              AND a.fecha_retiro IS NULL AND a.rol IN ('almacenero', 'ssoma')) THEN
              RAISE EXCEPTION 'SIGRO-R5: solo el almacenero o el ingeniero SSOMA asignados a la obra pueden tener dispositivo autorizado'
                USING ERRCODE = 'check_violation';
            END IF;
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_dispositivo BEFORE INSERT OR UPDATE ON obra.dispositivo "
        "FOR EACH ROW EXECUTE FUNCTION obra.tg_dispositivo()"
    )
    # Si se retira la asignación, su dispositivo deja de valer en ese mismo instante.
    op.execute(
        """
        CREATE FUNCTION obra.tg_asignacion_retira_dispositivo() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          UPDATE obra.dispositivo
             SET estado = 'revocado', fecha_revocacion = NEW.fecha_retiro,
                 revocado_por = NEW.retirado_por, motivo_revocacion = 'asignación retirada'
           WHERE proyecto_id = NEW.proyecto_id AND usuario_id = NEW.usuario_id
             AND estado IN ('pendiente', 'activo');
          RETURN NULL;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_asignacion_retira_dispositivo AFTER UPDATE OF fecha_retiro ON obra.asignacion "
        "FOR EACH ROW WHEN (OLD.fecha_retiro IS NULL AND NEW.fecha_retiro IS NOT NULL) "
        "EXECUTE FUNCTION obra.tg_asignacion_retira_dispositivo()"
    )

    # ------------------------------------------- periodos: se generan solos (HU-03)
    op.execute(
        """
        CREATE FUNCTION obra.generar_periodos(p_proyecto bigint) RETURNS integer
        LANGUAGE plpgsql SECURITY DEFINER SET search_path = obra, pg_temp AS $$
        DECLARE
          pr          obra.proyecto%ROWTYPE;
          ult         obra.periodo%ROWTYPE;
          cursor_     date;
          fin_natural date;
          fin_p       date;
          n           integer;
          creados     integer := 0;
        BEGIN
          SELECT * INTO pr FROM obra.proyecto WHERE id = p_proyecto;
          IF NOT FOUND THEN
            RAISE EXCEPTION 'SIGRO: la obra % no existe', p_proyecto;
          END IF;

          SELECT * INTO ult FROM obra.periodo WHERE proyecto_id = p_proyecto ORDER BY numero DESC LIMIT 1;
          IF FOUND THEN
            -- Si el plazo se amplió y el último periodo quedó corto, se completa hasta su corte.
            fin_natural := ult.fecha_inicio + ((pr.dia_corte - extract(isodow FROM ult.fecha_inicio)::int + 7) % 7);
            fin_p := least(pr.fecha_fin, fin_natural);
            IF ult.estado = 'abierto' AND fin_p > ult.fecha_fin THEN
              PERFORM set_config('sigro.permitir_periodo', 'on', true);
              UPDATE obra.periodo SET fecha_fin = fin_p WHERE id = ult.id;
              PERFORM set_config('sigro.permitir_periodo', 'off', true);
              ult.fecha_fin := fin_p;
            END IF;
            cursor_ := ult.fecha_fin + 1;
            n := ult.numero;
          ELSE
            cursor_ := pr.fecha_inicio;
            n := 0;
          END IF;

          WHILE cursor_ <= pr.fecha_fin LOOP
            fin_natural := cursor_ + ((pr.dia_corte - extract(isodow FROM cursor_)::int + 7) % 7);
            fin_p := least(pr.fecha_fin, fin_natural);
            n := n + 1;
            INSERT INTO obra.periodo (proyecto_id, numero, fecha_inicio, fecha_fin)
            VALUES (p_proyecto, n, cursor_, fin_p);
            creados := creados + 1;
            cursor_ := fin_p + 1;
          END LOOP;
          RETURN creados;
        END $$
        """
    )
    op.execute(
        """
        CREATE FUNCTION obra.tg_proyecto_genera_periodos() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          PERFORM obra.generar_periodos(NEW.id);
          RETURN NULL;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_proyecto_genera_periodos AFTER INSERT ON obra.proyecto "
        "FOR EACH ROW EXECUTE FUNCTION obra.tg_proyecto_genera_periodos()"
    )

    op.execute(
        """
        CREATE FUNCTION obra.tg_periodo_cambios() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF NEW.proyecto_id <> OLD.proyecto_id OR NEW.numero <> OLD.numero OR NEW.fecha_inicio <> OLD.fecha_inicio THEN
            RAISE EXCEPTION 'SIGRO: obra, número e inicio de un periodo no se editan' USING ERRCODE = 'restrict_violation';
          END IF;
          IF NEW.fecha_fin <> OLD.fecha_fin
             AND (OLD.estado = 'cerrado' OR coalesce(current_setting('sigro.permitir_periodo', true), 'off') <> 'on') THEN
            RAISE EXCEPTION 'SIGRO: el fin de un periodo solo cambia al ampliar el plazo de la obra y mientras esté abierto'
              USING ERRCODE = 'restrict_violation';
          END IF;
          IF OLD.estado = 'cerrado' THEN
            RAISE EXCEPTION 'SIGRO-R1: un periodo cerrado no se reabre ni se modifica' USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_periodo_cambios BEFORE UPDATE ON obra.periodo "
        "FOR EACH ROW EXECUTE FUNCTION obra.tg_periodo_cambios()"
    )

    # ----------------------------------------- ampliar el plazo deja rastro (HU-02)
    op.execute(
        """
        CREATE FUNCTION obra.tg_proyecto_cambios() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF NEW.fecha_inicio <> OLD.fecha_inicio OR NEW.dia_corte <> OLD.dia_corte
             OR NEW.creado_por <> OLD.creado_por OR NEW.creado_en <> OLD.creado_en THEN
            RAISE EXCEPTION 'SIGRO: inicio, día de corte y autoría de la obra no se editan' USING ERRCODE = 'restrict_violation';
          END IF;
          IF NEW.fecha_fin <> OLD.fecha_fin
             AND coalesce(current_setting('sigro.permitir_plazo', true), '') <> NEW.id::text THEN
            RAISE EXCEPTION 'SIGRO: el plazo se amplía con obra.ampliar_plazo(), que deja motivo, autor y fecha'
              USING ERRCODE = 'restrict_violation';
          END IF;
          IF OLD.estado = 'cerrado' AND NEW.estado <> 'cerrado' THEN
            RAISE EXCEPTION 'SIGRO: una obra cerrada no se reabre' USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
        """
    )
    op.execute(
        "CREATE TRIGGER tg_proyecto_cambios BEFORE UPDATE ON obra.proyecto "
        "FOR EACH ROW EXECUTE FUNCTION obra.tg_proyecto_cambios()"
    )
    op.execute(
        """
        CREATE FUNCTION obra.ampliar_plazo(p_proyecto bigint, p_nueva_fin date, p_motivo text, p_autor bigint)
        RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path = obra, pg_temp AS $$
        DECLARE
          pr obra.proyecto%ROWTYPE;
        BEGIN
          SELECT * INTO pr FROM obra.proyecto WHERE id = p_proyecto FOR UPDATE;
          IF NOT FOUND THEN
            RAISE EXCEPTION 'SIGRO: la obra % no existe', p_proyecto;
          END IF;
          IF pr.estado <> 'en_ejecucion' THEN
            RAISE EXCEPTION 'SIGRO: no se amplía el plazo de una obra cerrada' USING ERRCODE = 'check_violation';
          END IF;
          IF p_nueva_fin <= pr.fecha_fin THEN
            RAISE EXCEPTION 'SIGRO: ampliar exige una fecha de fin posterior a la vigente (%)', pr.fecha_fin
              USING ERRCODE = 'check_violation';
          END IF;

          INSERT INTO obra.proyecto_plazo_historial (proyecto_id, fecha_fin_anterior, fecha_fin_nueva, motivo, autor)
          VALUES (p_proyecto, pr.fecha_fin, p_nueva_fin, p_motivo, p_autor);

          PERFORM set_config('sigro.permitir_plazo', p_proyecto::text, true);
          UPDATE obra.proyecto SET fecha_fin = p_nueva_fin WHERE id = p_proyecto;
          PERFORM set_config('sigro.permitir_plazo', '', true);

          RETURN obra.generar_periodos(p_proyecto);   -- periodos nuevos creados
        END $$
        """
    )

    # ------------------------------------------------------------------ permisos
    conceder("obra.usuario", columnas_update="nombre, hash_clave, estado, fecha_baja, intentos_fallidos, bloqueado_hasta")
    conceder("obra.proyecto", columnas_update="nombre, estado")
    conceder("obra.proyecto_plazo_historial", "SELECT")
    conceder("obra.periodo", "SELECT", columnas_update="estado, cerrado_en, cerrado_por")
    conceder("obra.almacen", columnas_update="nombre, tipo, estado")
    conceder("obra.asignacion", columnas_update="fecha_retiro, retirado_por")
    conceder(
        "obra.dispositivo",
        columnas_update="estado, autorizado_por, fecha_autorizacion, revocado_por, fecha_revocacion, motivo_revocacion",
    )
    op.execute("GRANT EXECUTE ON FUNCTION obra.ampliar_plazo(bigint, date, text, bigint) TO sigro_app")


def downgrade() -> None:
    # El rol sigro_app y la extensión btree_gist son del clúster y pueden servir a otros
    # esquemas: se dejan. Al borrar los esquemas se van sus permisos y funciones.
    op.execute("DROP SCHEMA obra CASCADE")
    op.execute("DROP SCHEMA util CASCADE")
