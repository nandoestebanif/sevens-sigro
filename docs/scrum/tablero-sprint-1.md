# Sprint 1 · Tablero Scrum (tareas)

Sprint 1: **15 al 28 de octubre de 2026** · 29 HU · 153 puntos · **261 tareas**.
Fuente única: `docs/scrum/sigro-sprint1-tareas.csv` (se importa a GitHub Projects o Jira) y el Excel
`docs/scrum/SIGRO - Sprint 1 · Tablero Scrum (tareas).xlsx` (hoja «Validar HU y tareas» para revisar,
hoja «Resumen» que se actualiza sola). Registro de incidencias y bitácora de trabajo: `docs/scrum/SIGRO - Sprint 1 · Información del Sprint.xlsx`.

## Reparto (acordado el 22/10)

| Área | Responsable |
|---|---|
| Web (React) | **Fernando** y **Henry** |
| App Android | **Nicolás** |
| Backend (API FastAPI + PostgreSQL) | **Adiel** (además Product Owner) |
| Integraciones (S10, SUNAT, RENIEC) y despliegue | **Fernando** (además Scrum Master) |

## Cómo se mueve una tarjeta

| Columna | Regla |
|---|---|
| **To Do → Doing** | Solo si la HU cumple la **DoR (L1–L12)**: criterios escritos, estimada, sin dependencias abiertas, recursos identificados, requisitos técnicos definidos y accesos de red disponibles. |
| **Doing → Done** | Solo con la **DoD (T1–T10)** y las **condiciones finales** de la tarjeta 3 verificadas desde la interfaz. La HU pasa a Done cuando su tarea «Aceptar … como Product Owner» pasa a Done. |
| Límite | Máximo **2 tareas en Doing** por persona. Se termina antes de empezar otra. |
| Daily | Todos los días, 15 min, frente al tablero. Se mueve la tarjeta en la reunión. |
| Cierre | **Lunes 27/10 (penúltimo día): todo en Done.** Martes 28/10: Review y Retrospectiva. Lo que no llegue vuelve al Product Backlog. |

## Modelo de tareas (pizarra del docente, 22/10)

Cada HU se parte en las tareas del modelo del docente, en este orden (solo las que aplican a la HU):

1. **Diseñar la interfaz de usuario** (mockup de la pantalla; las 23 ya están en el lienzo).
2. **Modelar la funcionalidad con un diagrama UML** (secuencia, actividades, estados o clases).
3. **Implementar las tablas asociadas** a la HU (migración reversible).
4. **Programar el back-end**: entidad, repositorio, servicio y controlador, con sus atributos y métodos.
5. **Implementar triggers y funciones** de base de datos, donde la regla vive en el motor.
6. **Integrar con la API externa** (S10, SUNAT, RENIEC, Notificaciones), cuando aplica.
7. **Programar el front-end** (web en React o app en Kotlin).
8. **Conectar con la API** real.
9. **Probar la funcionalidad** (endpoints con Postman e interfaz contra las condiciones finales) y aprobar el PR, siempre alguien distinto del autor.
10. **Aceptar** como Product Owner → la HU pasa a Done.

## Historias técnicas (habilitadoras) · 22/10

No entregan una función al usuario: entregan lo que el equipo necesita para que las HU se construyan, se prueben y se acepten. Se estiman con Planning Poker en el próximo Daily y pasan por la misma DoR y DoD.

| Código | Historia técnica | Qué destraba | Responsable |
|---|---|---|---|
| HT-01 | Desplegar el ambiente de pruebas | T8 de la DoD: sin esto ninguna HU llega a Done | Fernando |
| HT-02 | Configurar la integración continua | T1 y T5 de la DoD | Adiel |
| HT-03 | Proteger el repositorio y main | T1 de la DoD y la confidencialidad de los datos de SEVENS | Fernando |
| HT-04 | Conectar la web con la API real | Todas las tareas «Conectar con la API» de la web | Henry |
| HT-05 | Crear la base de la app Android | Todas las HU de la app (HU-12, 15, 35, 37, 38, 40, 43, 62 y 63) | Nicolás |
| HT-06 | Publicar el contrato de la API y Postman | La prueba con Postman de cada HU (modelo del docente) | Adiel |
| HT-07 | Cargar los datos de prueba reales | L8 de la DoR y las pruebas de importación | Adiel |
| HT-08 | Autenticar a los servicios entre sí | La seguridad de las integraciones (incidencia del 22/10) | Fernando |

