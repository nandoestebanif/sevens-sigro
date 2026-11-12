# App del almacenero · Android e iOS (HT-05)

Una sola base de código en **Kotlin Multiplatform** con **Compose Multiplatform**: la misma interfaz,
la misma lógica y la misma base local corren en Android y en iOS. Responsable: **Nicolás**.
Base armada el 28/10 para que Nicolás construya encima las HU de la app (HU-12, 15, 35, 37, 38, 40, 43, 62, 63).

| Pieza | Qué se usa | Dónde |
|---|---|---|
| Interfaz | Compose Multiplatform 1.8 · Material 3 | `commonMain/ui` |
| Inyección de dependencias | **Koin** 4 | `commonMain/di` |
| API | **Ktor** 3 (OkHttp en Android, Darwin en iOS) + plugin Auth (bearer) | `commonMain/datos/remoto` |
| Base local | **Room KMP** 2.7 + SQLite empaquetado | `commonMain/datos/local` |
| Envío en segundo plano | WorkManager (Android) · tarea de fondo de iOS (pendiente) | `androidMain` / `iosMain` |
| Pruebas | kotlin.test + Ktor MockEngine, sin base ni servidor | `commonTest` |

## Capas (Clean Architecture)

```
ui/pantallas  ──►  dominio (modelos + interfaces de repositorio + validación)  ◄──  datos (Ktor, Room)
     ViewModel            no conoce Ktor, Room ni Compose                      implementa las interfaces
```

- `dominio/` no depende de ninguna librería: si mañana cambia Ktor o Room, no se toca.
- Las pantallas reciben un **ViewModel**; el ViewModel recibe **interfaces** del dominio (Koin las inyecta).
- Lo único que cambia por plataforma (`expect/actual`): motor HTTP, ruta del archivo de la base,
  programador de sincronización, hora y botón atrás. Es poco a propósito.

```
composeApp/src/
  commonMain/   ← casi todo el código: dominio, datos, ui, di
  androidMain/  ← MainActivity, WorkManager, motor OkHttp, manifiesto
  iosMain/      ← MainViewController, motor Darwin, ruta de la base, iniciarKoin()
  desktopMain/  ← solo para ver la app en la PC y generar capturas (no es producto)
  commonTest/   ← pruebas de HU-37 y HU-38
iosApp/iosApp/  ← entrada en Swift (iOSApp.swift, ContentView.swift)
docs/capturas/  ← el diseño de cada pantalla, claro y oscuro
```

## Lo que ya hace

| Pantalla | HU | Qué hace |
|---|---|---|
| Entrar | HU-12 | Usuario y clave contra `POST /auth/login`. El token queda **cifrado** en el celular (ver «Sesión y token»). Servidor configurable |
| Elegir obra | regla 9 | Solo las obras donde la persona está asignada; con una sola, entra directo |
| Inicio | — | Franja «N por enviar / todo enviado», accesos grandes, catálogo guardado para buscar sin señal |
| Registrar salida | HU-40, HU-37 | Elige el **almacén** (con uno solo, automático), busca el insumo **sin señal**, cantidad en su unidad de compra, motivo, quién retira, frente opcional. Guarda en el celular con `uuid_cliente` y la envía a `POST /obras/{id}/almacen/salidas` |
| Entrada de compra | HU-35, HU-36, HU-37 | Compras de la obra, lo **pendiente** de cada línea según el servidor y el almacén de destino. «Llegó todo» o «llegó una parte»; se guarda con `uuid_cliente` y viaja por la misma cola a `POST /obras/{id}/almacen/recepciones`. **HU-36:** antes de guardar, con señal, pregunta si se pasa lo requerido en el S10; si se pasa, avisa con las cifras del servidor y pide el motivo (o «recibir solo hasta lo requerido»). Sin señal guarda igual y lo dice |
| Mis registros | HU-37, HU-38 | Cada registro con su estado (por enviar · enviado · rechazado). Un rechazo muestra el **`mensaje` del servidor** tal cual (422, y 404 con código como `almacen_no_existe`). Al tocarlo, el detalle (insumo, cantidad, motivo, quién retira, estado) y, si se envió, «registró: <nombre> · <hora del servidor en Lima>», guardado en Room al recibir el 201/200. «Enviar ahora» |
| Stock | HU-43 | `GET /obras/{id}/almacen/stock`: por almacén y consolidado, con el **corte** del servidor. Sin señal, la última respuesta guardada con «actualizado a las HH:MM». Nunca calcula |
| Ingreso sin compra | HU-41 | Origen, insumo, cantidad, costo sin IGV y almacén; «otro» y «saldo de otra obra» exigen su detalle. Va por la cola a `POST /obras/{id}/almacen/ingresos` |
| Mi celular | HU-15 | Código del dispositivo generado en la primera instalación y botón para pedir autorización (`POST /obras/{id}/dispositivos`). Si el usuario no puede listar dispositivos (403), no dice «Sin solicitar»; un 409 al pedir = «Ya solicitado (pendiente o autorizado)» |
| Traslado, conteo, cierre | HU-42, 46, 47 | Se guardan en el celular y **todavía no se envían**: la API ya los recibe, falta alinear el cuerpo de cada uno en la app |

