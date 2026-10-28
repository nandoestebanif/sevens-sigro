"""Cargador de proveedores y arranque del primer administrador, contra una base real."""
from __future__ import annotations

import json
from datetime import date

import psycopg
import pytest
from psycopg.rows import dict_row
from sqlalchemy import create_engine

from pruebas.conftest import alembic, dsn, eliminar_base, recrear_base, url_sqlalchemy
from semillas import cargar_proveedores as semilla

CLIENTE = [
    {"numero_documento": "20606456892", "proveedor": "SEVENS  INGENIEROS   SAC"},    # RUC válido, espacios de más
    {"numero_documento": "20606456892", "proveedor": "REPETIDO"},                    # repetido en el archivo
    {"numero_documento": "12345678", "proveedor": "JUAN PEREZ"},                     # DNI: se carga, queda en evidencia
    {"numero_documento": "12345678901", "proveedor": "RUC FALSO"},                   # no pasa el dígito verificador
    {"numero_documento": "", "proveedor": "SIN DOCUMENTO"},                          # la base nunca lo aceptaría
    {"numero_documento": "20100070970", "proveedor": ""},                            # sin razón social
    {"numero_documento": "ab", "proveedor": "DEMASIADO CORTO"},
]


@pytest.fixture
def base():
    nombre = "sigro_semillas"
    recrear_base(nombre)
    alembic(nombre, "upgrade", "head")
    yield nombre
    eliminar_base(nombre)


def proveedores(base):
    with psycopg.connect(dsn(base), row_factory=dict_row) as c:
        return {f["ruc"]: f for f in c.execute("SELECT * FROM compras.proveedor ORDER BY ruc").fetchall()}


def test_preparar_cuenta_lo_que_entra_y_lo_que_no():
    lista, r = semilla.preparar(CLIENTE)
    assert dict(lista) == {"20606456892": "SEVENS INGENIEROS SAC", "12345678": "JUAN PEREZ", "12345678901": "RUC FALSO"}
    assert r["ruc_correcto"] == 1 and r["documento_sucio"] == 2
    assert r["repetido_en_el_archivo"] == 1 and r["descartado_sin_formato"] == 3


def test_cargar_es_idempotente_y_no_toca_la_validacion(base):
    lista, _ = semilla.preparar(CLIENTE)
    url = url_sqlalchemy(base)
    assert dict(semilla.cargar(url, lista)) == {"insertados": 3}
    assert dict(semilla.cargar(url, lista)) == {"sin_cambios": 3}                    # segunda vez: nada nuevo
    with psycopg.connect(dsn(base)) as c:
        c.execute("UPDATE compras.proveedor SET estado_validacion='validado', validado_en=now() WHERE ruc='20606456892'")
        c.commit()
    r = semilla.cargar(url, [("20606456892", "SEVENS INGENIEROS S.A.C.")])
    assert dict(r) == {"actualizados": 1}
    p = proveedores(base)
    assert len(p) == 3 and p["20606456892"]["razon_social"] == "SEVENS INGENIEROS S.A.C."
    assert p["20606456892"]["estado_validacion"] == "validado" and p["12345678"]["estado_validacion"] == "sin_validar"


def test_el_comando_en_seco_no_escribe(base, tmp_path, capsys):
    f = tmp_path / "p.json"
    f.write_text(json.dumps(CLIENTE), encoding="utf-8")
    assert semilla.main([str(f), "--dry-run", "--url", url_sqlalchemy(base)]) == 0
    assert proveedores(base) == {}
    assert semilla.main([str(f), "--url", url_sqlalchemy(base)]) == 0
    assert len(proveedores(base)) == 3
    assert "insertados" in capsys.readouterr().out


def test_sin_conexion_el_comando_avisa(tmp_path, monkeypatch):
    f = tmp_path / "p.json"
    f.write_text("[]", encoding="utf-8")
    monkeypatch.delenv("SIGRO_APP_DATABASE_URL", raising=False)
    assert semilla.main([str(f)]) == 2
    f.write_text("{}", encoding="utf-8")
    with pytest.raises(SystemExit):
        semilla.main([str(f), "--dry-run"])


