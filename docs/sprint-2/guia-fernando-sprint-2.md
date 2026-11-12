# Sprint 2 · Qué le toca a Fernando (Scrum Master, integraciones, web y pruebas)

Actualizado: 12/11/2026. Fernando no trabaja del viernes en la noche al sábado en la tarde, así que lo de esos días se adelanta.

Tienes **32 tareas (52,5 h)**. Ya están en Done las 8 de diseño y la Planning.

## 1. Hoy (12/11)

1. **Subir la rama de diagramas:** `cd sevens-sgr` y luego `git push -u origin docs-diagramas` (sube 8dd6513 y 9099dd9). Abre el PR a `main`; lo aprueba Nicolás.
2. **Daily (15 min, con foto):** revisión de UML, migraciones (Adiel), reparto de la carga de Henry y orden de los contratos de API. Toma el **corte 1** del tablero.
3. **T-273 HU-31 · SUNAT en Integraciones** (vence hoy):
   - `GET /interno/ruc/{ruc}` con tiempo límite de unos 5 s;
   - devuelve razón social, estado y condición;
   - si no responde, el servicio de Compras deja el proveedor «por validar».
   - Con esto también se cierra **T-343 (D15)**.
4. **T-272:** revisa el diagrama de HU-31 → Done.
5. **T-078:** probar HU-17 cuando Henry termine el formulario (T-077).

## 2. Resto del sprint

| Fecha | Tareas |
|---|---|
| 13/11 | T-191 (probar HU-43) · **T-237 HT-03**: repositorio privado e invitar al docente como lector |
| 13/11 (antes del viernes en la noche) | T-159 (probar HU-35, también la app en modo avión) |
| 14/11 | T-207 (HU-55), T-231 (HU-63 en modo avión), T-349, T-352, T-356 (volver a probar HU-25, 19 y 12) |
| 14–15/11 | **Web**: T-318 + T-319 Inventario valorizado (pantalla 13) y T-325 + T-326 Costo de la obra (pantalla 12, reemplaza el prototipo de `Tablero.tsx`) |
| 17/11 | T-284 (HU-33), T-299 (HU-36: recibir más de lo requerido y más de lo comprado), T-313 (HU-44: cuadrar a mano una semana de 2 insumos) |
| Cada día | T-362 tablero por cortes, T-363 Daily con foto, T-364 burndown (en el Registro del Sprint 2) |
| 19/11 | Review (la conduce Adiel) y Retrospective con acta (T-366), **el mismo día** |

## 3. Como Scrum Master

- Vigilar el camino crítico: **HT-12 → HU-57 → HU-58**, y que HU-63 esté aceptada antes de HU-58.
- Que nadie tenga más de 2 tareas en Doing y que cada prueba la haga alguien distinto del autor.
- Que las aceptaciones de Adiel sean diarias. Hoy el burndown en puntos está en 0.
- Que cada PR de front-end enlace su pantalla del lienzo y cada tarea de desarrollo su diagrama.
