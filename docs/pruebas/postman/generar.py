"""Genera la colección de Postman de SIGRO a partir del contrato del gateway (api/docs/openapi.json).

    python docs/pruebas/postman/generar.py            # escribe sigro.postman_collection.json y los dos ambientes
    python docs/pruebas/postman/generar.py --revisar  # falla si la colección del repo no coincide con el contrato

Qué genera, por cada endpoint del contrato:
  * un caso correcto (CASOS, abajo) con sus `pm.test` y las variables que captura para los siguientes;
  * casos de error derivados del propio contrato:
      - 401 sin token, si la ruta exige `HTTPBearer`;
      - 403 obra fuera de alcance, si la ruta lleva `{obra_id}` (una obra donde la persona no está asignada:
        el servidor responde 403 exista o no la obra, regla 9);
      - 422 cuerpo inválido, si la ruta recibe un cuerpo (se manda vacío);
      - 404 recurso inexistente, si la ruta lleva otro identificador además de la obra.
    Un endpoint nuevo en openapi.json recibe sus casos de error sin tocar nada; si además no tiene caso correcto
    en CASOS, el script lo avisa (y `--revisar` falla) para que alguien lo escriba.

Toda la corrida trabaja en una obra de prueba que la propia colección crea ("[QA] Obra Postman <corrida>"), con
dos usuarios que también crea (almacenero y residente) y que da de baja al final: la obra real no se toca, salvo
la lectura. Los datos de insumo son filas reales de la Relación de insumos del S10 (presupuesto 2701006,
subpresupuesto 002): el cemento portland tipo I, código 0221000000.

Ningún secreto vive aquí: usuario y clave son variables vacías del ambiente que cada uno llena en su Postman.
"""
from __future__ import annotations

import argparse
import json
import sys
import uuid
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[3]
CONTRATO = RAIZ / "api" / "docs" / "openapi.json"
SALIDA = Path(__file__).resolve().parent
PDF_S10 = "insumos-cliente/5.3. Relacion de insumos.pdf"     # relativo a la raíz del repo (newman --working-dir)

# Fila real del S10 (Relación de insumos, 2701006 / 002): la usan importación, requerimiento, compra y solicitud.
CEMENTO = {"codigo": "0221000000", "descripcion": "CEMENTO PORTLAND TIPO I (42.50 KG.)", "unidad": "bls",
           "tipo": "material", "cantidad": 3492.36, "precio": 36.0}
INEXISTENTE = 999999          # id que no existe; también la "obra ajena" para el 403

ADMIN, ALM, RES = "token", "token_almacenero", "token_residente"
REGLAS = "90 · Reglas de dominio (errores)"


def js(*lineas: str) -> list[str]:
    return list(lineas)


def estado(codigo: int | list[int]) -> str:
    if isinstance(codigo, list):
        return f"pm.test('responde {' o '.join(map(str, codigo))}', () => pm.expect(pm.response.code).to.be.oneOf({codigo}));"
    return f"pm.test('responde {codigo}', () => pm.response.to.have.status({codigo}));"


def lista() -> str:
    return "pm.test('devuelve una lista', () => pm.expect(pm.response.json()).to.be.an('array'));"


def guarda(var: str, expr: str) -> str:
    return f"if (pm.response.code < 300) {{ pm.collectionVariables.set('{var}', {expr}); }}"


