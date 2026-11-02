# Plan de pruebas · HT-06 (Postman + Playwright)

Qué se prueba de punta a punta, contra qué historia y criterio, cómo se corre y qué queda fuera.
Las pruebas unitarias y de integración de cada servicio siguen en `api/pruebas/` (pytest contra PostgreSQL real);
esto las complementa desde **afuera**: la API por el gateway (Postman) y la web como la usa una persona (Playwright).

## Cómo correrlo

### 1. Pila local

Hace falta un PostgreSQL 16 en el puerto 5435 (cualquier otro: `PG_PUERTO`). Con Docker:

```bash
docker run -d --name sigro-pg-qa -e POSTGRES_USER=sigro_owner -e POSTGRES_PASSWORD=sigro_owner_dev \
  -e POSTGRES_DB=sigro -p 5435:5432 postgres:16
python -m venv .venv && .venv/Scripts/pip install -r api/requirements-dev.txt          # (Linux/Mac: .venv/bin)
python -m venv .venv-int && .venv-int/Scripts/pip install -r integraciones/requirements.txt  # pins distintos: venv aparte
bash docs/pruebas/levantar-local.sh            # migra, crea un usuario por servicio, primer admin, 5 servicios + integraciones
```

El script genera en cada máquina los secretos (JWT, token de servicio, claves de base y del admin) y los deja solo en
`docs/pruebas/.local/pruebas-local.env`, que git ignora. Puertos: gateway 8000, obras 8101, catálogo 8102, compras 8103,
tareo 8104, integraciones 8105 (con `SERVICIO_TOKEN`, como en despliegue). `bash docs/pruebas/levantar-local.sh --parar` apaga todo.

### 2. Postman (API)

```bash
python docs/pruebas/postman/generar.py              # regenera colección, ambientes y la tabla de abajo desde api/docs/openapi.json
python docs/pruebas/postman/generar.py --revisar    # falla si algo quedó desfasado del contrato (útil en CI)
cd web/e2e && npm ci
set -a; . ../../docs/pruebas/.local/pruebas-local.env; set +a
npx newman run ../../docs/pruebas/postman/sigro.postman_collection.json \
  -e ../../docs/pruebas/postman/sigro-local.postman_environment.json --working-dir ../.. \
  --env-var usuario="$SIGRO_E2E_USUARIO" --env-var clave="$SIGRO_E2E_CLAVE"
```

En Postman de escritorio: importar `sigro.postman_collection.json` y uno de los ambientes
(`sigro-local` → `http://localhost:8000`, `sigro-render` → `https://sigro-gateway.onrender.com`), llenar `usuario` y
`clave` **en tu Postman** (los ambientes del repo los traen vacíos) y correr la colección completa en orden. Para la
lectura del PDF, Postman debe tener como *working directory* la raíz del repositorio (Settings → General).

- La corrida crea su propia obra «[QA] Obra Postman <corrida>», dos usuarios (almacenero y residente) asignados solo
  a ella, un proveedor con DNI y un insumo «[QA] …». Al final retira y da de baja a usuarios y proveedor (nada se
  borra, R10). La obra real solo se **lee**; en el catálogo y en proveedores queda lo «[QA]», porque son de la empresa.
- Cada corrida usa un sufijo propio (`corrida`), así se puede repetir sin choques.
- **Endpoints nuevos** (por ejemplo los de almacén que agrega HT de Henry/subagente D): al regenerar, cada ruta nueva
  del contrato recibe sola sus casos 401/403/404/422 en la carpeta «Sin caso correcto todavía», y el generador avisa
  (y `--revisar` falla) hasta que alguien escriba su caso correcto en `CASOS`.

### 3. Playwright (web)

```bash
cd web && npm ci && cd e2e && npm ci && npx playwright install chromium
set -a; . ../../docs/pruebas/.local/pruebas-local.env; set +a     # SIGRO_E2E_USUARIO y SIGRO_E2E_CLAVE
npx playwright test            # levanta la web sola en :5174 con VITE_API_URL=http://localhost:8000
npx playwright show-report     # informe HTML con trazas y capturas de lo que falló
```

Variables: `SIGRO_E2E_USUARIO`, `SIGRO_E2E_CLAVE` (obligatorias; sin ellas todo se omite con el motivo),
`SIGRO_E2E_API` (gateway, por defecto `http://localhost:8000`), `SIGRO_E2E_PUERTO` (5174), `FACTILIZA_TOKEN` (solo
como señal: sin él la prueba de SUNAT se omite; el token nunca va en el repositorio). Los archivos corren en orden y
en serie: `02-importar-s10` carga el catálogo y el requerimiento que leen `03` y `05`. Los selectores son por rol y
etiqueta accesible (lo que ve la persona), no por clases, para que sobrevivan al rediseño de estilo.

## Casos de Playwright (web)

