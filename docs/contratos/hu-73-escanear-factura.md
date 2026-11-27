# HU-73 · Escanear la factura electrónica al recibir la compra

> Contrato acordado **antes** de programar (acuerdo 2 de la Retrospective 2: el diagrama y el contrato
> mandan; si algo cambia al programar, primero se corrige este documento).
> Backend: Adiel · App: Fernando · Prueba cruzada: Adiel prueba la app, Fernando prueba la API.

## 1. Historia

**Como** almacenero, **quiero** escanear el código QR de la factura electrónica cuando llega el material,
**para** encontrar la compra sin escribir y saber al momento si el comprobante corresponde a lo que se compró.

Prioridad: Alta · Estimación propuesta: 8 puntos (backend 3 + app 5) · Épica E3 (compras y recepciones) ·
Depende de: HU-32 (compra), HU-35 (recepción), HU-31 (padrón SUNAT).

### Criterios de aceptación

1. La app lee el QR con la cámara del celular, **también sin señal**, sin escribir nada.
2. Con el QR leído, la app abre la recepción de la compra que coincide (proveedor + serie-número).
3. Si el comprobante **no es para SEVENS** (RUC del adquirente distinto), lo advierte antes de recibir.
4. Si el **total o el IGV** del QR no coinciden con la compra registrada, lo advierte con la diferencia en soles.
5. Si la compra ya está **recibida por completo** o **anulada**, lo dice y no ofrece recibirla.
6. Si no hay compra con ese comprobante, ofrece avisar al residente (no la registra el almacenero: CR-05).
7. Con señal, el servidor vuelve a hacer el cotejo (la app no es la única que decide).
8. El texto del QR no se guarda entero en la bitácora (solo serie-número y resultado).

## 2. El QR de la factura electrónica (SUNAT)

El QR del comprobante electrónico trae los datos separados por `|`, en este orden:

| # | Campo | Ejemplo | Regla |
|---|---|---|---|
| 1 | RUC del emisor | `20999999990` | 11 dígitos y dígito verificador (módulo 11) |
| 2 | Tipo de comprobante | `01` | `01` factura → `factura` · `03` boleta → `boleta` · `07`/`08` notas → no se reciben |
| 3 | Serie | `F001` | 4 caracteres alfanuméricos |
| 4 | Número | `00001234` | hasta 8 dígitos; se quitan los ceros a la izquierda |
| 5 | IGV | `180.00` | decimal con punto |
| 6 | Importe total | `1180.00` | decimal con punto |
| 7 | Fecha de emisión | `2026-11-20` | también se acepta `20/11/2026` |
| 8 | Tipo de documento del adquirente | `6` | `6` = RUC, `1` = DNI |
| 9 | Documento del adquirente | `20606456892` | para SEVENS debe ser su RUC |
| 10+ | Resumen / firma | (opcional) | se ignora |

**Normalización (la misma en la app y en el servidor):**
- `numero_comprobante` = `SERIE-NÚMERO` sin ceros a la izquierda en el número: `F001` + `00001234` → `F001-1234`
  (así se guarda hoy en `compras.compra`).
- Espacios alrededor de cada campo se quitan; el separador puede venir como `|`.
- Menos de 9 campos, RUC inválido, tipo desconocido o montos no numéricos → **QR no reconocido** (no se adivina).

Los casos de prueba compartidos están en [`hu-73-qr-casos.json`](hu-73-qr-casos.json): **los dos lados
(JUnit y pruebas de la app) leen el mismo archivo**, así el parser de Kotlin y el de Java no pueden
interpretar distinto el mismo QR. Los RUC de emisor del archivo son sintéticos (pasan el dígito
verificador, no se usan datos de proveedores reales).

## 3. Contrato de la API (Adiel)

### 3.1 `POST /obras/{obra_id}/compras/cotejar-qr`

Permiso: `recepcion.registrar` (almacenero, administrador). No modifica nada: solo consulta y compara.

