# Rendimiento de la app del almacenero · inspección medida

Rama `app-optimizacion`, 01/11/2026. Todo lo que dice este informe sale de una medición en el emulador
con `android/scripts/medir.sh`, no de mirar la pantalla. Cada mejora es un commit y se volvió a medir
después de cada uno. No cambia el diseño visible ni la lógica: la cola sin señal, `ClienteHttp`, el dominio
y las firmas de los componentes compartidos (`FondoPlano`, `Modifier.vidrio`, `BotonNegro`, `CampoVidrio`)
quedan igual.

## Resumen: antes y después

APK release firmado con la clave debug **solo para medir** (no hay keystore en el repositorio).
Medianas; entre corchetes, mínimo–máximo.

| Métrica | Antes (`main`, 5ec9b89) | Después (e6edf96) | Cambio |
|---|---|---|---|
| APK release | **22,13 MB** | **7,91 MB** | −64 % |
| APK debug | 28,28 MB | 26,22 MB | −7 % |
| dex del release (comprimido) | 13,27 MB | 1,58 MB | −88 % |
| Memoria PSS al terminar el recorrido | **72,5 MB** | **45,3 MB** | −38 % |
| Java heap / Native heap | 14,7 / 19,8 MB | 13,5 / 19,3 MB | |
| Arranque en frío, recién instalada, sin sesión (Entrar) | 637 ms [561–969] | 528 ms [483–590] | dentro del ruido¹ |
| Arranque en frío, recién instalada, con sesión (Inicio) | 482 ms [416–575] | 495 ms [446–526] | sin cambio |
| Arranque en frío con perfil aplicado, con sesión | 581 ms [499–628] | 512 ms [469–552] | dentro del ruido¹ |
| Cuadros lentos del recorrido (janky) | 51,4 % | 49,9 % (47,2 % con otra corrida) | sin cambio medible² |
| Cuadro p50 / p90 / p99 | 38 / 101 / 200 ms | 36 / 81 / 300 ms | p90 −20 % |
| `assembleRelease` | **fallaba** (lint) | compila | |

¹ El ruido del arranque en esta PC es de ±60–90 ms en la mediana (otras compilaciones corrían a la vez,
ver «Condiciones»). Ninguna mejora de arranque supera ese ruido de forma clara.
² El emulador sin ventana dibuja con GL traducido por software: casi la mitad de los cuadros es lenta en
**cualquier** versión, incluso sin desenfoque (45,6 %). Esa base no es de la app. Ver «Cuadros lentos».

## Cómo se midió

**Equipo.** Intel Core i9-14900HX (32 hilos), 32 GB RAM; durante las mediciones la CPU estaba al 65–77 %
y quedaban 1,2–3,8 GB libres porque otros agentes compilaban Gradle y Node en la misma máquina.
**Emulador** `Pixel_8` (sdk_gphone16k_x86_64, API 37, 1080 × 2400, 420 dpi),
renderer `skiagl` sobre `ro.hardware.egl=emulation`. El emulador ya estaba en marcha sin ventana (headless) y
se usó en exclusiva para estas mediciones. `dumpsys meminfo` reporta Graphics = 0 en este
emulador: la memoria de GPU no se puede medir aquí.

**Recorrido** (`android/scripts/medir.sh`, todo con `adb shell input`, siempre igual):
`pm clear` → abrir → Entrar (servidor, usuario, clave) → «el plano se dibuja» mientras el servidor tarda
2,5 s → Inicio, cuatro deslizamientos → Registrar salida, buscar «insumo», elegir el primero, dos
deslizamientos → atrás → Inicio, dos deslizamientos. Se repite 3 veces con `dumpsys gfxinfo reset` antes;
se informa la mediana de las 3.

**Servidor.** `android/scripts/servidor-medicion.py` responde lo mínimo (login con 2,5 s de demora, `/auth/yo`,
240 insumos `[marcador]`, dispositivos) para no depender de Render ni de una cuenta real. No es la API.

**Arranque.** `am force-stop` + `am start -W`, 7 a 9 veces, se toma `TotalTime`. En dos estados de ART:
*recién instalada* (`cmd package compile -m verify`: así llega el APK por enlace) y *con perfil*
(`compile --reset`, `ProfileInstallReceiver`, `compile -m speed-profile`, como `CompilationMode.Partial`
de Macrobenchmark).

**Memoria.** `dumpsys meminfo pe.sevens.sigro` 5 s después del último recorrido, en Inicio con sesión.