| Archivo · caso | HU · criterio que prueba |
|---|---|
| `01-entrar` · usuario inexistente recibe el mismo mensaje que una clave errónea | HU-12 · «no se revela cuál de los dos falló» |
| `01-entrar` · entra con usuario y clave; sesión en sessionStorage, nunca localStorage | HU-12 · acceso con usuario y contraseña validados por el servidor; el token dura lo que la pestaña |
| `01-entrar` · cerrar sesión vuelve a la pantalla de ingreso | HU-12 |
| `02-importar-s10` · lee el **PDF real** (284 insumos, 0 fallidos, S/ 1 479 338.33, 237/6/18/23 por tipo), aplica al catálogo y carga el requerimiento | HU-07 · importación con el archivo real del cliente; HU-19 · requerimiento de la obra desde el S10 |
| `02-importar-s10` · repetir la importación: 0 a crear, 0 creados | HU-07 · «repetir no duplica» (el código es la clave) |
| `03-catalogo` · encuentra por código el cemento (0221000000) y el acero fy=4200 en kg (0203020004) | HU-08 · regla 8 código único; acero en kg (excepción del catálogo) |
| `03-catalogo` · alta: el servidor pone `SIGRO-nnnnn`; un nombre repetido con la misma unidad se rechaza | HU-08 · D-K1 |
| `04-proveedores` · alta con DNI: queda «Sin validar» y se encuentra por documento | HU-30 · D-C5; HU-29 · búsqueda por RUC/razón social |
| `04-proveedores` · documento repetido se rechaza | HU-30 · RUC único en todo el sistema |
| `04-proveedores` · con 11 dígitos consulta SUNAT y trae la razón social | HU-31 · **se omite sin `FACTILIZA_TOKEN`** |
| `05-saldo-por-comprar` · requerido del S10 por insumo (cemento 3 492.36 bls, acero 36 357.42 kg) | HU-24 · saldo = requerido − comprado |

## Casos de Postman (API)

Por cada endpoint del contrato: un caso correcto con sus `pm.test` y los errores que el contrato permite deducir
(**401** sin token, **403** obra fuera de alcance —regla 9: el servidor responde 403 exista o no la obra—, **422**
cuerpo inválido, **404** recurso inexistente). Además, la carpeta «90 · Reglas de dominio» prueba lo que el contrato no
dice: rol sin permiso en su propia obra, obra ajena con usuario real, clave débil, duplicados, corrección doble,
punto de reposición menor que el colchón; y «99 · Cierre» prueba que un retiro o una baja cortan el acceso al instante.
Todo error se verifica además con la forma `{codigo, mensaje}`.

