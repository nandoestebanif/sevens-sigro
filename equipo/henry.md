# Henry Ticona · Developer · Web (con Fernando)

**Cambio del 22/10:** pasas del backend a la **web**. El backend completo (incluidos almacén y costo) lo
lleva **Adiel**, que ya tiene la API en `main` con sus pruebas. Tu trabajo es que la web deje de usar
datos simulados y hable con esa API real, y que las pantallas de administración existan.

Tu carpeta es `web/`. Fuera de ahí, solo con Pull Request y aviso.

## Lo primero: T-234 · el ClienteHttp (desbloquea 8 tareas tuyas y varias de Fernando)

Hoy toda la web pasa por una sola interfaz, `ClienteSigro` (`web/src/api/cliente.ts`), implementada por
`ClienteSimulado`. **No toques las pantallas para conectarlas:** escribe una segunda implementación,
`ClienteHttp`, en `web/src/api/http.ts`, y cambia cuál se exporta en `api`.

| Tema | Contrato |
|---|---|
| Base | `VITE_API_URL` + `/api/v1` (ej. `http://localhost:8000/api/v1` en local, la URL del ambiente de pruebas después) |
| Sesión | `POST /auth/login` → token; mándalo en `Authorization: Bearer …`; `GET /auth/yo` dice quién es y su rol |
| Campos | La API usa `snake_case`, la web `camelCase`: convierte en `http.ts`, en un solo lugar |
| Errores | La API responde `{codigo, mensaje}`: conviértelo en `ErrorApi(codigo, mensaje)`, que es lo que las pantallas ya muestran |
| Obra activa | Casi todo cuelga de `/obras/{id}/…`: usa el id de la obra elegida abajo a la izquierda |
| Regla 9 | Si la API dice 403, se muestra el mensaje; **nunca** se oculta solo el botón como control de acceso |

Los endpoints están en `api/LEEME.md` (sección «Endpoints»). Lo que la API todavía no tiene (almacén,
costo, reposición) sigue en `ClienteSimulado` hasta que Adiel lo entregue: un `ClienteHttp` que delegue
esos métodos al simulado es válido mientras tanto.

## Tus tareas del Sprint 1

### Modelar y programar

| ID | HU | Fase | Tarea | h | Límite | Depende de |
|---|---|---|---|--:|---|---|
| T-039 | HU-12 | Front-end web | Programar la pantalla web de login y el manejo de la sesión | 3 | 26/10 | T-035 |
| T-044 | HU-13 | Modelado UML | Modelar la funcionalidad de HU-13 con el diagrama UML de secuencia de la verificación del permiso en el servidor | 1 | 24/10 | T-043 |
| T-047 | HU-13 | Front-end web | Programar la pantalla web para crear usuarios, asignar su rol y darlos de baja | 4 | 26/10 | T-043 |
| T-051 | HU-14 | Modelado UML | Modelar la funcionalidad de HU-14 con el diagrama UML de secuencia de la asignación de usuario a obra | 1 | 24/10 | T-050 |
| T-054 | HU-14 | Front-end web | Programar la pantalla web para asignar usuarios a obras | 2 | 26/10 | T-050 |
| T-062 | HU-15 | Front-end web | Programar la pantalla web para autorizar o cambiar el celular de la obra | 3 | 26/10 | T-057 |
| T-129 | HU-29 | Modelado UML | Modelar la funcionalidad de HU-29 con el diagrama UML de secuencia de la búsqueda de proveedores | 1 | 24/10 | T-128 |
| T-133 | HU-29 | Conexión API | Conectar la pantalla de proveedores con la API real | 2 | 26/10 | T-131, T-132, T-234 |
| T-137 | HU-30 | Modelado UML | Modelar la funcionalidad de HU-30 con el diagrama UML de secuencia del alta rápida de proveedor | 1 | 24/10 | T-136 |
| T-141 | HU-30 | Conexión API | Conectar el alta de proveedor con la API real | 2 | 26/10 | T-139, T-140, T-234 |
| T-145 | HU-32 | Modelado UML | Modelar la funcionalidad de HU-32 con el diagrama UML de secuencia del registro de la compra | 1 | 24/10 | T-144 |
| T-149 | HU-32 | Conexión API | Conectar la pantalla de compras con la API real | 5 | 26/10 | T-147, T-148, T-234 |
| T-158 | HU-35 | Conexión API | Conectar la recepción con la API real | 4 | 26/10 | T-155, T-156, T-234 |
| T-190 | HU-43 | Conexión API | Conectar la pantalla de stock y kárdex con la API real | 2 | 26/10 | T-186, T-188, T-234 |
| T-194 | HU-45 | Modelado UML | Modelar la funcionalidad de HU-45 con el diagrama UML de estados del movimiento (vigente → revertido) | 1 | 24/10 | T-193 |
| T-198 | HU-45 | Conexión API | Conectar el reverso con la API real | 3 | 26/10 | T-196, T-197, T-234 |
| T-206 | HU-55 | Front-end web | Mostrar la salida valorizada en el kárdex y en el reporte web | 7 | 26/10 | T-201 |
| T-210 | HU-57 | Modelado UML | Modelar la funcionalidad de HU-57 con el diagrama UML de actividades de la comparación contra lo planificado | 1 | 24/10 | T-209 |
| T-214 | HU-57 | Conexión API | Conectar la pantalla de desviación con la API real | 5 | 26/10 | T-212, T-213, T-234 |
| T-234 | — | Web | Programar el ClienteHttp para que la web hable con la API real (snake_case ↔ camelCase, errores {codigo, mensaje}) | 4 | 26/10 | — |

