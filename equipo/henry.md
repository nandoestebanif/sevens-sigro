# Henry Ticona · Developer · Web (con Fernando)

**Cambio del 22/10:** pasas del backend a la **web**. El backend completo (incluidos almacén y costo) lo
lleva **Adiel**, que ya tiene la API en `main` con sus pruebas. Tu trabajo es que la web deje de usar
datos simulados y hable con esa API real, y que las pantallas de administración existan.

Tu carpeta es `web/`. Fuera de ahí, solo con Pull Request y aviso.

## Lo primero: T-140 · el ClienteHttp (desbloquea 8 tareas tuyas y varias de Fernando)

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

### Programar (web)

| ID | HU | Tarea | h | Límite | Depende de |
|---|---|---|--:|---|---|
| T-140 | — | Programar el ClienteHttp (snake_case ↔ camelCase, errores `{codigo, mensaje}`) | 4 | **cuanto antes** | — |
| T-023 | HU-12 | Programar la pantalla web de login y el manejo de la sesión | 3 | 26/10 | T-140 |
| T-028 | HU-13 | Programar la pantalla web para crear usuarios, asignar su rol y darlos de baja | 4 | 26/10 | T-140 |
| T-032 | HU-14 | Programar la pantalla web para asignar usuarios a obras | 2 | 26/10 | T-140 |
| T-037 | HU-15 | Programar la pantalla web para autorizar o cambiar el celular de la obra | 3 | 26/10 | T-140 |
| T-080 | HU-29 | Conectar la pantalla de proveedores con la API real | 2 | 26/10 | T-078, T-140 |
| T-085 | HU-30 | Conectar el alta de proveedor con la API real | 2 | 26/10 | T-083, T-140 |
| T-090 | HU-32 | Conectar la pantalla de compras con la API real | 5 | 26/10 | T-088, T-140 |
| T-095 | HU-35 | Conectar la recepción con la API real | 4 | 26/10 | T-093, T-140 |
| T-112 | HU-43 | Conectar la pantalla de stock y kárdex con la API real | 2 | 26/10 | T-110, T-140 |
| T-118 | HU-45 | Conectar el reverso con la API real | 3 | 26/10 | T-116, T-140 |
| T-122 | HU-55 | Mostrar la salida valorizada en el kárdex y en el reporte web | 7 | 26/10 | — |
| T-127 | HU-57 | Conectar la pantalla de desviación con la API real | 5 | 26/10 | T-125, T-140 |
| T-152 | — | Modelar en UML la secuencia del login y del permiso verificado en el servidor (HU-12 a HU-15) | 1.5 | 24/10 | — |

Las pantallas de HU-12 a HU-15 ya están diseñadas: **pantallas 10 y 20 del lienzo** «SIGRO · Pantallas y
diagramas del Sprint 1» (pídele el enlace a Fernando). Usa los componentes de `web/src/ui/base.tsx` y los
tokens de `web/src/tokens.css` para que el modo oscuro y el panel de accesibilidad sigan funcionando.

### Probar y aprobar PR (17 HU donde tú no programas nada)

Pruebas en el **ambiente de pruebas**, contra cada **condición final** de la tarjeta 3 de la HU (Excel
«Product Backlog y Sprint Backlog», hoja 2), y luego apruebas el PR: **límite 27/10**, 1 h cada una.

T-004 HU-01 · T-009 HU-03 · T-015 HU-07 · T-020 HU-08 · T-043 HU-16 · T-047 HU-17 · T-052 HU-19 ·
T-057 HU-23 · T-062 HU-24 · T-066 HU-25 · T-071 HU-26 · T-076 HU-27 · T-100 HU-37 · T-104 HU-38 ·
T-108 HU-40 · T-133 HU-62 · T-137 HU-63

Las HU donde tú programas (12 a 15, 29, 30, 32, 35, 43, 45, 55 y 57) las prueba Fernando: nadie prueba su propio código.

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
