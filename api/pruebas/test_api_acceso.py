"""Control de acceso de la API: se verifica en el SERVIDOR, contra la base, en cada petición (regla 9).

La prueba central recorre TODAS las rutas del sistema: ninguna puede quedar sin declarar su acción, y para cada
una el servidor debe rechazar con 403 a todo rol que la matriz (docs/permisos.md) no admita — y no rechazar a los que sí.
"""
from __future__ import annotations

import time
from datetime import datetime, timedelta, timezone

import jwt
import pytest

from app.config import cargar
from app.main import crear_app
from app.permisos import ACCIONES
from app.seguridad import ALGORITMO, hashear

ROLES = ("almacenero", "ssoma", "residente", "administrador", "gerente")
PUBLICAS = {("/api/v1/auth/login", "POST"), ("/salud", "GET")}
SOLO_AUTENTICADO = {("/api/v1/auth/yo", "GET"), ("/api/v1/auth/cambiar-clave", "POST"), ("/api/v1/obras", "GET")}


def _rutas():
    """(metodo, ruta, accion) de cada endpoint. FastAPI reciente monta los routers de forma perezosa: se recorren."""
    app = crear_app()
    salida = []
    for incl in app.routes:
        router = getattr(incl, "original_router", None)
        if router is None:
            continue
        prefijo = incl.include_context.prefix if hasattr(incl, "include_context") else "/api/v1"
        for r in router.routes:
            accion = _accion_de(r.dependant)
            for m in r.methods:
                salida.append((m, f"{prefijo}{r.path}", accion))
    return salida


def _accion_de(dep) -> str | None:
    if getattr(dep.call, "accion", None):
        return dep.call.accion
    for sub in dep.dependencies:
        a = _accion_de(sub)
        if a:
            return a
    return None


RUTAS = _rutas()


def test_se_encontraron_todas_las_rutas():
    assert len(RUTAS) >= 32


def test_ninguna_ruta_queda_sin_declarar_su_accion():
    for metodo, ruta, accion in RUTAS:
        if (ruta, metodo) in PUBLICAS:
            continue
        assert accion is not None, f"{metodo} {ruta} no declara qué rol y alcance exige"


def test_toda_accion_de_la_matriz_esta_usada_por_alguna_ruta():
    usadas = {a for _, _, a in RUTAS}
    sin_usar = set(ACCIONES) - usadas
    assert not sin_usar, f"acciones de la matriz que ningún endpoint exige: {sorted(sin_usar)}"


def _llenar(ruta: str, obra_id: int) -> str:
    import re
    ruta = ruta.replace("{obra_id}", str(obra_id))
    return re.sub(r"\{[^}]+\}", "999999", ruta)


def _llamar(mundo, metodo: str, ruta: str, headers: dict):
    f = mundo.http.get if metodo == "GET" else mundo.http.post
    return f(ruta, headers=headers) if metodo == "GET" else f(ruta, headers=headers, json={})


@pytest.fixture
def equipo(mundo):
    return mundo.f.obra_completa()


def test_la_matriz_se_cumple_en_cada_endpoint_para_cada_rol(mundo, equipo):
    """Roles no admitidos → 403. Roles admitidos → jamás 403 (llegan a validar el cuerpo o a buscar el dato)."""
    revisados = 0
    for metodo, ruta, accion in RUTAS:
        if accion is None or accion == "autenticado":
            continue
        permitidos = ACCIONES[accion]
        for rol in ROLES:
            r = _llamar(mundo, metodo, _llenar(ruta, equipo["proyecto"]), mundo.con(equipo["admin" if rol == "administrador" else rol]))
            if rol in permitidos:
                assert r.status_code != 403, f"{metodo} {ruta} rechazó a {rol}, que la matriz admite ({accion})"
            else:
                assert r.status_code == 403, f"{metodo} {ruta} dejó pasar a {rol} ({accion}): {r.status_code} {r.text}"
                assert r.json()["codigo"] == "sin_permiso"
            revisados += 1
    assert revisados >= 130


def test_una_persona_sin_asignacion_en_la_obra_recibe_403_aunque_tenga_rol_en_otra(mundo, equipo):
    otra = mundo.f.obra_completa()          # otra obra con su propio administrador
    for metodo, ruta, accion in RUTAS:
        if accion in (None, "autenticado") or "{obra_id}" not in ruta:
            continue
        r = _llamar(mundo, metodo, _llenar(ruta, equipo["proyecto"]), mundo.con(otra["admin"]))
        assert r.status_code == 403, f"{metodo} {ruta}: el administrador de OTRA obra pasó ({r.status_code})"


