# Rendimiento en Render · `GET /api/v1/obras`

Rama `perf-render`. Pregunta: **¿por qué consultar las obras en el ambiente de pruebas tarda tanto?**
Regla del trabajo: medir antes de cambiar y medir después con la misma herramienta.

## Cómo se mide

- **Cabecera `Server-Timing`** en el gateway y en cada servicio (`api/app/medicion.py`). Una respuesta trae:

  ```
  Server-Timing: gw;dur=…, gw-up;dur=…, obras;dur=…, obras-db;dur=…;desc="N consultas",
                 obras-db-con;dur=…;desc="N conexiones nuevas", obras-interno;dur=…;desc="N llamadas"
  ```

  | Entrada | Qué es |
  |---|---|
  | `gw` | tiempo total dentro del gateway |
  | `gw-up` | espera del gateway al servicio dueño de la ruta (incluye ir y volver por Cloudflare) |
  | `<servicio>` | tiempo total dentro del servicio |
  | `<servicio>-db` | espera a PostgreSQL y número de consultas |
  | `<servicio>-db-con` | apertura de conexiones nuevas a Neon (TCP + TLS + autenticación); 0 si el pool ya tenía una |
  | `<servicio>-interno` | espera a otros servicios por `/interno/...` y número de llamadas |

  Lo que queda **fuera** de `gw` (curl `ttfb` − `gw`) es red: navegador → Cloudflare → Render.
  `SIGRO_SERVER_TIMING=0` quita la cabecera.
- **CF-Ray en el log.** Cada servicio escribe una línea por petición:
  `sigro servicio=obras ray=a546f4ef8a38e9ae-SCL GET /api/v1/obras -> 200 total=… db=…ms/N con=…ms/N interno=…ms/N`.
  El gateway reenvía el CF-Ray con que Cloudflare marcó la entrada como `X-Sigro-Ray` (cada salto entre servicios
  por `onrender.com` recibe un ray nuevo), y los servicios lo pasan a sus llamadas `/interno`: un mismo ray une
  las líneas del gateway, del servicio y de obras en los logs de Render.
- **Script** `api/docs/rendimiento/medir_render.sh`: curl con tiempos (dns, tcp, tls, primer byte, total),
  CF-Ray y `Server-Timing`, **una petición cada 5 s** para que Cloudflare no bloquee la IP. El token de sesión
  se lee de `SIGRO_TOKEN` y no se imprime.

## Antes (02/11/2026, `main` = `5ec9b89`, servicios despiertos)

Desde Lima; Cloudflare atendió en **SCL** (Santiago) en todas las peticiones. Sin `Server-Timing` todavía
(no estaba desplegado): solo tiempos de curl.

| Ruta | n | Primer byte (mediana) | Rango | Qué recorre |
|---|---|---|---|---|
| `GET /salud` (gateway) | 6 | **0,38 s** | 0,36–0,79 s | cliente → Cloudflare → gateway |
| `GET /api/v1/obras` sin sesión (401) | 7 | **0,45 s** | 0,42–0,48 s | … → gateway → Cloudflare → obras; **no toca la base** |
| `GET /salud` (obras, directo) | 5 | **0,35 s** | 0,34–0,36 s | cliente → Cloudflare → obras; **no toca la base** |
| `GET /api/v1/obras` con sesión (obras, directo) | 7 | **0,75 s** | 0,72–1,18 s | cliente → Cloudflare → obras → Neon |
| `GET /api/v1/obras` con sesión, por el gateway | — | *no medido* | — | ver nota |
| `GET /api/v1/obras` con sesión, en frío (obras, directo) | 1 | **25,2 s** | — | despertar obras (Render) + Neon → consulta |

