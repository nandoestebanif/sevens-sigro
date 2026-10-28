# App del almacenero · Android e iOS (HT-05)

Una sola base de código en **Kotlin Multiplatform** con **Compose Multiplatform**: la misma interfaz,
la misma lógica y la misma base local corren en Android y en iOS. Responsable: **Nicolás**.
Base armada el 28/10 para que Nicolás construya encima las HU de la app (HU-12, 15, 35, 37, 38, 40, 43, 62, 63).

| Pieza | Qué se usa | Dónde |
|---|---|---|
| Interfaz | Compose Multiplatform 1.8 · Material 3 | `commonMain/ui` |
| Inyección de dependencias | **Koin** 4 | `commonMain/di` |
| API | **Ktor** 3 (OkHttp en Android, Darwin en iOS) | `commonMain/datos/remoto` |
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
| Entrar | HU-12 | Usuario y clave contra `POST /auth/login`; la sesión queda en el celular. Servidor configurable |
| Elegir obra | regla 9 | Solo las obras donde la persona está asignada; con una sola, entra directo |
| Inicio | — | Franja «N por enviar / todo enviado», accesos grandes, catálogo guardado para buscar sin señal |
| Registrar salida | HU-40, HU-37 | Busca el insumo **sin señal**, cantidad con su unidad, motivo, quién retira, frente opcional. Guarda en el celular con `uuid_cliente` y hora del dispositivo |
| Mis registros | HU-37, HU-38 | Cada salida con su estado (por enviar · enviado · rechazado + motivo). «Enviar ahora» |
| Mi celular | HU-15 | Código del dispositivo generado en la primera instalación y botón para pedir autorización (`POST /obras/{id}/dispositivos`) |

Reglas que el código ya respeta (y prueba): la app **no calcula saldo** (regla 2), cada salida nace con su
**uuid** y un reenvío lleva el mismo (regla 3), un rechazo **no se pierde** y queda con su motivo (HU-38),
sin señal **no se insiste** con el resto del lote, cerrar sesión **no borra** la cola.

## Lo que falta de la API (pedido a Adiel)

La app ya envía; el servidor todavía no recibe. Mientras responda 404, la salida queda «por enviar» con una
nota y **no se pierde nada**. Contrato propuesto:

```http
POST /api/v1/obras/{obra_id}/almacen/salidas
Authorization: Bearer <token>
{
  "uuid_cliente": "c0a8…",          // clave de idempotencia: repetido ⇒ 200 con el resultado original
  "insumo_id": 123,
  "cantidad": "12.5",                // texto decimal, como el resto de la API
  "motivo": "Vaciado de losa",
  "quien_retira": "…",
  "frente": null,                    // opcional
  "hora_dispositivo_ms": 1761670000000,
  "dispositivo": "sigro-android-…"   // el servidor rechaza si no está autorizado en la obra (regla 5)
}
→ 201 / 200 (reenvío)   · 422 {codigo, mensaje} por regla de negocio (stock insuficiente, dispositivo no autorizado)
```

Falta también: elegir el **almacén** de la obra (HU-40 lo exige; hoy la API tiene `/obras/{id}/almacenes`
pero la salida aún no lo pide) y el **saldo** para la pantalla de stock (HU-43).

## Cómo correrla

**Android (Windows, Linux o Mac).** Abre la carpeta `android/` en Android Studio (Ladybug o más nuevo, con el
plugin *Kotlin Multiplatform*). Corre la configuración `composeApp` en un emulador o en tu celular.
En el emulador la API local se ve como `http://10.0.2.2:8000` (ya viene por defecto); en un celular real
usa la IP de tu PC o la URL del ambiente de pruebas (botón «Servidor» en la pantalla de entrada).

```bash
./gradlew :composeApp:assembleDebug        # APK en composeApp/build/outputs/apk/debug/
./gradlew :composeApp:testDebugUnitTest    # pruebas de HU-37 y HU-38 (12 pruebas)
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
- [ ] iOS: compilar en una Mac y anotar el resultado en la bitácora.
- [ ] iOS: registrar la tarea de fondo (`BGAppRefreshTask`) que llame a `RepositorioSalidas.sincronizar()`.

## Reglas para quien siga

- Las pantallas nuevas usan los componentes de `ui/componentes` y el tema de `ui/tema` (botones de 60 dp,
  estado con ícono + palabra + color, contraste AA en claro y oscuro).
- Nada de lógica en las pantallas: va en el ViewModel o en el dominio, y se prueba en `commonTest`.
- El token y la URL viven en la base local del celular; **ningún secreto en el código**.
- Si la app necesita un endpoint o un campo, se le pide a Adiel: la app no inventa contratos en silencio
  (el de salidas está arriba, propuesto y pendiente de su visto bueno).
