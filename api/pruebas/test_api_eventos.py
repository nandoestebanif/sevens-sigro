"""Outbox + relevo + notificaciones: del hecho en la API hasta la campana de la persona."""
from __future__ import annotations

import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import psycopg
import pytest
from psycopg.rows import dict_row
from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session

from app import relay
from pruebas.conftest import CHECK, RESTRICT, SIN_PERMISO, alembic, dsn, recrear_base, eliminar_base, rechaza, url_sqlalchemy

U = "/api/v1"


@pytest.fixture
def eq(mundo):
    return mundo.f.obra_completa()


def post(mundo, ruta, uid, **cuerpo):
    return mundo.http.post(f"{U}{ruta}", headers=mundo.con(uid), json=cuerpo)


def get(mundo, ruta, uid):
    return mundo.http.get(f"{U}{ruta}", headers=mundo.con(uid))


def eventos_de(mundo, tipo=None):
    sql = "SELECT id, tipo, proyecto_id, payload, enviado_en, intentos FROM outbox.evento"
    sql += " WHERE tipo = %s ORDER BY id" if tipo else " ORDER BY id"
    return mundo.f.c.execute(sql, (tipo,) if tipo else ()).fetchall()


def pedir_dispositivo(mundo, eq, rol="almacenero", ident="android-AAAA-1111"):
    return post(mundo, f"/obras/{eq['proyecto']}/dispositivos", eq[rol], identificador=ident)


# ══════════════════════════════════════════ el motor protege la bandeja
def test_el_evento_no_se_edita_ni_se_borra_ni_siquiera_el_dueno(mundo, eq):
    pedir_dispositivo(mundo, eq)
    c = mundo.f.c
    e = eventos_de(mundo)[0]["id"]
    with rechaza(mundo.conn.connection.driver_connection, RESTRICT, "SIGRO-O1"):
        c.execute("UPDATE outbox.evento SET tipo = 'otro.tipo' WHERE id = %s", (e,))
    with rechaza(mundo.conn.connection.driver_connection, RESTRICT, "SIGRO-O1"):
        c.execute("UPDATE outbox.evento SET payload = '{\"destinatarios\":[1]}' WHERE id = %s", (e,))
    with rechaza(mundo.conn.connection.driver_connection, RESTRICT, "SIGRO-R10"):
        c.execute("DELETE FROM outbox.evento WHERE id = %s", (e,))
    with rechaza(mundo.conn.connection.driver_connection, RESTRICT):
        c.execute("TRUNCATE outbox.evento")


def test_la_aplicacion_solo_puede_anotar_el_resultado_de_la_entrega(mundo, eq):
    pedir_dispositivo(mundo, eq)
    e = eventos_de(mundo)[0]["id"]
    raw = mundo.conn.connection.driver_connection
    mundo.conn.exec_driver_sql("SET ROLE sigro_app")
    with rechaza(raw, SIN_PERMISO):
        raw.execute("UPDATE outbox.evento SET payload = '{}' WHERE id = %s", (e,))
    with rechaza(raw, SIN_PERMISO):
        raw.execute("DELETE FROM outbox.evento WHERE id = %s", (e,))
    raw.execute("UPDATE outbox.evento SET intentos = 1, ultimo_error = 'x' WHERE id = %s", (e,))      # esto sí
    mundo.conn.exec_driver_sql("RESET ROLE")


def test_una_entrega_confirmada_no_se_desmarca_y_los_intentos_no_retroceden(mundo, eq):
    pedir_dispositivo(mundo, eq)
    e = eventos_de(mundo)[0]["id"]
    raw = mundo.conn.connection.driver_connection
    raw.execute("UPDATE outbox.evento SET enviado_en = now(), intentos = 2 WHERE id = %s", (e,))
    with rechaza(raw, RESTRICT, "SIGRO-O2"):
        raw.execute("UPDATE outbox.evento SET enviado_en = NULL WHERE id = %s", (e,))
    with rechaza(raw, RESTRICT, "SIGRO-O3"):
        raw.execute("UPDATE outbox.evento SET intentos = 1 WHERE id = %s", (e,))


def test_el_evento_exige_destinatarios_y_no_se_marca_entregado_sin_haberlo_intentado(mundo, eq):
    raw = mundo.conn.connection.driver_connection
    base = "INSERT INTO outbox.evento (tipo, proyecto_id, payload) VALUES (%s, %s, %s)"
    for payload in ('{"destinatarios": []}', '{"titulo": "x"}', '{"destinatarios": "no-es-lista"}', '[1, 2]'):
        with rechaza(raw, CHECK):
            raw.execute(base, ("compra.anulada", eq["proyecto"], payload))
    with rechaza(raw, CHECK):
        raw.execute(base, ("TIPO MALO", eq["proyecto"], '{"destinatarios": [1]}'))
    with rechaza(raw, CHECK):
        raw.execute("INSERT INTO outbox.evento (tipo, proyecto_id, payload, enviado_en, intentos) VALUES "
                    "('compra.anulada', %s, '{\"destinatarios\": [1]}', now(), 0)", (eq["proyecto"],))


