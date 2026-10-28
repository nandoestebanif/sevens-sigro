# Víctor Adiel Luján · Product Owner · Backend

**Reparto del 22/10:** eres el Product Owner y llevas **todo el backend** (incluidos almacén y costo, que antes eran de Henry) y las migraciones: solo tú las creas. Tu carpeta es `api/`.

## Lo primero

1. **Como PO, hoy:** recortar el alcance del Sprint 1 con el equipo manteniendo el objetivo (la carga no cabe al 27/10) y decidir HU-45 vs la matriz (¿el residente autoriza el reverso?).
2. **T-235 · HT-02** (CI en cada PR) y **HT-07** (cargar proveedores, catálogo y la obra Multifamiliar Flores en la base de pruebas).
3. El corazón del almacén: `movimiento` append-only con trigger sin UPDATE/DELETE, idempotencia por `uuid_cliente` (HU-38), salida con costo congelado (HU-40), recepción que genera el ingreso (HU-35), saldo derivado (HU-43), reverso (HU-45) y costo promedio sin IGV de factura (HU-55, NIC 2).

## Tus tareas del Sprint 1 · 106 tareas · 117 h pendientes

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

**Como PO** aceptas cada HU desde la interfaz con su DoD (tareas «Aceptar … como Product Owner»): es lo único que mueve la velocidad. Tu carga es la más alta del equipo; si no entra, se recorta alcance, no se baja la DoD.

## Cómo trabajas (igual para los cuatro)

- Lee primero `CLAUDE.md` (reglas de dominio) y `docs/scrum/tablero-sprint-1.md` (reglas del tablero).
- Tus tareas están en `docs/scrum/SIGRO - Sprint 1 · Registro del Sprint.xlsx`, hoja «Tablero». Al moverlas:
  **To Do → Doing** solo si la HU cumple la DoR (L1–L12); **Doing → Done** con su criterio cumplido y la evidencia (PR o enlace).
  Máximo **2 tareas en Doing**. Anota la fecha.
- Cada día, en la hoja «Bitácora de trabajo»: una fila por actividad — fecha · hora · qué hiciste · T-xxx.
- Una rama por tarea o HU (`hu-40-salida-app`), nada directo a `main`, PR pequeño que diga qué HU y qué condiciones finales cubre.
  Lo aprueba alguien distinto de ti. **Sube tu trabajo aunque esté incompleto**: lo que no está en el repo no existe para la Review.
- **Lunes 27/10: todo en Done. Martes 28/10: Review.**
