# Decisiones de arquitectura

Cada decisión se registra con el contexto que la obligó, las alternativas evaluadas y las
consecuencias que se aceptan. Una decisión sin alternativas descartadas no es una
decisión: es una costumbre.

## ADR-01 · Monolito modular + microservicio de integraciones — **reemplazado por ADR-09 (28/10)**
**Contexto.** Seis dominios, dos integraciones con terceros (S10, SUNAT), cuatro personas
con dedicación parcial y cuatro semanas.
**Decisión.** Una sola API con módulos por dominio, sin consultas cruzadas directas, y un
servicio independiente para las integraciones.
**Descartado.** Microservicios por dominio (el despliegue y la coordinación cuestan más de
lo que el equipo puede pagar). Monolito sin separar integraciones (una importación de 237
insumos bloquearía al almacenero en obra).
**Consecuencia.** La disciplina de no cruzar módulos depende del equipo, no del
compilador: se vigila en la revisión del PR.

## ADR-02 · Android nativo, no aplicación web
**Contexto.** El registro ocurre en obra, sin señal, y debe sobrevivir a que el sistema
cierre la aplicación.
**Decisión.** Kotlin, Compose, Room y WorkManager.
**Descartado.** Aplicación web instalable (el almacenamiento del navegador puede ser
descartado por el sistema y el envío en segundo plano no está garantizado). React Native
(añade una capa entre la app y las garantías de persistencia que justamente se necesitan).
**Consecuencia.** Dos bases de código, app y web. Se asume porque atienden necesidades
distintas.

## ADR-03 · Libro de movimientos sin borrado y saldo derivado
**Contexto.** El problema de origen es que nadie puede reconstruir qué pasó con el
material. Un saldo en columna editable se corrige sin dejar rastro.
**Decisión.** Nada se modifica ni se elimina; corregir es registrar el inverso. El stock se
calcula desde los movimientos.
**Descartado.** Guardar el saldo y actualizarlo en cada operación (más rápido de consultar,
pierde la trazabilidad).
**Consecuencia.** La consulta cuesta más: se resuelve con índices y, si hiciera falta, con
un saldo precalculado que se reconstruye desde los movimientos y nunca se edita.

## ADR-04 · Identificador generado en el dispositivo
**Contexto.** Un movimiento registrado sin señal se reenvía al volver la cobertura; un
envío doble sacaría el material dos veces.
**Decisión.** El dispositivo genera un identificador único antes de encolar; el servidor lo
usa como clave de idempotencia y devuelve el resultado del original.
**Descartado.** Deduplicar comparando campos (dos salidas legítimas e iguales el mismo día
serían indistinguibles de un duplicado).
**Consecuencia.** El reintento deja de ser peligroso. Obliga a conservar los
identificadores recibidos; se purgan con el cierre de la obra.

## ADR-05 · Costo promedio ponderado congelado en el movimiento
**Contexto.** El mismo insumo entra a precios distintos a lo largo de la obra.
**Decisión.** Cada ingreso recalcula el promedio; cada salida toma el vigente y lo guarda.
**Descartado.** PEPS (exige seguir lotes y el almacenero no los distingue al despachar).
Precio del presupuesto (no refleja lo que se pagó).
**Consecuencia.** El promedio suaviza las variaciones: el reporte lo declara para que nadie
lo lea como precio de compra.

## ADR-06 · Despliegue en nube gestionada
**Contexto.** La Definición de Terminado exige que otro ejecute la historia en un ambiente
de pruebas, y el presupuesto es cero.
**Decisión.** Nivel gratuito con PostgreSQL administrado; APK por enlace.
**Descartado.** Servidor contratado (cuesta dinero del equipo). Solo local (incumple la DoD
y deja al cliente sin forma de probar).
**Consecuencia.** Los niveles gratuitos suspenden por inactividad y la primera petición
tarda: se advierte antes de cada demostración.

## ADR-08 · Kotlin Multiplatform para la app: Android e iOS con un solo código
**Contexto.** El 28/10 el equipo decidió que la app funcione en Android y en iPhone, para no depender del celular que tenga cada almacenero o ingeniero SSOMA (a confirmar con el Product Owner). ADR-02 eligió Android nativo por
las garantías de persistencia y de envío en segundo plano; esas garantías se tienen que conservar.
**Decisión.** Kotlin Multiplatform con Compose Multiplatform, Koin, Ktor y Room KMP. La interfaz, el
dominio, la base local y el cliente de la API se escriben una vez (`commonMain`); cada plataforma aporta
solo el motor HTTP, la ruta de la base y el programador de envío. En Android se sigue usando WorkManager.
**Descartado.** Dos apps nativas, Kotlin y Swift (el doble de trabajo con una persona en la app).
Flutter o React Native (otro lenguaje y otra capa sobre la persistencia, que es lo que ADR-02 quiso evitar).
Aplicación web instalable (mismas razones que en ADR-02).
**Consecuencia.** ADR-02 se mantiene en Android (Room y WorkManager reales) y se amplía a iOS. Se aceptan
tres costos: el iPhone solo se compila en una Mac con Xcode; iOS no garantiza el momento del envío en
segundo plano como WorkManager, así que en iOS la app envía al registrar, al abrirse y con «Enviar ahora»
hasta registrar la tarea de fondo; y Room KMP exige que las consultas sean `suspend` o `Flow`.

