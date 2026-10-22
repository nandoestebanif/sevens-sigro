# Sprint 1 · Tablero Scrum (tareas)

Sprint 1: **15 al 28 de octubre de 2026** · 29 HU · 153 puntos · **156 tareas**.
Fuente única: `docs/scrum/sigro-sprint1-tareas.csv` (se importa a GitHub Projects o Jira) y el Excel
`docs/scrum/SIGRO - Sprint 1 · Tablero Scrum (tareas).xlsx` (hoja «Validar HU y tareas» para revisar,
hoja «Resumen» que se actualiza sola).

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

## Criterio para las tareas

Una tarea entra solo si **produce algo que la DoD verifica** o **desbloquea a otra**. Lo ya hecho se cuenta
una vez; el diseño de pantallas y el UML se hacen **por flujo**, no por HU. Cada HU queda con:

1. **Programar** (backend, web o app, según la HU) — incluye documentar el endpoint y la regla (T10).
2. **Conectar** la pantalla web con la API real, cuando aplica.
3. **Probar** contra las condiciones finales y **aprobar el PR** — siempre alguien distinto del autor.
4. **Aceptar** como Product Owner → la HU pasa a Done.

## Lo que se hace primero (bloquea a las demás)

| ID | Tarea | Responsable | Límite |
|---|---|---|---|
| T-139 | Desplegar el ambiente de pruebas (API, base y web) — sin esto ninguna HU cumple T8 | Fernando | 24/10 |
| T-140 | Programar el ClienteHttp: la web habla con la API real | Henry | 26/10 (cuanto antes) |
| T-141 | Crear el proyecto base de la app Android | Nicolás | 26/10 (cuanto antes) |

## Carga al 22/10

Horas pendientes: Fernando ~70 · Henry ~64 · Nicolás ~55 · Adiel ~86. Es más de lo que cabe hasta el 27:
el Product Owner y el equipo deben decidir qué HU se quedan en el sprint (recorte de alcance manteniendo el
objetivo) y medir la velocidad real en la Review.

## Tareas por persona

### Fernando · 52 tareas · 70.5 h pendientes

