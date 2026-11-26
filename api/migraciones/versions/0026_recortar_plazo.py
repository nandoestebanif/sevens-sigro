"""Migración 0026 (va DESPUÉS de la 0025). Recortar el plazo de una obra (26/11).

Hasta hoy el plazo solo se ampliaba. Pedido del equipo: el administrador también puede recortarlo, con su PIN.
Reglas (las garantiza la base, no solo la pantalla):
  - solo una obra en ejecución; el nuevo fin es anterior al vigente, no anterior al inicio ni a HOY (hora de Lima):
    lo ya registrado nunca queda fuera del plazo;
  - el nuevo fin debe ser el último día de una semana (periodo) de la obra: no se parte ninguna semana;
  - las semanas que quedan después pasan a «fuera_de_plazo» (no se borran, regla 10) y no admiten registros;
    si luego se amplía el plazo, vuelven a «abierto»;
  - queda en obra.proyecto_plazo_historial con motivo, autor y fecha, igual que una ampliación.

No toca datos. La redacta Claude a pedido del equipo (Fernando Istaña) el 26/11/2026. Revisar antes de aplicar.

Revision ID: 0026
Revises: 0025
"""
from alembic import op

revision = "0026"
down_revision = "0025"
branch_labels = None
depends_on = None

AMPLIAR = """
CREATE OR REPLACE FUNCTION obra.ampliar_plazo(p_proyecto bigint, p_nueva_fin date, p_motivo text, p_autor bigint)
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path = obra, pg_temp AS $$
DECLARE
  pr obra.proyecto%%ROWTYPE;
BEGIN
  SELECT * INTO pr FROM obra.proyecto WHERE id = p_proyecto FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'SIGRO: la obra %% no existe', p_proyecto;
  END IF;
  IF pr.estado <> 'en_ejecucion' THEN
    RAISE EXCEPTION 'SIGRO: no se amplía el plazo de una obra cerrada' USING ERRCODE = 'check_violation';
  END IF;
  IF p_nueva_fin <= pr.fecha_fin THEN
    RAISE EXCEPTION 'SIGRO: ampliar exige una fecha de fin posterior a la vigente (%%)', pr.fecha_fin
      USING ERRCODE = 'check_violation';
  END IF;

  INSERT INTO obra.proyecto_plazo_historial (proyecto_id, fecha_fin_anterior, fecha_fin_nueva, motivo, autor)
  VALUES (p_proyecto, pr.fecha_fin, p_nueva_fin, p_motivo, p_autor);

  PERFORM set_config('sigro.permitir_plazo', p_proyecto::text, true);
  UPDATE obra.proyecto SET fecha_fin = p_nueva_fin WHERE id = p_proyecto;
  PERFORM set_config('sigro.permitir_plazo', '', true);
  %s
  RETURN obra.generar_periodos(p_proyecto);   -- periodos nuevos creados
END $$
"""
REABRIR = """-- 0026: las semanas que un recorte dejó «fuera_de_plazo» vuelven si el nuevo fin las alcanza
  UPDATE obra.periodo SET estado = 'abierto'
   WHERE proyecto_id = p_proyecto AND estado = 'fuera_de_plazo' AND fecha_inicio <= p_nueva_fin;"""


