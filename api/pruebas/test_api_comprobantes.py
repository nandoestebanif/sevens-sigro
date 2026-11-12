"""HU-34 / HU-61 · comprobantes de la compra por la API: subir, listar, descargar, anular."""
from __future__ import annotations

import hashlib

import pytest

from app.modulos.compras import dominio as dom
from pruebas.test_api_compras import comprar, eq, proveedor, sunat  # noqa: F401  (fixtures)

U = "/api/v1"
PDF = b"%PDF-1.4\n" + b"contenido de la factura\n" * 50
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 200
JPG = b"\xff\xd8\xff\xe0" + b"\x01" * 200


def ruta(eq, compra, extra=""):
    return f"{U}/obras/{eq['proyecto']}/compras/{compra}/comprobantes{extra}"


def subir(mundo, eq, compra, contenido=PDF, nombre="F001-4512.pdf", quien="residente", tipo="application/pdf"):
    return mundo.http.post(ruta(eq, compra), headers=mundo.con(eq[quien]), files={"archivo": (nombre, contenido, tipo)})


@pytest.fixture
def compra(mundo, eq, sunat):  # noqa: F811
    sunat["valor"] = None
    r = comprar(mundo, eq, proveedor(mundo, eq))
    assert r.status_code == 201, r.text
    return r.json()["id"]


# ---------------------------------------------------------------- dominio puro (sin base)
def test_el_tipo_lo_dice_el_contenido_no_la_extension():
    assert dom.tipo_real(PDF) == "application/pdf" and dom.tipo_real(PNG) == "image/png" and dom.tipo_real(JPG) == "image/jpeg"
    assert dom.tipo_real(b"MZ\x90\x00 programa.exe") is None


def test_el_nombre_pierde_rutas_y_caracteres_peligrosos():
    assert dom.nombre_seguro("C:\\Users\\juan\\Desktop\\factura.pdf", "application/pdf") == "factura.pdf"
    assert dom.nombre_seguro("../../etc/pas<s>wd", "application/pdf") == "passwd"
    assert dom.nombre_seguro("", "image/png") == "comprobante.png"
    assert len(dom.nombre_seguro("a" * 400 + ".pdf", "application/pdf")) <= 150


# ---------------------------------------------------------------- subir
def test_se_sube_un_pdf_y_queda_vigente_con_su_huella(mundo, eq, compra):
    r = subir(mundo, eq, compra)
    assert r.status_code == 201, r.text
    j = r.json()
    assert j["estado"] == "vigente" and j["tipo_mime"] == "application/pdf" and j["tamano_bytes"] == len(PDF)
    assert j["sha256"] == hashlib.sha256(PDF).hexdigest() and j["nombre"] == "F001-4512.pdf"


def test_un_exe_renombrado_a_pdf_se_rechaza_con_415(mundo, eq, compra):
    r = subir(mundo, eq, compra, b"MZ\x90\x00" + b"\x00" * 100, "factura.pdf")
    assert r.status_code == 415 and r.json()["codigo"] == "tipo_no_admitido"


def test_vacio_422_y_mas_de_10_mb_413(mundo, eq, compra):
    assert subir(mundo, eq, compra, b"").status_code == 422
    grande = PDF + b"0" * (dom.MAX_COMPROBANTE_BYTES)
    r = subir(mundo, eq, compra, grande)
    assert r.status_code == 413 and r.json()["codigo"] == "archivo_muy_grande"


def test_justo_10_mb_si_pasa(mundo, eq, compra):
    exacto = PDF + b"0" * (dom.MAX_COMPROBANTE_BYTES - len(PDF))
    assert subir(mundo, eq, compra, exacto).status_code == 201


def test_el_mismo_archivo_no_se_adjunta_dos_veces(mundo, eq, compra):
    assert subir(mundo, eq, compra).status_code == 201
    r = subir(mundo, eq, compra, nombre="otra.pdf")
    assert r.status_code == 409 and r.json()["codigo"] == "comprobante_repetido"


def test_varios_archivos_distintos_en_una_compra(mundo, eq, compra):
    for c, n, t in ((PDF, "a.pdf", "application/pdf"), (PNG, "b.png", "image/png"), (JPG, "c.jpg", "image/jpeg")):
        assert subir(mundo, eq, compra, c, n, tipo=t).status_code == 201
    lista = mundo.http.get(ruta(eq, compra), headers=mundo.con(eq["almacenero"])).json()
    assert [j["nombre"] for j in lista] == ["a.pdf", "b.png", "c.jpg"]


# ---------------------------------------------------------------- descargar
def test_se_descarga_el_original_byte_a_byte(mundo, eq, compra):
    j = subir(mundo, eq, compra).json()
    r = mundo.http.get(ruta(eq, compra, f"/{j['id']}"), headers=mundo.con(eq["almacenero"]))
    assert r.status_code == 200 and r.content == PDF
    assert r.headers["content-type"] == "application/pdf" and r.headers["x-sigro-sha256"] == j["sha256"]
    assert "attachment" in r.headers["content-disposition"] and r.headers["cache-control"] == "private, no-store"