| ID | HU | Fase | Tarea | h | Estado | Límite | Depende de |
|---|---|---|---|--:|---|---|---|
| T-002 | HU-01 | Web | Programar la pantalla web para crear la obra | 2 | Done | 26/10 | — |
| T-003 | HU-01 | Web | Conectar la pantalla «Crear la obra» con el endpoint real de la API | 2 | To Do | 26/10 | T-001, T-140 |
| T-007 | HU-03 | Web | Programar la tira de semanas en la web (cerrada, en curso, por venir) | 2 | Done | 26/10 | — |
| T-008 | HU-03 | Web | Conectar la tira de semanas con la API real | 2 | To Do | 26/10 | T-006, T-140 |
| T-011 | HU-07 | Integraciones | Programar el lector del PDF «Relación de insumos» del S10 en el microservicio | 4 | Done | 25/10 | — |
| T-014 | HU-07 | Web | Programar la pantalla web «Importar del S10» con el resumen de creados, actualizados y fallidos | 4 | Done | 26/10 | — |
| T-018 | HU-08 | Web | Programar la pantalla web del catálogo para ver, dar de alta y editar insumos | 3 | Done | 26/10 | — |
| T-019 | HU-08 | Web | Conectar la pantalla del catálogo con la API real | 3 | To Do | 26/10 | T-017, T-140 |
| T-025 | HU-12 | Pruebas | Probar HU-12 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-022, T-023, T-024, T-139 |
| T-029 | HU-13 | Pruebas | Probar HU-13 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-027, T-028, T-139 |
| T-033 | HU-14 | Pruebas | Probar HU-14 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-031, T-032, T-139 |
| T-038 | HU-15 | Pruebas | Probar HU-15 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-035, T-036, T-037, T-139 |
| T-040 | HU-16 | Integraciones | Programar el lector del PDF del APU (371 páginas), identificando la partida por código y descripción | 5 | Doing | 25/10 | — |
| T-042 | HU-16 | Web | Programar la pantalla web para importar el APU | 5 | To Do | 26/10 | — |
| T-046 | HU-17 | Web | Programar el formulario web del factor de conversión (kg ↔ varillas) | 2 | To Do | 26/10 | — |
| T-049 | HU-19 | Integraciones | Adaptar el lector de la Relación de insumos para obtener el requerimiento total | 3 | To Do | 25/10 | — |
| T-051 | HU-19 | Web | Programar la pantalla web del requerimiento total | 3 | To Do | 26/10 | — |
| T-055 | HU-23 | Web | Programar la pantalla web de avance de compra y consumo | 3 | Done | 26/10 | — |
| T-056 | HU-23 | Web | Conectar la pantalla de avance con la API real | 3 | To Do | 26/10 | T-054, T-140 |
| T-060 | HU-24 | Web | Programar la pantalla web «Saldo por comprar» con filtros y exportación CSV | 2 | Done | 26/10 | — |
| T-061 | HU-24 | Web | Conectar la pantalla de saldo por comprar con la API real | 2 | To Do | 26/10 | T-059, T-140 |
| T-065 | HU-25 | Web | Programar el formulario web de parámetros de reposición | 4 | To Do | 26/10 | — |
| T-069 | HU-26 | Web | Programar las alertas de reposición en la web | 3 | Done | 26/10 | — |
| T-070 | HU-26 | Web | Conectar las alertas con la API real | 3 | To Do | 26/10 | T-068, T-140 |
| T-074 | HU-27 | Web | Programar la sugerencia editable dentro de la alerta | 3 | Done | 26/10 | — |
| T-075 | HU-27 | Web | Conectar la sugerencia con la API real | 3 | To Do | 26/10 | T-073, T-140 |
| T-079 | HU-29 | Web | Programar la pantalla web de proveedores con los 626 reales | 2 | Done | 26/10 | — |
| T-081 | HU-29 | Pruebas | Probar HU-29 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-078, T-079, T-080, T-139 |
| T-084 | HU-30 | Web | Programar el alta rápida de proveedor desde la compra | 2 | Done | 26/10 | — |
| T-086 | HU-30 | Pruebas | Probar HU-30 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-083, T-084, T-085, T-139 |
| T-089 | HU-32 | Web | Programar la pantalla web de compras | 5 | Done | 26/10 | — |
| T-091 | HU-32 | Pruebas | Probar HU-32 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-088, T-089, T-090, T-139 |
| T-094 | HU-35 | Web | Programar el registro de recepción en la web (pedido, recibido, pendiente) | 4 | Done | 26/10 | — |
| T-097 | HU-35 | Pruebas | Probar HU-35 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-093, T-094, T-095, T-096, T-139 |
| T-111 | HU-43 | Web | Programar la pantalla web de stock y kárdex | 2 | Done | 26/10 | — |
| T-114 | HU-43 | Pruebas | Probar HU-43 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-110, T-111, T-112, T-113, T-139 |
| T-117 | HU-45 | Web | Programar la corrección con reverso desde el kárdex web | 3 | Done | 26/10 | — |
| T-119 | HU-45 | Pruebas | Probar HU-45 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-116, T-117, T-118, T-139 |
| T-123 | HU-55 | Pruebas | Probar HU-55 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-121, T-122, T-139 |
| T-126 | HU-57 | Web | Programar la pantalla web «Gasto en recursos» con la desviación | 5 | Done | 26/10 | — |
| T-128 | HU-57 | Pruebas | Probar HU-57 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-125, T-126, T-127, T-139 |
| T-131 | HU-62 | Integraciones | Programar la consulta de DNI en RENIEC desde el microservicio | 2 | Done | 25/10 | — |
| T-139 | — | DevOps | Desplegar el ambiente de pruebas (API, base de datos y web) que exige el T8 de la DoD | 6 | To Do | 24/10 | — |
| T-144 | — | Scrum | Poner el repositorio en privado e invitar al docente como lector | 0.5 | To Do | 23/10 | — |
| T-145 | — | Scrum | Armar el tablero en GitHub Projects con las plantillas de DoR y DoD | 2 | Doing | 23/10 | — |
| T-146 | — | Scrum | Facilitar el Daily Scrum de 15 minutos cada día, registrarlo y actualizar el burndown | 2 | Doing | 27/10 | — |
| T-147 | — | UX | Simplificar los textos de cada pantalla web: título y una línea, con ayuda «?» bajo demanda | 3 | To Do | 26/10 | — |
| T-148 | — | UX | Validar las pantallas con el cliente (Pool) en una sesión corta y registrar sus observaciones | 1.5 | To Do | 26/10 | — |
| T-150 | — | Diseño UI | Diseñar las 23 pantallas del Sprint 1 y los mapas de navegación | 2 | Done | 23/10 | — |
| T-151 | — | Modelado UML | Modelar los 3 diagramas de casos de uso y los 3 de secuencia (importación S10, salida sin señal, recepción y costo promedio) | 2 | Done | 24/10 | — |
| T-153 | — | Modelado UML | Modelar la secuencia de la alerta de reposición con Notificaciones (HU-25 a HU-27) | 1.5 | To Do | 24/10 | — |
| T-156 | — | Review | Preparar la demo de la Sprint Review: una compra que cruza recepción, salida y costo de punta a punta | 2 | To Do | 27/10 | — |