<!-- casos-postman:inicio (lo escribe generar.py) -->
| # | Carpeta | Caso | Petición | HU / regla | Espera |
|---|---|---|---|---|---|
| 1 | 01 · Sesión y obra | Entrar como administrador (guarda el JWT) | `POST /auth/login` | HU-12 | 200 |
| 2 | 01 · Sesión y obra | 422 · cuerpo inválido | `POST /auth/login` | contrato | 422 |
| 3 | 01 · Sesión y obra | Quién soy | `GET /auth/yo` | HU-12 | 200 |
| 4 | 01 · Sesión y obra | 401 · sin token | `GET /auth/yo` | R9 | 401 |
| 5 | 01 · Sesión y obra | Mis obras (solo donde estoy asignado) | `GET /obras` | HU-01 | 200 |
| 6 | 01 · Sesión y obra | 401 · sin token | `GET /obras` | R9 | 401 |
| 7 | 01 · Sesión y obra | Ver la obra de la sesión | `GET /obras/{obra_id}` | HU-01 | 200 |
| 8 | 01 · Sesión y obra | 401 · sin token | `GET /obras/{obra_id}` | R9 | 401 |
| 9 | 01 · Sesión y obra | 403 · obra fuera de alcance | `GET /obras/{obra_id}` | R9 | 403 |
| 10 | 02 · Obra de prueba | Crear la obra de prueba (los periodos nacen solos) | `POST /obras` | HU-01 · HU-03 | 201 |
| 11 | 02 · Obra de prueba | 401 · sin token | `POST /obras` | R9 | 401 |
| 12 | 02 · Obra de prueba | 422 · cuerpo inválido | `POST /obras` | contrato | 422 |
| 13 | 02 · Obra de prueba | Ampliar el plazo con motivo | `POST /obras/{obra_id}/ampliar-plazo` | HU-02 | 200 |
| 14 | 02 · Obra de prueba | 401 · sin token | `POST /obras/{obra_id}/ampliar-plazo` | R9 | 401 |
| 15 | 02 · Obra de prueba | 403 · obra fuera de alcance | `POST /obras/{obra_id}/ampliar-plazo` | R9 | 403 |
| 16 | 02 · Obra de prueba | 422 · cuerpo inválido | `POST /obras/{obra_id}/ampliar-plazo` | contrato | 422 |
| 17 | 02 · Obra de prueba | Historial del plazo (no se pisa) | `GET /obras/{obra_id}/plazo-historial` | HU-02 | 200 |
| 18 | 02 · Obra de prueba | 401 · sin token | `GET /obras/{obra_id}/plazo-historial` | R9 | 401 |
| 19 | 02 · Obra de prueba | 403 · obra fuera de alcance | `GET /obras/{obra_id}/plazo-historial` | R9 | 403 |
| 20 | 02 · Obra de prueba | Periodos (semanas) de la obra | `GET /obras/{obra_id}/periodos` | HU-03 | 200 |
| 21 | 02 · Obra de prueba | 401 · sin token | `GET /obras/{obra_id}/periodos` | R9 | 401 |
| 22 | 02 · Obra de prueba | 403 · obra fuera de alcance | `GET /obras/{obra_id}/periodos` | R9 | 403 |
| 23 | 02 · Obra de prueba | Crear un almacén de frente | `POST /obras/{obra_id}/almacenes` | HU-04 | 201 |
| 24 | 02 · Obra de prueba | 401 · sin token | `POST /obras/{obra_id}/almacenes` | R9 | 401 |
| 25 | 02 · Obra de prueba | 403 · obra fuera de alcance | `POST /obras/{obra_id}/almacenes` | R9 | 403 |
| 26 | 02 · Obra de prueba | 422 · cuerpo inválido | `POST /obras/{obra_id}/almacenes` | contrato | 422 |
| 27 | 02 · Obra de prueba | Almacenes de la obra | `GET /obras/{obra_id}/almacenes` | HU-04 | 200 |
| 28 | 02 · Obra de prueba | 401 · sin token | `GET /obras/{obra_id}/almacenes` | R9 | 401 |
| 29 | 02 · Obra de prueba | 403 · obra fuera de alcance | `GET /obras/{obra_id}/almacenes` | R9 | 403 |
| 30 | 03 · Usuarios y asignaciones | Crear el usuario almacenero de prueba | `POST /usuarios` | HU-13 | 201 |
| 31 | 03 · Usuarios y asignaciones | 401 · sin token | `POST /usuarios` | R9 | 401 |
| 32 | 03 · Usuarios y asignaciones | 422 · cuerpo inválido | `POST /usuarios` | contrato | 422 |
| 33 | 03 · Usuarios y asignaciones | Crear el usuario residente de prueba | `POST /usuarios` | HU-13 | 201 |
| 34 | 03 · Usuarios y asignaciones | Listar usuarios | `GET /usuarios` | HU-13 | 200 |
| 35 | 03 · Usuarios y asignaciones | 401 · sin token | `GET /usuarios` | R9 | 401 |
| 36 | 03 · Usuarios y asignaciones | Asignar el almacenero a la obra de prueba | `POST /obras/{obra_id}/asignaciones` | HU-14 | 201 |
| 37 | 03 · Usuarios y asignaciones | 401 · sin token | `POST /obras/{obra_id}/asignaciones` | R9 | 401 |
| 38 | 03 · Usuarios y asignaciones | 403 · obra fuera de alcance | `POST /obras/{obra_id}/asignaciones` | R9 | 403 |
| 39 | 03 · Usuarios y asignaciones | 422 · cuerpo inválido | `POST /obras/{obra_id}/asignaciones` | contrato | 422 |
| 40 | 03 · Usuarios y asignaciones | Asignar el residente a la obra de prueba | `POST /obras/{obra_id}/asignaciones` | HU-14 | 201 |
| 41 | 03 · Usuarios y asignaciones | Asignaciones vigentes | `GET /obras/{obra_id}/asignaciones` | HU-14 | 200 |
| 42 | 03 · Usuarios y asignaciones | 401 · sin token | `GET /obras/{obra_id}/asignaciones` | R9 | 401 |
| 43 | 03 · Usuarios y asignaciones | 403 · obra fuera de alcance | `GET /obras/{obra_id}/asignaciones` | R9 | 403 |
| 44 | 03 · Usuarios y asignaciones | Entrar como almacenero de prueba | `POST /auth/login` | HU-12 | 200 |
| 45 | 03 · Usuarios y asignaciones | Entrar como residente de prueba | `POST /auth/login` | HU-12 | 200 |
| 46 | 03 · Usuarios y asignaciones | El almacenero cambia su clave | `POST /auth/cambiar-clave` | HU-12 | 204 |
| 47 | 03 · Usuarios y asignaciones | 401 · sin token | `POST /auth/cambiar-clave` | R9 | 401 |
| 48 | 03 · Usuarios y asignaciones | 422 · cuerpo inválido | `POST /auth/cambiar-clave` | contrato | 422 |
| 49 | 04 · Dispositivos (regla 5) | El almacenero pide autorizar su celular | `POST /obras/{obra_id}/dispositivos` | HU-15 | 201 |
| 50 | 04 · Dispositivos (regla 5) | 401 · sin token | `POST /obras/{obra_id}/dispositivos` | R9 | 401 |
| 51 | 04 · Dispositivos (regla 5) | 403 · obra fuera de alcance | `POST /obras/{obra_id}/dispositivos` | R9 | 403 |
| 52 | 04 · Dispositivos (regla 5) | 422 · cuerpo inválido | `POST /obras/{obra_id}/dispositivos` | contrato | 422 |
| 53 | 04 · Dispositivos (regla 5) | Dispositivos de la obra | `GET /obras/{obra_id}/dispositivos` | HU-15 | 200 |
| 54 | 04 · Dispositivos (regla 5) | 401 · sin token | `GET /obras/{obra_id}/dispositivos` | R9 | 401 |
| 55 | 04 · Dispositivos (regla 5) | 403 · obra fuera de alcance | `GET /obras/{obra_id}/dispositivos` | R9 | 403 |
| 56 | 04 · Dispositivos (regla 5) | El administrador lo autoriza | `POST /obras/{obra_id}/dispositivos/{dispositivo_id}/autorizar` | HU-15 | 200 |
| 57 | 04 · Dispositivos (regla 5) | 401 · sin token | `POST /obras/{obra_id}/dispositivos/{dispositivo_id}/autorizar` | R9 | 401 |
| 58 | 04 · Dispositivos (regla 5) | 403 · obra fuera de alcance | `POST /obras/{obra_id}/dispositivos/{dispositivo_id}/autorizar` | R9 | 403 |
| 59 | 04 · Dispositivos (regla 5) | 404 · no existe | `POST /obras/{obra_id}/dispositivos/{dispositivo_id}/autorizar` | R9 | 404 |
| 60 | 04 · Dispositivos (regla 5) | El administrador lo revoca con motivo | `POST /obras/{obra_id}/dispositivos/{dispositivo_id}/revocar` | HU-15 | 200 |
| 61 | 04 · Dispositivos (regla 5) | 401 · sin token | `POST /obras/{obra_id}/dispositivos/{dispositivo_id}/revocar` | R9 | 401 |
| 62 | 04 · Dispositivos (regla 5) | 403 · obra fuera de alcance | `POST /obras/{obra_id}/dispositivos/{dispositivo_id}/revocar` | R9 | 403 |
| 63 | 04 · Dispositivos (regla 5) | 422 · cuerpo inválido | `POST /obras/{obra_id}/dispositivos/{dispositivo_id}/revocar` | contrato | 422 |
| 64 | 04 · Dispositivos (regla 5) | 404 · no existe | `POST /obras/{obra_id}/dispositivos/{dispositivo_id}/revocar` | R9 | 404 |
| 65 | 05 · Notificaciones | Mis notificaciones | `GET /notificaciones` | ADR-07 | 200 o 503 |
| 66 | 05 · Notificaciones | 401 · sin token | `GET /notificaciones` | R9 | 401 |
| 67 | 05 · Notificaciones | Marcar una notificación como leída | `POST /notificaciones/{notificacion_id}/leida` | ADR-07 | 204 o 503 |
| 68 | 05 · Notificaciones | 401 · sin token | `POST /notificaciones/{notificacion_id}/leida` | R9 | 401 |
| 69 | 06 · Catálogo e importación del S10 | Leer el PDF real del S10 (no escribe) | `POST /catalogo/importaciones-s10` | HU-07 | 200 |
| 70 | 06 · Catálogo e importación del S10 | 401 · sin token | `POST /catalogo/importaciones-s10` | R9 | 401 |
| 71 | 06 · Catálogo e importación del S10 | 422 · cuerpo inválido | `POST /catalogo/importaciones-s10` | contrato | 422 |
| 72 | 06 · Catálogo e importación del S10 | Aplicar una fila real del S10 (repetir no duplica) | `POST /catalogo/insumos/importar-s10` | HU-07 | 200 |
| 73 | 06 · Catálogo e importación del S10 | 401 · sin token | `POST /catalogo/insumos/importar-s10` | R9 | 401 |
| 74 | 06 · Catálogo e importación del S10 | 422 · cuerpo inválido | `POST /catalogo/insumos/importar-s10` | contrato | 422 |
| 75 | 06 · Catálogo e importación del S10 | Registrar un insumo nuevo (nace por verificar) | `POST /catalogo/insumos` | HU-08 | 201 |
| 76 | 06 · Catálogo e importación del S10 | 401 · sin token | `POST /catalogo/insumos` | R9 | 401 |
| 77 | 06 · Catálogo e importación del S10 | 422 · cuerpo inválido | `POST /catalogo/insumos` | contrato | 422 |
| 78 | 06 · Catálogo e importación del S10 | Buscar el cemento del S10 en el catálogo | `GET /catalogo/insumos` | HU-08 | 200 |
| 79 | 06 · Catálogo e importación del S10 | 401 · sin token | `GET /catalogo/insumos` | R9 | 401 |
| 80 | 06 · Catálogo e importación del S10 | El residente verifica el insumo | `POST /catalogo/insumos/{insumo_id}/verificar` | HU-08 | 200 |
| 81 | 06 · Catálogo e importación del S10 | 401 · sin token | `POST /catalogo/insumos/{insumo_id}/verificar` | R9 | 401 |
| 82 | 06 · Catálogo e importación del S10 | 404 · no existe | `POST /catalogo/insumos/{insumo_id}/verificar` | R9 | 404 |
| 83 | 06 · Catálogo e importación del S10 | Cambiar el factor de conversión con motivo | `POST /catalogo/insumos/{insumo_id}/factor-conversion` | HU-17 | 200 |
| 84 | 06 · Catálogo e importación del S10 | 401 · sin token | `POST /catalogo/insumos/{insumo_id}/factor-conversion` | R9 | 401 |
| 85 | 06 · Catálogo e importación del S10 | 422 · cuerpo inválido | `POST /catalogo/insumos/{insumo_id}/factor-conversion` | contrato | 422 |
| 86 | 06 · Catálogo e importación del S10 | 404 · no existe | `POST /catalogo/insumos/{insumo_id}/factor-conversion` | R9 | 404 |
| 87 | 06 · Catálogo e importación del S10 | Histórico del factor (libro) | `GET /catalogo/insumos/{insumo_id}/factor-conversion/historial` | HU-17 | 200 |
| 88 | 06 · Catálogo e importación del S10 | 401 · sin token | `GET /catalogo/insumos/{insumo_id}/factor-conversion/historial` | R9 | 401 |
| 89 | 06 · Catálogo e importación del S10 | 404 · no existe | `GET /catalogo/insumos/{insumo_id}/factor-conversion/historial` | R9 | 404 |
| 90 | 07 · Proveedores | Registrar un proveedor con DNI (queda sin validar) | `POST /proveedores` | HU-30 · D-C5 | 201 |
| 91 | 07 · Proveedores | 401 · sin token | `POST /proveedores` | R9 | 401 |
| 92 | 07 · Proveedores | 422 · cuerpo inválido | `POST /proveedores` | contrato | 422 |
| 93 | 07 · Proveedores | Buscar proveedores por razón social | `GET /proveedores` | HU-29 | 200 |
| 94 | 07 · Proveedores | 401 · sin token | `GET /proveedores` | R9 | 401 |
| 95 | 07 · Proveedores | Revalidar en SUNAT (un DNI nunca se valida) | `POST /proveedores/{proveedor_id}/validar` | HU-31 | 200 |
| 96 | 07 · Proveedores | 401 · sin token | `POST /proveedores/{proveedor_id}/validar` | R9 | 401 |
| 97 | 07 · Proveedores | 404 · no existe | `POST /proveedores/{proveedor_id}/validar` | R9 | 404 |
| 98 | 08 · Requerimiento, solicitudes, compras y reposición | Cargar el requerimiento del cemento (S10) | `POST /obras/{obra_id}/requerimiento/importar` | HU-19 | 200 |
| 99 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `POST /obras/{obra_id}/requerimiento/importar` | R9 | 401 |
| 100 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `POST /obras/{obra_id}/requerimiento/importar` | R9 | 403 |
| 101 | 08 · Requerimiento, solicitudes, compras y reposición | 422 · cuerpo inválido | `POST /obras/{obra_id}/requerimiento/importar` | contrato | 422 |
| 102 | 08 · Requerimiento, solicitudes, compras y reposición | Ver el requerimiento de la obra | `GET /obras/{obra_id}/requerimiento` | HU-19 | 200 |
| 103 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `GET /obras/{obra_id}/requerimiento` | R9 | 401 |
| 104 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `GET /obras/{obra_id}/requerimiento` | R9 | 403 |
| 105 | 08 · Requerimiento, solicitudes, compras y reposición | Ajustar la cantidad con motivo | `POST /obras/{obra_id}/requerimiento/{req_id}/ajustar` | HU-19 | 200 |
| 106 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `POST /obras/{obra_id}/requerimiento/{req_id}/ajustar` | R9 | 401 |
| 107 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `POST /obras/{obra_id}/requerimiento/{req_id}/ajustar` | R9 | 403 |
| 108 | 08 · Requerimiento, solicitudes, compras y reposición | 422 · cuerpo inválido | `POST /obras/{obra_id}/requerimiento/{req_id}/ajustar` | contrato | 422 |
| 109 | 08 · Requerimiento, solicitudes, compras y reposición | 404 · no existe | `POST /obras/{obra_id}/requerimiento/{req_id}/ajustar` | R9 | 404 |
| 110 | 08 · Requerimiento, solicitudes, compras y reposición | Libro de ajustes del requerimiento | `GET /obras/{obra_id}/requerimiento/{req_id}/ajustes` | HU-19 | 200 |
| 111 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `GET /obras/{obra_id}/requerimiento/{req_id}/ajustes` | R9 | 401 |
| 112 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `GET /obras/{obra_id}/requerimiento/{req_id}/ajustes` | R9 | 403 |
| 113 | 08 · Requerimiento, solicitudes, compras y reposición | 404 · no existe | `GET /obras/{obra_id}/requerimiento/{req_id}/ajustes` | R9 | 404 |
| 114 | 08 · Requerimiento, solicitudes, compras y reposición | El residente emite una solicitud de cemento | `POST /obras/{obra_id}/solicitudes` | HU-66 (CR-04) | 201 |
| 115 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `POST /obras/{obra_id}/solicitudes` | R9 | 401 |
| 116 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `POST /obras/{obra_id}/solicitudes` | R9 | 403 |
| 117 | 08 · Requerimiento, solicitudes, compras y reposición | 422 · cuerpo inválido | `POST /obras/{obra_id}/solicitudes` | contrato | 422 |
| 118 | 08 · Requerimiento, solicitudes, compras y reposición | Solicitudes de la obra | `GET /obras/{obra_id}/solicitudes` | HU-66 | 200 |
| 119 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `GET /obras/{obra_id}/solicitudes` | R9 | 401 |
| 120 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `GET /obras/{obra_id}/solicitudes` | R9 | 403 |
| 121 | 08 · Requerimiento, solicitudes, compras y reposición | Registrar una compra de 40 bolsas (precios con IGV) | `POST /obras/{obra_id}/compras` | HU-32 | 201 |
| 122 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `POST /obras/{obra_id}/compras` | R9 | 401 |
| 123 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `POST /obras/{obra_id}/compras` | R9 | 403 |
| 124 | 08 · Requerimiento, solicitudes, compras y reposición | 422 · cuerpo inválido | `POST /obras/{obra_id}/compras` | contrato | 422 |
| 125 | 08 · Requerimiento, solicitudes, compras y reposición | La solicitud pasa a comprada en parte | `GET /obras/{obra_id}/solicitudes/{solicitud_id}` | HU-66 · D-S2 | 200 |
| 126 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `GET /obras/{obra_id}/solicitudes/{solicitud_id}` | R9 | 401 |
| 127 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `GET /obras/{obra_id}/solicitudes/{solicitud_id}` | R9 | 403 |
| 128 | 08 · Requerimiento, solicitudes, compras y reposición | 404 · no existe | `GET /obras/{obra_id}/solicitudes/{solicitud_id}` | R9 | 404 |
| 129 | 08 · Requerimiento, solicitudes, compras y reposición | Compras de la obra | `GET /obras/{obra_id}/compras` | HU-32 | 200 |
| 130 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `GET /obras/{obra_id}/compras` | R9 | 401 |
| 131 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `GET /obras/{obra_id}/compras` | R9 | 403 |
| 132 | 08 · Requerimiento, solicitudes, compras y reposición | Ver la compra | `GET /obras/{obra_id}/compras/{compra_id}` | HU-32 | 200 |
| 133 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `GET /obras/{obra_id}/compras/{compra_id}` | R9 | 401 |
| 134 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `GET /obras/{obra_id}/compras/{compra_id}` | R9 | 403 |
| 135 | 08 · Requerimiento, solicitudes, compras y reposición | 404 · no existe | `GET /obras/{obra_id}/compras/{compra_id}` | R9 | 404 |
| 136 | 08 · Requerimiento, solicitudes, compras y reposición | Saldo por comprar = requerido − comprado | `GET /obras/{obra_id}/saldo-por-comprar` | HU-24 | 200 |
| 137 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `GET /obras/{obra_id}/saldo-por-comprar` | R9 | 401 |
| 138 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `GET /obras/{obra_id}/saldo-por-comprar` | R9 | 403 |
| 139 | 08 · Requerimiento, solicitudes, compras y reposición | Anular la compra con motivo (no se borra) | `POST /obras/{obra_id}/compras/{compra_id}/anular` | HU-32 · D-C2 | 200 |
| 140 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `POST /obras/{obra_id}/compras/{compra_id}/anular` | R9 | 401 |
| 141 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `POST /obras/{obra_id}/compras/{compra_id}/anular` | R9 | 403 |
| 142 | 08 · Requerimiento, solicitudes, compras y reposición | 422 · cuerpo inválido | `POST /obras/{obra_id}/compras/{compra_id}/anular` | contrato | 422 |
| 143 | 08 · Requerimiento, solicitudes, compras y reposición | 404 · no existe | `POST /obras/{obra_id}/compras/{compra_id}/anular` | R9 | 404 |
| 144 | 08 · Requerimiento, solicitudes, compras y reposición | Anular la solicitud con motivo | `POST /obras/{obra_id}/solicitudes/{solicitud_id}/anular` | HU-66 · D-S2 | 200 |
| 145 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `POST /obras/{obra_id}/solicitudes/{solicitud_id}/anular` | R9 | 401 |
| 146 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `POST /obras/{obra_id}/solicitudes/{solicitud_id}/anular` | R9 | 403 |
| 147 | 08 · Requerimiento, solicitudes, compras y reposición | 422 · cuerpo inválido | `POST /obras/{obra_id}/solicitudes/{solicitud_id}/anular` | contrato | 422 |
| 148 | 08 · Requerimiento, solicitudes, compras y reposición | 404 · no existe | `POST /obras/{obra_id}/solicitudes/{solicitud_id}/anular` | R9 | 404 |
| 149 | 08 · Requerimiento, solicitudes, compras y reposición | Registrar stock de seguridad y punto de reposición | `POST /obras/{obra_id}/parametros-reposicion` | HU-25 | 201 |
| 150 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `POST /obras/{obra_id}/parametros-reposicion` | R9 | 401 |
| 151 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `POST /obras/{obra_id}/parametros-reposicion` | R9 | 403 |
| 152 | 08 · Requerimiento, solicitudes, compras y reposición | 422 · cuerpo inválido | `POST /obras/{obra_id}/parametros-reposicion` | contrato | 422 |
| 153 | 08 · Requerimiento, solicitudes, compras y reposición | Parámetros vigentes | `GET /obras/{obra_id}/parametros-reposicion` | HU-25 | 200 |
| 154 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `GET /obras/{obra_id}/parametros-reposicion` | R9 | 401 |
| 155 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `GET /obras/{obra_id}/parametros-reposicion` | R9 | 403 |
| 156 | 08 · Requerimiento, solicitudes, compras y reposición | Historial de parámetros del cemento | `GET /obras/{obra_id}/parametros-reposicion/{insumo_id}/historial` | HU-25 | 200 |
| 157 | 08 · Requerimiento, solicitudes, compras y reposición | 401 · sin token | `GET /obras/{obra_id}/parametros-reposicion/{insumo_id}/historial` | R9 | 401 |
| 158 | 08 · Requerimiento, solicitudes, compras y reposición | 403 · obra fuera de alcance | `GET /obras/{obra_id}/parametros-reposicion/{insumo_id}/historial` | R9 | 403 |
| 159 | 09 · Tareo | El almacenero registra un trabajador | `POST /obras/{obra_id}/trabajadores` | HU-61 | 201 |
| 160 | 09 · Tareo | 401 · sin token | `POST /obras/{obra_id}/trabajadores` | R9 | 401 |
| 161 | 09 · Tareo | 403 · obra fuera de alcance | `POST /obras/{obra_id}/trabajadores` | R9 | 403 |
| 162 | 09 · Tareo | 422 · cuerpo inválido | `POST /obras/{obra_id}/trabajadores` | contrato | 422 |
| 163 | 09 · Tareo | Trabajadores activos | `GET /obras/{obra_id}/trabajadores` | HU-61 | 200 |
| 164 | 09 · Tareo | 401 · sin token | `GET /obras/{obra_id}/trabajadores` | R9 | 401 |
| 165 | 09 · Tareo | 403 · obra fuera de alcance | `GET /obras/{obra_id}/trabajadores` | R9 | 403 |
| 166 | 09 · Tareo | Registrar la jornada de ayer con uuid_cliente | `POST /obras/{obra_id}/jornadas` | HU-62 · R3 | 201 |
| 167 | 09 · Tareo | 401 · sin token | `POST /obras/{obra_id}/jornadas` | R9 | 401 |
| 168 | 09 · Tareo | 403 · obra fuera de alcance | `POST /obras/{obra_id}/jornadas` | R9 | 403 |
| 169 | 09 · Tareo | 422 · cuerpo inválido | `POST /obras/{obra_id}/jornadas` | contrato | 422 |
| 170 | 09 · Tareo | Reenviar la misma jornada no duplica (R3) | `POST /obras/{obra_id}/jornadas` | HU-62 · R3 | 200 |
| 171 | 09 · Tareo | Jornadas de ayer | `GET /obras/{obra_id}/jornadas` | HU-62 | 200 |
| 172 | 09 · Tareo | 401 · sin token | `GET /obras/{obra_id}/jornadas` | R9 | 401 |
| 173 | 09 · Tareo | 403 · obra fuera de alcance | `GET /obras/{obra_id}/jornadas` | R9 | 403 |
| 174 | 09 · Tareo | Corregir la jornada con motivo (la original no se toca) | `POST /obras/{obra_id}/jornadas/{jornada_id}/correccion` | HU-63 · R1 | 201 |
| 175 | 09 · Tareo | 401 · sin token | `POST /obras/{obra_id}/jornadas/{jornada_id}/correccion` | R9 | 401 |
| 176 | 09 · Tareo | 403 · obra fuera de alcance | `POST /obras/{obra_id}/jornadas/{jornada_id}/correccion` | R9 | 403 |
| 177 | 09 · Tareo | 422 · cuerpo inválido | `POST /obras/{obra_id}/jornadas/{jornada_id}/correccion` | contrato | 422 |
| 178 | 09 · Tareo | 404 · no existe | `POST /obras/{obra_id}/jornadas/{jornada_id}/correccion` | R9 | 404 |
| 179 | 09 · Tareo | Horas acumuladas (cuenta la versión vigente) | `GET /obras/{obra_id}/horas-acumuladas` | HU-64 | 200 |
| 180 | 09 · Tareo | 401 · sin token | `GET /obras/{obra_id}/horas-acumuladas` | R9 | 401 |
| 181 | 09 · Tareo | 403 · obra fuera de alcance | `GET /obras/{obra_id}/horas-acumuladas` | R9 | 403 |
| 182 | 09 · Tareo | Dar de baja al trabajador con fecha (R10) | `POST /obras/{obra_id}/trabajadores/{trabajador_id}/baja` | HU-61 · R10 | 200 |
| 183 | 09 · Tareo | 401 · sin token | `POST /obras/{obra_id}/trabajadores/{trabajador_id}/baja` | R9 | 401 |
| 184 | 09 · Tareo | 403 · obra fuera de alcance | `POST /obras/{obra_id}/trabajadores/{trabajador_id}/baja` | R9 | 403 |
| 185 | 09 · Tareo | 422 · cuerpo inválido | `POST /obras/{obra_id}/trabajadores/{trabajador_id}/baja` | contrato | 422 |
| 186 | 09 · Tareo | 404 · no existe | `POST /obras/{obra_id}/trabajadores/{trabajador_id}/baja` | R9 | 404 |
| 187 | 90 · Reglas de dominio (errores) | 401 · usuario inexistente (misma respuesta que clave mala) | `POST /auth/login` | HU-12 | 401 |
| 188 | 90 · Reglas de dominio (errores) | 403 · el almacenero no ve una obra donde no está asignado (R9) | `GET /obras/{obra_id}` | R9 | 403 |
| 189 | 90 · Reglas de dominio (errores) | 403 · el almacenero no registra compras (rol) | `POST /obras/{obra_id}/compras` | R9 · permisos | 403 |
| 190 | 90 · Reglas de dominio (errores) | 403 · el administrador no emite solicitudes (D-C4) | `POST /obras/{obra_id}/solicitudes` | HU-66 · D-C4 | 403 |
| 191 | 90 · Reglas de dominio (errores) | 422 · clave débil | `POST /usuarios` | HU-13 | 422 |
| 192 | 90 · Reglas de dominio (errores) | 409 · nombre repetido con la misma unidad (HU-08) | `POST /catalogo/insumos` | HU-08 | 409 |
| 193 | 90 · Reglas de dominio (errores) | 409 · documento repetido | `POST /proveedores` | HU-30 | 409 |
| 194 | 90 · Reglas de dominio (errores) | 409 · corregir dos veces la misma versión | `POST /obras/{obra_id}/jornadas/{jornada_id}/correccion` | HU-63 | 409 |
| 195 | 90 · Reglas de dominio (errores) | 422 · punto de reposición menor que el stock de seguridad | `POST /obras/{obra_id}/parametros-reposicion` | HU-25 | 422 |
| 196 | 99 · Cierre | Retirar al residente de la obra de prueba | `POST /obras/{obra_id}/asignaciones/{asignacion_id}/retiro` | HU-14 | 200 |
| 197 | 99 · Cierre | 401 · sin token | `POST /obras/{obra_id}/asignaciones/{asignacion_id}/retiro` | R9 | 401 |
| 198 | 99 · Cierre | 403 · obra fuera de alcance | `POST /obras/{obra_id}/asignaciones/{asignacion_id}/retiro` | R9 | 403 |
| 199 | 99 · Cierre | 404 · no existe | `POST /obras/{obra_id}/asignaciones/{asignacion_id}/retiro` | R9 | 404 |
| 200 | 99 · Cierre | Retirado: el residente pierde el acceso al instante | `GET /obras/{obra_id}/requerimiento` | HU-14 · R9 | 403 |
| 201 | 99 · Cierre | Dar de baja al proveedor de prueba | `POST /proveedores/{proveedor_id}/baja` | HU-30 · R10 | 200 |
| 202 | 99 · Cierre | 401 · sin token | `POST /proveedores/{proveedor_id}/baja` | R9 | 401 |
| 203 | 99 · Cierre | 404 · no existe | `POST /proveedores/{proveedor_id}/baja` | R9 | 404 |
| 204 | 99 · Cierre | Dar de baja al almacenero de prueba | `POST /usuarios/{usuario_id}/baja` | HU-13 · R10 | 200 |
| 205 | 99 · Cierre | 401 · sin token | `POST /usuarios/{usuario_id}/baja` | R9 | 401 |
| 206 | 99 · Cierre | 404 · no existe | `POST /usuarios/{usuario_id}/baja` | R9 | 404 |
| 207 | 99 · Cierre | Dar de baja al residente de prueba | `POST /usuarios/{usuario_id}/baja` | HU-13 · R10 | 200 |
| 208 | 99 · Cierre | De baja: su token deja de servir al instante | `GET /auth/yo` | HU-13 · R9 | 401 |
| 209 | 99 · Cierre | 409 · retirar dos veces | `POST /obras/{obra_id}/asignaciones/{asignacion_id}/retiro` | HU-14 | 409 |
<!-- casos-postman:fin -->