## Lo que se hace primero (bloquea a las demás)

| ID | Tarea | Responsable | Límite |
|---|---|---|---|
| T-233 | Desplegar el ambiente de pruebas (API, base y web) — sin esto ninguna HU cumple T8 | Fernando | 24/10 |
| T-240 | Programar el ClienteHttp: la web habla con la API real | Henry | 26/10 (cuanto antes) |
| T-242 | Crear el proyecto base de la app Android | Nicolás | 26/10 (cuanto antes) |

## Carga al 22/10

Horas pendientes: Fernando ~79.5 · Henry ~73 · Nicolás ~61 · Adiel ~117. Es más de lo que cabe hasta el 27:
el Product Owner y el equipo deben decidir qué HU se quedan en el sprint (recorte de alcance manteniendo el
objetivo) y medir la velocidad real en la Review.

## Tareas por persona

### Fernando · 98 tareas · 79.5 h pendientes

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

### Henry · 38 tareas · 73 h pendientes

| ID | HU | Fase | Tarea | h | Estado | Límite | Depende de |
|---|---|---|---|--:|---|---|---|
| T-008 | HU-01 | Pruebas | Probar la funcionalidad de HU-01 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-003, T-004, T-005, T-006, T-007, T-233 |
| T-016 | HU-03 | Pruebas | Probar la funcionalidad de HU-03 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-012, T-013, T-014, T-015, T-233 |
| T-025 | HU-07 | Pruebas | Probar la funcionalidad de HU-07 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-020, T-021, T-022, T-023, T-024, T-233 |
| T-033 | HU-08 | Pruebas | Probar la funcionalidad de HU-08 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-029, T-030, T-031, T-032, T-233 |
| T-039 | HU-12 | Front-end web | Programar la pantalla web de login y el manejo de la sesión | 3 | To Do | 26/10 | T-035 |
| T-044 | HU-13 | Modelado UML | Modelar la funcionalidad de HU-13 con el diagrama UML de secuencia de la verificación del permiso en el servidor | 1 | To Do | 24/10 | T-043 |
| T-047 | HU-13 | Front-end web | Programar la pantalla web para crear usuarios, asignar su rol y darlos de baja | 4 | To Do | 26/10 | T-043 |
| T-051 | HU-14 | Modelado UML | Modelar la funcionalidad de HU-14 con el diagrama UML de secuencia de la asignación de usuario a obra | 1 | To Do | 24/10 | T-050 |
| T-054 | HU-14 | Front-end web | Programar la pantalla web para asignar usuarios a obras | 2 | To Do | 26/10 | T-050 |
| T-062 | HU-15 | Front-end web | Programar la pantalla web para autorizar o cambiar el celular de la obra | 3 | To Do | 26/10 | T-057 |
| T-071 | HU-16 | Pruebas | Probar la funcionalidad de HU-16 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-067, T-068, T-069, T-070, T-233 |
| T-078 | HU-17 | Pruebas | Probar la funcionalidad de HU-17 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-075, T-076, T-077, T-233 |
| T-086 | HU-19 | Pruebas | Probar la funcionalidad de HU-19 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-082, T-083, T-084, T-085, T-233 |
| T-094 | HU-23 | Pruebas | Probar la funcionalidad de HU-23 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-090, T-091, T-092, T-093, T-233 |
| T-102 | HU-24 | Pruebas | Probar la funcionalidad de HU-24 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-098, T-099, T-100, T-101, T-233 |
| T-109 | HU-25 | Pruebas | Probar la funcionalidad de HU-25 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-106, T-107, T-108, T-233 |
| T-118 | HU-26 | Pruebas | Probar la funcionalidad de HU-26 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-113, T-114, T-115, T-116, T-117, T-233 |
| T-126 | HU-27 | Pruebas | Probar la funcionalidad de HU-27 (la API con Postman y la pantalla web) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-122, T-123, T-124, T-125, T-233 |
| T-129 | HU-29 | Modelado UML | Modelar la funcionalidad de HU-29 con el diagrama UML de secuencia de la búsqueda de proveedores | 1 | To Do | 24/10 | T-128 |
| T-133 | HU-29 | Conexión API | Conectar la pantalla de proveedores con la API real | 2 | To Do | 26/10 | T-131, T-132, T-240 |
| T-137 | HU-30 | Modelado UML | Modelar la funcionalidad de HU-30 con el diagrama UML de secuencia del alta rápida de proveedor | 1 | To Do | 24/10 | T-136 |
| T-141 | HU-30 | Conexión API | Conectar el alta de proveedor con la API real | 2 | To Do | 26/10 | T-139, T-140, T-240 |
| T-145 | HU-32 | Modelado UML | Modelar la funcionalidad de HU-32 con el diagrama UML de secuencia del registro de la compra | 1 | To Do | 24/10 | T-144 |
| T-149 | HU-32 | Conexión API | Conectar la pantalla de compras con la API real | 5 | To Do | 26/10 | T-147, T-148, T-240 |
| T-158 | HU-35 | Conexión API | Conectar la recepción con la API real | 4 | To Do | 26/10 | T-155, T-156, T-240 |
| T-165 | HU-37 | Pruebas | Probar la funcionalidad de HU-37 (la app en modo avión y al volver la señal) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-163, T-164, T-233 |
| T-173 | HU-38 | Pruebas | Probar la funcionalidad de HU-38 (la API con Postman y la app en modo avión y al volver la señal) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-169, T-170, T-171, T-172, T-233 |
| T-181 | HU-40 | Pruebas | Probar la funcionalidad de HU-40 (la API con Postman y la app en modo avión y al volver la señal) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-177, T-178, T-179, T-180, T-233 |
| T-190 | HU-43 | Conexión API | Conectar la pantalla de stock y kárdex con la API real | 2 | To Do | 26/10 | T-186, T-188, T-240 |
| T-194 | HU-45 | Modelado UML | Modelar la funcionalidad de HU-45 con el diagrama UML de estados del movimiento (vigente → revertido) | 1 | To Do | 24/10 | T-193 |
| T-198 | HU-45 | Conexión API | Conectar el reverso con la API real | 3 | To Do | 26/10 | T-196, T-197, T-240 |
| T-206 | HU-55 | Front-end web | Mostrar la salida valorizada en el kárdex y en el reporte web | 7 | To Do | 26/10 | T-201 |
| T-210 | HU-57 | Modelado UML | Modelar la funcionalidad de HU-57 con el diagrama UML de actividades de la comparación contra lo planificado | 1 | To Do | 24/10 | T-209 |
| T-214 | HU-57 | Conexión API | Conectar la pantalla de desviación con la API real | 5 | To Do | 26/10 | T-212, T-213, T-240 |
| T-223 | HU-62 | Pruebas | Probar la funcionalidad de HU-62 (la API con Postman y la app) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-219, T-220, T-221, T-222, T-233 |
| T-231 | HU-63 | Pruebas | Probar la funcionalidad de HU-63 (la API con Postman y la app en modo avión y al volver la señal) contra cada condición final y aprobar su PR | 1 | To Do | 27/10 | T-227, T-228, T-229, T-230, T-233 |
| T-240 | HT-04 | Web | Programar el ClienteHttp para que la web hable con la API real (snake_case ↔ camelCase, errores {codigo, mensaje}) | 4 | To Do | 26/10 | — |
| T-245 | HT-06 | Pruebas | Armar y compartir la colección de Postman con entorno de pruebas, login y un ejemplo correcto y uno de error por endpoint | 3 | To Do | 24/10 | — |

