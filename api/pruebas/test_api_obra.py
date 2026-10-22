"""API del módulo obra: usuarios, obras, periodos, plazo, almacenes, asignaciones y dispositivos."""
from __future__ import annotations

import pytest

U = "/api/v1"


@pytest.fixture
def eq(mundo):
    return mundo.f.obra_completa()


def post(mundo, ruta, uid, **cuerpo):
    return mundo.http.post(f"{U}{ruta}", headers=mundo.con(uid), json=cuerpo)


def get(mundo, ruta, uid):
    return mundo.http.get(f"{U}{ruta}", headers=mundo.con(uid))


# ──────────────────────────────────────────────── usuarios
def test_el_administrador_crea_usuarios_y_no_se_repiten_aunque_cambie_la_mayuscula(mundo, eq):
    ok = post(mundo, "/usuarios", eq["admin"], nombre="Luis Condori", usuario="lcondori", clave="Clave-Segura-2026")
    assert ok.status_code == 201 and ok.json()["estado"] == "activo"
    dup = post(mundo, "/usuarios", eq["admin"], nombre="Otro", usuario="LCondori", clave="Clave-Segura-2026")
    assert dup.status_code == 409


def test_crear_usuario_valida_la_clave_y_el_formato(mundo, eq):
    assert post(mundo, "/usuarios", eq["admin"], nombre="A B", usuario="ab.cd", clave="corta").json()["codigo"] == "clave_debil"
    assert post(mundo, "/usuarios", eq["admin"], nombre="A B", usuario="a b", clave="Clave-Segura-2026").status_code == 422
    assert post(mundo, "/usuarios", eq["admin"], nombre="A B", usuario="abcd", clave="Clave-Segura-2026", rol="administrador").status_code == 422


def test_solo_el_administrador_ve_la_lista_de_usuarios(mundo, eq):
    assert get(mundo, "/usuarios", eq["admin"]).status_code == 200
    for rol in ("residente", "almacenero", "ssoma", "gerente"):
        assert get(mundo, "/usuarios", eq[rol]).status_code == 403


def test_dar_de_baja_a_un_usuario_conserva_el_registro_y_retira_sus_asignaciones(mundo, eq):
    r = post(mundo, f"/usuarios/{eq['almacenero']}/baja", eq["admin"])
    assert r.status_code == 200 and r.json()["estado"] == "baja"
    asig = mundo.f.c.execute("SELECT fecha_retiro FROM obra.asignacion WHERE usuario_id=%s", (eq["almacenero"],)).fetchone()
    assert asig["fecha_retiro"] is not None
    assert mundo.f.c.execute("SELECT count(*) AS n FROM obra.usuario WHERE id=%s", (eq["almacenero"],)).fetchone()["n"] == 1
    assert post(mundo, f"/usuarios/{eq['almacenero']}/baja", eq["admin"]).status_code == 404     # ya estaba de baja
    assert post(mundo, f"/usuarios/{eq['admin']}/baja", eq["admin"]).json()["codigo"] == "baja_propia"


# ──────────────────────────────────────────────── obras (HU-01 · HU-03)
def test_crear_obra_genera_los_periodos_y_asigna_a_quien_la_crea(mundo, eq):
    r = post(mundo, "/obras", eq["residente"], nombre="Edificio Los Álamos", fecha_inicio="2026-10-19", fecha_fin="2026-12-06")
    assert r.status_code == 201
    o = r.json()
    assert o["rol"] == "residente" and o["semanas_totales"] == 7 and o["dia_corte"] == 7
    periodos = get(mundo, f"/obras/{o['id']}/periodos", eq["residente"]).json()
    assert [p["numero"] for p in periodos] == list(range(1, 8))
    assert periodos[0]["fecha_inicio"] == "2026-10-19" and periodos[-1]["fecha_fin"] == "2026-12-06"
    assert [x["id"] for x in get(mundo, "/obras", eq["residente"]).json()].count(o["id"]) == 1


def test_crear_obra_exige_rol_de_residente_o_administrador(mundo, eq):
    for rol in ("almacenero", "ssoma", "gerente"):
        r = post(mundo, "/obras", eq[rol], nombre="X Obra", fecha_inicio="2026-10-19", fecha_fin="2026-12-06")
        assert r.status_code == 403, rol