### Henry · 31 tareas · 64.5 h pendientes

| ID | HU | Fase | Tarea | h | Estado | Límite | Depende de |
|---|---|---|---|--:|---|---|---|
| T-004 | HU-01 | Pruebas | Probar HU-01 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-001, T-002, T-003, T-139 |
| T-009 | HU-03 | Pruebas | Probar HU-03 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-006, T-007, T-008, T-139 |
| T-015 | HU-07 | Pruebas | Probar HU-07 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-011, T-012, T-013, T-014, T-139 |
| T-020 | HU-08 | Pruebas | Probar HU-08 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-017, T-018, T-019, T-139 |
| T-023 | HU-12 | Web | Programar la pantalla web de login y el manejo de la sesión | 3 | To Do | 26/10 | — |
| T-028 | HU-13 | Web | Programar la pantalla web para crear usuarios, asignar su rol y darlos de baja | 4 | To Do | 26/10 | — |
| T-032 | HU-14 | Web | Programar la pantalla web para asignar usuarios a obras | 2 | To Do | 26/10 | — |
| T-037 | HU-15 | Web | Programar la pantalla web para autorizar o cambiar el celular de la obra | 3 | To Do | 26/10 | — |
| T-043 | HU-16 | Pruebas | Probar HU-16 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-040, T-041, T-042, T-139 |
| T-047 | HU-17 | Pruebas | Probar HU-17 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-045, T-046, T-139 |
| T-052 | HU-19 | Pruebas | Probar HU-19 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-049, T-050, T-051, T-139 |
| T-057 | HU-23 | Pruebas | Probar HU-23 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-054, T-055, T-056, T-139 |
| T-062 | HU-24 | Pruebas | Probar HU-24 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-059, T-060, T-061, T-139 |
| T-066 | HU-25 | Pruebas | Probar HU-25 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-064, T-065, T-139 |
| T-071 | HU-26 | Pruebas | Probar HU-26 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-068, T-069, T-070, T-139 |
| T-076 | HU-27 | Pruebas | Probar HU-27 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-073, T-074, T-075, T-139 |
| T-080 | HU-29 | Web | Conectar la pantalla de proveedores con la API real | 2 | To Do | 26/10 | T-078, T-140 |
| T-085 | HU-30 | Web | Conectar el alta de proveedor con la API real | 2 | To Do | 26/10 | T-083, T-140 |
| T-090 | HU-32 | Web | Conectar la pantalla de compras con la API real | 5 | To Do | 26/10 | T-088, T-140 |
| T-095 | HU-35 | Web | Conectar la recepción con la API real | 4 | To Do | 26/10 | T-093, T-140 |
| T-100 | HU-37 | Pruebas | Probar HU-37 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-099, T-139 |
| T-104 | HU-38 | Pruebas | Probar HU-38 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-102, T-103, T-139 |
| T-108 | HU-40 | Pruebas | Probar HU-40 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-106, T-107, T-139 |
| T-112 | HU-43 | Web | Conectar la pantalla de stock y kárdex con la API real | 2 | To Do | 26/10 | T-110, T-140 |
| T-118 | HU-45 | Web | Conectar el reverso con la API real | 3 | To Do | 26/10 | T-116, T-140 |
| T-122 | HU-55 | Web | Mostrar la salida valorizada en el kárdex y en el reporte web | 7 | To Do | 26/10 | — |
| T-127 | HU-57 | Web | Conectar la pantalla de desviación con la API real | 5 | To Do | 26/10 | T-125, T-140 |
| T-133 | HU-62 | Pruebas | Probar HU-62 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-130, T-131, T-132, T-139 |
| T-137 | HU-63 | Pruebas | Probar HU-63 en el ambiente de pruebas contra sus condiciones finales y aprobar su PR | 1 | To Do | 27/10 | T-135, T-136, T-139 |
| T-140 | — | Web | Programar el ClienteHttp para que la web hable con la API real (snake_case ↔ camelCase, errores {codigo, mensaje}) | 4 | To Do | 26/10 | — |
| T-152 | — | Modelado UML | Modelar la secuencia del login y del permiso verificado en el servidor (HU-12 a HU-15) | 1.5 | To Do | 24/10 | — |

