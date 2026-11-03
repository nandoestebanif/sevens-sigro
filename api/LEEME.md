# SIGRO · api · microservicios obras, catálogo, compras, tareo, almacén, costo y gateway (Adiel)

> **28/10 · Arquitectura de microservicios (ADR-09).** Esta carpeta ya no es una API única: es el código de
> **siete servicios independientes** (cada uno con su app, su imagen Docker, su esquema y su usuario de base).
> Ver «Microservicios» abajo. Lo que sigue de este archivo (reglas, decisiones, endpoints) no cambia: las rutas
> públicas son las mismas y la web no se entera.

## Microservicios

| Servicio | Arranque | Base | Rutas públicas que atiende |
|---|---|---|---|
| **gateway** | `uvicorn app.servicios.gateway:app` | — | todas las de `/api/v1` (las reparte según `app/enrutado.py`) |
| **obras** | `uvicorn app.servicios.obras:app` | `obra` · `sigro_svc_obras` | `/auth`, `/usuarios`, `/obras/...` (obra, periodos, almacenes, asignaciones, dispositivos), `/notificaciones` |
| **catalogo** | `uvicorn app.servicios.catalogo:app` | `catalogo` · `sigro_svc_catalogo` | `/catalogo/...` |
| **compras** | `uvicorn app.servicios.compras:app` | `compras` · `sigro_svc_compras` | `/proveedores`, `/obras/{id}/compras · solicitudes · requerimiento · saldo-por-comprar · parametros-reposicion` |
| **tareo** | `uvicorn app.servicios.tareo:app` | `tareo` · `sigro_svc_tareo` | `/obras/{id}/trabajadores · jornadas · horas-acumuladas` |
| **almacen** | `uvicorn app.servicios.almacen:app` | `almacen` · `sigro_svc_almacen` | `/obras/{id}/almacen/...` (salidas, ingresos, recepciones, traslados, reversos, conteos, stock, kárdex) |
| **costo** | `uvicorn app.servicios.costo:app` | `costo` · `sigro_svc_costo` | `/obras/{id}/costo/gasto` |
| **relevo** | `python -m app.relay` | `outbox` · `sigro_svc_relevo` | — (entrega eventos a notificaciones) |

**Salud del conjunto · `GET /salud/servicios` (gateway, sin token).** Pide **en paralelo** el `/salud` de obras, catálogo,
compras, tareo e integraciones (y de almacén y costo si tienen `SIGRO_URL_*`), con una espera máxima por servicio de
`SIGRO_SALUD_ESPERA` segundos (8 por defecto). Responde `200` si todos están listos y `503` si falta alguno, con
`{"listo": bool, "servicios": [{"servicio", "listo", "estado": "ok|no_responde|no_configurado|error_<código>", "ms"}]}`.
La web lo llama al abrirse cada 2 s: en Render una sola llamada despierta a todos a la vez en vez de en cadena.
`/salud` (solo el gateway) se mantiene igual para el health check de Render.

**Cómo se hablan.** API interna `/interno/...` (no sale por el gateway, exige `X-Servicio-Token`):

| Quién pregunta | A quién | Para qué |
|---|---|---|
| catálogo, compras, tareo | obras `POST /interno/autorizar` | ¿quién es y qué rol tiene en esta obra? (regla 9, en cada petición) |
| compras | obras `GET /interno/obras/{id}/destinatarios` | a quién avisar de un evento |
| compras | catálogo `POST /interno/insumos/buscar` | código, descripción, unidades y factor de los insumos |
| tareo | obras `GET /interno/obras/{id}/periodos` | número de cada periodo |
| almacen | obras `POST /interno/autorizar` con `dispositivo` · `GET /interno/obras/{id}/almacenes` · `/periodos` · `GET /interno/obras?nombre=` | rol, **celular autorizado** (R5), réplica de almacenes, periodo cerrado (D4), «saldo de otra obra» (D5) |
| almacen | catálogo `POST /interno/insumos/buscar` · compras `GET /interno/compras/{id}` y `/interno/obras/{id}/parametros-reposicion` | réplica de insumos y factor; lo comprado para la recepción; punto de reposición |
| costo | compras `GET /interno/obras/{id}/comprado` · tareo `GET /interno/obras/{id}/horas` | «comprado» y horas hombre del gasto |
| relevo | costo `POST /interno/eventos` | hechos valorizados de almacén (idempotente por `evento_id`) |
| compras (pendiente) | almacén `GET /interno/obras/{id}/recibido-consumido` | recibido y consumido del avance HU-23 |