# --------------------------------------------------------------------------------------------- casos correctos
# Orden = orden de ejecución. Cada caso: metodo, ruta (como en el contrato), nombre, hu, token, y opcionales
# path (valores de los parámetros), query, body (dict o "multipart"), espera, pruebas (JS), antes (JS), carpeta.
CASOS: list = [   # un texto suelto abre una carpeta; lo que sigue va en ella
    "01 · Sesión y obra",
    dict(metodo="POST", ruta="/api/v1/auth/login", nombre="Entrar como administrador (guarda el JWT)", hu="HU-12",
         token=None, body={"usuario": "{{usuario}}", "clave": "{{clave}}"}, espera=200,
         antes=js(
             "// una corrida = un sufijo único, para que repetir la colección no choque con lo creado antes",
             "pm.collectionVariables.set('corrida', String(Date.now()).slice(-9));",
             "const hoy = new Date(); const dia = (n) => new Date(hoy.getTime() + n * 864e5).toISOString().slice(0, 10);",
             "pm.collectionVariables.set('hoy', dia(0)); pm.collectionVariables.set('ayer', dia(-1));",
             "pm.collectionVariables.set('hace_7', dia(-7)); pm.collectionVariables.set('hace_14', dia(-14));",
             "pm.collectionVariables.set('en_60', dia(60)); pm.collectionVariables.set('en_90', dia(90));",
             "pm.collectionVariables.set('dni', String(10000000 + Math.floor(Math.random() * 89999999)));",
             "pm.collectionVariables.set('dni_trab', String(10000000 + Math.floor(Math.random() * 89999999)));",
             "pm.collectionVariables.set('uuid_jornada', pm.variables.replaceIn('{{$guid}}'));",
             "// clave de los usuarios de prueba: generada en cada corrida, nunca escrita en el repositorio",
             "pm.collectionVariables.set('clave_qa', 'Qa' + Math.random().toString(36).slice(2, 10) + 'X7z');",
             "pm.collectionVariables.set('clave_qa_nueva', 'Nq' + Math.random().toString(36).slice(2, 10) + 'Y8w');",
             "if (!pm.variables.get('usuario') || !pm.variables.get('clave')) {",
             "  throw new Error('Llena las variables «usuario» y «clave» del ambiente (no se guardan en el repo).');",
             "}",
         ),
         pruebas=js(
             "const s = pm.response.json();",
             "pm.test('trae token bearer y vencimiento', () => { pm.expect(s.token).to.be.a('string').and.not.empty; pm.expect(s.expira_en).to.be.above(0); });",
             "pm.collectionVariables.set('token', s.token);",
             "const adm = (s.obras || []).find(o => o.rol === 'administrador') || (s.obras || [])[0];",
             "pm.test('tiene al menos una obra asignada', () => pm.expect(adm).to.be.an('object'));",
             "if (adm) pm.collectionVariables.set('obra_id', adm.id);",
         )),
    dict(metodo="GET", ruta="/api/v1/auth/yo", nombre="Quién soy", hu="HU-12", token=ADMIN, espera=200,
         pruebas=js("pm.test('devuelve el usuario y sus obras', () => { const r = pm.response.json(); pm.expect(r.usuario.id).to.be.a('number'); pm.expect(r.obras).to.be.an('array'); });")),
    dict(metodo="GET", ruta="/api/v1/obras", nombre="Mis obras (solo donde estoy asignado)", hu="HU-01", token=ADMIN, espera=200,
         pruebas=js(lista(), "pm.test('incluye la obra de la sesión', () => pm.expect(pm.response.json().map(o => o.id)).to.include(Number(pm.collectionVariables.get('obra_id'))));")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}", nombre="Ver la obra de la sesión", hu="HU-01", token=ADMIN, espera=200,
         pruebas=js("pm.test('es la obra pedida', () => pm.expect(pm.response.json().id).to.eql(Number(pm.collectionVariables.get('obra_id'))));")),
    "02 · Obra de prueba",
    dict(metodo="POST", ruta="/api/v1/obras", nombre="Crear la obra de prueba (los periodos nacen solos)", hu="HU-01 · HU-03", token=ADMIN,
         body={"nombre": "[QA] Obra Postman {{corrida}}", "fecha_inicio": "{{hace_14}}", "fecha_fin": "{{en_60}}", "dia_corte": 7},
         espera=201,
         pruebas=js("const o = pm.response.json();",
                    "pm.test('nace con periodos y quien la crea queda asignado', () => { pm.expect(o.semanas_totales).to.be.above(0); pm.expect(o.rol).to.be.a('string'); });",
                    guarda("obra_qa_id", "o.id"))),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/ampliar-plazo", nombre="Ampliar el plazo con motivo", hu="HU-02", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, body={"nueva_fecha_fin": "{{en_90}}", "motivo": "[QA] ampliación de prueba Postman"},
         espera=200, pruebas=js("pm.test('la fecha de fin es la nueva', () => pm.expect(pm.response.json().fecha_fin).to.eql(pm.collectionVariables.get('en_90')));")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/plazo-historial", nombre="Historial del plazo (no se pisa)", hu="HU-02", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, espera=200,
         pruebas=js(lista(), "pm.test('queda la ampliación con su motivo y autor', () => { const h = pm.response.json()[0]; pm.expect(h.motivo).to.include('[QA]'); pm.expect(h.autor).to.be.a('string'); });")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/periodos", nombre="Periodos (semanas) de la obra", hu="HU-03", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, espera=200,
         pruebas=js(lista(), "pm.test('están numerados desde 1', () => pm.expect(pm.response.json()[0].numero).to.eql(1));")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/almacenes", nombre="Crear un almacén de frente", hu="HU-04", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, body={"nombre": "[QA] Frente {{corrida}}", "tipo": "frente"}, espera=201,
         pruebas=js("pm.test('nace activo', () => pm.expect(pm.response.json().estado).to.be.a('string'));", guarda("almacen_id", "pm.response.json().id"))),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/almacenes", nombre="Almacenes de la obra", hu="HU-04", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, espera=200,
         pruebas=js(lista(), "pm.test('incluye el creado', () => pm.expect(pm.response.json().map(a => a.id)).to.include(Number(pm.collectionVariables.get('almacen_id'))));")),
    "03 · Usuarios y asignaciones",
    dict(metodo="POST", ruta="/api/v1/usuarios", nombre="Crear el usuario almacenero de prueba", hu="HU-13", token=ADMIN,
         body={"nombre": "[QA] Almacenero {{corrida}}", "usuario": "qa_alm_{{corrida}}", "clave": "{{clave_qa}}"}, espera=201,
         pruebas=js("pm.test('nace activo', () => pm.expect(pm.response.json().estado).to.eql('activo'));",
                    "pm.test('no devuelve la clave ni su hash', () => { const u = pm.response.json(); pm.expect(u).to.not.have.property('clave'); pm.expect(u).to.not.have.property('hash_clave'); });",
                    guarda("usuario_alm_id", "pm.response.json().id"))),
    dict(metodo="POST", ruta="/api/v1/usuarios", nombre="Crear el usuario residente de prueba", hu="HU-13", token=ADMIN,
         body={"nombre": "[QA] Residente {{corrida}}", "usuario": "qa_res_{{corrida}}", "clave": "{{clave_qa}}"}, espera=201,
         pruebas=js(guarda("usuario_res_id", "pm.response.json().id")), solo_ok=True),
    dict(metodo="GET", ruta="/api/v1/usuarios", nombre="Listar usuarios", hu="HU-13", token=ADMIN, espera=200,
         pruebas=js(lista(), "pm.test('incluye al creado', () => pm.expect(pm.response.json().map(u => u.id)).to.include(Number(pm.collectionVariables.get('usuario_alm_id'))));")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/asignaciones", nombre="Asignar el almacenero a la obra de prueba", hu="HU-14", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, body={"usuario_id": "{{usuario_alm_id}}", "rol": "almacenero"}, crudo=True, espera=201,
         pruebas=js("pm.test('rol almacenero vigente', () => { const a = pm.response.json(); pm.expect(a.rol).to.eql('almacenero'); pm.expect(a.fecha_retiro).to.be.null; });",
                    guarda("asignacion_alm_id", "pm.response.json().id"))),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/asignaciones", nombre="Asignar el residente a la obra de prueba", hu="HU-14", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, body={"usuario_id": "{{usuario_res_id}}", "rol": "residente"}, crudo=True, espera=201,
         pruebas=js(guarda("asignacion_res_id", "pm.response.json().id")), solo_ok=True),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/asignaciones", nombre="Asignaciones vigentes", hu="HU-14", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, query={"vigentes": "true"}, espera=200,
         pruebas=js(lista(), "pm.test('almacenero y residente están asignados', () => { const roles = pm.response.json().map(a => a.rol); pm.expect(roles).to.include('almacenero'); pm.expect(roles).to.include('residente'); });")),
    dict(metodo="POST", ruta="/api/v1/auth/login", nombre="Entrar como almacenero de prueba", hu="HU-12", token=None,
         body={"usuario": "qa_alm_{{corrida}}", "clave": "{{clave_qa}}"}, espera=200, solo_ok=True,
         pruebas=js("pm.collectionVariables.set('token_almacenero', pm.response.json().token);",
                    "pm.test('ve solo la obra de prueba', () => pm.expect(pm.response.json().obras.map(o => o.id)).to.eql([Number(pm.collectionVariables.get('obra_qa_id'))]));")),
    dict(metodo="POST", ruta="/api/v1/auth/login", nombre="Entrar como residente de prueba", hu="HU-12", token=None,
         body={"usuario": "qa_res_{{corrida}}", "clave": "{{clave_qa}}"}, espera=200, solo_ok=True,
         pruebas=js("pm.collectionVariables.set('token_residente', pm.response.json().token);")),
    dict(metodo="POST", ruta="/api/v1/auth/cambiar-clave", nombre="El almacenero cambia su clave", hu="HU-12", token=ALM,
         body={"clave_actual": "{{clave_qa}}", "clave_nueva": "{{clave_qa_nueva}}"}, espera=204),
    "04 · Dispositivos (regla 5)",
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/dispositivos", nombre="El almacenero pide autorizar su celular", hu="HU-15", token=ALM,
         path={"obra_id": "{{obra_qa_id}}"}, body={"identificador": "qa-dispositivo-{{corrida}}"}, espera=201,
         pruebas=js("pm.test('queda pendiente', () => pm.expect(pm.response.json().estado).to.eql('pendiente'));", guarda("dispositivo_id", "pm.response.json().id"))),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/dispositivos", nombre="Dispositivos de la obra", hu="HU-15", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, espera=200, pruebas=js(lista())),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/dispositivos/{dispositivo_id}/autorizar", nombre="El administrador lo autoriza", hu="HU-15", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}", "dispositivo_id": "{{dispositivo_id}}"}, espera=200,
         pruebas=js("pm.test('queda activo', () => pm.expect(pm.response.json().estado).to.eql('activo'));")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/dispositivos/{dispositivo_id}/revocar", nombre="El administrador lo revoca con motivo", hu="HU-15", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}", "dispositivo_id": "{{dispositivo_id}}"}, body={"motivo": "[QA] celular de prueba Postman"}, espera=200,
         pruebas=js("pm.test('queda revocado con su motivo', () => { const d = pm.response.json(); pm.expect(d.estado).to.eql('revocado'); pm.expect(d.motivo_revocacion).to.include('[QA]'); });")),
    "05 · Notificaciones",
    dict(metodo="GET", ruta="/api/v1/notificaciones", nombre="Mis notificaciones", hu="ADR-07", token=ALM, espera=[200, 503],
         pruebas=js("if (pm.response.code === 503) { pm.test('sin el micro de notificaciones responde 503 con su código', () => pm.expect(pm.response.json().codigo).to.eql('notificaciones_no_disponible')); }",
                    "else { pm.test('devuelve una lista', () => pm.expect(pm.response.json()).to.be.an('array'));",
                    "  const n = pm.response.json()[0]; if (n) pm.collectionVariables.set('notificacion_id', n.id); }")),
    dict(metodo="POST", ruta="/api/v1/notificaciones/{notificacion_id}/leida", nombre="Marcar una notificación como leída", hu="ADR-07", token=ALM,
         path={"notificacion_id": "{{notificacion_id}}"}, espera=[204, 503],
         antes=js("if (!pm.collectionVariables.get('notificacion_id')) {",
                  "  console.log('No hay notificaciones (el micro de notificaciones o el relevo no corren): se omite.');",
                  "  pm.execution.skipRequest();",
                  "}")),
    "06 · Catálogo e importación del S10",
    dict(metodo="POST", ruta="/api/v1/catalogo/importaciones-s10", nombre="Leer el PDF real del S10 (no escribe)", hu="HU-07", token=ADMIN,
         body="multipart", espera=200,
         pruebas=js("const r = pm.response.json();",
                    "pm.test('lee los 284 insumos de la Relación del S10 sin fallidos', () => { pm.expect(r.resumen.leidos).to.eql(284); pm.expect(r.resumen.fallidos).to.eql(0); });",
                    "pm.test('por tipo: 6 mano de obra, 237 materiales, 18 equipos, 23 subcontratos', () => pm.expect(r.resumen.por_tipo).to.include({ mano_obra: 6, material: 237, equipo: 18, subcontrato: 23 }));",
                    "pm.test('parcial total S/ 1 479 338.33', () => pm.expect(Number(r.resumen.parcial_total)).to.be.closeTo(1479338.33, 0.01));",
                    "pm.test('trae el acero corrugado como un solo insumo en kg', () => { const a = r.insumos.find(i => i.codigo === '0203020004'); pm.expect(a.unidad).to.eql('kg'); });")),
    dict(metodo="POST", ruta="/api/v1/catalogo/insumos/importar-s10", nombre="Aplicar una fila real del S10 (repetir no duplica)", hu="HU-07", token=ADMIN,
         body={"insumos": [{k: CEMENTO[k] for k in ("codigo", "descripcion", "unidad", "tipo")}]}, espera=200,
         pruebas=js("const r = pm.response.json();",
                    "pm.test('la fila queda creada, actualizada o sin cambios: nunca duplicada', () => pm.expect(r.creados + r.actualizados + r.sin_cambios).to.eql(1));")),
    dict(metodo="POST", ruta="/api/v1/catalogo/insumos", nombre="Registrar un insumo nuevo (nace por verificar)", hu="HU-08", token=ALM,
         body={"descripcion": "[QA] Insumo Postman {{corrida}}", "unidad": "und", "tipo": "material"}, espera=201,
         pruebas=js("const i = pm.response.json();",
                    "pm.test('el servidor genera el código y nace sin verificar', () => { pm.expect(i.codigo).to.match(/^SIGRO-\\d+$/); pm.expect(i.verificado).to.eql(false); });",
                    guarda("insumo_id", "i.id"))),
    dict(metodo="GET", ruta="/api/v1/catalogo/insumos", nombre="Buscar el cemento del S10 en el catálogo", hu="HU-08", token=ADMIN,
         query={"q": "CEMENTO PORTLAND TIPO I"}, espera=200,
         pruebas=js(lista(), f"const c = pm.response.json().find(i => i.codigo === '{CEMENTO['codigo']}');",
                    "pm.test('el código del S10 está en el catálogo', () => pm.expect(c).to.be.an('object'));",
                    "if (c) pm.collectionVariables.set('insumo_cemento_id', c.id);")),
    dict(metodo="POST", ruta="/api/v1/catalogo/insumos/{insumo_id}/verificar", nombre="El residente verifica el insumo", hu="HU-08", token=RES,
         path={"insumo_id": "{{insumo_id}}"}, espera=200,
         pruebas=js("pm.test('queda verificado', () => pm.expect(pm.response.json().verificado).to.eql(true));")),
    dict(metodo="POST", ruta="/api/v1/catalogo/insumos/{insumo_id}/factor-conversion", nombre="Cambiar el factor de conversión con motivo", hu="HU-17", token=RES,
         path={"insumo_id": "{{insumo_id}}"}, body={"factor": "2.5", "motivo": "[QA] factor de prueba Postman"}, espera=200,
         pruebas=js("pm.test('el factor es el nuevo', () => pm.expect(Number(pm.response.json().factor_conversion)).to.eql(2.5));")),
    dict(metodo="GET", ruta="/api/v1/catalogo/insumos/{insumo_id}/factor-conversion/historial", nombre="Histórico del factor (libro)", hu="HU-17", token=ADMIN,
         path={"insumo_id": "{{insumo_id}}"}, espera=200,
         pruebas=js(lista(), "pm.test('guarda antes, después, motivo y autor', () => { const h = pm.response.json()[0]; pm.expect(Number(h.factor_nuevo)).to.eql(2.5); pm.expect(h.motivo).to.include('[QA]'); pm.expect(h.cambiado_por).to.exist; });")),
    "07 · Proveedores",
    dict(metodo="POST", ruta="/api/v1/proveedores", nombre="Registrar un proveedor con DNI (queda sin validar)", hu="HU-30 · D-C5", token=ADMIN,
         body={"ruc": "{{dni}}", "razon_social": "[QA] Proveedor Postman {{corrida}}"}, espera=201,
         pruebas=js("const p = pm.response.json();",
                    "pm.test('nace activo', () => pm.expect(p.estado).to.eql('activo'));",
                    guarda("proveedor_id", "p.id"))),
    dict(metodo="GET", ruta="/api/v1/proveedores", nombre="Buscar proveedores por razón social", hu="HU-29", token=ADMIN,
         query={"q": "[QA] Proveedor Postman {{corrida}}"}, espera=200,
         pruebas=js(lista(), "pm.test('encuentra al creado', () => pm.expect(pm.response.json().map(p => p.id)).to.include(Number(pm.collectionVariables.get('proveedor_id'))));")),
    dict(metodo="POST", ruta="/api/v1/proveedores/{proveedor_id}/validar", nombre="Revalidar en SUNAT (un DNI nunca se valida)", hu="HU-31", token=ADMIN,
         path={"proveedor_id": "{{proveedor_id}}"}, espera=200,
         pruebas=js("pm.test('sigue siendo el mismo proveedor', () => pm.expect(pm.response.json().id).to.eql(Number(pm.collectionVariables.get('proveedor_id'))));")),
    "08 · Requerimiento, solicitudes, compras y reposición",
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/requerimiento/importar", nombre="Cargar el requerimiento del cemento (S10)", hu="HU-19", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, body={"filas": [{"insumo_codigo": CEMENTO["codigo"], "cantidad": CEMENTO["cantidad"], "precio": CEMENTO["precio"]}]},
         espera=200, pruebas=js("pm.test('crea la fila sin fallidos', () => { const r = pm.response.json(); pm.expect(r.creados).to.eql(1); pm.expect(r.fallidos).to.be.empty; });")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/requerimiento", nombre="Ver el requerimiento de la obra", hu="HU-19", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, espera=200,
         pruebas=js(lista(), "const r = pm.response.json()[0];",
                    f"pm.test('cantidad y precio planificados del S10', () => {{ pm.expect(Number(r.cantidad_planificada)).to.eql({CEMENTO['cantidad']}); pm.expect(Number(r.precio_planificado)).to.eql({CEMENTO['precio']}); }});",
                    "if (r) pm.collectionVariables.set('req_id', r.id);")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/requerimiento/{req_id}/ajustar", nombre="Ajustar la cantidad con motivo", hu="HU-19", token=RES,
         path={"obra_id": "{{obra_qa_id}}", "req_id": "{{req_id}}"}, body={"cantidad": "3500", "motivo": "[QA] ajuste de prueba Postman"}, espera=200,
         pruebas=js("pm.test('la cantidad planificada es la nueva', () => pm.expect(Number(pm.response.json().cantidad_planificada)).to.eql(3500));")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/requerimiento/{req_id}/ajustes", nombre="Libro de ajustes del requerimiento", hu="HU-19", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}", "req_id": "{{req_id}}"}, espera=200,
         pruebas=js(lista(), f"pm.test('guarda antes y después', () => {{ const a = pm.response.json()[0]; pm.expect(Number(a.cantidad_anterior)).to.eql({CEMENTO['cantidad']}); pm.expect(Number(a.cantidad_nueva)).to.eql(3500); }});")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/solicitudes", nombre="El residente emite una solicitud de cemento", hu="HU-66 (CR-04)", token=RES,
         path={"obra_id": "{{obra_qa_id}}"}, body={"lineas": [{"insumo_codigo": CEMENTO["codigo"], "cantidad": "100", "unidad_compra": "bls"}]}, espera=201,
         pruebas=js("const s = pm.response.json();",
                    "pm.test('nace solicitada con correlativo', () => { pm.expect(s.estado).to.eql('solicitada'); pm.expect(s.numero).to.exist; });",
                    guarda("solicitud_id", "s.id"))),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/solicitudes", nombre="Solicitudes de la obra", hu="HU-66", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, espera=200, pruebas=js(lista())),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/compras", nombre="Registrar una compra de 40 bolsas (precios con IGV)", hu="HU-32", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"},
         body={"proveedor_id": "{{proveedor_id}}", "tipo_comprobante": "factura", "numero_comprobante": "F001-{{corrida}}",
               "fecha": "{{ayer}}", "solicitud_id": "{{solicitud_id}}",
               "lineas": [{"insumo_codigo": CEMENTO["codigo"], "cantidad": "40", "unidad_compra": "bls", "precio_unitario": "29.50"}]},
         crudo=True, espera=201,
         pruebas=js("const c = pm.response.json();",
                    "pm.test('el servidor calcula base + IGV = total (40 × 29.50 = 1180.00)', () => { pm.expect(Number(c.totales.total)).to.eql(1180); pm.expect(Number(c.totales.base) + Number(c.totales.igv)).to.be.closeTo(1180, 0.001); });",
                    guarda("compra_id", "c.id"))),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/solicitudes/{solicitud_id}", nombre="La solicitud pasa a comprada en parte", hu="HU-66 · D-S2", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}", "solicitud_id": "{{solicitud_id}}"}, espera=200,
         pruebas=js("pm.test('estado derivado de las compras: comprada_en_parte', () => pm.expect(pm.response.json().estado).to.eql('comprada_en_parte'));")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/compras", nombre="Compras de la obra", hu="HU-32", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, espera=200, pruebas=js(lista())),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/compras/{compra_id}", nombre="Ver la compra", hu="HU-32", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}", "compra_id": "{{compra_id}}"}, espera=200,
         pruebas=js("pm.test('trae sus líneas', () => pm.expect(pm.response.json().lineas).to.have.lengthOf(1));")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/saldo-por-comprar", nombre="Saldo por comprar = requerido − comprado", hu="HU-24", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, espera=200,
         pruebas=js(lista(), f"const s = pm.response.json().find(x => x.insumo_codigo === '{CEMENTO['codigo']}');",
                    "pm.test('cemento: 3500 requerido, 40 comprado, saldo 3460', () => { pm.expect(Number(s.requerido)).to.eql(3500); pm.expect(Number(s.comprado)).to.eql(40); pm.expect(Number(s.saldo)).to.eql(3460); });")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/compras/{compra_id}/anular", nombre="Anular la compra con motivo (no se borra)", hu="HU-32 · D-C2", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}", "compra_id": "{{compra_id}}"}, body={"motivo": "[QA] anulación de prueba Postman"}, espera=200,
         pruebas=js("pm.test('queda anulada con su motivo', () => { const c = pm.response.json(); pm.expect(c.estado).to.eql('anulada'); pm.expect(c.motivo_anulacion).to.include('[QA]'); });")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/solicitudes/{solicitud_id}/anular", nombre="Anular la solicitud con motivo", hu="HU-66 · D-S2", token=RES,
         path={"obra_id": "{{obra_qa_id}}", "solicitud_id": "{{solicitud_id}}"}, body={"motivo": "[QA] anulación de prueba Postman"}, espera=200,
         pruebas=js("pm.test('queda anulada', () => pm.expect(pm.response.json().estado).to.eql('anulada'));")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/parametros-reposicion", nombre="Registrar stock de seguridad y punto de reposición", hu="HU-25", token=RES,
         path={"obra_id": "{{obra_qa_id}}"}, body={"insumo_codigo": CEMENTO["codigo"], "stock_seguridad": "50", "punto_reposicion": "120"}, espera=201,
         pruebas=js("pm.test('queda vigente con quien lo registró', () => { const p = pm.response.json(); pm.expect(Number(p.punto_reposicion)).to.eql(120); pm.expect(p.registrado_por).to.exist; });")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/parametros-reposicion", nombre="Parámetros vigentes", hu="HU-25", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, espera=200, pruebas=js(lista())),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/parametros-reposicion/{insumo_id}/historial", nombre="Historial de parámetros del cemento", hu="HU-25", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}", "insumo_id": "{{insumo_cemento_id}}"}, espera=200,
         pruebas=js(lista(), "pm.test('tiene al menos la vigencia registrada', () => pm.expect(pm.response.json().length).to.be.at.least(1));")),
    "09 · Tareo",
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/trabajadores", nombre="El almacenero registra un trabajador", hu="HU-61", token=ALM,
         path={"obra_id": "{{obra_qa_id}}"},
         body={"nombre": "[QA] Trabajador {{corrida}}", "tipo_documento": "DNI", "documento": "{{dni_trab}}", "categoria": "peon",
               "cuadrilla": "QA", "fecha_ingreso": "{{hace_7}}"}, espera=201,
         pruebas=js("pm.test('nace sin fecha de salida', () => pm.expect(pm.response.json().fecha_salida).to.be.null);", guarda("trabajador_id", "pm.response.json().id"))),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/trabajadores", nombre="Trabajadores activos", hu="HU-61", token=ALM,
         path={"obra_id": "{{obra_qa_id}}"}, query={"solo_activos": "true"}, espera=200, pruebas=js(lista())),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/jornadas", nombre="Registrar la jornada de ayer con uuid_cliente", hu="HU-62 · R3", token=ALM,
         path={"obra_id": "{{obra_qa_id}}"},
         body={"trabajador_id": "{{trabajador_id}}", "fecha": "{{ayer}}", "estado": "presente", "horas_normales": "8", "horas_extra": "0", "uuid_cliente": "{{uuid_jornada}}"},
         crudo=True, espera=201,
         pruebas=js("pm.test('congela categoría y periodo', () => { const j = pm.response.json(); pm.expect(j.categoria).to.eql('peon'); pm.expect(j.periodo_id).to.be.above(0); });",
                    guarda("jornada_id", "pm.response.json().id"))),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/jornadas", nombre="Reenviar la misma jornada no duplica (R3)", hu="HU-62 · R3", token=ALM,
         path={"obra_id": "{{obra_qa_id}}"},
         body={"trabajador_id": "{{trabajador_id}}", "fecha": "{{ayer}}", "estado": "presente", "horas_normales": "8", "horas_extra": "0", "uuid_cliente": "{{uuid_jornada}}"},
         crudo=True, espera=200, solo_ok=True,
         pruebas=js("pm.test('devuelve el original', () => pm.expect(pm.response.json().id).to.eql(Number(pm.collectionVariables.get('jornada_id'))));")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/jornadas", nombre="Jornadas de ayer", hu="HU-62", token=ALM,
         path={"obra_id": "{{obra_qa_id}}"}, query={"fecha": "{{ayer}}"}, espera=200,
         pruebas=js(lista(), "pm.test('una sola jornada pese al reenvío', () => pm.expect(pm.response.json().filter(j => j.trabajador_id === Number(pm.collectionVariables.get('trabajador_id')))).to.have.lengthOf(1));")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/jornadas/{jornada_id}/correccion", nombre="Corregir la jornada con motivo (la original no se toca)", hu="HU-63 · R1", token=ALM,
         path={"obra_id": "{{obra_qa_id}}", "jornada_id": "{{jornada_id}}"},
         body={"estado": "presente", "horas_normales": "8", "horas_extra": "2", "motivo": "[QA] corrección de prueba Postman"}, espera=201,
         pruebas=js("pm.test('apunta a la original', () => pm.expect(pm.response.json().corrige_a_id).to.eql(Number(pm.collectionVariables.get('jornada_id'))));")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/horas-acumuladas", nombre="Horas acumuladas (cuenta la versión vigente)", hu="HU-64", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}"}, espera=200,
         pruebas=js(lista(), "const h = pm.response.json().find(x => x.trabajador_id === Number(pm.collectionVariables.get('trabajador_id')));",
                    "pm.test('8 normales y 2 extra (la corrección reemplaza, no suma)', () => { pm.expect(Number(h.horas_normales)).to.eql(8); pm.expect(Number(h.horas_extra)).to.eql(2); });")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/trabajadores/{trabajador_id}/baja", nombre="Dar de baja al trabajador con fecha (R10)", hu="HU-61 · R10", token=ALM,
         path={"obra_id": "{{obra_qa_id}}", "trabajador_id": "{{trabajador_id}}"}, body={"fecha_salida": "{{hoy}}"}, espera=200,
         pruebas=js("pm.test('queda con fecha de salida', () => pm.expect(pm.response.json().fecha_salida).to.eql(pm.collectionVariables.get('hoy')));")),
    "99 · Cierre",   # retiros y bajas: nada se borra
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/asignaciones/{asignacion_id}/retiro", nombre="Retirar al residente de la obra de prueba", hu="HU-14", token=ADMIN,
         path={"obra_id": "{{obra_qa_id}}", "asignacion_id": "{{asignacion_res_id}}"}, espera=200, carpeta="99 · Cierre",
         pruebas=js("pm.test('queda con fecha de retiro', () => pm.expect(pm.response.json().fecha_retiro).to.not.be.null);")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}/requerimiento", nombre="Retirado: el residente pierde el acceso al instante", hu="HU-14 · R9", token=RES,
         path={"obra_id": "{{obra_qa_id}}"}, espera=403, solo_ok=True, carpeta="99 · Cierre",
         pruebas=js("pm.test('código sin_permiso', () => pm.expect(pm.response.json().codigo).to.eql('sin_permiso'));")),
    dict(metodo="POST", ruta="/api/v1/proveedores/{proveedor_id}/baja", nombre="Dar de baja al proveedor de prueba", hu="HU-30 · R10", token=ADMIN,
         path={"proveedor_id": "{{proveedor_id}}"}, espera=200, carpeta="99 · Cierre",
         pruebas=js("pm.test('queda de baja', () => pm.expect(pm.response.json().estado).to.eql('baja'));")),
    dict(metodo="POST", ruta="/api/v1/usuarios/{usuario_id}/baja", nombre="Dar de baja al almacenero de prueba", hu="HU-13 · R10", token=ADMIN,
         path={"usuario_id": "{{usuario_alm_id}}"}, espera=200, carpeta="99 · Cierre",
         pruebas=js("pm.test('queda de baja', () => pm.expect(pm.response.json().estado).to.eql('baja'));")),
    dict(metodo="POST", ruta="/api/v1/usuarios/{usuario_id}/baja", nombre="Dar de baja al residente de prueba", hu="HU-13 · R10", token=ADMIN,
         path={"usuario_id": "{{usuario_res_id}}"}, espera=200, solo_ok=True, carpeta="99 · Cierre"),
    dict(metodo="GET", ruta="/api/v1/auth/yo", nombre="De baja: su token deja de servir al instante", hu="HU-13 · R9", token=ALM,
         espera=401, solo_ok=True, carpeta="99 · Cierre"),
]