def upgrade() -> None:
    op.execute("ALTER TABLE obra.periodo DROP CONSTRAINT ck_periodo_estado")
    op.execute("ALTER TABLE obra.periodo ADD CONSTRAINT ck_periodo_estado CHECK (estado IN ('abierto', 'cerrado', 'fuera_de_plazo'))")
    op.execute("COMMENT ON COLUMN obra.periodo.estado IS 'abierto (admite registros) · cerrado (solo correcciones con reverso) · "
               "fuera_de_plazo (quedó después del fin al recortar el plazo; no admite registros; vuelve a abierto si se amplía)'")
    op.execute("ALTER TABLE obra.proyecto_plazo_historial DROP CONSTRAINT ck_proyecto_plazo_historial_amplia")
    op.execute("ALTER TABLE obra.proyecto_plazo_historial ADD CONSTRAINT ck_proyecto_plazo_historial_cambia CHECK (fecha_fin_nueva <> fecha_fin_anterior)")
    op.execute("COMMENT ON TABLE obra.proyecto_plazo_historial IS 'Cada ampliación (fin nuevo posterior) o recorte (fin nuevo anterior) del plazo, "
               "con motivo, autor y fecha. Solo se agrega.'")
    op.execute(AMPLIAR % REABRIR)
    op.execute("""
        CREATE FUNCTION obra.recortar_plazo(p_proyecto bigint, p_nueva_fin date, p_motivo text, p_autor bigint)
        RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path = obra, pg_temp AS $$
        DECLARE
          pr obra.proyecto%ROWTYPE;
          n  integer;
        BEGIN
          SELECT * INTO pr FROM obra.proyecto WHERE id = p_proyecto FOR UPDATE;
          IF NOT FOUND THEN
            RAISE EXCEPTION 'SIGRO: la obra % no existe', p_proyecto;
          END IF;
          IF pr.estado <> 'en_ejecucion' THEN
            RAISE EXCEPTION 'SIGRO: no se recorta el plazo de una obra cerrada' USING ERRCODE = 'check_violation';
          END IF;
          IF p_nueva_fin >= pr.fecha_fin THEN
            RAISE EXCEPTION 'SIGRO: recortar exige una fecha de fin anterior a la vigente (%)', pr.fecha_fin USING ERRCODE = 'check_violation';
          END IF;
          IF p_nueva_fin < pr.fecha_inicio THEN
            RAISE EXCEPTION 'SIGRO: el nuevo fin no puede ser anterior al inicio de la obra (%)', pr.fecha_inicio USING ERRCODE = 'check_violation';
          END IF;
          IF p_nueva_fin < (now() AT TIME ZONE 'America/Lima')::date THEN
            RAISE EXCEPTION 'SIGRO: no se recorta a una fecha pasada: lo ya registrado quedaría fuera del plazo' USING ERRCODE = 'check_violation';
          END IF;
          IF NOT EXISTS (SELECT 1 FROM obra.periodo WHERE proyecto_id = p_proyecto AND fecha_fin = p_nueva_fin AND estado <> 'fuera_de_plazo') THEN
            RAISE EXCEPTION 'SIGRO: el nuevo fin debe ser el último día de una semana de la obra (su día de corte)' USING ERRCODE = 'check_violation';
          END IF;
          IF EXISTS (SELECT 1 FROM obra.periodo WHERE proyecto_id = p_proyecto AND fecha_inicio > p_nueva_fin AND estado = 'cerrado') THEN
            RAISE EXCEPTION 'SIGRO: hay semanas cerradas después del nuevo fin' USING ERRCODE = 'check_violation';
          END IF;

          INSERT INTO obra.proyecto_plazo_historial (proyecto_id, fecha_fin_anterior, fecha_fin_nueva, motivo, autor)
          VALUES (p_proyecto, pr.fecha_fin, p_nueva_fin, p_motivo, p_autor);

          PERFORM set_config('sigro.permitir_plazo', p_proyecto::text, true);
          UPDATE obra.proyecto SET fecha_fin = p_nueva_fin WHERE id = p_proyecto;
          PERFORM set_config('sigro.permitir_plazo', '', true);

          UPDATE obra.periodo SET estado = 'fuera_de_plazo'
           WHERE proyecto_id = p_proyecto AND fecha_inicio > p_nueva_fin AND estado = 'abierto';
          GET DIAGNOSTICS n = ROW_COUNT;
          RETURN n;   -- semanas que quedaron fuera del plazo
        END $$
    """)
    op.execute("COMMENT ON FUNCTION obra.recortar_plazo(bigint, date, text, bigint) IS "
               "'Recorta el plazo (solo administrador con PIN, lo exige la API): deja rastro en proyecto_plazo_historial y pasa a fuera_de_plazo las semanas siguientes.'")


def downgrade() -> None:
    op.execute("""
        DO $$ BEGIN
          IF EXISTS (SELECT 1 FROM obra.periodo WHERE estado = 'fuera_de_plazo')
             OR EXISTS (SELECT 1 FROM obra.proyecto_plazo_historial WHERE fecha_fin_nueva < fecha_fin_anterior) THEN
            RAISE EXCEPTION 'SIGRO-M26: ya hay plazos recortados; no se puede deshacer la 0026 sin perder ese rastro';
          END IF;
        END $$
    """)
    op.execute("DROP FUNCTION obra.recortar_plazo(bigint, date, text, bigint)")
    op.execute(AMPLIAR % "")
    op.execute("ALTER TABLE obra.proyecto_plazo_historial DROP CONSTRAINT ck_proyecto_plazo_historial_cambia")
    op.execute("ALTER TABLE obra.proyecto_plazo_historial ADD CONSTRAINT ck_proyecto_plazo_historial_amplia CHECK (fecha_fin_nueva > fecha_fin_anterior)")
    op.execute("ALTER TABLE obra.periodo DROP CONSTRAINT ck_periodo_estado")
    op.execute("ALTER TABLE obra.periodo ADD CONSTRAINT ck_periodo_estado CHECK (estado IN ('abierto', 'cerrado'))")