## ADR-09 · Arquitectura de microservicios (reemplaza al ADR-01)
**Contexto.** El 28/10 el equipo acordó que SIGRO sea una arquitectura de microservicios en todas sus
piezas, no un monolito modular con servicios satélite. Los seis módulos ya tenían esquema propio y no se
consultaban directamente, así que las fronteras existían: faltaba que cada uno fuera un servicio de verdad.
**Decisión.**

| Servicio | Responsabilidad | Datos (esquema y usuario de base propios) |
|---|---|---|
| **gateway** | Única puerta de entrada `/api/v1`; enruta por la tabla `app/enrutado.py`; no expone `/interno` | ninguno |
| **obras** | Identidad (sesión, usuarios), obras, periodos, almacenes, asignaciones, dispositivos, notificaciones del usuario. Responde a los demás **quién puede qué** | `obra` · `sigro_svc_obras` |
| **catalogo** | Código único de insumo, verificación, factor de conversión, importación del S10 | `catalogo` · `sigro_svc_catalogo` |
| **compras** | Proveedores, compras, solicitudes, requerimiento, saldo por comprar, reposición | `compras` · `sigro_svc_compras` |
| **tareo** | Trabajadores y asistencia (horas hombre) | `tareo` · `sigro_svc_tareo` |
| **almacen**, **costo** | Movimientos, saldo, kárdex y costo promedio (por construir, mismo patrón) | `almacen`, `costo` |
| **integraciones** | S10, SUNAT y RENIEC (Factiliza) | sin base |
| **notificaciones** | Avisos a cada persona (ADR-07) | su propia base |
| **relevo** | Entrega la bandeja de salida (outbox) a notificaciones | `outbox` · `sigro_svc_relevo` |

Reglas de la arquitectura:
1. **Base de datos por servicio.** Un PostgreSQL con un esquema y un usuario por servicio; cada usuario solo
   tiene permisos sobre su esquema (migración 0009). Leer el esquema de otro da «permiso denegado».
2. **Comunicación por API.** Síncrona: REST interno `/interno/...` con token de servicio
   (`X-Servicio-Token`). Asíncrona: eventos por outbox → relevo → notificaciones.
3. **Control de acceso centralizado en su dueño.** Cada servicio pregunta a obras `POST /interno/autorizar`
   en cada petición: una baja o un retiro de la obra corta el acceso al instante en todos los servicios (regla 9).
4. **Código e imagen propios.** Cada servicio tiene su app FastAPI y su imagen Docker; al construirla se
   **poda** el código ajeno (`app/servicios/podar.py`): si un servicio dependiera de otro, no arrancaría.
5. **Fallos aislados.** Si un servicio cae, solo fallan con 503 las rutas que lo necesitan.

**Descartado.** Mantener el monolito modular (ADR-01): no cumple lo acordado. Una base PostgreSQL por servicio
en servidores distintos: en el plan gratuito de la nube (ADR-06) cuesta varias bases; con esquema y usuario por
servicio el aislamiento es el mismo a nivel de permisos y se puede separar después sin tocar el código.
Un bus de mensajes (RabbitMQ, Kafka): para el volumen de una obra, el outbox con relevo da la misma garantía
(al menos una vez, sin perder eventos) sin otra pieza que operar.
**Consecuencias que se aceptan.**
- Cada petición de compras, catálogo o tareo hace una llamada interna a obras para autorizar (milisegundos en la
  misma red). Si obras cae, esos servicios responden 503: obras es el servicio crítico y se despliega primero.
- Lo que antes era un JOIN entre esquemas ahora es una llamada a la API del dueño (p. ej. compras pide al
  catálogo el código y la descripción de los insumos).
- **Fase 1 (hoy):** las claves foráneas que cruzan esquemas y tres disparadores que validan contra la obra
  (plazo de la obra, periodo cerrado) se mantienen en la base, ejecutados con permisos del dueño
  (SECURITY DEFINER), sin que el servicio pueda leer el esquema ajeno. **Fase 2 (Sprint 2):** reemplazarlos por
  réplicas locales alimentadas por eventos (`obra.creada`, `periodo.cerrado`) y quitar esas claves.