# Casos de error que el contrato no puede deducir (reglas de dominio) — se agregan al endpoint indicado.
ERRORES_EXTRA: list[dict] = [
    dict(metodo="POST", ruta="/api/v1/auth/login", nombre="401 · usuario inexistente (misma respuesta que clave mala)", hu="HU-12",
         token=None, body={"usuario": "qa_no_existe_{{corrida}}", "clave": "NoEsLaClave123"}, espera=401,
         pruebas=js("pm.test('código credenciales_invalidas', () => pm.expect(pm.response.json().codigo).to.eql('credenciales_invalidas'));")),
    dict(metodo="GET", ruta="/api/v1/obras/{obra_id}", nombre="403 · el almacenero no ve una obra donde no está asignado (R9)", hu="R9",
         token=ALM, path={"obra_id": "{{obra_id}}"}, espera=403),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/compras", nombre="403 · el almacenero no registra compras (rol)", hu="R9 · permisos",
         token=ALM, path={"obra_id": "{{obra_qa_id}}"},
         body={"proveedor_id": "{{proveedor_id}}", "tipo_comprobante": "factura", "numero_comprobante": "F002-{{corrida}}", "fecha": "{{ayer}}",
               "lineas": [{"insumo_codigo": CEMENTO["codigo"], "cantidad": "1", "unidad_compra": "bls", "precio_unitario": "29.50"}]},
         crudo=True, espera=403),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/solicitudes", nombre="403 · el administrador no emite solicitudes (D-C4)", hu="HU-66 · D-C4",
         token=ADMIN, path={"obra_id": "{{obra_qa_id}}"}, body={"lineas": [{"insumo_codigo": CEMENTO["codigo"], "cantidad": "1", "unidad_compra": "bls"}]}, espera=403),
    dict(metodo="POST", ruta="/api/v1/usuarios", nombre="422 · clave débil", hu="HU-13",
         token=ADMIN, body={"nombre": "[QA] Débil", "usuario": "qa_debil_{{corrida}}", "clave": "corta"}, espera=422,
         pruebas=js("pm.test('código clave_debil', () => pm.expect(pm.response.json().codigo).to.eql('clave_debil'));")),
    dict(metodo="POST", ruta="/api/v1/catalogo/insumos", nombre="409 · nombre repetido con la misma unidad (HU-08)", hu="HU-08",
         token=ADMIN, body={"descripcion": "[QA] Insumo Postman {{corrida}}", "unidad": "und", "tipo": "material"}, espera=409,
         pruebas=js("pm.test('código insumo_duplicado', () => pm.expect(pm.response.json().codigo).to.eql('insumo_duplicado'));")),
    dict(metodo="POST", ruta="/api/v1/proveedores", nombre="409 · documento repetido", hu="HU-30",
         token=ADMIN, body={"ruc": "{{dni}}", "razon_social": "[QA] Repetido {{corrida}}"}, espera=409),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/jornadas/{jornada_id}/correccion", nombre="409 · corregir dos veces la misma versión", hu="HU-63",
         token=ALM, path={"obra_id": "{{obra_qa_id}}", "jornada_id": "{{jornada_id}}"},
         body={"estado": "presente", "horas_normales": "8", "horas_extra": "1", "motivo": "[QA] segunda corrección"}, espera=409,
         pruebas=js("pm.test('código ya_corregida', () => pm.expect(pm.response.json().codigo).to.eql('ya_corregida'));")),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/parametros-reposicion", nombre="422 · punto de reposición menor que el stock de seguridad", hu="HU-25",
         token=RES, path={"obra_id": "{{obra_qa_id}}"}, body={"insumo_codigo": CEMENTO["codigo"], "stock_seguridad": "100", "punto_reposicion": "10"}, espera=422),
    dict(metodo="POST", ruta="/api/v1/obras/{obra_id}/asignaciones/{asignacion_id}/retiro", nombre="409 · retirar dos veces", hu="HU-14",
         token=ADMIN, path={"obra_id": "{{obra_qa_id}}", "asignacion_id": "{{asignacion_res_id}}"}, espera=409, carpeta="99 · Cierre"),
]

