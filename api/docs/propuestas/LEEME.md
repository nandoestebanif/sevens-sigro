# Propuesta 0010 · microservicios almacén y costo (modelo físico)

**Estado:** borrador para Adiel (solo él crea migraciones). No está en `migraciones/versions/`, así que Alembic no la aplica.
**Archivo:** `api/migraciones/propuestas/0010_almacen_y_costo.py` · **Diagrama:** `fisico-almacen-costo.png`

```bash
python docs/propuestas/probar_0010.py     # base temporal: 0009 → 0010 → baja a 0009 → 0010, y 16 escenarios
python docs/propuestas/diagrama_0010.py   # su diagrama físico, con el mismo generador que el modelo oficial
```

## Qué propone

| Servicio | Tablas | Idea central |
|---|---|---|
| **almacen** | `movimiento` (libro), `recepcion`, `traslado`, `conteo` + `conteo_linea`, réplicas `almacen_ref` e `insumo_ref` | El saldo y el costo promedio **no son columnas**: salen de los movimientos (vistas `saldo`, `saldo_obra`, `kardex`, `recibido_por_compra`). Un disparador valida stock y **congela** el promedio en cada salida bajo un candado por (almacén, insumo). |
| **costo** | `evento_recibido`, `hecho` | **Modelo de lectura**: recibe hechos ya valorizados por evento (idempotente por `evento_id`) y responde el gasto en recursos (`gasto_por_obra`). No calcula el promedio: eso tiene que pasar en la misma transacción que la salida. |

**Sin claves foráneas hacia otros servicios desde el día 1** (la fase 2 del ADR-09 aplicada de entrada). Lo ajeno
vive en réplicas locales que el servicio llena al validar contra obras y catálogo por `/interno`. Compra, periodo,
dispositivo y usuario quedan como referencias lógicas validadas por API.

## Escenarios probados (16, todos en verde)

HU-35 recepción con costo · R4 promedio ponderado (100 a 20 + 50 a 26 = 22) · HU-40 salida congela el promedio e
ignora el costo que mande el cliente · R4 un ingreso posterior no cambia una salida ya hecha · HU-40 no sacar más de lo
disponible · HU-40 motivo y quién retira obligatorios · R3 reenvío no duplica · R1 ni UPDATE ni DELETE (el rol ni
siquiera tiene permiso) · HU-45 reverso exacto, una sola vez, y un reverso no se revierte · HU-41 origen y costo
obligatorios · HU-42 el costo viaja con el material · HU-46 saldo teórico lo pone el servidor, la diferencia exige
motivo y el ajuste va al promedio · ADR-09 almacén no puede leer obra ni compras · costo idempotente ·
**dos salidas simultáneas no dejan el saldo en negativo**.

## Endpoints que habilita (para la app y la web)

| Método y ruta (`/api/v1`) | HU |
|---|---|
| `POST /obras/{id}/almacen/salidas` (el contrato que ya usa la app, más `almacen_id`) | 40, 37, 38 |
| `POST /obras/{id}/almacen/recepciones` · `POST /obras/{id}/almacen/ingresos` | 35, 36 · 41 |
| `POST /obras/{id}/almacen/traslados` · `POST /obras/{id}/almacen/movimientos/{mid}/reverso` | 42 · 45 |
| `POST /obras/{id}/almacen/conteos` (+ líneas y confirmación) · `POST /obras/{id}/cierre` | 46 · 47 |
| `GET /obras/{id}/almacen/stock?almacen=` · `GET /obras/{id}/almacen/kardex?insumo=` | 43 · 45 |
| `GET /obras/{id}/costo/gasto` · `GET /obras/{id}/costo/inventario-valorizado` | 58 · 56 |

En `app/enrutado.py` bastan dos líneas: `/obras/{id}/(almacen|cierre)` → almacen y `/obras/{id}/costo` → costo.

## Decisiones que Adiel tiene que tomar

1. **¿Promedio por almacén o por obra?** La regla 4 del CLAUDE.md dice «en ese almacén»; HU-55 dice «en la obra».
   La propuesta sigue al CLAUDE.md (por almacén). Con dos almacenes en la obra, los números cambian: hay que fijar uno
   y corregir el otro texto.
2. **La bandeja de salida solo sirve para avisos.** `outbox.evento` exige `payload.destinatarios` no vacío
   (`ck_evento_destinatarios`). Un evento de integración (salida registrada → costo) no tiene destinatarios.
   Propuesta: una columna `destino` (`aviso` | `integracion`) y que el CHECK aplique solo a `aviso`.
3. **Dispositivo autorizado (regla 5).** `POST /interno/autorizar` hoy recibe usuario, obra y roles. Para los
   movimientos del celular hace falta también `dispositivo` (identificador), y que obras responda 403 si no está activo.
4. **¿Movimientos en un periodo cerrado?** Tareo solo admite correcciones en un periodo cerrado. Propuesta: la misma
   regla para almacén (solo reversos), validada en el servicio con `/interno/obras/{id}/periodos`.
5. **HU-41 «saldo de otra obra»** solo si esa obra no está en el sistema: lo valida el servicio preguntando a obras.
   (El criterio cita el traslado como HU-40; es HU-42. Corregirlo en `historias-sprint-2.md`.)
6. **Límites conocidos:** el candado usa los id como `int` (vale hasta 2 147 483 647); el promedio se redondea a 6
   decimales y el residuo es menor que 0,000001 por unidad.
