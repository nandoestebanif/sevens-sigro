# Diccionario de datos · SIGRO (por módulo)

> Generado por `api/docs/generar_diccionario.py` desde la base migrada (PostgreSQL 16.15, migración `0025`). **No editar a mano:** se regenera. 38 tablas, 354 columnas, 72 claves foráneas (31 entre servicios), 12 vistas.

Convenciones: **FK\*** = clave foránea que apunta a la tabla de otro servicio (deuda de la fase 1, ver al final). Permisos: S select · I insert · U update · D delete. Ningún servicio tiene D: nada se borra (reglas 1 y 10).

## Resumen por servicio

| Módulo | Esquema | Usuario → rol | Tablas | FK hacia otros servicios | Qué guarda |
|---|---|---|---|---|---|
| **obras** | `obra` | `sigro_obras` → `sigro_svc_obras` | 8 | 0 | identidad, sesión, usuarios, obras, periodos, almacenes, asignaciones, dispositivos; decide quién puede qué |
| **catalogo** | `catalogo` | `sigro_catalogo` → `sigro_svc_catalogo` | 3 | 2 | código único de insumo, nombres comerciales, factor de conversión, importación del S10 |
| **compras** | `compras` | `sigro_compras` → `sigro_svc_compras` | 14 | 24 | proveedores, compras, solicitudes, requerimiento y reposición |
| **tareo** | `tareo` | `sigro_tareo` → `sigro_svc_tareo` | 2 | 4 | trabajadores y asistencia (horas hombre) |
| **almacen** | `almacen` | `sigro_almacen` → `sigro_svc_almacen` | 8 | 0 | libro de movimientos (solo INSERT), recepciones, traslados, conteos y préstamos de herramientas; saldo y kárdex derivados |
| **costo** | `costo` | `sigro_costo` → `sigro_svc_costo` | 2 | 0 | modelo de lectura del gasto: hechos valorizados que llegan de almacén por el outbox (RelevoCosto) |
| **relevo** | `outbox` | `sigro_relevo` → `sigro_svc_relevo` | 1 | 1 | bandeja de salida (outbox): eventos hacia costo (integración) y avisos (notificaciones) |
| **almacen** · **costo** | `almacen` · `costo` | por crear | — | — | movimientos, saldo, kárdex, costo promedio (por construir) |

## Módulo `obras` · esquema `obra`

Identidad, sesión, usuarios, obras, periodos, almacenes, asignaciones, dispositivos; decide quién puede qué.

### `obra.almacen` · Almacén

- **Tipo:** maestra / transaccional (no se borra)
- **Permisos:** `sigro_svc_obras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `nombre` | text | no |  |  | Nombre del almacén. |
| `tipo` | text | no | 'frente'::text |  | central o frente. |
| `estado` | text | no | 'activo'::text |  | activo o inactivo. |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |

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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `usuario_id` | bigint | no |  | FK | Persona asignada (obra.usuario.id). |
| `proyecto_id` | bigint | no |  | FK | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `rol` | text | no |  |  | almacenero, ssoma, residente, administrador o gerente. |
| `asignado_por` | bigint | no |  | FK | Usuario que hizo la asignación. |
| `fecha_asignacion` | timestamptz | no | now() |  | Desde cuándo tiene el rol en la obra. |
| `fecha_retiro` | timestamptz | sí |  |  | Desde cuándo ya no lo tiene; nulo si sigue asignado. |
| `retirado_por` | bigint | sí |  | FK | Usuario que retiró la asignación. |

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

### `obra.bitacora` · Registro de bitácora

- **Tipo:** libro (solo se agrega) · **Regla:** Solo se agrega · sin cuerpos, claves, PIN, tokens ni IP
- **Permisos:** `sigro_svc_obras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `momento` | timestamptz | no | now() |  | Hora del servidor en que terminó la operación. |
| `usuario_id` | bigint | sí |  |  | Usuario que la hizo (vacío si no inició sesión o el token no era válido). |
| `proyecto_id` | bigint | sí |  |  | Obra de la ruta, si la operación es de una obra. |
| `metodo` | text | no |  |  | Método HTTP (POST para cambios; GET solo si fue rechazado). |
| `ruta` | text | no |  |  | Ruta de la API sin parámetros de consulta (nunca lleva datos sensibles). |
| `estado` | integer | no |  |  | Código HTTP con que terminó (201 creado, 403 sin permiso, 422 datos inválidos…). |
| `duracion_ms` | integer | no | 0 |  | Cuánto tardó en milisegundos. |


**CHECK:**
- `ck_bitacora_estado`: `((estado >= 100) AND (estado <= 599))`
- `ck_bitacora_metodo`: `(metodo = ANY (ARRAY['GET'::text, 'POST'::text, 'PUT'::text, 'PATCH'::text, 'DELETE'::text]))`
- `ck_bitacora_ruta`: `((length(ruta) <= 300) AND (POSITION(('?'::text) IN (ruta)) = 0))`

**Índices:** `ix_bitacora_momento (momento DESC)` · `ix_bitacora_proyecto_momento (proyecto_id, momento DESC)`

**Disparadores:** `tg_obra_bitacora_append_only`, `tg_obra_bitacora_no_truncar`

