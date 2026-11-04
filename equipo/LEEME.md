# Equipo · qué lee cada uno para avanzar en el Sprint 2

**El Sprint 1 cerró el 28/10/2026** con su Review y su Retrospectiva: 105 de 261 tareas en Done y ninguna HU en Done.
Lo que no llegó volvió al Product Backlog con su avance y sigue en el Sprint 2 (hoja «Cierre del Sprint 1» del registro).

**Sprint 2: 29/10 al 11/11/2026.** El 11/11 todas las tareas deben estar en Done con el 100 % de la DoD; ese día es la
Review y se pone la etiqueta `sprint-2`.

| Integrante | Rol y área | Lee | Revisa estos PR (al 04/11) | Prueba estas HU |
|---|---|---|---|---|
| Víctor Adiel Luján | **Product Owner · backend** (`api/`, migraciones) | [`victor.md`](victor.md) | #25 y #30 (estas guías) | ninguna: **acepta** las 10 |
| Fernando Istaña | **Scrum Master · despliegue y web** (`render.yaml`, `web/`, `integraciones/`) | [`fernando.md`](fernando.md) | ninguno de los abiertos (es su autor); los fusiona y despliega | HU-12 |
| Henry Ticona | **Developer · web y pruebas** (`web/`, Postman, e2e) | [`henry.md`](henry.md) | #15 · #23 · #16 | HU-08 · HU-19 · HU-24 · HU-29 · HU-30 |
| Nicolás Pachamora | **Developer · app** (`android/`) | [`nicolas.md`](nicolas.md) | #20 · #21 · #22 · #29 | HU-01 · HU-03 · HU-07 · HU-25 |

#17, #18, #19 y #24 se fusionaron el 03/11, y #26, #27 y #28 el 04/11: ya no se revisan.

## Ambiente de pruebas · vigente desde el 04/11

Desde el 04/11 el ambiente de pruebas corre en **un solo servicio de Render** (ADR-10, `docs/despliegue-pruebas.md`).
`sigro-gateway` y los otros siete servicios viejos están **suspendidos** y se van a borrar.

| Para | Dirección |
|---|---|
| **Web** | **https://sigro-web.onrender.com** |
| **App** (campo «Servidor» de la pantalla de entrada) | **https://sigro-api.onrender.com** |
| **Postman** (base de la colección) | `https://sigro-api.onrender.com/api/v1`, **como máximo 1 petición cada 5 s**; si no, Cloudflare bloquea el servidor |

- Si tu app, tu colección o tus notas todavía dicen `sigro-gateway.onrender.com`, cámbialas. Un celular que ya tenía
  guardado ese servidor **no** se actualiza solo: cámbialo a mano en «Servidor». El APK nuevo trae `sigro-api` por
  defecto cuando se fusione #29.
- La primera vez del día el servicio tarda en despertar. Espera a que la web termine de cargar y **no reintentes el
  login varias veces**.
- Almacén y costo ya funcionan en el ambiente: la migración 0010 se aplicó en Neon el 03/11.
- **Tu usuario y su clave te los pasa Fernando por privado.** No los escribas en el repositorio, en el Excel, en
  Postman compartido ni en el grupo.

## Pruebas de aceptación del Sprint 2

El archivo es [`docs/pruebas/SIGRO - Pruebas de aceptación (Sprint 2).xlsx`](../docs/pruebas/SIGRO%20-%20Pruebas%20de%20aceptaci%C3%B3n%20(Sprint%202).xlsx).
Son **10 HU** que el PO aceptó el 03/11 y que falta probar en el ambiente, cada una por alguien distinto de quien la
programó (DoD T8).

1. La hoja «Cómo probar» explica el procedimiento. Cada probador tiene su hoja: Nicolás, Henry o Fernando.
2. Sigue cada condición final en orden **desde la interfaz** (DoD T9). Postman solo sirve de apoyo.
3. Anota *Cumple* o *No cumple*, la fecha y la hora. Si algo falla, escribe qué pasó y toma una captura.
4. Con eso tu tarjeta «Pruebas» pasa a Done. Avisa a Adiel: él llena la columna «Acepta el PO» de la hoja «Resumen» y
   mueve la tarjeta «Aceptación».
5. Para crear o cambiar datos usa una **obra de prueba tuya** («Obra de prueba – tu nombre»). Multifamiliar Flores es
   del cliente: solo se consulta.

## Los 3 cortes diarios del tablero

Regla del docente: el tablero se mueve **como mínimo 3 veces al día**, y cada movimiento lleva **fecha y hora**.
Se registra en [`docs/scrum/SIGRO - Tablero por cortes horarios.xlsx`](../docs/scrum/SIGRO%20-%20Tablero%20por%20cortes%20horarios.xlsx)
(hoja «Plantilla Sprint 2», 39 cortes del 30/10 al 11/11).

| Corte | Qué se hace |
|---|---|
| **08:00 · Inicio** | Cada integrante pasa a Doing las tareas que hará hoy, **solo si su HU cumple la DoR (L1–L12)**. Máximo **2 tareas en Doing** por persona. |
| **12:00 · Mediodía** | Lo terminado pasa de Doing a Done, **solo si cumple su criterio**; se anota lo que avanzó. |
| **18:00 · Cierre** | Se mueve lo terminado en la tarde y se deja listo el día siguiente. |
| **11/11 · Último día del sprint** | Todas las tareas en Done con el 100 % de la DoD. La HU pasa a Done cuando su tarea «Aceptación» cumple la DoD (T1–T10) y la acepta el PO. |

Fernando no trabaja del viernes en la noche al sábado en la tarde: esos cortes los registra otro integrante.

## Los cuatro, antes de tocar código

1. `CLAUDE.md`: las diez reglas de dominio y la Definición de Terminado.
2. `docs/historias-sprint-2.md`: las 38 historias del Sprint 2 con sus criterios. Su campo «Responsable» es del reparto
   anterior: manda el área de la tabla de arriba.
3. `docs/scrum/SIGRO - Tablero por cortes horarios.xlsx`: los cortes con hora (hoja «Plantilla Sprint 2»).
4. `docs/scrum/tablero-sprint-1.md` y `docs/scrum/SIGRO - Sprint 1 · Registro del Sprint.xlsx`: el tablero del Sprint 1,
   sus reglas y los criterios para Done por fase, que siguen vigentes.

Los archivos `arranque-adiel.md` y `arranque-nicolas.md` son del 21/10 y quedan como historia. Las guías del Sprint 1
siguen en el historial de Git.
