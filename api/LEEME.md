# SIGRO · api · obra, compras y tareo (Adiel)

Esquema de PostgreSQL con migraciones Alembic **y la API FastAPI** (autenticación, permisos por rol y obra, endpoints de
obra/tareo/compras, bandeja de salida), más el microservicio de Notificaciones (`../notificaciones`).
**654 pruebas** de la API contra PostgreSQL real + 19 del microservicio. No toca `almacen` ni `costo` (Henry) ni `integraciones` (Fernando). El catálogo es de Fernando en su base (0002) y la API de catálogo (alta, factor, importación) la puso Adiel según el tablero (T-021, T-030, T-076).

## Cómo correrlo

```bash
python -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
export SIGRO_DATABASE_URL=postgresql+psycopg://USUARIO:CLAVE@localhost:5432/sigro_dev
alembic upgrade head        # base limpia
alembic downgrade base      # vuelta atrás completa
alembic check               # los modelos no se desvían de la base
python -m pytest            # necesita un PostgreSQL con usuario superusuario (ver conftest.py)
SIGRO_DB=sigro_dev python docs/generar_drawio.py      # regenera los tres diagramas .drawio
```

Las migraciones corren con el usuario DUEÑO. La aplicación debe conectarse como un usuario
miembro del rol `sigro_app` (se crea en 0001 sin LOGIN; operaciones le da contraseña): ese rol
no puede borrar nada ni editar ningún libro.

## Qué hay

| Migración | Contenido |
|---|---|
| 0001 | utilidades comunes, rol `sigro_app`, **obra**: usuario, proyecto, plazo, periodo, almacén, asignación, dispositivo |
| 0002 | catálogo **mínimo y provisional** (insumo, nombre comercial), de Fernando |
| 0003 | **tareo**: trabajador, jornada (libro), vistas `jornada_vigente` y `horas_acumuladas` |
| 0004 | **compras**: proveedor, compra, detalle, adjuntos |
| 0005 | **solicitud de materiales** (CR-04, Sprint 2): solicitud, líneas, correlativo, vistas de estado derivado |
| 0006 | **outbox**: bandeja de salida de eventos hacia Notificaciones (inmutable, sin borrado) |
| 0007 | **histórico del factor de conversión** del insumo (HU-17): libro solo-agregar con quién, cuándo y por qué |

## Diagramas (`api/docs/`)

| Diagrama | Archivo | Cómo se mantiene |
|---|---|---|
| **Físico** (tablas, claves, índices únicos, disparadores) | `diagrama-fisico.drawio` | Se genera **desde la base migrada** con `SIGRO_DB=sigro_dev python docs/generar_drawio.py` |
| **Entidad-relación** (modelo lógico: nombres de negocio, cardinalidades) | `diagrama-er.drawio` | El mismo script; **falla** si un atributo, una tabla, una relación o un valor de estado no coincide con la base |
| **Microservicios** (frontera API ↔ Integraciones ↔ Notificaciones) | `diagrama-microservicios.drawio` | Dibujado en el mismo script (`microservicios()`); editable a mano en draw.io |

Se abren con draw.io (app.diagrams.net, la extensión de VS Code o draw.io Desktop), donde cada tabla y cada flecha se puede mover y editar. Para regenerarlos hacen falta Graphviz (`dot`) y una base migrada (`alembic upgrade head`). El de microservicios deja a la vista un riesgo: la web llama a Integraciones directo. Decisión de Fernando (22/10): **B ahora** (Integraciones exige el token de servicio; la API ya lo envía en `X-Servicio-Token`) y **A después** (la web pasará por la API).

## Reglas del CLAUDE.md y dónde las sostiene el motor