# Rutas donde el 404 genérico no aplica (la ruta no busca el recurso o el micro externo responde otra cosa).
SIN_404 = {
    ("POST", "/api/v1/notificaciones/{notificacion_id}/leida"),   # sin el micro de notificaciones responde 503
}
# Valores válidos de los path params para los casos de error genéricos (403 y 404).
PATH_VALIDO = {"obra_id": "{{obra_qa_id}}", "usuario_id": "{{usuario_alm_id}}", "insumo_id": "{{insumo_id}}",
               "proveedor_id": "{{proveedor_id}}", "compra_id": "{{compra_id}}", "solicitud_id": "{{solicitud_id}}",
               "req_id": "{{req_id}}", "trabajador_id": "{{trabajador_id}}", "jornada_id": "{{jornada_id}}",
               "asignacion_id": "{{asignacion_alm_id}}", "dispositivo_id": "{{dispositivo_id}}",
               "notificacion_id": "{{notificacion_id}}"}
# Token con el que se prueban 403/404/422 genéricos (alguien que SÍ tiene el rol en la obra de prueba).
TOKEN_ERRORES = {("POST", "/api/v1/obras/{obra_id}/dispositivos"): ALM, ("POST", "/api/v1/obras/{obra_id}/trabajadores"): ALM,
                 ("POST", "/api/v1/obras/{obra_id}/jornadas"): ALM, ("POST", "/api/v1/obras/{obra_id}/jornadas/{jornada_id}/correccion"): ALM,
                 ("POST", "/api/v1/obras/{obra_id}/trabajadores/{trabajador_id}/baja"): ALM,
                 ("POST", "/api/v1/obras/{obra_id}/solicitudes"): RES, ("POST", "/api/v1/obras/{obra_id}/solicitudes/{solicitud_id}/anular"): RES}


