"""Migración 0017 (va DESPUÉS de la 0016). Insumo agregado a mano al requerimiento de la obra (T-340, HU-19 / HT-11).

El S10 no siempre trae todo lo que la obra termina necesitando (un insumo que el presupuesto no previó, una partida
nueva). Hasta hoy la única forma de que un insumo estuviera en el requerimiento era importarlo del S10. Ahora el residente
o el administrador pueden agregarlo a mano, y queda distinguible para siempre de lo que vino del S10:

  - compras.requerimiento.origen: 's10' (lo importado; todas las filas existentes) o 'manual'.
  - compras.requerimiento.motivo_alta: por qué se agregó a mano. Obligatorio (y no en blanco) si el origen es 'manual';
    vacío (NULL) si viene del S10. Quién y cuándo ya los guardan creado_por y creado_en.

Las dos columnas no se editan: sigro_app solo tiene UPDATE sobre cantidad_planificada (0008), y el disparador
tg_requerimiento_ajuste_obligatorio se amplía para rechazar también un cambio de origen o de motivo de alta, por si en
el futuro alguien concede más permisos de los debidos. La cantidad de una línea manual se corrige igual que cualquier
otra: con un ajuste motivado.

Base con datos: las filas existentes quedan con origen 's10' (es de donde vinieron: hasta hoy no había otra vía).
Vuelta atrás: si ya hay líneas manuales, el downgrade NO las borra (regla 10: nada se borra) y se detiene con un mensaje;
hay que decidir qué hacer con ellas antes de bajar.

Autorizada por el PO (Víctor Adiel Luján) el 13/11/2026, al decidir que T-340 entra en este sprint; la redacta Claude a su
pedido. Revisar antes de aplicar.

Revision ID: 0017
Revises: 0016
"""
from alembic import op

revision = "0017"
down_revision = "0016"
branch_labels = None
depends_on = None

_DISPARADOR = """
        CREATE OR REPLACE FUNCTION compras.tg_requerimiento_ajuste_obligatorio() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF (NEW.proyecto_id, NEW.insumo_id, NEW.precio_planificado, NEW.creado_por, NEW.creado_en{extra_new})
             IS DISTINCT FROM (OLD.proyecto_id, OLD.insumo_id, OLD.precio_planificado, OLD.creado_por, OLD.creado_en{extra_old}) THEN
            RAISE EXCEPTION 'SIGRO-Q1: del requerimiento solo se ajusta la cantidad' USING ERRCODE = 'restrict_violation';
          END IF;
          IF NEW.cantidad_planificada <> OLD.cantidad_planificada AND NOT EXISTS (
              SELECT 1 FROM compras.requerimiento_ajuste a
               WHERE a.requerimiento_id = NEW.id AND a.cantidad_anterior = OLD.cantidad_planificada
                 AND a.cantidad_nueva = NEW.cantidad_planificada AND a.ajustado_en = now()) THEN
            RAISE EXCEPTION 'SIGRO-Q2: ajustar la cantidad exige registrar el ajuste con su motivo' USING ERRCODE = 'restrict_violation';
          END IF;
          RETURN NEW;
        END $$
"""


def upgrade() -> None:
    op.execute(
        """
        ALTER TABLE compras.requerimiento
          ADD COLUMN origen text NOT NULL DEFAULT 's10',
          ADD COLUMN motivo_alta text,
          ADD CONSTRAINT ck_requerimiento_origen CHECK (origen IN ('s10', 'manual')),
          ADD CONSTRAINT ck_requerimiento_motivo_alta CHECK (
              (origen = 's10' AND motivo_alta IS NULL) OR (origen = 'manual' AND btrim(coalesce(motivo_alta, '')) <> ''))
        """
    )
    op.execute(_DISPARADOR.format(extra_new=", NEW.origen, NEW.motivo_alta", extra_old=", OLD.origen, OLD.motivo_alta"))


def downgrade() -> None:
    op.execute(
        """
        DO $$
        BEGIN
          IF EXISTS (SELECT 1 FROM compras.requerimiento WHERE origen = 'manual') THEN
            RAISE EXCEPTION 'SIGRO-M17: hay insumos agregados a mano en el requerimiento; bajar la 0017 los confundiría con '
                            'lo importado del S10. Decide qué hacer con ellos antes de revertir.';
          END IF;
        END $$
        """
    )
    op.execute(_DISPARADOR.format(extra_new="", extra_old=""))
    op.execute(
        """
        ALTER TABLE compras.requerimiento
          DROP CONSTRAINT ck_requerimiento_motivo_alta,
          DROP CONSTRAINT ck_requerimiento_origen,
          DROP COLUMN motivo_alta,
          DROP COLUMN origen
        """
    )