def test_las_obras_solo_listan_las_propias(mundo, equipo):
    mundo.f.obra_completa()
    r = mundo.http.get("/api/v1/obras", headers=mundo.con(equipo["residente"]))
    assert [o["id"] for o in r.json()] == [equipo["proyecto"]]


def test_una_asignacion_retirada_pierde_el_acceso_en_la_siguiente_peticion(mundo, equipo):
    h = mundo.con(equipo["almacenero"])
    assert mundo.http.get(f"/api/v1/obras/{equipo['proyecto']}/almacenes", headers=h).status_code == 200
    mundo.conn.exec_driver_sql(
        "UPDATE obra.asignacion SET fecha_retiro = now(), retirado_por = %s WHERE usuario_id = %s",
        (equipo["admin"], equipo["almacenero"]),
    )
    assert mundo.http.get(f"/api/v1/obras/{equipo['proyecto']}/almacenes", headers=h).status_code == 403


def test_un_usuario_dado_de_baja_deja_de_entrar_con_su_token_vigente(mundo, equipo):
    h = mundo.con(equipo["residente"])
    assert mundo.http.get("/api/v1/auth/yo", headers=h).status_code == 200
    mundo.conn.exec_driver_sql("UPDATE obra.usuario SET estado='baja', fecha_baja=now() WHERE id=%s", (equipo["residente"],))
    r = mundo.http.get("/api/v1/auth/yo", headers=h)
    assert r.status_code == 401 and r.json()["codigo"] == "no_autenticado"


# ───────────────────────────────────────────── tokens
def _token(sub, secreto=None, vence=None, alg=ALGORITMO, extra=None):
    cfg = cargar()
    carga = {"sub": str(sub), "exp": vence or datetime.now(timezone.utc) + timedelta(hours=1), **(extra or {})}
    return jwt.encode(carga, secreto or cfg.jwt_secret, algorithm=alg)


@pytest.mark.parametrize("encabezado", [None, "", "Bearer", "Bearer basura", "Token abc", "Basic YWJjOmFiYw=="])
def test_sin_token_o_con_basura_es_401(mundo, encabezado):
    h = {} if encabezado is None else {"Authorization": encabezado}
    r = mundo.http.get("/api/v1/auth/yo", headers=h)
    assert r.status_code == 401 and r.json()["codigo"] == "no_autenticado"


def test_token_vencido_o_firmado_con_otra_clave_o_sin_firma_es_401(mundo, equipo):
    uid = equipo["residente"]
    malos = [
        _token(uid, vence=datetime.now(timezone.utc) - timedelta(seconds=5)),
        _token(uid, secreto="otra-clave-distinta-de-treinta-y-dos-caracteres-o-mas"),
        jwt.encode({"sub": str(uid), "exp": int(time.time()) + 3600}, key=None, algorithm="none"),
    ]
    for t in malos:
        r = mundo.http.get("/api/v1/auth/yo", headers={"Authorization": f"Bearer {t}"})
        assert r.status_code == 401, t


def test_el_token_no_otorga_rol_el_servidor_lo_consulta(mundo, equipo):
    """Aunque el token afirme ser administrador, manda la asignación vigente en la base."""
    t = _token(equipo["almacenero"], extra={"rol": "administrador", "obras": [equipo["proyecto"]]})
    r = mundo.http.post(
        f"/api/v1/obras/{equipo['proyecto']}/asignaciones", headers={"Authorization": f"Bearer {t}"},
        json={"usuario_id": equipo["almacenero"], "rol": "administrador"},
    )
    assert r.status_code == 403


# ───────────────────────────────────────────── inicio de sesión (HU-12)
def _usuario_con_clave(mundo, clave="Clave-Segura-2026", usuario=None):
    uid = mundo.f.usuario(usuario)
    mundo.conn.exec_driver_sql("UPDATE obra.usuario SET hash_clave=%s WHERE id=%s", (hashear(clave), uid))
    nombre = mundo.f.c.execute("SELECT usuario FROM obra.usuario WHERE id=%s", (uid,)).fetchone()["usuario"]
    return uid, nombre


def _login(mundo, usuario, clave):
    return mundo.http.post("/api/v1/auth/login", json={"usuario": usuario, "clave": clave})


