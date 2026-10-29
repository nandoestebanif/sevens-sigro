# android/ · app del almacenero (Kotlin Multiplatform: Android + iOS)

Lee primero `../CLAUDE.md` (reglas de dominio) y `LEEME.md` (cómo compilar). Este archivo es lo propio de la app.

## Dónde está todo

| Qué | Dónde |
|---|---|
| Código común (dominio, datos, UI, Koin) | `composeApp/src/commonMain/kotlin/pe/sevens/sigro/` |
| Android (WorkManager, OkHttp, MainActivity) | `composeApp/src/androidMain/` |
| iOS (Darwin, MainViewController) · Swift | `composeApp/src/iosMain/` · `iosApp/` |
| Escritorio (ver la app en la PC, capturas) | `composeApp/src/desktopMain/` |
| Pruebas | `composeApp/src/commonTest/` |
| **Diseño aprobado** (pantallas, tokens, fondos, animaciones) | `diseno/LEEME.md` y `diseno/lienzo/*.dc.html` |

## Trabajo pendiente, en orden

1. **Capa visual nueva** según `diseno/LEEME.md`: tema Inter + estilo B + vidrio, fondo de plano por pantalla,
   pantallas «Entrar», «Elegir obra» (perfil + buscador + estado del celular por obra), «Almacén» (Inicio con «Más»)
   y «Mi celular». Animación «el plano se dibuja» al entrar, mientras responde el servidor.
2. Pantallas de Materiales del lienzo (página 2) con su lógica: entrada de compra (HU-35/36), ingreso sin compra (HU-41),
   detalle y reverso (HU-45), stock (HU-43), conteo (HU-46), traslado (HU-42), cierre (HU-47).
3. Catálogo, Herramientas y Mano de obra cuando se diseñen.

## Reglas al programar aquí

- La app **no calcula saldo**; muestra el del servidor con su hora (regla 2).
- Todo registro nace con `uuid_cliente` y se guarda en Room antes de enviarse; un rechazo queda con su motivo.
- Lógica en ViewModel o dominio, con prueba en `commonTest`; la pantalla solo pinta.
- Si falta un endpoint, se le pide a Adiel (backend). El contrato propuesto de salidas está en `LEEME.md`.
- Nada de datos inventados de la obra: lo que falte va como `[marcador]`.
- Antes de pedir PR: `./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug` en verde.
- Rama por HU (`hu-40-salida-app`), PR aprobado por otro integrante. El responsable de la app es Nicolás.
