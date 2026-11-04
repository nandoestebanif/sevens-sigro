# Víctor Adiel Luján · Product Owner · Backend

**Sprint 2: 29/10 al 11/11/2026.** El Sprint 1 cerró el 28/10.

**Tu área:** **Product Owner** y **todo el backend** (`api/`: obras, catálogo, compras, tareo, almacén, costo y el gateway). Eres el único que crea migraciones.

## Lo primero

1. **Como PO:** acepta desde la interfaz las HU que vuelven del Sprint 1 a medida que pasen sus pruebas. 39 de tus tareas pendientes son de «Aceptación»: sin tu aceptación ninguna HU llega a Done. Empieza por las **10 del Excel de pruebas** (abajo).
2. **Revisa los PR de estas guías** (abajo). Los de `api/` que tenías (#17, #18 y #24) se fusionaron el 03/11.
3. **Backend del Sprint 2:** tus HU de abajo, empezando por las que desbloquean a la app y a la web (traslados HU-42, conteo físico HU-46 en el servidor, cierre de obra HU-47).

## Ambiente de pruebas · vigente desde el 04/11

| Para | Dirección |
|---|---|
| **Web** | **https://sigro-web.onrender.com** |
| **App** (campo «Servidor») | **https://sigro-api.onrender.com** |
| **Postman** | `https://sigro-api.onrender.com/api/v1`, **como máximo 1 petición cada 5 s**; si no, Cloudflare bloquea el servidor |

- Corre en **un solo servicio de Render**, `sigro-api`, con un proceso por microservicio, y cada uno conserva su usuario
  y su esquema (ADR-10). Los ocho servicios viejos están suspendidos y se borran después de un día de uso normal.
- **En Neon, la migración 0010 se aplicó el 03/11** (antes estaba en 0009: almacén y costo no tenían base). Se crearon
  solo los usuarios `sigro_almacen` y `sigro_costo`, sin volver a correr `migrar.sh`, y la clave de `sigro_relevo` se
  cambió. Todo quedó en la incidencia I-6 del registro del sprint.
- **Fernando te pasa tu usuario por privado.** No lo escribas en el repositorio ni en el Excel. Más detalle en
  [`LEEME.md`](LEEME.md).

## Las 10 HU que aceptas después de sus pruebas

Archivo: [`docs/pruebas/SIGRO - Pruebas de aceptación (Sprint 2).xlsx`](../docs/pruebas/SIGRO%20-%20Pruebas%20de%20aceptaci%C3%B3n%20(Sprint%202).xlsx).
La hoja **«Resumen»** se llena sola con las hojas de cada probador. Tú completas la columna **«Acepta el PO (Sí/No)»** y
mueves la tarjeta «Aceptación», solo si **todas** las condiciones finales de esa HU cumplen.

| Prueba | HU | Tarjeta «Aceptación» |
|---|---|---|
| Nicolás | HU-01 · HU-03 · HU-07 · HU-25 | T-009 · T-017 · T-026 · T-110 |
| Henry | HU-08 · HU-19 · HU-24 · HU-29 · HU-30 | T-034 · T-087 · T-103 · T-135 · T-143 |
| Fernando | HU-12 | T-042 |

En HU-12, la condición 3 («la contraseña se guarda cifrada») pide que muestres en el código que se guarda con hash.

## Los PR que te toca revisar

| PR | Qué es | Qué miras |
|---|---|---|
| [#25](https://github.com/xteban-sp/sevens-sigro/pull/25) | Guías del equipo para el Sprint 2 | que el reparto y las tareas sean los acordados |
| [#30](https://github.com/xteban-sp/sevens-sigro/pull/30) | Guías al 04/11: ambiente en un nodo, Excel de pruebas y PR vigentes (apilado sobre #25) | que las HU de cada probador y los PR sean los acordados |

Primero se fusiona #25 y después #30.

## Tus tareas pendientes según el tablero · 70 tareas · 86 h · 1 en Doing

Son las que quedaron sin terminar en el tablero de cierre del Sprint 1 (`SIGRO - Sprint 1 · Registro del Sprint (cierre).xlsx`, hoja «Tablero») y vuelven al Sprint 2 con su avance.

| ID | HU | Fase | Tarea | h | Estado | Depende de |
|---|---|---|---|--:|---|---|
| T-244 | HT-06 | Backend | Publicar el OpenAPI de la API en el ambiente de pruebas | 1 | Doing | — |
| T-009 | HU-01 | Aceptación | Aceptar HU-01 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-008 |
| T-017 | HU-03 | Aceptación | Aceptar HU-03 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-016 |
| T-026 | HU-07 | Aceptación | Aceptar HU-07 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-025 |
| T-034 | HU-08 | Aceptación | Aceptar HU-08 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-033 |
| T-042 | HU-12 | Aceptación | Aceptar HU-12 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-041 |
| T-049 | HU-13 | Aceptación | Aceptar HU-13 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-048 |
| T-056 | HU-14 | Aceptación | Aceptar HU-14 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-055 |
| T-064 | HU-15 | Aceptación | Aceptar HU-15 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-063 |
| T-067 | HU-16 | Base de datos | Implementar las tablas asociadas a HU-16: partida, partida_recurso | 1 | To Do | T-066 |
| T-068 | HU-16 | Backend | Crear la tabla partida–recurso y programar el endpoint que la guarda | 5 | To Do | T-067 |
| T-072 | HU-16 | Aceptación | Aceptar HU-16 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-071 |
| T-079 | HU-17 | Aceptación | Aceptar HU-17 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-078 |
| T-087 | HU-19 | Aceptación | Aceptar HU-19 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-086 |
| T-090 | HU-23 | Base de datos | Implementar las tablas asociadas a HU-23: vista de avance sobre requerimiento, compra_detalle y movimiento | 1 | To Do | T-089 |
| T-091 | HU-23 | Backend | Programar el back-end de HU-23 (entidad, repositorio, servicio y controlador): la consulta de avance: requerido, comprado, recibido y consumido por insumo | 3 | To Do | T-090 |
| T-095 | HU-23 | Aceptación | Aceptar HU-23 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-094 |
| T-103 | HU-24 | Aceptación | Aceptar HU-24 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-102 |
| T-110 | HU-25 | Aceptación | Aceptar HU-25 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-109 |
| T-113 | HU-26 | Base de datos | Implementar las tablas asociadas a HU-26: alerta_reposicion y outbox de Notificaciones | 1 | To Do | T-112 |
| T-114 | HU-26 | Backend | Programar el back-end de HU-26 (entidad, repositorio, servicio y controlador): el motor de alertas: una por insumo y marcada como atendida al comprar | 2 | To Do | T-113 |
| T-115 | HU-26 | Integración | Publicar la alerta en el microservicio de Notificaciones (outbox) para avisar al residente | 2 | To Do | T-112 |
| T-119 | HU-26 | Aceptación | Aceptar HU-26 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-118 |
| T-122 | HU-27 | Base de datos | Implementar las tablas asociadas a HU-27: vista de sugerencia de compra | 1 | To Do | T-121 |
| T-123 | HU-27 | Backend | Programar el back-end de HU-27 (entidad, repositorio, servicio y controlador): el cálculo de la sugerencia de compra con su criterio | 3 | To Do | T-122 |
| T-127 | HU-27 | Aceptación | Aceptar HU-27 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-126 |
| T-135 | HU-29 | Aceptación | Aceptar HU-29 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-134 |
| T-143 | HU-30 | Aceptación | Aceptar HU-30 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-142 |
| T-151 | HU-32 | Aceptación | Aceptar HU-32 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-150 |
| T-154 | HU-35 | Base de datos | Implementar las tablas asociadas a HU-35: recepcion, recepcion_detalle y su movimiento de ingreso | 1 | To Do | T-153 |
| T-155 | HU-35 | Backend | Programar el back-end de HU-35 (entidad, repositorio, servicio y controlador): la recepción total o parcial que genera el ingreso al kárdex | 4 | To Do | T-154 |
| T-160 | HU-35 | Aceptación | Aceptar HU-35 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-159 |
| T-166 | HU-37 | Aceptación | Aceptar HU-37 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-165 |
| T-169 | HU-38 | Base de datos | Implementar las tablas asociadas a HU-38: movimiento.uuid_cliente UNIQUE | 1 | To Do | T-168 |
| T-170 | HU-38 | Backend | Programar el back-end de HU-38 (entidad, repositorio, servicio y controlador): la idempotencia por uuid_cliente y devolver el motivo del rechazo | 4 | To Do | T-169 |
| T-171 | HU-38 | Triggers y funciones | Implementar el índice único de uuid_cliente que vuelve idempotente el reenvío (HU-38) | 1 | To Do | T-169 |
| T-174 | HU-38 | Aceptación | Aceptar HU-38 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-173 |
| T-177 | HU-40 | Base de datos | Implementar las tablas asociadas a HU-40: movimiento (append-only, costo_unitario congelado) | 1 | To Do | T-176 |
| T-178 | HU-40 | Backend | Programar el back-end de HU-40 (entidad, repositorio, servicio y controlador): la salida: rechazar si supera el saldo y congelar el costo promedio | 7 | To Do | T-177 |
| T-179 | HU-40 | Triggers y funciones | Implementar el trigger que impide UPDATE y DELETE en movimiento y el que rechaza fechas fuera del plazo (HU-40) | 1 | To Do | T-177 |
| T-182 | HU-40 | Aceptación | Aceptar HU-40 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-181 |
| T-185 | HU-43 | Base de datos | Implementar las tablas asociadas a HU-43: vista de saldo derivada de movimiento | 1 | To Do | T-184 |
| T-186 | HU-43 | Backend | Programar el back-end de HU-43 (entidad, repositorio, servicio y controlador): el cálculo del stock desde el libro de movimientos, por almacén y consolidado | 2 | To Do | T-185 |
| T-187 | HU-43 | Triggers y funciones | Implementar la función que calcula el saldo SUM(cantidad × signo) por almacén e insumo (HU-43) | 1 | To Do | T-185 |
| T-192 | HU-43 | Aceptación | Aceptar HU-43 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-191 |
| T-195 | HU-45 | Base de datos | Implementar las tablas asociadas a HU-45: movimiento.movimiento_origen_id (reverso) | 1 | To Do | T-194 |
| T-196 | HU-45 | Backend | Programar el back-end de HU-45 (entidad, repositorio, servicio y controlador): el reverso que apunta al original, sin UPDATE ni DELETE | 3 | To Do | T-195 |
| T-200 | HU-45 | Aceptación | Aceptar HU-45 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-199 |
| T-203 | HU-55 | Base de datos | Implementar las tablas asociadas a HU-55: costo_promedio por insumo y almacén | 1 | To Do | T-202 |
| T-204 | HU-55 | Backend | Programar el back-end de HU-55 (entidad, repositorio, servicio y controlador): el costo promedio ponderado congelado en cada salida, sin IGV de factura (NIC 2) | 7 | To Do | T-203 |
| T-205 | HU-55 | Triggers y funciones | Implementar la función que recalcula el costo promedio ponderado en cada ingreso (HU-55) | 1 | To Do | T-203 |
| T-208 | HU-55 | Aceptación | Aceptar HU-55 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-207 |
| T-211 | HU-57 | Base de datos | Implementar las tablas asociadas a HU-57: vista de desviación contra requerimiento | 1 | To Do | T-210 |
| T-212 | HU-57 | Backend | Programar el back-end de HU-57 (entidad, repositorio, servicio y controlador): la comparación del consumo contra lo planificado en el S10 | 5 | To Do | T-211 |
| T-216 | HU-57 | Aceptación | Aceptar HU-57 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-215 |
| T-224 | HU-62 | Aceptación | Aceptar HU-62 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-223 |
| T-232 | HU-63 | Aceptación | Aceptar HU-63 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | T-231 |
| T-234 | HT-01 | Aceptación | Aceptar HT-01 como Product Owner y moverla a Done | 0.5 | To Do | — |
| T-236 | HT-02 | Aceptación | Aceptar HT-02 como Product Owner y moverla a Done | 0.5 | To Do | — |
| T-239 | HT-03 | Aceptación | Aceptar HT-03 como Product Owner y moverla a Done | 0.5 | To Do | — |
| T-241 | HT-04 | Aceptación | Aceptar HT-04 como Product Owner y moverla a Done | 0.5 | To Do | — |
| T-243 | HT-05 | Aceptación | Aceptar HT-05 como Product Owner y moverla a Done | 0.5 | To Do | — |
| T-246 | HT-06 | Aceptación | Aceptar HT-06 como Product Owner y moverla a Done | 0.5 | To Do | — |
| T-248 | HT-07 | Backend | Crear la obra Multifamiliar Flores con su relación de insumos del S10 en la base de pruebas | 1 | To Do | — |
| T-249 | HT-07 | Aceptación | Aceptar HT-07 como Product Owner y moverla a Done | 0.5 | To Do | — |
| T-252 | HT-08 | Aceptación | Aceptar HT-08 como Product Owner y moverla a Done | 0.5 | To Do | — |
| T-264 | HT-09 | Base de datos | Revisar y aprobar la migración 0009: un rol de base por servicio con permisos solo sobre su esquema | 1 | To Do | T-263 |
| T-265 | HT-09 | Aceptación | Aceptar HT-09 como Product Owner y moverla a Done | 0.5 | To Do | T-263, T-264 |
| T-267 | HT-10 | Modelado UML | Revisar la propuesta física de almacén y costo (0010) y decidir sus 5 puntos abiertos | 1.5 | To Do | T-266 |
| T-268 | HT-10 | Aceptación | Aceptar HT-10 como Product Owner y moverla a Done | 0.5 | To Do | T-266, T-267 |

## Tus historias del Sprint 2

**Backend:** HU-02 Ampliar plazo del proyecto · HU-05 Registrar almacenes de la obra · HU-18 Repartir acero por diámetro · HU-20 Registrar requerimiento por fase · HU-21 Avisar exceso del requerimiento · HU-42 Trasladar material entre obras · HU-47 Cerrar obra y transferir saldo · HU-54 Declarar estado de herramientas al cierre · HU-64 Consultar horas acumuladas · HU-04 Definir el día de corte del periodo · HU-09 Registrar nombres comerciales del insumo · HU-10 Buscar insumo · HU-22 Agregar insumo no presupuestado · HU-33 Registrar precio pagado · HU-36 Avisar recepción excedida · HU-66 y HU-67 (solicitud de materiales, ficha CR-04): el servidor y sus permisos.

Los criterios de aceptación están en `docs/historias-sprint-2.md`. El campo «Responsable» de ese archivo es del
reparto anterior: manda el área de esta guía.

## Los 3 cortes diarios del tablero (resumen)

Regla del docente: el tablero se mueve **como mínimo 3 veces al día**, y cada movimiento lleva **fecha y hora**.
Se registra en [`docs/scrum/SIGRO - Tablero por cortes horarios.xlsx`](../docs/scrum/SIGRO%20-%20Tablero%20por%20cortes%20horarios.xlsx)
(hoja «Plantilla Sprint 2», 39 cortes del 30/10 al 11/11).

| Corte | Qué se hace |
|---|---|
| **08:00 · Inicio** | Cada integrante pasa a Doing las tareas que hará hoy, **solo si su HU cumple la DoR (L1–L12)**. Máximo **2 tareas en Doing** por persona. |
| **12:00 · Mediodía** | Lo terminado pasa de Doing a Done, **solo si cumple su criterio**; se anota lo que avanzó. |
| **18:00 · Cierre** | Se mueve lo terminado en la tarde y se deja listo el día siguiente. |
| **11/11 · Último día del sprint** | Todas las tareas en Done con el 100 % de la DoD. La HU pasa a Done cuando su tarea «Aceptación» cumple la DoD (T1–T10) y la acepta el PO. |

Fernando no trabaja del viernes en la noche al sábado en la tarde: esos cortes los registra otro integrante.

## Cómo trabajas (igual para los cuatro)

- Lee `CLAUDE.md` (las diez reglas de dominio y la Definición de Terminado) y [`LEEME.md`](LEEME.md) (los 3 cortes diarios).
- Cada movimiento de tarjeta lleva fecha, **hora**, quién y evidencia (PR, captura o archivo). To Do → Doing exige la DoR;
  Doing → Done exige el criterio de su fase (hoja «Criterios acordados» del registro del sprint).
- Una rama por tarea o HU (`hu-46-conteo-fisico`), nada directo a `main`, PR pequeño que diga qué HU y qué condiciones
  finales cubre. **Lo aprueba alguien distinto del autor.** Una rama que vive más de dos días se parte.
- **Sube tu trabajo aunque esté incompleto**: lo que no está en el repo no existe para la Review.
- Solo Adiel crea migraciones: si necesitas una tabla, pídesela.
- **11/11: todo en Done. Se etiqueta `sprint-2`** y eso es lo que se demuestra.
