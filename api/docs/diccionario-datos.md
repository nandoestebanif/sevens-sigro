# Diccionario de datos · SIGRO (por microservicio)

> Generado por `api/docs/generar_diccionario.py` desde la base migrada (PostgreSQL 16.15, migración `0009`). **No editar a mano:** se regenera. 23 tablas, 191 columnas, 48 claves foráneas (24 entre servicios), 6 vistas.

Convenciones: **FK\*** = clave foránea que apunta a la tabla de otro servicio (deuda de la fase 1, ver al final). Permisos: S select · I insert · U update · D delete. Ningún servicio tiene D: nada se borra (reglas 1 y 10).

## Resumen por servicio

| Microservicio | Esquema | Usuario → rol | Tablas | FK hacia otros servicios | Qué guarda |
|---|---|---|---|---|---|
| **obras** | `obra` | `sigro_obras` → `sigro_svc_obras` | 7 | 0 | identidad, sesión, usuarios, obras, periodos, almacenes, asignaciones, dispositivos; decide quién puede qué |
| **catalogo** | `catalogo` | `sigro_catalogo` → `sigro_svc_catalogo` | 3 | 2 | código único de insumo, nombres comerciales, factor de conversión, importación del S10 |
| **compras** | `compras` | `sigro_compras` → `sigro_svc_compras` | 10 | 17 | proveedores, compras, solicitudes, requerimiento y reposición |
| **tareo** | `tareo` | `sigro_tareo` → `sigro_svc_tareo` | 2 | 4 | trabajadores y asistencia (horas hombre) |
| **relevo** | `outbox` | `sigro_relevo` → `sigro_svc_relevo` | 1 | 1 | bandeja de salida (outbox): entrega los eventos a Notificaciones |
| **almacen** · **costo** | `almacen` · `costo` | por crear | — | — | movimientos, saldo, kárdex, costo promedio (por construir) |

## Microservicio `obras` · esquema `obra`

Identidad, sesión, usuarios, obras, periodos, almacenes, asignaciones, dispositivos; decide quién puede qué.

### `obra.almacen` · Almacén

- **Tipo:** maestra / transaccional (no se borra)
- **Permisos:** `sigro_svc_obras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `proyecto_id` | bigint | no |  | FK | Referencia a Obra (obra.proyecto) |
| `nombre` | text | no |  |  | Nombre |
| `tipo` | text | no | 'frente'::text |  | Tipo: central / frente |
| `estado` | text | no | 'activo'::text |  | Estado: activo / inactivo |
| `creado_en` | timestamptz | no | now() |  | Momento en que se registró (hora del servidor) |

**Únicos:** `(id, proyecto_id)` · `(proyecto_id, lower(nombre))`

**CHECK:**
- `ck_almacen_estado`: `(estado = ANY (ARRAY['activo'::text, 'inactivo'::text]))`
- `ck_almacen_nombre`: `(btrim(nombre) <> ''::text)`
- `ck_almacen_tipo`: `(tipo = ANY (ARRAY['central'::text, 'frente'::text]))`

**Claves foráneas:**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION

**Disparadores:** `tg_obra_almacen_no_borrar`, `tg_obra_almacen_no_truncar`

### `obra.asignacion` · Asignación

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Una vigente por persona y obra · aquí vive el rol
- **Permisos:** `sigro_svc_obras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `usuario_id` | bigint | no |  | FK | Referencia a Usuario (obra.usuario) |
| `proyecto_id` | bigint | no |  | FK | Referencia a Obra (obra.proyecto) |
| `rol` | text | no |  |  | Rol: almacenero / SSOMA / residente / administrador / gerente |
| `asignado_por` | bigint | no |  | FK | Referencia a Usuario (obra.usuario) |
| `fecha_asignacion` | timestamptz | no | now() |  | Desde |
| `fecha_retiro` | timestamptz | sí |  |  | Hasta |
| `retirado_por` | bigint | sí |  | FK | Referencia a Usuario (obra.usuario) |

**Únicos:** `(usuario_id, proyecto_id)` si `fecha_retiro IS NULL`

**CHECK:**
- `ck_asignacion_retiro`: `(((fecha_retiro IS NULL) AND (retirado_por IS NULL)) OR ((fecha_retiro IS NOT NULL) AND (retirado_por IS NOT NULL) AND (fecha_retiro >= fecha_asignacion)))`
- `ck_asignacion_rol`: `(rol = ANY (ARRAY['almacenero'::text, 'ssoma'::text, 'residente'::text, 'administrador'::text, 'gerente'::text]))`

**Claves foráneas:**
- `(asignado_por)` → `obra.usuario(id)` · al borrar: NO ACTION
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION
- `(retirado_por)` → `obra.usuario(id)` · al borrar: NO ACTION
- `(usuario_id)` → `obra.usuario(id)` · al borrar: NO ACTION

**Índices:** `ix_asignacion_proyecto_id_rol (proyecto_id, rol) WHERE fecha_retiro IS NULL`

**Disparadores:** `tg_asignacion_cambios`, `tg_asignacion_retira_dispositivo`, `tg_obra_asignacion_no_borrar`, `tg_obra_asignacion_no_truncar`