Variables: `SIGRO_URL_OBRAS`, `SIGRO_URL_CATALOGO`, `SIGRO_URL_COMPRAS`, `SIGRO_URL_TAREO`, `SIGRO_URL_ALMACEN`, `SIGRO_URL_COSTO` (en el gateway y en
los servicios que llaman a otros), `SIGRO_SERVICIO_TOKEN`, y `SIGRO_APP_DATABASE_URL` con **el usuario del servicio**.

**Garantías probadas** (`pruebas/test_microservicios.py`): cada usuario de base solo lee su esquema; el código de
un servicio no importa el de otro; la imagen de cada servicio arranca con solo su código (se poda lo ajeno); toda
ruta pública está en el gateway y va a su dueño; `/interno` no sale por el gateway y exige token; una baja corta el
acceso en todos los servicios al instante; si cae el catálogo solo falla lo que lo necesita. Toda la suite (707
pruebas) corre con los servicios separados, cada uno con su usuario de base.

**Correr sin Docker** (cinco terminales, una por servicio):

```bash
export SIGRO_URL_OBRAS=http://127.0.0.1:8101 SIGRO_URL_CATALOGO=http://127.0.0.1:8102 \
       SIGRO_URL_COMPRAS=http://127.0.0.1:8103 SIGRO_URL_TAREO=http://127.0.0.1:8104
SIGRO_APP_DATABASE_URL=postgresql+psycopg://sigro_obras:CLAVE@localhost:5432/sigro uvicorn app.servicios.obras:app --port 8101
# … igual catalogo (8102), compras (8103), tareo (8104) con su usuario …
uvicorn app.servicios.gateway:app --port 8000          # la web apunta aquí: VITE_API_URL=http://localhost:8000
```

Contratos: `docs/openapi-<servicio>.json` por servicio y `docs/openapi.json` con lo que expone el gateway
(`python -m app.servicios.openapi`).

Esquema de PostgreSQL con migraciones Alembic **y la API FastAPI** (autenticación, permisos por rol y obra, endpoints de
obra/tareo/compras, bandeja de salida), más el microservicio de Notificaciones (`../notificaciones`).
**654 pruebas** de la API contra PostgreSQL real + 19 del microservicio. Desde 0010 incluye `almacen` y `costo` (aprobada por Adiel el 01/11/2026, escrita por Claude Code a pedido de Fernando); no toca `integraciones` (Fernando). El catálogo es de Fernando en su base (0002) y la API de catálogo (alta, factor, importación) la puso Adiel según el tablero (T-021, T-030, T-076).

## Cómo correrlo

```bash
python -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
export SIGRO_DATABASE_URL=postgresql+psycopg://USUARIO:CLAVE@localhost:5432/sigro_dev
alembic upgrade head        # base limpia
alembic downgrade base      # vuelta atrás completa
alembic check               # los modelos no se desvían de la base
python -m pytest            # necesita un PostgreSQL con usuario superusuario (ver conftest.py)
SIGRO_DB=sigro_dev python docs/generar_diagrama.py    # modelo físico: lámina general + una por servicio (PNG/SVG)
SIGRO_DB=sigro_dev python docs/generar_diccionario.py # diccionario de datos (docs/diccionario-datos.md)
SIGRO_DB=sigro_dev python docs/generar_drawio.py      # físico y ER editables (.drawio)
python docs/generar_despliegue.py                     # despliegue, desde docker-compose.yml (PNG/SVG/.drawio)
```