| Regla | Mecanismo |
|---|---|
| R1 libro que no se modifica | disparador `rechazar_modificacion` + la aplicación no tiene UPDATE/DELETE (jornada, detalle, adjunto, líneas, historial de plazo) |
| R2 sin columna de saldo | prueba de invariante sobre todo el esquema |
| R3 un reenvío no duplica | `jornada.uuid_cliente` único; el contrato de `movimiento` (Henry) está dibujado en el diagrama |
| R5 un dispositivo por responsable, máximo dos por obra | disparador con bloqueo consultivo + índices únicos parciales. Probado con hilos reales y verificado con una mutación (sin el bloqueo, la prueba falla) |
| R9 el rol vive junto a la obra | `asignacion`, única vigente por (usuario, obra) |
| R10 nada se borra | disparador `rechazar_borrado` en **todas** las tablas; una prueba recorre el catálogo y falla si aparece una tabla sin él |

## Decisiones que NO estaban escritas y hay que ratificar

| # | Decisión | Por qué |
|---|---|---|
| D-P1 | Los periodos terminan en el día de corte (domingo por defecto). El **primer** periodo también puede ser corto si la obra arranca a mitad de semana | HU-03 solo menciona el último. Si prefieren semanas de 7 días desde el inicio, se cambia `generar_periodos` en un solo lugar |
| D-P2 | El día de corte se elige al crear la obra y no se edita después | Cambiarlo con movimientos hechos reasigna periodos. Si HU-04 pide cambiarlo, hace falta decidir qué pasa con lo ya registrado |
| D-R5 | **HU-15 dice «un dispositivo activo a la vez»**; la regla 5, el modelo y el cliente dicen **dos** (almacenero y SSOMA). Se implementó dos | Hay que corregir el criterio de HU-15 en la tarjeta |
| D-T1 | `jornada.categoria` se congela al registrar | Si no, un ascenso reprecia hacia atrás todas las horas pasadas |
| D-T2 | Un periodo cerrado no admite jornadas nuevas pero sí correcciones con motivo | Corregir después del cierre es lo normal y deja rastro |
| D-S1 | El estado de la solicitud se deriva de las compras hasta «comprada». «Recibida / recibida en parte» depende de almacén | No se cruza de módulo en una vista; el servicio de compras lo compone llamando a la interfaz de Henry |
| D-C1 | `insumo.verificado` nace en **falso**; la importación del S10 debe mandarlo en `true` | Falla del lado seguro: un insumo creado en obra queda «por verificar» |
| D-C2 | Una compra registrada no se edita: se anula con motivo y se registra otra | R10. La suma de líneas contra `base_imponible` NO se exige en el motor (los descuentos la rompen); la API debe avisar |

## Contrato con los otros módulos

- **Henry (`almacen.movimiento`)**: referenciar `(almacen_id, proyecto_id)`, `(periodo_id, proyecto_id)`
  y `(dispositivo_id, proyecto_id)` — ya existen los `UNIQUE (id, proyecto_id)` para eso, y así el motor
  garantiza que movimiento, almacén, periodo y dispositivo son de la misma obra. Dirección de dependencias:
  nada en `obra` puede referenciar a otro esquema (hay una prueba).
- **Fernando**: `catalogo` es mínimo y está en 0002 para poder enlazar compras. Amplíen encima con
  migraciones nuevas coordinadas con Henry (solo él crea migraciones una vez en pie el esqueleto).
- **Datos que escriben solo funciones**: periodos (`obra.generar_periodos`), plazo (`obra.ampliar_plazo`)
  y correlativo de solicitud. La aplicación no puede insertarlos directamente.

## Arrancar todo (Docker)

```bash
cp .env.example .env            # cambia TODOS los valores
docker compose up --build       # db + migraciones + api + relevo + notificaciones + integraciones
docker compose run --rm -e SIGRO_ADMIN_CLAVE='tu-clave-larga' api python -m app.cli primer-admin \
    --usuario adiel --nombre "Víctor Adiel Luján" --obra "Multifamiliar Flores" --inicio 2026-10-19 --fin 2027-10-17
# opcional: proveedores reales del cliente (idempotente; --dry-run para solo contar)
python -m semillas.cargar_proveedores RUTA/proveedores-cliente.json --dry-run
```

