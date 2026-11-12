"""Proveedor «con alerta SUNAT» (HU-31, decisión D12 del PO).

Hasta la 0013 un proveedor que SUNAT informa no activo o no habido quedaba como `sin_validar`, igual que el que SUNAT ni
siquiera respondió. Son casos distintos y el residente necesita verlos distintos:

  - validado        SUNAT respondió: activo y habido.
  - sin_validar     no se pudo consultar (SUNAT/Integraciones caídos, DNI, dígito verificador malo): «por validar», se reintenta.
  - con_alerta_sunat SUNAT respondió y NO es activo y habido: avisa, no bloquea (D12); la compra queda marcada.

Cambios (ninguno borra ni reescribe datos):
  - `ck_proveedor_estado_validacion` admite el tercer valor.
  - `alerta_sunat`: el estado y la condición que informó SUNAT, p. ej. «BAJA DEFINITIVA / NO HABIDO». Obligatoria si y solo si el
    proveedor está «con_alerta_sunat».
  - `consultado_en`: cuándo se hizo la última consulta que SUNAT respondió.
  - El usuario de la aplicación y el del servicio compras (0009) pueden actualizar las dos columnas nuevas, como ya podían con
    estado_validacion y validado_en.
  - `ck_proveedor_validacion_con_fecha` no cambia: `validado_en` sigue siendo no nulo solo si está validado.

Los proveedores que hoy están `sin_validar` se quedan así: no se sabe si fue por falla de consulta o por alerta, y se
reclasifican la próxima vez que alguien pulse «Validar» (POST /proveedores/{id}/validar).

Revision ID: 0014
Revises: 0013
"""
from alembic import op

revision = "0014"
down_revision = "0013"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(
        """
        ALTER TABLE compras.proveedor
          ADD COLUMN alerta_sunat  text,
          ADD COLUMN consultado_en timestamptz,
          DROP CONSTRAINT ck_proveedor_estado_validacion,
          ADD CONSTRAINT ck_proveedor_estado_validacion CHECK (estado_validacion IN ('validado', 'sin_validar', 'con_alerta_sunat')),
          ADD CONSTRAINT ck_proveedor_alerta_sunat CHECK (
            (estado_validacion = 'con_alerta_sunat') = (alerta_sunat IS NOT NULL)
            AND (alerta_sunat IS NULL OR btrim(alerta_sunat) <> '')
          )
        """
    )
    # 0009: cada servicio tiene su propio rol y la copia de permisos se hizo en esa migración; hay que conceder a los dos.
    op.execute("GRANT UPDATE (alerta_sunat, consultado_en) ON compras.proveedor TO sigro_app")
    op.execute("GRANT UPDATE (alerta_sunat, consultado_en) ON compras.proveedor TO sigro_svc_compras")


def downgrade() -> None:
    # Volver al modelo de dos estados: la alerta pasa a «sin validar» (es lo que eran hasta la 0013).
    op.execute("UPDATE compras.proveedor SET estado_validacion = 'sin_validar', alerta_sunat = NULL WHERE estado_validacion = 'con_alerta_sunat'")
    op.execute(
        """
        ALTER TABLE compras.proveedor
          DROP CONSTRAINT ck_proveedor_alerta_sunat,
          DROP CONSTRAINT ck_proveedor_estado_validacion,
          ADD CONSTRAINT ck_proveedor_estado_validacion CHECK (estado_validacion IN ('validado', 'sin_validar')),
          DROP COLUMN alerta_sunat,
          DROP COLUMN consultado_en
        """
    )
