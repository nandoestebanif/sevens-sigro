# Nicolás Pachamora · Developer · App

**Sprint 2: 29/10 al 11/11/2026.** El Sprint 1 cerró el 28/10.

**Tu área:** **App del almacenero** (`android/`: Kotlin Multiplatform, Android + iOS). Todo lo que registra movimientos se prueba **en modo avión y recuperando la señal después** (T7).

## Lo primero

1. **Revisa los PR de la app** (abajo): eres el responsable de la app y quien la aprueba. Empieza por el #29: es una
   línea y hace que la app entre al ambiente nuevo.
2. **Pruebas de aceptación:** tus 4 HU del Excel de pruebas (abajo), en el ambiente de pruebas.
3. **HT-05 (T-242)** sigue en Doing. Si los PR #20 y #21 cumplen su criterio, pásala a Done con esa evidencia.
4. **App del Sprint 2:** tus HU de abajo, empezando por las que ya tienen el servicio de almacén (#19, fusionado):
   conteo físico HU-46 y estado de sincronización HU-39.

## Ambiente de pruebas · vigente desde el 04/11

| Para | Dirección |
|---|---|
| **App** (campo «Servidor» de la pantalla de entrada) | **https://sigro-api.onrender.com** |
| **Web** | **https://sigro-web.onrender.com** |
| **Postman** | `https://sigro-api.onrender.com/api/v1`, **como máximo 1 petición cada 5 s**; si no, Cloudflare bloquea el servidor |

`sigro-gateway.onrender.com` ya no responde. Un celular o emulador que lo tenía guardado **no** cambia solo, porque el
servidor guardado manda sobre el que trae la app por defecto: cámbialo a mano en «Servidor». La primera vez del día el
servicio tarda en despertar, así que espera y no reintentes el login varias veces.
**Fernando te pasa tu usuario por privado.** No lo escribas en el repositorio, en el código ni en el Excel. Más detalle
en [`LEEME.md`](LEEME.md).

## Tus pruebas de aceptación

Archivo: [`docs/pruebas/SIGRO - Pruebas de aceptación (Sprint 2).xlsx`](../docs/pruebas/SIGRO%20-%20Pruebas%20de%20aceptaci%C3%B3n%20(Sprint%202).xlsx), hoja **«Nicolás»**.
Son las mismas tareas «Pruebas» de tu tablero (abajo).

| HU | Historia | Tarjeta «Pruebas» | Tarjeta «Aceptación» (Adiel) |
|---|---|---|---|
| HU-01 | Creación del proyecto | T-008 | T-009 |
| HU-03 | División en semanas | T-016 | T-017 |
| HU-07 | Importación del catálogo | T-025 | T-026 |
| HU-25 | Parámetros de reposición | T-109 | T-110 |

Prueba desde la web, que es donde están esas pantallas, y usa tu obra de prueba para crear datos. HU-07 se prueba con
el PDF real del cliente. Al terminar tu hoja, avísale a Adiel. Si una pantalla te responde que no tienes permiso,
anótalo en «Observación» y avísale a Fernando.

## Los PR que te toca revisar

| PR | Qué es | Qué miras |
|---|---|---|
| [#29](https://github.com/xteban-sp/sevens-sigro/pull/29) | app: servidor por defecto `https://sigro-api.onrender.com` | que compile y que la app entre al ambiente nuevo, también en modo avión y al volver la señal |
| [#20](https://github.com/xteban-sp/sevens-sigro/pull/20) | App: pantallas del Sprint 2 en estilo vidrio | que compile y se vea bien en el celular |
| [#21](https://github.com/xteban-sp/sevens-sigro/pull/21) | App: almacén conectado al servicio real (apilado sobre #20) | modo avión y recuperar la señal; que un reenvío no duplique (regla 3) |
| [#22](https://github.com/xteban-sp/sevens-sigro/pull/22) | app: rendimiento medido (R8, Baseline Profile, APK de 22 a 7,9 MB) | que el APK firmado se instale y abra |

#20, #21 y #22 se fusionan en ese orden. El #29 no depende de ellos y puede entrar antes.

## Tus tareas pendientes según el tablero · 26 tareas · 69 h · 2 en Doing

Son las que quedaron sin terminar en el tablero de cierre del Sprint 1 (`SIGRO - Sprint 1 · Registro del Sprint (cierre).xlsx`, hoja «Tablero») y vuelven al Sprint 2 con su avance.

| ID | HU | Fase | Tarea | h | Estado | Depende de |
|---|---|---|---|--:|---|---|
| T-242 | HT-05 | Android | Crear el proyecto base de la app: Compose, Room, WorkManager y cliente de la API | 6 | Doing | — |
| T-260 | — | Modelado UML | Dibujar la arquitectura en las cuatro vistas del C4 a partir del diagrama de microservicios | 4 | Doing | — |
| T-008 | HU-01 | Pruebas | Probar la funcionalidad de HU-01 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-003, T-004, T-005, T-006, T-007, T-233 |
| T-016 | HU-03 | Pruebas | Probar la funcionalidad de HU-03 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-012, T-013, T-014, T-015, T-233 |
| T-025 | HU-07 | Pruebas | Probar la funcionalidad de HU-07 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-020, T-021, T-022, T-023, T-024, T-233 |
| T-040 | HU-12 | Front-end app | Programar el login en la app del almacenero | 3 | To Do | T-035, T-242 |
| T-061 | HU-15 | Front-end app | Programar en la app la generación del código del dispositivo en la primera instalación | 3 | To Do | T-057, T-242 |
| T-071 | HU-16 | Pruebas | Probar la funcionalidad de HU-16 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-067, T-068, T-069, T-070, T-233 |
| T-094 | HU-23 | Pruebas | Probar la funcionalidad de HU-23 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-090, T-091, T-092, T-093, T-233 |
| T-109 | HU-25 | Pruebas | Probar la funcionalidad de HU-25 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-106, T-107, T-108, T-233 |
| T-118 | HU-26 | Pruebas | Probar la funcionalidad de HU-26 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-113, T-114, T-115, T-116, T-117, T-233 |
| T-126 | HU-27 | Pruebas | Probar la funcionalidad de HU-27 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-122, T-123, T-124, T-125, T-233 |
| T-150 | HU-32 | Pruebas | Probar la funcionalidad de HU-32 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-146, T-147, T-148, T-149, T-233 |
| T-157 | HU-35 | Front-end app | Programar la recepción de compras en la app del almacenero | 4 | To Do | T-152, T-242 |
| T-159 | HU-35 | Pruebas | Probar la funcionalidad de HU-35 (la API con Postman, la pantalla web y la app en modo avión y al volver la señal) contra cada condición final y aprobar su PR | 1 | To Do | T-154, T-155, T-156, T-157, T-158, T-233 |
| T-163 | HU-37 | Base de datos | Implementar las tablas asociadas a HU-37: cola local en Room (movimiento_pendiente) en el celular | 1 | To Do | T-162 |
| T-164 | HU-37 | Front-end app | Programar la cola local (Room) con uuid_cliente y hora del celular, y mostrar «pendiente de enviar» en cada movimiento | 14 | To Do | T-161, T-242 |
| T-172 | HU-38 | Front-end app | Programar el envío automático de pendientes al volver la señal (WorkManager) | 4 | To Do | T-167, T-242 |
| T-180 | HU-40 | Front-end app | Programar la pantalla de salida en la app (insumo, cantidad, motivo, quién retira) | 7 | To Do | T-175, T-242 |
| T-189 | HU-43 | Front-end app | Programar la consulta de stock en la app | 2 | To Do | T-183, T-242 |
| T-191 | HU-43 | Pruebas | Probar la funcionalidad de HU-43 (la API con Postman, la pantalla web y la app) contra cada condición final y aprobar su PR | 1 | To Do | T-185, T-186, T-187, T-188, T-189, T-190, T-233 |
| T-199 | HU-45 | Pruebas | Probar la funcionalidad de HU-45 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-195, T-196, T-197, T-198, T-233 |
| T-215 | HU-57 | Pruebas | Probar la funcionalidad de HU-57 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-211, T-212, T-213, T-214, T-233 |
| T-222 | HU-62 | Front-end app | Programar en la app el registro de trabajadores con consulta de DNI | 2 | To Do | T-217, T-242 |
| T-230 | HU-63 | Front-end app | Programar la pantalla de asistencia del día en la app (horas normales y extra) | 4 | To Do | T-225, T-242 |
| T-257 | — | UX | Revisar la app con guantes y al sol: botones grandes, una acción por pantalla, contraste AA | 2 | To Do | — |

## Tus historias del Sprint 2

**App:** HU-39 Mostrar estado de sincronización · HU-46 Registrar conteo físico · HU-48 Registrar ingreso de herramientas · HU-49 Entregar herramienta a trabajador · HU-50 Registrar devolución de herramienta · HU-41 Registrar ingreso sin compra · HU-11 Adjuntar foto del insumo · HU-52 Registrar mantenimiento de herramienta · HU-53 Dar de baja herramienta.

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