def test_si_los_bytes_cambian_la_descarga_lo_detecta(mundo, eq, compra):
    j = subir(mundo, eq, compra).json()
    mundo.f.c.execute("ALTER TABLE compras.compra_adjunto_contenido DISABLE TRIGGER USER")
    mundo.f.c.execute("UPDATE compras.compra_adjunto_contenido SET contenido = %s WHERE adjunto_id = %s", (b"%PDF-manipulado", j["id"]))
    r = mundo.http.get(ruta(eq, compra, f"/{j['id']}"), headers=mundo.con(eq["residente"]))
    assert r.status_code == 409 and r.json()["codigo"] == "comprobante_alterado"


# ---------------------------------------------------------------- anular
def test_anular_exige_motivo_y_el_archivo_sigue_listado_y_descargable(mundo, eq, compra):
    j = subir(mundo, eq, compra).json()
    assert mundo.http.post(ruta(eq, compra, f"/{j['id']}/anulacion"), headers=mundo.con(eq["residente"]), json={"motivo": "ab"}).status_code == 422
    r = mundo.http.post(ruta(eq, compra, f"/{j['id']}/anulacion"), headers=mundo.con(eq["residente"]), json={"motivo": "Foto de otra factura"})
    assert r.status_code == 200 and r.json()["estado"] == "anulado" and r.json()["motivo_anulacion"] == "Foto de otra factura"
    assert r.json()["anulado_por"] == eq["residente"]
    lista = mundo.http.get(ruta(eq, compra), headers=mundo.con(eq["admin"])).json()
    assert len(lista) == 1 and lista[0]["estado"] == "anulado"
    assert mundo.http.get(ruta(eq, compra, f"/{j['id']}"), headers=mundo.con(eq["admin"])).content == PDF


def test_anular_dos_veces_da_409(mundo, eq, compra):
    j = subir(mundo, eq, compra).json()
    cuerpo = {"motivo": "Foto movida"}
    assert mundo.http.post(ruta(eq, compra, f"/{j['id']}/anulacion"), headers=mundo.con(eq["residente"]), json=cuerpo).status_code == 200
    assert mundo.http.post(ruta(eq, compra, f"/{j['id']}/anulacion"), headers=mundo.con(eq["residente"]), json=cuerpo).status_code == 409


def test_tras_anular_se_sube_el_correcto_y_la_compra_lo_muestra_vigente(mundo, eq, compra):
    j = subir(mundo, eq, compra).json()
    mundo.http.post(ruta(eq, compra, f"/{j['id']}/anulacion"), headers=mundo.con(eq["residente"]), json={"motivo": "Foto movida"})
    assert subir(mundo, eq, compra, PNG, "buena.png", tipo="image/png").status_code == 201
    c = mundo.http.get(f"{U}/obras/{eq['proyecto']}/compras/{compra}", headers=mundo.con(eq["residente"])).json()
    assert c["comprobantes_vigentes"] == 1


# ---------------------------------------------------------------- permisos y alcance (regla 9, en el servidor)
def test_el_almacenero_consulta_pero_no_sube_ni_anula(mundo, eq, compra):
    j = subir(mundo, eq, compra).json()
    assert subir(mundo, eq, compra, PNG, "x.png", quien="almacenero", tipo="image/png").status_code == 403
    assert mundo.http.post(ruta(eq, compra, f"/{j['id']}/anulacion"), headers=mundo.con(eq["almacenero"]), json={"motivo": "Foto movida"}).status_code == 403
    assert mundo.http.get(ruta(eq, compra), headers=mundo.con(eq["almacenero"])).status_code == 200


def test_otra_obra_no_ve_ni_descarga_los_comprobantes(mundo, eq, compra):
    j = subir(mundo, eq, compra).json()
    otra = mundo.f.obra_completa()
    base = f"{U}/obras/{otra['proyecto']}/compras/{compra}/comprobantes"
    assert mundo.http.get(base, headers=mundo.con(otra["residente"])).status_code == 404
    assert mundo.http.get(f"{base}/{j['id']}", headers=mundo.con(otra["residente"])).status_code == 404
    # y quien no está asignado a la obra ni siquiera entra
    assert mundo.http.get(ruta(eq, compra), headers=mundo.con(otra["residente"])).status_code in (403, 404)


def test_una_compra_anulada_no_admite_comprobantes_nuevos(mundo, eq, compra):
    r = mundo.http.post(f"{U}/obras/{eq['proyecto']}/compras/{compra}/anular", headers=mundo.con(eq["residente"]),
                        json={"motivo": "Compra duplicada"})
    assert r.status_code == 200, r.text
    assert subir(mundo, eq, compra).status_code == 409