# ------------------------------------------------------------------------------------------------ construcción
def _id(*partes: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "sigro-postman/" + "/".join(partes)))   # estable: el diff solo muestra cambios reales


def _url(ruta: str, path: dict[str, str] | None, query: dict[str, str] | None) -> dict:
    valores = path or {}
    segmentos = []
    for s in ruta.strip("/").split("/"):
        if s.startswith("{") and s.endswith("}"):
            nombre = s[1:-1]
            segmentos.append(valores.get(nombre, PATH_VALIDO.get(nombre, "{{" + nombre + "}}")))
        else:
            segmentos.append(s)
    crudo = "{{baseUrl}}/" + "/".join(segmentos)
    url: dict = {"raw": crudo, "host": ["{{baseUrl}}"], "path": segmentos}
    if query:
        url["query"] = [{"key": k, "value": v} for k, v in query.items()]
        url["raw"] += "?" + "&".join(f"{k}={v}" for k, v in query.items())
    return url


def _cuerpo(body, crudo: bool) -> dict | None:
    if body is None:
        return None
    if body == "multipart":
        return {"mode": "formdata", "formdata": [{"key": "archivo", "type": "file", "src": PDF_S10}]}
    if body == "multipart-vacio":
        return {"mode": "formdata", "formdata": []}
    texto = json.dumps(body, ensure_ascii=False, indent=2)
    if crudo:   # ids numéricos tomados de variables: van sin comillas en el JSON
        for v in ("usuario_alm_id", "usuario_res_id", "proveedor_id", "solicitud_id", "trabajador_id"):
            texto = texto.replace(f'"{{{{{v}}}}}"', f"{{{{{v}}}}}")
    return {"mode": "raw", "raw": texto, "options": {"raw": {"language": "json"}}}