- Las pruebas levantan todos los servicios en el mismo proceso, cada uno con su usuario de base, y se hablan
  por el mismo contrato HTTP; `pruebas/test_microservicios.py` verifica las cinco reglas de arriba.


## ADR-10 · Ambiente de pruebas en un solo nodo (03/11/2026)
**Contexto.** El ambiente de pruebas (HT-01, ADR-06) corría en Render con **ocho servicios Docker gratuitos**, uno
por microservicio, y dos problemas que no se arreglan con código:
- **La red privada no sirve en el plan gratuito.** Un servicio gratuito puede *enviar* tráfico privado pero no
  *recibirlo* («Free web services can't receive private network traffic»). El gateway llamaba a cada servicio por
  `https://sigro-<servicio>.onrender.com`, es decir **pasando por Cloudflare**, y Cloudflare empezó a responder
  **429**: el 03/11 `/salud/servicios` mostró catálogo, compras, tareo e integraciones con `error_429`, y almacén y
  costo con 502. El sondeo de la pantalla de arranque (7 llamadas cada 2 s) lo precipitó (hotfix en el PR #26),
  pero cualquier uso normal multiplica las llamadas gateway → servicio → obras (`/interno/autorizar` en cada petición).
- **Cada servicio duerme por separado.** Tras 15 min sin tráfico, Render duerme cada servicio y despertarlo tarda
  ~25 s (medido: 25,2 s para obras sola, `docs/rendimiento-render.md`). Una petición que cruza gateway → servicio →
  obras podía pagar tres despertares.

**Decisión.** Solo en el **ambiente de pruebas**, todos los microservicios corren en **un solo servicio gratuito,
`sigro-api`**: un contenedor (`api/Dockerfile.pruebas`) con **un proceso por microservicio**. El gateway atiende
`$PORT` (el único puerto que Render publica); obras, catálogo, compras, tareo, almacén, costo e integraciones
escuchan en `127.0.0.1:8001–8007`, notificaciones en `8008`, y el relevo corre sin puerto. Un supervisor de
biblioteca estándar (`infra/un_nodo/supervisor.py`) reinicia el proceso que se cae (espera 1, 2, 4 … 30 s),
reenvía `SIGTERM` y pone el nombre del proceso en cada línea de log. Se hablan por `http://127.0.0.1:800x`: sin TLS,
sin Cloudflare, sin 429. La región es **ohio**, la misma de Neon (`aws-us-east-2`). La web (`sigro-web`, estática)
llama a su propio dominio y Render **reescribe** `/api/*` y `/salud/*` hacia `sigro-api` («Destination can be either
a path or a full, publicly accessible URL»): el navegador no habla con otro origen, así que no hay CORS ni preflight
y no se cruza con el *challenge* de Cloudflare en `sigro-api`.

**Qué se mantiene igual (y se sigue verificando).**

| Regla de ADR-09 | En el nodo único |
|---|---|
| Un esquema y un usuario de base por servicio | Igual. Cada proceso recibe **solo su** cadena: `SIGRO_DB_OBRAS` → `SIGRO_APP_DATABASE_URL` de obras, y así. Ningún proceso ve la cadena de otro (`api/pruebas/test_un_nodo.py`). La base sigue negando el esquema ajeno por permisos (migración 0009) |
| Comunicación solo por API (`/interno` con `X-Servicio-Token`) o eventos (outbox) | Igual. Cambia solo la dirección: `127.0.0.1:800x` en lugar de `onrender.com` |
| Control de acceso en obras en cada petición (regla 9) | Igual: `POST /interno/autorizar` en cada petición, ahora sin salir del contenedor |
| Código e imagen propios, podados | Producción sigue con `api/Dockerfile` (una imagen podada por servicio) y `docker-compose.yml` **sin cambios**; CI sigue construyendo cada imagen podada (`test_microservicios.py`). La imagen de pruebas lleva todo el código, pero cada proceso importa solo su `app.servicios.<servicio>` |
| Fallos aislados | Un proceso que se cae no tumba a los demás: el supervisor lo reinicia y mientras tanto solo sus rutas responden 503. Si falta la variable de base de un servicio, ese servicio no arranca y el resto sí |
| Secretos solo donde se usan | El gateway y las integraciones no reciben cadenas de base ni el secreto JWT; `FACTILIZA_TOKEN` solo va a integraciones |

**Descartado.**
- *Seguir con ocho servicios gratuitos* y bajar el tráfico interno: no quita el paso por Cloudflare ni los ocho
  despertares; el 429 vuelve con el uso normal.
- *Planes pagos o `type: pserv` con red privada*: resuelve las dos cosas y es lo que corresponde a producción, pero
  el presupuesto del ambiente de pruebas es cero (ADR-06).
- *Un solo proceso Python que monte todas las apps*: menos memoria todavía, pero los servicios compartirían
  proceso, memoria, conexión y fallos, y dejaría de ser una arquitectura de microservicios. La precarga con `fork()`
  consigue la mayor parte del ahorro sin juntar procesos.
- *honcho / supervisord*: honcho no reinicia un proceso caído (si uno muere, para todos) y supervisord agrega una
  dependencia y su configuración para lo que hacen unas 300 líneas de biblioteca estándar (que además
  hacen la precarga con `fork()`).
- *Otra nube gratuita* (Fly.io, Railway): cambiar de proveedor a mitad del Sprint 2 cuesta más que esta decisión, y
  sus niveles gratuitos tienen límites parecidos.

**Consecuencias que se aceptan.**
- **Memoria: 512 MB y 0,1 CPU para diez procesos.** Solo cabe con precarga y `fork()` (ver «Memoria», abajo). Si no
  alcanza, `SIGRO_UN_NODO_EXTRAS=0` quita relevo y notificaciones: los avisos y los hechos de costo esperan en
  `outbox.evento`, sin perderse. Con 0,1 CPU, una importación del S10 hace más lentas las demás peticiones.
- **Un solo punto de fallo en pruebas**: si `sigro-api` cae o se redespliega, cae todo el ambiente a la vez. En
  pruebas es aceptable; en producción no, y por eso producción no cambia.
- **Un solo despertar** en lugar de uno por servicio; a cambio, el arranque en frío suma el de los diez procesos.
- **Notificaciones pierde su base al dormir** (sistema de archivos efímero): la campana del usuario se vacía al
  redesplegar; el evento original queda en `outbox.evento`.
- La reescritura de `sigro-web` hacia `sigro-api` depende de que Render reenvíe métodos y cabeceras; Render no lo
  documenta. Se prueba con el login (un POST) al migrar y, si falla, `VITE_API_URL` vuelve a apuntar a `sigro-api`
  (la API ya acepta ese origen por `SIGRO_CORS`).
- La app del almacenero apunta a `https://sigro-api.onrender.com` (antes `sigro-gateway`).

**Memoria.** Medido en CI (job `un-nodo`, Linux, contenedor con `--memory 512m` como Render gratis, datos reales del
cliente), con la prueba de humo completa por el gateway:

| Cómo se lanzan los procesos | Diez procesos vivos | PSS total | Contenedor | Resultado |
|---|---|---|---|---|
| Cada servicio con su propio intérprete (run 41674366797) | **no**: el núcleo mata obras, catálogo, almacén y costo al arrancar (código -9) y quedan en bucle de reinicio | 488 MB con compras ya muerta | 450 MiB / 512 (88 %) | **no cabe** |
| **Precarga + `fork()`** (run 41675739156) | **sí**, sin un solo reinicio | **398 MB** | **366 MiB / 512 (71,5 %)** | humo en verde |

Por eso el supervisor, en Linux, carga **una vez** las bibliotecas comunes del venv de la API (FastAPI, pydantic,
SQLAlchemy, psycopg, httpx, uvicorn…) y crea cada servicio con `fork()` seguido de `gc.freeze()`, como
`gunicorn --preload`. Cada servicio **sigue siendo un proceso aparte**: su PID, su entorno (`os.environ` se
reemplaza en el hijo por el suyo), su cadena de base, su pool de conexiones y sus fallos; lo que comparten son las
páginas de solo lectura de esas bibliotecas (copia al escribir). El código `app.*` se importa recién en el hijo,
con su entorno. Cada servicio de la API bajó de ~55 MB a 25–41 MB de PSS. Integraciones y notificaciones fijan
otras versiones de FastAPI y se lanzan con su propio intérprete (~45–49 MB cada uno). Margen: ~146 MiB para picos
(una importación del S10, un catálogo grande). Si el log de Render muestra `terminó con código -9`, primero
`SIGRO_UN_NODO_EXTRAS=0` (relevo y notificaciones fuera, ~75 MB) y después, si hace falta, subir el plan de
`sigro-api`. `SIGRO_UN_NODO_FORK=0` vuelve a un intérprete por servicio (solo para diagnosticar).

**Cómo se vuelve atrás.** `render.yaml` anterior (ocho servicios) está en la historia de Git; el paso a un nodo
(`docs/despliegue-pruebas.md`) deja los servicios viejos **suspendidos** antes de borrarlos.
