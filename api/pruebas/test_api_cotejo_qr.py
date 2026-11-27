"""HU-73 · POST /obras/{id}/compras/cotejar-qr y GET /obras/{id}/compras (contrato 3.1 y 3.2) contra PostgreSQL real.

El endpoint vive en el backend Spring Boot (backend-spring); por eso estas pruebas corren solo en la prueba de paridad:

    SIGRO_SPRING_URL=http://localhost:8080 python -m pytest pruebas/test_api_cotejo_qr.py

Cubren lo que las pruebas JUnit simulan: el SQL del esquema compras, lo pendiente que responde almacén, el permiso de
verdad (recepcion.registrar) y que la bitácora anota la ruta sin el texto del QR (criterio 8).
"""
from __future__ import annotations

import os
import uuid
from datetime import date

import pytest

from pruebas.fabrica import ruc_nuevo

pytestmark = pytest.mark.skipif(not os.environ.get("SIGRO_SPRING_URL"), reason="HU-73: el cotejo del QR es del backend Spring")

U = "/api/v1"
RUC_SEVENS = "20606456892"


def qr(ruc, serie="F001", numero="00001234", igv="180.00", total="1180.00", fecha="2026-11-20", adquirente=RUC_SEVENS, tipo="01"):
    return f"{ruc}|{tipo}|{serie}|{numero}|{igv}|{total}|{fecha}|6|{adquirente}|aGFzaA=="


@pytest.fixture
def ob(mundo):
    e = mundo.f.obra_completa()
    e["central"] = mundo.f.almacen(e["proyecto"], "Central", "central")
    e["cem"] = mundo.f.insumo("0221000000", unidad="bls")
    e["ruc"] = ruc_nuevo()
    e["prov"] = mundo.f.proveedor(e["ruc"], validado=True, razon="Aceros del Sur S.A.C.")
    return e


def compra(mundo, ob, numero="F001-1234", base="1000.00", igv="180.00", total="1180.00", prov=None):
    c = mundo.f.compra(ob["proyecto"], prov or ob["prov"], ob["residente"], numero=numero, base=base, igv=igv, total=total, fecha=date(2026, 11, 20))
    mundo.f.detalle(c, ob["cem"], cantidad="40", unidad="bls", precio="29.50")
    return c


def cotejar(mundo, ob, texto, quien="almacenero"):
    return mundo.http.post(f"{U}/obras/{ob['proyecto']}/compras/cotejar-qr", headers=mundo.con(ob[quien]), json={"qr": texto})


def test_recibir_abre_la_compra_que_coincide(mundo, ob):
    c = compra(mundo, ob)
    r = cotejar(mundo, ob, qr(ob["ruc"]))
    assert r.status_code == 200, r.text
    d = r.json()
    assert d["resultado"] == "recibir" and d["avisos"] == []
    assert d["compra"] == {"id": c, "estado": "registrada", "proveedor_razon_social": "Aceros del Sur S.A.C.", "total": "1180.00",
                           "queda_por_recibir": True}
    assert d["comprobante"] == {"ruc_emisor": ob["ruc"], "tipo_comprobante": "factura", "numero_comprobante": "F001-1234", "igv": "180.00",
                                "total": "1180.00", "fecha": "2026-11-20", "adquirente_tipo": "6", "adquirente_documento": RUC_SEVENS}


def test_recibir_con_avisos_da_la_diferencia_en_soles(mundo, ob):
    compra(mundo, ob, base="900.00", igv="162.00", total="1062.00")
    d = cotejar(mundo, ob, qr(ob["ruc"])).json()
    assert d["resultado"] == "recibir_con_avisos"
    assert d["avisos"][0] == "El total del QR (S/ 1 180.00) difiere en S/ 118.00 del registrado en la compra (S/ 1 062.00)."
    assert d["avisos"][1].startswith("El IGV del QR (S/ 180.00) difiere en S/ 18.00")


def test_un_centimo_de_diferencia_se_tolera(mundo, ob):
    compra(mundo, ob, base="1000.01", igv="180.00", total="1180.01")
    assert cotejar(mundo, ob, qr(ob["ruc"])).json()["resultado"] == "recibir"


def test_ya_recibida_no_ofrece_recibir(mundo, ob):
    c = compra(mundo, ob)
    r = mundo.http.post(f"{U}/obras/{ob['proyecto']}/almacen/recepciones", headers=mundo.con(ob["almacenero"]),
                        json={"uuid_cliente": str(uuid.uuid4()), "compra_id": c})
    assert r.status_code == 201, r.text
    d = cotejar(mundo, ob, qr(ob["ruc"])).json()
    assert d["resultado"] == "ya_recibida" and d["compra"]["queda_por_recibir"] is False


def test_recibida_a_medias_todavia_se_recibe(mundo, ob):
    c = compra(mundo, ob)
    r = mundo.http.post(f"{U}/obras/{ob['proyecto']}/almacen/recepciones", headers=mundo.con(ob["almacenero"]),
                        json={"uuid_cliente": str(uuid.uuid4()), "compra_id": c, "lineas": [{"insumo_id": ob["cem"], "cantidad": "10"}]})
    assert r.status_code == 201, r.text
    assert cotejar(mundo, ob, qr(ob["ruc"])).json()["resultado"] == "recibir"


