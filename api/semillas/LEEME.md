# Semillas · proveedores reales del cliente

`proveedores-cliente.json` / `.csv` — los **626 proveedores reales** de SEVENS,
extraídos de la lista que entregó el cliente (octubre 2026). Es la semilla de la
tabla `proveedor` para desarrollo y demo: con esto la pantalla de compras arranca
con datos verdaderos, no inventados.

## Campos

| Campo | Va a la tabla `proveedor` | Nota |
|---|---|---|
| `proveedor` | `razon_social` | tal como la tiene el cliente |
| `tipo_documento` | `tipo_documento` | RUC (620) o DNI (6) |
| `numero_documento` | `ruc` | **único en todo el sistema** (regla de HU-30); aquí ya viene sin duplicados |
| `documento_valido` | — | ver «datos sucios» abajo |
| `banco`, `cuenta`, `cci`, `titular_cuenta` | *no cargar por ahora* | SIGRO no maneja pagos; queda pendiente preguntar a Pool si lo quiere como dato informativo. Los números vienen enmascarados desde el origen. |
| `direccion` | `direccion` (si la columna existe) | SUNAT la devuelve también vía el micro de integraciones |
| `telefono` | `telefono` (si la columna existe) | pocos la tienen |

## Datos sucios · importa leer esto

12 registros traen `documento_valido: false`: RUC de 6, 8, 9 o 13 dígitos y
«DNI» con 11. Son datos de prueba que viven en la base actual del cliente.
**No corregirlos ni botarlos a mano:** cargarlos como `sin_validar` y que el
flujo normal (HU-31, consulta a SUNAT) los deje en evidencia. Es la
demostración más barata de por qué SIGRO valida el documento al crear el
proveedor y el sistema actual del cliente no.

Otros hallazgos: SEVENS aparece como proveedor de sí mismo (RUC 20606456892,
legítimo: compras entre obras), y un registro (#47) arrastra en
`titular_cuenta` un fragmento de su cuenta de detracciones por el ajuste de
columnas del origen; no afecta los campos que se cargan.

## Cómo cargarla

Semilla de desarrollo, **no** migración: la migración crea la tabla (Henry),
esta semilla la llena. Sugerido: un script `python -m api.semillas.cargar_proveedores`
idempotente — si el `numero_documento` ya existe, actualiza en lugar de duplicar
(misma regla que la importación del catálogo, HU-07).

Este repositorio debe seguir **privado**: la lista es información comercial del
cliente.