**Tamaño.** Desglose del zip (dex, lib, res, composeResources) con un script; `apkanalyzer` no está
instalado en esta PC.

Repetir: `python -I android/scripts/servidor-medicion.py 8765` en una terminal, instalar el APK y
`ADB=<adb> CAPTURAS=<carpeta> android/scripts/medir.sh <salida> 7 3`.

## Medición después de cada commit

| Paso (commit) | APK release | PSS | Janky (mediana de 3) | p50 / p90 / p99 | Arranque recién inst. sin / con sesión | Arranque con perfil con / sin sesión |
|---|---|---|---|---|---|---|
| 0 · antes (`main`) | 22,13 MB | 72,5 MB | 51,4 % | 38 / 101 / 200 | 637 / 482 | 581 / 563 |
| 1 · R8 (d4f9ee2) | 9,97 MB | 47,6 MB | 51,6 %³ | 42 / 101 / 250 | 486 / 477 | 556 / 517 |
| 2 · Haze a 1/3 (750093a) | 9,99 MB | 48,1 MB | 49,7 % | 38 / 85 / 250 | 482 / 463 | 559 / 518 |
| 3 · respaldo sin desenfoque (4ee639a)⁴ | 9,99 MB | 49,3 MB | 47,2 % | 34 / 77 / 250 | 571 / 458 | 483 / 517 |
| 4 · Baseline Profile (df49ee0) | 9,97 MB | 46,1 MB | 51,2 % | 38 / 81 / 250 | 546 / 540 | 499 / 512 |
| 5 · sin ABI x86 (e6edf96) | **7,91 MB** | **45,3 MB** | 49,9 % | 36 / 81 / 300 | 528 / 495 | 512 / 463 |
| diagnóstico: todo sin desenfoque (no se sube) | 9,97 MB | 43,2 MB | 45,6 % | 36 / 69 / 200 | 565 / 484 | 502 / 477 |

³ La primera corrida del paso 1 solo registró 106 cuadros (75 % lentos): coincidió con un pico de carga de
la PC; las otras dos dan 48 % y 52 %.
⁴ En el Pixel 8 con la configuración normal el paso 3 no cambia nada (capturas idénticas píxel a píxel):
sus números muestran el **ruido** entre dos corridas del mismo código: ±3 puntos de janky, ±8 ms de p90,
±90 ms de arranque.

## Hallazgos, con evidencia

### 1. `assembleRelease` no compilaba (aplicado, 3702e8b)
`lintVitalAnalyzeRelease` se caía con `IncompatibleClassChangeError` en
`NonNullableMutableLiveDataDetector` (AGP 8.7 + Kotlin 2.1). Nadie había notado que el release no salía
porque solo se compilaba debug. La app no usa LiveData: se apagó solo esa regla (`NullSafeMutableLiveData`).

### 2. Release sin R8 (aplicado, d4f9ee2) — lo que más pesa
`isMinifyEnabled = false`: el release llevaba 49 MB de dex sin comprimir, casi todo
`material-icons-extended` (miles de íconos; la app usa 34). Con R8 + `shrinkResources` el dex queda en
3,3 MB y la memoria baja 25 MB (menos clases cargadas). Reglas en `composeApp/proguard-rules.pro`:
`-dontwarn` de SLF4J/JVM que Ktor referencia; los `$serializer` de los DTO de `datos/remoto`; el
`RoomDatabase` y el `ConstructorBaseDatos` de Room KMP. kotlinx.serialization, Room, WorkManager, OkHttp,
Koin y Compose traen sus propias reglas.
**Probado ejecutando el release minificado** en el emulador: login (deserializa `SesionDto`), descarga del
catálogo (`List<InsumoDto>`), búsqueda en Room, guardar salida («Salida guardada … quedó en el celular»),
Inicio con «1 por enviar» y «Enviar ahora» → «Enviados 0. Quedan 1: se reintentará solo» (el servidor de
medición responde 404 a salidas, igual que la API hoy). Sin `FATAL` ni `SerializationException` en logcat.

### 3. Vidrio: Haze en muchas capas a la vez (aplicado en parte, 750093a y 4ee639a)
Inicio monta **7 efectos de vidrio** a la vez (chip de obra, resumen, 3 accesos, últimos registros y barra
inferior; 8 con la hoja «Más» abierta); Salida 2; Entrar 2. Cada uno desenfoca su pedazo del plano en cada
cuadro que algo se mueve encima (desplazamiento, animación).
- **Aplicado:** `inputScale = HazeInputScale.Auto`: Haze desenfoca una copia a 1/3 de resolución
  (verificado en el bytecode de Haze 1.6.10: 0,3334 sin desenfoque progresivo). p90 de 101 a 85 ms. Las
  capturas no cambian a la vista: Inicio dif. máx. 2/255, Salida 22/255 en el borde de una tarjeta,
  0 % de píxeles con diferencia > 8.
