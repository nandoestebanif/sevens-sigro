"""PROPUESTA (no aplicada) · Tres índices para las consultas más usadas, medidos con datos reales del cliente.

Estado: BORRADOR para revisión de Adiel (solo él crea migraciones). Vive en `migraciones/propuestas/`, fuera de
`versions/`, así que Alembic no la aplica. Para adoptarla: moverla a `versions/` y ajustar `down_revision` a la última
migración real que haya en ese momento (hoy sería la 0010 de almacén y costo si se adopta antes; si no, la 0009).

Medición (docs/rendimiento-web-api.md, `api/docs/rendimiento/medir_consultas.py explicar [--indices]`), PostgreSQL 16,
catálogo real (2 243 del cliente + 284 del S10), 626 proveedores reales y volumen de EJEMPLO rotulado
(1 200 compras, 5 400 líneas, 300 solicitudes). Mediana de 5 `EXPLAIN (ANALYZE, BUFFERS)` con la caché caliente:

| Índice | Consulta | Sin índice | Con índice |
|---|---|---|---|
| ix_insumo_codigo_upper | compras → catálogo `POST /interno/insumos/buscar` (`upper(codigo) = ANY(...)`) | 0.60 ms · Seq Scan · 40 bloques | 0.03 ms · Index Scan · 12 bloques |
| ix_compra_proveedor_registrada | `GET /proveedores?solo_para_comprar=true` (cuenta y última compra por proveedor) | 6.34 ms · 4 913 bloques | 1.76 ms · 2 517 bloques |
| ix_solicitud_linea_solicitud_id | `GET /obras/{id}/solicitudes` (líneas por solicitud; el único actual es PARCIAL y no sirve para las líneas sin código) | 0.92 ms | 0.55 ms |

Descartados tras medir: índices trigram (pg_trgm) para `ILIKE '%texto%'` en insumo y proveedor. Con 2 527 insumos y 626
proveedores el planificador sigue prefiriendo el Seq Scan (40 bloques, ~1 ms): no aportan hoy y exigen una extensión.
Reconsiderarlos si el catálogo pasa de unas decenas de miles de filas.

Ninguno cambia reglas ni datos: solo lectura más barata. En una base con datos grandes se crearían con
`CREATE INDEX CONCURRENTLY` fuera de transacción; con los volúmenes de SIGRO un CREATE INDEX normal tarda milisegundos.

Revision ID: 0011
Revises: 0010
"""
from alembic import op

revision = "0011"
down_revision = "0010"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # catálogo: compras, requerimiento y reposición piden insumos por código sin distinguir mayúsculas
    op.execute("CREATE INDEX ix_insumo_codigo_upper ON catalogo.insumo (upper(codigo))")
    # compras: «compras registradas» y «última compra» de cada proveedor en la lista de proveedores
    op.execute("CREATE INDEX ix_compra_proveedor_registrada ON compras.compra (proveedor_id, fecha) WHERE estado = 'registrada'")
    # compras: las líneas de una solicitud, con o sin insumo del catálogo
    op.execute("CREATE INDEX ix_solicitud_linea_solicitud_id ON compras.solicitud_linea (solicitud_id)")


def downgrade() -> None:
    op.execute("DROP INDEX compras.ix_solicitud_linea_solicitud_id")
    op.execute("DROP INDEX compras.ix_compra_proveedor_registrada")
    op.execute("DROP INDEX catalogo.ix_insumo_codigo_upper")
