# Fernando Istaña · Scrum Master · Despliegue y web

**Sprint 2: 29/10 al 11/11/2026.** El Sprint 1 cerró el 28/10.

**Tu área:** **Scrum Master**, **despliegue** del ambiente de pruebas (Render + Neon, `render.yaml`) y **web** (`web/`) junto con Henry. Sigue a tu cargo `integraciones/` (S10, SUNAT, RENIEC).

## Lo primero

1. **Como SM:** que los 3 cortes diarios queden registrados con hora en la plantilla del Sprint 2, incluidos los que otro integrante registra por ti del viernes en la noche al sábado en la tarde.
2. **Despliegue:** cada PR fusionado a `main` queda desplegado en el ambiente de pruebas (T8 de la DoD). Con el #24: la cadena `-pooler` de Neon en `SIGRO_APP_DATABASE_URL` y la medición del después.
3. **Web:** las HU web del Sprint 2, repartidas con Henry en el corte de las 08:00.

## Los PR que te toca revisar

Todos los PR abiertos hoy (#15 al #24) salieron de tu cuenta, así que **no apruebas ninguno**: los aprueba alguien distinto del autor. Te toca atender las observaciones y fusionar en orden lo que esté aprobado:

- web: **#15** → **#23** (el #23 está apilado sobre el #15) · **#16** · **#18**
- app: **#20** → **#21** → **#22**
- api: **#17** · **#24**

Tú revisas los PR que abran Adiel, Henry o Nicolás desde sus cuentas.

## Tus tareas pendientes según el tablero · 20 tareas · 35.5 h · 6 en Doing

Son las que quedaron sin terminar en el tablero de cierre del Sprint 1 (`SIGRO - Sprint 1 · Registro del Sprint (cierre).xlsx`, hoja «Tablero») y vuelven al Sprint 2 con su avance.

> Tienes **4 tareas en Doing fuera de Scrum** (T-069, T-233, T-263 y T-269), y el máximo es 2. T-233 (PR #9), T-263 (PR #8) y T-269 (PR #11) ya tienen su PR fusionado: si cumplen su criterio, pásalas a Done en el próximo corte con esa evidencia.

| ID | HU | Fase | Tarea | h | Estado | Depende de |
|---|---|---|---|--:|---|---|
| T-069 | HU-16 | Integración | Programar el lector del PDF del APU (371 páginas), identificando la partida por código y descripción — integración con S10 (análisis de precios unitarios) | 5 | Doing | T-066 |
| T-233 | HT-01 | DevOps | Desplegar el ambiente de pruebas (API, base de datos y web) que exige el T8 de la DoD | 6 | Doing | — |
| T-253 | — | Scrum | Armar el tablero en GitHub Projects con las plantillas de DoR y DoD | 2 | Doing | — |
| T-254 | — | Scrum | Facilitar el Daily Scrum de 15 minutos cada día, registrarlo y actualizar el burndown | 2 | Doing | — |
| T-263 | HT-09 | Backend | Separar la API en gateway y servicios obras, catálogo, compras y tareo, con su API interna /interno y token entre servicios | 6 | Doing | T-262 |
| T-269 | HU-07 | Backend | Pasar la importación del S10 por el gateway: POST /api/v1/catalogo/importaciones-s10 en el servicio catálogo reenvía el PDF a Integraciones con X-Servicio-Token; la web lo usa con su JWT (incidencia del 28/10, decisión «A» del 22/10) | — | Doing | T-021, T-023, T-024 |
| T-041 | HU-12 | Pruebas | Probar la funcionalidad de HU-12 (la API con Postman, la pantalla web y la app) contra cada condición final y aprobar su PR | 1 | To Do | T-037, T-038, T-039, T-040, T-233 |
| T-048 | HU-13 | Pruebas | Probar la funcionalidad de HU-13 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-045, T-046, T-047, T-233 |
| T-055 | HU-14 | Pruebas | Probar la funcionalidad de HU-14 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-052, T-053, T-054, T-233 |
| T-063 | HU-15 | Pruebas | Probar la funcionalidad de HU-15 (la API con Postman, la pantalla web y la app) contra cada condición final y aprobar su PR | 1 | To Do | T-059, T-060, T-061, T-062, T-233 |
| T-078 | HU-17 | Pruebas | Probar la funcionalidad de HU-17 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-075, T-076, T-077, T-233 |
| T-165 | HU-37 | Pruebas | Probar la funcionalidad de HU-37 (la app en modo avión y al volver la señal) contra cada condición final y aprobar su PR | 1 | To Do | T-163, T-164, T-233 |
| T-173 | HU-38 | Pruebas | Probar la funcionalidad de HU-38 (la API con Postman y la app en modo avión y al volver la señal) contra cada condición final y aprobar su PR | 1 | To Do | T-169, T-170, T-171, T-172, T-233 |
| T-181 | HU-40 | Pruebas | Probar la funcionalidad de HU-40 (la API con Postman y la app en modo avión y al volver la señal) contra cada condición final y aprobar su PR | 1 | To Do | T-177, T-178, T-179, T-180, T-233 |
| T-207 | HU-55 | Pruebas | Probar la funcionalidad de HU-55 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-203, T-204, T-205, T-206, T-233 |
| T-231 | HU-63 | Pruebas | Probar la funcionalidad de HU-63 (la API con Postman y la app en modo avión y al volver la señal) contra cada condición final y aprobar su PR | 1 | To Do | T-227, T-228, T-229, T-230, T-233 |
| T-237 | HT-03 | DevOps | Poner el repositorio en privado e invitar al docente como lector | 0.5 | To Do | — |
| T-238 | HT-03 | DevOps | Proteger la rama main: PR obligatorio con una aprobación de otro integrante | 0.5 | To Do | — |
| T-256 | — | UX | Validar las pantallas con el cliente (Pool) en una sesión corta y registrar sus observaciones | 1.5 | To Do | — |
| T-261 | — | Review | Preparar la demo de la Sprint Review: una compra que cruza recepción, salida y costo de punta a punta | 2 | To Do | — |

## Tus historias del Sprint 2

**Web (con Henry):** HU-34 Adjuntar comprobante de compra · HU-51 Consultar responsable de herramienta · HU-56 Consultar inventario valorizado · HU-58 Consultar el gasto en recursos de la obra · HU-65 Consultar costo de la mano de obra · HU-06 Mostrar estado de proyectos · HU-28 Marcar insumos críticos · HU-44 Reportar entradas y salidas semanales · HU-60 Listar facturas de compra · HU-59 Separar impuesto del costo · HU-61 Descargar comprobante · HU-66 y HU-67 (solicitud de materiales, ficha CR-04): las pantallas.

**Integraciones:** HU-31 Validar RUC en SUNAT.

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