def test_crear_obra_rechaza_nombre_repetido_y_plazo_invertido(mundo, eq):
    nombre = mundo.f.c.execute("SELECT nombre FROM obra.proyecto WHERE id=%s", (eq["proyecto"],)).fetchone()["nombre"]
    assert post(mundo, "/obras", eq["admin"], nombre=nombre.upper(), fecha_inicio="2026-10-19", fecha_fin="2026-12-06").status_code == 409
    r = post(mundo, "/obras", eq["admin"], nombre="Plazo roto", fecha_inicio="2026-12-06", fecha_fin="2026-10-19")
    assert r.status_code == 422
    r = post(mundo, "/obras", eq["admin"], nombre="Corte raro", fecha_inicio="2026-10-19", fecha_fin="2026-12-06", dia_corte=9)
    assert r.status_code == 422


def test_el_dia_de_corte_se_respeta(mundo, eq):
    r = post(mundo, "/obras", eq["admin"], nombre="Corte viernes", fecha_inicio="2026-10-19", fecha_fin="2026-11-01", dia_corte=5)
    periodos = get(mundo, f"/obras/{r.json()['id']}/periodos", eq["admin"]).json()
    assert periodos[0]["fecha_fin"] == "2026-10-23"          # viernes


# ──────────────────────────────────────────────── plazo (HU-02)
def test_ampliar_el_plazo_deja_rastro_y_agrega_periodos(mundo, eq):
    p = eq["proyecto"]
    antes = get(mundo, f"/obras/{p}", eq["residente"]).json()
    r = post(mundo, f"/obras/{p}/ampliar-plazo", eq["residente"], nueva_fecha_fin="2026-12-27", motivo="Lluvias de noviembre")
    assert r.status_code == 200
    despues = r.json()
    assert despues["fecha_fin"] == "2026-12-27" and despues["semanas_totales"] == antes["semanas_totales"] + 3
    h = get(mundo, f"/obras/{p}/plazo-historial", eq["gerente"]).json()
    assert len(h) == 1 and h[0]["fecha_fin_anterior"] == "2026-12-06" and h[0]["motivo"] == "Lluvias de noviembre"


def test_ampliar_exige_una_fecha_posterior_y_un_motivo(mundo, eq):
    p = eq["proyecto"]
    assert post(mundo, f"/obras/{p}/ampliar-plazo", eq["residente"], nueva_fecha_fin="2026-12-01", motivo="Adelanto").status_code == 422
    assert post(mundo, f"/obras/{p}/ampliar-plazo", eq["residente"], nueva_fecha_fin="2026-12-20", motivo="").status_code == 422


def test_el_plazo_no_se_pisa_por_ningun_camino_de_la_api(mundo, eq):
    """No hay endpoint para editar fecha_fin: PUT/PATCH/DELETE no existen."""
    p = eq["proyecto"]
    for metodo in ("put", "patch", "delete"):
        r = getattr(mundo.http, metodo)(f"{U}/obras/{p}", headers=mundo.con(eq["admin"]))
        assert r.status_code == 405


# ──────────────────────────────────────────────── almacenes
def test_almacenes(mundo, eq):
    p = eq["proyecto"]
    assert post(mundo, f"/obras/{p}/almacenes", eq["almacenero"], nombre="Almacén central", tipo="central").status_code == 201
    assert post(mundo, f"/obras/{p}/almacenes", eq["residente"], nombre="ALMACÉN CENTRAL", tipo="central").status_code == 409
    assert post(mundo, f"/obras/{p}/almacenes", eq["residente"], nombre="Frente B", tipo="otro").status_code == 422
    assert post(mundo, f"/obras/{p}/almacenes", eq["gerente"], nombre="Frente C").status_code == 403
    assert [a["nombre"] for a in get(mundo, f"/obras/{p}/almacenes", eq["gerente"]).json()] == ["Almacén central"]


# ──────────────────────────────────────────────── asignación (HU-13 · HU-14)
def test_asignar_rol_a_una_persona_en_una_obra(mundo, eq):
    p = eq["proyecto"]
    nuevo = mundo.f.usuario()
    r = post(mundo, f"/obras/{p}/asignaciones", eq["admin"], usuario_id=nuevo, rol="almacenero")
    assert r.status_code == 201 and r.json()["rol"] == "almacenero" and r.json()["fecha_retiro"] is None
    # la misma persona no puede tener dos asignaciones vigentes en la misma obra
    assert post(mundo, f"/obras/{p}/asignaciones", eq["admin"], usuario_id=nuevo, rol="ssoma").status_code == 409
    # …pero sí otro rol en OTRA obra (el rol vive en la asignación, no en el usuario)
    otra = mundo.f.proyecto(eq["admin"])
    mundo.f.asignar(eq["admin"], otra, "administrador")
    assert post(mundo, f"/obras/{otra}/asignaciones", eq["admin"], usuario_id=nuevo, rol="residente").status_code == 201


