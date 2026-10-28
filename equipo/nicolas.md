# Nicolás Pachamora · Developer · App Android

**Reparto del 22/10:** construyes la app del almacenero —lo único que se usa parado en la obra, con polvo y sin señal— y los diagramas del negocio y la arquitectura. Tu carpeta es `android/`. El backend lo lleva Adiel (API en `main`, endpoints en `api/LEEME.md`).

## Lo primero

1. **T-242 · HT-05 · proyecto base de la app**: Compose, Room, WorkManager, sesión y cliente de la API. Destraba todas tus pantallas. Parte de la plantilla oficial de Compose.
2. HU-37 y HU-38 (cola local con `uuid_cliente` y envío al volver la señal): es la pieza más difícil y la que más importa. La secuencia ya está modelada en el lienzo.
3. HU-40 (salida) sobre esa cola; después recepción (HU-35), stock (HU-43), trabajadores (HU-62) y asistencia (HU-63).

## Tus tareas del Sprint 1 · 19 tareas · 61 h pendientes

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

**Lo que no puedes perder de vista:** la app **nunca calcula el saldo** (lo devuelve el servidor); cada movimiento nace con su `uuid_cliente`; lo que registra movimientos se prueba **en modo avión y recuperando la señal** (T7); botones grandes, una acción por pantalla, contraste AA. Las pantallas están diseñadas en el lienzo (pantallas 1 a 8b y la variante A, industrial, para la salida).

## Cómo trabajas (igual para los cuatro)

- Lee primero `CLAUDE.md` (reglas de dominio) y `docs/scrum/tablero-sprint-1.md` (reglas del tablero).
- Tus tareas están en `docs/scrum/SIGRO - Sprint 1 · Registro del Sprint.xlsx`, hoja «Tablero». Al moverlas:
  **To Do → Doing** solo si la HU cumple la DoR (L1–L12); **Doing → Done** con su criterio cumplido y la evidencia (PR o enlace).
  Máximo **2 tareas en Doing**. Anota la fecha.
- Cada día, en la hoja «Bitácora de trabajo»: una fila por actividad — fecha · hora · qué hiciste · T-xxx.
- Una rama por tarea o HU (`hu-40-salida-app`), nada directo a `main`, PR pequeño que diga qué HU y qué condiciones finales cubre.
  Lo aprueba alguien distinto de ti. **Sube tu trabajo aunque esté incompleto**: lo que no está en el repo no existe para la Review.
- **Lunes 27/10: todo en Done. Martes 28/10: Review.**
