# Plan de trabajo para Claude Code · inicio del Sprint 2 (desde el 29/10/2026)

Para quien abra Claude Code en este repositorio. **Lee antes:** `CLAUDE.md` (reglas de dominio), `android/CLAUDE.md`,
`web/diseno/LEEME.md`, `android/diseno/LEEME.md`, `docs/decisiones-arquitectura.md` (ADR-09) y `api/LEEME.md`.

## Reglas del equipo que Claude Code también cumple

- Una rama por tarea (`ht-01-despliegue`, `web-estilo-vidrio`, …), nunca directo sobre `main`; todo entra por PR.
- **Quien prueba no es el autor:** al terminar una HU se avisa a **Henry Ticona** (pruebas con Postman y en la interfaz)
  y la acepta **Víctor Adiel Luján** (Product Owner). Claude Code no marca nada como Done por su cuenta.
- **Solo Adiel crea migraciones.** Si una tarea necesita una tabla, se deja la propuesta y se le pide.
- Al cerrar cada tarea, anotar en el registro del sprint (carpeta del curso, `Sprint 1\04 Seguimiento\…`, o el del Sprint 2
  cuando exista): una fila en la **Bitácora** (fecha, hora, quién, qué, tarea) y mover la tarjeta del **tablero** con su fecha.
  Si aparece un problema, una fila en el **Registro de incidencias**.
- Datos reales del cliente siempre; lo que falte va como `[marcador]`. `FACTILIZA_TOKEN` y claves solo en variables de entorno.
- Fernando no trabaja del viernes en la noche al sábado en la tarde.

## Tareas, en orden

### 1. HT-01 · Ambiente de pruebas (bloquea las pruebas de Henry y la app en el emulador)
Seguir `docs/despliegue-pruebas.md` (`render.yaml` ya está). Fernando crea las cuentas de Neon y Render y pasa la cadena
de conexión por variable de entorno. **Hecho cuando:** `https://sigro-gateway.onrender.com/salud` responde, la web entra con
el usuario de prueba y la importación del S10 real da 284 insumos.

### 2. Importación del S10 por el gateway (incidencia del 28/10, decisión «A» del 22/10)
Hoy la web llama directo a Integraciones. Agregar en el **servicio catálogo** `POST /api/v1/catalogo/importaciones-s10`
(multipart con el PDF) que reenvía a Integraciones con `X-Servicio-Token`; la web usa ese endpoint con su JWT y
`VITE_INTEGRACIONES_URL` deja de hacer falta. Luego activar `SERVICIO_TOKEN` en Integraciones. **Hecho cuando:** pruebas del
endpoint en verde, la web importa el PDF real por el gateway y una llamada directa a Integraciones sin token da 401.

### 3. Web en estilo vidrio
Aplicar el diseño aprobado (`web/diseno/lienzo/*.dc.html`, lienzo «SIGRO Oficina · Web») a `web/src`:
tokens en `tokens.css` (Inter, `#C2410C`, vidrio 58 %/18 px), menú lateral de vidrio con la sección activa en naranja,
fondo de plano por página, botones negros en píldora. La lógica y el `ClienteHttp` no cambian. Página por página,
empezando por Entrar y Gasto en recursos. **Hecho cuando:** `npm run build` sin errores, cada página igual a su lienzo
en 1440 y usable en 390, contraste AA, y Fernando la aprueba.

### 4. App en estilo vidrio y prueba en el emulador
Lista de `android/CLAUDE.md` («Trabajo pendiente»): capa visual de acceso e inicio (Haze para el vidrio, fondos, animación
«el plano se dibuja»), luego Materiales. Probar en el emulador de Android Studio contra el ambiente de la tarea 1
(«Servidor» en la pantalla de entrada). **Hecho cuando:** `./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug`
en verde, capturas de cada pantalla y prueba en modo avión (registrar sin señal y ver que se envía al volver).
El responsable de la app es **Nicolás Pachamora**: él revisa.

### 5. Servicios almacén y costo (solo después de que Adiel decida la propuesta 0010)
`api/docs/propuestas/LEEME.md` tiene el modelo, 16 escenarios probados y 5 decisiones abiertas. Cuando Adiel cree la
migración, implementar el servicio con el patrón de `app/servicios/` y sumar sus rutas en `app/enrutado.py`; el primer
endpoint es `POST /api/v1/obras/{id}/almacen/salidas`, el que ya usa la app.

## Prompt sugerido para empezar

> Lee CLAUDE.md y docs/plan-claude-code.md. Vamos con la tarea 1 (ambiente de pruebas). Te paso la cadena de Neon como
> variable de entorno NEON_URL. Hazlo en la rama ht-01-despliegue y al terminar anota la bitácora y el tablero.
