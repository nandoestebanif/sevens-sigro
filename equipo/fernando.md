# Fernando Istaña · Scrum Master · Integraciones, web y despliegue

**Reparto del 22/10:** Scrum Master; integraciones (`integraciones/`: S10, SUNAT, RENIEC); web junto con Henry (pantallas de obra, importación, catálogo y reposición); y el despliegue del ambiente de pruebas.

## Lo primero

1. **T-237 · HT-03:** repositorio privado y `main` protegida (hoy es público y tiene datos del cliente, incluidas cuentas bancarias de proveedores en la semilla).
2. **T-233 · HT-01:** ambiente de pruebas (API, base y web). Sin él ninguna HU cumple T8.
3. Como SM: Daily con el tablero, bitácora e incidencias al día, y el recorte de alcance con el PO antes de la Review.

## Tus tareas del Sprint 1 · 98 tareas · 79.5 h pendientes

| ID | HU | Fase | Tarea | h | Estado | Límite | Depende de |
|---|---|---|---|--:|---|---|---|
| T-001 | HU-01 | Diseño UI | Diseñar la interfaz de usuario de HU-01: pantalla 9 · Crear la obra | 1 | Done | 23/10 | — |
| T-002 | HU-01 | Modelado UML | Modelar la funcionalidad de HU-01 con el diagrama UML de estados de la obra (en ejecución → ampliada → cerrada) | 1 | To Do | 24/10 | T-001 |
| T-006 | HU-01 | Front-end web | Programar la pantalla web para crear la obra | 2 | Done | 26/10 | — |
| T-007 | HU-01 | Conexión API | Conectar la pantalla «Crear la obra» con el endpoint real de la API | 2 | To Do | 26/10 | T-004, T-006, T-240 |
| T-010 | HU-03 | Diseño UI | Diseñar la interfaz de usuario de HU-03: pantalla 9 · Crear la obra y 22 · Detalle de la obra | 1 | Done | 23/10 | — |
| T-011 | HU-03 | Modelado UML | Modelar la funcionalidad de HU-03 con el diagrama UML de actividades de la generación de semanas | 1 | To Do | 24/10 | T-010 |
| T-014 | HU-03 | Front-end web | Programar la tira de semanas en la web (cerrada, en curso, por venir) | 2 | Done | 26/10 | — |
| T-015 | HU-03 | Conexión API | Conectar la tira de semanas con la API real | 2 | To Do | 26/10 | T-013, T-014, T-240 |
| T-018 | HU-07 | Diseño UI | Diseñar la interfaz de usuario de HU-07: pantalla 11 · Importar el catálogo del S10 | 1 | Done | 23/10 | — |
| T-019 | HU-07 | Modelado UML | Modelar la funcionalidad de HU-07 con el diagrama UML de secuencia de la importación del S10 por el microservicio | 1 | Done | 24/10 | — |
| T-023 | HU-07 | Integración | Programar el lector del PDF «Relación de insumos» del S10 en el microservicio — integración con S10 (relación de insumos) | 4 | Done | 25/10 | — |
| T-024 | HU-07 | Front-end web | Programar la pantalla web «Importar del S10» con el resumen de creados, actualizados y fallidos | 4 | Done | 26/10 | — |
| T-027 | HU-08 | Diseño UI | Diseñar la interfaz de usuario de HU-08: pantalla 16 · Catálogo e insumo | 1 | Done | 23/10 | — |
| T-028 | HU-08 | Modelado UML | Modelar la funcionalidad de HU-08 con el diagrama UML de secuencia del alta de insumo con verificación de duplicado | 1 | To Do | 24/10 | T-027 |
| T-031 | HU-08 | Front-end web | Programar la pantalla web del catálogo para ver, dar de alta y editar insumos | 3 | Done | 26/10 | — |
| T-032 | HU-08 | Conexión API | Conectar la pantalla del catálogo con la API real | 3 | To Do | 26/10 | T-030, T-031, T-240 |
| T-035 | HU-12 | Diseño UI | Diseñar la interfaz de usuario de HU-12: pantalla 1 (app) y 20 (web) · Login | 1 | Done | 23/10 | — |
| T-041 | HU-12 | Pruebas | Probar la funcionalidad de HU-12 (la API con Postman, la pantalla web y la app) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-037, T-038, T-039, T-040, T-233 |
| T-043 | HU-13 | Diseño UI | Diseñar la interfaz de usuario de HU-13: pantalla 10 · Usuarios, roles y dispositivos | 1 | Done | 23/10 | — |
| T-048 | HU-13 | Pruebas | Probar la funcionalidad de HU-13 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-045, T-046, T-047, T-233 |
| T-050 | HU-14 | Diseño UI | Diseñar la interfaz de usuario de HU-14: pantalla 10 · Usuarios, roles y dispositivos | 1 | Done | 23/10 | — |
| T-055 | HU-14 | Pruebas | Probar la funcionalidad de HU-14 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-052, T-053, T-054, T-233 |
| T-057 | HU-15 | Diseño UI | Diseñar la interfaz de usuario de HU-15: pantalla 10 · Usuarios, roles y dispositivos | 1 | Done | 23/10 | — |
| T-063 | HU-15 | Pruebas | Probar la funcionalidad de HU-15 (la API con Postman, la pantalla web y la app) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-059, T-060, T-061, T-062, T-233 |
| T-065 | HU-16 | Diseño UI | Diseñar la interfaz de usuario de HU-16: pantalla 19 · Importar APU y requerimiento | 1 | Done | 23/10 | — |
| T-066 | HU-16 | Modelado UML | Modelar la funcionalidad de HU-16 con el diagrama UML de actividades de la lectura del APU | 1 | To Do | 24/10 | T-065 |
| T-069 | HU-16 | Integración | Programar el lector del PDF del APU (371 páginas), identificando la partida por código y descripción — integración con S10 (análisis de precios unitarios) | 5 | Doing | 25/10 | T-066 |
| T-070 | HU-16 | Front-end web | Programar la pantalla web para importar el APU | 5 | To Do | 26/10 | T-065 |
| T-073 | HU-17 | Diseño UI | Diseñar la interfaz de usuario de HU-17: pantalla 16 · Catálogo e insumo | 1 | Done | 23/10 | — |
| T-074 | HU-17 | Modelado UML | Modelar la funcionalidad de HU-17 con el diagrama UML de secuencia de la conversión kg ↔ varillas | 1 | To Do | 24/10 | T-073 |
| T-077 | HU-17 | Front-end web | Programar el formulario web del factor de conversión (kg ↔ varillas) | 2 | To Do | 26/10 | T-073 |
| T-080 | HU-19 | Diseño UI | Diseñar la interfaz de usuario de HU-19: pantalla 19 · Importar APU y requerimiento | 1 | Done | 23/10 | — |
| T-081 | HU-19 | Modelado UML | Modelar la funcionalidad de HU-19 con el diagrama UML de actividades de la importación del requerimiento total | 1 | To Do | 24/10 | T-080 |
| T-084 | HU-19 | Integración | Adaptar el lector de la Relación de insumos para obtener el requerimiento total — integración con S10 (relación de insumos) | 3 | To Do | 25/10 | T-081 |
| T-085 | HU-19 | Front-end web | Programar la pantalla web del requerimiento total | 3 | To Do | 26/10 | T-080 |
| T-088 | HU-23 | Diseño UI | Diseñar la interfaz de usuario de HU-23: pantalla 13 · Gasto en recursos | 1 | Done | 23/10 | — |
| T-089 | HU-23 | Modelado UML | Modelar la funcionalidad de HU-23 con el diagrama UML de secuencia del cálculo del avance | 1 | To Do | 24/10 | T-088 |
| T-092 | HU-23 | Front-end web | Programar la pantalla web de avance de compra y consumo | 3 | Done | 26/10 | — |
| T-093 | HU-23 | Conexión API | Conectar la pantalla de avance con la API real | 3 | To Do | 26/10 | T-091, T-092, T-240 |
| T-096 | HU-24 | Diseño UI | Diseñar la interfaz de usuario de HU-24: pantalla 8 · Reposición | 1 | Done | 23/10 | — |
| T-097 | HU-24 | Modelado UML | Modelar la funcionalidad de HU-24 con el diagrama UML de secuencia de la consulta de lo que falta comprar | 1 | To Do | 24/10 | T-096 |
| T-100 | HU-24 | Front-end web | Programar la pantalla web «Saldo por comprar» con filtros y exportación CSV | 2 | Done | 26/10 | — |
| T-101 | HU-24 | Conexión API | Conectar la pantalla de saldo por comprar con la API real | 2 | To Do | 26/10 | T-099, T-100, T-240 |
| T-104 | HU-25 | Diseño UI | Diseñar la interfaz de usuario de HU-25: pantalla 17 · Parámetros de reposición | 1 | Done | 23/10 | — |
| T-105 | HU-25 | Modelado UML | Modelar la funcionalidad de HU-25 con el diagrama UML de clases de los parámetros de reposición | 1 | To Do | 24/10 | T-104 |
| T-108 | HU-25 | Front-end web | Programar el formulario web de parámetros de reposición | 4 | To Do | 26/10 | T-104 |
| T-111 | HU-26 | Diseño UI | Diseñar la interfaz de usuario de HU-26: pantalla 8 · Reposición y 5 · Consultar stock | 1 | Done | 23/10 | — |
| T-112 | HU-26 | Modelado UML | Modelar la funcionalidad de HU-26 con el diagrama UML de secuencia de la alerta de reposición con Notificaciones | 1 | To Do | 24/10 | T-111 |
| T-116 | HU-26 | Front-end web | Programar las alertas de reposición en la web | 2 | Done | 26/10 | — |
| T-117 | HU-26 | Conexión API | Conectar las alertas con la API real | 2 | To Do | 26/10 | T-114, T-116, T-240 |
| T-120 | HU-27 | Diseño UI | Diseñar la interfaz de usuario de HU-27: pantalla 8 · Reposición | 1 | Done | 23/10 | — |
| T-121 | HU-27 | Modelado UML | Modelar la funcionalidad de HU-27 con el diagrama UML de actividades del cálculo de la sugerencia | 1 | To Do | 24/10 | T-120 |
| T-124 | HU-27 | Front-end web | Programar la sugerencia editable dentro de la alerta | 3 | Done | 26/10 | — |
| T-125 | HU-27 | Conexión API | Conectar la sugerencia con la API real | 3 | To Do | 26/10 | T-123, T-124, T-240 |
| T-128 | HU-29 | Diseño UI | Diseñar la interfaz de usuario de HU-29: pantalla 18 · Proveedores | 1 | Done | 23/10 | — |
| T-132 | HU-29 | Front-end web | Programar la pantalla web de proveedores con los 626 reales | 2 | Done | 26/10 | — |
| T-134 | HU-29 | Pruebas | Probar la funcionalidad de HU-29 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-130, T-131, T-132, T-133, T-233 |
| T-136 | HU-30 | Diseño UI | Diseñar la interfaz de usuario de HU-30: pantalla 12 · Registrar la compra | 1 | Done | 23/10 | — |
| T-140 | HU-30 | Front-end web | Programar el alta rápida de proveedor desde la compra | 2 | Done | 26/10 | — |
| T-142 | HU-30 | Pruebas | Probar la funcionalidad de HU-30 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-138, T-139, T-140, T-141, T-233 |
| T-144 | HU-32 | Diseño UI | Diseñar la interfaz de usuario de HU-32: pantalla 12 · Registrar la compra | 1 | Done | 23/10 | — |
| T-148 | HU-32 | Front-end web | Programar la pantalla web de compras | 5 | Done | 26/10 | — |
| T-150 | HU-32 | Pruebas | Probar la funcionalidad de HU-32 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-146, T-147, T-148, T-149, T-233 |
| T-152 | HU-35 | Diseño UI | Diseñar la interfaz de usuario de HU-35: pantalla 14 · Recepción de compra | 1 | Done | 23/10 | — |
| T-153 | HU-35 | Modelado UML | Modelar la funcionalidad de HU-35 con el diagrama UML de secuencia de la recepción y el costo promedio ponderado | 1 | Done | 24/10 | — |
| T-156 | HU-35 | Front-end web | Programar el registro de recepción en la web (pedido, recibido, pendiente) | 4 | Done | 26/10 | — |
| T-159 | HU-35 | Pruebas | Probar la funcionalidad de HU-35 (la API con Postman, la pantalla web y la app en modo avión y al volver la señal) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-154, T-155, T-156, T-157, T-158, T-233 |
| T-161 | HU-37 | Diseño UI | Diseñar la interfaz de usuario de HU-37: pantalla 3 · Salida y 4 · Ingreso (banda sin señal) | 1 | Done | 23/10 | — |
| T-162 | HU-37 | Modelado UML | Modelar la funcionalidad de HU-37 con el diagrama UML de secuencia de la salida sin señal y su sincronización | 1 | Done | 24/10 | — |
| T-167 | HU-38 | Diseño UI | Diseñar la interfaz de usuario de HU-38: pantalla 8b · Envíos pendientes y 2 · Mis obras | 1 | Done | 23/10 | — |
| T-168 | HU-38 | Modelado UML | Modelar la funcionalidad de HU-38 con el diagrama UML de secuencia de la salida sin señal y su sincronización | 1 | Done | 24/10 | — |
| T-175 | HU-40 | Diseño UI | Diseñar la interfaz de usuario de HU-40: pantalla 3 · Registrar salida | 1 | Done | 23/10 | — |
| T-176 | HU-40 | Modelado UML | Modelar la funcionalidad de HU-40 con el diagrama UML de secuencia de la salida sin señal y su sincronización | 1 | Done | 24/10 | — |
| T-183 | HU-43 | Diseño UI | Diseñar la interfaz de usuario de HU-43: pantalla 5 · Consultar stock y 15 · Kárdex | 1 | Done | 23/10 | — |
| T-188 | HU-43 | Front-end web | Programar la pantalla web de stock y kárdex | 2 | Done | 26/10 | — |
| T-191 | HU-43 | Pruebas | Probar la funcionalidad de HU-43 (la API con Postman, la pantalla web y la app) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-185, T-186, T-187, T-188, T-189, T-190, T-233 |
| T-193 | HU-45 | Diseño UI | Diseñar la interfaz de usuario de HU-45: pantalla 15 · Kárdex y 21 · Movimientos del día | 1 | Done | 23/10 | — |
| T-197 | HU-45 | Front-end web | Programar la corrección con reverso desde el kárdex web | 3 | Done | 26/10 | — |
| T-199 | HU-45 | Pruebas | Probar la funcionalidad de HU-45 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-195, T-196, T-197, T-198, T-233 |
| T-201 | HU-55 | Diseño UI | Diseñar la interfaz de usuario de HU-55: pantalla 3 · Registrar salida y 13 · Tablero | 1 | Done | 23/10 | — |
| T-202 | HU-55 | Modelado UML | Modelar la funcionalidad de HU-55 con el diagrama UML de secuencia de la recepción y el costo promedio ponderado | 1 | Done | 24/10 | — |
| T-207 | HU-55 | Pruebas | Probar la funcionalidad de HU-55 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-203, T-204, T-205, T-206, T-233 |
| T-209 | HU-57 | Diseño UI | Diseñar la interfaz de usuario de HU-57: pantalla 13 · Gasto en recursos | 1 | Done | 23/10 | — |
| T-213 | HU-57 | Front-end web | Programar la pantalla web «Gasto en recursos» con la desviación | 5 | Done | 26/10 | — |
| T-215 | HU-57 | Pruebas | Probar la funcionalidad de HU-57 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-211, T-212, T-213, T-214, T-233 |
| T-217 | HU-62 | Diseño UI | Diseñar la interfaz de usuario de HU-62: pantalla 6 · Registrar trabajador | 1 | Done | 23/10 | — |
| T-221 | HU-62 | Integración | Programar la consulta de DNI en RENIEC desde el microservicio — integración con RENIEC (DNI) vía Factiliza | 2 | Done | 25/10 | — |
| T-225 | HU-63 | Diseño UI | Diseñar la interfaz de usuario de HU-63: pantalla 7 · Asistencia de la jornada | 1 | Done | 23/10 | — |
| T-233 | HT-01 | DevOps | Desplegar el ambiente de pruebas (API, base de datos y web) que exige el T8 de la DoD | 6 | To Do | 24/10 | — |
| T-237 | HT-03 | DevOps | Poner el repositorio en privado e invitar al docente como lector | 0.5 | To Do | 24/10 | — |
| T-238 | HT-03 | DevOps | Proteger la rama main: PR obligatorio con una aprobación de otro integrante | 0.5 | To Do | 24/10 | — |
| T-250 | HT-08 | Integración | Exigir el token de servicio en Integraciones | 1 | Done | 25/10 | — |
| T-253 | — | Scrum | Armar el tablero en GitHub Projects con las plantillas de DoR y DoD | 2 | Doing | 23/10 | — |
| T-254 | — | Scrum | Facilitar el Daily Scrum de 15 minutos cada día, registrarlo y actualizar el burndown | 2 | Doing | 27/10 | — |
| T-255 | — | UX | Simplificar los textos de cada pantalla web: título y una línea, con ayuda «?» bajo demanda | 3 | To Do | 26/10 | — |
| T-256 | — | UX | Validar las pantallas con el cliente (Pool) en una sesión corta y registrar sus observaciones | 1.5 | To Do | 26/10 | — |
| T-258 | — | Modelado UML | Modelar los 3 diagramas de casos de uso y los 3 de secuencia (importación S10, salida sin señal, recepción y costo promedio) | 2 | Done | 24/10 | — |
| T-261 | — | Review | Preparar la demo de la Sprint Review: una compra que cruza recepción, salida y costo de punta a punta | 2 | To Do | 27/10 | — |

Tus HU de web ya tienen la pantalla hecha con datos simulados: lo que falta es **conectarlas** a la API real cuando Henry termine el ClienteHttp (HT-04).

## Cómo trabajas (igual para los cuatro)

- Lee primero `CLAUDE.md` (reglas de dominio) y `docs/scrum/tablero-sprint-1.md` (reglas del tablero).
- Tus tareas están en `docs/scrum/SIGRO - Sprint 1 · Registro del Sprint.xlsx`, hoja «Tablero». Al moverlas:
  **To Do → Doing** solo si la HU cumple la DoR (L1–L12); **Doing → Done** con su criterio cumplido y la evidencia (PR o enlace).
  Máximo **2 tareas en Doing**. Anota la fecha.
- Cada día, en la hoja «Bitácora de trabajo»: una fila por actividad — fecha · hora · qué hiciste · T-xxx.
- Una rama por tarea o HU (`hu-40-salida-app`), nada directo a `main`, PR pequeño que diga qué HU y qué condiciones finales cubre.
  Lo aprueba alguien distinto de ti. **Sube tu trabajo aunque esté incompleto**: lo que no está en el repo no existe para la Review.
- **Lunes 27/10: todo en Done. Martes 28/10: Review.**