Documentación interactiva en `http://localhost:8000/docs`. Sin Docker: las mismas variables (`SIGRO_APP_DATABASE_URL`,
`SIGRO_JWT_SECRET`, `SIGRO_NOTIFICACIONES_URL`, `SIGRO_SERVICIO_TOKEN`) y `uvicorn app.main:app`, `python -m app.relay`.
El compose se validó con `docker compose config`; **las imágenes no se pudieron construir en el entorno de desarrollo**
(no había demonio de Docker): la primera vez que lo corras, avisa si algo falla. La misma cadena sí se probó a mano con
servidores reales: login → asignar → pedir dispositivo → relevo → campana del administrador.

## Cómo se protege cada petición

El token solo dice **quién** eres (`sub`). Tu rol y tu obra se leen de `obra.asignacion` **en cada petición**, así que
dar de baja a alguien o retirarle la obra corta el acceso al instante. Cada ruta declara su acción con
`exigir("compra.registrar")` y la matriz vive en `app/permisos.py`; una prueba recorre **todas las rutas × todos los
roles** y falla si una ruta no declara permiso o si alguien accede sin tenerlo (verificado con una mutación). Login:
Argon2id, bloqueo a los 5 intentos (15 min), misma respuesta para usuario inexistente y clave errónea.

## Endpoints (`/api/v1`) y quién puede

| Método y ruta | Acción (matriz) |
|---|---|
| `POST /auth/login`, `GET /auth/yo`, `POST /auth/cambiar-clave` | público / autenticado |
| `GET·POST /usuarios`, `POST /usuarios/{id}/baja` | usuario.consultar·crear → administrador |
| `GET·POST /obras`, `GET /obras/{id}` | crear: residente, administrador · ver: todos |
| `POST /obras/{id}/ampliar-plazo`, `GET /plazo-historial`, `GET /periodos` | residente, administrador · todos |
| `GET·POST /obras/{id}/almacenes` | almacenero, residente, administrador (gerente solo ve) |
| `GET·POST /obras/{id}/asignaciones`, `POST …/{id}/retiro` | administrador (residente ve) |
| `GET·POST /obras/{id}/dispositivos`, `POST …/autorizar`, `…/revocar` | pedir: almacenero, SSOMA · autorizar: administrador |
| `GET·POST /obras/{id}/trabajadores`, `POST …/baja` | almacenero, SSOMA registran |
| `GET·POST /obras/{id}/jornadas`, `POST …/correccion`, `GET /horas-acumuladas` | almacenero, SSOMA registran · horas: todos |
| `GET·POST /proveedores`, `POST …/validar`, `…/baja` | residente, administrador (gerente ve) |
| `GET·POST /obras/{id}/compras`, `GET …/{id}`, `POST …/anular` | residente, administrador (gerente ve) |
| `GET·POST /obras/{id}/solicitudes`, `GET …/{id}`, `POST …/anular` | emitir: residente |
| `GET·POST /catalogo/insumos`, `POST …/{id}/verificar` | ver: todos · crear («por verificar»): almacenero, residente, administrador · verificar: residente, administrador |
| `POST /catalogo/insumos/{id}/factor-conversion`, `GET …/historial` | cambiar: residente, administrador (con motivo; queda en el histórico) · ver: todos |
| `POST /catalogo/insumos/importar-s10` | residente, administrador (aplica lo que leyó Integraciones; repetirlo no duplica) |
| `GET /notificaciones`, `POST /notificaciones/{id}/leida` | autenticado (siempre con SU usuario del token) |

Errores: `{"codigo": "...", "mensaje": "..."}`, igual que `ErrorApi` en `web/src/api/cliente.ts`. Los campos van en
`snake_case`; `ClienteSigro` los pasa a `camelCase`.

## Decisiones nuevas de la API · ratificadas por Adiel (PO) el 22 de octubre de 2026

