# Rendimiento de la web y de la API · medición y optimización (Sprint 2)

Medido el 01/11/2026 por Claude Code (por Fernando Istaña) en la rama `web-api-optimizacion`. Cada cifra tiene su
«antes» medido sobre `main` (5ec9b89) y su «después» sobre la rama, en la misma máquina, el mismo día y con el mismo
script. Nada está estimado: donde no se pudo medir, se dice.

**Resumen**

| Qué | Antes | Después |
|---|---:|---:|
| JS que baja la web al abrir (gzip) | 93.6 KiB, 1 archivo | 69.4 KiB (React 52.2 + SIGRO 17.2) + la pantalla abierta (~2 KiB) |
| Bytes totales de la primera carga (red real, sin caché) | 229.9 KiB, 7 peticiones, 3 orígenes | 141.3 KiB, 9 peticiones, **1 origen** |
| LCP en «Slow 4G» (150 ms, 1.6 Mbps) | 1 736 ms | 1 216 ms |
| Catálogo completo (2 243 insumos): nodos del DOM | 27 061 | 469 |
| Catálogo completo: ver los 2 243 | 60.7 s (37 clics en «Mostrar 60 más», CPU ×4) | inmediato: están todos, se desplaza |
| Catálogo completo: FPS al desplazar, CPU ×4 / sin limitar | 4.6 / 26.6 | 25.6 / 159.4 |
| Catálogo completo: peor cuadro, sin limitar | 60.7 ms | 17.6 ms |
| Logos del menú | 83 KiB (PNG 800×561) | 14.6 KiB (WebP 165×116) |
| `POST importar-s10` con el S10 real (284 filas) | 288 sentencias SQL | 5 |
| `POST requerimiento/importar` con el S10 real | 288 sentencias SQL | 5 |
| `GET /obras` (5 obras) | 12 sentencias SQL | 2 |
| `GET /obras/{id}/jornadas` (20 160 jornadas de EJEMPLO) | 5.6 MiB siempre | igual sin parámetros; `?limite=200`: 57 KiB |
| Consulta de insumos por código (compras → catálogo) | 0.60 ms | 0.03 ms con el índice propuesto (no aplicado) |
| Arranque en frío en Render (plan gratuito) | 21 a 31 s por servicio | sin cambio: es del plan; ver la propuesta |

---

## 1. Web (revisa Henry)

### 1.1 Cómo se midió

