"""Migración 0024 (va DESPUÉS de la 0023). Bitácora de operaciones y PIN del panel de administración (25/11).

1. obra.bitacora: cada operación que cambia algo (POST) y cada acceso rechazado (401/403) queda con quién, cuándo,
   en qué obra, qué ruta y cómo terminó. A propósito NO guarda cuerpos, parámetros, tokens, claves, IP ni datos
   personales: solo lo necesario para auditar. Es un libro: no se edita ni se borra (R1/R10).
2. obra.usuario.pin_hash: PIN de 6 dígitos del administrador (Argon2id, como la clave) para entrar a su panel;
   con intentos fallidos y bloqueo temporal.

No toca datos. La redacta Claude a pedido del equipo (Fernando Istaña) el 25/11/2026. Revisar antes de aplicar.

Revision ID: 0024
Revises: 0023
"""
from alembic import op

from migraciones._utiles import solo_insertar

revision = "0024"
down_revision = "0023"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("""
        CREATE TABLE obra.bitacora (
          id          bigint GENERATED ALWAYS AS IDENTITY,
          momento     timestamptz NOT NULL DEFAULT now(),
          usuario_id  bigint,
          proyecto_id bigint,
          metodo      text        NOT NULL,
          ruta        text        NOT NULL,
          estado      integer     NOT NULL,
          duracion_ms integer     NOT NULL DEFAULT 0,
          CONSTRAINT pk_bitacora PRIMARY KEY (id),
          CONSTRAINT ck_bitacora_metodo CHECK (metodo IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
          CONSTRAINT ck_bitacora_ruta CHECK (length(ruta) <= 300 AND position('?' in ruta) = 0),
          CONSTRAINT ck_bitacora_estado CHECK (estado BETWEEN 100 AND 599)
        )""")
    op.execute("CREATE INDEX ix_bitacora_proyecto_momento ON obra.bitacora (proyecto_id, momento DESC)")
    op.execute("CREATE INDEX ix_bitacora_momento ON obra.bitacora (momento DESC)")
    solo_insertar("obra.bitacora")   # libro: ni UPDATE, ni DELETE, ni TRUNCATE
    # la 0023 dejó almacen.prestamo sin el disparador de TRUNCATE: se completa aquí
    op.execute("CREATE TRIGGER tg_almacen_prestamo_no_truncar BEFORE TRUNCATE ON almacen.prestamo "
               "FOR EACH STATEMENT EXECUTE FUNCTION util.rechazar_borrado()")
    op.execute("COMMENT ON TABLE obra.bitacora IS 'Bitácora de operaciones: quién hizo qué y cuándo. Sin cuerpos, parámetros, tokens, claves, IP ni datos personales. No se edita ni se borra.'")
    for col, txt in {
        "id": "Identificador interno.",
        "momento": "Hora del servidor en que terminó la operación.",
        "usuario_id": "Usuario que la hizo (vacío si no inició sesión o el token no era válido).",
        "proyecto_id": "Obra de la ruta, si la operación es de una obra.",
        "metodo": "Método HTTP (POST para cambios; GET solo si fue rechazado).",
        "ruta": "Ruta de la API sin parámetros de consulta (nunca lleva datos sensibles).",
        "estado": "Código HTTP con que terminó (201 creado, 403 sin permiso, 422 datos inválidos…).",
        "duracion_ms": "Cuánto tardó en milisegundos.",
    }.items():
        op.execute(f"COMMENT ON COLUMN obra.bitacora.{col} IS '{txt}'")

    op.execute("ALTER TABLE obra.usuario ADD COLUMN pin_hash text, ADD COLUMN pin_intentos integer NOT NULL DEFAULT 0, "
               "ADD COLUMN pin_bloqueado_hasta timestamptz")
    op.execute("COMMENT ON COLUMN obra.usuario.pin_hash IS 'PIN de 6 dígitos del panel de administración, con Argon2id (nunca el PIN en claro).'")
    op.execute("COMMENT ON COLUMN obra.usuario.pin_intentos IS 'Intentos fallidos seguidos del PIN; al 5.º se bloquea un rato.'")
    op.execute("COMMENT ON COLUMN obra.usuario.pin_bloqueado_hasta IS 'Hasta cuándo el PIN está bloqueado por intentos fallidos.'")

    op.execute("GRANT SELECT, INSERT ON obra.bitacora TO sigro_svc_obras")
    op.execute("GRANT UPDATE (pin_hash, pin_intentos, pin_bloqueado_hasta) ON obra.usuario TO sigro_svc_obras")


def downgrade() -> None:
    op.execute("""
        DO $$ BEGIN
          IF EXISTS (SELECT 1 FROM obra.bitacora) THEN
            RAISE EXCEPTION 'SIGRO-M24: la bitácora tiene registros; bajar la 0024 los borraría';
          END IF;
        END $$""")
    op.execute("REVOKE UPDATE (pin_hash, pin_intentos, pin_bloqueado_hasta) ON obra.usuario FROM sigro_svc_obras")
    op.execute("ALTER TABLE obra.usuario DROP COLUMN pin_bloqueado_hasta, DROP COLUMN pin_intentos, DROP COLUMN pin_hash")
    op.execute("DROP TRIGGER tg_almacen_prestamo_no_truncar ON almacen.prestamo")
    op.execute("DROP TABLE obra.bitacora")