Entrada:

```json
{ "qr": "20999999990|01|F001|00001234|180.00|1180.00|2026-11-20|6|20606456892|" }
```

Salida 200:

```json
{
  "comprobante": { "ruc_emisor": "20999999990", "tipo_comprobante": "factura", "numero_comprobante": "F001-1234",
                   "igv": "180.00", "total": "1180.00", "fecha": "2026-11-20",
                   "adquirente_tipo": "6", "adquirente_documento": "20606456892" },
  "resultado": "recibir",
  "compra": { "id": 12, "estado": "registrada", "proveedor_razon_social": "…", "total": "1180.00", "queda_por_recibir": true },
  "avisos": []
}
```

`resultado` es uno de:

| resultado | Cuándo | Qué hace la app |
|---|---|---|
| `recibir` | hay compra vigente con algo pendiente | abre la recepción de esa compra |
| `recibir_con_avisos` | coincide, pero difiere el total, el IGV o la fecha | muestra los avisos y deja continuar |
| `ya_recibida` | la compra ya se recibió por completo | lo dice; no ofrece recibir |
| `anulada` | la compra está anulada | lo dice con el motivo |
| `sin_compra` | no hay compra con ese proveedor y comprobante | ofrece avisar al residente |
| `no_es_de_sevens` | el adquirente no es el RUC de SEVENS | advierte; no busca la compra |

Errores: `422 qr_no_reconocido` (no cumple el formato de la sección 2) · `403` sin permiso · `401` sin sesión.

Avisos (texto listo para mostrar), por ejemplo:
`"El total del QR (S/ 1 180.00) difiere en S/ 118.00 del registrado en la compra (S/ 1 062.00)."`
Tolerancia: **S/ 0.01** (redondeo).

### 3.2 Cambio pequeño en `GET /obras/{obra_id}/compras`

Agregar `proveedor_ruc` y `total` a cada compra. Lo necesita la app para **cotejar sin señal** con las compras
que ya bajó (criterio 1). No cambia nada más del listado.

## 4. Lo que hace la app (Fernando)

1. **Entrada → «Escanear factura»**: pide permiso de cámara solo al tocar (si lo niega, explica y deja seguir a mano).
2. Cámara con **CameraX + ML Kit Barcode Scanning** (modelo incluido en la app: funciona sin señal; solo `FORMAT_QR_CODE`).
3. `dominio/QrFactura.kt`: parser puro (Kotlin común) con la sección 2 → `QrFactura` o `null`. Probado con el JSON compartido.
4. **Sin señal**: cotejo local contra las compras ya bajadas (`proveedor_ruc` + `numero_comprobante`, total con tolerancia
   S/ 0.01) → mismos `resultado` que el servidor. Muestra «se confirmará al volver la señal».
5. **Con señal**: llama a `cotejar-qr` y muestra lo que dice el servidor (manda el servidor).
6. Abre `PantallaEntrada` con la compra elegida; los avisos quedan visibles hasta confirmar.
7. Versión **1.2.0** (versionCode 17).

## 5. Diagrama de secuencia

`docs/diagramas/secuencias-spring/HU-73 · secuencia.puml` (con PNG). Es el contrato de quién llama a quién:
Vista → ViewModel → dominio (`QrFactura`, `CotejoQr`) → repositorio → API → `ComprasController` →
`Acceso.exigir("recepcion.registrar")` → reglas de compras → PostgreSQL (esquema compras).

## 6. Reparto y orden (para que se complementen)