Las pantallas de HU-12 a HU-15 ya están diseñadas: **pantallas 10 y 20 del lienzo** «SIGRO · Pantallas y
diagramas del Sprint 1» (pídele el enlace a Fernando). Usa los componentes de `web/src/ui/base.tsx` y los
tokens de `web/src/tokens.css` para que el modo oscuro y el panel de accesibilidad sigan funcionando.

### Probar la funcionalidad y aprobar el PR (17 HU donde tú no programas nada)

Pruebas los endpoints con **Postman** y la interfaz en el **ambiente de pruebas**, contra cada **condición final** de la tarjeta 3 de la HU (Excel «Product Backlog y Sprint Backlog», hoja 2), y luego apruebas el PR: **límite 27/10**, 1 h cada una.

T-008 HU-01 · T-016 HU-03 · T-025 HU-07 · T-033 HU-08 · T-071 HU-16 · T-078 HU-17 · T-086 HU-19 · T-094 HU-23 · T-102 HU-24 · T-109 HU-25 · T-118 HU-26 · T-126 HU-27 · T-165 HU-37 · T-173 HU-38 · T-181 HU-40 · T-223 HU-62 · T-231 HU-63

Las HU donde tú programas (12, 13, 14, 15, 29, 30, 32, 35, 43, 45, 55, 57) las prueba Fernando: nadie prueba su propio código.

## Cómo trabajas

- Una rama por tarea o por HU: `hu-29-proveedores-api`. Nada directo a `main`.
- PR pequeño, que diga **qué HU y qué condiciones finales** cubre. Lo aprueba alguien distinto de ti.
- Mueve tu tarjeta en el tablero **en el Daily**: To Do → Doing solo si la HU cumple la DoR; máximo 2 en Doing.
- Antes de pedir revisión: `npm run build` sin errores y la pantalla probada en modo claro, oscuro y con escala 125 %.

## Lo que no puedes perder de vista

- La web **no calcula** saldos, costos ni estados: los muestra como los devuelve la API.
- Un servicio caído **no bloquea**: mensaje claro de qué pasó y qué hacer (el patrón ya está en las pantallas).
- Nada de secretos en el frontend: ni el token de Factiliza ni el de servicio. Solo `VITE_API_URL`.
- Textos cortos: título y una línea; el detalle va en una ayuda «?» (tarea T-147 de Fernando).

## Dónde está todo

| Qué | Dónde |
|---|---|
| Tablero completo y reglas | `docs/scrum/tablero-sprint-1.md` y el Excel del tablero en `docs/scrum/` |
| Historias y criterios | `docs/historias-sprint-1.md` |
| Endpoints de la API | `api/LEEME.md` |
| Permisos por rol | `docs/permisos.md` |
| DoR y DoD | `CLAUDE.md` y la hoja «3. Criterios DoR y DoD» del Excel del backlog |