### Nicolás · 13 tareas · 55 h pendientes

| ID | HU | Fase | Tarea | h | Estado | Límite | Depende de |
|---|---|---|---|--:|---|---|---|
| T-024 | HU-12 | Android | Programar el login en la app del almacenero | 3 | To Do | 26/10 | T-141 |
| T-036 | HU-15 | Android | Programar en la app la generación del código del dispositivo en la primera instalación | 3 | To Do | 26/10 | T-141 |
| T-096 | HU-35 | Android | Programar la recepción de compras en la app del almacenero | 4 | To Do | 26/10 | T-141 |
| T-099 | HU-37 | Android | Programar la cola local (Room) con uuid_cliente y hora del celular, y mostrar «pendiente de enviar» en cada movimiento | 14 | To Do | 26/10 | T-141 |
| T-102 | HU-38 | Android | Programar el envío automático de pendientes al volver la señal (WorkManager) | 4 | To Do | 26/10 | T-141 |
| T-107 | HU-40 | Android | Programar la pantalla de salida en la app (insumo, cantidad, motivo, quién retira) | 7 | To Do | 26/10 | T-141 |
| T-113 | HU-43 | Android | Programar la consulta de stock en la app | 2 | To Do | 26/10 | T-141 |
| T-132 | HU-62 | Android | Programar en la app el registro de trabajadores con consulta de DNI | 2 | To Do | 26/10 | T-141 |
| T-136 | HU-63 | Android | Programar la pantalla de asistencia del día en la app (horas normales y extra) | 4 | To Do | 26/10 | T-141 |
| T-141 | — | Android | Crear el proyecto base de la app: Compose, Room, WorkManager y cliente de la API | 6 | To Do | 26/10 | — |
| T-149 | — | UX | Revisar la app con guantes y al sol: botones grandes, una acción por pantalla, contraste AA | 2 | To Do | 26/10 | — |
| T-154 | — | Modelado UML | Dibujar los diagramas de actividades del negocio y de estados | 2 | Done | 24/10 | — |
| T-155 | — | Modelado UML | Dibujar la arquitectura en las cuatro vistas del C4 a partir del diagrama de microservicios | 4 | To Do | 24/10 | — |

