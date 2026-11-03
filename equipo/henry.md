# Henry Ticona · Developer · Web y pruebas

**Sprint 2: 29/10 al 11/11/2026.** El Sprint 1 cerró el 28/10.

**Tu área:** **Web** (`web/`) junto con Fernando, y **pruebas**: Postman y la interfaz contra cada condición final, y la aprobación de los PR de la web. Cuando alguien termina una HU, te avisa para que la pruebes (T8).

## Lo primero

1. **Revisa los PR de la web** (abajo): son los que hacen que la Review se vea entera en estilo vidrio.
2. **Pruebas:** tus tareas «Pruebas» del tablero, en el ambiente de pruebas (`https://sigro-web.onrender.com`).
3. **Web del Sprint 2:** las HU web, repartidas con Fernando en el corte de las 08:00.

## Los PR que te toca revisar

| PR | Qué es | Qué miras |
|---|---|---|
| [#15](https://github.com/xteban-sp/sevens-sigro/pull/15) | Web: resto de páginas en estilo vidrio | build sin errores, modo claro/oscuro y escala 125 % |
| [#23](https://github.com/xteban-sp/sevens-sigro/pull/23) | Web: pantalla de arranque, login con reintentos y entrada a la obra (apilado sobre #15) | el arranque en frío y el login (HU-12) |
| [#16](https://github.com/xteban-sp/sevens-sigro/pull/16) | HT-06: colección Postman, e2e con Playwright y plan de pruebas | que la colección y el plan te sirvan para tus pruebas |
| [#18](https://github.com/xteban-sp/sevens-sigro/pull/18) | Rendimiento web y API | la parte de `web/` (la de `api/` la revisa Adiel) |

## Tus tareas pendientes según el tablero · 24 tareas · 65 h · 2 en Doing

Son las que quedaron sin terminar en el tablero de cierre del Sprint 1 (`SIGRO - Sprint 1 · Registro del Sprint (cierre).xlsx`, hoja «Tablero») y vuelven al Sprint 2 con su avance.

> T-270 (vidrio: base, Entrar, armazón e Importar) ya tiene su PR fusionado (#13): si cumple su criterio, pásala a Done en el próximo corte.

| ID | HU | Fase | Tarea | h | Estado | Depende de |
|---|---|---|---|--:|---|---|
| T-039 | HU-12 | Front-end web | Programar la pantalla web de login y el manejo de la sesión | 3 | Doing | T-035 |
| T-270 | — | Front-end web | Aplicar el estilo vidrio del lienzo «SIGRO Oficina · Web» a la web, página por página: base, Entrar, menú lateral e Importar del S10 | — | Doing | T-269 |
| T-033 | HU-08 | Pruebas | Probar la funcionalidad de HU-08 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-029, T-030, T-031, T-032, T-233 |
| T-047 | HU-13 | Front-end web | Programar la pantalla web para crear usuarios, asignar su rol y darlos de baja | 4 | To Do | T-043 |
| T-054 | HU-14 | Front-end web | Programar la pantalla web para asignar usuarios a obras | 2 | To Do | T-050 |
| T-062 | HU-15 | Front-end web | Programar la pantalla web para autorizar o cambiar el celular de la obra | 3 | To Do | T-057 |
| T-070 | HU-16 | Front-end web | Programar la pantalla web para importar el APU | 5 | To Do | T-065 |
| T-077 | HU-17 | Front-end web | Programar el formulario web del factor de conversión (kg ↔ varillas) | 2 | To Do | T-073 |
| T-086 | HU-19 | Pruebas | Probar la funcionalidad de HU-19 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-082, T-083, T-084, T-085, T-233 |
| T-093 | HU-23 | Conexión API | Conectar la pantalla de avance con la API real | 3 | To Do | T-091, T-092, T-240 |
| T-102 | HU-24 | Pruebas | Probar la funcionalidad de HU-24 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-098, T-099, T-100, T-101, T-233 |
| T-117 | HU-26 | Conexión API | Conectar las alertas con la API real | 2 | To Do | T-114, T-116, T-240 |
| T-125 | HU-27 | Conexión API | Conectar la sugerencia con la API real | 3 | To Do | T-123, T-124, T-240 |
| T-134 | HU-29 | Pruebas | Probar la funcionalidad de HU-29 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-130, T-131, T-132, T-133, T-233 |
| T-142 | HU-30 | Pruebas | Probar la funcionalidad de HU-30 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | T-138, T-139, T-140, T-141, T-233 |
| T-149 | HU-32 | Conexión API | Conectar la pantalla de compras con la API real | 5 | To Do | T-147, T-148, T-240 |
| T-158 | HU-35 | Conexión API | Conectar la recepción con la API real | 4 | To Do | T-155, T-156, T-240 |
| T-190 | HU-43 | Conexión API | Conectar la pantalla de stock y kárdex con la API real | 2 | To Do | T-186, T-188, T-240 |
| T-198 | HU-45 | Conexión API | Conectar el reverso con la API real | 3 | To Do | T-196, T-197, T-240 |
| T-206 | HU-55 | Front-end web | Mostrar la salida valorizada en el kárdex y en el reporte web | 7 | To Do | T-201 |
| T-214 | HU-57 | Conexión API | Conectar la pantalla de desviación con la API real | 5 | To Do | T-212, T-213, T-240 |
| T-223 | HU-62 | Pruebas | Probar la funcionalidad de HU-62 (la API con Postman y la app) contra cada condición final y aprobar su PR | 1 | To Do | T-219, T-220, T-221, T-222, T-233 |
| T-245 | HT-06 | Pruebas | Armar y compartir la colección de Postman con entorno de pruebas, login y un ejemplo correcto y uno de error por endpoint | 3 | To Do | — |
| T-255 | — | UX | Simplificar los textos de cada pantalla web: título y una línea, con ayuda «?» bajo demanda | 3 | To Do | — |

## Tus historias del Sprint 2

**Web (con Fernando):** HU-34 Adjuntar comprobante de compra · HU-51 Consultar responsable de herramienta · HU-56 Consultar inventario valorizado · HU-58 Consultar el gasto en recursos de la obra · HU-65 Consultar costo de la mano de obra · HU-06 Mostrar estado de proyectos · HU-28 Marcar insumos críticos · HU-44 Reportar entradas y salidas semanales · HU-60 Listar facturas de compra · HU-59 Separar impuesto del costo · HU-61 Descargar comprobante · HU-66 y HU-67 (solicitud de materiales, ficha CR-04): las pantallas.

**Pruebas:** toda HU que termine otro integrante pasa por ti antes de que la acepte el PO.

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