def test_asignar_valida_usuario_y_rol(mundo, eq):
    p = eq["proyecto"]
    assert post(mundo, f"/obras/{p}/asignaciones", eq["admin"], usuario_id=999999, rol="residente").json()["codigo"] == "usuario_no_existe"
    assert post(mundo, f"/obras/{p}/asignaciones", eq["admin"], usuario_id=mundo.f.usuario(), rol="jefe").status_code == 422
    baja = mundo.f.usuario()
    mundo.conn.exec_driver_sql("UPDATE obra.usuario SET estado='baja', fecha_baja=now() WHERE id=%s", (baja,))
    assert post(mundo, f"/obras/{p}/asignaciones", eq["admin"], usuario_id=baja, rol="residente").json()["codigo"] == "usuario_de_baja"


def test_solo_el_administrador_asigna_el_residente_solo_consulta(mundo, eq):
    p = eq["proyecto"]
    nuevo = mundo.f.usuario()
    assert post(mundo, f"/obras/{p}/asignaciones", eq["residente"], usuario_id=nuevo, rol="almacenero").status_code == 403
    assert get(mundo, f"/obras/{p}/asignaciones", eq["residente"]).status_code == 200
    assert get(mundo, f"/obras/{p}/asignaciones", eq["almacenero"]).status_code == 403


def test_retirar_no_borra_y_el_historial_conserva_la_fila(mundo, eq):
    p = eq["proyecto"]
    a = [x for x in get(mundo, f"/obras/{p}/asignaciones", eq["admin"]).json() if x["usuario_id"] == eq["ssoma"]][0]
    r = post(mundo, f"/obras/{p}/asignaciones/{a['id']}/retiro", eq["admin"])
    assert r.status_code == 200 and r.json()["fecha_retiro"] is not None
    assert post(mundo, f"/obras/{p}/asignaciones/{a['id']}/retiro", eq["admin"]).status_code == 409
    vigentes = get(mundo, f"/obras/{p}/asignaciones", eq["admin"]).json()
    todas = get(mundo, f"/obras/{p}/asignaciones?vigentes=false", eq["admin"]).json()
    assert a["id"] not in [x["id"] for x in vigentes] and a["id"] in [x["id"] for x in todas]
    # y se puede volver a asignar después
    assert post(mundo, f"/obras/{p}/asignaciones", eq["admin"], usuario_id=eq["ssoma"], rol="ssoma").status_code == 201


def test_no_se_deja_la_obra_sin_administrador(mundo, eq):
    p = eq["proyecto"]
    a = [x for x in get(mundo, f"/obras/{p}/asignaciones", eq["admin"]).json() if x["rol"] == "administrador"][0]
    r = post(mundo, f"/obras/{p}/asignaciones/{a['id']}/retiro", eq["admin"])
    assert r.status_code == 409 and r.json()["codigo"] == "ultimo_administrador"


def test_no_se_retira_una_asignacion_de_otra_obra(mundo, eq):
    otra = mundo.f.obra_completa()
    ajena = mundo.f.c.execute("SELECT id FROM obra.asignacion WHERE proyecto_id=%s AND rol='residente'", (otra["proyecto"],)).fetchone()["id"]
    r = post(mundo, f"/obras/{eq['proyecto']}/asignaciones/{ajena}/retiro", eq["admin"])
    assert r.status_code == 404


# ──────────────────────────────────────────────── dispositivos (regla 5)
def test_flujo_de_dispositivo_solicitar_autorizar_revocar(mundo, eq):
    p = eq["proyecto"]
    s = post(mundo, f"/obras/{p}/dispositivos", eq["almacenero"], identificador="android-AAAA-1111")
    assert s.status_code == 201 and s.json()["estado"] == "pendiente" and s.json()["usuario_id"] == eq["almacenero"]
    d = s.json()["id"]
    assert post(mundo, f"/obras/{p}/dispositivos/{d}/autorizar", eq["residente"]).status_code == 403     # el residente no autoriza
    a = post(mundo, f"/obras/{p}/dispositivos/{d}/autorizar", eq["admin"])
    assert a.status_code == 200 and a.json()["estado"] == "activo" and a.json()["fecha_autorizacion"]
    assert post(mundo, f"/obras/{p}/dispositivos/{d}/autorizar", eq["admin"]).status_code == 409           # ya no está pendiente
    assert post(mundo, f"/obras/{p}/dispositivos/{d}/revocar", eq["admin"], motivo="").status_code == 422
    rv = post(mundo, f"/obras/{p}/dispositivos/{d}/revocar", eq["admin"], motivo="Celular perdido")
    assert rv.json()["estado"] == "revocado" and rv.json()["motivo_revocacion"] == "Celular perdido"
    assert post(mundo, f"/obras/{p}/dispositivos/{d}/revocar", eq["admin"], motivo="Otra vez").status_code == 409