Reglas que el código ya respeta (y prueba): la app **no calcula saldo** (regla 2), cada registro nace con su
**uuid** y un reenvío lleva el mismo (regla 3), un rechazo **no se pierde** y queda con su motivo (HU-38),
sin señal **no se insiste** con el resto del lote, cerrar sesión **no borra** la cola.

## Lo que la app usa de la API

La API ya recibe salidas, recepciones y stock (servicio almacén, migración 0010). La app usa:

| Para qué | Endpoint | Notas |
|---|---|---|
| Entrar | `POST /auth/login` | Sin token. Un 401 aquí es «clave incorrecta» |
| Almacenes de la obra | `GET /obras/{id}/almacenes` | Solo los `activo`. Con uno, se elige solo; el último elegido se recuerda por obra y sirve sin señal |
| Salida | `POST /obras/{id}/almacen/salidas` | `almacen_id` (nulo solo si la obra tiene uno), `unidad` = unidad de compra (el servidor convierte con el factor), `dispositivo` |
| Compras por recibir | `GET /obras/{id}/compras?estado=registrada` | |
| Pendiente de una compra | `GET /obras/{id}/almacen/compras/{compra_id}/recepcion` | Se une a las líneas de la compra por orden (ambas vienen por `compra_detalle.id`); si no calzan, no se adivina |
| Entrada de compra | `POST /obras/{id}/almacen/recepciones` | Cuerpo exacto de `RecepcionIn`: `lineas: null` = todo lo pendiente; si no, `[{insumo_id, cantidad}]`; `motivo_exceso` solo si se pasa lo requerido (HU-36) |
| Aviso de lo requerido (HU-36) | `POST /obras/{id}/almacen/recepciones/verificacion` | Mismo cuerpo que la recepción (`compra_id`, `lineas`); no guarda nada. Un 404 sin código (servidor viejo) no bloquea |
| Ingreso sin compra | `POST /obras/{id}/almacen/ingresos` | Cuerpo exacto de `IngresoIn`; `origen` con los valores de `ORIGENES`; `costo_unitario` por unidad del S10 |
| Stock | `GET /obras/{id}/almacen/stock` | Se guarda la última respuesta (tabla `stock_guardado`) para verla sin señal |
| Estado del celular | `GET /obras/{id}/dispositivos` | Solo el administrador puede listar: el almacenero recibe 403. **Pedido a Adiel:** `GET /obras/{id}/dispositivos/mio` |

Respuestas: 201/200 (reenvío con el mismo `uuid_cliente`) ⇒ enviado · 422/403/409 o 404 **con** `codigo` ⇒
rechazado con su `mensaje` · 404 sin `codigo` (servidor anterior) ⇒ sigue en cola · sin señal o 5xx ⇒ sigue en
cola y se corta el lote · 401 ⇒ se cierra la sesión (ver abajo). Sin red o con 502/503/504 la pantalla dice
«El servidor no responde. Revisa la dirección o inténtalo en un minuto.». Un celular que tenía guardado
`sigro-gateway.onrender.com` (retirado, ADR-10) pasa solo a `https://sigro-api.onrender.com`.

La API devuelve `registrado_por` como id de usuario: la app pone el nombre de la sesión cuando es el mismo
usuario, y «usuario N.º …» si no. **Pedido a Adiel:** `registrado_por_nombre` en la respuesta (la app ya lo lee si llega).