| Paso | Adiel (backend, PO) | Fernando (app, SM) |
|---|---|---|
| 0 | Revisa y aprueba este contrato (PR `hu73-contrato-qr`) | Abre el PR del contrato y la HU-73 en el Backlog |
| 1 | `QrFacturaSunat.java` (parser) + JUnit con el JSON compartido | `QrFactura.kt` (parser) + pruebas con el mismo JSON |
| 2 | `proveedor_ruc` y `total` en `GET /compras` (3.2) | Cámara: CameraX + ML Kit, permiso y pantalla «Escanear» |
| 3 | `POST /compras/cotejar-qr` con los 6 resultados + pruebas de API | Cotejo sin señal (`CotejoQr.kt`) + pruebas |
| 4 | Bitácora: solo serie-número y resultado (criterio 8) | Con señal llama a `cotejar-qr`; integra con Entrada |
| 5 | **Prueba la app** con una factura real (DoD T8) | **Prueba la API** con Postman/curl (DoD T8) |
| 6 | Acepta como PO desde la app (T10) | Firma la 1.2.0 y actualiza el diagrama si algo cambió |

Puntos de unión: el JSON de casos (paso 1) y el contrato 3.1/3.2 (pasos 2–4). Mientras la API no esté
desplegada, la app prueba su cotejo con un `ApiSigro` falso que responde según la tabla de la sección 3.1.

**Ramas:** `hu73-contrato-qr` (este documento) → `hu73-backend-cotejo` (Adiel, base `backend-spring`) y
`hu73-app-escanear` (Fernando, base `main`, después de fusionar la app 1.1.4).

## 7. Definition of Done (T1–T10)

PR revisado por el otro · parser probado con los mismos casos en los dos lados · pruebas de API de los 6
resultados y del 422 · pruebas de la app del cotejo sin señal · desplegado en Render · probado con una
factura real (foto tapada en la evidencia) · aceptado por el PO desde la app.

## 8. Por qué es una mejora (ISO/IEC 25010)

- **Adecuación funcional (exactitud):** el comprobante físico se coteja con la compra registrada; un total distinto se ve en la puerta del almacén, no a fin de mes.
- **Usabilidad (protección contra errores):** no se escribe la serie ni el número a mano, con guantes y en obra.
- **Seguridad (integridad):** una factura emitida a otra empresa o ya recibida no entra al almacén de SEVENS.
- **Fiabilidad:** funciona sin señal (modelo de ML Kit en el celular) y el servidor confirma después.

## 9. Para empezar (pedido a Claude Code en cada rama)

**Adiel · `hu73-backend-cotejo` (base `backend-spring`):**
> Lee `docs/contratos/hu-73-escanear-factura.md` y `hu-73-qr-casos.json`. En el módulo compras de backend-spring crea
> `QrFacturaSunat` (parser, sección 2) y `CotejoQr` (reglas de la sección 3.1, tolerancia S/ 0.01), el endpoint
> `POST /obras/{obra_id}/compras/cotejar-qr` con `acceso.exigir("recepcion.registrar", obraId)`, y agrega
> `proveedor_ruc` y `total` a `GET /obras/{obra_id}/compras`. JUnit que lea el JSON compartido (válidos, no
> reconocidos y cotejo) y pruebas de API de los 6 resultados y del 422. La bitácora no guarda el texto del QR.
> Sin migraciones. No cambies el contrato: si hace falta, avisa para corregir primero el documento.

**Fernando · `hu73-app-escanear` (base `main`, con la 1.1.4 ya fusionada):**
> Lee `docs/contratos/hu-73-escanear-factura.md` y `hu-73-qr-casos.json`. En `android/composeApp` crea en commonMain
> `dominio/QrFactura.kt` (parser) y `dominio/CotejoQr.kt` (mismos resultados que el servidor), con pruebas que lean
> el JSON compartido. En androidMain, `LectorQr` con CameraX + ML Kit Barcode Scanning (modelo incluido, solo
> QR) y el permiso de cámara pedido al tocar. Pantalla «Escanear factura» desde Entrada, MVVM, que con señal llama a
> `cotejar-qr` y sin señal usa `CotejoQr` con las compras bajadas; abre la recepción de la compra elegida con sus avisos.
> Versión 1.2.0 (versionCode 17). Si algo del contrato no alcanza, avisa antes de cambiarlo.
