"""API de tareo: trabajadores, jornadas (libro con correcciones) y horas acumuladas."""
from __future__ import annotations

import uuid

import pytest

U = "/api/v1"


@pytest.fixture
def eq(mundo):
    return mundo.f.obra_completa()


def post(mundo, ruta, uid, **cuerpo):
    return mundo.http.post(f"{U}{ruta}", headers=mundo.con(uid), json=cuerpo)


def get(mundo, ruta, uid):
    return mundo.http.get(f"{U}{ruta}", headers=mundo.con(uid))


def nuevo_trabajador(mundo, eq, quien="almacenero", **kw):
    datos = dict(nombre="Juan Quispe Huamán", documento="45678912", categoria="peon", cuadrilla="Cuadrilla 1", fecha_ingreso="2026-10-19")
    datos.update(kw)
    return post(mundo, f"/obras/{eq['proyecto']}/trabajadores", eq[quien], **datos)


def jornada(mundo, eq, trabajador, quien="almacenero", **kw):
    datos = dict(trabajador_id=trabajador, fecha="2026-10-20", estado="presente", horas_normales="8")
    datos.update(kw)
    return post(mundo, f"/obras/{eq['proyecto']}/jornadas", eq[quien], **datos)


# ─────────────────────────────────────────── trabajadores (HU-62)
def test_el_almacenero_y_el_ssoma_registran_trabajadores(mundo, eq):
    assert nuevo_trabajador(mundo, eq).status_code == 201
    assert nuevo_trabajador(mundo, eq, quien="ssoma", documento="45678913", nombre="Rosa Mamani Cruz").status_code == 201


def test_el_residente_el_admin_y_el_gerente_no_registran_trabajadores(mundo, eq):
    for rol in ("residente", "admin", "gerente"):
        assert nuevo_trabajador(mundo, eq, quien=rol).status_code == 403, rol


def test_un_trabajador_no_se_repite_en_la_misma_obra_pero_si_en_otra(mundo, eq):
    assert nuevo_trabajador(mundo, eq).status_code == 201
    dup = nuevo_trabajador(mundo, eq)
    assert dup.status_code == 409 and dup.json()["codigo"] == "uq_trabajador_proyecto_id_documento"
    otra = mundo.f.obra_completa()
    assert nuevo_trabajador(mundo, otra).status_code == 201


@pytest.mark.parametrize("cambio", [
    {"documento": "123"}, {"documento": "ABCDEFGH"}, {"categoria": "capataz"}, {"nombre": "Jo"}, {"cuadrilla": ""},
    {"fecha_ingreso": "no-es-fecha"}, {"tipo_documento": "LICENCIA"},
])
def test_datos_invalidos_de_trabajador_se_rechazan(mundo, eq, cambio):
    assert nuevo_trabajador(mundo, eq, **cambio).status_code == 422


def test_el_documento_de_un_extranjero_admite_letras(mundo, eq):
    assert nuevo_trabajador(mundo, eq, tipo_documento="CE", documento="CE1234567").status_code == 201