**Nota sobre la medición con sesión (02/11/2026, 22:54, `main` = `9570173`).** Cloudflare respondió **429** a
la IP del autor en `sigro-gateway.onrender.com`, así que **el gateway no se pudo medir desde esa red** y la ruta
con sesión se midió **directo contra `https://sigro-obras.onrender.com/api/v1/obras`**, con el mismo script y la
misma cadencia (`SIGRO_BASE=https://sigro-obras.onrender.com`, una petición cada 5 s). Entre `5ec9b89` y
`9570173` no cambió ningún archivo de la ruta medida (`modulos/obra/rutas.py`, `db.py`, `internos.py`). Para
comparar con la entrada por el gateway, súmese el salto gateway → obras medido arriba (~0,06 s). La sesión ve
**1 obra**, es decir, 4 + 2 × 1 = **6 viajes a Neon** por petición. Las 7 respuestas fueron 200, todas en SCL.
Salida cruda (sin token: solo tiempos y CF-Ray): `api/docs/rendimiento/antes-2026-11-02-obras-directo.txt` y
`api/docs/rendimiento/antes-2026-11-02-gateway-sin-sesion.txt`.

**En frío (02/11/2026, 23:19).** También directo contra obras, por el mismo 429 del gateway: 16 min sin ninguna
petición nuestra (de 23:03:12 a 23:19) y luego **una sola** `GET /api/v1/obras` con sesión → 200 en
**25,2 s** de primer byte (SCL; DNS + TCP + TLS ~0,13 s como siempre). Casi todo es el despertar del servicio
gratuito de Render; incluye además reanudar el cómputo de Neon (se suspende a los 5 min) y abrir la primera
conexión. Sin `Server-Timing` no se puede separar una parte de la otra. No se controla que otra persona del equipo
no haya usado el ambiente en esos 16 min, pero 25 s solo se explican con el servicio dormido. Por el gateway habría
que sumar su propio despertar si también dormía. Salida cruda: `api/docs/rendimiento/antes-2026-11-02-obras-frio.txt`.

Lectura de lo medido:

- DNS + TCP + TLS hasta Cloudflare: ~0,13 s. De ahí al primer byte del gateway, ~0,24 s: es la ida a Render
  y la vuelta, sin trabajo (el `/salud` del gateway no hace nada).
- El salto **gateway → obras** por `https://sigro-obras.onrender.com` cuesta **~0,06 s** (0,45 − 0,38). Pasa
  por Cloudflare, pero el gateway ya usaba un cliente httpx persistente (keep-alive), así que no paga TLS en
  cada petición. **No es donde se va el tiempo.**
- **La base es lo que pesa.** El mismo servicio responde `/salud` en 0,35 s y `/api/v1/obras` con sesión en
  0,75 s: **~0,40 s** dentro de obras, para 6 viajes a Neon con 1 sola obra. Eso son **~65 ms por viaje**, que
  coincide con un ida y vuelta Oregon ↔ US East (la hipótesis de región de abajo). Es una inferencia sobre
  tiempos de curl: lo confirma `obras-db` ÷ consultas cuando `Server-Timing` esté desplegado.