### `obra.dispositivo` · Dispositivo (celular)

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** 1 activo por responsable · máx. 2 por obra
- **Permisos:** `sigro_svc_obras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `usuario_id` | bigint | no |  | FK | Responsable del celular (almacenero o ingeniero SSOMA). |
| `identificador` | text | no |  |  | Código que la app genera en su primera instalación y conserva. |
| `estado` | text | no | 'pendiente'::text |  | pendiente, activo o revocado. |
| `solicitado_en` | timestamptz | no | now() |  | Momento en que la app pidió la autorización. |
| `autorizado_por` | bigint | sí |  | FK | Usuario que autorizó el celular. |
| `fecha_autorizacion` | timestamptz | sí |  |  | Momento de la autorización. |
| `revocado_por` | bigint | sí |  | FK | Usuario que revocó el celular. |
| `fecha_revocacion` | timestamptz | sí |  |  | Momento de la revocación. |
| `motivo_revocacion` | text | sí |  |  | Por qué se revocó (pérdida, cambio de equipo…). |

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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `numero` | integer | no |  |  | Número de semana dentro de la obra (1, 2, …). |
| `fecha_inicio` | date | no |  |  | Primer día del periodo. |
| `fecha_fin` | date | no |  |  | Último día del periodo. |
| `estado` | text | no | 'abierto'::text |  | abierto o cerrado. |
| `cerrado_en` | timestamptz | sí |  |  | Momento del cierre. |
| `cerrado_por` | bigint | sí |  | FK | Usuario que cerró el periodo. |

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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `nombre` | text | no |  |  | Nombre de la obra (p. ej. Multifamiliar Flores). |
| `fecha_inicio` | date | no |  |  | Inicio del plazo de la obra. |
| `fecha_fin` | date | no |  |  | Fin vigente del plazo; sus cambios quedan en proyecto_plazo_historial. |
| `dia_corte` | smallint | no | 7 |  | Día de la semana en que cierra cada periodo (ISO: 1 lunes … 7 domingo). |
| `estado` | text | no | 'en_ejecucion'::text |  | en_ejecucion o cerrado. |
| `creado_por` | bigint | no |  | FK | Usuario que creó la fila (obra.usuario.id). |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |

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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `fecha_fin_anterior` | date | no |  |  | Fin de plazo antes del cambio. |
| `fecha_fin_nueva` | date | no |  |  | Fin de plazo después del cambio. |
| `motivo` | text | no |  |  | Por qué cambió el plazo. |
| `autor` | bigint | no |  | FK | Usuario que cambió el plazo. |
| `fecha` | timestamptz | no | now() |  | Momento del cambio. |


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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `nombre` | text | no |  |  | Nombre completo para mostrar. |
| `usuario` | text | no |  |  | Nombre de usuario para entrar; único. |
| `hash_clave` | text | no |  |  | Contraseña con Argon2id; nunca se guarda la clave. |
| `estado` | text | no | 'activo'::text |  | activo o baja. |
| `fecha_baja` | timestamptz | sí |  |  | Desde cuándo está de baja; nulo si está activo. |
| `intentos_fallidos` | smallint | no | 0 |  | Intentos de entrada fallidos seguidos; al llegar al tope se bloquea la cuenta. |
| `bloqueado_hasta` | timestamptz | sí |  |  | Hasta cuándo no puede entrar tras demasiados intentos; nulo si no está bloqueado. |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |
| `dni` | text | sí |  |  | DNI de la persona (8 dígitos); único entre los usuarios; nulo si no se registró. |
| `correo` | text | sí |  |  | Correo de contacto; único sin importar mayúsculas; nulo si no se registró. |
| `celular` | text | sí |  |  | Celular de contacto (9 dígitos, empieza en 9); nulo si no se registró. |
| `pin_hash` | text | sí |  |  | PIN de 6 dígitos del panel de administración, con Argon2id (nunca el PIN en claro). |
| `pin_intentos` | integer | no | 0 |  | Intentos fallidos seguidos del PIN; al 5.º se bloquea un rato. |
| `pin_bloqueado_hasta` | timestamptz | sí |  |  | Hasta cuándo el PIN está bloqueado por intentos fallidos. |

**Únicos:** `(lower(correo))` si `correo IS NOT NULL` · `(dni)` si `dni IS NOT NULL` · `(lower(usuario))`

**CHECK:**
- `ck_usuario_baja_con_fecha`: `((estado = 'baja'::text) = (fecha_baja IS NOT NULL))`
- `ck_usuario_celular`: `((celular IS NULL) OR (celular ~ '^9\d{8}$'::text))`
- `ck_usuario_correo`: `((correo IS NULL) OR (correo ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'::text))`
- `ck_usuario_dni`: `((dni IS NULL) OR (dni ~ '^\d{8}$'::text))`
- `ck_usuario_estado`: `(estado = ANY (ARRAY['activo'::text, 'baja'::text]))`
- `ck_usuario_hash_clave`: `(hash_clave ~ '^\$(argon2(id\|i\|d)\|2[aby])\$'::text)`
- `ck_usuario_intentos`: `(intentos_fallidos >= 0)`
- `ck_usuario_nombre`: `(btrim(nombre) <> ''::text)`
- `ck_usuario_usuario`: `(usuario ~ '^[A-Za-z0-9._-]{3,40}$'::text)`

**Disparadores:** `tg_obra_usuario_no_borrar`, `tg_obra_usuario_no_truncar`

## Módulo `catalogo` · esquema `catalogo`

Código único de insumo, nombres comerciales, factor de conversión, importación del s10.

### `catalogo.insumo` · Insumo

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** El código no cambia · es de la empresa, no de la obra
- **Permisos:** `sigro_svc_catalogo` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `codigo` | text | no |  |  | Código del insumo en el S10; único. |
| `descripcion` | text | no |  |  | Descripción del S10. |
| `unidad_s10` | text | no |  |  | Unidad en que el S10 planifica y valoriza (p. ej. kg). |
| `unidad_compra` | text | no |  |  | Unidad en que se compra y se registra en obra (p. ej. varilla). |
| `factor_conversion` | numeric(18,6) | no | 1 |  | Cuántas unidades del S10 hay en una unidad de compra; sus cambios van al historial. |
| `tipo` | text | no |  |  | material, mano_obra, equipo o subcontrato. |
| `activo` | boolean | no | true |  | Falso si ya no se usa (no se borra). |
| `verificado` | boolean | no | false |  | Verdadero cuando alguien revisó la unidad y el factor. |
| `creado_por` | bigint | sí |  | FK* | Usuario que creó la fila (obra.usuario.id). |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |
| `medida` | text | sí |  |  | Medida comercial (diámetro, tamaño…); ficha del insumo. |
| `norma` | text | sí |  |  | Norma técnica que cumple; ficha del insumo. |
| `marca` | text | sí |  |  | Marca habitual; ficha del insumo. |

**Únicos:** `(codigo)` · `(catalogo.normalizar(descripcion), COALESCE(catalogo.normalizar(medida), ''::text), COALESCE(catalogo.normalizar(marca), ''::text))` si `(medida IS NOT NULL) OR (marca IS NOT NULL)`

**CHECK:**
- `ck_insumo_codigo`: `(btrim(codigo) <> ''::text)`
- `ck_insumo_descripcion`: `(btrim(descripcion) <> ''::text)`
- `ck_insumo_factor`: `(factor_conversion > (0)::numeric)`
- `ck_insumo_marca`: `((marca IS NULL) OR (btrim(marca) <> ''::text))`
- `ck_insumo_medida`: `((medida IS NULL) OR (btrim(medida) <> ''::text))`
- `ck_insumo_norma`: `((norma IS NULL) OR (btrim(norma) <> ''::text))`
- `ck_insumo_tipo`: `(tipo = ANY (ARRAY['material'::text, 'mano_obra'::text, 'equipo'::text, 'subcontrato'::text]))`
- `ck_insumo_unidades`: `((btrim(unidad_s10) <> ''::text) AND (btrim(unidad_compra) <> ''::text))`

**Claves foráneas:**
- `(creado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Índices:** `ix_insumo_codigo_upper (upper(codigo))` · `ix_insumo_material_normalizado (catalogo.normalizar(descripcion))`

**Disparadores:** `tg_catalogo_insumo_no_borrar`, `tg_catalogo_insumo_no_truncar`, `tg_insumo_codigo_inmutable`

### `catalogo.insumo_factor_historial` · Cambio de factor

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: solo se agrega · quién y por qué (HU-17)
- **Permisos:** `sigro_svc_catalogo` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `insumo_id` | bigint | no |  | FK | Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8). |
| `factor_anterior` | numeric(18,6) | no |  |  | Factor antes del cambio. |
| `factor_nuevo` | numeric(18,6) | no |  |  | Factor después del cambio. |
| `motivo` | text | no |  |  | Por qué cambió. |
| `cambiado_por` | bigint | no |  | FK* | Usuario que lo cambió. |
| `cambiado_en` | timestamptz | no | now() |  | Momento del cambio. |


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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `insumo_id` | bigint | no |  | FK | Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8). |
| `nombre` | text | no |  |  | Nombre comercial o de obra. |

**Únicos:** `(insumo_id, lower(nombre))`

**CHECK:**
- `ck_insumo_nombre_comercial_nombre`: `(btrim(nombre) <> ''::text)`

**Claves foráneas:**
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION

**Disparadores:** `tg_catalogo_insumo_nombre_comercial_no_borrar`, `tg_catalogo_insumo_nombre_comercial_no_truncar`

## Módulo `compras` · esquema `compras`

Proveedores, compras, solicitudes, requerimiento y reposición.

### `compras.compra` · Compra

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** No se edita: se anula con motivo · comprobante único por proveedor
- **Permisos:** `sigro_svc_compras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK* | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `proveedor_id` | bigint | no |  | FK | Proveedor (compras.proveedor.id). |
| `tipo_comprobante` | text | no |  |  | factura, boleta, nota_venta u otro. |
| `numero_comprobante` | text | no |  |  | Serie y número del comprobante (p. ej. F001-4512). |
| `fecha` | date | no |  |  | Fecha del comprobante. |
| `moneda` | text | no | 'PEN'::text |  | PEN o USD. |
| `base_imponible` | numeric(14,2) | no |  |  | Importe sin IGV. |
| `igv` | numeric(14,2) | no | 0 |  | IGV del comprobante. |
| `total` | numeric(14,2) | no |  |  | Importe total del comprobante. |
| `estado` | text | no | 'registrada'::text |  | registrada o anulada. |
| `motivo_anulacion` | text | sí |  |  | Por qué se anuló; obligatorio al anular (nada se borra). |
| `anulada_por` | bigint | sí |  | FK* | Usuario que la anuló. |
| `anulada_en` | timestamptz | sí |  |  | Momento de la anulación. |
| `registrado_por` | bigint | no |  | FK* | Usuario que registró la fila (obra.usuario.id). |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |
| `solicitud_id` | bigint | sí |  | FK | Solicitud de compra que atiende, si la hay. |

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

**Índices:** `ix_compra_proveedor_id (proveedor_id)` · `ix_compra_proveedor_registrada (proveedor_id, fecha) WHERE estado = 'registrada'` · `ix_compra_proyecto_id_fecha (proyecto_id, fecha DESC)` · `ix_compra_solicitud_id (solicitud_id) WHERE solicitud_id IS NOT NULL`

**Disparadores:** `tg_compra_solicitud_vigente`, `tg_compra_validar`, `tg_compras_compra_no_borrar`, `tg_compras_compra_no_truncar`

### `compras.compra_adjunto` · Adjunto de compra

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Libro · el mismo archivo no se sube dos veces
- **Permisos:** `sigro_svc_compras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `compra_id` | bigint | no |  | FK | Compra a la que pertenece. |
| `archivo` | text | no |  |  | Clave del archivo en el almacenamiento de objetos. |
| `nombre_original` | text | no |  |  | Nombre del archivo tal como se subió. |
| `tipo_mime` | text | no |  |  | application/pdf, image/jpeg o image/png, decidido por la firma del archivo. |
| `tamano_bytes` | bigint | no |  |  | Tamaño en bytes (hasta 10 MB). |
| `sha256` | character(64) | no |  |  | Huella del contenido; el mismo archivo no se adjunta dos veces a una compra. |
| `subido_por` | bigint | no |  | FK* | Usuario que lo subió. |
| `subido_en` | timestamptz | no | now() |  | Momento de la subida. |
| `almacenamiento` | text | no | 'bd'::text |  | Dónde están los bytes: bd (compra_adjunto_contenido) o s3. |
| `anulado_por` | bigint | sí |  | FK* | Usuario que lo anuló. |
| `anulado_en` | timestamptz | sí |  |  | Momento de la anulación. |
| `motivo_anulacion` | text | sí |  |  | Por qué se anuló; obligatorio al anular (nada se borra). |

**Únicos:** `(compra_id, sha256)`

**CHECK:**
- `ck_compra_adjunto_almacenamiento`: `(almacenamiento = ANY (ARRAY['bd'::text, 's3'::text]))`
- `ck_compra_adjunto_anulacion`: `(((anulado_por IS NULL) AND (anulado_en IS NULL) AND (motivo_anulacion IS NULL)) OR ((anulado_por IS NOT NULL) AND (anulado_en IS NOT NULL) AND (length(btrim(COALESCE(motivo_anulacion, ''::text))) >= 5)))`
- `ck_compra_adjunto_archivo`: `(btrim(archivo) <> ''::text)`
- `ck_compra_adjunto_sha256`: `(sha256 ~ '^[0-9a-f]{64}$'::text)`
- `ck_compra_adjunto_tamano`: `((tamano_bytes > 0) AND (tamano_bytes <= ((10 * 1024) * 1024)))`
- `ck_compra_adjunto_tipo_mime`: `(tipo_mime = ANY (ARRAY['application/pdf'::text, 'image/jpeg'::text, 'image/png'::text]))`

**Claves foráneas:**
- `(anulado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(compra_id)` → `compras.compra(id)` · al borrar: NO ACTION
- `(subido_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Disparadores:** `tg_compra_adjunto_anular`, `tg_compra_adjunto_no_borrar`, `tg_compras_compra_adjunto_no_truncar`

### `compras.compra_adjunto_contenido` · —

- **Tipo:** libro (solo se agrega)
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `adjunto_id` | bigint | no |  | PK FK | Adjunto al que pertenecen (compras.compra_adjunto.id). |
| `contenido` | bytea | no |  |  | Contenido binario del archivo. |


**CHECK:**
- `ck_compra_adjunto_contenido_tamano`: `((length(contenido) >= 1) AND (length(contenido) <= ((10 * 1024) * 1024)))`

**Claves foráneas:**
- `(adjunto_id)` → `compras.compra_adjunto(id)` · al borrar: NO ACTION

**Disparadores:** `tg_compras_compra_adjunto_contenido_append_only`, `tg_compras_compra_adjunto_contenido_no_truncar`

### `compras.compra_detalle` · Línea de compra

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: solo se agrega · un insumo una vez por compra
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `compra_id` | bigint | no |  | FK | Compra a la que pertenece. |
| `insumo_id` | bigint | no |  | FK* | Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8). |
| `cantidad` | numeric(18,4) | no |  |  | Cantidad comprada. |
| `unidad` | text | no |  |  | Unidad de la cantidad (la de compra). |
| `precio_unitario` | numeric(18,4) | no |  |  | Precio por unidad según el comprobante. |
| `precio_referencia` | numeric(18,6) | sí |  |  | Promedio vigente del insumo al registrar; base para avisar precios fuera de lo habitual. |
| `fuera_de_lo_habitual` | boolean | no | false |  | Verdadero si el precio se aparta del de referencia más que el límite (HU-33); avisa, no bloquea. |
| `nota_precio` | text | sí |  |  | Explicación de quien registró cuando el precio salió fuera de lo habitual. |

**Únicos:** `(compra_id, insumo_id)`

**CHECK:**
- `ck_compra_detalle_cantidad`: `(cantidad > (0)::numeric)`
- `ck_compra_detalle_fuera_con_referencia`: `((NOT fuera_de_lo_habitual) OR (precio_referencia IS NOT NULL))`
- `ck_compra_detalle_nota_precio`: `((nota_precio IS NULL) OR ((btrim(nota_precio) <> ''::text) AND (length(nota_precio) <= 300)))`
- `ck_compra_detalle_precio`: `(precio_unitario >= (0)::numeric)`
- `ck_compra_detalle_precio_referencia`: `((precio_referencia IS NULL) OR (precio_referencia > (0)::numeric))`
- `ck_compra_detalle_unidad`: `(btrim(unidad) <> ''::text)`

**Claves foráneas:**
- `(compra_id)` → `compras.compra(id)` · al borrar: NO ACTION
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION · **cruza al servicio catalogo (fase 1)**

**Índices:** `ix_compra_detalle_insumo_id (insumo_id)`

**Disparadores:** `tg_compras_compra_detalle_append_only`, `tg_compras_compra_detalle_no_truncar`

### `compras.insumo_critico` · Marca de insumo crítico

- **Tipo:** libro (solo se agrega) · **Regla:** Historial: la vigente es la última
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK* | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `insumo_id` | bigint | no |  | FK* | Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8). |
| `critico` | boolean | no |  |  | Verdadero si se marcó como crítico; falso si se desmarcó. |
| `registrado_por` | bigint | no |  | FK* | Usuario que registró la fila (obra.usuario.id). |
| `registrado_en` | timestamptz | no | now() |  | Momento de la marca. |


**Claves foráneas:**
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION · **cruza al servicio catalogo (fase 1)**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(registrado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Índices:** `ix_insumo_critico_vigencia (proyecto_id, insumo_id, id DESC)`

**Disparadores:** `tg_compras_insumo_critico_append_only`, `tg_compras_insumo_critico_no_truncar`

### `compras.parametro_reposicion` · Parámetro de reposición

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: cada cambio es una vigencia nueva
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK* | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `insumo_id` | bigint | no |  | FK* | Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8). |
| `stock_seguridad` | numeric(18,4) | no |  |  | Cantidad mínima que debe quedar para no detener la obra. |
| `punto_reposicion` | numeric(18,4) | no |  |  | Al bajar de esta cantidad se sugiere comprar. |
| `registrado_por` | bigint | no |  | FK* | Usuario que registró la fila (obra.usuario.id). |
| `vigente_desde` | timestamptz | no | now() |  | Desde cuándo rige este parámetro; vale el último. |
| `nivel_maximo` | numeric(18,4) | sí |  |  | Hasta cuánto conviene reponer. |


**CHECK:**
- `ck_parametro_reposicion_nivel_maximo`: `((nivel_maximo IS NULL) OR (nivel_maximo > punto_reposicion))`
- `ck_parametro_reposicion_valores`: `((stock_seguridad >= (0)::numeric) AND (punto_reposicion >= stock_seguridad))`

**Claves foráneas:**
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION · **cruza al servicio catalogo (fase 1)**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(registrado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Índices:** `ix_parametro_reposicion_vigencia (proyecto_id, insumo_id, id DESC)`

**Disparadores:** `tg_compras_parametro_reposicion_append_only`, `tg_compras_parametro_reposicion_no_truncar`

### `compras.partida` · Partida del presupuesto

- **Tipo:** libro (solo se agrega) · **Regla:** Viene del APU del S10
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK* | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `codigo` | text | no |  |  | Código de la partida en el S10. |
| `descripcion` | text | no |  |  | Descripción de la partida. |
| `unidad` | text | no |  |  | Unidad de medida de la partida. |
| `creado_por` | bigint | no |  | FK* | Usuario que creó la fila (obra.usuario.id). |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |

**Únicos:** `(proyecto_id, codigo, lower(btrim(descripcion)))` · `(proyecto_id, id)`

**CHECK:**
- `ck_partida_textos`: `((btrim(codigo) <> ''::text) AND (btrim(descripcion) <> ''::text) AND (btrim(unidad) <> ''::text))`

**Claves foráneas:**
- `(creado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Disparadores:** `tg_compras_partida_append_only`, `tg_compras_partida_no_truncar`

### `compras.partida_recurso` · Recurso de la partida

- **Tipo:** libro (solo se agrega) · **Regla:** Qué insumos consume cada partida
- **Permisos:** `sigro_svc_compras` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `partida_id` | bigint | no |  | FK | Partida (compras.partida.id). |
| `insumo_id` | bigint | no |  | FK* | Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8). |
| `cantidad_por_unidad` | numeric(18,6) | no |  |  | Cantidad del insumo por unidad de partida. |
| `unidad` | text | no |  |  | Unidad de esa cantidad. |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |

**Únicos:** `(partida_id, insumo_id)`

**CHECK:**
- `ck_partida_recurso_cantidad`: `(cantidad_por_unidad > (0)::numeric)`
- `ck_partida_recurso_unidad`: `(btrim(unidad) <> ''::text)`

**Claves foráneas:**
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION · **cruza al servicio catalogo (fase 1)**
- `(partida_id)` → `compras.partida(id)` · al borrar: NO ACTION

**Índices:** `ix_partida_recurso_insumo_id (insumo_id)`

**Disparadores:** `tg_compras_partida_recurso_append_only`, `tg_compras_partida_recurso_no_truncar`

### `compras.proveedor` · Proveedor

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Es de la empresa, no de la obra
- **Permisos:** `sigro_svc_compras` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `ruc` | text | no |  |  | RUC de 11 dígitos; único. |
| `razon_social` | text | no |  |  | Razón social según SUNAT. |
| `estado_validacion` | text | no | 'sin_validar'::text |  | validado, sin_validar o con_alerta_sunat. |
| `validado_en` | timestamptz | sí |  |  | Momento de la última validación correcta en SUNAT. |
| `estado` | text | no | 'activo'::text |  | activo o baja. |
| `fecha_baja` | timestamptz | sí |  |  | Desde cuándo está de baja; nulo si está activo. |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |
| `alerta_sunat` | text | sí |  |  | Texto de la alerta de SUNAT (no habido, baja de oficio…), si la hay. |
| `consultado_en` | timestamptz | sí |  |  | Momento de la última consulta a SUNAT, haya salido bien o no. |

**Únicos:** `(ruc)`

**CHECK:**
- `ck_proveedor_alerta_sunat`: `(((estado_validacion = 'con_alerta_sunat'::text) = (alerta_sunat IS NOT NULL)) AND ((alerta_sunat IS NULL) OR (btrim(alerta_sunat) <> ''::text)))`
- `ck_proveedor_baja_con_fecha`: `((estado = 'baja'::text) = (fecha_baja IS NOT NULL))`
- `ck_proveedor_estado`: `(estado = ANY (ARRAY['activo'::text, 'baja'::text]))`
- `ck_proveedor_estado_validacion`: `(estado_validacion = ANY (ARRAY['validado'::text, 'sin_validar'::text, 'con_alerta_sunat'::text]))`
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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK* | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `insumo_id` | bigint | no |  | FK* | Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8). |
| `cantidad_planificada` | numeric(18,4) | no |  |  | Cantidad planificada vigente (incluye desperdicio y reserva; pasarse avisa, regla 7). |
| `precio_planificado` | numeric(18,4) | no |  |  | Precio unitario planificado en el S10. |
| `creado_por` | bigint | no |  | FK* | Usuario que creó la fila (obra.usuario.id). |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |
| `origen` | text | no | 's10'::text |  | s10 o manual. |
| `motivo_alta` | text | sí |  |  | Por qué se agregó a mano (obligatorio si origen = manual). |

**Únicos:** `(proyecto_id, insumo_id)`

**CHECK:**
- `ck_requerimiento_cantidad`: `(cantidad_planificada > (0)::numeric)`
- `ck_requerimiento_motivo_alta`: `(((origen = 's10'::text) AND (motivo_alta IS NULL)) OR ((origen = 'manual'::text) AND (btrim(COALESCE(motivo_alta, ''::text)) <> ''::text)))`
- `ck_requerimiento_origen`: `(origen = ANY (ARRAY['s10'::text, 'manual'::text]))`
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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `requerimiento_id` | bigint | no |  | FK | Línea del requerimiento ajustada. |
| `cantidad_anterior` | numeric(18,4) | no |  |  | Cantidad antes del ajuste. |
| `cantidad_nueva` | numeric(18,4) | no |  |  | Cantidad después del ajuste. |
| `motivo` | text | no |  |  | Por qué se ajustó. |
| `ajustado_por` | bigint | no |  | FK* | Usuario que ajustó. |
| `ajustado_en` | timestamptz | no | now() |  | Momento del ajuste. |


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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK* | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `numero` | integer | no |  |  | Correlativo de la solicitud dentro de la obra. |
| `fecha` | date | no | CURRENT_DATE |  | Fecha de la solicitud. |
| `estado` | text | no | 'emitida'::text |  | emitida o anulada. |
| `motivo_anulacion` | text | sí |  |  | Por qué se anuló; obligatorio al anular (nada se borra). |
| `anulada_por` | bigint | sí |  | FK* | Usuario que la anuló. |
| `anulada_en` | timestamptz | sí |  |  | Momento de la anulación. |
| `emitida_por` | bigint | no |  | FK* | Usuario que la emitió. |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |

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
| `proyecto_id` | bigint | no |  | PK FK* | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `ultimo` | integer | no | 0 |  | Último número emitido en la obra. |


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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `solicitud_id` | bigint | no |  | FK | Solicitud a la que pertenece. |
| `insumo_id` | bigint | sí |  | FK* | Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8). |
| `descripcion_libre` | text | sí |  |  | Descripción cuando lo pedido aún no está en el catálogo. |
| `cantidad` | numeric(18,4) | no |  |  | Cantidad pedida, en unidad de compra. |
| `unidad_compra` | text | no |  |  | Unidad de compra de la cantidad. |

**Únicos:** `(solicitud_id, insumo_id)` si `insumo_id IS NOT NULL`

**CHECK:**
- `ck_solicitud_linea_cantidad`: `(cantidad > (0)::numeric)`
- `ck_solicitud_linea_describe`: `((insumo_id IS NOT NULL) OR (btrim(COALESCE(descripcion_libre, ''::text)) <> ''::text))`
- `ck_solicitud_linea_unidad`: `(btrim(unidad_compra) <> ''::text)`

**Claves foráneas:**
- `(insumo_id)` → `catalogo.insumo(id)` · al borrar: NO ACTION · **cruza al servicio catalogo (fase 1)**
- `(solicitud_id)` → `compras.solicitud(id)` · al borrar: NO ACTION

**Índices:** `ix_solicitud_linea_insumo_id (insumo_id) WHERE insumo_id IS NOT NULL` · `ix_solicitud_linea_solicitud_id (solicitud_id)`

**Disparadores:** `tg_compras_solicitud_linea_append_only`, `tg_compras_solicitud_linea_no_truncar`, `tg_solicitud_linea_vigente`

## Módulo `tareo` · esquema `tareo`

Trabajadores y asistencia (horas hombre).

### `tareo.jornada` · Jornada

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: se corrige agregando otra · una vigente por trabajador y día
- **Permisos:** `sigro_svc_tareo` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `uuid_cliente` | uuid | sí |  |  | Identificador generado en el dispositivo; clave de idempotencia: un reenvío no duplica (regla 3). |
| `proyecto_id` | bigint | no |  | FK* | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `trabajador_id` | bigint | no |  | FK | Trabajador (tareo.trabajador.id). |
| `fecha` | date | no |  | FK | Día trabajado. |
| `estado` | text | no |  |  | presente, ausente o permiso. |
| `horas_normales` | numeric(4,2) | no | 0 |  | Horas normales del día. |
| `horas_extra` | numeric(4,2) | no | 0 |  | Horas extra del día. |
| `categoria` | text | no |  |  | Categoría del trabajador ese día (operario, oficial o peon). |
| `periodo_id` | bigint | no |  | FK* | Periodo de la obra al que se imputa (obra.periodo.id). |
| `registrado_por` | bigint | no |  | FK* | Usuario que registró la fila (obra.usuario.id). |
| `fecha_servidor` | timestamptz | no | now() |  | Hora del servidor al recibir el registro; es la que vale para el libro y los cortes. |
| `corrige_a_id` | bigint | sí |  | FK | Jornada que esta fila corrige, si es una corrección. |
| `motivo_correccion` | text | sí |  |  | Por qué se corrigió. |

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
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  | FK* | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `nombre` | text | no |  |  | Nombre completo para mostrar; desde la 0020 la API lo arma como «Paterno Materno, Nombres». |
| `tipo_documento` | text | no | 'DNI'::text |  | DNI, CE o PASAPORTE. |
| `documento` | text | no |  |  | Número de documento. |
| `categoria` | text | no |  |  | operario, oficial o peon. |
| `cuadrilla` | text | no |  |  | Cuadrilla a la que pertenece. |
| `fecha_ingreso` | date | no |  |  | Primer día en la obra. |
| `fecha_salida` | date | sí |  |  | Último día en la obra; nulo si sigue. |
| `registrado_por` | bigint | no |  | FK* | Usuario que registró la fila (obra.usuario.id). |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |
| `nombres` | text | sí |  |  | Nombres de pila (de RENIEC si se consultó el DNI); nulo en los registrados antes de la 0020. |
| `apellido_paterno` | text | sí |  |  | Apellido paterno; obligatorio si se registran nombres por separado. |
| `apellido_materno` | text | sí |  |  | Apellido materno; puede faltar (personas con un solo apellido). |

**Únicos:** `(id, proyecto_id)` · `(proyecto_id, documento)`

**CHECK:**
- `ck_trabajador_categoria`: `(categoria = ANY (ARRAY['operario'::text, 'oficial'::text, 'peon'::text]))`
- `ck_trabajador_cuadrilla`: `(btrim(cuadrilla) <> ''::text)`
- `ck_trabajador_documento`: `(((tipo_documento = 'DNI'::text) AND (documento ~ '^[0-9]{8}$'::text)) OR ((tipo_documento <> 'DNI'::text) AND (documento ~ '^[A-Za-z0-9]{6,20}$'::text)))`
- `ck_trabajador_nombre`: `(btrim(nombre) <> ''::text)`
- `ck_trabajador_nombres_y_apellidos`: `((nombres IS NULL) OR ((btrim(nombres) <> ''::text) AND (apellido_paterno IS NOT NULL) AND (btrim(apellido_paterno) <> ''::text)))`
- `ck_trabajador_salida`: `((fecha_salida IS NULL) OR (fecha_salida >= fecha_ingreso))`
- `ck_trabajador_tipo_documento`: `(tipo_documento = ANY (ARRAY['DNI'::text, 'CE'::text, 'PASAPORTE'::text]))`

**Claves foráneas:**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**
- `(registrado_por)` → `obra.usuario(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Índices:** `ix_trabajador_proyecto_id_cuadrilla (proyecto_id, cuadrilla)`

**Disparadores:** `tg_tareo_trabajador_no_borrar`, `tg_tareo_trabajador_no_truncar`, `tg_trabajador_validar`

## Módulo `almacen` · esquema `almacen`

Libro de movimientos (solo insert), recepciones, traslados, conteos y préstamos de herramientas; saldo y kárdex derivados.

### `almacen.almacen_ref` · Almacén (réplica en almacén)

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Copia local de obra.almacen: almacén no lee el esquema obra
- **Permisos:** `sigro_svc_almacen` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Identificador interno. |
| `proyecto_id` | bigint | no |  |  | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `nombre` | text | no |  |  | Nombre del almacén. |
| `tipo` | text | no |  |  | central o frente. |
| `estado` | text | no |  |  | activo o inactivo. |
| `sincronizado_en` | timestamptz | no | now() |  | Última vez que se copió desde obras. |

**Únicos:** `(id, proyecto_id)`

**CHECK:**
- `ck_almacen_ref_estado`: `(estado = ANY (ARRAY['activo'::text, 'inactivo'::text]))`
- `ck_almacen_ref_tipo`: `(tipo = ANY (ARRAY['central'::text, 'frente'::text]))`

**Disparadores:** `tg_almacen_almacen_ref_no_borrar`, `tg_almacen_almacen_ref_no_truncar`

### `almacen.conteo` · Conteo físico

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Al confirmarlo, cada diferencia es un ajuste con motivo
- **Permisos:** `sigro_svc_almacen` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `uuid_cliente` | uuid | no |  |  | Identificador generado en el dispositivo; clave de idempotencia: un reenvío no duplica (regla 3). |
| `proyecto_id` | bigint | no |  | FK | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `almacen_id` | bigint | no |  | FK | Almacén de la obra (obra.almacen.id). |
| `fecha` | date | no |  |  | Día del conteo. |
| `estado` | text | no | 'borrador'::text |  | borrador o confirmado. |
| `es_cierre` | boolean | no | false |  | Verdadero si es el conteo de cierre de la obra. |
| `registrado_por` | bigint | no |  |  | Usuario que registró la fila (obra.usuario.id). |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |
| `confirmado_por` | bigint | sí |  |  | Usuario que confirmó el conteo. |
| `confirmado_en` | timestamptz | sí |  |  | Momento de la confirmación. |

**Únicos:** `(almacen_id)` si `estado = 'borrador'` · `(uuid_cliente)`

**CHECK:**
- `ck_conteo_confirmacion`: `(((estado = 'borrador'::text) AND (confirmado_por IS NULL) AND (confirmado_en IS NULL)) OR ((estado = 'confirmado'::text) AND (confirmado_por IS NOT NULL) AND (confirmado_en IS NOT NULL)))`
- `ck_conteo_estado`: `(estado = ANY (ARRAY['borrador'::text, 'confirmado'::text]))`

**Claves foráneas:**
- `(almacen_id, proyecto_id)` → `almacen.almacen_ref(id, proyecto_id)` · al borrar: NO ACTION

**Disparadores:** `tg_almacen_conteo_no_borrar`, `tg_almacen_conteo_no_truncar`, `tg_conteo_cambios`

### `almacen.conteo_linea` · Línea del conteo

- **Tipo:** libro (solo se agrega)
- **Permisos:** `sigro_svc_almacen` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `conteo_id` | bigint | no |  | FK | Conteo al que pertenece. |
| `insumo_id` | bigint | no |  | FK | Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8). |
| `saldo_teorico` | numeric(18,4) | no |  |  | Saldo que daba el libro al contar. |
| `cantidad_contada` | numeric(18,4) | no |  |  | Cantidad encontrada físicamente. |
| `diferencia` | numeric(18,4) | sí | (cantidad_contada - saldo_teorico) |  | Contada menos teórica; genera el ajuste. |
| `motivo` | text | sí |  |  | Explicación de la diferencia. |

**Únicos:** `(conteo_id, insumo_id)`

**CHECK:**
- `ck_conteo_linea_contada`: `(cantidad_contada >= (0)::numeric)`
- `ck_conteo_linea_motivo`: `((cantidad_contada = saldo_teorico) OR (btrim(COALESCE(motivo, ''::text)) <> ''::text))`

**Claves foráneas:**
- `(conteo_id)` → `almacen.conteo(id)` · al borrar: NO ACTION
- `(insumo_id)` → `almacen.insumo_ref(id)` · al borrar: NO ACTION

**Disparadores:** `tg_almacen_conteo_linea_append_only`, `tg_almacen_conteo_linea_no_truncar`, `tg_conteo_linea_validar`

### `almacen.insumo_ref` · Insumo (réplica en almacén)

- **Tipo:** maestra / transaccional (no se borra) · **Regla:** Copia local del catálogo
- **Permisos:** `sigro_svc_almacen` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no |  | PK | Identificador interno. |
| `codigo` | text | no |  |  | Código del S10. |
| `descripcion` | text | no |  |  | Descripción del S10. |
| `unidad` | text | no |  |  | Unidad del S10. |
| `tipo` | text | no |  |  | material o equipo. |
| `activo` | boolean | no |  |  | Falso si ya no se usa. |
| `sincronizado_en` | timestamptz | no | now() |  | Última vez que se copió desde catálogo. |

**Únicos:** `(codigo)`

**CHECK:**
- `ck_insumo_ref_tipo`: `(tipo = ANY (ARRAY['material'::text, 'equipo'::text]))`

**Disparadores:** `tg_almacen_insumo_ref_no_borrar`, `tg_almacen_insumo_ref_no_truncar`

### `almacen.movimiento` · Movimiento

- **Tipo:** libro (solo se agrega) · **Regla:** Libro: solo se agrega · sin saldo guardado · corregir = reverso
- **Permisos:** `sigro_svc_almacen` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `uuid_cliente` | uuid | no |  |  | Identificador generado en el dispositivo; clave de idempotencia: un reenvío no duplica (regla 3). |
| `proyecto_id` | bigint | no |  | FK | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `almacen_id` | bigint | no |  | FK | Almacén de la obra (obra.almacen.id). |
| `insumo_id` | bigint | no |  | FK | Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8). |
| `tipo` | text | no |  |  | ingreso_compra, ingreso_sin_compra, salida, traslado_salida, traslado_ingreso, ajuste_conteo o reverso. |
| `sentido` | smallint | no |  |  | 1 si entra al almacén, -1 si sale. |
| `cantidad` | numeric(18,4) | no |  |  | Cantidad movida, en la unidad del S10. |
| `costo_unitario` | numeric(18,6) | no |  |  | Costo por unidad: en un ingreso, el de compra; en una salida, el promedio ponderado congelado en ese instante (regla 4). |
| `moneda` | text | no | 'PEN'::text |  | PEN o USD. |
| `motivo` | text | sí |  |  | Motivo del movimiento (obligatorio en reversos y ajustes). |
| `quien_retira` | text | sí |  |  | Persona que retira el material en una salida. |
| `frente` | text | sí |  |  | Frente de trabajo al que va la salida. |
| `origen` | text | sí |  |  | De dónde vino un ingreso sin compra: saldo_otra_obra, devolucion_cuadrilla, prestamo, sobrante u otro. |
| `origen_detalle` | text | sí |  |  | Detalle libre del origen. |
| `recepcion_id` | bigint | sí |  | FK | Recepción de compra que originó el ingreso. |
| `traslado_id` | bigint | sí |  | FK | Traslado al que pertenece este movimiento. |
| `conteo_id` | bigint | sí |  | FK | Conteo que originó el ajuste. |
| `revierte_a_id` | bigint | sí |  | FK | Movimiento que este reverso anula (uno solo por movimiento). |
| `periodo_id` | bigint | sí |  |  | Periodo de la obra al que se imputa (obra.periodo.id). |
| `dispositivo_id` | bigint | sí |  |  | Dispositivo autorizado desde el que se registró (obra.dispositivo.id, regla 5). |
| `registrado_por` | bigint | no |  |  | Usuario que registró la fila (obra.usuario.id). |
| `fecha_dispositivo` | timestamptz | sí |  |  | Hora del celular al registrar (sin señal puede ir antes que fecha_servidor); dato informativo. |
| `fecha_servidor` | timestamptz | no | now() |  | Hora del servidor al recibir el registro; es la que vale para el libro y los cortes. |

**Únicos:** `(revierte_a_id)` · `(uuid_cliente)`

**CHECK:**
- `ck_movimiento_cantidad`: `(cantidad > (0)::numeric)`
- `ck_movimiento_costo`: `(costo_unitario >= (0)::numeric)`
- `ck_movimiento_moneda`: `(moneda = ANY (ARRAY['PEN'::text, 'USD'::text]))`
- `ck_movimiento_por_tipo`: `CHECK ( CASE tipo     WHEN 'ingreso_compra'::text THEN ((sentido = 1) AND (recepcion_id IS NOT NULL))     WHEN 'ingreso_sin_compra'::text THEN ((sentido = 1) AND (origen = ANY (ARRAY['saldo_otra_obra'::text, 'devolucion_cuadrilla'::text, 'prestamo'::text, 'sobrante'::text, 'otro'::text])) AND (costo_unitario > (0)::numeric) AND ((origen <> ALL (ARRAY['otro'::text, 'saldo_otra_obra'::text])) OR (btrim(COALESCE(origen_detalle, ''::text)) <> ''::text)))     WHEN 'salida'::text THEN ((sentido = '-1'::integer) AND (btrim(COALESCE(motivo, ''::text)) <> ''::text) AND (btrim(COALESCE(quien_retira, ''::text)) <> ''::text))     WHEN 'traslado_salida'::text THEN ((sentido = '-1'::integer) AND (traslado_id IS NOT NULL))     WHEN 'traslado_ingreso'::text THEN ((sentido = 1) AND (traslado_id IS NOT NULL))     WHEN 'ajuste_conteo'::text THEN ((conteo_id IS NOT NULL) AND (btrim(COALESCE(motivo, ''::text)) <> ''::text))     WHEN 'reverso'::text THEN ((revierte_a_id IS NOT NULL) AND (btrim(COALESCE(motivo, ''::text)) <> ''::text))     ELSE NULL::boolean END)`
- `ck_movimiento_sentido`: `(sentido = ANY (ARRAY['-1'::integer, 1]))`
- `ck_movimiento_solo_su_referencia`: `(((recepcion_id IS NULL) OR (tipo = ANY (ARRAY['ingreso_compra'::text, 'reverso'::text]))) AND ((traslado_id IS NULL) OR (tipo = ANY (ARRAY['traslado_salida'::text, 'traslado_ingreso'::text, 'reverso'::text]))) AND ((conteo_id IS NULL) OR (tipo = 'ajuste_conteo'::text)) AND ((revierte_a_id IS NULL) OR (tipo = 'reverso'::text)))`
- `ck_movimiento_tipo`: `(tipo = ANY (ARRAY['ingreso_compra'::text, 'ingreso_sin_compra'::text, 'salida'::text, 'traslado_salida'::text, 'traslado_ingreso'::text, 'ajuste_conteo'::text, 'reverso'::text]))`

**Claves foráneas:**
- `(almacen_id, proyecto_id)` → `almacen.almacen_ref(id, proyecto_id)` · al borrar: NO ACTION
- `(conteo_id)` → `almacen.conteo(id)` · al borrar: NO ACTION
- `(insumo_id)` → `almacen.insumo_ref(id)` · al borrar: NO ACTION
- `(recepcion_id, proyecto_id)` → `almacen.recepcion(id, proyecto_id)` · al borrar: NO ACTION
- `(revierte_a_id)` → `almacen.movimiento(id)` · al borrar: NO ACTION
- `(traslado_id)` → `almacen.traslado(id)` · al borrar: NO ACTION

**Índices:** `ix_movimiento_conteo_id (conteo_id) WHERE conteo_id IS NOT NULL` · `ix_movimiento_proyecto_fecha (proyecto_id, fecha_servidor)` · `ix_movimiento_recepcion_id (recepcion_id) WHERE recepcion_id IS NOT NULL` · `ix_movimiento_saldo (almacen_id, insumo_id) INCLUDE (sentido, cantidad, costo_unitario)` · `ix_movimiento_traslado_id (traslado_id) WHERE traslado_id IS NOT NULL`

**Disparadores:** `tg_almacen_movimiento_append_only`, `tg_almacen_movimiento_no_truncar`, `tg_movimiento_validar`

### `almacen.prestamo` · Préstamo de herramienta

- **Tipo:** transaccional · **Regla:** No mueve el saldo · no se presta más de lo disponible · se devuelve una vez
- **Permisos:** `sigro_svc_almacen` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `uuid_cliente` | uuid | no |  |  | Identificador que genera el cliente (celular o web): reenviar el mismo préstamo no lo duplica (R3). |
| `proyecto_id` | bigint | no |  | FK | Obra donde se presta la herramienta. |
| `almacen_id` | bigint | no |  | FK | Almacén de la obra de donde sale la herramienta. |
| `insumo_id` | bigint | no |  | FK | Herramienta o equipo prestado (insumo de tipo «equipo» del catálogo S10). |
| `cantidad` | numeric(18,4) | no |  |  | Cuántas unidades se prestan, en la unidad del S10. |
| `trabajador_id` | bigint | sí |  |  | Trabajador de la obra que la lleva, si está registrado (referencia lógica a tareo.trabajador). |
| `responsable` | text | no |  |  | Nombre de quien se la lleva, tal como se registró (queda aunque cambie el trabajador). |
| `devolver_el` | date | sí |  |  | Fecha en que debe devolverla; si pasa sin devolución, el préstamo se ve «atrasado». |
| `nota` | text | sí |  |  | Para qué se la llevan u otra observación al prestarla. |
| `registrado_por` | bigint | no |  |  | Usuario que registró el préstamo. |
| `dispositivo_id` | bigint | sí |  |  | Celular autorizado desde el que se registró, si vino de la app. |
| `fecha_dispositivo` | timestamptz | sí |  |  | Hora del celular al registrarlo (puede diferir de la del servidor). |
| `prestado_en` | timestamptz | no | now() |  | Hora del servidor en que se registró el préstamo. |
| `devuelto_en` | timestamptz | sí |  |  | Hora del servidor en que se registró la devolución; vacío mientras no se devuelve. |
| `devuelto_por` | bigint | sí |  |  | Usuario que registró la devolución. |
| `estado_devolucion` | text | sí |  |  | Cómo volvió: bueno, danado (dañado) o perdido. |
| `nota_devolucion` | text | sí |  |  | Qué pasó con la herramienta; obligatoria si volvió dañada o se perdió. |

**Únicos:** `(uuid_cliente)`

**CHECK:**
- `ck_prestamo_cantidad`: `(cantidad > (0)::numeric)`
- `ck_prestamo_devolucion`: `(((devuelto_en IS NULL) AND (devuelto_por IS NULL) AND (estado_devolucion IS NULL) AND (nota_devolucion IS NULL)) OR ((devuelto_en IS NOT NULL) AND (devuelto_por IS NOT NULL) AND (estado_devolucion IS NOT NULL)))`
- `ck_prestamo_estado`: `((estado_devolucion IS NULL) OR (estado_devolucion = ANY (ARRAY['bueno'::text, 'danado'::text, 'perdido'::text])))`
- `ck_prestamo_nota`: `((nota IS NULL) OR (length(nota) <= 300))`
- `ck_prestamo_nota_devolucion`: `((COALESCE(estado_devolucion, 'bueno'::text) = 'bueno'::text) OR ((nota_devolucion IS NOT NULL) AND (btrim(nota_devolucion) <> ''::text)))`
- `ck_prestamo_nota_devolucion_largo`: `((nota_devolucion IS NULL) OR (length(nota_devolucion) <= 300))`
- `ck_prestamo_responsable`: `((btrim(responsable) <> ''::text) AND (length(responsable) <= 120))`

**Claves foráneas:**
- `(almacen_id, proyecto_id)` → `almacen.almacen_ref(id, proyecto_id)` · al borrar: NO ACTION
- `(insumo_id)` → `almacen.insumo_ref(id)` · al borrar: NO ACTION

**Índices:** `ix_prestamo_abierto (proyecto_id, almacen_id, insumo_id) WHERE devuelto_en IS NULL`

**Disparadores:** `tg_almacen_prestamo_no_truncar`, `tg_prestamo_borrado`, `tg_prestamo_cambios`

### `almacen.recepcion` · Recepción de compra

- **Tipo:** libro (solo se agrega) · **Regla:** Genera los ingresos de la compra
- **Permisos:** `sigro_svc_almacen` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `uuid_cliente` | uuid | no |  |  | Identificador generado en el dispositivo; clave de idempotencia: un reenvío no duplica (regla 3). |
| `proyecto_id` | bigint | no |  | FK | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `almacen_id` | bigint | no |  | FK | Almacén de la obra (obra.almacen.id). |
| `compra_id` | bigint | no |  |  | Compra recibida (compras.compra.id). |
| `motivo_exceso` | text | sí |  |  | Por qué se recibió más de lo comprado, si pasó. |
| `registrado_por` | bigint | no |  |  | Usuario que registró la fila (obra.usuario.id). |
| `dispositivo_id` | bigint | sí |  |  | Dispositivo autorizado desde el que se registró (obra.dispositivo.id, regla 5). |
| `fecha_dispositivo` | timestamptz | sí |  |  | Hora del celular al registrar (sin señal puede ir antes que fecha_servidor); dato informativo. |
| `fecha_servidor` | timestamptz | no | now() |  | Hora del servidor al recibir el registro; es la que vale para el libro y los cortes. |

**Únicos:** `(id, proyecto_id)` · `(uuid_cliente)`

**CHECK:**
- `ck_recepcion_motivo_exceso`: `((motivo_exceso IS NULL) OR (btrim(motivo_exceso) <> ''::text))`

**Claves foráneas:**
- `(almacen_id, proyecto_id)` → `almacen.almacen_ref(id, proyecto_id)` · al borrar: NO ACTION

**Índices:** `ix_recepcion_compra_id (compra_id)`

**Disparadores:** `tg_almacen_recepcion_append_only`, `tg_almacen_recepcion_no_truncar`

### `almacen.traslado` · Traslado

- **Tipo:** libro (solo se agrega) · **Regla:** Una salida y un ingreso que se anulan juntos
- **Permisos:** `sigro_svc_almacen` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `uuid_cliente` | uuid | no |  |  | Identificador generado en el dispositivo; clave de idempotencia: un reenvío no duplica (regla 3). |
| `origen_proyecto_id` | bigint | no |  | FK | Obra de donde sale. |
| `origen_almacen_id` | bigint | no |  | FK | Almacén de donde sale. |
| `destino_proyecto_id` | bigint | no |  | FK | Obra a donde llega. |
| `destino_almacen_id` | bigint | no |  | FK | Almacén a donde llega. |
| `motivo` | text | no |  |  | Por qué se traslada. |
| `es_cierre` | boolean | no | false |  | Verdadero si es parte del cierre de la obra (HU-47). |
| `registrado_por` | bigint | no |  |  | Usuario que registró la fila (obra.usuario.id). |
| `fecha_servidor` | timestamptz | no | now() |  | Hora del servidor al recibir el registro; es la que vale para el libro y los cortes. |

**Únicos:** `(uuid_cliente)`

**CHECK:**
- `ck_traslado_distinto`: `(origen_almacen_id <> destino_almacen_id)`
- `ck_traslado_motivo`: `(btrim(motivo) <> ''::text)`

**Claves foráneas:**
- `(destino_almacen_id, destino_proyecto_id)` → `almacen.almacen_ref(id, proyecto_id)` · al borrar: NO ACTION
- `(origen_almacen_id, origen_proyecto_id)` → `almacen.almacen_ref(id, proyecto_id)` · al borrar: NO ACTION

**Disparadores:** `tg_almacen_traslado_append_only`, `tg_almacen_traslado_no_truncar`

## Módulo `costo` · esquema `costo`

Modelo de lectura del gasto: hechos valorizados que llegan de almacén por el outbox (relevocosto).

### `costo.evento_recibido` · Evento recibido

- **Tipo:** libro (solo se agrega) · **Regla:** Deduplica: un evento se aplica una vez
- **Permisos:** `sigro_svc_costo` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `evento_id` | bigint | no |  | PK | Identificador del evento en el outbox. |
| `tipo` | text | no |  |  | Tipo de evento. |
| `recibido_en` | timestamptz | no | now() |  | Momento en que se procesó. |


**Disparadores:** `tg_costo_evento_recibido_append_only`, `tg_costo_evento_recibido_no_truncar`

### `costo.hecho` · Hecho de costo

- **Tipo:** libro (solo se agrega) · **Regla:** Llega de almacén por el outbox (RelevoCosto)
- **Permisos:** `sigro_svc_costo` S I

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `evento_id` | bigint | no |  | FK | Evento del outbox que originó el hecho. |
| `proyecto_id` | bigint | no |  |  | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `familia` | text | no |  |  | material, mano_obra o equipo. |
| `clase` | text | no |  |  | ingreso, consumo, traslado, ajuste, compra u horas. |
| `insumo_codigo` | text | no |  |  | Código del insumo en el S10. |
| `cantidad` | numeric(18,4) | no |  |  | Cantidad del hecho. |
| `unidad` | text | no |  |  | Unidad de la cantidad. |
| `costo_unitario` | numeric(18,6) | no |  |  | Costo por unidad congelado en el hecho. |
| `importe` | numeric(18,2) | sí | round((cantidad * costo_unitario), 2) |  | Cantidad por costo unitario. |
| `moneda` | text | no | 'PEN'::text |  | PEN o USD. |
| `fecha` | date | no |  |  | Día del hecho. |
| `referencia` | text | no |  |  | Registro de origen para volver a él (p. ej. almacen.movimiento:123). |

**Únicos:** `(referencia, clase)`

**CHECK:**
- `ck_hecho_clase`: `(clase = ANY (ARRAY['ingreso'::text, 'consumo'::text, 'traslado'::text, 'ajuste'::text, 'compra'::text, 'horas'::text]))`
- `ck_hecho_familia`: `(familia = ANY (ARRAY['material'::text, 'mano_obra'::text, 'equipo'::text]))`
- `ck_hecho_moneda`: `(moneda = ANY (ARRAY['PEN'::text, 'USD'::text]))`

**Claves foráneas:**
- `(evento_id)` → `costo.evento_recibido(evento_id)` · al borrar: NO ACTION

**Índices:** `ix_hecho_proyecto (proyecto_id, familia, fecha)`

**Disparadores:** `tg_costo_hecho_append_only`, `tg_costo_hecho_no_truncar`

## Módulo `relevo` · esquema `outbox`

Bandeja de salida (outbox): eventos hacia costo (integración) y avisos (notificaciones).

### `outbox.evento` · Evento por avisar

- **Tipo:** bandeja de salida (eventos) · **Regla:** Se escribe en la misma transacción que el hecho
- **Permisos:** `sigro_svc_obras` S I, `sigro_svc_compras` S I, `sigro_svc_almacen` S I, `sigro_svc_costo` S U, `sigro_svc_relevo` S I U

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | bigint | no | identidad | PK | Identificador interno. |
| `tipo` | text | no |  |  | Tipo de evento (p. ej. salida registrada). |
| `proyecto_id` | bigint | no |  | FK* | Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9). |
| `payload` | jsonb | no |  |  | Contenido del evento en JSON. |
| `creado_en` | timestamptz | no | now() |  | Momento en que se creó la fila (hora del servidor). |
| `enviado_en` | timestamptz | sí |  |  | Momento de la entrega; nulo si sigue pendiente. |
| `intentos` | smallint | no | 0 |  | Cuántas veces se intentó entregar. |
| `siguiente_intento` | timestamptz | no | now() |  | Cuándo se vuelve a intentar. |
| `ultimo_error` | text | sí |  |  | Error del último intento fallido. |
| `destino` | text | no | 'aviso'::text |  | aviso o integracion. |


**CHECK:**
- `ck_evento_destinatarios`: `CHECK (((destino <> 'aviso'::text) OR CASE     WHEN (jsonb_typeof((payload -> 'destinatarios'::text)) = 'array'::text) THEN (jsonb_array_length((payload -> 'destinatarios'::text)) > 0)     ELSE false END))`
- `ck_evento_destino`: `(destino = ANY (ARRAY['aviso'::text, 'integracion'::text]))`
- `ck_evento_enviado_tras_intentar`: `((enviado_en IS NULL) OR (intentos >= 1))`
- `ck_evento_intentos`: `(intentos >= 0)`
- `ck_evento_payload_objeto`: `(jsonb_typeof(payload) = 'object'::text)`
- `ck_evento_tipo`: `(tipo ~ '^[a-z]+(\.[a-z_]+)+$'::text)`

**Claves foráneas:**
- `(proyecto_id)` → `obra.proyecto(id)` · al borrar: NO ACTION · **cruza al servicio obras (fase 1)**

**Índices:** `ix_evento_pendientes (siguiente_intento, id) WHERE enviado_en IS NULL` · `ix_evento_proyecto_id (proyecto_id, id)`

**Disparadores:** `tg_evento_cambios`, `tg_outbox_evento_no_borrar`, `tg_outbox_evento_no_truncar`

## Vistas

- `almacen.kardex`
- `almacen.recibido_por_compra`
- `almacen.saldo`
- `almacen.saldo_obra`
- `compras.insumo_critico_vigente`
- `compras.parametro_reposicion_vigente`
- `compras.saldo_por_comprar`
- `compras.solicitud_estado`
- `compras.solicitud_linea_avance`
- `costo.gasto_por_obra`
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

Hoy se conservan para no perder integridad mientras todos los esquemas viven en la misma instancia. El servicio que tiene la FK **no puede leer** la tabla a la que apunta (no tiene permiso): solo la referencia. Con ADR-12 (un desplegable Spring) se conservan: dan integridad y cada rol sigue sin leer el esquema ajeno.

| Desde (servicio) | Columnas | Hacia (servicio) |
|---|---|---|
| `catalogo.insumo` (catalogo) | `creado_por` | `obra.usuario` (obras) |
| `catalogo.insumo_factor_historial` (catalogo) | `cambiado_por` | `obra.usuario` (obras) |
| `compras.compra` (compras) | `proyecto_id` | `obra.proyecto` (obras) |
| `compras.compra` (compras) | `anulada_por` | `obra.usuario` (obras) |
| `compras.compra` (compras) | `registrado_por` | `obra.usuario` (obras) |
| `compras.compra_adjunto` (compras) | `anulado_por` | `obra.usuario` (obras) |
| `compras.compra_adjunto` (compras) | `subido_por` | `obra.usuario` (obras) |
| `compras.compra_detalle` (compras) | `insumo_id` | `catalogo.insumo` (catalogo) |
| `compras.insumo_critico` (compras) | `insumo_id` | `catalogo.insumo` (catalogo) |
| `compras.insumo_critico` (compras) | `proyecto_id` | `obra.proyecto` (obras) |
| `compras.insumo_critico` (compras) | `registrado_por` | `obra.usuario` (obras) |
| `compras.parametro_reposicion` (compras) | `insumo_id` | `catalogo.insumo` (catalogo) |
| `compras.parametro_reposicion` (compras) | `proyecto_id` | `obra.proyecto` (obras) |
| `compras.parametro_reposicion` (compras) | `registrado_por` | `obra.usuario` (obras) |
| `compras.partida` (compras) | `proyecto_id` | `obra.proyecto` (obras) |
| `compras.partida` (compras) | `creado_por` | `obra.usuario` (obras) |
| `compras.partida_recurso` (compras) | `insumo_id` | `catalogo.insumo` (catalogo) |
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