- **Aplicado:** respaldo sin desenfoque en gama baja (Android `isLowRamDevice`) y con «reducir movimiento»
  (escala de animación 0 en Android, Reduce Motion en iOS): `FondoPlano` no crea la fuente de Haze y
  `vidrio()` pinta el blanco 82/90 % que `diseno/LEEME.md` ya admite (y que Android < 12 ya usaba). El
  diagnóstico «todo sin desenfoque» mide cuánto cuesta el desenfoque en el emulador: 4 puntos de janky,
  16 ms de p90 y 5 MB de PSS.
- **No aplicado: un solo nivel de desenfoque.** El diseño tiene dos (18 dp normal, 22 dp «fuerte»).
  Con `RenderEffect` el costo depende del área y de la cantidad de capas, no del radio, y con la copia a 1/3
  la diferencia de radio es despreciable. Unificar cambia cómo se ve la barra inferior y la tarjeta de
  Entrar: lo decide quien diseña, no rendimiento. Queda propuesto.

### 4. Imágenes de fondo pesadas (no hay)
Los fondos de plano no son imágenes: se dibujan en `Canvas` con trazos (`Planos.kt`). Los únicos mapas de
bits son los dos logos (53 KB y 32 KB, PNG de 800 × 561 px), que se muestran a unos 210 dp: ≈ 480–550 px
en el Pixel 8 y ≈ 735 px en una pantalla de 560 dpi. 800 px es el tamaño justo para la densidad más alta;
achicarlos ahorraría ~0,9 MB de bitmap decodificado por logo en pantallas medianas pero se verían borrosos
en las grandes, y pasarlos a WebP ahorraría unos 40 KB sobre 7,9 MB. No se tocaron: son recursos
aprobados por el cliente y ya se cargan solo en la pantalla que los muestra (`painterResource`). Las fuentes (Inter ×5, Sora) son
372 KB: ya son los subconjuntos estáticos por peso.

### 5. Recomposiciones de más (revisado, nada que corregir)
Informe del compilador de Compose (`-Psigro.metricasCompose`, ver `composeApp/build.gradle.kts`):
Strong Skipping activo; en el detalle por función (`composables.csv`) **ninguna función con nombre es
reiniciable sin poder saltarse**: la diferencia del resumen (169 reiniciables, 91 saltables) son lambdas de
contenido, que por diseño no se saltan. Parámetros inestables: 13 de 2 613, casi todos
`Sesion`, `ResumenObra` y los ViewModel; con Strong Skipping se comparan por referencia y no cambian de
referencia mientras no cambie su estado (`resumen` sale de `remember(lista)`). Marcarlos `@Immutable`
obligaría al dominio a depender de Compose, lo que la arquitectura prohíbe (`android/LEEME.md`).
Listas: `Obras` y `Pendientes` usan `LazyColumn` con `key`; Inicio (≤ 3 filas) y los resultados de
Salida (≤ 8) son `Column` y no lo necesitan. `resumirObra` ya va en `remember(lista)`, que aquí es mejor
que `derivedStateOf` (la entrada es un `State` de un `Flow`, no un cálculo sobre otro estado de Compose).
Las lambdas de los `FondoPlano(plano = { planoX(it) })` no capturan nada: son instancias únicas.

### 6. «El plano se dibuja» no se reinicia (revisado, correcto)
La animación vive en `LaunchedEffect(Unit)` dentro de `CapaPlanoQueSeDibuja`, que solo entra en la
composición cuando `conCapa` pasa a verdadero (al pulsar «Entrar»). Una recomposición no la reinicia; solo
vuelve a empezar si el servidor rechaza y se intenta de nuevo, que es lo que pide el diseño. Detalle menor
no aplicado: cada cuadro crea un `Path` por subtrazo (~30 por cuadro durante 2,3 s); reutilizarlos
ahorraría basura pero el recorrido no mostró pausas de GC atribuibles y tocaría la animación aprobada.