### `obra.dispositivo` · Dispositivo (celular)

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** 1 activo por responsable · máx. 2 por obra
- **Permisos:** `sigro_svc_obras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `proyecto_id` | bigint | no |  | FK | Referencia a Obra (obra.proyecto) |
| `usuario_id` | bigint | no |  | FK | Referencia a Usuario (obra.usuario) |
| `identificador` | text | no |  |  | Identificador |
| `estado` | text | no | 'pendiente'::text |  | Estado: pendiente / activo / revocado |
| `solicitado_en` | timestamptz | no | now() |  | Solicitado en |
| `autorizado_por` | bigint | sí |  | FK | Referencia a Usuario (obra.usuario) |
| `fecha_autorizacion` | timestamptz | sí |  |  | Autorizado en |
| `revocado_por` | bigint | sí |  | FK | Referencia a Usuario (obra.usuario) |
| `fecha_revocacion` | timestamptz | sí |  |  | Revocado en |
| `motivo_revocacion` | text | sí |  |  | Motivo de revocación |

**Únicos:** `(proyecto_id, identificador)` si `estado = 'activo'` · `(proyecto_id, usuario_id)` si `estado = 'activo'` · `(id, proyecto_id)`

**CHECK:**
- `ck_dispositivo_autorizacion`: `(((estado = 'pendiente'::text) AND (autorizado_por IS NULL) AND (fecha_autorizacion IS NULL)) OR ((estado = 'activo'::text) AND (autorizado_por IS NOT NULL) AND (fecha_autorizacion IS NOT NULL)) OR (estado = 'revocado'::text))`
- `ck_dispositivo_estado`: `(estado = ANY (ARRAY['pendiente'::text, 'activo'::text, 'revocado'::text]))`
- `ck_dispositivo_identificador`: `(identificador ~ '^[A-Za-z0-9._:-]{8,128}$'::text)`
- `ck_dispositivo_revocacion`: `((estado = 'revocado'::text) = (fecha_revocacion IS NOT NULL))`

**Claves foráneas:**
- `(autorizado_por)` → `obra.usuario(id)` · al borrar: NO ACTION
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION
- `(revocado_por)` → `obra.usuario(id)` · al borrar: NO ACTION
- `(usuario_id)` → `obra.usuario(id)` · al borrar: NO ACTION

**Disparadores:** `tg_dispositivo`, `tg_obra_dispositivo_no_borrar`, `tg_obra_dispositivo_no_truncar`

### `obra.periodo` · Periodo (semana)

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Lo genera el sistema al crear la obra
- **Permisos:** `sigro_svc_obras` S U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `proyecto_id` | bigint | no |  | FK | Referencia a Obra (obra.proyecto) |
| `numero` | integer | no |  |  | Número |
| `fecha_inicio` | date | no |  |  | Desde |
| `fecha_fin` | date | no |  |  | Hasta |
| `estado` | text | no | 'abierto'::text |  | Estado: abierto / cerrado |
| `cerrado_en` | timestamptz | sí |  |  | Cerrado en |
| `cerrado_por` | bigint | sí |  | FK | Referencia a Usuario (obra.usuario) |

**Únicos:** `(id, proyecto_id)` · `(proyecto_id, numero)`

**CHECK:**
- `ck_periodo_cierre`: `((estado = 'cerrado'::text) = (cerrado_en IS NOT NULL))`
- `ck_periodo_estado`: `(estado = ANY (ARRAY['abierto'::text, 'cerrado'::text]))`
- `ck_periodo_numero`: `(numero > 0)`
- `ck_periodo_rango`: `(fecha_fin >= fecha_inicio)`

**Claves foráneas:**
- `(cerrado_por)` → `obra.usuario(id)` · al borrar: NO ACTION
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION

**Índices:** `ex_periodo_sin_solape (proyecto_id, daterange(fecha_inicio, fecha_fin, '[]'::text))`

**Disparadores:** `tg_obra_periodo_no_borrar`, `tg_obra_periodo_no_truncar`, `tg_periodo_cambios`

### `obra.proyecto` · Obra

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Todo lo demás cuelga de una obra
- **Permisos:** `sigro_svc_obras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `nombre` | text | no |  |  | Nombre |
| `fecha_inicio` | date | no |  |  | Fecha de inicio |
| `fecha_fin` | date | no |  |  | Fecha de fin |
| `dia_corte` | smallint | no | 7 |  | Día de corte semanal |
| `estado` | text | no | 'en_ejecucion'::text |  | Estado: en ejecución / cerrado |
| `creado_por` | bigint | no |  | FK | Referencia a Usuario (obra.usuario) |
| `creado_en` | timestamptz | no | now() |  | Momento en que se registró (hora del servidor) |

**Únicos:** `(lower(nombre))`

**CHECK:**
- `ck_proyecto_dia_corte`: `((dia_corte >= 1) AND (dia_corte <= 7))`
- `ck_proyecto_estado`: `(estado = ANY (ARRAY['en_ejecucion'::text, 'cerrado'::text]))`
- `ck_proyecto_nombre`: `(btrim(nombre) <> ''::text)`
- `ck_proyecto_plazo`: `(fecha_fin >= fecha_inicio)`

**Claves foráneas:**
- `(creado_por)` → `obra.usuario(id)` · al borrar: NO ACTION

**Disparadores:** `tg_obra_proyecto_no_borrar`, `tg_obra_proyecto_no_truncar`, `tg_proyecto_cambios`, `tg_proyecto_genera_periodos`

### `obra.proyecto_plazo_historial` · Ampliación de plazo

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: solo se agrega
- **Permisos:** `sigro_svc_obras` S

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `proyecto_id` | bigint | no |  | FK | Referencia a Obra (obra.proyecto) |
| `fecha_fin_anterior` | date | no |  |  | Fecha de fin anterior |
| `fecha_fin_nueva` | date | no |  |  | Fecha de fin nueva |
| `motivo` | text | no |  |  | Motivo |
| `autor` | bigint | no |  | FK | Referencia a Usuario (obra.usuario) |
| `fecha` | timestamptz | no | now() |  | Fecha |