# ══════════════════════════════════════════ la API publica en la misma transacción
def test_pedir_un_dispositivo_avisa_a_los_administradores_pero_no_a_quien_lo_pidio(mundo, eq):
    otro_admin = mundo.f.usuario()
    mundo.f.asignar(otro_admin, eq["proyecto"], "administrador", eq["admin"])
    assert pedir_dispositivo(mundo, eq).status_code == 201
    ev = eventos_de(mundo, "dispositivo.solicitado")
    assert len(ev) == 1
    p = ev[0]["payload"]
    assert p["destinatarios"] == sorted([eq["admin"], otro_admin]) and eq["almacenero"] not in p["destinatarios"]
    assert "pidió autorizar su celular" in p["mensaje"] and p["datos"]["usuario_id"] == eq["almacenero"]
    assert ev[0]["enviado_en"] is None and ev[0]["proyecto_id"] == eq["proyecto"]


def test_autorizar_y_revocar_avisan_a_quien_pidio_el_dispositivo(mundo, eq):
    d = pedir_dispositivo(mundo, eq).json()["id"]
    post(mundo, f"/obras/{eq['proyecto']}/dispositivos/{d}/autorizar", eq["admin"])
    post(mundo, f"/obras/{eq['proyecto']}/dispositivos/{d}/revocar", eq["admin"], motivo="Celular perdido")
    aut = eventos_de(mundo, "dispositivo.autorizado")[0]["payload"]
    rev = eventos_de(mundo, "dispositivo.revocado")[0]["payload"]
    assert aut["destinatarios"] == [eq["almacenero"]] and rev["destinatarios"] == [eq["almacenero"]]
    assert "Celular perdido" in rev["mensaje"]


def test_anular_una_compra_avisa_a_admin_y_residente_excepto_a_quien_la_anula(mundo, eq):
    mundo.f.insumo("0221000000", unidad="bls")
    prov = post(mundo, "/proveedores", eq["residente"], ruc="20606456892", razon_social="SEVENS INGENIEROS SAC").json()
    c = post(mundo, f"/obras/{eq['proyecto']}/compras", eq["residente"], proveedor_id=prov["id"], tipo_comprobante="factura",
             numero_comprobante="F001-4512", fecha="2026-10-18",
             lineas=[{"insumo_codigo": "0221000000", "cantidad": "10", "unidad_compra": "bls", "precio_unitario": "29.23"}]).json()
    assert eventos_de(mundo, "compra.anulada") == []
    post(mundo, f"/obras/{eq['proyecto']}/compras/{c['id']}/anular", eq["residente"], motivo="Proveedor equivocado")
    p = eventos_de(mundo, "compra.anulada")[0]["payload"]
    assert p["destinatarios"] == [eq["admin"]]                   # el residente la anuló: no se avisa a sí mismo
    assert "F001-4512" in p["mensaje"] and "Proveedor equivocado" in p["mensaje"] and p["datos"] == {"compra_id": c["id"]}


def test_emitir_una_solicitud_avisa_a_los_administradores(mundo, eq):
    mundo.f.insumo("0221000000", unidad="bls")
    r = post(mundo, f"/obras/{eq['proyecto']}/solicitudes", eq["residente"],
             lineas=[{"insumo_codigo": "0221000000", "cantidad": "400", "unidad_compra": "bls"}])
    assert r.status_code == 201
    p = eventos_de(mundo, "solicitud.emitida")[0]["payload"]
    assert p["destinatarios"] == [eq["admin"]] and "N.º 1" in p["mensaje"]


def test_una_peticion_rechazada_no_deja_eventos(mundo, eq):
    pedir_dispositivo(mundo, eq, ident="android-AAAA-1111")
    antes = len(eventos_de(mundo))
    assert pedir_dispositivo(mundo, eq, ident="corto").status_code == 422
    assert post(mundo, f"/obras/{eq['proyecto']}/solicitudes", eq["residente"], lineas=[]).status_code == 422
    assert post(mundo, f"/obras/{eq['proyecto']}/compras/999999/anular", eq["residente"], motivo="No existe esta").status_code == 404
    assert post(mundo, f"/obras/{eq['proyecto']}/dispositivos/999999/autorizar", eq["admin"]).status_code == 404
    assert len(eventos_de(mundo)) == antes


