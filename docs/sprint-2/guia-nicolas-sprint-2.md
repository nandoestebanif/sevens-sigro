# Sprint 2 · Qué le toca a Nicolás (app, backend y pruebas)

Actualizado: 12/11/2026. Fuentes: el Sprint Backlog 2 (hoja Tareas), los diagramas UML del Sprint 2 y los lienzos. El sprint cierra el **19/11**; todo tiene que estar en Done el **18/11**.

Tienes **31 tareas (59,5 h)**:

- 6 de la app;
- 7 de backend (3 de HU nuevas y 4 defectos);
- 3 de web (defectos);
- 12 pruebas;
- 3 de UML.

**Dónde está todo**
- App: https://claude.ai/artifact/KTkoYe5QpwdimW6T7AuQFk (2.3 Entrada ya dice «aviso sobre lo requerido», 2.4 Ingreso sin compra dice «costo por m³»).
- Web: https://claude.ai/artifact/UCjJFibwqCJGfbCC37RebT
- Diagramas: `Sprint 2/2. Diagramas por HU/`.

## 1. Lo urgente (12/11)

1. **T-367 (D18, 408/429)**:
   - el PR #44 ya está en `app-mejoras-tecnicas`;
   - abre el PR `app-mejoras-tecnicas → main` (debe aprobarlo alguien distinto de ti);
   - corre `./gradlew :composeApp:testDebugUnitTest`.

   Con eso y la prueba en modo avión pasa a Done.
2. **T-302 (UML de HU-41)**: pasa a Done cuando Fernando suba la rama `docs-diagramas`.
3. **T-287 y T-316**: revisa los diagramas de HU-34 y HU-56 (ya están hechos) → Done.
4. **T-150**: probar HU-32 venció el 11/11. Hazla apenas Henry conecte Compras (T-149).
5. **Unidad del costo en HU-41**: confirma con Adiel si el acero va en kg o en varilla. La app debe enviar el costo en la **misma unidad que la cantidad**.

## 2. Backend (contratos acordados para que Henry programe en paralelo)

### T-281 · HU-33 Precio contra el promedio
- `GET /api/v1/obras/{id}/compras/precio-referencia?insumo=` devuelve `promedio_ponderado` y `limite_pct`.
- El promedio es el **ponderado pagado en la obra**:
  - se recalcula con cada recepción;
  - nunca se edita;
  - el límite es configurable y hoy vale 10 %.
- La compra guarda el **precio pagado**. Si se sale del límite, guarda la marca `fuera_de_lo_habitual` y la nota opcional.

### T-296 · HU-36 Exceso sobre lo requerido
- `POST /api/v1/obras/{id}/almacen/recepciones/verificacion` devuelve, por insumo, `requerido` (requerimiento total del S10, HU-19), `ya_recibido`, `con_esta` y `exceso`.
- En `POST /recepciones`, el servidor **vuelve a calcular**:
  - recibir más de lo comprado → 422 (HU-35);
  - exceso sobre lo requerido sin `motivo_exceso` → 422;
  - con motivo, se guarda y queda marcado en el kárdex.

### T-310 · HU-44 Movimiento semanal
- `GET /api/v1/obras/{id}/almacen/movimiento-semanal?semana=n[&formato=csv]`
- Cálculo por insumo:
  - saldo inicial = cierre de la semana anterior;
  - entradas = recepciones + ingresos sin compra;
  - salidas;
  - saldo final.
- Solo los insumos con movimiento. Los reversos cuentan en la semana en que se hicieron. Devuelve `estado_semana` (cerrada o en curso).
- Es un reporte derivado del kárdex: no lleva tabla nueva.

### Defectos (HT-11)
- T-335 (D07): nivel máximo.
- T-337 (D09): duplicado sin distinguir mayúsculas ni espacios.
- T-338 (D10): requerimiento en unidad de compra.
- T-341 (D13): «comprado» sale 0.

El D13 es importante: sin él, HU-23 y el tablero muestran comprado en cero.

## 3. App

| Tarea | Pantalla | Regla clave |
|---|---|---|
| T-157 HU-35 | 2.3 Entrada | Propone la recepción total; no deja pasar lo comprado |
| T-298 HU-36 | 2.3 Entrada | Aviso **antes** de guardar si se pasa lo requerido, con motivo (3 opciones); «recibir solo hasta lo requerido» |
| T-189 HU-43 | 2.7 Stock | Sin señal muestra la copia con «actualizado a las HH:MM»; la app nunca calcula el saldo |
| T-230 HU-63 | 5.3 Asistencia | Presente / ausente / permiso, horas normales y extra aparte, `uuid_cliente`, cola sin señal; 409 → «use corrección» |
| T-304 + T-305 HU-41 | 2.4 Ingreso sin compra | Origen obligatorio y costo por unidad; también sin señal |

Criterio de Done de front-end app:
- PR fusionado;
- comparado con su pantalla;
- lo que registra movimientos, probado en **modo avión** por alguien distinto de ti, con la guía de 12 casos.

## 4. Pruebas que te tocan (siempre de trabajo ajeno)

- 14/11: T-199 (HU-45).
- 14/11: T-346, T-347 y T-355 (volver a probar HU-01, 03 y 29).
- 17/11: T-094 (HU-23), T-215 (HU-57), T-277 (HU-31 con SUNAT disponible y sin respuesta), T-292 (HU-34 con un PDF, una foto y un archivo no permitido) y T-327 (HU-58, cuadrar cada cifra).
- T-333 (D05): una segunda importación del S10 no cambia ningún saldo.

## 5. Tu carga
- 59,5 h, unas 7,4 h por día.
- Si te atrasas, lo primero que puede pasar a Adiel es el backend de HU-44 (T-310). Se decide en la Daily.
