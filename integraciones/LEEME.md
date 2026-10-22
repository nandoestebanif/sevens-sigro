# Integraciones · Fernando Istaña

Microservicio FastAPI para lo que depende de terceros: la consulta de RUC en
SUNAT y de DNI en RENIEC (ambas vía Factiliza, con el mismo token) y, después,
la importación de los reportes del S10.
**No escribe en la base de datos de la API** (ADR-01): devuelve datos
normalizados y la API decide qué guardar.

| Endpoint | Para qué | Historia |
|---|---|---|
| `GET /ruc/{ruc}` | razón social + estado SUNAT al crear un proveedor | HU-31 |
| `GET /dni/{dni}` | nombre RENIEC al registrar un trabajador | HU-62 |
| `POST /s10/relacion-insumos` | lee la Relación de insumos del S10 (PDF) y devuelve los insumos normalizados con su acción crear/actualizar | HU-07 |
| `GET /salud` | ¿el token está configurado? (solo un sí/no, nunca el token) | — |

## Importar el S10 (HU-07)

La forma más fácil de probarlo: con el micro corriendo, abrir
`http://localhost:8001/docs`, desplegar `POST /s10/relacion-insumos`, «Try it
out» y subir el PDF real de la Relación de insumos. Con el archivo de la obra
debe responder: **284 leídos** (6 mano de obra, 237 materiales, 18 equipos,
23 subcontratos), parcial total **S/ 1 479 338.33** y 0 fallidos.

- El micro **no escribe en la base** (ADR-01): devuelve los insumos con su
  `accion` y la API los aplica usando el código como clave — por eso repetir
  la importación **no duplica** y jamás toca los saldos (el catálogo no es el
  almacén).
- `codigos_existentes` (opcional): los códigos que la API ya tiene, para que
  esos vuelvan marcados «actualizar» en lugar de «crear».
- Una fila que no se entiende va a `fallidos` con su motivo: se informa, no se
  descarta en silencio. Un archivo que no es la Relación responde 422 con un
  mensaje claro.
- Requiere `pip install -r requirements.txt` de nuevo (se sumaron `pdfplumber`
  y `python-multipart`).

## Arrancar

```bash
cd integraciones
pip install -r requirements.txt
echo FACTILIZA_TOKEN=tu_token_aqui > .env    # crea el .env y pega tu token
uvicorn main:app --reload --port 8001 --env-file .env
```

Probar: `http://localhost:8001/salud`, `http://localhost:8001/ruc/20606456892`
y `http://localhost:8001/dni/` con un DNI de 8 dígitos (el tuyo, por ejemplo).
Cada DNI consultado también se cachea 24 h, así que repetir la prueba no gasta
el plan.

Si Factiliza/RENIEC no responde, el registro del trabajador **no se bloquea**:
el nombre se escribe a mano, igual que el proveedor queda «sin validar».

## El token

Se crea una cuenta gratis en https://app.factiliza.com y el token se copia del
panel. Va solo en el archivo `.env` (ignorado por git) o en una variable de
entorno. **Nunca en el código, nunca en el frontend, nunca en el repositorio**:
quien tenga el token puede gastar nuestras 100 consultas del mes.

## Por qué el frontend no llama a Factiliza directo

1. El token quedaría expuesto en el navegador de cualquiera que abra la web.
2. Aquí se cachea: un RUC consultado hoy no se vuelve a consultar hoy, y las
   100 consultas gratis del mes alcanzan (se consulta una vez por proveedor
   nuevo, no una por compra).
3. Si Factiliza cambia su formato, se toca este servicio y nada más.

Si Factiliza no responde o se acabó el plan, este servicio devuelve 503 y la
API registra al proveedor como «sin validar»: la compra no se bloquea.