def test_sin_destinatarios_no_se_publica_nada(mundo):
    from app import eventos
    eq = mundo.f.obra_con_equipo()                    # aquí el administrador NO está asignado a la obra
    mundo.conn.exec_driver_sql("SET ROLE sigro_app")
    with Session(bind=mundo.conn, join_transaction_mode="create_savepoint") as s:
        assert eventos.publicar(s, "compra.anulada", eq["proyecto"], [], "t", "m") is None
        assert eventos.destinatarios_con_rol(s, eq["proyecto"], ["administrador"]) == []
    mundo.conn.exec_driver_sql("RESET ROLE")
    assert eventos_de(mundo) == []


def test_los_usuarios_de_baja_no_reciben_avisos(mundo, eq):
    mundo.conn.exec_driver_sql("UPDATE obra.usuario SET estado='baja', fecha_baja=now() WHERE id=%s", (eq["admin"],))
    pedir_dispositivo(mundo, eq)
    assert eventos_de(mundo, "dispositivo.solicitado") == []          # su único destinatario estaba de baja


# ══════════════════════════════════════════ el relevo
def abrir(mundo):
    mundo.conn.exec_driver_sql("SET ROLE sigro_app")
    return lambda: Session(bind=mundo.conn, join_transaction_mode="create_savepoint")


def estado(mundo, eid):
    return mundo.f.c.execute("SELECT enviado_en, intentos, ultimo_error, siguiente_intento > now() AS en_espera "
                             "FROM outbox.evento WHERE id = %s", (eid,)).fetchone()


def test_el_relevo_entrega_y_marca_lo_enviado_una_sola_vez(mundo, eq):
    pedir_dispositivo(mundo, eq)
    enviados = []
    r = relay.procesar(abrir(mundo), lambda ev: enviados.append(ev["id"]))
    assert (r.enviados, r.fallidos) == (1, 0) and len(enviados) == 1
    e = estado(mundo, enviados[0])
    assert e["enviado_en"] is not None and e["intentos"] == 1 and e["ultimo_error"] is None
    assert relay.procesar(abrir(mundo), lambda ev: enviados.append(ev["id"])).enviados == 0      # ya no está pendiente
    assert len(enviados) == 1


def test_un_fallo_deja_el_evento_pendiente_con_espera_y_no_frena_a_los_demas(mundo, eq):
    pedir_dispositivo(mundo, eq, "almacenero")
    pedir_dispositivo(mundo, eq, "ssoma", "android-BBBB-2222")
    primero, segundo = [e["id"] for e in eventos_de(mundo)]

    def se_cae_con_el_primero(ev):
        if ev["id"] == primero:
            raise ConnectionError("Notificaciones no responde")

    r = relay.procesar(abrir(mundo), se_cae_con_el_primero)
    assert (r.enviados, r.fallidos) == (1, 1)
    a, b = estado(mundo, primero), estado(mundo, segundo)
    assert a["enviado_en"] is None and a["intentos"] == 1 and "ConnectionError" in a["ultimo_error"] and a["en_espera"]
    assert b["enviado_en"] is not None
    # mientras espera, el relevo no lo vuelve a intentar
    assert relay.procesar(abrir(mundo), lambda ev: pytest.fail("no debía reintentar todavía")).fallidos == 0


def test_cuando_toca_el_reintento_se_entrega_y_se_limpia_el_error(mundo, eq):
    pedir_dispositivo(mundo, eq)
    e = eventos_de(mundo)[0]["id"]

    def falla(ev):
        raise TimeoutError("lento")

    relay.procesar(abrir(mundo), falla)
    mundo.conn.exec_driver_sql("RESET ROLE")
    mundo.conn.exec_driver_sql("UPDATE outbox.evento SET siguiente_intento = now() - interval '1 second' WHERE id = %s", (e,))
    r = relay.procesar(abrir(mundo), lambda ev: None)
    assert r.enviados == 1
    est = estado(mundo, e)
    assert est["enviado_en"] is not None and est["intentos"] == 2 and est["ultimo_error"] is None


def test_la_espera_crece_hasta_un_tope():
    assert [relay.espera(n) for n in (1, 2, 3, 4, 5)] == [5, 10, 20, 40, 80]
    assert relay.espera(30) == relay.ESPERA_MAX_S == 3600