def test_anulada_lo_dice_con_el_motivo(mundo, ob):
    c = compra(mundo, ob)
    mundo.f.q1("UPDATE compras.compra SET estado = 'anulada', motivo_anulacion = 'Comprobante equivocado', anulada_por = %s, "
               "anulada_en = now() WHERE id = %s RETURNING id", (ob["residente"], c))
    d = cotejar(mundo, ob, qr(ob["ruc"])).json()
    assert d["resultado"] == "anulada" and d["compra"]["id"] == c
    assert d["avisos"] == ["La compra F001-1234 está anulada: Comprobante equivocado."]


def test_sin_compra_si_el_mismo_numero_es_de_otro_proveedor_o_de_otra_obra(mundo, ob):
    otro = mundo.f.proveedor()
    compra(mundo, ob, prov=otro)                                   # mismo número, otro proveedor
    otra_obra = mundo.f.obra_completa()
    mundo.f.compra(otra_obra["proyecto"], ob["prov"], otra_obra["residente"], numero="F001-1234")   # mismo comprobante, otra obra
    d = cotejar(mundo, ob, qr(ob["ruc"])).json()
    assert d["resultado"] == "sin_compra" and d["compra"] is None


def test_no_es_de_sevens_ni_busca_la_compra(mundo, ob):
    compra(mundo, ob)
    d = cotejar(mundo, ob, qr(ob["ruc"], adquirente=ruc_nuevo())).json()
    assert d["resultado"] == "no_es_de_sevens" and d["compra"] is None


def test_el_numero_guardado_con_ceros_es_el_mismo_comprobante(mundo, ob):
    c = compra(mundo, ob, numero="F001-00001234")
    d = cotejar(mundo, ob, qr(ob["ruc"], numero="1234")).json()
    assert d["resultado"] == "recibir" and d["compra"]["id"] == c


def test_qr_no_reconocido_es_422_y_no_repite_el_texto(mundo, ob):
    for texto in ("https://ejemplo.pe/promo", qr(ob["ruc"], tipo="07"), qr(ob["ruc"], total="ciento"), qr(ob["ruc"], fecha="2026-13-40")):
        r = cotejar(mundo, ob, texto)
        assert r.status_code == 422 and r.json()["codigo"] == "qr_no_reconocido", texto
        assert "ejemplo.pe" not in r.text and ob["ruc"] not in r.json()["mensaje"]
    r = mundo.http.post(f"{U}/obras/{ob['proyecto']}/compras/cotejar-qr", headers=mundo.con(ob["almacenero"]), json={})
    assert r.status_code == 422 and r.json()["codigo"] == "datos_invalidos"


@pytest.mark.parametrize("rol", ["residente", "ssoma", "gerente"])
def test_solo_almacenero_y_administrador_cotejan(mundo, ob, rol):
    compra(mundo, ob)
    assert cotejar(mundo, ob, qr(ob["ruc"]), quien=rol).status_code == 403
    assert cotejar(mundo, ob, qr(ob["ruc"]), quien="admin").json()["resultado"] == "recibir"


def test_sin_sesion_es_401(mundo, ob):
    r = mundo.http.post(f"{U}/obras/{ob['proyecto']}/compras/cotejar-qr", json={"qr": qr(ob["ruc"])})
    assert r.status_code == 401


def test_de_otra_obra_es_403(mundo, ob):
    otra = mundo.f.obra_completa()
    r = mundo.http.post(f"{U}/obras/{otra['proyecto']}/compras/cotejar-qr", headers=mundo.con(ob["almacenero"]), json={"qr": qr(ob["ruc"])})
    assert r.status_code == 403


def test_la_bitacora_anota_el_cotejo_sin_el_texto_del_qr(mundo, ob):
    compra(mundo, ob)
    texto = qr(ob["ruc"])
    assert cotejar(mundo, ob, texto).status_code == 200
    fila = mundo.f.c.execute("SELECT * FROM obra.bitacora WHERE ruta LIKE %s ORDER BY id DESC LIMIT 1", ("%/compras/cotejar-qr",)).fetchone()
    assert fila is not None, "el cotejo queda en la bitácora"
    guardado = " ".join(str(v) for v in (fila.values() if isinstance(fila, dict) else fila))
    for pedazo in (texto, ob["ruc"], RUC_SEVENS, "1180.00", "aGFzaA==", "|"):
        assert pedazo not in guardado, pedazo


def test_el_listado_de_compras_trae_ruc_del_proveedor_y_total(mundo, ob):
    c = compra(mundo, ob)
    r = mundo.http.get(f"{U}/obras/{ob['proyecto']}/compras", headers=mundo.con(ob["almacenero"]))
    assert r.status_code == 200, r.text
    fila = next(x for x in r.json() if x["id"] == c)
    assert fila["proveedor_ruc"] == ob["ruc"] and fila["total"] == "1180.00"
    assert fila["totales"] == {"base": "1000.00", "igv": "180.00", "total": "1180.00"}     # lo de antes no cambia