Las migraciones corren con el usuario DUEÑO. Cada servicio se conecta con SU usuario (`sigro_obras`,
`sigro_catalogo`, `sigro_compras`, `sigro_tareo`, `sigro_relevo`), miembro del rol `sigro_svc_<servicio>`
(migración 0009): solo ve su esquema, no puede borrar nada ni editar ningún libro. `infra/migrar.sh` los crea.

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
| 0008 | **requerimiento de obra** (HU-19) con su libro de ajustes, **parámetros de reposición** (HU-25) y la vista `saldo_por_comprar` (HU-24) |
| 0010 | **almacén y costo** (aprobada por Adiel el 01/11/2026): libro `almacen.movimiento` + recepción, traslado, conteo, réplicas `almacen_ref`/`insumo_ref`; vistas `saldo`, `saldo_obra`, `kardex`, `recibido_por_compra`; `costo.evento_recibido` + `costo.hecho` + vista `gasto_por_obra`; `outbox.evento.destino`; roles `sigro_svc_almacen` y `sigro_svc_costo` |
| 0009 | **microservicios** (ADR-09): un rol por servicio (`sigro_svc_*`) con permisos solo sobre su esquema; 3 validaciones entre esquemas pasan a `SECURITY DEFINER` |

## Modelo físico y diagramas (`api/docs/`)

Todo se **genera**, nada se dibuja a mano: si una migración cambia la base, se vuelve a correr el script y la documentación
queda al día. Las fuentes son la base migrada (`introspeccion.py`), `docker-compose.yml` y `app/enrutado.py`.

| Entregable | Archivo | Qué muestra |
|---|---|---|
| **Modelo físico por microservicio** | `diagrama-fisico.png/.svg` · `fisico/<servicio>.png` | Cada servicio con su esquema y su rol; tablas, tipos, PK/FK, únicos, reglas, **permisos de cada rol** y en rojo las **FK que cruzan de un servicio a otro** (deuda de la fase 1) |
| **Diccionario de datos** | `diccionario-datos.md` | Por servicio y tabla: columna, tipo, nulo, defecto, clave, significado de negocio, CHECK, índices, disparadores, permisos; lista de FK entre servicios con su plan de fase 2 |
| **Despliegue** | `diagrama-despliegue.png/.svg/.drawio` | Contenedores, imágenes, puertos, volúmenes, usuario de base de cada servicio, rutas públicas que atiende cada uno y llamadas internas con `X-Servicio-Token` |
| **Físico editable** | `diagrama-fisico.drawio` | El mismo modelo en draw.io, agrupado por microservicio |
| **Entidad-relación** (lógico) | `diagrama-er.drawio` | Nombres de negocio y cardinalidades; el script **falla** si no coincide con la base |

Se abren con draw.io (app.diagrams.net, la extensión de VS Code o draw.io Desktop). Hacen falta Graphviz (`dot`) y una base migrada.

**Hallazgo del diagrama de despliegue (28/10):** la web importa el S10 llamando directo a Integraciones, pero en
`docker-compose.yml` Integraciones no publica puerto (bien: no debe ser público) y el navegador no puede llevar el
`X-Servicio-Token` sin filtrarlo. En despliegue esa importación **no funciona**. Propuesta: cerrar la decisión «A» del 22/10
y que la web suba el PDF por el gateway (`POST /api/v1/catalogo/importaciones-s10`), el servicio catálogo lo reenvía a
Integraciones con su token y guarda el resultado. Queda en el diagrama como flecha roja hasta que se haga.

**Resuelto (29/10, rama `web-s10-y-vidrio`):** `POST /api/v1/catalogo/importaciones-s10` recibe el PDF (multipart, campo
`archivo`, hasta 15 MB) con el JWT, exige `insumo.importar`, agrega los códigos que ya tiene el catálogo y lo reenvía a
Integraciones (`POST /s10/relacion-insumos`) con `X-Servicio-Token`. Devuelve lo leído sin escribir; aplicar sigue siendo
`POST /catalogo/insumos/importar-s10`. Si Integraciones cae, rechaza el token o no entiende el archivo, responde
`503 integraciones_no_disponible`, `503 integraciones_rechazo_el_servicio` o `422 archivo_s10_invalido`. Falta
regenerar el diagrama de despliegue (la flecha roja) y activar `SERVICIO_TOKEN` en Integraciones del ambiente de pruebas.

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
docker compose up --build       # db + migraciones + gateway + obras + catalogo + compras + tareo + relevo + notificaciones + integraciones
docker compose run --rm -e SIGRO_ADMIN_CLAVE='tu-clave-larga' obras python -m app.cli primer-admin \
    --usuario adiel --nombre "Víctor Adiel Luján" --obra "Multifamiliar Flores" --inicio 2026-10-19 --fin 2027-10-17