- Con más obras el costo crece en ~0,13 s por obra (2 viajes cada una) mientras siga el N+1.
- Una de las 7 peticiones tardó 1,18 s (#5): el resto estuvo entre 0,72 y 0,89 s.

### Lo que dice el código (antes)

`GET /api/v1/obras` en el servicio obras, por petición:

| Paso | Viajes a Neon |
|---|---|
| `pool_pre_ping` al sacar la conexión del pool | 1 |
| abrir conexión nueva si el pool estaba vacío (5 min sin uso → Neon suspende y corta) | TCP + TLS + auth |
| `usuario_autenticado`: el usuario sigue activo | 1 |
| `_obras_de`: obras asignadas | 1 |
| `_obra_out` por **cada** obra: proyecto + periodos | **2 × N** |
| `COMMIT` | 1 |

Con 3 obras son **10 viajes**. Si cada viaje cuesta lo que un ida y vuelta entre la región de Render y la de
Neon, el tiempo crece con el número de obras: es un **N+1**.

Además, para todo lo que **no** es obras (catálogo, compras, tareo): cada petición pregunta a obras
`POST /interno/autorizar` con un `httpx.Client` **nuevo por llamada**, es decir TCP + TLS a Cloudflare otra vez
en cada petición.

**Región.** `render.yaml` no fija `region:` (Render usa **Oregon** por omisión) y `docs/despliegue-pruebas.md`
crea Neon en **US East**. Si es así, cada viaje a la base cruza el país (del orden de 60–70 ms). Lo confirma o
lo descarta `obras-db` ÷ número de consultas en la primera medición con sesión.

## Qué cambia esta rama

| # | Cambio | Dónde | Efecto esperado |
|---|---|---|---|
| 1 | `Server-Timing` + CF-Ray en el log, en gateway y servicios | `app/medicion.py`, `servicios/base.py`, `servicios/gateway.py`, `db.py`, `internos.py` | medir sin adivinar |
| 2 | `GET /obras` en **una** consulta (`LEFT JOIN LATERAL` a periodos) | `modulos/obra/rutas.py` | de 4 + 2N a **4** viajes, sin importar N. Es la misma consulta del PR #18, así los dos PR no chocan |
| 3 | Cliente httpx **persistente** para `/interno` (keep-alive, seguro entre hilos) | `internos.py` | catálogo, compras y tareo dejan de pagar TLS en cada `autorizar` |
| 4 | Pool chico pero persistente: 2 + 3, `pool_recycle=240 s` (< 5 min de suspensión de Neon), `pre_ping` | `db.py`, `render.yaml` | conexiones abiertas listas; ninguna «muerta» |
| 5 | Con la cadena **-pooler** de Neon: `prepare_threshold=None` | `db.py` | PgBouncer en modo transacción no ve las sentencias preparadas de otra conexión física |
| 6 | `GET /salud/servicios` despierta a **todos** los servicios a la vez | `servicios/gateway.py` (mismo commit que el PR #23) | en frío, el usuario espera **un** despertar, no uno por servicio en cadena |

### Lo que **no** se hizo, y por qué

- **Red privada de Render.** Según la documentación de Render, en el plan gratuito un servicio web **puede
  enviar** tráfico por la red privada **pero no recibirlo**. El gateway podría llamar a
  `http://sigro-obras:10000`, pero obras no lo atendería. Así que las `SIGRO_URL_*` siguen siendo
  `https://…onrender.com`; `render.yaml` deja escrita la forma interna para cuando los servicios pasen a un plan
  pago o a `type: pserv`, **en la misma región**. Lo medido arriba dice que hoy ese salto cuesta ~60 ms: no
  justifica pagar todavía.
- **Caché de 30 s del permiso.** En permisos no hay N+1: es **una** llamada a `/interno/autorizar` por petición.
  Un caché rompería la regla 9 tal como está escrita en `app/dependencias.py`: «una baja o un retiro de la obra
  corta el acceso **al instante** en todos los servicios». Con el cliente persistente, esa llamada ya no paga TLS.
  Si después de medir sigue pesando, es una decisión del Product Owner, no de rendimiento.
- **Cambiar de región.** En Render la región se fija al crear el servicio. Si la medición con sesión confirma
  que la base está lejos, lo correcto es **recrear los servicios en la región de Neon** (`virginia` para
  `aws-us-east-1`, `ohio` para `aws-us-east-2`) y agregar `region:` en `render.yaml`. Es trabajo en el panel
  de Render; no se hace desde este PR.

### Pasos en el panel (después de fusionar)

1. En cada servicio con base (`sigro-obras`, `sigro-catalogo`, `sigro-compras`, `sigro-tareo`):
   `SIGRO_APP_DATABASE_URL` con el host **`-pooler`** de Neon (Neon → *Connection string* → *Pooled connection*).
   Las migraciones siguen con la cadena directa (`SIGRO_DATABASE_URL`).
2. Comprobar en el log de arranque: `sigro base: pooler=sí pool=2+3`.
3. Volver a medir con el script y llenar la tabla de abajo.

## Después

*Pendiente de despliegue en Render.* Misma ruta, mismo script, misma cadencia (1 cada 5 s). Para que la
comparación sea justa con el «antes» con sesión, medir **también directo contra `sigro-obras`** (si el gateway
sigue bloqueado, solo así).

| Ruta | n | Primer byte (mediana) | `gw-up` | `obras-db` (consultas) | `obras-db-con` |
|---|---|---|---|---|---|
| `GET /api/v1/obras` con sesión, despierto | | | | | |
| `GET /api/v1/obras` con sesión, en frío | | | | | |

### Un nodo (ADR-10, rama `deploy-un-nodo`)

Misma ruta y mismo script (`medir_render.sh`, una petición cada 5 s), con `SIGRO_BASE` apuntando al gateway del
**nodo único**: el supervisor y los diez procesos (`infra/un_nodo/supervisor.py`), cada servicio con su usuario de
base. **Medido en local (03/11/2026, 11:20, Windows 11, PostgreSQL 16.15 local, datos reales del cliente), no en
Render**: no hay Cloudflare entre el navegador y el gateway ni red hasta Neon, así que estas cifras aíslan lo que
cuesta el gateway y el salto entre procesos por `127.0.0.1`; no reemplazan la medición en Render, que queda pendiente
de crear `sigro-api` (fila de abajo). Docker no arrancó en la máquina del autor (falta *Virtual Machine Platform*):
el contenedor se construyó y probó en CI (job `un-nodo`, Linux, `--memory 512m` como Render): los diez procesos
vivos en **366 MiB de 512** con la precarga + `fork()` del supervisor (ADR-10, «Memoria»), y la prueba de humo en verde
(en esa corrida, por el gateway: `GET /api/v1/obras` 22 ms, catálogo con 2 243 insumos 94 ms, salida de almacén 28 ms;
una petición de cada una, no la cadencia del script).

| Ruta | n | Primer byte (mediana) | Rango | `gw-up` (mediana) | `obras-db` (consultas) | `obras-db-con` |
|---|---|---|---|---|---|---|
| `GET /api/v1/obras` con sesión, despierto · un nodo **local** | 7 | **0,022 s** | 0,020–0,023 s | 18,6 ms | 2,2 ms (2) | 0 |
| `GET /api/v1/obras` con sesión, en frío · un nodo **local** | 1 | **0,072 s** | — | 68,3 ms | 7,7 ms (2) | 0 |
| arranque en frío del nodo (`/salud/servicios` → 200, los 7 servicios) · **local** | 1 | **10,2 s** | — | — | — | — |
| `GET /api/v1/obras` con sesión, despierto · `sigro-api` en **Render** | | *pendiente* | | | | |
| `GET /api/v1/obras` con sesión, en frío · `sigro-api` en **Render** | | *pendiente* | | | | |

Lectura:

- **El salto gateway → obras por `127.0.0.1` cuesta ~11 ms** (`gw-up` 18,6 − `obras` 7,7). Antes, por
  `https://sigro-obras.onrender.com`, eran **~60 ms** y, sobre todo, pasaba por Cloudflare, que respondía 429.
- «En frío» local = la primera `GET /api/v1/obras` tras arrancar el nodo. El login previo ya había abierto la conexión
  de obras (`obras-db-con` 0); los 68 ms son la primera ejecución de la ruta en ese proceso. En Render el frío lo
  domina el despertar del servicio (25,2 s medidos el 02/11 para obras sola): con un nodo se despierta **una** vez
  para todo el sistema en lugar de una por servicio.
- El arranque de los diez procesos (10,2 s en local) se suma al despertar de Render; con 0,1 CPU en el plan gratuito
  será más lento. Esa cifra es la que debe medirse primero en `sigro-api`.

Salida cruda: `api/docs/rendimiento/un-nodo-2026-11-03-local-frio.txt`, `…-local-despierto.txt` y la prueba de
humo `…-local-humo.txt` (login, catálogo con 2 243 insumos, 626 proveedores, ingreso y salida de almacén con el costo
congelado, reenvío idempotente y 403 de un rol sin permiso, todo por el gateway).

Pruebas sin base que sostienen los cambios: `api/pruebas/test_medicion.py` (cabecera, conteo de consultas y de
conexiones con SQLite, ray reenviado y no falsificable desde afuera, pool y `-pooler`) y
`api/pruebas/test_gateway_salud.py` (despertar en paralelo).

Fuentes: Render, [Private Network](https://render.com/docs/private-network) y
[Deploy for Free](https://render.com/docs/free).