- **Bundle:** `npm run analizar` (= `vite build --mode analisis`) deja el mapa en `web/dist/estadisticas.html`
  ([rollup-plugin-visualizer](https://github.com/btd/rollup-plugin-visualizer), crudo/gzip/brotli por módulo).
  `node scripts/tamanos.mjs` imprime la tabla de abajo (gzip nivel 9 y brotli por archivo). Nota: `vite build
  --report` no existe en Vite; por eso el visualizador.
- **Navegador:** `web/scripts/medir-web.mjs` abre Chrome 1440×900 (playwright-core + CDP) contra `vite preview` en
  `:5175`, con el cliente simulado (sin `VITE_API_URL`; los datos del catálogo son los 2 243 reales del cliente).
  Carga: 5 repeticiones sin caché, se informa la mediana. Catálogo: 3 repeticiones, mediana, con la CPU ×4 más lenta
  (perfil de una laptop de oficina modesta) y sin limitar. FPS: 4 s bajando 120 px por cuadro con `requestAnimationFrame`
  (Chrome sin pantalla no se limita a 60 Hz, por eso «sin limitar» pasa de 60). Cuadro largo = más de 50 ms.

### 1.2 Bundle: crudo y gzip por archivo

**Antes** (`main`):

| Archivo | Crudo (KiB) | gzip (KiB) | brotli (KiB) | Cuándo baja |
|---|---:|---:|---:|---|
| index.js | 321.04 | 93.62 | 80.21 | al abrir |
| catalogo-cliente.js | 147.13 | 34.36 | 29.18 | cliente simulado, al abrir el catálogo |
| sevens-logo.png | 51.93 | 50.25 | 50.13 | al abrir |
| proveedores-cliente.js | 31.98 | 11.30 | 9.66 | cliente simulado, al abrir proveedores |
| sevens-logo-blanco.png | 30.76 | 29.64 | 29.60 | al abrir (tema oscuro y pantalla Entrar) |
| index.css | 8.73 | 2.70 | 2.34 | al abrir |
| Inter (Google Fonts: CSS + woff2) | — | 47.2 | — | al abrir, desde 2 orígenes externos |

**Después** (rama):

| Archivo | Crudo (KiB) | gzip (KiB) | brotli (KiB) | Cuándo baja |
|---|---:|---:|---:|---|
| react.js (React, ReactDOM, router) | 160.10 | 52.17 | 45.70 | al abrir; cambia casi nunca → queda en caché entre despliegues |
| index.js (armazón de SIGRO) | 52.80 | 17.24 | 15.18 | al abrir |
| catalogo-cliente.js | 147.13 | 34.36 | 29.18 | igual que antes |
| fuentes/inter-latin-variable.woff2 | 47.13 | 47.12 | 47.13 | al abrir, precargada, mismo origen |
| Catalogo.js (pantalla + lista virtual) | 45.31 | 14.18 | 12.87 | al abrir el catálogo |
| insumos-s10.js | 32.45 | 6.53 | 5.53 | cliente simulado, solo si se usa |
| proveedores-cliente.js | 31.98 | 11.30 | 9.66 | igual que antes |
| sevens-logo-blanco.png | 30.76 | 29.64 | 29.60 | solo en la pantalla Entrar (ver pendientes) |
| Importar.js · Compras.js · Movimientos.js · Tablero.js · Reposicion.js · Parametros.js · Proveedores.js · Obra.js | 4.9 a 13.3 | 2.0 a 4.6 | — | cada una al abrir su pantalla |
| index.css | 9.10 | 2.89 | 2.50 | al abrir |
| sevens-logo.webp | 8.87 | 8.89 | 8.88 | al abrir |
| sevens-logo-blanco.webp | 5.68 | 5.71 | 5.69 | al abrir |
| base.js (componentes comunes) | 3.95 | 1.48 | 1.27 | con la primera pantalla |

### 1.3 Qué se cambió

1. **Rutas con `React.lazy` + `Suspense`** (`web/src/App.tsx`): cada pantalla es su propio archivo. El guardián de
   errores existente envuelve el `Suspense`, así que si un trozo no llega (red caída) la persona ve la tarjeta de
   «Esta pantalla tuvo un problema», no una página en blanco. `Entrar` sigue en el arranque (es lo primero que se ve
   con la API real).
2. **React aparte** (`manualChunks` en `vite.config.ts`): 52 KiB gzip que el navegador conserva entre despliegues.
3. **Insumos del S10 del cliente simulado fuera del arranque** (`import()` dinámico en `ClienteSimulado`; `ClienteHttp`
   no se tocó): −6.8 KiB gzip para todos.
4. **Catálogo virtualizado** (`web/src/ui/FilasVirtuales.tsx`, nuevo, con
   [@tanstack/react-virtual](https://tanstack.com/virtual)): un `<tbody>` que solo pinta las filas visibles más 12 de
   margen y dos filas espaciadoras con el alto del resto. Se desplaza la ventana, no una caja: la página se comporta
   como las demás. Las filas se miden al pintarse (las que saltan a dos líneas no descuadran), se memorizan (al
   desplazar solo se pintan las que entran) y sin `flushSync`. El catálogo ahora muestra los 2 243 de una vez y el
   botón «Mostrar 60 más» desaparece. En `Catalogo.tsx` el cambio es: el `<tbody>` pasa a `<FilasVirtuales>`, se
   quita el tope de 60 y se fijan los anchos de columna con `<colgroup>` (si no, las columnas cambiaban de ancho al
   entrar y salir filas: medido 101 a 146 px en «Marca»; ahora constantes).
5. **Inter propia** (`web/public/fuentes/`, `web/src/fuentes.css`, `index.html`): una sola fuente **variable** (pesos
   100–900, el diseño usa 400–800), **subconjunto latino** (español completo, «», —, …, €; lo demás cae a
   `system-ui`), **woff2**, `font-display: swap` y `<link rel="preload">`. Antes: hoja de Google Fonts que bloqueaba el
   pintado + `fonts.gstatic.com` sin `preconnect`. Licencia OFL en `public/fuentes/LICENSE-Inter.txt`.
6. **Logos a su tamaño real**: en el menú miden 58 px de alto; se sirven a 116 px (pantallas 2x) en WebP calidad 90
   (165×116): 53.2 + 31.5 KiB → 8.9 + 5.7 KiB. Con `width`/`height` declarados (sin saltos de diseño).

### 1.4 Medición en el navegador

**Primera carga** (`#/tablero`, sin caché, mediana de 5):

| | Antes | Después |
|---|---:|---:|
| Bytes transferidos | 229.9 KiB | 141.3 KiB |
| … de JS / fuente / imágenes / CSS | 94.4 / 47.2 / 83.2 / 4.3 | 74.6 / 47.4 / 15.1 / 3.3 |
| Peticiones · orígenes | 7 · 3 | 9 · 1 |
| FCP / LCP, red local | 528 / 532 ms | 76 / 76 ms |
| FCP / LCP, «Slow 4G» | 912 / 1 736 ms | 972 / 1 216 ms |

El FCP en Slow 4G no mejora (912 → 972 ms, dentro del ruido de esa red): el primer pintado espera al JS igual que
antes y la fuente precargada compite por el mismo enlace. Lo que mejora es el LCP (−30 %): el texto final ya no espera
a una hoja de estilos externa ni a dos conexiones TLS más.

**Catálogo** (CPU ×4 salvo donde dice «sin limitar»):

| | Antes · primeras 60 | Antes · los 2 243 | Después · los 2 243 |
|---|---:|---:|---:|
| Filas en el DOM | 60 | 2 243 | 27 al abrir, 46 al desplazar |
| Nodos del DOM | 867 | 27 061 | 469 |
| Memoria JS | 5.0 MiB | 43.9 MiB | 5.2 MiB |
| Tiempo para tener todo el catálogo a la vista | — | 60.7 s (37 clics; 10.6 s sin limitar) | 29 ms (12 ms sin limitar) |
| FPS al desplazar · CPU ×4 | (página corta) | 4.6 | 25.6 |
| FPS al desplazar · sin limitar | (página corta) | 26.6 | 159.4 |
| Peor cuadro · sin limitar | 6.1 ms | 60.7 ms | 17.6 ms |

Con CPU ×4 y 120 px por cuadro (7 200 px/s, un desplazamiento muy rápido) quedan cuadros de hasta 67 ms al montar filas
nuevas; a velocidad normal y CPU normal no hay cuadros largos.

### 1.5 Pendiente y a tener en cuenta

- **Choca con el PR #15 (`web-vidrio-resto`, Subagente B)** en `App.tsx` y `Catalogo.tsx`. Resolución, la haga quien
  integre segundo: en `App.tsx`, `Accesos` también como `lazy(() => import('./paginas/Accesos').then((m) => ({ default:
  m.Accesos })))` dentro del mismo `Suspense`; en `Catalogo.tsx`, el `<tbody>{recorte.map(... <FilaInsumo/>)}</tbody>`
  de B pasa a `<FilasVirtuales items={visibles} columnas={6} fila={(ins, _i, medir) => <FilaInsumo key={ins.codigo}
  medir={medir} insumo={ins} … />} />`, `FilaInsumo` recibe `medir` y lo pone en su `<tr {...medir}>`, se quitan
  `PASO`/`tope`/«Mostrar 60 más» y se fijan los anchos con `<colgroup>` sobre `.tabla-vidrio`.
- `Entrar.tsx` (de B) sigue usando `sevens-logo-blanco.png` de 800×561 hasta a 120 px de alto: con un WebP de 240 px
  bajaría de 29.6 a unos 10 KiB. No se tocó para no pisar el rediseño.
- Ctrl+F del navegador no encuentra insumos fuera de pantalla (no están en el DOM); el buscador de la página sí.
- Los dos logos (claro y oscuro) bajan siempre aunque uno esté oculto; ya pesan 14.6 KiB juntos.

---

## 2. API (revisa Adiel)

### 2.1 Datos y método

PostgreSQL 16.10 local en `:5434` (Docker Desktop no arrancó en este equipo; el PostgreSQL es nativo). Base
`sigro_perf` migrada hasta 0009 y sembrada con `api/docs/rendimiento/medir_consultas.py sembrar`:

| Dato | Filas | Origen |
|---|---:|---|
| catalogo.insumo | 2 527 | **REAL**: 2 243 del catálogo del cliente (`semillas/catalogo-cliente.json`) + 284 de la Relación de insumos del S10 (`insumos-cliente/5.3`, leída con `integraciones/s10.py` y aplicada por `POST /catalogo/insumos/importar-s10`) |
| compras.proveedor | 626 | **REAL** (`semillas/proveedores-cliente.json`) |
| compras.requerimiento | 284 | **REAL**: el S10 por `POST /obras/{id}/requerimiento/importar` |
| obra.proyecto | 5 | 1 real (Multifamiliar Flores) + 4 de **EJEMPLO** |
| compras.compra / compra_detalle | 1 200 / 5 400 | **EJEMPLO** (aún no hay compras reales); números de comprobante `E…` |
| compras.solicitud / solicitud_linea | 300 / 1 200 | **EJEMPLO** (1 de cada 4 líneas sin código, «EJEMPLO material sin código») |
| tareo.trabajador / jornada | 80 / 20 160 | **EJEMPLO**: 80 trabajadores × 10 meses de lunes a sábado |

`explicar` corre cada consulta tal como la manda el servicio, 3 veces para calentar y 5 con `EXPLAIN (ANALYZE,
BUFFERS)`; se informa la mediana. `contar` hace las peticiones de la web contra los servicios en proceso (mismo
enrutado que el gateway, cada servicio con su rol `sigro_svc_*`) y cuenta las sentencias que llegan a PostgreSQL con el
evento `before_cursor_execute` de SQLAlchemy (sin contar `SET ROLE`/`SAVEPOINT` de la infraestructura). El «antes» es
el mismo script con `SIGRO_PERF_APP` apuntando al código de `main`.

### 2.2 Las consultas más usadas y por qué

Elegidas por quién las dispara: `web/src/api/http.ts` (`ClienteHttp`) y la app (`android/`: `/catalogo/insumos`,
`/auth/yo`, dispositivos) más las llamadas internas que esas rutas provocan.

| # | Quién la dispara | Mediana | Filas | Plan |
|---|---|---:|---:|---|
| Q1 | **Toda** petición a catálogo, compras y tareo: obras `POST /interno/autorizar` | 0.010 ms | 1 | Seq Scan (asignación tiene 5 filas; con más, usa `uq_asignacion_vigente`) |
| Q2 | Web al abrir y al cambiar de obra: `GET /obras` (dos veces por carga) | 0.159 ms | 5 | consulta única de esta rama (antes 1 + 2 por obra) |
| Q3 | Web y app: `GET /catalogo/insumos` | 1.77 ms | 2 527 | Seq Scan + Sort (todo el catálogo: es lo correcto) |
| Q4 | App y web: `GET /catalogo/insumos?q=cemento` | 1.18 ms | 9 | Seq Scan, 40 bloques |
| Q5 | Compras → catálogo `POST /interno/insumos/buscar` (registrar compra, solicitud, requerimiento, reposición) | 0.60 ms | 2 | **Seq Scan**: `upper(codigo)` no tiene índice |
| Q6 | Web Proveedores: `GET /proveedores?q=ferre` | 0.41 ms | 17 | Seq Scan + subconsultas por proveedor |
| Q7 | Web Compras (nueva compra): `GET /proveedores?solo_para_comprar=true` | 6.34 ms | 626 | 2 subconsultas por proveedor sobre `compra`: 4 913 bloques |
| Q8 | Web Reposición: `GET /obras/{id}/saldo-por-comprar` | 3.13 ms | 284 | Hash Join sobre la vista |
| Q9 | Web Compras: `GET /obras/{id}/compras` (EJEMPLO) | 0.64 ms | 720 | Seq Scan + Hash Join |
| Q10 | Web Compras: líneas de `GET /obras/{id}/solicitudes` (EJEMPLO) | 0.92 ms | 240 | Seq Scan de líneas: el único índice por solicitud es parcial (`WHERE insumo_id IS NOT NULL`) |
| Q11 | App tareo: `GET /obras/{id}/jornadas?fecha=…` (EJEMPLO) | 0.077 ms | 80 | Index Scan `ix_jornada_proyecto_id_fecha` |

Conclusión honesta: **la base no es el cuello de botella**. Todas las consultas tardan entre 0.01 y 6 ms; lo que pesa
es el tamaño de las respuestas (2.3), los viajes de más (2.4) y, sobre todo, el arranque en frío de Render (3).

### 2.3 Índices que faltan · PROPUESTA para Adiel (no aplicada)

`api/migraciones/propuestas/0011_indices_rendimiento.py` (fuera de `versions/`; con `upgrade` y `downgrade`, probados
ida, vuelta e ida sobre `sigro_perf`). Efecto medido creando los índices a mano en la base local:

| Índice | Consulta | Sin índice | Con índice |
|---|---|---:|---:|
| `catalogo.ix_insumo_codigo_upper` `(upper(codigo))` | Q5 | 0.60 ms · 40 bloques | **0.03 ms** · 12 bloques |
| `compras.ix_compra_proveedor_registrada` `(proveedor_id, fecha) WHERE estado = 'registrada'` | Q7 | 6.34 ms · 4 913 bloques | **1.76 ms** · 2 517 bloques |
| | Q6 | 0.41 ms · 145 bloques | 0.24 ms · 79 bloques |
| `compras.ix_solicitud_linea_solicitud_id` `(solicitud_id)` | Q10 | 0.92 ms | **0.55 ms** |

**Descartados tras medir:** índices trigram (`pg_trgm`) para `ILIKE '%texto%'` en insumo, código y razón social. Con
2 527 insumos y 626 proveedores el planificador sigue eligiendo el Seq Scan (Q4: 1.18 → 1.07 ms, sin usar el índice):
no aportan hoy y piden una extensión. Revisarlos si el catálogo llega a decenas de miles de filas.

### 2.4 N+1 corregidos en el código (aplicado)

| Petición (datos reales salvo donde dice) | Sentencias antes | Después | Tiempo antes → después |
|---|---:|---:|---:|
| `POST /catalogo/insumos/importar-s10` con las 284 filas del S10 real | 288 | **5** | 134 → 44 ms |
| `POST /obras/{id}/requerimiento/importar` con el S10 real | 288 | **5** | 180 → 63 ms |
| `GET /obras` (5 obras asignadas) | 12 | **2** | 23 → 7 ms |
| `POST /obras/{id}/compras` y `/solicitudes` | 1 INSERT por línea | 1 `executemany` | — |
| `GET /catalogo/insumos`, `/proveedores`, `/saldo-por-comprar`, `/compras`, `/solicitudes`, `/jornadas` | 4 a 7 | igual (ya no tenían N+1) | — |

(Tiempos en proceso, sin red; mínimos de 2 corridas.) Cómo: una sola lectura de lo que ya existe
(`WHERE codigo = ANY(:cs)` / `insumo_id = ANY(:ids)`) y los INSERT/UPDATE en un `executemany`; `GET /obras` en una
consulta con `LEFT JOIN LATERAL` sobre los periodos, con el mismo cálculo de semana que `GET /obras/{id}` (hay una
prueba que lo compara). Lo prueba `api/pruebas/test_rendimiento_consultas.py`: el número de sentencias con 5 filas es
igual al de 150 o 200 (S10, requerimiento, compra de 1 y de 30 líneas, 1 y 6 obras).

### 2.5 Tamaño de respuesta y paginación (aplicado, compatible hacia atrás)

| Respuesta (EJEMPLO salvo catálogo) | Elementos | Bytes | gzip |
|---|---:|---:|---:|
| `GET /obras/{id}/jornadas` | 20 160 | 5.6 MiB | 112 KiB |
| `GET /obras/{id}/jornadas?limite=200` | 200 | 57 KiB | 1.6 KiB |
| `GET /obras/{id}/compras` | 720 | 753 KiB | 99 KiB |
| `GET /obras/{id}/compras?limite=50` | 50 | 52 KiB | 7.7 KiB |
| `GET /catalogo/insumos` (real) | 2 527 | 517 KiB | 50 KiB |
| `GET /catalogo/insumos?limite=100` | 100 | 20 KiB | 2.5 KiB |

`api/app/paginacion.py`: `limite` (1..500; 1..3000 en catálogo) y `desde` **opcionales** en catálogo, proveedores,
compras, solicitudes, trabajadores y jornadas. **Sin parámetros la consulta y la respuesta son las de siempre**: medido,
los bytes de cada respuesta son idénticos antes y después, y la web y la app no cambian. Con `limite`, si quedan más
filas llega la cabecera `X-Siguiente-Desde` (se pide una fila de más, sin `count(*)` aparte). Una prueba comprueba que
concatenar las páginas da exactamente la lista completa. Para que la web la lea desde otro origen, el CORS del gateway
tendría que exponer esa cabecera (`expose_headers`); no se tocó el gateway. El gzip ya lo hace el gateway (rama
`fix-gateway-compresion`): la columna gzip es lo que viaja.

No se paginaron `requerimiento`, `saldo-por-comprar` y `parametros-reposicion`: están acotadas por los insumos de la obra
(284) y la web las necesita completas para ordenar por saldo.

### 2.6 Suite

739 pruebas pasan, 12 de ellas nuevas (`test_rendimiento_consultas.py`, 6 parametrizadas). Fallan 6 que **fallan igual en `main` en este equipo**: `test_almacenes`
(la base local se creó con locale C y `lower('Á')` no baja la tilde) y las 5 `test_cada_imagen_arranca_solo_con_su_codigo`
(los subprocesos de Windows no heredan Winsock). Con `PYTHONUTF8=1` pasa `test_cada_migracion_declara_su_vuelta_atras`.

---

## 3. Render: arranque en frío y propuesta

### 3.1 Medido el 01/11/2026 (18:54, hora de Lima) contra el ambiente de pruebas

| Servicio | Primera petición tras dormir | Ya despierto |
|---|---:|---:|
| sigro-gateway `/salud` | 21.6 s | 0.32 a 0.39 s |
| sigro-obras · catalogo · compras `/salud` | 21.4 s cada uno | 0.33 a 0.35 s |
| sigro-tareo · integraciones `/salud` | 31.4 a 31.5 s | 0.33 a 0.75 s |
| sigro-web (sitio estático) | — (no duerme) | 0.60 s |

Con microservicios el frío **se encadena**: la primera petición de la mañana despierta al gateway (~21 s), este llama al
servicio dueño de la ruta (~21 s más si duerme) y ese pregunta a obras `/interno/autorizar` (otros ~21 s si duerme).
Peor caso: más de un minuto para la primera pantalla, y el almacenero lo vive como «la app no funciona». Cada servicio
se duerme por su cuenta tras 15 minutos sin tráfico.

### 3.2 Reglas del plan gratuito y precios (consultados el 01/11/2026)

- Un servicio web gratuito se duerme tras **15 minutos** sin tráfico entrante y despertarlo «toma cerca de un minuto»;
  cada workspace tiene **750 horas de instancia gratuitas por mes**, y si se acaban Render **suspende todos los
  servicios gratuitos hasta el mes siguiente** ([render.com/docs/free](https://render.com/docs/free)).
- Precios ([render.com/pricing](https://render.com/pricing.md)): workspace Hobby US$ 0/mes; servicio web «menos de 1 CPU,
  512 MB» **US$ 7/mes**; 1 CPU / 2 GB US$ 25/mes; Postgres de Render desde US$ 6/mes (la base sigue en Neon, gratis).
  Los sitios estáticos son gratis.

### 3.3 Opción A · ping cada 10 minutos

Un `GET /salud` cada 10 min (menor que los 15 de Render) con un monitor externo (UptimeRobot, Better Stack) o un
`schedule` de GitHub Actions. Hay que pingear **cada servicio**, no solo el gateway (sus `/salud` no despiertan a los
demás). Las cuentas no dan:

| Qué se mantiene despierto | Horas/mes | ¿Cabe en 750? |
|---|---:|---|
| los 6 servicios, 24/7 | 6 × 720 = 4 320 | No: a los ~5 días se suspende TODO hasta el mes siguiente |
| los 6, lunes a sábado de 7:00 a 19:00 | 6 × 12 × 26 = 1 872 | No |
| gateway + obras + catálogo, lunes a viernes de 7:00 a 19:00 | 3 × 12 × 22 = 792 | No, por poco |
| gateway + obras, lunes a sábado de 7:00 a 19:00 | 2 × 12 × 26 = 624 | Sí, pero compras/tareo siguen en frío |
| los 6 solo en ventanas de demostración (p. ej. 2 h, 8 veces al mes) | 6 × 16 = 96 | Sí |

Además: el cron de GitHub Actions no garantiza la hora exacta (puede retrasarse varios minutos, y un retraso de más de 5
deja dormir al servicio) y en repositorios públicos se desactiva tras 60 días sin actividad. Mantener despierto con ping
un servicio gratuito va contra el espíritu del plan.

### 3.4 Opción B · plan pago

| Configuración | Costo mensual |
|---|---:|
| Los 6 servicios en «menos de 1 CPU, 512 MB» (US$ 7) | **US$ 42** |
| Solo la ruta crítica (gateway, obras, catálogo, compras) pagada; tareo e integraciones gratis con ping en horario | US$ 28 + 2 servicios × 312 h = 624 h gratis |

### 3.5 Recomendación

Para el curso (demostraciones al docente y pruebas del equipo): **opción A acotada** — un monitor externo que pingee los
6 `/salud` cada 10 min solo en las ventanas de demostración y de pruebas acordadas (≈ 100 a 300 h/mes, dentro de las
750), y abrir la web 3 minutos antes de demostrar. Para cuando SEVENS use el sistema en obra: **opción B, US$ 28 a
42/mes**; ningún ping cabe en el plan gratuito con 6 servicios en horario de obra. No se crearon cuentas ni se cambió
nada en Render.