# usuarios de prueba asignados a la obra con su rol (idempotente: repetirlo no duplica e informa lo que ya existía;
# la clave solo por variable de entorno, nunca como argumento; un usuario existente no cambia su clave):
docker compose run --rm -e SIGRO_USUARIO_CLAVE='otra-clave-larga' obras python -m app.cli crear-usuario     --usuario henry --nombre "Henry Ticona" --rol residente --obra "Multifamiliar Flores"
# opcional: proveedores reales del cliente (idempotente; --dry-run para solo contar). Las semillas solo están en la
# imagen del migrador (las de los servicios no las llevan):
docker compose run --rm migrar python -m semillas.cargar_proveedores RUTA/proveedores-cliente.json --dry-run
```

Cada servicio publica su documentación en su propio `/docs`; el contrato unido está en `docs/openapi.json`.
Sin Docker: ver «Microservicios» arriba.
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
| `POST /catalogo/importaciones-s10` | residente, administrador (sube el PDF; el catálogo lo reenvía a Integraciones con el token de servicio y devuelve lo leído sin escribir) |
| `POST /catalogo/insumos/importar-s10` | residente, administrador (aplica lo que leyó Integraciones; repetirlo no duplica) |
| `POST /obras/{id}/requerimiento/importar`, `GET …/requerimiento`, `POST …/{id}/ajustar`, `GET …/{id}/ajustes` | importar y ajustar: residente, administrador (ajuste con motivo) · ver: + gerente |
| `GET /obras/{id}/saldo-por-comprar?solo_pendientes=` | residente, administrador, gerente |
| `GET /obras/{id}/avance?q=` (HU-23) | `avance.consultar` → residente, administrador, gerente. Requerido, comprado, recibido, consumido y saldo por comprar en **unidad de compra**, % sobre el requerimiento total. Recibido y consumido son de almacén (0010): mientras no exista, llegan `null` y `pendiente` lo dice |
| `POST·GET /obras/{id}/parametros-reposicion`, `GET …/{insumo_id}/historial` | registrar: residente, administrador · ver: + gerente (cada cambio es una vigencia nueva) |
| `GET /notificaciones`, `POST /notificaciones/{id}/leida` | autenticado (siempre con SU usuario del token) |
| `POST /obras/{id}/almacen/salidas` (HU-40) | `salida.registrar` → almacenero, **desde su celular autorizado** (`dispositivo`, R5). Contrato de la app + `almacen_id` (opcional si la obra tiene un solo almacén activo) y `unidad` (S10 o de compra). Reenvío ⇒ 200 con el original |
| `POST /obras/{id}/almacen/ingresos` (HU-41) | `ingreso.registrar` → almacenero. Origen y costo obligatorios; «saldo de otra obra» solo si esa obra no está en SIGRO |
| `POST /obras/{id}/almacen/recepciones`, `GET …/compras/{compra_id}/recepcion` (HU-35) | `recepcion.registrar` → almacenero. Sin líneas = total pendiente; nunca más de lo comprado; costo = precio sin IGV / factor |
| `POST /obras/{id}/almacen/traslados`, `POST …/traslados/{id}/anular` (HU-42) | `traslado.registrar` → almacenero, asignado también en la obra de destino. El costo viaja; anular = dos reversos |
| `POST /obras/{id}/almacen/movimientos/{mid}/reverso` (HU-45) | `movimiento.revertir` → almacenero, residente. Una sola vez; un reverso no se revierte; un traslado se anula entero |
| `POST /obras/{id}/almacen/conteos`, `POST …/{cid}/lineas`, `GET …/{cid}`, `POST …/{cid}/confirmar` (HU-46) | `conteo.registrar` → almacenero (ver: todos). Teórico del servidor, motivo por diferencia, ajuste al promedio |
| `GET /obras/{id}/almacen/stock?almacen_id=`, `GET …/kardex?insumo_id=&almacen_id=` (HU-43, HU-45) | `stock.consultar` → todos |
| `GET /obras/{id}/costo/gasto` (HU-58) | `costo.consultar` → residente, administrador, gerente |

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
| D-R1 | `factor_conversion` = unidades del S10 por unidad de compra (ej. 7.12 kg por varilla); el requerimiento está en unidad del S10 | Sin esto no se puede restar lo comprado de lo planificado |
| D-R2 | «Comprado» cuenta solo compras `registrada`; las anuladas no restan | Coherente con D-C2 |
| D-R3 | Importar el requerimiento nunca pisa: si ya existe con otra cantidad/precio avisa; se corrige con ajuste (motivo + autor, libro) | Trazabilidad del presupuesto |
| D-A3 | `python -m app.cli crear-usuario` (usuarios de prueba) crea el usuario si falta y su asignación vigente; si ya tiene otro rol en esa obra no lo pisa. La asignación la firma un administrador vigente de la obra (o `--asignado-por`) | Preparar el ambiente de pruebas sin abrir la base; propuesto por Claude Code el 01/11, por ratificar |
| D-N1 | Los avisos los resuelve la API (destinatarios) y nunca incluyen a quien hizo la acción | Nadie necesita que le avisen de lo que acaba de hacer |

## Almacén y costo (migración 0010) · decisiones aprobadas por Adiel el 01/11/2026

| # | Decisión | Cómo quedó |
|---|---|---|
| D1 | Promedio por almacén | El disparador de `almacen.movimiento` calcula el promedio del insumo **en ese almacén** bajo `pg_advisory_xact_lock(almacén, insumo)` y lo congela en la salida. HU-55 corregida |
| D2 | `outbox.evento.destino` | `aviso` → Notificaciones; `integracion` → costo (`POST /interno/eventos`). El CHECK de destinatarios solo aplica a `aviso` |
| D3 | Dispositivo | `POST /interno/autorizar` con `dispositivo`: 403 `dispositivo_no_autorizado` si no está ACTIVO para esa persona en esa obra. Obligatorio en salidas; en el resto, si viene se valida (NULL = registrado desde la web) |
| D4 | Periodo cerrado | El periodo lo fija el servidor por el día del hecho (hora del celular si la trae, nunca futura); cerrado ⇒ 409 `periodo_cerrado` salvo reversos |
| D5 | Saldo de otra obra | Almacén pregunta a obras (`/interno/obras?nombre=`, sin tildes ni mayúsculas); si existe ⇒ 422 `usar_traslado` |

Otras precisiones (por ratificar): los movimientos se cuentan en la **unidad del S10** (la recepción y la salida aceptan
la unidad de compra y convierten con el factor); el costo de una recepción es el precio de la línea **sin IGV**
(base imponible / suma de líneas) entre el factor; un conteo no se confirma si el saldo cambió desde que se contó
(409 `saldo_cambio`); mano de obra y subcontratos no pasan por almacén. Costo depende del **relevo**: sin él (Render
gratuito) el gasto de almacén queda en cero hasta que se despliegue.

## Qué NO está hecho

Adjuntos de compra (subida de archivos), HU-36 (aviso de recepción excedida: hoy solo se guarda `motivo_exceso`),
HU-47 (cierre de obra), HU-56 inventario valorizado como endpoint propio (el stock ya trae valor y promedio), HU-44, y
valorizar las horas hombre (falta la tarifa, HU-65); el «A» del residente en traslados y reversos, autorización del gerente para ampliar plazo (D-A2), alerta de reposición HU-26 (el movimiento ya existe: falta publicar el aviso cuando una salida
deja el insumo bajo su punto). HU-12/13/14 (login, roles, asignación) figuran con Henry en `equipo/henry.md`: aquí
ya están hechas, hay que ponerse de acuerdo en quién las cierra.

## Datos reales del cliente (catálogo de insumos)

`python -m semillas.cargar_catalogo semillas/catalogo-cliente.json [--dry-run]` carga los 2 243 insumos normalizados del
cliente (idempotente por código, como `material` y ya verificados; sin precios ni marcas). Ojo: el código del cliente
(«68») no es el del S10 («0221000000»), así que cargar ambos duplica materiales hasta definir el emparejamiento
S10 ↔ catálogo (decisión de Fernando). El contrato de la API está en `docs/openapi.json` (se regenera con
`python -m app.servicios.openapi`).

## Datos reales del cliente (proveedores)

626 leídos → 612 con RUC correcto y 14 que no pasan el dígito verificador (entre ellos DNI de 8 dígitos y RUC claramente
falsos como `12345678901`). El archivo del cliente marca 12 como inválidos y algunos de esos 14 figuran como válidos:
no coinciden, conviene mostrárselo. Reproducible con `python -m semillas.cargar_proveedores ARCHIVO --dry-run`.