## Resultados de esta entrega (01/11/2026)

Pila local: PostgreSQL 16.10 en :5435 (nativo: Docker Desktop no arrancó en la máquina), migraciones 0001→0009,
un usuario de base por servicio, gateway + obras + catálogo + compras + tareo + integraciones con `SERVICIO_TOKEN`.

| Qué | Resultado |
|---|---|
| Postman · colección completa (209 peticiones) por newman, 3 corridas seguidas sobre la misma base | **208 corridas, 778 aserciones, 0 fallas**; 1 omitida a propósito (marcar notificación leída: no hay micro de notificaciones) · ~70 s |
| Playwright · 11 casos contra la web de `main` | **10 pasan, 0 fallan, 1 omitido** (SUNAT: sin `FACTILIZA_TOKEN`) · ~40 s |
| Playwright · los mismos 11 contra la web del PR #15 (rediseño de estilos) | **10 pasan, 0 fallan, 1 omitido**: los selectores sobreviven al rediseño |
| Lectura del PDF real (`POST /catalogo/importaciones-s10` → Integraciones) | **284 insumos, 0 fallidos**, 6/237/18/23 por tipo, S/ 1 479 338.33 |

Lo que hubo que corregir en la primera corrida fueron errores de las pruebas, no del producto: los valores reales
de `estado` del proveedor (`sin_validar`, `de_baja`), un caso que tomaba la obra antes de tenerla y un enlace del menú
que coincidía con «Parámetros de reposición». El único comportamiento del producto que no calza es I-5.