def _item(caso: dict, nombre: str, clave: str) -> dict:
    cabeceras = []
    if caso.get("token"):
        cabeceras.append({"key": "Authorization", "value": "Bearer {{" + caso["token"] + "}}"})
    if isinstance(caso.get("body"), (dict, list)):
        cabeceras.append({"key": "Content-Type", "value": "application/json"})
    pruebas = [estado(caso["espera"])]
    if isinstance(caso["espera"], int) and caso["espera"] >= 400:
        pruebas.append("pm.test('error con la forma {codigo, mensaje}', () => { const e = pm.response.json(); pm.expect(e.codigo).to.be.a('string'); pm.expect(e.mensaje).to.be.a('string'); });")
    pruebas += caso.get("pruebas", [])
    req: dict = {"method": caso["metodo"], "header": cabeceras, "url": _url(caso["ruta"], caso.get("path"), caso.get("query"))}
    cuerpo = _cuerpo(caso.get("body"), caso.get("crudo", False))
    if cuerpo:
        req["body"] = cuerpo
    eventos = [{"listen": "test", "script": {"id": _id(clave, "test"), "type": "text/javascript", "exec": pruebas}}]
    if caso.get("antes"):
        eventos.insert(0, {"listen": "prerequest", "script": {"id": _id(clave, "pre"), "type": "text/javascript", "exec": caso["antes"]}})
    desc = f"**{caso.get('hu', '')}** · `{caso['metodo']} {caso['ruta']}`"
    return {"id": _id(clave), "name": nombre, "request": {**req, "description": desc}, "event": eventos}