def test_baja_de_trabajador_conserva_sus_jornadas(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    assert jornada(mundo, eq, t).status_code == 201
    b = post(mundo, f"/obras/{eq['proyecto']}/trabajadores/{t}/baja", eq["almacenero"], fecha_salida="2026-10-23")
    assert b.status_code == 200 and b.json()["fecha_salida"] == "2026-10-23"
    assert len(get(mundo, f"/obras/{eq['proyecto']}/jornadas", eq["almacenero"]).json()) == 1
    # después de la baja no se le registra un día posterior…
    assert jornada(mundo, eq, t, fecha="2026-10-27").status_code == 422
    # …y el trabajador sigue existiendo (nada se borra)
    assert [x["id"] for x in get(mundo, f"/obras/{eq['proyecto']}/trabajadores", eq["residente"]).json()] == [t]


def test_baja_de_un_trabajador_de_otra_obra_es_404(mundo, eq):
    otra = mundo.f.obra_completa()
    t = nuevo_trabajador(mundo, otra).json()["id"]
    assert post(mundo, f"/obras/{eq['proyecto']}/trabajadores/{t}/baja", eq["almacenero"], fecha_salida="2026-10-23").status_code == 404


def test_listar_trabajadores_solo_activos(mundo, eq):
    t1 = nuevo_trabajador(mundo, eq).json()["id"]
    nuevo_trabajador(mundo, eq, documento="45678913", nombre="Rosa Mamani Cruz")
    post(mundo, f"/obras/{eq['proyecto']}/trabajadores/{t1}/baja", eq["almacenero"], fecha_salida="2026-10-20")
    todos = get(mundo, f"/obras/{eq['proyecto']}/trabajadores", eq["admin"]).json()
    activos = get(mundo, f"/obras/{eq['proyecto']}/trabajadores?solo_activos=true", eq["admin"]).json()
    assert len(todos) == 2 and len(activos) == 1


# ─────────────────────────────────────────── jornadas
def test_registrar_jornada_el_servidor_decide_categoria_y_periodo(mundo, eq):
    t = nuevo_trabajador(mundo, eq, categoria="operario").json()["id"]
    r = jornada(mundo, eq, t)
    assert r.status_code == 201
    j = r.json()
    assert j["categoria"] == "operario" and j["periodo_id"] and j["registrado_por"] == eq["almacenero"]
    per = mundo.f.c.execute("SELECT numero FROM obra.periodo WHERE id=%s", (j["periodo_id"],)).fetchone()["numero"]
    assert per == 1


def test_el_cliente_no_puede_imponer_categoria_ni_periodo(mundo, eq):
    t = nuevo_trabajador(mundo, eq, categoria="peon").json()["id"]
    for campo, valor in (("categoria", "operario"), ("periodo_id", 99), ("registrado_por", 1), ("fecha_servidor", "2020-01-01T00:00:00Z")):
        assert jornada(mundo, eq, t, **{campo: valor}).status_code == 422, campo


def test_una_sola_jornada_por_trabajador_y_dia(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    assert jornada(mundo, eq, t).status_code == 201
    dup = jornada(mundo, eq, t)
    assert dup.status_code == 409 and dup.json()["codigo"] == "uq_jornada_original"


def test_reglas_de_horas(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    assert jornada(mundo, eq, t, estado="presente", horas_normales="0").status_code == 422          # presente sin horas
    assert jornada(mundo, eq, t, estado="ausente", horas_normales="8").status_code == 422           # ausente con horas
    assert jornada(mundo, eq, t, estado="presente", horas_normales="20", horas_extra="6").status_code == 422   # > 24
    assert jornada(mundo, eq, t, estado="presente", horas_normales="25").status_code == 422
    assert jornada(mundo, eq, t, estado="presente", horas_normales="-1").status_code == 422
    assert jornada(mundo, eq, t, estado="ausente", horas_normales="0").status_code == 201


def test_fechas_fuera_del_plazo_o_antes_del_ingreso_se_rechazan(mundo, eq):
    t = nuevo_trabajador(mundo, eq, fecha_ingreso="2026-10-21").json()["id"]
    assert jornada(mundo, eq, t, fecha="2026-10-20").status_code == 422      # antes de ingresar
    assert jornada(mundo, eq, t, fecha="2026-10-10").status_code == 422      # antes de la obra
    assert jornada(mundo, eq, t, fecha="2027-01-10").status_code == 422      # después del plazo


def test_registrar_jornada_es_idempotente_con_uuid_cliente(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    u = str(uuid.uuid4())
    primera = jornada(mundo, eq, t, uuid_cliente=u)
    reenvio = jornada(mundo, eq, t, uuid_cliente=u)
    assert primera.status_code == 201 and reenvio.status_code == 200      # el reenvío NO crea nada
    assert reenvio.json()["id"] == primera.json()["id"]
    n = mundo.f.c.execute("SELECT count(*) AS n FROM tareo.jornada WHERE trabajador_id=%s", (t,)).fetchone()["n"]
    assert n == 1


def test_un_uuid_reutilizado_para_otra_jornada_es_conflicto(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    u = str(uuid.uuid4())
    assert jornada(mundo, eq, t, uuid_cliente=u).status_code == 201
    r = jornada(mundo, eq, t, uuid_cliente=u, fecha="2026-10-21")
    assert r.status_code == 409 and r.json()["codigo"] == "uuid_reutilizado"


def test_un_uuid_de_otra_obra_no_filtra_su_jornada(mundo, eq):
    otra = mundo.f.obra_completa()
    t_otra = nuevo_trabajador(mundo, otra).json()["id"]
    u = str(uuid.uuid4())
    assert jornada(mundo, otra, t_otra, uuid_cliente=u).status_code == 201
    t = nuevo_trabajador(mundo, eq).json()["id"]
    r = jornada(mundo, eq, t, uuid_cliente=u)
    assert r.status_code == 409 and "id" not in r.json()


def test_no_se_registra_jornada_de_un_trabajador_de_otra_obra(mundo, eq):
    otra = mundo.f.obra_completa()
    t_otra = nuevo_trabajador(mundo, otra).json()["id"]
    r = jornada(mundo, eq, t_otra)
    assert r.status_code in (409, 422)      # la clave foránea compuesta (trabajador, obra) lo impide


# ─────────────────────────────────────────── correcciones: el libro no se edita
def test_corregir_inserta_otra_jornada_y_deja_la_original_intacta(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    orig = jornada(mundo, eq, t, horas_normales="8").json()
    c = post(mundo, f"/obras/{eq['proyecto']}/jornadas/{orig['id']}/correccion", eq["almacenero"],
             estado="presente", horas_normales="6", motivo="Salió temprano por lluvia")
    assert c.status_code == 201
    cj = c.json()
    assert cj["corrige_a_id"] == orig["id"] and cj["motivo_correccion"] == "Salió temprano por lluvia" and cj["horas_normales"] == "6.00"
    guardada = mundo.f.c.execute("SELECT horas_normales FROM tareo.jornada WHERE id=%s", (orig["id"],)).fetchone()
    assert str(guardada["horas_normales"]) == "8.00"                      # la original no se tocó
    vigentes = get(mundo, f"/obras/{eq['proyecto']}/jornadas?trabajador_id={t}", eq["residente"]).json()
    assert [j["id"] for j in vigentes] == [cj["id"]]                      # solo la vigente


def test_la_correccion_exige_motivo_y_solo_se_corrige_una_vez(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    orig = jornada(mundo, eq, t).json()
    ruta = f"/obras/{eq['proyecto']}/jornadas/{orig['id']}/correccion"
    assert post(mundo, ruta, eq["almacenero"], estado="presente", horas_normales="6", motivo="").status_code == 422
    assert post(mundo, ruta, eq["almacenero"], estado="presente", horas_normales="6", motivo="Se digitó mal").status_code == 201
    dos = post(mundo, ruta, eq["almacenero"], estado="presente", horas_normales="7", motivo="Otra vez")
    assert dos.status_code == 409 and dos.json()["codigo"] == "ya_corregida"


def test_se_puede_corregir_la_correccion_encadenando(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    j1 = jornada(mundo, eq, t).json()
    j2 = post(mundo, f"/obras/{eq['proyecto']}/jornadas/{j1['id']}/correccion", eq["ssoma"], estado="presente", horas_normales="7", motivo="Primera corrección").json()
    j3 = post(mundo, f"/obras/{eq['proyecto']}/jornadas/{j2['id']}/correccion", eq["ssoma"], estado="presente", horas_normales="6.5", motivo="Segunda corrección")
    assert j3.status_code == 201
    vigentes = get(mundo, f"/obras/{eq['proyecto']}/jornadas?trabajador_id={t}", eq["admin"]).json()
    assert [j["id"] for j in vigentes] == [j3.json()["id"]]


def test_corregir_una_jornada_de_otra_obra_es_404(mundo, eq):
    otra = mundo.f.obra_completa()
    t = nuevo_trabajador(mundo, otra).json()["id"]
    j = jornada(mundo, otra, t).json()
    r = post(mundo, f"/obras/{eq['proyecto']}/jornadas/{j['id']}/correccion", eq["almacenero"], estado="presente", horas_normales="6", motivo="Intento ajeno")
    assert r.status_code == 404


def test_no_existe_forma_de_editar_ni_borrar_una_jornada(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    j = jornada(mundo, eq, t).json()
    for metodo in ("put", "patch", "delete"):
        r = getattr(mundo.http, metodo)(f"{U}/obras/{eq['proyecto']}/jornadas/{j['id']}", headers=mundo.con(eq["almacenero"]))
        assert r.status_code in (404, 405)      # no hay ruta que lo permita


def test_un_periodo_cerrado_no_admite_jornadas_nuevas_pero_si_correcciones(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    j = jornada(mundo, eq, t).json()
    mundo.conn.exec_driver_sql("UPDATE obra.periodo SET estado='cerrado', cerrado_en=now(), cerrado_por=%s WHERE id=%s", (eq["admin"], j["periodo_id"]))
    assert jornada(mundo, eq, t, fecha="2026-10-21").status_code == 422
    c = post(mundo, f"/obras/{eq['proyecto']}/jornadas/{j['id']}/correccion", eq["almacenero"], estado="presente", horas_normales="7", motivo="Ajuste tras el cierre")
    assert c.status_code == 201


# ─────────────────────────────────────────── consultas y horas acumuladas (HU-64)
def test_horas_acumuladas_cuentan_solo_lo_vigente(mundo, eq):
    t = nuevo_trabajador(mundo, eq, categoria="oficial").json()["id"]
    a = jornada(mundo, eq, t, fecha="2026-10-20", horas_normales="8").json()
    jornada(mundo, eq, t, fecha="2026-10-21", horas_normales="8", horas_extra="2")
    jornada(mundo, eq, t, fecha="2026-10-22", estado="ausente", horas_normales="0")
    post(mundo, f"/obras/{eq['proyecto']}/jornadas/{a['id']}/correccion", eq["almacenero"], estado="presente", horas_normales="6", motivo="Se fue antes")
    h = get(mundo, f"/obras/{eq['proyecto']}/horas-acumuladas", eq["gerente"]).json()
    assert len(h) == 1
    x = h[0]
    assert (x["periodo_numero"], x["categoria"], x["dias_presente"], x["dias_ausente"]) == (1, "oficial", 2, 1)
    assert float(x["horas_normales"]) == 14.0 and float(x["horas_extra"]) == 2.0       # 6 corregidas + 8, no 8 + 8 + 8


def test_horas_acumuladas_por_periodo_y_visibles_para_todos_los_roles(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    jornada(mundo, eq, t, fecha="2026-10-20")
    jornada(mundo, eq, t, fecha="2026-10-27")                                # periodo 2
    todos = get(mundo, f"/obras/{eq['proyecto']}/horas-acumuladas", eq["almacenero"]).json()
    assert sorted(x["periodo_numero"] for x in todos) == [1, 2]
    p2 = [x["periodo_id"] for x in todos if x["periodo_numero"] == 2][0]
    solo = get(mundo, f"/obras/{eq['proyecto']}/horas-acumuladas?periodo_id={p2}", eq["ssoma"]).json()
    assert [x["periodo_numero"] for x in solo] == [2]
    for rol in ("almacenero", "ssoma", "residente", "admin", "gerente"):
        assert get(mundo, f"/obras/{eq['proyecto']}/horas-acumuladas", eq[rol]).status_code == 200


def test_la_categoria_congelada_no_cambia_el_pasado(mundo, eq):
    t = nuevo_trabajador(mundo, eq, categoria="peon").json()["id"]
    antes = jornada(mundo, eq, t, fecha="2026-10-20").json()
    mundo.conn.exec_driver_sql("UPDATE tareo.trabajador SET categoria='operario' WHERE id=%s", (t,))     # ascenso
    despues = jornada(mundo, eq, t, fecha="2026-10-21").json()
    assert antes["categoria"] == "peon" and despues["categoria"] == "operario"
    h = {x["categoria"] for x in get(mundo, f"/obras/{eq['proyecto']}/horas-acumuladas", eq["admin"]).json()}
    assert h == {"peon", "operario"}


def test_listar_jornadas_filtra_por_fecha(mundo, eq):
    t = nuevo_trabajador(mundo, eq).json()["id"]
    jornada(mundo, eq, t, fecha="2026-10-20")
    jornada(mundo, eq, t, fecha="2026-10-21")
    r = get(mundo, f"/obras/{eq['proyecto']}/jornadas?fecha=2026-10-21", eq["residente"]).json()
    assert [j["fecha"] for j in r] == ["2026-10-21"]
    assert get(mundo, f"/obras/{eq['proyecto']}/jornadas?fecha=ayer", eq["residente"]).status_code == 422


def test_el_gerente_no_ve_jornadas_ni_trabajadores_solo_horas(mundo, eq):
    assert get(mundo, f"/obras/{eq['proyecto']}/jornadas", eq["gerente"]).status_code == 403
    assert get(mundo, f"/obras/{eq['proyecto']}/trabajadores", eq["gerente"]).status_code == 403
    assert get(mundo, f"/obras/{eq['proyecto']}/horas-acumuladas", eq["gerente"]).status_code == 200