### 7. Baseline Profile (aplicado, df49ee0)
Las librerías ya traían su perfil (5 KB en `assets/dexopt/baseline.prof`), pero el código de la app no
tenía ninguno: tras instalar, el perfil del celular tenía **3** entradas de `pe/sevens/sigro`. Se agregó
`src/androidMain/baselineProfiles/baseline-prof.txt` con reglas para todo el paquete, registrado en el
source set `main` (con KMP, AGP no mira `androidMain` solo) y `-keeppackagenames pe.sevens.sigro.**`
para que R8 no mude las clases a otro paquete (sin eso el comodín encontraba 33 clases de 212). Ahora el
perfil instalado tiene **854** entradas de la app (`cmd package dump-profiles`).
En el emulador x86_64 la ganancia no supera el ruido; en un celular ARM de gama baja, donde interpretar
cuesta más, es donde se espera notarla. Para generarlo midiendo (y no a mano): un módulo
`:baselineprofile` con `androidx.baselineprofile` y Macrobenchmark, una prueba `BaselineProfileRule` que
haga este mismo recorrido, y `./gradlew :composeApp:generateReleaseBaselineProfile` con un emulador de
API ≥ 33. No se hizo ahora porque agrega un módulo y un plugin nuevos a un proyecto KMP en pleno sprint.

### 8. ABI x86 de 32 bits (aplicado, e6edf96)
SQLite empaquetado de Room KMP pone un `.so` de ~2 MB por ABI. Se quitó `x86` (ningún celular la usa);
quedan `arm64-v8a`, `armeabi-v7a` (celulares de 32 bits de gama baja) y `x86_64` (emulador). −2,1 MB.

### 9. Room, Ktor y Koin (revisado, correcto)
- Room: todas las consultas son `suspend` o `Flow`, con `setQueryCoroutineContext(Dispatchers.IO)`;
  ninguna corre en el hilo principal.
- `HttpClient` y `BaseDatosSigro` son `single` en Koin (`Modulos.android.kt`): una instancia por proceso.
  Koin arranca una vez en `SigroApp.onCreate`.
- Dependencias: todas se usan; `coroutines-swing` es solo del escritorio y no entra al APK.

### 10. Cuadros lentos: qué queda y por qué
En todas las versiones ~45–50 % de los cuadros pasa de 16 ms, y en `gfxinfo` cada cuadro lento es
«Slow issue draw commands» con tiempos de GPU no disponibles (4 950 ms = sin dato). Es el costo de enviar
comandos GL al emulador sin ventana, compartiendo una CPU cargada; la versión sin ningún desenfoque sigue
en 45,6 %. Este número **no** sirve para juzgar la app; lo que sí se ve es que el desenfoque agrega unos
4 puntos y 16 ms de p90 encima de esa base. Para el porcentaje real hace falta repetir `medir.sh` en un
celular físico (idealmente uno de gama baja de la obra).

## Paridad visual

Capturas de Entrar, Inicio y Salida antes y después de cada paso (`CAPTURAS=` en `medir.sh`), comparadas
píxel a píxel sin la barra de estado (guardadas fuera del repositorio, en la carpeta temporal de la sesión
de Claude Code; se regeneran con el mismo comando):

| Pantalla | antes → final: diferencia media | píxeles con dif. > 8/255 | dif. máxima |
|---|---|---|---|
| Entrar | 0,12 / 255 | 0,00 % | 2 |
| Inicio | 0,16 / 255 | 0,00 % | 2 |
| Salida | 0,34 / 255 | 0,00 % | 22 (borde de una tarjeta de vidrio) |

Con «Quitar animaciones» activado, Inicio cambia en el 11 % de los píxeles (dif. máx. 23/255): las
tarjetas pasan al blanco de respaldo, como pide el diseño.

## Lo que no se aplicó, en una lista

| Propuesta | Por qué no |
|---|---|
| Un solo nivel de desenfoque | Cambia el diseño visible; el costo no depende del radio. Lo decide diseño. |
| Logos a WebP o más chicos | ~40 KB de 7,9 MB; achicarlos se vería borroso en 560 dpi; son recursos aprobados. |
| `@Immutable` en `Sesion`/`ResumenObra` | El dominio no puede depender de Compose; con Strong Skipping no hace falta. |
| Reutilizar los `Path` del plano que se dibuja | Ganancia no medible; tocaría la animación aprobada. |
| Baseline Profile generado con Macrobenchmark | Módulo y plugin nuevos; se dejó el perfil a mano y los pasos arriba. |
| APK por ABI (splits) | Se reparte por enlace, no por tienda: un APK por ABI confunde al almacenero. |
| Tocar la cola sin señal, `ClienteHttp` o el dominio | Fuera de alcance por pedido. |