### Adiel · 60 tareas · 86.5 h pendientes

| ID | HU | Fase | Tarea | h | Estado | Límite | Depende de |
|---|---|---|---|--:|---|---|---|
| T-001 | HU-01 | Backend | Programar el endpoint para crear la obra, validando nombre y plazo obligatorios, fin ≥ inicio y estado inicial «en ejecución» | 2 | Done | 25/10 | — |
| T-005 | HU-01 | Aceptación | Aceptar HU-01 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-004 |
| T-006 | HU-03 | Backend | Programar la generación automática de semanas al crear la obra y al ampliar el plazo, con sus pruebas | 2 | Done | 25/10 | — |
| T-010 | HU-03 | Aceptación | Aceptar HU-03 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-009 |
| T-012 | HU-07 | Backend | Programar el endpoint que aplica la importación: crear o actualizar insumos por código sin tocar saldos | 4 | To Do | 25/10 | — |
| T-013 | HU-07 | Backend | Configurar la llamada de la API a Integraciones con el token de servicio en docker compose | 4 | To Do | 25/10 | — |
| T-016 | HU-07 | Aceptación | Aceptar HU-07 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-015 |
| T-017 | HU-08 | Backend | Programar el endpoint del catálogo: generar código único, rechazar duplicados y marcar «por verificar» | 3 | To Do | 25/10 | — |
| T-021 | HU-08 | Aceptación | Aceptar HU-08 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-020 |
| T-022 | HU-12 | Backend | Programar el login con Argon2id, sesión que caduca y se renueva, y bloqueo por intentos fallidos | 3 | Done | 25/10 | — |
| T-026 | HU-12 | Aceptación | Aceptar HU-12 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-025 |
| T-027 | HU-13 | Backend | Programar los roles y verificar sus permisos en el servidor | 4 | Done | 25/10 | — |
| T-030 | HU-13 | Aceptación | Aceptar HU-13 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-029 |
| T-031 | HU-14 | Backend | Programar el endpoint para asignar usuarios a obras | 2 | Done | 25/10 | — |
| T-034 | HU-14 | Aceptación | Aceptar HU-14 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-033 |
| T-035 | HU-15 | Backend | Programar el registro de dispositivos autorizados por obra (máximo dos) y el rechazo del no autorizado | 3 | Done | 25/10 | — |
| T-039 | HU-15 | Aceptación | Aceptar HU-15 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-038 |
| T-041 | HU-16 | Backend | Crear la tabla partida–recurso y programar el endpoint que la guarda | 5 | To Do | 25/10 | — |
| T-044 | HU-16 | Aceptación | Aceptar HU-16 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-043 |
| T-045 | HU-17 | Backend | Programar el registro del factor de conversión con su fecha y su usuario | 2 | To Do | 25/10 | — |
| T-048 | HU-17 | Aceptación | Aceptar HU-17 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-047 |
| T-050 | HU-19 | Backend | Programar el guardado del requerimiento total y de sus ajustes con motivo | 3 | To Do | 25/10 | — |
| T-053 | HU-19 | Aceptación | Aceptar HU-19 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-052 |
| T-054 | HU-23 | Backend | Programar la consulta de avance: requerido, comprado, recibido y consumido por insumo | 3 | To Do | 25/10 | — |
| T-058 | HU-23 | Aceptación | Aceptar HU-23 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-057 |
| T-059 | HU-24 | Backend | Programar la consulta de lo que falta comprar por insumo | 2 | To Do | 25/10 | — |
| T-063 | HU-24 | Aceptación | Aceptar HU-24 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-062 |
| T-064 | HU-25 | Backend | Programar el registro del stock de seguridad y del punto de reposición, con histórico | 4 | To Do | 25/10 | — |
| T-067 | HU-25 | Aceptación | Aceptar HU-25 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-066 |
| T-068 | HU-26 | Backend | Programar el motor de alertas: una por insumo y marcada como atendida al comprar | 3 | To Do | 25/10 | — |
| T-072 | HU-26 | Aceptación | Aceptar HU-26 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-071 |
| T-073 | HU-27 | Backend | Programar el cálculo de la sugerencia de compra con su criterio | 3 | To Do | 25/10 | — |
| T-077 | HU-27 | Aceptación | Aceptar HU-27 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-076 |
| T-078 | HU-29 | Backend | Programar la búsqueda de proveedores por razón social o RUC | 2 | Done | 25/10 | — |
| T-082 | HU-29 | Aceptación | Aceptar HU-29 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-081 |
| T-083 | HU-30 | Backend | Programar el alta de proveedor rechazando RUC o DNI repetidos | 2 | Done | 25/10 | — |
| T-087 | HU-30 | Aceptación | Aceptar HU-30 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-086 |
| T-088 | HU-32 | Backend | Programar el registro de compra: comprobante único, base, IGV y total, sin mover stock | 5 | Done | 25/10 | — |
| T-092 | HU-32 | Aceptación | Aceptar HU-32 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-091 |
| T-093 | HU-35 | Backend | Programar la recepción total o parcial que genera el ingreso al kárdex | 4 | To Do | 25/10 | — |
| T-098 | HU-35 | Aceptación | Aceptar HU-35 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-097 |
| T-101 | HU-37 | Aceptación | Aceptar HU-37 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-100 |
| T-103 | HU-38 | Backend | Programar la idempotencia por uuid_cliente y devolver el motivo del rechazo | 4 | To Do | 25/10 | — |
| T-105 | HU-38 | Aceptación | Aceptar HU-38 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-104 |
| T-106 | HU-40 | Backend | Programar la salida: rechazar si supera el saldo y congelar el costo promedio | 7 | To Do | 25/10 | — |
| T-109 | HU-40 | Aceptación | Aceptar HU-40 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-108 |
| T-110 | HU-43 | Backend | Programar el cálculo del stock desde el libro de movimientos, por almacén y consolidado | 2 | To Do | 25/10 | — |
| T-115 | HU-43 | Aceptación | Aceptar HU-43 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-114 |
| T-116 | HU-45 | Backend | Programar el reverso que apunta al original, sin UPDATE ni DELETE | 3 | To Do | 25/10 | — |
| T-120 | HU-45 | Aceptación | Aceptar HU-45 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-119 |
| T-121 | HU-55 | Backend | Programar el costo promedio ponderado congelado en cada salida, sin IGV de factura (NIC 2) | 7 | To Do | 25/10 | — |
| T-124 | HU-55 | Aceptación | Aceptar HU-55 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-123 |
| T-125 | HU-57 | Backend | Programar la comparación del consumo contra lo planificado en el S10 | 5 | To Do | 25/10 | — |
| T-129 | HU-57 | Aceptación | Aceptar HU-57 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-128 |
| T-130 | HU-62 | Backend | Programar el registro de trabajadores: único por obra y baja con fecha | 2 | Done | 25/10 | — |
| T-134 | HU-62 | Aceptación | Aceptar HU-62 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-133 |
| T-135 | HU-63 | Backend | Programar la asistencia: una jornada por trabajador y fecha, corrección sin borrar | 4 | Done | 25/10 | — |
| T-138 | HU-63 | Aceptación | Aceptar HU-63 como Product Owner desde la interfaz y moverla a Done | 0.5 | To Do | 27/10 | T-137 |
| T-142 | — | Backend | Cargar en la base las semillas de los 626 proveedores reales | 1 | To Do | 25/10 | — |
| T-143 | — | DevOps | Configurar las pruebas automáticas en cada PR con GitHub Actions | 3 | To Do | 24/10 | — |