def test_un_dispositivo_activo_por_responsable(mundo, eq):
    p = eq["proyecto"]
    d1 = post(mundo, f"/obras/{p}/dispositivos", eq["almacenero"], identificador="android-AAAA-1111").json()["id"]
    assert post(mundo, f"/obras/{p}/dispositivos/{d1}/autorizar", eq["admin"]).status_code == 200
    d2 = post(mundo, f"/obras/{p}/dispositivos", eq["almacenero"], identificador="android-BBBB-2222")
    # pedirlo no basta: autorizarlo dejaría dos activos del mismo almacenero y el motor lo rechaza
    assert d2.status_code == 201 and d2.json()["estado"] == "pendiente"
    r = post(mundo, f"/obras/{p}/dispositivos/{d2.json()['id']}/autorizar", eq["admin"])
    assert r.status_code == 409 and r.json()["codigo"] == "uq_dispositivo_activo_por_usuario"
    assert "revócalo" in r.json()["mensaje"] and "duplicate key" not in r.text


def test_maximo_dos_dispositivos_activos_por_obra_y_solo_almacenero_o_ssoma(mundo, eq):
    p = eq["proyecto"]
    ids = []
    for rol, ident in (("almacenero", "android-AAAA-1111"), ("ssoma", "android-BBBB-2222")):
        d = post(mundo, f"/obras/{p}/dispositivos", eq[rol], identificador=ident).json()["id"]
        assert post(mundo, f"/obras/{p}/dispositivos/{d}/autorizar", eq["admin"]).status_code == 200
        ids.append(d)
    otro = mundo.f.usuario()
    mundo.f.asignar(otro, p, "almacenero", eq["admin"])
    d3 = post(mundo, f"/obras/{p}/dispositivos", otro, identificador="android-CCCC-3333")
    assert d3.status_code == 201                      # pedirlo se puede; autorizarlo, no: ya hay dos activos
    r3 = post(mundo, f"/obras/{p}/dispositivos/{d3.json()['id']}/autorizar", eq["admin"])
    assert r3.status_code == 422 and "dos dispositivos" in r3.json()["mensaje"]
    # el residente ni siquiera puede pedir uno
    assert post(mundo, f"/obras/{p}/dispositivos", eq["residente"], identificador="android-DDDD-4444").status_code == 403
    activos = mundo.f.c.execute("SELECT count(*) AS n FROM obra.dispositivo WHERE proyecto_id=%s AND estado='activo'", (p,)).fetchone()["n"]
    assert activos == 2


def test_el_identificador_del_dispositivo_se_valida(mundo, eq):
    p = eq["proyecto"]
    for malo in ("corto", "con espacios en medio!", "x" * 200):
        assert post(mundo, f"/obras/{p}/dispositivos", eq["almacenero"], identificador=malo).status_code == 422


def test_no_se_toca_un_dispositivo_de_otra_obra(mundo, eq):
    otra = mundo.f.obra_completa()
    d = post(mundo, f"/obras/{otra['proyecto']}/dispositivos", otra["almacenero"], identificador="android-ZZZZ-9999").json()["id"]
    assert post(mundo, f"/obras/{eq['proyecto']}/dispositivos/{d}/autorizar", eq["admin"]).status_code == 404
    assert post(mundo, f"/obras/{eq['proyecto']}/dispositivos/{d}/revocar", eq["admin"], motivo="intento").status_code == 404


def test_los_errores_del_motor_no_filtran_detalles_internos(mundo, eq):
    r = post(mundo, "/usuarios", eq["admin"], nombre="A B", usuario="abcd", clave="Clave-Segura-2026")
    r2 = post(mundo, "/usuarios", eq["admin"], nombre="A B", usuario="abcd", clave="Clave-Segura-2026")
    assert r2.status_code == 409
    assert r2.json() == {"codigo": "uq_usuario_usuario", "mensaje": "Ese nombre de usuario ya existe."}
    cuerpo = r2.text.lower()
    assert "select" not in cuerpo and "psycopg" not in cuerpo and "traceback" not in cuerpo and "sigro_app" not in cuerpo
