# Sprint 2 · Qué le toca a Henry (web y pruebas)

Actualizado: 12/11/2026. Fuentes: el Sprint Backlog 2 (hoja Tareas), los diagramas UML del Sprint 2 y las pantallas del lienzo web cálido. El sprint cierra el **19/11**; todo tiene que estar en Done el **18/11**.

Tienes **37 tareas (70 h)**, la carga más alta del equipo. En la Daily del 12/11 se propone pasar a Adiel tus 3 defectos de backend (ver §4).

**Dónde está todo**
- Pantallas: https://claude.ai/artifact/UCjJFibwqCJGfbCC37RebT (lienzo web cálido de Adiel). Cada pantalla web se programa **igual al lienzo** y en el PR se compara con él (regla nueva).
- Diagramas: `Sprint 2/2. Diagramas por HU/` y en el repo `docs/sprint-2/diagramas-hu/`.

**Antes de pasar una tarea a Doing**
- Revisa su diagrama y anótalo en la bitácora.
- Máximo 2 tareas en Doing a la vez.

**El backend que necesitas aún no existe.** Programa la pantalla contra el **contrato del endpoint** (abajo) con datos simulados. Cuando Adiel o Nicolás fusionen su PR, solo cambias la conexión (tus tareas «Conexión API»).

## 1. Orden de trabajo

| Día | Tareas | Qué |
|---|---|---|
| **Jue 12/11** | T-280, T-295, T-309, T-323 | Revisar los 4 diagramas UML que te tocan (ya están hechos: léelos contra la pantalla y el criterio; si algo no cuadra, avisa) → Done |
| Jue 12/11 | T-359 | Cuando Adiel aplique la 0011/0012: captura de `alembic current` = 0012, tablas de partidas y los 3 índices |
| Jue 12/11 | T-077 | Formulario del factor de conversión (HU-17), pantalla 10 Catálogo |
| Jue 12/11 | T-149 | Conectar Compras con la API real (venció el 11/11) |
| Vie 13/11 | T-158, T-190 | Conectar Recepción y Stock/kárdex con la API |
| Vie 13/11 | T-331, T-339, T-340, T-344, T-345 | Defectos web de HT-11 (D03, D11, D12, D16, D17) |
| Sáb 14/11 | T-275, T-282, T-290, T-297 | Pantalla 07b (estados SUNAT, precio contra el promedio, comprobantes) y 07c (aviso sobre lo requerido) |
| Sáb 14/11 | T-311, T-206, T-198 | Stock semana a semana (pantalla 14), salida valorizada en el kárdex, reverso |
| Dom 15/11 | T-276, T-283, T-291, T-312 | Conexiones API de HU-31, 33, 34 y 44 |
| Lun 16–Mar 17/11 | T-093, T-214, T-306, T-320 | Conexión de avance (HU-23) y desviación (HU-57); probar HU-41 (app en modo avión) y HU-56 |
| Vie 13–Sáb 14/11 | T-348, T-350, T-351, T-353, T-354 | Volver a probar HU-07, 08, 19, 24 y 29 con evidencia |

## 2. Contratos de los endpoints que vas a consumir

Todos van con `Authorization: Bearer <JWT>` y el prefijo `/api/v1`. Los errores vuelven con `detail` y se muestran junto al campo.

| HU | Endpoint | Respuesta (lo que pinta la pantalla) |
|---|---|---|
| HU-31 | `POST /proveedores` · `POST /proveedores/{id}/validar` | `estado_sunat`: `validado` · `por_validar` · `con_alerta`, `consultado_en` |
| HU-33 | `GET /obras/{id}/compras/precio-referencia?insumo=` | `promedio_ponderado`, `limite_pct` (hoy 10) |
| HU-34 | `POST /obras/{id}/compras/{c}/comprobantes` (multipart) · `GET …/comprobantes` · `GET …/comprobantes/{a}` · `POST …/comprobantes/{a}/anulacion` | lista con nombre, tipo, tamaño, subido_por, subido_en, anulado (motivo) |
| HU-36 | `POST /obras/{id}/almacen/recepciones/verificacion` | por insumo: `requerido`, `ya_recibido`, `con_esta`, `exceso` |
| HU-35/36 | `POST /obras/{id}/almacen/recepciones` | 422 si recibe más de lo comprado; 422 si hay exceso sobre lo requerido **sin** `motivo_exceso` |
| HU-44 | `GET /obras/{id}/almacen/movimiento-semanal?semana=16[&formato=csv]` | filas (insumo, inicial, entradas, salidas, final) y `estado_semana` |
| HU-23 | `GET /obras/{id}/avance` | requerido, comprado, recibido, consumido, saldo por comprar y los 3 %, en unidad de compra |
| HU-57 | `GET /obras/{id}/costo/desviacion?orden=importe` | desviación en cantidad, importe y %; `no_planificados` aparte |

## 3. Reglas que la pantalla tiene que respetar

- **07b Registrar la compra**
  - Proveedor «con alerta SUNAT»: **avisa, no bloquea**.
  - Precio fuera del límite: advertencia y nota **opcional**. Se envía el precio pagado, nunca el promedio.
  - Comprobante: PDF, JPG o PNG de hasta 10 MB. Un comprobante no se borra: se **anula** con motivo.
- **07c Recepción**
  - El campo no deja pasar lo **comprado** (HU-35, bloquea).
  - Si se pasa lo **requerido en el S10**, el aviso aparece **antes** de confirmar y pide motivo (HU-36). El servidor vuelve a validar.
- **14 Stock semana a semana**
  - Vista por defecto: una semana con los insumos que se movieron (criterio 3).
  - Las semanas cerradas no cambian.
  - Exportación en CSV (se abre en Excel).
- **Criterio de Done de front-end web:** PR fusionado, build sin errores, modo claro/oscuro, escala al 125 % y comparado con su pantalla del lienzo.

## 4. Propuesta para tu carga (se decide en la Daily del 12/11)

- Pasar a Adiel **T-329, T-330 y T-332** (defectos de backend D01, D02 y D04, 6 h). Son de su área y él tiene 40,5 h. Tú quedarías en 64 h.
- Las pantallas de Inventario (HU-56) y Costo de la obra (HU-58) ya son de Fernando (T-318, T-325).
