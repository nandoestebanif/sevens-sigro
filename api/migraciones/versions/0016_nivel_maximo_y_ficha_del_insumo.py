"""Migración 0016 (va DESPUÉS de la 0014 de HU-31 y la 0015 de HU-34 de Adiel). Nivel máximo de reposición (D07, HU-25) y medida, norma y marca del insumo en sus propios campos (D08 y D09, HU-08).

Defectos del Registro de incidencias del Sprint 1, que se corrigen en HT-11:

  - D07 · compras.parametro_reposicion.nivel_maximo: hasta cuánto llenar el almacén al reponer. Es opcional (las
    filas anteriores quedan en NULL: no se inventa un máximo) y, si se da, debe ser mayor que el punto de reposición.
    La tabla sigue siendo un historial: cambiar el máximo es registrar una fila nueva, como el resto de parámetros.
    La vista parametro_reposicion_vigente lo expone como última columna (CREATE OR REPLACE conserva sus permisos).

  - D08 · catalogo.insumo.medida, norma y marca: hoy van escritas dentro del nombre («TUBO PVC 4" NTP 399.003
    PAVCO»). Ahora cada una tiene su campo; la descripción queda como el material. Las filas existentes quedan en
    NULL: separar los nombres antiguos se hace al editarlos, no aquí (no se adivina qué parte es la marca).

  - D09 · no se acepta dos veces el mismo material con la misma medida y marca aunque cambien mayúsculas o espacios.
    Se compara con catalogo.normalizar(): minúsculas, sin espacios al borde y con un solo espacio entre palabras.
    El índice único es PARCIAL (solo filas con medida o marca): los 2 527 insumos que ya existen no tienen esos campos
    y entre ellos hay nombres repetidos del catálogo del cliente y del S10 que aún no se emparejan (decisión pendiente
    del PO); un índice total haría fallar la migración en Neon. Para un insumo sin medida ni marca la regla la
    aplica el servicio (T-337) antes de insertar.

Las tres columnas del insumo se pueden corregir como la descripción, así que reciben UPDATE por columna, tanto el
usuario de la aplicación (sigro_app) como el rol del servicio catálogo (sigro_svc_catalogo, ADR-09).

Autorizada por el PO (Víctor Adiel Luján) el 12/11/2026; la redacta Claude a su pedido. Revisar antes de aplicar.

Revision ID: 0016
Revises: 0015
"""
from alembic import op

revision = "0016"
down_revision = "0015"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # ---- D07 · nivel máximo -------------------------------------------------------------------------------------
    op.execute(
        """
        ALTER TABLE compras.parametro_reposicion
          ADD COLUMN nivel_maximo numeric(18, 4),
          ADD CONSTRAINT ck_parametro_reposicion_nivel_maximo
              CHECK (nivel_maximo IS NULL OR nivel_maximo > punto_reposicion)
        """
    )
    op.execute(
        """
        CREATE OR REPLACE VIEW compras.parametro_reposicion_vigente AS
        SELECT DISTINCT ON (proyecto_id, insumo_id) id, proyecto_id, insumo_id, stock_seguridad, punto_reposicion,
               registrado_por, vigente_desde, nivel_maximo
          FROM compras.parametro_reposicion
         ORDER BY proyecto_id, insumo_id, id DESC
        """
    )

    # ---- D08 · medida, norma y marca en su campo ------------------------------------------------------------------
    op.execute(
        """
        CREATE FUNCTION catalogo.normalizar(t text) RETURNS text
          LANGUAGE sql IMMUTABLE PARALLEL SAFE
          AS $$ SELECT lower(regexp_replace(btrim(t), '\\s+', ' ', 'g')) $$
        """
    )
    op.execute(
        """
        ALTER TABLE catalogo.insumo
          ADD COLUMN medida text,
          ADD COLUMN norma  text,
          ADD COLUMN marca  text,
          ADD CONSTRAINT ck_insumo_medida CHECK (medida IS NULL OR btrim(medida) <> ''),
          ADD CONSTRAINT ck_insumo_norma  CHECK (norma  IS NULL OR btrim(norma)  <> ''),
          ADD CONSTRAINT ck_insumo_marca  CHECK (marca  IS NULL OR btrim(marca)  <> '')
        """
    )

    # ---- D09 · mismo material, medida y marca = mismo insumo ------------------------------------------------------
    op.execute(
        """
        CREATE UNIQUE INDEX uq_insumo_material_medida_marca ON catalogo.insumo (
            catalogo.normalizar(descripcion),
            coalesce(catalogo.normalizar(medida), ''),
            coalesce(catalogo.normalizar(marca), '')
        ) WHERE medida IS NOT NULL OR marca IS NOT NULL
        """
    )
    # para que el servicio busque el duplicado de un insumo sin medida ni marca sin recorrer la tabla
    op.execute("CREATE INDEX ix_insumo_material_normalizado ON catalogo.insumo (catalogo.normalizar(descripcion))")

    for rol in ("sigro_app", "sigro_svc_catalogo"):
        op.execute(f"GRANT UPDATE (medida, norma, marca) ON catalogo.insumo TO {rol}")
        op.execute(f"GRANT EXECUTE ON FUNCTION catalogo.normalizar(text) TO {rol}")


def downgrade() -> None:
    for rol in ("sigro_app", "sigro_svc_catalogo"):
        op.execute(f"REVOKE UPDATE (medida, norma, marca) ON catalogo.insumo FROM {rol}")
    op.execute("DROP INDEX IF EXISTS catalogo.ix_insumo_material_normalizado")
    op.execute("DROP INDEX IF EXISTS catalogo.uq_insumo_material_medida_marca")
    op.execute(
        """
        ALTER TABLE catalogo.insumo
          DROP CONSTRAINT IF EXISTS ck_insumo_marca,
          DROP CONSTRAINT IF EXISTS ck_insumo_norma,
          DROP CONSTRAINT IF EXISTS ck_insumo_medida,
          DROP COLUMN IF EXISTS marca,
          DROP COLUMN IF EXISTS norma,
          DROP COLUMN IF EXISTS medida
        """
    )
    op.execute("DROP FUNCTION IF EXISTS catalogo.normalizar(text)")

    # una vista no puede perder columnas con CREATE OR REPLACE: se recrea como en 0008 y se devuelven sus permisos
    op.execute("DROP VIEW compras.parametro_reposicion_vigente")
    op.execute(
        """
        CREATE VIEW compras.parametro_reposicion_vigente AS
        SELECT DISTINCT ON (proyecto_id, insumo_id) id, proyecto_id, insumo_id, stock_seguridad, punto_reposicion,
               registrado_por, vigente_desde
          FROM compras.parametro_reposicion
         ORDER BY proyecto_id, insumo_id, id DESC
        """
    )
    op.execute("GRANT SELECT ON compras.parametro_reposicion_vigente TO sigro_app")
    op.execute("GRANT SELECT ON compras.parametro_reposicion_vigente TO sigro_svc_compras")
    op.execute(
        """
        ALTER TABLE compras.parametro_reposicion
          DROP CONSTRAINT IF EXISTS ck_parametro_reposicion_nivel_maximo,
          DROP COLUMN IF EXISTS nivel_maximo
        """
    )
