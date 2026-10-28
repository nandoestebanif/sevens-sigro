# Semillas · proveedores reales del cliente

`proveedores-cliente.json` / `.csv` — los **626 proveedores reales** de SEVENS,
extraídos de la lista que entregó el cliente (octubre 2026). Es la semilla de la
tabla `proveedor` para desarrollo y demo: con esto la pantalla de compras arranca
con datos verdaderos, no inventados.

## Campos

| Campo | Va a la tabla `proveedor` | Nota |
|---|---|---|
| `proveedor` | `razon_social` | tal como la tiene el cliente |
| `tipo_documento` | `tipo_documento` | RUC o DNI, corregido según el número (no según la etiqueta del origen) |
| `numero_documento` | `ruc` | **único en todo el sistema** (regla de HU-30); aquí ya viene sin duplicados |
| `documento_valido` | — | ver «datos sucios» abajo |
| `banco`, `cuenta`, `cci`, `titular_cuenta` | *no cargar por ahora* | SIGRO no maneja pagos; queda pendiente preguntar a Pool si lo quiere como dato informativo. Los números vienen enmascarados desde el origen. |
| `direccion` | `direccion` (si la columna existe) | SUNAT la devuelve también vía el micro de integraciones |
| `telefono` | `telefono` (si la columna existe) | pocos la tienen |

## Datos sucios · importa leer esto (corregido el 23 oct)

Clasificación con el **dígito verificador** del RUC (módulo 11, el mismo criterio del
cargador de Adiel y de la API), no solo por cantidad de dígitos:

| Grupo | Cantidad | Qué se hace |
|---|---|---|
| RUC con dígito verificador correcto | **612** | se carga; se valida en SUNAT recién cuando se use (HU-31) |
| DNI de 8 dígitos (persona natural sin RUC) | **7** | se carga «sin validar» y **nunca** va a SUNAT (decisión D-C5) |
| Documento imposible | **7** | se carga «sin validar» y queda en evidencia |

Los 7 imposibles: `12345678902`, `959163628`, `969774240`, `12345678901`, `123456`,
`10770933428` (11 dígitos con verificador malo) y `2045040472451` (13 dígitos). Son datos de
prueba o de digitación que viven en la base actual del cliente. **No corregirlos ni botarlos
a mano**: la demostración más barata de por qué SIGRO valida el documento al crearlo.

Además, 4 registros venían rotulados «DNI» con un RUC válido de 11 dígitos: el campo
`tipo_documento` se corrigió a RUC según el número, no según la etiqueta del origen.
(Una versión anterior de este archivo decía «12 inválidos»: contaba solo formato y no el
dígito verificador. Gracias a Adiel por detectarlo.)

Otros hallazgos: SEVENS aparece como proveedor de sí mismo (RUC 20606456892, legítimo:
compras entre obras), y un registro (#47) arrastra en `titular_cuenta` un fragmento de su
cuenta de detracciones por el ajuste de columnas del origen; no afecta los campos que se cargan.

## Cómo cargarla

Semilla de desarrollo, **no** migración: la migración crea la tabla (Henry),
esta semilla la llena. Sugerido: un script `python -m api.semillas.cargar_proveedores`
idempotente — si el `numero_documento` ya existe, actualiza en lugar de duplicar
(misma regla que la importación del catálogo, HU-07).

Este repositorio debe seguir **privado**: la lista es información comercial del
cliente.