### Nicolás · 19 tareas · 61 h pendientes

| ID | HU | Fase | Tarea | h | Estado | Límite | Depende de |
|---|---|---|---|--:|---|---|---|
| T-036 | HU-12 | Modelado UML | Modelar la funcionalidad de HU-12 con el diagrama UML de secuencia del login y la renovación de la sesión | 1 | To Do | 24/10 | T-035 |
| T-040 | HU-12 | Front-end app | Programar el login en la app del almacenero | 3 | To Do | 26/10 | T-035, T-242 |
| T-058 | HU-15 | Modelado UML | Modelar la funcionalidad de HU-15 con el diagrama UML de secuencia del registro desde un dispositivo no autorizado | 1 | To Do | 24/10 | T-057 |
| T-061 | HU-15 | Front-end app | Programar en la app la generación del código del dispositivo en la primera instalación | 3 | To Do | 26/10 | T-057, T-242 |
| T-157 | HU-35 | Front-end app | Programar la recepción de compras en la app del almacenero | 4 | To Do | 26/10 | T-152, T-242 |
| T-163 | HU-37 | Base de datos | Implementar las tablas asociadas a HU-37: cola local en Room (movimiento_pendiente) en el celular | 1 | To Do | 24/10 | T-162 |
| T-164 | HU-37 | Front-end app | Programar la cola local (Room) con uuid_cliente y hora del celular, y mostrar «pendiente de enviar» en cada movimiento | 14 | To Do | 26/10 | T-161, T-242 |
| T-172 | HU-38 | Front-end app | Programar el envío automático de pendientes al volver la señal (WorkManager) | 4 | To Do | 26/10 | T-167, T-242 |
| T-180 | HU-40 | Front-end app | Programar la pantalla de salida en la app (insumo, cantidad, motivo, quién retira) | 7 | To Do | 26/10 | T-175, T-242 |
| T-184 | HU-43 | Modelado UML | Modelar la funcionalidad de HU-43 con el diagrama UML de secuencia del cálculo del stock desde el libro | 1 | To Do | 24/10 | T-183 |
| T-189 | HU-43 | Front-end app | Programar la consulta de stock en la app | 2 | To Do | 26/10 | T-183, T-242 |
| T-218 | HU-62 | Modelado UML | Modelar la funcionalidad de HU-62 con el diagrama UML de secuencia del registro con consulta de DNI | 1 | To Do | 24/10 | T-217 |
| T-222 | HU-62 | Front-end app | Programar en la app el registro de trabajadores con consulta de DNI | 2 | To Do | 26/10 | T-217, T-242 |
| T-226 | HU-63 | Modelado UML | Modelar la funcionalidad de HU-63 con el diagrama UML de actividades de la toma de asistencia | 1 | To Do | 24/10 | T-225 |
| T-230 | HU-63 | Front-end app | Programar la pantalla de asistencia del día en la app (horas normales y extra) | 4 | To Do | 26/10 | T-225, T-242 |
| T-242 | HT-05 | Android | Crear el proyecto base de la app: Compose, Room, WorkManager y cliente de la API | 6 | To Do | 26/10 | — |
| T-257 | — | UX | Revisar la app con guantes y al sol: botones grandes, una acción por pantalla, contraste AA | 2 | To Do | 26/10 | — |
| T-259 | — | Modelado UML | Dibujar los diagramas de actividades del negocio y de estados | 2 | Done | 24/10 | — |
| T-260 | — | Modelado UML | Dibujar la arquitectura en las cuatro vistas del C4 a partir del diagrama de microservicios | 4 | To Do | 24/10 | — |