def test_el_relevo_respeta_el_limite_por_vuelta(mundo, eq):
    for i in range(4):
        pedir_dispositivo(mundo, eq, "almacenero", f"android-{i:04d}-ABCD") if i == 0 else None
    mundo.conn.exec_driver_sql("RESET ROLE")
    for i in range(4):
        mundo.conn.exec_driver_sql(
            "INSERT INTO outbox.evento (tipo, proyecto_id, payload) VALUES ('compra.anulada', %s, %s)",
            (eq["proyecto"], '{"destinatarios": [1], "titulo": "t", "mensaje": "m", "datos": {}}'),
        )
    total = len(eventos_de(mundo))
    r = relay.procesar(abrir(mundo), lambda ev: None, limite=3)
    assert r.enviados == 3
    assert relay.procesar(abrir(mundo), lambda ev: None).enviados == total - 3


def test_dos_relevos_a_la_vez_nunca_entregan_el_mismo_evento_dos_veces():
    """Concurrencia real (FOR UPDATE SKIP LOCKED): 30 eventos, 4 relevos con commits de verdad."""
    base = "sigro_relevo"
    recrear_base(base)
    try:
        alembic(base, "upgrade", "head")
        hash_ = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"
        with psycopg.connect(dsn(base), row_factory=dict_row) as c:
            u = c.execute("INSERT INTO obra.usuario (nombre,usuario,hash_clave) VALUES ('A','a1234',%s) RETURNING id", (hash_,)).fetchone()["id"]
            p = c.execute("INSERT INTO obra.proyecto (nombre,fecha_inicio,fecha_fin,creado_por) VALUES ('O','2026-10-19','2026-12-06',%s) RETURNING id", (u,)).fetchone()["id"]
            for _ in range(30):
                c.execute("INSERT INTO outbox.evento (tipo, proyecto_id, payload) VALUES ('compra.anulada', %s, %s)",
                          (p, '{"destinatarios": [1], "titulo": "t", "mensaje": "m", "datos": {}}'))
            c.commit()
        motor = create_engine(url_sqlalchemy(base), pool_size=8)
        with motor.connect() as c0:
            c0.execute(text("GRANT sigro_app TO CURRENT_USER"))
            c0.commit()
        entregados: list[int] = []

        def lento(ev):
            time.sleep(0.01)                  # da tiempo a que los otros relevos choquen con el mismo evento
            entregados.append(ev["id"])

        def trabajar(_):
            def abrir_():
                s = Session(motor)
                s.execute(text("SET ROLE sigro_app"))
                return s
            return relay.procesar(abrir_, lento)

        with ThreadPoolExecutor(max_workers=4) as ex:
            resultados = list(ex.map(trabajar, range(4)))
        assert sum(r.enviados for r in resultados) == 30
        assert sorted(entregados) == sorted(set(entregados)) and len(entregados) == 30     # ni uno repetido, ni uno perdido
        assert sum(1 for r in resultados if r.enviados > 0) >= 2                            # y de verdad trabajaron en paralelo
        motor.dispose()
    finally:
        eliminar_base(base)


# ══════════════════════════════════════════ proxy de notificaciones (lo que consume la web)
class _Resp:
    def __init__(self, estado, cuerpo=None):
        self.status_code, self._c = estado, cuerpo

    def json(self):
        return self._c


def test_las_notificaciones_se_piden_con_el_usuario_del_token_nunca_el_de_la_url(mundo, eq, monkeypatch):
    visto = {}

    def falso(metodo, ruta, **params):
        visto.update(metodo=metodo, ruta=ruta, params=params)
        return _Resp(200, {"no_leidas": 0, "notificaciones": []})

    monkeypatch.setattr("app.modulos.notificaciones.rutas._llamar", falso)
    r = mundo.http.get(f"{U}/notificaciones?usuario_id=999&solo_no_leidas=true", headers=mundo.con(eq["residente"]))
    assert r.status_code == 200 and visto["params"]["usuario_id"] == eq["residente"] and visto["params"]["solo_no_leidas"] == "true"


def test_notificaciones_exigen_sesion(mundo):
    assert mundo.http.get(f"{U}/notificaciones").status_code == 401
    assert mundo.http.post(f"{U}/notificaciones/1/leida").status_code == 401


def test_marcar_leida_pasa_el_usuario_del_token_y_traduce_el_404(mundo, eq, monkeypatch):
    llamadas = []

    def falso(metodo, ruta, **params):
        llamadas.append((metodo, ruta, params))
        return _Resp(404 if ruta.endswith("/77/leida") else 204)

    monkeypatch.setattr("app.modulos.notificaciones.rutas._llamar", falso)
    h = mundo.con(eq["almacenero"])
    assert mundo.http.post(f"{U}/notificaciones/5/leida", headers=h).status_code == 204
    assert mundo.http.post(f"{U}/notificaciones/77/leida", headers=h).status_code == 404
    assert llamadas[0] == ("POST", "/notificaciones/5/leida", {"usuario_id": eq["almacenero"]})


