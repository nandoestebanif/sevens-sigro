# Sprint 2 · Qué le toca a Adiel (backend, base de datos y Product Owner)

Actualizado: 12/11/2026. Fuente: Sprint Backlog 2 (hoja Tareas), diagramas UML del Sprint 2 y pantallas del lienzo web cálido. El sprint cierra el **19/11**; todo en Done el **18/11**.

Tienes **34 tareas (40,5 h)**: 10 de construcción y 24 como Product Owner (aceptaciones, Review). Antes de pasar una tarea a Doing, abre su diagrama UML (regla nueva del Sprint 2) y anótalo en la bitácora.

- Diagramas: `Sprint 2/2. Diagramas por HU/HU-xx · …/` (y en el repo `docs/sprint-2/diagramas-hu/`).
- Pantallas: lienzo web https://claude.ai/artifact/UCjJFibwqCJGfbCC37RebT · app https://claude.ai/artifact/KTkoYe5QpwdimW6T7AuQFk
- Estados: `00 Documentos generales/5. Diagramas de estados/` (E06 proveedor, E07 compra y el nuevo E12 comprobante).

## 1. Orden de trabajo

| Día | Tarea | Qué | Por qué primero |
|---|---|---|---|
| **Jue 12/11** | **T-358** (HT-12) | Aplicar las migraciones 0011 y 0012 en Neon y dejar captura de `alembic current` = 0012 | Sin esto HU-57 no avanza y Henry no puede verificar (T-359) |
| Jue 12/11 | T-274 (HU-31) | Proveedor «por validar» si SUNAT no responde; alerta si no activo o no habido | Lo usa la compra (HU-32) |
| Jue 12–Vie 13/11 | T-288 + T-289 (HU-34) | Tabla y endpoints de comprobantes + ADR del almacenamiento | El objetivo del sprint dice «la compra con su comprobante» |
| Vie 13/11 | T-303 (HU-41) | Ingreso sin compra con origen y costo por unidad del insumo | Nicolás ya tiene la app lista |
| Vie 13/11 | T-244 (HT-06) | OpenAPI publicado en el ambiente de pruebas | Vence el 13/11; ya está en Doing |
| Vie 13/11 | T-264 (HT-09) | Revisar la migración 0009 (rol por servicio) | Vence el 13/11 |
| Sáb 14–Lun 16/11 | T-317 (HU-56) y T-324 (HU-58) | Inventario valorizado y tablero del gerente | HU-58 depende de HU-56, HU-57 y HU-63 |
| Lun 16–Mar 17/11 | T-211 + T-212 (HU-57) | Vista de desviación y su endpoint | Necesita la 0011/0012 aplicadas |
| **Cada día** | Aceptaciones (T-xxx «Aceptar HU-xx») | Acepta cada HU **apenas** su prueba pase, no todas el 18/11 | Acuerdo 1 de la Retrospective: el burndown en puntos sigue en 0 |

## 2. Detalle por tarea

### T-358 · Migraciones 0011 y 0012 en Neon (HT-12)
- Desde tu entorno local, con la conexión del dueño en variable de entorno: `alembic upgrade head`. **Nunca pegues la cadena de conexión en un chat ni en el repo.** No uses `infra/migrar.sh`.
- Evidencia: captura de `alembic current` (0012), de las tablas `partida` y `partida_recurso` y de los 3 índices de la 0011. Henry lo verifica (T-359).

### T-274 · HU-31 Validación de RUC
- Diagrama: HU-31 actividades/secuencia · Estados: E06.
- Reglas: el dígito verificador se valida siempre (también en la web, sin red). Integraciones consulta SUNAT con tiempo límite (propuesto 5 s).
  - SUNAT responde activo y habido → «validado» y autocompleta la razón social.
  - SUNAT responde no activo o no habido → «con alerta SUNAT»: **la compra se permite** y queda marcada para el residente (decisión propuesta, ver §3).
  - SUNAT no responde → «por validar», la compra sigue y se reintenta.
- Endpoint de reintento: `POST /api/v1/proveedores/{id}/validar`.

### T-288 + T-289 · HU-34 Comprobante adjunto
- Diagrama: HU-34 · Estados: E12 (adjunto → anulado; nunca se borra ni se reemplaza).
- Tabla `compras.comprobante`: id, compra_id, nombre, tipo_mime, tamaño, **sha256**, ubicación, subido_por, subido_en, anulado_por, anulado_en, motivo_anulacion.
- Endpoints: `POST /api/v1/obras/{id}/compras/{c}/comprobantes` (multipart) · `GET …/comprobantes` · `GET …/comprobantes/{a}` (descarga, compara la huella) · `POST …/comprobantes/{a}/anulacion` (motivo obligatorio).
- Validar el **tipo real** del archivo (firma del PDF/JPG/PNG), no solo la extensión; máximo **10 MB** (413 si pasa, 415 si el tipo no es admitido).
- **ADR (nuevo) · dónde se guarda el archivo.** El disco de Render se borra en cada despliegue, así que ahí no. Dos opciones:
  1. **En Neon** (columna `bytea` en su propia tabla): lo más rápido para este sprint, sin servicios nuevos; contra: ocupa el espacio de la base gratuita.
  2. **Almacenamiento de objetos compatible con S3** (p. ej. Cloudflare R2, con capa gratuita): lo correcto a largo plazo; contra: un servicio y un secreto más que configurar.
  Recomendación: opción 1 para el Sprint 2 con la tabla separada y la huella, y dejar escrita en el ADR la migración a la opción 2. El ADR es parte de T-288.