**CHECK:**
- `ck_proyecto_plazo_historial_amplia`: `(fecha_fin_nueva > fecha_fin_anterior)`
- `ck_proyecto_plazo_historial_motivo`: `(btrim(motivo) <> ''::text)`

**Claves foráneas:**
- `(autor)` → `obra.usuario(id)` · al borrar: NO ACTION
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION

**Índices:** `ix_proyecto_plazo_historial_proyecto_id (proyecto_id, fecha)`

**Disparadores:** `tg_obra_proyecto_plazo_historial_append_only`, `tg_obra_proyecto_plazo_historial_no_truncar`

### `obra.usuario` · Usuario

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** No tiene rol: el rol vive en la Asignación
- **Permisos:** `sigro_svc_obras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `nombre` | text | no |  |  | Nombre |
| `usuario` | text | no |  |  | Usuario (nombre de acceso) |
| `hash_clave` | text | no |  |  | Clave (solo su hash) |
| `estado` | text | no | 'activo'::text |  | Estado: activo / baja |
| `fecha_baja` | timestamptz | sí |  |  | Fecha de baja |
| `intentos_fallidos` | smallint | no | 0 |  |  |
| `bloqueado_hasta` | timestamptz | sí |  |  | Bloqueado hasta |
| `creado_en` | timestamptz | no | now() |  | Momento en que se registró (hora del servidor) |

**Únicos:** `(lower(usuario))`

**CHECK:**
- `ck_usuario_baja_con_fecha`: `((estado = 'baja'::text) = (fecha_baja IS NOT NULL))`
- `ck_usuario_estado`: `(estado = ANY (ARRAY['activo'::text, 'baja'::text]))`
- `ck_usuario_hash_clave`: `(hash_clave ~ '^\$(argon2(id\|i\|d)\|2[aby])\$'::text)`
- `ck_usuario_intentos`: `(intentos_fallidos >= 0)`
- `ck_usuario_nombre`: `(btrim(nombre) <> ''::text)`
- `ck_usuario_usuario`: `(usuario ~ '^[A-Za-z0-9._-]{3,40}$'::text)`

**Disparadores:** `tg_obra_usuario_no_borrar`, `tg_obra_usuario_no_truncar`

## Microservicio `catalogo` · esquema `catalogo`

Código único de insumo, nombres comerciales, factor de conversión, importación del s10.

### `catalogo.insumo` · Insumo

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** El código no cambia · es de la empresa, no de la obra
- **Permisos:** `sigro_svc_catalogo` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `codigo` | text | no |  |  | Código |
| `descripcion` | text | no |  |  | Descripción |
| `unidad_s10` | text | no |  |  | Unidad del S10 |
| `unidad_compra` | text | no |  |  | Unidad de compra |
| `factor_conversion` | numeric(18,6) | no | 1 |  | Factor de conversión |
| `tipo` | text | no |  |  | Tipo: material / mano de obra / equipo / subcontrato |
| `activo` | boolean | no | true |  | Activo |
| `verificado` | boolean | no | false |  | Verificado |
| `creado_por` | bigint | sí |  | FK* | Referencia a Usuario (obra.usuario) |
| `creado_en` | timestamptz | no | now() |  | Momento en que se registró (hora del servidor) |

**Únicos:** `(codigo)`

**CHECK:**
- `ck_insumo_codigo`: `(btrim(codigo) <> ''::text)`
- `ck_insumo_descripcion`: `(btrim(descripcion) <> ''::text)`
- `ck_insumo_factor`: `(factor_conversion > (0)::numeric)`
- `ck_insumo_tipo`: `(tipo = ANY (ARRAY['material'::text, 'mano_obra'::text, 'equipo'::text, 'subcontrato'::text]))`
- `ck_insumo_unidades`: `((btrim(unidad_s10) <> ''::text) AND (btrim(unidad_compra) <> ''::text))`

**Claves foráneas:**
- `(creado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Disparadores:** `tg_catalogo_insumo_no_borrar`, `tg_catalogo_insumo_no_truncar`, `tg_insumo_codigo_inmutable`

### `catalogo.insumo_factor_historial` · Cambio de factor

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: solo se agrega · quién y por qué (HU-17)
- **Permisos:** `sigro_svc_catalogo` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `insumo_id` | bigint | no |  | FK | Referencia a Insumo (catalogo.insumo) |
| `factor_anterior` | numeric(18,6) | no |  |  | Factor anterior |
| `factor_nuevo` | numeric(18,6) | no |  |  | Factor nuevo |
| `motivo` | text | no |  |  | Motivo |
| `cambiado_por` | bigint | no |  | FK* | Referencia a Usuario (obra.usuario) |
| `cambiado_en` | timestamptz | no | now() |  | Cambiado en |


**CHECK:**
- `ck_insumo_factor_historial_cambia`: `(factor_nuevo <> factor_anterior)`
- `ck_insumo_factor_historial_motivo`: `(btrim(motivo) <> ''::text)`
- `ck_insumo_factor_historial_positivos`: `((factor_anterior > (0)::numeric) AND (factor_nuevo > (0)::numeric))`

**Claves foráneas:**
- `(cambiado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION

**Índices:** `ix_insumo_factor_historial_insumo_id (insumo_id, id)`

**Disparadores:** `tg_catalogo_insumo_factor_historial_append_only`, `tg_catalogo_insumo_factor_historial_no_truncar`

### `catalogo.insumo_nombre_comercial` · Nombre comercial

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Cómo lo llaman los proveedores
- **Permisos:** `sigro_svc_catalogo` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `insumo_id` | bigint | no |  | FK | Referencia a Insumo (catalogo.insumo) |
| `nombre` | text | no |  |  | Nombre |

**Únicos:** `(insumo_id, lower(nombre))`

**CHECK:**
- `ck_insumo_nombre_comercial_nombre`: `(btrim(nombre) <> ''::text)`

**Claves foráneas:**
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION

**Disparadores:** `tg_catalogo_insumo_nombre_comercial_no_borrar`, `tg_catalogo_insumo_nombre_comercial_no_truncar`

## Microservicio `compras` · esquema `compras`

Proveedores, compras, solicitudes, requerimiento y reposición.

### `compras.compra` · Compra

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** No se edita: se anula con motivo · comprobante único por proveedor
- **Permisos:** `sigro_svc_compras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `proyecto_id` | bigint | no |  | FK* | Referencia a Obra (obra.proyecto) |
| `proveedor_id` | bigint | no |  | FK | Referencia a Proveedor (compras.proveedor) |
| `tipo_comprobante` | text | no |  |  | Tipo de comprobante |
| `numero_comprobante` | text | no |  |  | N.º de comprobante |
| `fecha` | date | no |  |  | Fecha |
| `moneda` | text | no | 'PEN'::text |  | Moneda: PEN / USD |
| `base_imponible` | numeric(14,2) | no |  |  | Base imponible |
| `igv` | numeric(14,2) | no | 0 |  | IGV |
| `total` | numeric(14,2) | no |  |  | Total |
| `estado` | text | no | 'registrada'::text |  | Estado: registrada / anulada |
| `motivo_anulacion` | text | sí |  |  | Motivo de anulación |
| `anulada_por` | bigint | sí |  | FK* | Referencia a Usuario (obra.usuario) |
| `anulada_en` | timestamptz | sí |  |  | Anulada en |
| `registrado_por` | bigint | no |  | FK* | Referencia a Usuario (obra.usuario) |
| `creado_en` | timestamptz | no | now() |  | Momento en que se registró (hora del servidor) |
| `solicitud_id` | bigint | sí |  | FK | Referencia a Solicitud de materiales (compras.solicitud) |

**Únicos:** `(id, proyecto_id)` · `(proveedor_id, tipo_comprobante, numero_comprobante)`

**CHECK:**
- `ck_compra_anulacion`: `(((estado = 'registrada'::text) AND (motivo_anulacion IS NULL) AND (anulada_por IS NULL) AND (anulada_en IS NULL)) OR ((estado = 'anulada'::text) AND (btrim(COALESCE(motivo_anulacion, ''::text)) <> ''::text) AND (anulada_por IS NOT NULL) AND (anulada_en IS NOT NULL)))`
- `ck_compra_estado`: `(estado = ANY (ARRAY['registrada'::text, 'anulada'::text]))`
- `ck_compra_moneda`: `(moneda = ANY (ARRAY['PEN'::text, 'USD'::text]))`
- `ck_compra_montos`: `((base_imponible >= (0)::numeric) AND (igv >= (0)::numeric) AND (total = (base_imponible + igv)))`
- `ck_compra_numero_comprobante`: `(numero_comprobante ~ '^[A-Za-z0-9]{1,4}-[0-9]{1,10}$'::text)`
- `ck_compra_tipo_comprobante`: `(tipo_comprobante = ANY (ARRAY['factura'::text, 'boleta'::text, 'nota_venta'::text, 'otro'::text]))`

**Claves foráneas:**
- `(anulada_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(proveedor_id)` → `compras.proveedor(id)` · al borrar: NO ACTION
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(registrado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(solicitud_id, proyecto_id)` → `compras.solicitud(id, proyecto_id)` · al borrar: NO ACTION

**Índices:** `ix_compra_proveedor_id (proveedor_id)` · `ix_compra_proyecto_id_fecha (proyecto_id, fecha DESC)` · `ix_compra_solicitud_id (solicitud_id) WHERE solicitud_id IS NOT NULL`

**Disparadores:** `tg_compra_solicitud_vigente`, `tg_compra_validar`, `tg_compras_compra_no_borrar`, `tg_compras_compra_no_truncar`

### `compras.compra_adjunto` · Adjunto de compra

- **Tipo:** libro (solo se agrega) · **Regla:** Libro · el mismo archivo no se sube dos veces
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `compra_id` | bigint | no |  | FK | Referencia a Compra (compras.compra) |
| `archivo` | text | no |  |  | Archivo |
| `nombre_original` | text | no |  |  | Nombre original |
| `tipo_mime` | text | no |  |  | Tipo |
| `tamano_bytes` | bigint | no |  |  | Tamaño |
| `sha256` | character(64) | no |  |  | Huella SHA-256 |
| `subido_por` | bigint | no |  | FK* | Referencia a Usuario (obra.usuario) |
| `subido_en` | timestamptz | no | now() |  |  |

**Únicos:** `(compra_id, sha256)`

**CHECK:**
- `ck_compra_adjunto_archivo`: `(btrim(archivo) <> ''::text)`
- `ck_compra_adjunto_sha256`: `(sha256 ~ '^[0-9a-f]{64}$'::text)`
- `ck_compra_adjunto_tamano`: `((tamano_bytes > 0) AND (tamano_bytes <= ((15 * 1024) * 1024)))`
- `ck_compra_adjunto_tipo_mime`: `(tipo_mime = ANY (ARRAY['application/pdf'::text, 'image/jpeg'::text, 'image/png'::text]))`

**Claves foráneas:**
- `(compra_id)` → `compras.compra(id)` · al borrar: NO ACTION
- `(subido_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Disparadores:** `tg_compras_compra_adjunto_append_only`, `tg_compras_compra_adjunto_no_truncar`

### `compras.compra_detalle` · Línea de compra

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: solo se agrega · un insumo una vez por compra
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `compra_id` | bigint | no |  | FK | Referencia a Compra (compras.compra) |
| `insumo_id` | bigint | no |  | FK* | Referencia a Insumo (catalogo.insumo) |
| `cantidad` | numeric(18,4) | no |  |  | Cantidad |
| `unidad` | text | no |  |  | Unidad |
| `precio_unitario` | numeric(18,4) | no |  |  | Precio unitario |

**Únicos:** `(compra_id, insumo_id)`

**CHECK:**
- `ck_compra_detalle_cantidad`: `(cantidad > (0)::numeric)`
- `ck_compra_detalle_precio`: `(precio_unitario >= (0)::numeric)`
- `ck_compra_detalle_unidad`: `(btrim(unidad) <> ''::text)`

**Claves foráneas:**
- `(compra_id)` → `compras.compra(id)` · al borrar: NO ACTION
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION · **cruza al servicio catalogo (fase 1)**

**Índices:** `ix_compra_detalle_insumo_id (insumo_id)`

**Disparadores:** `tg_compras_compra_detalle_append_only`, `tg_compras_compra_detalle_no_truncar`

### `compras.parametro_reposicion` · Parámetro de reposición

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: cada cambio es una vigencia nueva
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `proyecto_id` | bigint | no |  | FK* | Referencia a Obra (obra.proyecto) |
| `insumo_id` | bigint | no |  | FK* | Referencia a Insumo (catalogo.insumo) |
| `stock_seguridad` | numeric(18,4) | no |  |  | Stock de seguridad |
| `punto_reposicion` | numeric(18,4) | no |  |  | Punto de reposición |
| `registrado_por` | bigint | no |  | FK* | Referencia a Usuario (obra.usuario) |
| `vigente_desde` | timestamptz | no | now() |  |  |


**CHECK:**
- `ck_parametro_reposicion_valores`: `((stock_seguridad >= (0)::numeric) AND (punto_reposicion >= stock_seguridad))`

**Claves foráneas:**
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION · **cruza al servicio catalogo (fase 1)**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(registrado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Índices:** `ix_parametro_reposicion_vigencia (proyecto_id, insumo_id, id DESC)`

**Disparadores:** `tg_compras_parametro_reposicion_append_only`, `tg_compras_parametro_reposicion_no_truncar`

### `compras.proveedor` · Proveedor

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Es de la empresa, no de la obra
- **Permisos:** `sigro_svc_compras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `ruc` | text | no |  |  | RUC |
| `razon_social` | text | no |  |  | Razón social |
| `estado_validacion` | text | no | 'sin_validar'::text |  | Validación: validado / sin validar |
| `validado_en` | timestamptz | sí |  |  | Validado en |
| `estado` | text | no | 'activo'::text |  | Estado: activo / baja |
| `fecha_baja` | timestamptz | sí |  |  | Fecha de baja |
| `creado_en` | timestamptz | no | now() |  | Momento en que se registró (hora del servidor) |

**Únicos:** `(ruc)`

**CHECK:**
- `ck_proveedor_baja_con_fecha`: `((estado = 'baja'::text) = (fecha_baja IS NOT NULL))`
- `ck_proveedor_estado`: `(estado = ANY (ARRAY['activo'::text, 'baja'::text]))`
- `ck_proveedor_estado_validacion`: `(estado_validacion = ANY (ARRAY['validado'::text, 'sin_validar'::text]))`
- `ck_proveedor_razon_social`: `(btrim(razon_social) <> ''::text)`
- `ck_proveedor_ruc_formato`: `(ruc ~ '^[0-9A-Za-z-]{4,20}$'::text)`
- `ck_proveedor_validacion_con_fecha`: `((estado_validacion = 'validado'::text) = (validado_en IS NOT NULL))`
- `ck_proveedor_validado_ruc_correcto`: `((estado_validacion = 'sin_validar'::text) OR compras.ruc_valido(ruc))`

**Índices:** `ix_proveedor_razon_social (lower(razon_social) text_pattern_ops)`

**Disparadores:** `tg_compras_proveedor_no_borrar`, `tg_compras_proveedor_no_truncar`, `tg_proveedor_cambios`

### `compras.requerimiento` · Requerimiento de obra

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Un insumo una vez por obra · la cantidad solo cambia con ajuste
- **Permisos:** `sigro_svc_compras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `proyecto_id` | bigint | no |  | FK* | Referencia a Obra (obra.proyecto) |
| `insumo_id` | bigint | no |  | FK* | Referencia a Insumo (catalogo.insumo) |
| `cantidad_planificada` | numeric(18,4) | no |  |  | Cantidad planificada (S10) |
| `precio_planificado` | numeric(18,4) | no |  |  | Precio planificado |
| `creado_por` | bigint | no |  | FK* | Referencia a Usuario (obra.usuario) |
| `creado_en` | timestamptz | no | now() |  | Momento en que se registró (hora del servidor) |

**Únicos:** `(proyecto_id, insumo_id)`

**CHECK:**
- `ck_requerimiento_cantidad`: `(cantidad_planificada > (0)::numeric)`
- `ck_requerimiento_precio`: `(precio_planificado >= (0)::numeric)`

**Claves foráneas:**
- `(creado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION · **cruza al servicio catalogo (fase 1)**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Índices:** `ix_requerimiento_insumo_id (insumo_id)`

**Disparadores:** `tg_compras_requerimiento_no_borrar`, `tg_compras_requerimiento_no_truncar`, `tg_requerimiento_ajuste_obligatorio`

### `compras.requerimiento_ajuste` · Ajuste de requerimiento

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: solo se agrega · lleva motivo y autor
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `requerimiento_id` | bigint | no |  | FK | Referencia a Requerimiento de obra (compras.requerimiento) |
| `cantidad_anterior` | numeric(18,4) | no |  |  | Cantidad anterior |
| `cantidad_nueva` | numeric(18,4) | no |  |  | Cantidad nueva |
| `motivo` | text | no |  |  | Motivo |
| `ajustado_por` | bigint | no |  | FK* | Referencia a Usuario (obra.usuario) |
| `ajustado_en` | timestamptz | no | now() |  |  |


**CHECK:**
- `ck_requerimiento_ajuste_cantidades`: `((cantidad_anterior > (0)::numeric) AND (cantidad_nueva > (0)::numeric) AND (cantidad_nueva <> cantidad_anterior))`
- `ck_requerimiento_ajuste_motivo`: `(btrim(motivo) <> ''::text)`

**Claves foráneas:**
- `(ajustado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(requerimiento_id)` → `compras.requerimiento(id)` · al borrar: NO ACTION

**Índices:** `ix_requerimiento_ajuste_requerimiento_id (requerimiento_id, id)`

**Disparadores:** `tg_compras_requerimiento_ajuste_append_only`, `tg_compras_requerimiento_ajuste_no_truncar`

### `compras.solicitud` · Solicitud de materiales

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Emitida por el residente · nunca es pedido automático
- **Permisos:** `sigro_svc_compras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `proyecto_id` | bigint | no |  | FK* | Referencia a Obra (obra.proyecto) |
| `numero` | integer | no |  |  | N.º (correlativo por obra) |
| `fecha` | date | no | CURRENT_DATE |  | Fecha |
| `estado` | text | no | 'emitida'::text |  | Estado: emitida / anulada |
| `motivo_anulacion` | text | sí |  |  | Motivo de anulación |
| `anulada_por` | bigint | sí |  | FK* | Referencia a Usuario (obra.usuario) |
| `anulada_en` | timestamptz | sí |  |  | Anulada en |
| `emitida_por` | bigint | no |  | FK* | Referencia a Usuario (obra.usuario) |
| `creado_en` | timestamptz | no | now() |  | Momento en que se registró (hora del servidor) |

**Únicos:** `(id, proyecto_id)` · `(proyecto_id, numero)`

**CHECK:**
- `ck_solicitud_anulacion`: `(((estado = 'emitida'::text) AND (motivo_anulacion IS NULL) AND (anulada_por IS NULL) AND (anulada_en IS NULL)) OR ((estado = 'anulada'::text) AND (btrim(COALESCE(motivo_anulacion, ''::text)) <> ''::text) AND (anulada_por IS NOT NULL) AND (anulada_en IS NOT NULL)))`
- `ck_solicitud_estado`: `(estado = ANY (ARRAY['emitida'::text, 'anulada'::text]))`
- `ck_solicitud_numero`: `(numero > 0)`

**Claves foráneas:**
- `(anulada_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(emitida_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Disparadores:** `tg_compras_solicitud_no_borrar`, `tg_compras_solicitud_no_truncar`, `tg_solicitud_cambios`, `tg_solicitud_numero`

### `compras.solicitud_correlativo` · —

- **Tipo:** técnica (contador)
- **Permisos:** ningún rol escribe directo; solo su función `SECURITY DEFINER`

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `proyecto_id` | bigint | no |  | PK FK* | Referencia a Obra (obra.proyecto) |
| `ultimo` | integer | no | 0 |  |  |


**CHECK:**
- `ck_solicitud_correlativo_ultimo`: `(ultimo >= 0)`

**Claves foráneas:**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Disparadores:** `tg_compras_solicitud_correlativo_no_borrar`, `tg_compras_solicitud_correlativo_no_truncar`

### `compras.solicitud_linea` · Línea de solicitud

- **Tipo:** libro (solo se agrega) · **Regla:** Libro · el insumo puede faltar (línea libre)
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `solicitud_id` | bigint | no |  | FK | Referencia a Solicitud de materiales (compras.solicitud) |
| `insumo_id` | bigint | sí |  | FK* | Referencia a Insumo (catalogo.insumo) |
| `descripcion_libre` | text | sí |  |  | Descripción libre |
| `cantidad` | numeric(18,4) | no |  |  | Cantidad |
| `unidad_compra` | text | no |  |  | Unidad de compra |

**Únicos:** `(solicitud_id, insumo_id)` si `insumo_id IS NOT NULL`

**CHECK:**
- `ck_solicitud_linea_cantidad`: `(cantidad > (0)::numeric)`
- `ck_solicitud_linea_describe`: `((insumo_id IS NOT NULL) OR (btrim(COALESCE(descripcion_libre, ''::text)) <> ''::text))`
- `ck_solicitud_linea_unidad`: `(btrim(unidad_compra) <> ''::text)`

**Claves foráneas:**
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION · **cruza al servicio catalogo (fase 1)**
- `(solicitud_id)` → `compras.solicitud(id)` · al borrar: NO ACTION

**Índices:** `ix_solicitud_linea_insumo_id (insumo_id) WHERE insumo_id IS NOT NULL`

**Disparadores:** `tg_compras_solicitud_linea_append_only`, `tg_compras_solicitud_linea_no_truncar`, `tg_solicitud_linea_vigente`

## Microservicio `tareo` · esquema `tareo`

Trabajadores y asistencia (horas hombre).

### `tareo.jornada` · Jornada

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: se corrige agregando otra · una vigente por trabajador y día
- **Permisos:** `sigro_svc_tareo` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `uuid_cliente` | uuid | sí |  |  | Identificador del celular |
| `proyecto_id` | bigint | no |  | FK* | Referencia a Periodo (semana) (obra.periodo) |
| `trabajador_id` | bigint | no |  | FK | Referencia a Jornada (tareo.jornada) |
| `fecha` | date | no |  | FK | Fecha |
| `estado` | text | no |  |  | Asistencia: presente / ausente / permiso |
| `horas_normales` | numeric(4,2) | no | 0 |  | Horas normales |
| `horas_extra` | numeric(4,2) | no | 0 |  | Horas extra |
| `categoria` | text | no |  |  | Categoría (congelada al registrar) |
| `periodo_id` | bigint | no |  | FK* | Referencia a Periodo (semana) (obra.periodo) |
| `registrado_por` | bigint | no |  | FK* | Referencia a Usuario (obra.usuario) |
| `fecha_servidor` | timestamptz | no | now() |  | Hora del servidor al recibir el registro |
| `corrige_a_id` | bigint | sí |  | FK | Referencia a Jornada (tareo.jornada) |
| `motivo_correccion` | text | sí |  |  | Motivo de corrección |

**Únicos:** `(corrige_a_id)` si `corrige_a_id IS NOT NULL` · `(id, trabajador_id, fecha)` · `(trabajador_id, fecha)` si `corrige_a_id IS NULL` · `(uuid_cliente)` si `uuid_cliente IS NOT NULL`

**CHECK:**
- `ck_jornada_categoria`: `(categoria = ANY (ARRAY['operario'::text, 'oficial'::text, 'peon'::text]))`
- `ck_jornada_correccion`: `(((corrige_a_id IS NULL) AND (motivo_correccion IS NULL)) OR ((corrige_a_id IS NOT NULL) AND (btrim(COALESCE(motivo_correccion, ''::text)) <> ''::text)))`
- `ck_jornada_estado`: `(estado = ANY (ARRAY['presente'::text, 'ausente'::text, 'permiso'::text]))`
- `ck_jornada_horas_rango`: `(((horas_normales >= (0)::numeric) AND (horas_normales <= (24)::numeric)) AND ((horas_extra >= (0)::numeric) AND (horas_extra <= (24)::numeric)) AND ((horas_normales + horas_extra) <= (24)::numeric))`
- `ck_jornada_horas_segun_estado`: `(((estado = 'presente'::text) AND ((horas_normales + horas_extra) > (0)::numeric)) OR ((estado <> 'presente'::text) AND (horas_normales = (0)::numeric) AND (horas_extra = (0)::numeric)))`

**Claves foráneas:**
- `(corrige_a_id, trabajador_id, fecha)` → `tareo.jornada(id, trabajador_id, fecha)` · al borrar: NO ACTION
- `(periodo_id, proyecto_id)` → `obra.periodo(id, proyecto_id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(registrado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(trabajador_id, proyecto_id)` → `tareo.trabajador(id, proyecto_id)` · al borrar: NO ACTION

**Índices:** `ix_jornada_periodo_id (periodo_id)` · `ix_jornada_proyecto_id_fecha (proyecto_id, fecha)`

**Disparadores:** `tg_jornada_validar`, `tg_tareo_jornada_append_only`, `tg_tareo_jornada_no_truncar`

### `tareo.trabajador` · Trabajador

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Por obra · se da de baja, no se borra
- **Permisos:** `sigro_svc_tareo` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `proyecto_id` | bigint | no |  | FK* | Referencia a Obra (obra.proyecto) |
| `nombre` | text | no |  |  | Nombre |
| `tipo_documento` | text | no | 'DNI'::text |  | Tipo de documento: DNI / CE / pasaporte |
| `documento` | text | no |  |  | Documento |
| `categoria` | text | no |  |  | Categoría: operario / oficial / peón |
| `cuadrilla` | text | no |  |  | Cuadrilla |
| `fecha_ingreso` | date | no |  |  | Fecha de ingreso |
| `fecha_salida` | date | sí |  |  | Fecha de salida |
| `registrado_por` | bigint | no |  | FK* | Referencia a Usuario (obra.usuario) |
| `creado_en` | timestamptz | no | now() |  | Momento en que se registró (hora del servidor) |

**Únicos:** `(id, proyecto_id)` · `(proyecto_id, documento)`

**CHECK:**
- `ck_trabajador_categoria`: `(categoria = ANY (ARRAY['operario'::text, 'oficial'::text, 'peon'::text]))`
- `ck_trabajador_cuadrilla`: `(btrim(cuadrilla) <> ''::text)`
- `ck_trabajador_documento`: `(((tipo_documento = 'DNI'::text) AND (documento ~ '^[0-9]{8}$'::text)) OR ((tipo_documento <> 'DNI'::text) AND (documento ~ '^[A-Za-z0-9]{6,20}$'::text)))`
- `ck_trabajador_nombre`: `(btrim(nombre) <> ''::text)`
- `ck_trabajador_salida`: `((fecha_salida IS NULL) OR (fecha_salida >= fecha_ingreso))`
- `ck_trabajador_tipo_documento`: `(tipo_documento = ANY (ARRAY['DNI'::text, 'CE'::text, 'PASAPORTE'::text]))`

**Claves foráneas:**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(registrado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Índices:** `ix_trabajador_proyecto_id_cuadrilla (proyecto_id, cuadrilla)`

**Disparadores:** `tg_tareo_trabajador_no_borrar`, `tg_tareo_trabajador_no_truncar`, `tg_trabajador_validar`

## Microservicio `relevo` · esquema `outbox`

Bandeja de salida (outbox): entrega los eventos a notificaciones.

### `outbox.evento` · Evento por avisar

- **Tipo:** bandeja de salida (eventos) · **Regla:** Se escribe en la misma transacción que el hecho
- **Permisos:** `sigro_svc_obras` S I, `sigro_svc_compras` S I, `sigro_svc_relevo` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno (lo asigna la base) |
| `tipo` | text | no |  |  | Tipo |
| `proyecto_id` | bigint | no |  | FK* | Referencia a Obra (obra.proyecto) |
| `payload` | jsonb | no |  |  | Contenido |
| `creado_en` | timestamptz | no | now() |  | Creado en |
| `enviado_en` | timestamptz | sí |  |  | Entregado en |
| `intentos` | smallint | no | 0 |  | Intentos |
| `siguiente_intento` | timestamptz | no | now() |  |  |
| `ultimo_error` | text | sí |  |  | Último error |


**CHECK:**
- `ck_evento_destinatarios`: `CHECK ( CASE     WHEN (jsonb_typeof((payload -> 'destinatarios'::text)) = 'array'::text) THEN (jsonb_array_length((payload -> 'destinatarios'::text)) > 0)     ELSE false END)`
- `ck_evento_enviado_tras_intentar`: `((enviado_en IS NULL) OR (intentos >= 1))`
- `ck_evento_intentos`: `(intentos >= 0)`
- `ck_evento_payload_objeto`: `(jsonb_typeof(payload) = 'object'::text)`
- `ck_evento_tipo`: `(tipo ~ '^[a-z]+(\.[a-z_]+)+$'::text)`

**Claves foráneas:**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Índices:** `ix_evento_pendientes (siguiente_intento, id) WHERE enviado_en IS NULL` · `ix_evento_proyecto_id (proyecto_id, id)`

**Disparadores:** `tg_evento_cambios`, `tg_outbox_evento_no_borrar`, `tg_outbox_evento_no_truncar`

## Vistas

- `compras.parametro_reposicion_vigente`
- `compras.saldo_por_comprar`
- `compras.solicitud_estado`
- `compras.solicitud_linea_avance`
- `tareo.horas_acumuladas`
- `tareo.jornada_vigente`

## Funciones `SECURITY DEFINER`

Son las únicas que escriben periodos, plazos y correlativos, o validan contra el esquema de otro servicio sin darle a ese servicio permiso de lectura.

- `compras.tg_compra_validar()`
- `compras.tg_solicitud_numero()`
- `obra.ampliar_plazo()`
- `obra.generar_periodos()`
- `tareo.tg_jornada_validar()`
- `tareo.tg_trabajador_validar()`

## Claves foráneas entre servicios (deuda de la fase 1)

Hoy se conservan para no perder integridad mientras todos los esquemas viven en la misma instancia. El servicio que tiene la FK **no puede leer** la tabla a la que apunta (no tiene permiso): solo la referencia. En la fase 2 cada una se reemplaza por una réplica local alimentada por eventos y la FK se elimina (ADR-09).

| Desde (servicio) | Columnas | Hacia (servicio) |
|---|---|---|
| `catalogo.insumo` (catalogo) | `creado_por` | `obra.usuario` (obras) |
| `catalogo.insumo_factor_historial` (catalogo) | `cambiado_por` | `obra.usuario` (obras) |
| `compras.compra` (compras) | `proyecto_id` | `obra.proyecto` (obras) |
| `compras.compra` (compras) | `anulada_por` | `obra.usuario` (obras) |
| `compras.compra` (compras) | `registrado_por` | `obra.usuario` (obras) |
| `compras.compra_adjunto` (compras) | `subido_por` | `obra.usuario` (obras) |
| `compras.compra_detalle` (compras) | `insumo_id` | `catalogo.insumo` (catalogo) |
| `compras.parametro_reposicion` (compras) | `insumo_id` | `catalogo.insumo` (catalogo) |
| `compras.parametro_reposicion` (compras) | `proyecto_id` | `obra.proyecto` (obras) |
| `compras.parametro_reposicion` (compras) | `registrado_por` | `obra.usuario` (obras) |
| `compras.requerimiento` (compras) | `insumo_id` | `catalogo.insumo` (catalogo) |
| `compras.requerimiento` (compras) | `proyecto_id` | `obra.proyecto` (obras) |
| `compras.requerimiento` (compras) | `creado_por` | `obra.usuario` (obras) |
| `compras.requerimiento_ajuste` (compras) | `ajustado_por` | `obra.usuario` (obras) |
| `compras.solicitud` (compras) | `proyecto_id` | `obra.proyecto` (obras) |
| `compras.solicitud` (compras) | `anulada_por` | `obra.usuario` (obras) |
| `compras.solicitud` (compras) | `emitida_por` | `obra.usuario` (obras) |
| `compras.solicitud_correlativo` (compras) | `proyecto_id` | `obra.proyecto` (obras) |
| `compras.solicitud_linea` (compras) | `insumo_id` | `catalogo.insumo` (catalogo) |
| `outbox.evento` (relevo) | `proyecto_id` | `obra.proyecto` (obras) |
| `tareo.jornada` (tareo) | `periodo_id, proyecto_id` | `obra.periodo` (obras) |
| `tareo.jornada` (tareo) | `registrado_por` | `obra.usuario` (obras) |
| `tareo.trabajador` (tareo) | `proyecto_id` | `obra.proyecto` (obras) |
| `tareo.trabajador` (tareo) | `registrado_por` | `obra.usuario` (obras) |
