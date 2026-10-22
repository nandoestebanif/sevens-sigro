"""Pruebas del microservicio de Notificaciones (sin red y con una base SQLite temporal por prueba)."""
from __future__ import annotations

import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import almacen  # noqa: E402

TOKEN = "token-de-servicio-de-pruebas-0123456789"
H = {"X-Servicio-Token": TOKEN}


@pytest.fixture
def cli(tmp_path, monkeypatch):
    monkeypatch.setenv("NOTIF_TOKEN", TOKEN)
    monkeypatch.setattr(almacen, "RUTA", str(tmp_path / "n.db"))
    import main
    with TestClient(main.app) as c:
        yield c


def evento(eid="api-1", dest=(10, 11), **kw):
    e = {"evento_id": eid, "tipo": "dispositivo.solicitado", "proyecto_id": 1, "creado_en": "2026-10-22T14:00:00Z",
         "destinatarios": list(dest), "titulo": "Dispositivo por autorizar", "mensaje": "Luis pidió autorizar su celular.",
         "datos": {"dispositivo_id": 7}}
    e.update(kw)
    return e


def test_sin_token_o_con_token_malo_se_rechaza(cli):
    assert cli.post("/eventos", json=evento()).status_code == 401
    assert cli.post("/eventos", json=evento(), headers={"X-Servicio-Token": "otro"}).status_code == 401
    assert cli.get("/notificaciones?usuario_id=10").status_code == 401
    assert cli.post("/notificaciones/1/leida?usuario_id=10").status_code == 401


def test_sin_token_configurado_el_servicio_no_acepta_nada(cli, monkeypatch):
    monkeypatch.delenv("NOTIF_TOKEN")
    assert cli.post("/eventos", json=evento(), headers=H).status_code == 503
    assert cli.get("/salud").json()["token_configurado"] is False


def test_un_evento_crea_una_notificacion_por_destinatario(cli):
    r = cli.post("/eventos", json=evento(), headers=H)
    assert r.status_code == 201 and r.json() == {"nuevo": True, "notificaciones": 2}
    for uid in (10, 11):
        lista = cli.get(f"/notificaciones?usuario_id={uid}", headers=H).json()
        assert lista["no_leidas"] == 1 and lista["notificaciones"][0]["titulo"] == "Dispositivo por autorizar"
        assert lista["notificaciones"][0]["datos"] == {"dispositivo_id": 7} and lista["notificaciones"][0]["leida"] is False
    assert cli.get("/notificaciones?usuario_id=99", headers=H).json() == {"no_leidas": 0, "notificaciones": []}


def test_un_reenvio_del_mismo_evento_no_duplica(cli):
    assert cli.post("/eventos", json=evento(), headers=H).status_code == 201
    de_nuevo = cli.post("/eventos", json=evento(), headers=H)
    assert de_nuevo.status_code == 200 and de_nuevo.json() == {"nuevo": False, "notificaciones": 0}
    assert len(cli.get("/notificaciones?usuario_id=10", headers=H).json()["notificaciones"]) == 1


def test_destinatarios_repetidos_en_un_evento_se_cuentan_una_vez(cli):
    r = cli.post("/eventos", json=evento(dest=(10, 10, 11, 11)), headers=H)
    assert r.json()["notificaciones"] == 2


def test_marcar_leida_solo_la_marca_su_dueno_y_no_cambia_la_hora(cli):
    cli.post("/eventos", json=evento(), headers=H)
    nid = cli.get("/notificaciones?usuario_id=10", headers=H).json()["notificaciones"][0]["id"]
    assert cli.post(f"/notificaciones/{nid}/leida?usuario_id=11", headers=H).status_code == 404     # ajena
    assert cli.get("/notificaciones?usuario_id=10", headers=H).json()["no_leidas"] == 1
    assert cli.post(f"/notificaciones/{nid}/leida?usuario_id=10", headers=H).status_code == 204
    primera = cli.get("/notificaciones?usuario_id=10", headers=H).json()["notificaciones"][0]["leida_en"]
    assert primera and cli.post(f"/notificaciones/{nid}/leida?usuario_id=10", headers=H).status_code == 204
    assert cli.get("/notificaciones?usuario_id=10", headers=H).json()["notificaciones"][0]["leida_en"] == primera
    assert cli.get("/notificaciones?usuario_id=10", headers=H).json()["no_leidas"] == 0
    assert cli.get("/notificaciones?usuario_id=11", headers=H).json()["no_leidas"] == 1             # la de 11 sigue sin leer


def test_listar_solo_no_leidas_y_limite_con_las_mas_nuevas_primero(cli):
    for i in range(5):
        cli.post("/eventos", json=evento(eid=f"api-{i}", dest=(10,), titulo=f"N{i}"), headers=H)
    nid = cli.get("/notificaciones?usuario_id=10", headers=H).json()["notificaciones"][0]["id"]
    cli.post(f"/notificaciones/{nid}/leida?usuario_id=10", headers=H)
    todas = cli.get("/notificaciones?usuario_id=10", headers=H).json()
    assert [n["titulo"] for n in todas["notificaciones"]] == ["N4", "N3", "N2", "N1", "N0"] and todas["no_leidas"] == 4
    sin_leer = cli.get("/notificaciones?usuario_id=10&solo_no_leidas=true", headers=H).json()
    assert [n["titulo"] for n in sin_leer["notificaciones"]] == ["N3", "N2", "N1", "N0"]
    assert len(cli.get("/notificaciones?usuario_id=10&limite=2", headers=H).json()["notificaciones"]) == 2


@pytest.mark.parametrize("cambio", [
    {"evento_id": ""}, {"evento_id": "con espacios"}, {"tipo": "MAL"}, {"proyecto_id": 0}, {"destinatarios": []},
    {"titulo": ""}, {"mensaje": "x" * 501}, {"creado_en": "ayer"}, {"extra": 1}, {"destinatarios": ["a"]},
])
def test_eventos_invalidos_se_rechazan(cli, cambio):
    assert cli.post("/eventos", json=evento(**cambio), headers=H).status_code == 422
    assert cli.get("/notificaciones?usuario_id=10", headers=H).json()["notificaciones"] == []


def test_el_usuario_es_obligatorio_al_consultar(cli):
    assert cli.get("/notificaciones", headers=H).status_code == 422
    assert cli.get("/notificaciones?usuario_id=0", headers=H).status_code == 422


def test_la_atomicidad_un_fallo_a_mitad_no_deja_un_evento_a_medias(cli, monkeypatch):
    original = almacen.json.dumps
    llamadas = {"n": 0}

    def falla_en_la_segunda(*a, **k):
        llamadas["n"] += 1
        if llamadas["n"] == 2:
            raise RuntimeError("falla simulada")
        return original(*a, **k)

    monkeypatch.setattr(almacen.json, "dumps", falla_en_la_segunda)
    with pytest.raises(RuntimeError):
        cli.post("/eventos", json=evento(), headers=H)
    monkeypatch.setattr(almacen.json, "dumps", original)
    assert cli.get("/notificaciones?usuario_id=10", headers=H).json()["notificaciones"] == []
    assert cli.post("/eventos", json=evento(), headers=H).status_code == 201       # el reintento sí entra completo
    assert cli.get("/notificaciones?usuario_id=11", headers=H).json()["no_leidas"] == 1