## Sesión y token

- El token **no** vive en la base local: va al `AlmacenSeguro` de cada plataforma (`plataforma/AlmacenSeguro.kt`).
  Android: clave AES-256/GCM en el **Android Keystore** + SharedPreferences con el texto cifrado.
  iOS: **Keychain** (tras el primer desbloqueo, solo en este dispositivo). Escritorio: archivo sin cifrar (no es producto).
- Al actualizar la app, el token que una versión anterior dejó en la tabla `ajuste` se **muda** al almacén
  cifrado y se borra de `ajuste` (`BovedaSesion`).
- El token lo pone el **plugin Auth de Ktor** (`bearer`) en cada petición; ningún repositorio lo toca.
- Un **401** cierra la sesión y la app vuelve a «Entrar» **sin borrar la cola**: lo registrado se envía al volver a ingresar.

## Cómo correrla

**Android (Windows, Linux o Mac).** Abre la carpeta `android/` en Android Studio (Ladybug o más nuevo, con el
plugin *Kotlin Multiplatform*). Corre la configuración `composeApp` en un emulador o en tu celular.
En el emulador la API local se ve como `http://10.0.2.2:8000` (ya viene por defecto); en un celular real
usa la IP de tu PC o la URL del ambiente de pruebas (botón «Servidor» en la pantalla de entrada).

```bash
./gradlew :composeApp:assembleDebug        # APK en composeApp/build/outputs/apk/debug/
./gradlew :composeApp:testDebugUnitTest    # pruebas de commonTest (85: colas, sesión, almacén, entrada, stock, ingreso)
./gradlew :composeApp:run                  # la app en una ventana de la PC, contra la API local
./gradlew :composeApp:capturas             # PNG de cada pantalla en composeApp/build/capturas
```

**iOS (solo en una Mac con Xcode 16+).** Apple solo permite compilar para iPhone desde macOS: en Windows se
programa todo (el código es el mismo) pero el iPhone se prueba en una Mac.

1. En Xcode: *File → New → Project → iOS App* (SwiftUI), nombre `iosApp`, guárdalo en `android/iosApp/`.
2. Reemplaza los dos archivos Swift por los de `iosApp/iosApp/` y agrega al `Info.plist` lo de
   `Info.plist.fragmento.md`.
3. En *Build Phases* agrega, antes de *Compile Sources*, un *Run Script*:
   `cd "$SRCROOT/.." && ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`
4. En *Build Settings → Framework Search Paths*:
   `$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
5. Corre en el simulador. El simulador ve la API de la Mac como `http://localhost:8000`.

Alternativa más corta: generar un proyecto con el asistente oficial de Kotlin Multiplatform (kmp.jetbrains.com)
y copiar su carpeta `iosApp/`; los archivos Swift de aquí lo reemplazan.

## Lo que queda para que HT-05 cumpla su DoD

- [ ] Instalarla en un **celular real** e iniciar sesión contra la API (ambiente de pruebas, HT-01).
- [ ] Probar en **modo avión**: registrar, cerrar la app, recuperar señal y ver que se envía (WorkManager).
- [ ] Generar el APK de pruebas desde el repositorio (ya compila: `assembleDebug`).
- [ ] iOS: compilar en una Mac y anotar el resultado en la bitácora (incluye `AlmacenSeguroIos`, el Keychain, escrito sin Mac).
- [ ] iOS: registrar la tarea de fondo (`BGAppRefreshTask`) que llame a `RepositorioSalidas.sincronizar()` y `RepositorioRegistros.sincronizar()`.

## Reglas para quien siga

- Las pantallas nuevas usan los componentes de `ui/componentes` y el tema de `ui/tema` (botones de 60 dp,
  estado con ícono + palabra + color, contraste AA en claro y oscuro).
- Nada de lógica en las pantallas: va en el ViewModel o en el dominio, y se prueba en `commonTest`.
- El token vive cifrado en el `AlmacenSeguro` de la plataforma; la URL, en la base local. **Ningún secreto en el código**.
- Si la app necesita un endpoint o un campo, se le pide a Adiel: la app no inventa contratos en silencio
  (los que usa están arriba, en «Lo que la app usa de la API»).