def test_login_correcto_devuelve_token_y_las_obras_con_su_rol(mundo):
    eq = mundo.f.obra_completa()
    uid, nombre = _usuario_con_clave(mundo)
    mundo.f.asignar(uid, eq["proyecto"], "almacenero", eq["admin"])
    r = _login(mundo, nombre, "Clave-Segura-2026")
    assert r.status_code == 200
    cuerpo = r.json()
    assert cuerpo["usuario"]["id"] == uid and cuerpo["obras"] == [
        {"id": eq["proyecto"], "nombre": cuerpo["obras"][0]["nombre"], "rol": "almacenero"}
    ]
    assert mundo.http.get("/api/v1/auth/yo", headers={"Authorization": f"Bearer {cuerpo['token']}"}).status_code == 200
    assert "hash" not in r.text and "clave" not in r.text


def test_usuario_inexistente_y_clave_mala_dan_exactamente_la_misma_respuesta(mundo):
    _, nombre = _usuario_con_clave(mundo)
    a = _login(mundo, nombre, "mala-clave-1234")
    b = _login(mundo, "no.existe", "mala-clave-1234")
    assert a.status_code == b.status_code == 401
    assert a.json() == b.json() and a.json()["codigo"] == "credenciales_invalidas"


def test_el_usuario_no_distingue_mayusculas(mundo):
    _, nombre = _usuario_con_clave(mundo, usuario="Maria.Lopez")
    assert _login(mundo, "MARIA.LOPEZ", "Clave-Segura-2026").status_code == 200


def test_cinco_intentos_fallidos_bloquean_aun_con_la_clave_correcta_y_el_bloqueo_expira(mundo):
    uid, nombre = _usuario_con_clave(mundo)
    for _ in range(5):
        assert _login(mundo, nombre, "incorrecta-1234").status_code == 401
    r = _login(mundo, nombre, "Clave-Segura-2026")
    assert r.status_code == 429 and r.json()["codigo"] == "cuenta_bloqueada"
    mundo.conn.exec_driver_sql("UPDATE obra.usuario SET bloqueado_hasta = now() - interval '1 minute' WHERE id=%s", (uid,))
    assert _login(mundo, nombre, "Clave-Segura-2026").status_code == 200


def test_un_login_correcto_reinicia_el_contador(mundo):
    uid, nombre = _usuario_con_clave(mundo)
    for _ in range(4):
        _login(mundo, nombre, "incorrecta-1234")
    assert _login(mundo, nombre, "Clave-Segura-2026").status_code == 200
    n = mundo.f.c.execute("SELECT intentos_fallidos FROM obra.usuario WHERE id=%s", (uid,)).fetchone()["intentos_fallidos"]
    assert n == 0
    for _ in range(4):                       # otros cuatro: todavía no bloquea
        _login(mundo, nombre, "incorrecta-1234")
    assert _login(mundo, nombre, "Clave-Segura-2026").status_code == 200


def test_un_usuario_de_baja_no_puede_iniciar_sesion(mundo):
    uid, nombre = _usuario_con_clave(mundo)
    mundo.conn.exec_driver_sql("UPDATE obra.usuario SET estado='baja', fecha_baja=now() WHERE id=%s", (uid,))
    assert _login(mundo, nombre, "Clave-Segura-2026").status_code == 401


def test_cambiar_clave(mundo):
    uid, nombre = _usuario_con_clave(mundo)
    h = mundo.con(uid)
    c = lambda a, n: mundo.http.post("/api/v1/auth/cambiar-clave", headers=h, json={"clave_actual": a, "clave_nueva": n})
    assert c("equivocada-123", "Nueva-Clave-2026").status_code == 403
    assert c("Clave-Segura-2026", "corta").json()["codigo"] == "clave_debil"
    assert c("Clave-Segura-2026", "todominusculas1").json()["codigo"] == "clave_debil"
    assert c("Clave-Segura-2026", "Nueva-Clave-2026").status_code == 204
    assert _login(mundo, nombre, "Clave-Segura-2026").status_code == 401
    assert _login(mundo, nombre, "Nueva-Clave-2026").status_code == 200


def test_la_clave_se_guarda_con_argon2_nunca_en_claro(mundo):
    eq = mundo.f.obra_completa()
    r = mundo.http.post("/api/v1/usuarios", headers=mundo.con(eq["admin"]),
                        json={"nombre": "Ana Ruiz", "usuario": "ana.ruiz", "clave": "Clave-Segura-2026"})
    assert r.status_code == 201
    h = mundo.f.c.execute("SELECT hash_clave FROM obra.usuario WHERE usuario='ana.ruiz'").fetchone()["hash_clave"]
    assert h.startswith("$argon2id$") and "Clave-Segura-2026" not in h
    assert "hash_clave" not in r.text
