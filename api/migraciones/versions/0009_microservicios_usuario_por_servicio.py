"""Microservicios: un usuario de base de datos por servicio, con acceso SOLO a su propio esquema.

Antes, la API única entraba con `sigro_app`, que veía todos los esquemas. Ahora cada microservicio entra
con su propio rol y no puede leer ni escribir los datos de otro servicio: si lo intenta, la base responde
«permiso denegado». Lo que un servicio necesita de otro lo pide por su API interna.

    sigro_svc_obras     esquema obra   (+ publicar en la bandeja de salida)
    sigro_svc_catalogo  esquema catalogo
    sigro_svc_compras   esquema compras (+ publicar en la bandeja de salida)
    sigro_svc_tareo     esquema tareo
    sigro_svc_relevo    bandeja de salida (outbox): la entrega a Notificaciones

Los privilegios de cada rol son EXACTAMENTE los que `sigro_app` tenía sobre ese esquema (los mismos
mínimos: nada de UPDATE en libros, columnas puntuales donde se edita); se copian del catálogo de la base,
así no hay dos listas que mantener.

Integridad entre servicios (fase 1): las claves foráneas que cruzan esquemas (p. ej. compra → obra) siguen
en la base, y los tres disparadores que validan contra la obra (plazo, periodo cerrado) pasan a
SECURITY DEFINER: verifican con permisos del dueño sin que el servicio pueda leer el esquema ajeno.
Sprint 2: reemplazarlos por réplicas locales alimentadas por eventos (ADR-09).

Revision ID: 0009
Revises: 0008
"""
from alembic import op

revision = "0009"
down_revision = "0008"
branch_labels = None
depends_on = None

SERVICIOS = {
    "sigro_svc_obras": ["obra"],
    "sigro_svc_catalogo": ["catalogo"],
    "sigro_svc_compras": ["compras"],
    "sigro_svc_tareo": ["tareo"],
    "sigro_svc_relevo": ["outbox"],
}
# Publicar un evento = INSERT … RETURNING id: solo eso, sin poder leer los eventos de otros.
PUBLICAN_EVENTOS = ["sigro_svc_obras", "sigro_svc_compras"]
DEFINER = ["compras.tg_compra_validar()", "tareo.tg_jornada_validar()", "tareo.tg_trabajador_validar()"]

_COPIAR = """
DO $$
DECLARE
  r record; c record; priv text;
BEGIN
  -- tablas y vistas: privilegio de tabla completa
  FOR r IN SELECT cl.oid, n.nspname, cl.relname FROM pg_class cl JOIN pg_namespace n ON n.oid = cl.relnamespace
           WHERE n.nspname = '{esquema}' AND cl.relkind IN ('r', 'v', 'm', 'p') LOOP
    FOREACH priv IN ARRAY ARRAY['SELECT', 'INSERT', 'UPDATE', 'DELETE'] LOOP
      IF has_table_privilege('sigro_app', r.oid, priv) THEN
        EXECUTE format('GRANT %s ON %I.%I TO {rol}', priv, r.nspname, r.relname);
      ELSE
        -- privilegio solo sobre algunas columnas (p. ej. UPDATE (estado, fecha_baja))
        FOR c IN SELECT a.attname FROM pg_attribute a WHERE a.attrelid = r.oid AND a.attnum > 0 AND NOT a.attisdropped LOOP
          IF priv IN ('SELECT', 'INSERT', 'UPDATE') AND has_column_privilege('sigro_app', r.oid, c.attname, priv) THEN
            EXECUTE format('GRANT %s (%I) ON %I.%I TO {rol}', priv, c.attname, r.nspname, r.relname);
          END IF;
        END LOOP;
      END IF;
    END LOOP;
  END LOOP;
  -- secuencias
  FOR r IN SELECT cl.oid, n.nspname, cl.relname FROM pg_class cl JOIN pg_namespace n ON n.oid = cl.relnamespace
           WHERE n.nspname = '{esquema}' AND cl.relkind = 'S' LOOP
    IF has_sequence_privilege('sigro_app', r.oid, 'USAGE') THEN
      EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %I.%I TO {rol}', r.nspname, r.relname);
    END IF;
  END LOOP;
  -- funciones que la aplicación puede llamar
  FOR r IN SELECT p.oid, p.oid::regprocedure AS firma FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
           WHERE n.nspname = '{esquema}' LOOP
    IF has_function_privilege('sigro_app', r.oid, 'EXECUTE') THEN
      EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO {rol}', r.firma);
    END IF;
  END LOOP;
END $$;
"""


def upgrade() -> None:
    for rol, esquemas in SERVICIOS.items():
        op.execute(f"""
            DO $$ BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '{rol}') THEN
                CREATE ROLE {rol} NOLOGIN;   -- migrar.sh crea el usuario con LOGIN y se lo concede
              END IF;
            END $$;""")
        op.execute(f"GRANT USAGE ON SCHEMA util, {', '.join(esquemas)} TO {rol}")
        for esquema in esquemas:
            op.execute(_COPIAR.format(esquema=esquema, rol=rol))
    for rol in PUBLICAN_EVENTOS:
        op.execute(f"GRANT USAGE ON SCHEMA outbox TO {rol}")
        op.execute(f"GRANT INSERT ON outbox.evento TO {rol}")
        op.execute(f"GRANT SELECT (id) ON outbox.evento TO {rol}")
    for funcion in DEFINER:
        esquema = funcion.split(".")[0]
        op.execute(f"ALTER FUNCTION {funcion} SECURITY DEFINER SET search_path = {esquema}, pg_temp")


def downgrade() -> None:
    for funcion in DEFINER:
        op.execute(f"ALTER FUNCTION {funcion} SECURITY INVOKER RESET search_path")
    # Los roles son del clúster (pueden servir a otras bases): aquí solo se retiran sus privilegios en ESTA base.
    for rol in SERVICIOS:
        op.execute(f"DROP OWNED BY {rol}")