### T-303 · HU-41 Ingreso sin compra
- Diagrama: HU-41 (Nicolás lo actualizó según el código de la app).
- El costo unitario va **en la misma unidad que la cantidad** (S/ por m³, por bolsa, por varilla). Confirma con Nicolás qué unidad envía la app para el acero (kg o varilla) y documenta la regla en el endpoint.
- El ingreso queda distinguible de una recepción (tipo y origen) y recalcula el costo promedio ponderado.

### T-317 · HU-56 Inventario valorizado
- Diagrama: HU-56 · Pantalla: 13 Inventario valorizado.
- `GET /api/v1/obras/{id}/costo/inventario?almacen=todos|{a}&corte=AAAA-MM-DDTHH:MM`
- Valor = saldo a la fecha de corte × costo promedio ponderado vigente a esa fecha. Devuelve por insumo: cantidad, unidad, costo promedio, valor, % del total y fecha del último movimiento; totales por almacén y consolidado; la fecha de corte.
- Necesita en Almacén: `GET /interno/obras/{id}/saldos-valorizados?corte=`.

### T-324 · HU-58 Tablero de costo de la obra
- Diagrama: HU-58 · Pantalla: 12 Costo de la obra.
- `GET /api/v1/obras/{id}/costo/gasto?corte=` devuelve 5 cifras, cada una con **definición y la misma fecha de corte**:
  - comprado (compras sin IGV);
  - recibido (recepciones al costo de compra);
  - consumido (salidas al costo promedio);
  - en stock (= HU-56);
  - horas hombre (horas × tarifa, con las **horas extra aparte**).
- Control: si recibido − consumido ≠ en stock, marcar «cifras por conciliar».
- La respuesta incluye el texto de alcance: es gasto en recursos, no costo total (fuera gastos generales, subcontratos, servicios y planilla).
- Internos que necesita: `GET /interno/obras/{id}/comprado` (Compras), `/recibido-consumido` (Almacén, ya existe), `/horas` (Tareo).

### T-211 + T-212 · HU-57 Consumo contra lo planificado
- Diagrama: HU-57 · Pantalla: 04 Gasto en recursos.
- `GET /api/v1/obras/{id}/costo/desviacion?orden=importe`: desviación por insumo en **cantidad, importe y %**, comparada con el avance del plazo; los no planificados en una lista aparte; el aviso de que lo planificado incluye desperdicio y reserva.
- Observación sobre tu pantalla 04: hoy muestra la desviación solo en puntos («+2.5 pt»). Agrega cantidad e importe y el orden por importe para cumplir los criterios 2 y 5.

### Aceptaciones (Product Owner)
- Acepta desde la interfaz en el ambiente de pruebas, con la prueba hecha por alguien distinto del autor (DoD T8–T9), y mueve la tarjeta el mismo día.
- HU-63 (asistencia) la necesitas aceptada antes de HU-58.

## 3. Decisiones que te tocan como PO (antes del 13/11)

| # | Decisión | Propuesta ya dibujada en diagramas y pantallas |
|---|---|---|
| D11 | HU-36: ¿«exceder lo requerido» contra qué? | Contra el **requerimiento total del S10**. Lo comprado es un límite aparte que bloquea (HU-35). |
| D12 | HU-31: proveedor no activo o no habido en SUNAT | **Avisa, no bloquea**; la compra queda marcada para el residente |
| D13 | HU-33: precio fuera de lo habitual | Límite configurable (hoy ±10 %) contra el promedio ponderado de la obra; advertencia y nota **opcional** |
| D14 | HU-34: ¿el comprobante es obligatorio para registrar la compra? | La pantalla lo exige (al menos uno). Si no lo quieres obligatorio, se quita esa validación |
| D15 | HU-34: dónde se guarda el archivo | ADR: Neon (`bytea`) en este sprint; S3/R2 después |

Anótalas en el Product Backlog (hoja de decisiones) y en el Registro del Sprint 2 cuando las confirmes.

## 4. Tu carga y riesgos
- 40,5 h estimadas: eres quien tiene más holgura. Si la Daily lo decide, puedes tomar tareas de backend de Nicolás o de web de Henry (Henry tiene 70 h).
- El camino crítico pasa por ti: **HT-12 → HU-57 → HU-58**. Si HU-57 se atrasa, el tablero (HU-58) se demuestra con lo que esté en Done.