def _errores_genericos(metodo: str, ruta: str, op: dict, ok: dict | None) -> list[dict]:
    """Casos de error que salen solos del contrato."""
    params = [p["name"] for p in op.get("parameters", []) if p["in"] == "path"]
    seguro = bool(op.get("security"))
    cuerpo = op.get("requestBody", {}).get("content", {})
    token = TOKEN_ERRORES.get((metodo, ruta), ok["token"] if ok and ok.get("token") else ADMIN)
    base = dict(metodo=metodo, ruta=ruta, hu="R9" if seguro else "")
    salida = []
    if seguro:
        # ids inexistentes: sin token, el servidor rechaza antes de buscar nada (no depende de lo creado antes)
        salida.append({**base, "nombre": "401 · sin token", "token": None, "espera": 401,
                       "path": {p: str(INEXISTENTE) for p in params}, "body": (ok or {}).get("body"),
                       "pruebas": js("pm.test('código no_autenticado', () => pm.expect(pm.response.json().codigo).to.eql('no_autenticado'));")})
    if "obra_id" in params:
        salida.append({**base, "nombre": "403 · obra fuera de alcance", "token": token, "espera": 403,
                       "path": {"obra_id": str(INEXISTENTE)}, "body": (ok or {}).get("body"), "crudo": (ok or {}).get("crudo", False),
                       "pruebas": js("pm.test('código sin_permiso', () => pm.expect(pm.response.json().codigo).to.eql('sin_permiso'));")})
    if "application/json" in cuerpo or "multipart/form-data" in cuerpo:
        salida.append({**base, "nombre": "422 · cuerpo inválido", "token": token, "espera": 422, "hu": "contrato",
                       "path": (ok or {}).get("path"), "body": {} if "application/json" in cuerpo else "multipart-vacio",
                       "pruebas": js("pm.test('código datos_invalidos', () => pm.expect(pm.response.json().codigo).to.eql('datos_invalidos'));")})
    otros = [p for p in params if p != "obra_id"]
    if otros and (metodo, ruta) not in SIN_404:
        path = {**((ok or {}).get("path") or {}), **{p: str(INEXISTENTE) for p in otros}}
        salida.append({**base, "nombre": "404 · no existe", "token": token, "espera": 404, "path": path,
                       "body": (ok or {}).get("body"), "crudo": (ok or {}).get("crudo", False),
                       "pruebas": js("pm.test('código no_existe', () => pm.expect(pm.response.json().codigo).to.match(/no_existe/));")})
    return salida


def _casos() -> list[dict]:
    return [c for c in CASOS if isinstance(c, dict)]