def test_primer_admin_crea_usuario_obra_y_asignacion_y_no_se_repite(base, monkeypatch):
    from app import cli
    from app.seguridad import verificar

    monkeypatch.setattr(cli, "motor", lambda: create_engine(url_sqlalchemy(base)))
    uid, pid = cli.primer_admin("adiel", "Víctor Adiel Luján", "Clave-Segura-2026", "Multifamiliar Flores",
                                date(2026, 10, 19), date(2027, 10, 17))
    with psycopg.connect(dsn(base), row_factory=dict_row) as c:
        u = c.execute("SELECT * FROM obra.usuario WHERE id=%s", (uid,)).fetchone()
        a = c.execute("SELECT rol FROM obra.asignacion WHERE usuario_id=%s AND proyecto_id=%s", (uid, pid)).fetchone()
    assert a["rol"] == "administrador" and u["hash_clave"].startswith("$argon2id$")
    assert verificar("Clave-Segura-2026", u["hash_clave"]) and "Clave-Segura" not in u["hash_clave"]
    with pytest.raises(SystemExit, match="sistema vacío"):
        cli.primer_admin("otro", "Otro", "Clave-Segura-2026", "Otra", date(2026, 1, 1), date(2026, 2, 1))


def test_primer_admin_rechaza_clave_debil_sin_escribir(base, monkeypatch):
    from app import cli

    monkeypatch.setattr(cli, "motor", lambda: create_engine(url_sqlalchemy(base)))
    with pytest.raises(SystemExit):
        cli.primer_admin("adiel", "A", "123", "Obra", date(2026, 1, 1), date(2026, 2, 1))
    with psycopg.connect(dsn(base)) as c:
        assert c.execute("SELECT count(*) FROM obra.usuario").fetchone()[0] == 0


def test_el_archivo_real_del_cliente_si_esta_disponible():
    import os
    ruta = os.environ.get("SIGRO_PROVEEDORES_JSON")
    if not ruta:
        pytest.skip("define SIGRO_PROVEEDORES_JSON para probar con el archivo real del cliente")
    lista, r = semilla.preparar(semilla.leer(__import__("pathlib").Path(ruta)))
    assert len(lista) == r["ruc_correcto"] + r["documento_sucio"] and len(lista) > 0


# ───────────────────────── catálogo del cliente (HT-07)
from semillas import cargar_catalogo as catalogo  # noqa: E402

CATALOGO = [
    {"codigo": "68", "nombre": "ABRAZADERA  DE 1 1/2¨ METAL-1 OREJA", "um": "UNIDADES", "precioPromedio": 4.5, "marcas": ""},
    {"codigo": "68", "nombre": "REPETIDO", "um": "UNIDADES"},
    {"codigo": "1632", "nombre": "CEMENTO PORTLAND TIPO I", "um": "BOLSAS", "marcas": "SOL"},
    {"codigo": "", "nombre": "SIN CÓDIGO", "um": "UNIDADES"},
    {"codigo": "9", "nombre": "", "um": "UNIDADES"},
    {"codigo": "10", "nombre": "SIN UNIDAD", "um": ""},
]


def insumos(base):
    with psycopg.connect(dsn(base), row_factory=dict_row) as c:
        return {f["codigo"]: f for f in c.execute("SELECT * FROM catalogo.insumo ORDER BY codigo").fetchall()}


def test_catalogo_preparar_cuenta_lo_que_entra_y_lo_que_no():
    lista, r = catalogo.preparar(CATALOGO)
    assert lista == [("68", "ABRAZADERA DE 1 1/2¨ METAL-1 OREJA", "UNIDADES"), ("1632", "CEMENTO PORTLAND TIPO I", "BOLSAS")]
    assert r["valido"] == 2 and r["repetido_en_el_archivo"] == 1 and r["descartado_incompleto"] == 3


def test_catalogo_es_idempotente_y_no_pisa_factor_ni_unidad(base):
    lista, _ = catalogo.preparar(CATALOGO)
    url = url_sqlalchemy(base)
    assert dict(catalogo.cargar(url, lista)) == {"insertados": 2}
    assert dict(catalogo.cargar(url, lista)) == {"sin_cambios": 2}
    with psycopg.connect(dsn(base)) as c:
        c.execute("UPDATE catalogo.insumo SET factor_conversion = 42.5, unidad_compra = 'bls' WHERE codigo = '1632'")
        c.commit()
    assert dict(catalogo.cargar(url, [("1632", "CEMENTO PORTLAND TIPO I (42.5 kg)", "BOLSAS")])) == {"actualizados": 1}
    i = insumos(base)["1632"]
    assert i["descripcion"].endswith("(42.5 kg)") and float(i["factor_conversion"]) == 42.5 and i["unidad_compra"] == "bls"
    assert i["verificado"] is True and i["tipo"] == "material"


def test_catalogo_en_seco_no_escribe(base, tmp_path):
    f = tmp_path / "c.json"
    f.write_text(json.dumps(CATALOGO), encoding="utf-8")
    assert catalogo.main([str(f), "--dry-run", "--url", url_sqlalchemy(base)]) == 0
    assert insumos(base) == {}
    assert catalogo.main([str(f), "--url", url_sqlalchemy(base)]) == 0
    assert len(insumos(base)) == 2