def test_si_notificaciones_no_esta_configurado_o_cae_la_api_responde_503_y_nada_mas_se_rompe(mundo, eq, monkeypatch):
    h = mundo.con(eq["residente"])
    r = mundo.http.get(f"{U}/notificaciones", headers=h)
    assert r.status_code == 503 and r.json()["codigo"] == "notificaciones_no_disponible"
    monkeypatch.setenv("SIGRO_NOTIFICACIONES_URL", "http://127.0.0.1:9")        # puerto cerrado
    monkeypatch.setenv("SIGRO_SERVICIO_TOKEN", "x" * 32)
    r = mundo.http.get(f"{U}/notificaciones", headers=h)
    assert r.status_code == 503 and r.json()["codigo"] == "notificaciones_no_disponible"
    assert mundo.http.get(f"{U}/obras", headers=h).status_code == 200            # el resto del sistema sigue vivo


# ══════════════════════════════════════════ de punta a punta, con el microservicio de verdad
RAIZ_NOTIF = Path(__file__).resolve().parents[2] / "notificaciones"


@pytest.mark.skipif(not (RAIZ_NOTIF / "main.py").exists(), reason="no está la carpeta notificaciones/ junto a api/")
def test_de_punta_a_punta_dispositivo_outbox_relevo_microservicio_campana(mundo, eq, monkeypatch, tmp_path):
    from fastapi.testclient import TestClient

    monkeypatch.syspath_prepend(str(RAIZ_NOTIF))
    for m in ("main", "almacen"):
        sys.modules.pop(m, None)
    token = "token-de-servicio-de-pruebas-0123456789"
    monkeypatch.setenv("NOTIF_TOKEN", token)
    monkeypatch.setenv("NOTIF_DB", str(tmp_path / "n.db"))
    monkeypatch.setenv("SIGRO_NOTIFICACIONES_URL", "http://notificaciones.test")
    monkeypatch.setenv("SIGRO_SERVICIO_TOKEN", token)
    import almacen as almacen_notif
    monkeypatch.setattr(almacen_notif, "RUTA", str(tmp_path / "n.db"))
    import main as notif

    with TestClient(notif.app) as micro:
        # 1) el hecho en la API: un almacenero pide autorizar su celular
        assert pedir_dispositivo(mundo, eq).status_code == 201
        # 2) el relevo lo entrega al microservicio (cliente HTTP real contra su app)
        r = relay.procesar(abrir(mundo), lambda ev: relay.enviar_http(ev, cliente=micro))
        assert (r.enviados, r.fallidos) == (1, 0)
        # 3) el administrador ve la notificación por la API (que pregunta al micro con SU usuario)
        def por_micro(metodo, ruta, **params):
            x = micro.request(metodo, ruta, params=params, headers={"X-Servicio-Token": token})
            return x
        monkeypatch.setattr("app.modulos.notificaciones.rutas._llamar", por_micro)
        lista = get(mundo, "/notificaciones", eq["admin"]).json()
        assert lista["no_leidas"] == 1
        n = lista["notificaciones"][0]
        assert n["titulo"] == "Dispositivo por autorizar" and n["proyecto_id"] == eq["proyecto"]
        # el almacenero que lo pidió NO la ve; el residente tampoco
        assert get(mundo, "/notificaciones", eq["almacenero"]).json()["no_leidas"] == 0
        assert get(mundo, "/notificaciones", eq["residente"]).json()["no_leidas"] == 0
        # 4) la marca como leída; otro no puede marcarla
        assert mundo.http.post(f"{U}/notificaciones/{n['id']}/leida", headers=mundo.con(eq["residente"])).status_code == 404
        assert mundo.http.post(f"{U}/notificaciones/{n['id']}/leida", headers=mundo.con(eq["admin"])).status_code == 204
        assert get(mundo, "/notificaciones", eq["admin"]).json()["no_leidas"] == 0
        # 5) reenviar el mismo evento (p. ej. un relevo que murió antes de marcarlo) NO duplica
        mundo.conn.exec_driver_sql("RESET ROLE")
        mundo.conn.exec_driver_sql("SELECT 1")
        ev = mundo.f.c.execute("SELECT id, tipo, proyecto_id, payload, creado_en FROM outbox.evento").fetchone()
        relay.enviar_http(ev, cliente=micro)
        assert len(get(mundo, "/notificaciones", eq["admin"]).json()["notificaciones"]) == 1
