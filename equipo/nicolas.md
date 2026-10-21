# Nicolás Pachamora · Developer · Android

Construyes lo único que se usa parado en la obra, con polvo y sin señal. Si tu parte falla, el sistema entero no sirve: nadie va a volver a la oficina a registrar una salida.

Tu carpeta es `android/`. Fuera de ahí, solo con Pull Request y aviso.

## Por dónde empiezas

1. Proyecto Android con Compose y Room, y la pantalla de inicio de sesión contra la API de Henry.
2. HU-37 registro sin señal y HU-38 sincronización: la cola local y el envío con WorkManager. Es la pieza más difícil y la más importante.
3. HU-39 y HU-40, salida e ingreso de material, sobre esa cola.

## Lo que no puedes perder de vista

- Todo movimiento se guarda primero en Room y se encola; el envío lo hace WorkManager cuando vuelve la señal.
- Cada movimiento nace con un identificador único generado en el celular.
- La app **nunca** calcula el saldo: muestra lo que el servidor devolvió y marca lo pendiente de enviar.
- Se prueba en modo avión y recuperando la señal, no solo con el emulador conectado.
- Las pantallas están diseñadas: pídele a Fernando el enlace de los mockups.

## Tus historias del Sprint 1 — 5 historias, 31 puntos

| Código | Historia | Prioridad | Puntos | Depende de |
|---|---|---|:--:|---|
| HU-37 | Registrar movimientos sin conexión | Crítica | 8 | HU-15 |
| HU-38 | Sincronizar movimientos pendientes | Crítica | 5 | HU-37 |
| HU-40 | Registrar salida de material | Crítica | 8 | HU-35 |
| HU-63 | Registrar asistencia de la jornada | Crítica | 5 | HU-62 |
| HU-15 | Autorizar dispositivo de registro | Alta | 5 | HU-13 |

Los criterios de aceptación de cada una están en `docs/historias-sprint-1.md`.

En el Sprint 2 te tocan 6 historias más, 24 puntos: `docs/historias-sprint-2.md`.