def construir(contrato: dict) -> tuple[dict, list[str]]:
    ops = {(m.upper(), p): o for p, v in contrato["paths"].items() for m, o in v.items()}
    sin_caso = sorted(f"{m} {p}" for (m, p) in ops if not any(c["metodo"] == m and c["ruta"] == p for c in _casos()))
    for c in _casos() + ERRORES_EXTRA:
        if (c["metodo"], c["ruta"]) not in ops:
            raise SystemExit(f"El caso «{c['nombre']}» apunta a {c['metodo']} {c['ruta']}, que ya no está en el contrato.")

    carpetas: dict[str, list] = {}
    hechos: set = set()

    actual = "otros"
    for i, c in enumerate(CASOS):
        if isinstance(c, str):
            actual = c
            continue
        clave = (c["metodo"], c["ruta"])
        dest = carpetas.setdefault(c.get("carpeta") or actual, [])
        op = ops[clave]
        dest.append(_item(c, f"{c['nombre']}", f"ok/{i}"))
        if c.get("solo_ok") or clave in hechos:
            continue
        hechos.add(clave)
        for e in _errores_genericos(c["metodo"], c["ruta"], op, c):
            dest.append(_item(e, f"{e['nombre']} · {c['metodo']} {c['ruta'].removeprefix('/api/v1')}", f"err/{c['metodo']}{c['ruta']}/{e['nombre']}"))
    # reglas de dominio: después de todos los casos correctos (ya existen tokens e ids), antes del cierre
    for e in ERRORES_EXTRA:
        carpetas.setdefault(e.get("carpeta") or REGLAS, []).append(_item(e, e["nombre"], f"extra/{e['nombre']}"))
    # endpoints nuevos sin caso correcto: al menos sus errores
    for m, p in sorted(ops):
        if (m, p) not in hechos and not any(c["metodo"] == m and c["ruta"] == p for c in _casos()):
            dest = carpetas.setdefault("Sin caso correcto todavía", [])
            for e in _errores_genericos(m, p, ops[(m, p)], None):
                dest.append(_item(e, f"{e['nombre']} · {m} {p.removeprefix('/api/v1')}", f"err/{m}{p}/{e['nombre']}"))

    finales = [REGLAS, "99 · Cierre"]
    orden = [k for k in carpetas if k not in finales] + [k for k in finales if k in carpetas]
    variables = ["corrida", "token", "token_almacenero", "token_residente", "obra_id", "obra_qa_id", "almacen_id",
                 "usuario_alm_id", "usuario_res_id", "asignacion_alm_id", "asignacion_res_id", "dispositivo_id",
                 "notificacion_id", "insumo_id", "insumo_cemento_id", "proveedor_id", "req_id", "solicitud_id", "compra_id",
                 "trabajador_id", "jornada_id", "clave_qa", "clave_qa_nueva", "dni", "dni_trab", "uuid_jornada",
                 "hoy", "ayer", "hace_7", "hace_14", "en_60", "en_90"]
    coleccion = {
        "info": {
            "_postman_id": _id("coleccion"),
            "name": f"SIGRO · API pública (gateway) · {contrato['info']['version']}",
            "description": ("Generada por `docs/pruebas/postman/generar.py` desde `api/docs/openapi.json`. **No se edita a mano**: "
                            "se cambia el generador y se vuelve a correr.\n\nAntes de correrla, llena `usuario` y `clave` del ambiente "
                            "(un administrador de una obra). La corrida crea su propia obra «[QA] Obra Postman …» y dos usuarios "
                            "de prueba que da de baja al final. Plan de pruebas: `docs/pruebas/plan-de-pruebas.md`."),
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "item": [{"id": _id("carpeta", k), "name": k, "item": carpetas[k]} for k in orden],
        "event": [{"listen": "test", "script": {"id": _id("global-test"), "type": "text/javascript", "exec": js(
            "pm.test('responde en menos de 90 s (Render despierta en ~1 min)', () => pm.expect(pm.response.responseTime).to.be.below(90000));",
        )}}],
        "variable": [{"key": v, "value": ""} for v in variables],
    }
    return coleccion, sin_caso


PLAN = RAIZ / "docs" / "pruebas" / "plan-de-pruebas.md"
INICIO, FIN = "<!-- casos-postman:inicio (lo escribe generar.py) -->", "<!-- casos-postman:fin -->"


def tabla_casos(coleccion: dict) -> str:
    """La tabla de casos de Postman del plan de pruebas, sacada de la colección misma (no se desfasa)."""
    import re
    filas = ["| # | Carpeta | Caso | Petición | HU / regla | Espera |", "|---|---|---|---|---|---|"]
    n = 0
    for carpeta in coleccion["item"]:
        for it in carpeta["item"]:
            n += 1
            m = re.match(r"\*\*(.*?)\*\* · `(\w+) (\S+)`", it["request"]["description"])
            hu, metodo, ruta = (m.group(1), m.group(2), m.group(3)) if m else ("", it["request"]["method"], "")
            espera = re.search(r"responde ([\d o]+)'", it["event"][-1]["script"]["exec"][0])
            nombre = re.sub(r" · (GET|POST|PUT|PATCH|DELETE) /.*$", "", it["name"])
            filas.append(f"| {n} | {carpeta['name']} | {nombre} | `{metodo} {ruta.removeprefix('/api/v1')}` | {hu or '—'} | "
                         f"{espera.group(1) if espera else ''} |")
    return "\n".join(filas)


def plan_con_tabla(coleccion: dict) -> str | None:
    if not PLAN.exists():
        return None
    texto = PLAN.read_text(encoding="utf-8")
    if INICIO not in texto or FIN not in texto:
        return texto
    antes, resto = texto.split(INICIO, 1)
    _, despues = resto.split(FIN, 1)
    return f"{antes}{INICIO}\n{tabla_casos(coleccion)}\n{FIN}{despues}"


def ambiente(nombre: str, base: str) -> dict:
    return {
        "id": _id("ambiente", nombre),
        "name": f"SIGRO · {nombre}",
        "values": [
            {"key": "baseUrl", "value": base, "type": "default", "enabled": True},
            {"key": "usuario", "value": "", "type": "default", "enabled": True},
            {"key": "clave", "value": "", "type": "secret", "enabled": True},
        ],
        "_postman_variable_scope": "environment",
    }


def _texto(d: dict) -> str:
    return json.dumps(d, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--revisar", action="store_true", help="no escribe; falla si los archivos del repo no coinciden")
    args = ap.parse_args()
    contrato = json.loads(CONTRATO.read_text(encoding="utf-8"))
    coleccion, sin_caso = construir(contrato)
    archivos = {
        SALIDA / "sigro.postman_collection.json": _texto(coleccion),
        SALIDA / "sigro-local.postman_environment.json": _texto(ambiente("local", "http://localhost:8000")),
        SALIDA / "sigro-render.postman_environment.json": _texto(ambiente("Render (pruebas)", "https://sigro-gateway.onrender.com")),
    }
    plan = plan_con_tabla(coleccion)
    if plan is not None:
        archivos[PLAN] = plan
    total = sum(len(c["item"]) for c in coleccion["item"])
    if sin_caso:
        print("Endpoints SIN caso correcto (agrégalos a CASOS):\n  " + "\n  ".join(sin_caso), file=sys.stderr)
    if args.revisar:
        distintos = [p.name for p, t in archivos.items() if not p.exists() or p.read_text(encoding="utf-8") != t]
        if distintos or sin_caso:
            print("La colección no está al día con openapi.json: " + ", ".join(distintos), file=sys.stderr)
            return 1
        print(f"Colección al día: {total} peticiones.")
        return 0
    for p, t in archivos.items():
        p.write_text(t, encoding="utf-8", newline="\n")
    print(f"{total} peticiones en {len(coleccion['item'])} carpetas · {len(contrato['paths'])} rutas del contrato.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