## Qué NO cubre

- **Almacén y costo** (movimientos, saldo, kárdex, costo promedio, idempotencia de `movimiento`, dispositivo
  autorizado al registrar): todavía no tienen endpoint en `main`. Al regenerar la colección se agregan sus errores.
- **App Android/iOS** y el modo avión (DoD): fuera de Postman y Playwright.
- **Notificaciones**: sin el microservicio y el relevo corriendo, `GET /notificaciones` responde 503 y el caso lo acepta
  con su código; marcar como leída se omite si no hay ninguna.
- **SUNAT/RENIEC reales**: sin `FACTILIZA_TOKEN` no se consultan (la prueba web se omite; en la API se usa DNI, que
  nunca se consulta). La revalidación de un RUC real no está cubierta.
- **Bloqueo a los 5 intentos** (HU-12): no se prueba a propósito, bloquearía al admin 15 min en el ambiente compartido.
- **Compras, solicitudes, parámetros y tareo desde la web**: solo por API en esta entrega.
- **Concurrencia** (dos dispositivos a la vez, regla 5): la cubre `api/pruebas/` con hilos reales.

## Incidencias encontradas

| # | Dónde | Qué pasa | Efecto |
|---|---|---|---|
| I-1 | `web/src/api/sunat.ts` | La consulta de RUC llama **directo** a `http://localhost:8001` (fijo en el código), no por el gateway | En Render la web nunca consulta SUNAT (apunta al localhost de quien la abre); localmente exige Integraciones en 8001 sin `SERVICIO_TOKEN`. Es el mismo hallazgo que ya se resolvió para el S10 (decisión «A»): falta hacerlo para el RUC |
| I-2 | `api/docs/openapi.json` | Las rutas declaran `security: HTTPBearer` pero el contrato no define `components.securitySchemes` | Importadores (Postman, generadores de clientes) no saben que es un Bearer; el generador lo resuelve poniendo la cabecera a mano |
| I-3 | `CLAUDE.md` | Dice que los reportes del S10 son de fecha **09/01/2026**; el PDF de la Relación de insumos dice **01/01/2026** (y así lo lee Integraciones) | Solo documentación; conviene confirmarlo con el cliente |
| I-4 | Entorno | Docker Desktop no arrancó («unable to start», la VM de WSL quedó trabada) | Se corrió con un PostgreSQL 16 nativo en :5435; `levantar-local.sh` sirve con cualquier PostgreSQL |
| I-5 | `GET /obras/{id}/parametros-reposicion/{insumo_id}/historial` | Con un insumo que no existe responde **200 `[]`**; las demás rutas con id (factor, ajustes, compras…) responden 404 `no_existe` | Inconsistencia menor: la pantalla no distingue «sin historial» de «insumo equivocado». Se excluyó del 404 genérico (`SIN_404`) hasta que se decida |
| O-1 | Web · Reposición | «Saldo por comprar» muestra las cantidades redondeadas a enteros (3 492.36 bls → «3,492 bls») | Observación, no incidencia: el servidor guarda los decimales; la prueba acepta ambas formas |