| # | Decisión | Por qué |
|---|---|---|
| D-A1 | `python -m app.cli primer-admin` crea el primer usuario, obra y asignación; solo en sistema vacío | Todo permiso vive en una asignación a una obra: sin esto, nadie podría crear nada |
| D-A2 | Ampliar plazo lo hacen residente y administrador; **sigue pendiente la autorización del gerente «A»** (queda fuera del Sprint 1) | La matriz no dice cómo se autoriza; no se inventó |
| D-C3 | Los precios de una compra **incluyen IGV** por defecto (`precios_incluyen_igv=true`): 400 × 29.23 = 11 692.00 → base 9 908.47 + IGV 1 783.53 | Así vienen las facturas de materiales en el archivo del cliente; el servidor calcula los totales, el cliente no los manda |
| D-C4 | Emitir solicitud: solo residente | La matriz no lista la acción; se tomó lo más restrictivo |
| D-C5 | Un proveedor nuevo se registra con **RUC (11 dígitos) o DNI (8, persona natural)**. El DNI queda «sin validar» y nunca se consulta a SUNAT; la semilla carga además los documentos sucios del cliente como «sin validar» | El archivo real trae personas naturales con DNI: exigir solo RUC no las dejaría registrar. No se descarta ni se corrige a mano nada del cliente |
| D-K1 | El código de un insumo nuevo lo genera el servidor (`SIGRO-00001`…); un nombre repetido (sin tildes ni mayúsculas, misma unidad) se rechaza señalando el que ya existe; nace «por verificar» | El almacenero no espera pero tampoco se duplica el catálogo (HU-08). Código y verificación: pendiente de confirmar con Fernando, dueño del catálogo |
| D-K2 | Importar el S10 crea los nuevos ya verificados y de los existentes solo actualiza la descripción; **no** toca código, unidad, factor ni saldos | El catálogo comparte solo nombre y código entre obras; cantidades y precios son de cada obra (HU-19) |
| D-S2 | El estado de una solicitud nunca se edita: se calcula de las compras (`solicitada → comprada_en_parte → comprada`); solo «anulada» se marca a mano, y se permite en cualquier estado | Así no hay estados inconsistentes con lo comprado. Falta «recibida», que depende de almacén (D-S1). Anular con compras hechas no las revierte: por confirmar con el cliente |
| D-N1 | Los avisos los resuelve la API (destinatarios) y nunca incluyen a quien hizo la acción | Nadie necesita que le avisen de lo que acaba de hacer |

## Qué NO está hecho

Adjuntos de compra (subida de archivos), recepción de compras y endpoints de `almacen`/`costo` (Henry: HU-35 depende de
almacén), autorización del gerente para ampliar plazo (D-A2), alerta de reposición HU-26 (el outbox está listo; falta el
movimiento de Henry que la dispare). HU-12/13/14 (login, roles, asignación) figuran con Henry en `equipo/henry.md`: aquí
ya están hechas, hay que ponerse de acuerdo en quién las cierra.

## Datos reales del cliente (catálogo de insumos)

`python -m semillas.cargar_catalogo semillas/catalogo-cliente.json [--dry-run]` carga los 2 243 insumos normalizados del
cliente (idempotente por código, como `material` y ya verificados; sin precios ni marcas). Ojo: el código del cliente
(«68») no es el del S10 («0221000000»), así que cargar ambos duplica materiales hasta definir el emparejamiento
S10 ↔ catálogo (decisión de Fernando). El contrato de la API está en `docs/openapi.json` (se regenera con
`python -c "import json; from app.main import app; json.dump(app.openapi(), open('docs/openapi.json','w'), ensure_ascii=False, indent=1)"`).

## Datos reales del cliente (proveedores)

626 leídos → 612 con RUC correcto y 14 que no pasan el dígito verificador (entre ellos DNI de 8 dígitos y RUC claramente
falsos como `12345678901`). El archivo del cliente marca 12 como inválidos y algunos de esos 14 figuran como válidos:
no coinciden, conviene mostrárselo. Reproducible con `python -m semillas.cargar_proveedores ARCHIVO --dry-run`.