### Adiel · 106 tareas · 117 h pendientes

| ID | HU | Fase | Tarea | h | Estado | Límite | Depende de |
|---|---|---|---|--:|---|---|---|
| T-003 | HU-01 | Base de datos | Implementar las tablas asociadas a HU-01: proyecto, proyecto_plazo_historial | 1 | Done | 24/10 | — |
| T-004 | HU-01 | Backend | Programar el back-end de HU-01 (entidad, repositorio, servicio y controlador): el endpoint para crear la obra, validando nombre y plazo obligatorios, fin ≥ inicio y estado inicial «en ejecución» | 2 | Done | 25/10 | — |
| T-005 | HU-01 | Triggers y funciones | Implementar el CHECK de fecha de fin ≥ inicio en proyecto (HU-01) | 1 | To Do | 25/10 | T-003 |
| T-009 | HU-01 | Aceptación | Aceptar HU-01 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-008 |
| T-012 | HU-03 | Base de datos | Implementar las tablas asociadas a HU-03: periodo | 1 | Done | 24/10 | — |
| T-013 | HU-03 | Backend | Programar el back-end de HU-03 (entidad, repositorio, servicio y controlador): la generación automática de semanas al crear la obra y al ampliar el plazo, con sus pruebas | 2 | Done | 25/10 | — |
| T-017 | HU-03 | Aceptación | Aceptar HU-03 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-016 |
| T-020 | HU-07 | Base de datos | Implementar las tablas asociadas a HU-07: insumo (crear/actualizar por código) | 1 | To Do | 24/10 | T-019 |
| T-021 | HU-07 | Backend | Programar el back-end de HU-07 (entidad, repositorio, servicio y controlador): el endpoint que aplica la importación: crear o actualizar insumos por código sin tocar saldos | 4 | To Do | 25/10 | T-020 |
| T-022 | HU-07 | Backend | Configurar la llamada de la API a Integraciones con el token de servicio en docker compose | 4 | To Do | 25/10 | T-020 |
| T-026 | HU-07 | Aceptación | Aceptar HU-07 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-025 |
| T-029 | HU-08 | Base de datos | Implementar las tablas asociadas a HU-08: insumo, insumo_nombre_comercial | 1 | To Do | 24/10 | T-028 |
| T-030 | HU-08 | Backend | Programar el back-end de HU-08 (entidad, repositorio, servicio y controlador): el endpoint del catálogo: generar código único, rechazar duplicados y marcar «por verificar» | 3 | To Do | 25/10 | T-029 |
| T-034 | HU-08 | Aceptación | Aceptar HU-08 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-033 |
| T-037 | HU-12 | Base de datos | Implementar las tablas asociadas a HU-12: usuario (hash de la clave, intentos, estado) | 1 | Done | 24/10 | — |
| T-038 | HU-12 | Backend | Programar el back-end de HU-12 (entidad, repositorio, servicio y controlador): el login con Argon2id, sesión que caduca y se renueva, y bloqueo por intentos fallidos | 3 | Done | 25/10 | — |
| T-042 | HU-12 | Aceptación | Aceptar HU-12 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-041 |
| T-045 | HU-13 | Base de datos | Implementar las tablas asociadas a HU-13: asignacion (el rol vive aquí, por obra) | 1 | Done | 24/10 | — |
| T-046 | HU-13 | Backend | Programar el back-end de HU-13 (entidad, repositorio, servicio y controlador): los roles y verificar sus permisos en el servidor | 4 | Done | 25/10 | — |
| T-049 | HU-13 | Aceptación | Aceptar HU-13 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-048 |
| T-052 | HU-14 | Base de datos | Implementar las tablas asociadas a HU-14: asignacion | 1 | Done | 24/10 | — |
| T-053 | HU-14 | Backend | Programar el back-end de HU-14 (entidad, repositorio, servicio y controlador): el endpoint para asignar usuarios a obras | 2 | Done | 25/10 | — |
| T-056 | HU-14 | Aceptación | Aceptar HU-14 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-055 |
| T-059 | HU-15 | Base de datos | Implementar las tablas asociadas a HU-15: dispositivo (máximo dos activos por obra) | 1 | Done | 24/10 | — |
| T-060 | HU-15 | Backend | Programar el back-end de HU-15 (entidad, repositorio, servicio y controlador): el registro de dispositivos autorizados por obra (máximo dos) y el rechazo del no autorizado | 3 | Done | 25/10 | — |
| T-064 | HU-15 | Aceptación | Aceptar HU-15 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-063 |
| T-067 | HU-16 | Base de datos | Implementar las tablas asociadas a HU-16: partida, partida_recurso | 1 | To Do | 24/10 | T-066 |
| T-068 | HU-16 | Backend | Crear la tabla partida–recurso y programar el endpoint que la guarda | 5 | To Do | 25/10 | T-067 |
| T-072 | HU-16 | Aceptación | Aceptar HU-16 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-071 |
| T-075 | HU-17 | Base de datos | Implementar las tablas asociadas a HU-17: insumo.factor_conversion con su histórico | 1 | To Do | 24/10 | T-074 |
| T-076 | HU-17 | Backend | Programar el back-end de HU-17 (entidad, repositorio, servicio y controlador): el registro del factor de conversión con su fecha y su usuario | 2 | To Do | 25/10 | T-075 |
| T-079 | HU-17 | Aceptación | Aceptar HU-17 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-078 |
| T-082 | HU-19 | Base de datos | Implementar las tablas asociadas a HU-19: requerimiento (cantidad y costo planificados, ajuste con motivo) | 1 | To Do | 24/10 | T-081 |
| T-083 | HU-19 | Backend | Programar el back-end de HU-19 (entidad, repositorio, servicio y controlador): el guardado del requerimiento total y de sus ajustes con motivo | 3 | To Do | 25/10 | T-082 |
| T-087 | HU-19 | Aceptación | Aceptar HU-19 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-086 |
| T-090 | HU-23 | Base de datos | Implementar las tablas asociadas a HU-23: vista de avance sobre requerimiento, compra_detalle y movimiento | 1 | To Do | 24/10 | T-089 |
| T-091 | HU-23 | Backend | Programar el back-end de HU-23 (entidad, repositorio, servicio y controlador): la consulta de avance: requerido, comprado, recibido y consumido por insumo | 3 | To Do | 25/10 | T-090 |
| T-095 | HU-23 | Aceptación | Aceptar HU-23 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-094 |
| T-098 | HU-24 | Base de datos | Implementar las tablas asociadas a HU-24: vista de saldo por comprar (requerido − comprado) | 1 | To Do | 24/10 | T-097 |
| T-099 | HU-24 | Backend | Programar el back-end de HU-24 (entidad, repositorio, servicio y controlador): la consulta de lo que falta comprar por insumo | 2 | To Do | 25/10 | T-098 |
| T-103 | HU-24 | Aceptación | Aceptar HU-24 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-102 |
| T-106 | HU-25 | Base de datos | Implementar las tablas asociadas a HU-25: parametro_reposicion (con vigencias, nunca se pisa) | 1 | To Do | 24/10 | T-105 |
| T-107 | HU-25 | Backend | Programar el back-end de HU-25 (entidad, repositorio, servicio y controlador): el registro del stock de seguridad y del punto de reposición, con histórico | 4 | To Do | 25/10 | T-106 |
| T-110 | HU-25 | Aceptación | Aceptar HU-25 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-109 |
| T-113 | HU-26 | Base de datos | Implementar las tablas asociadas a HU-26: alerta_reposicion y outbox de Notificaciones | 1 | To Do | 24/10 | T-112 |
| T-114 | HU-26 | Backend | Programar el back-end de HU-26 (entidad, repositorio, servicio y controlador): el motor de alertas: una por insumo y marcada como atendida al comprar | 2 | To Do | 25/10 | T-113 |
| T-115 | HU-26 | Integración | Publicar la alerta en el microservicio de Notificaciones (outbox) para avisar al residente | 2 | To Do | 25/10 | T-112 |
| T-119 | HU-26 | Aceptación | Aceptar HU-26 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-118 |
| T-122 | HU-27 | Base de datos | Implementar las tablas asociadas a HU-27: vista de sugerencia de compra | 1 | To Do | 24/10 | T-121 |
| T-123 | HU-27 | Backend | Programar el back-end de HU-27 (entidad, repositorio, servicio y controlador): el cálculo de la sugerencia de compra con su criterio | 3 | To Do | 25/10 | T-122 |
| T-127 | HU-27 | Aceptación | Aceptar HU-27 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-126 |
| T-130 | HU-29 | Base de datos | Implementar las tablas asociadas a HU-29: proveedor (RUC o DNI único) | 1 | Done | 24/10 | — |
| T-131 | HU-29 | Backend | Programar el back-end de HU-29 (entidad, repositorio, servicio y controlador): la búsqueda de proveedores por razón social o RUC | 2 | Done | 25/10 | — |
| T-135 | HU-29 | Aceptación | Aceptar HU-29 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-134 |
| T-138 | HU-30 | Base de datos | Implementar las tablas asociadas a HU-30: proveedor | 1 | Done | 24/10 | — |
| T-139 | HU-30 | Backend | Programar el back-end de HU-30 (entidad, repositorio, servicio y controlador): el alta de proveedor rechazando RUC o DNI repetidos | 2 | Done | 25/10 | — |
| T-143 | HU-30 | Aceptación | Aceptar HU-30 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-142 |
| T-146 | HU-32 | Base de datos | Implementar las tablas asociadas a HU-32: compra, compra_detalle | 1 | Done | 24/10 | — |
| T-147 | HU-32 | Backend | Programar el back-end de HU-32 (entidad, repositorio, servicio y controlador): el registro de compra: comprobante único, base, IGV y total, sin mover stock | 5 | Done | 25/10 | — |
| T-151 | HU-32 | Aceptación | Aceptar HU-32 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-150 |
| T-154 | HU-35 | Base de datos | Implementar las tablas asociadas a HU-35: recepcion, recepcion_detalle y su movimiento de ingreso | 1 | To Do | 24/10 | T-153 |
| T-155 | HU-35 | Backend | Programar el back-end de HU-35 (entidad, repositorio, servicio y controlador): la recepción total o parcial que genera el ingreso al kárdex | 4 | To Do | 25/10 | T-154 |
| T-160 | HU-35 | Aceptación | Aceptar HU-35 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-159 |
| T-166 | HU-37 | Aceptación | Aceptar HU-37 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-165 |
| T-169 | HU-38 | Base de datos | Implementar las tablas asociadas a HU-38: movimiento.uuid_cliente UNIQUE | 1 | To Do | 24/10 | T-168 |
| T-170 | HU-38 | Backend | Programar el back-end de HU-38 (entidad, repositorio, servicio y controlador): la idempotencia por uuid_cliente y devolver el motivo del rechazo | 4 | To Do | 25/10 | T-169 |
| T-171 | HU-38 | Triggers y funciones | Implementar el índice único de uuid_cliente que vuelve idempotente el reenvío (HU-38) | 1 | To Do | 25/10 | T-169 |
| T-174 | HU-38 | Aceptación | Aceptar HU-38 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-173 |
| T-177 | HU-40 | Base de datos | Implementar las tablas asociadas a HU-40: movimiento (append-only, costo_unitario congelado) | 1 | To Do | 24/10 | T-176 |
| T-178 | HU-40 | Backend | Programar el back-end de HU-40 (entidad, repositorio, servicio y controlador): la salida: rechazar si supera el saldo y congelar el costo promedio | 7 | To Do | 25/10 | T-177 |
| T-179 | HU-40 | Triggers y funciones | Implementar el trigger que impide UPDATE y DELETE en movimiento y el que rechaza fechas fuera del plazo (HU-40) | 1 | To Do | 25/10 | T-177 |
| T-182 | HU-40 | Aceptación | Aceptar HU-40 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-181 |
| T-185 | HU-43 | Base de datos | Implementar las tablas asociadas a HU-43: vista de saldo derivada de movimiento | 1 | To Do | 24/10 | T-184 |
| T-186 | HU-43 | Backend | Programar el back-end de HU-43 (entidad, repositorio, servicio y controlador): el cálculo del stock desde el libro de movimientos, por almacén y consolidado | 2 | To Do | 25/10 | T-185 |
| T-187 | HU-43 | Triggers y funciones | Implementar la función que calcula el saldo SUM(cantidad × signo) por almacén e insumo (HU-43) | 1 | To Do | 25/10 | T-185 |
| T-192 | HU-43 | Aceptación | Aceptar HU-43 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-191 |
| T-195 | HU-45 | Base de datos | Implementar las tablas asociadas a HU-45: movimiento.movimiento_origen_id (reverso) | 1 | To Do | 24/10 | T-194 |
| T-196 | HU-45 | Backend | Programar el back-end de HU-45 (entidad, repositorio, servicio y controlador): el reverso que apunta al original, sin UPDATE ni DELETE | 3 | To Do | 25/10 | T-195 |
| T-200 | HU-45 | Aceptación | Aceptar HU-45 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-199 |
| T-203 | HU-55 | Base de datos | Implementar las tablas asociadas a HU-55: costo_promedio por insumo y almacén | 1 | To Do | 24/10 | T-202 |
| T-204 | HU-55 | Backend | Programar el back-end de HU-55 (entidad, repositorio, servicio y controlador): el costo promedio ponderado congelado en cada salida, sin IGV de factura (NIC 2) | 7 | To Do | 25/10 | T-203 |
| T-205 | HU-55 | Triggers y funciones | Implementar la función que recalcula el costo promedio ponderado en cada ingreso (HU-55) | 1 | To Do | 25/10 | T-203 |
| T-208 | HU-55 | Aceptación | Aceptar HU-55 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-207 |
| T-211 | HU-57 | Base de datos | Implementar las tablas asociadas a HU-57: vista de desviación contra requerimiento | 1 | To Do | 24/10 | T-210 |
| T-212 | HU-57 | Backend | Programar el back-end de HU-57 (entidad, repositorio, servicio y controlador): la comparación del consumo contra lo planificado en el S10 | 5 | To Do | 25/10 | T-211 |
| T-216 | HU-57 | Aceptación | Aceptar HU-57 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-215 |
| T-219 | HU-62 | Base de datos | Implementar las tablas asociadas a HU-62: trabajador (único por obra, baja con fecha) | 1 | Done | 24/10 | — |
| T-220 | HU-62 | Backend | Programar el back-end de HU-62 (entidad, repositorio, servicio y controlador): el registro de trabajadores: único por obra y baja con fecha | 2 | Done | 25/10 | — |
| T-224 | HU-62 | Aceptación | Aceptar HU-62 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-223 |
| T-227 | HU-63 | Base de datos | Implementar las tablas asociadas a HU-63: jornada (UNIQUE trabajador + fecha entre las vigentes) | 1 | Done | 24/10 | — |
| T-228 | HU-63 | Backend | Programar el back-end de HU-63 (entidad, repositorio, servicio y controlador): la asistencia: una jornada por trabajador y fecha, corrección sin borrar | 4 | Done | 25/10 | — |
| T-229 | HU-63 | Triggers y funciones | Implementar el índice único parcial (trabajador, fecha) entre las jornadas vigentes (HU-63) | 1 | Done | 25/10 | — |
| T-232 | HU-63 | Aceptación | Aceptar HU-63 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-231 |
| T-234 | HT-01 | Aceptación | Aceptar HT-01 como Product Owner y moverla a Done | 0.5 | To Do | 27/10 | — |
| T-235 | HT-02 | DevOps | Configurar las pruebas automáticas en cada PR con GitHub Actions | 3 | To Do | 24/10 | — |
| T-236 | HT-02 | Aceptación | Aceptar HT-02 como Product Owner y moverla a Done | 0.5 | To Do | 27/10 | — |
| T-239 | HT-03 | Aceptación | Aceptar HT-03 como Product Owner y moverla a Done | 0.5 | To Do | 27/10 | — |
| T-241 | HT-04 | Aceptación | Aceptar HT-04 como Product Owner y moverla a Done | 0.5 | To Do | 27/10 | — |
| T-243 | HT-05 | Aceptación | Aceptar HT-05 como Product Owner y moverla a Done | 0.5 | To Do | 27/10 | — |
| T-244 | HT-06 | Backend | Publicar el OpenAPI de la API en el ambiente de pruebas | 1 | Doing | 25/10 | — |
| T-246 | HT-06 | Aceptación | Aceptar HT-06 como Product Owner y moverla a Done | 0.5 | To Do | 27/10 | — |
| T-247 | HT-07 | Backend | Cargar en la base los 626 proveedores y el catálogo de insumos del cliente | 1 | To Do | 25/10 | — |
| T-248 | HT-07 | Backend | Crear la obra Multifamiliar Flores con su relación de insumos del S10 en la base de pruebas | 1 | To Do | 25/10 | — |
| T-249 | HT-07 | Aceptación | Aceptar HT-07 como Product Owner y moverla a Done | 0.5 | To Do | 27/10 | — |
| T-251 | HT-08 | DevOps | Configurar el token de servicio en docker compose y enviarlo desde la API a Integraciones y Notificaciones | 1.5 | To Do | 24/10 | — |
| T-252 | HT-08 | Aceptación | Aceptar HT-08 como Product Owner y moverla a Done | 0.5 | To Do | 27/10 | — |
