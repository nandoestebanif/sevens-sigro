"""Hallazgos del fuzzing del contrato (test_contrato_fuzz.py, 24/11/2026), cada uno con su prueba de regresión.

1. Un texto con el carácter NUL (\\x00) es un dato inválido del cliente: 422 «texto_invalido», no un 500.

   Apareció en POST /catalogo/insumos/importar-s10, pero afecta a toda ruta con texto: se corrige en app/errores.py.
2. El plazo de una obra no tenía tope: del año 645 al 5902 eran ~275 000 periodos en una petición (denegación de
   servicio). Ahora fechas entre 2000 y 2100 y plazo de hasta 10 años, al crear y al ampliar."""
import pytest


@pytest.mark.parametrize("ruta, cuerpo", [
    ("/api/v1/catalogo/insumos/importar-s10",
     {"insumos": [{"codigo": "NUL-01", "descripcion": "Cemento\x00", "unidad": "bls", "tipo": "material"}]}),
    ("/api/v1/obras", {"nombre": "Obra\x00", "fecha_inicio": "2026-10-19", "fecha_fin": "2027-10-17"}),
])
def test_texto_con_nul_responde_422(mundo, ruta, cuerpo):
    eq = mundo.f.obra_completa()
    r = mundo.http.post(ruta, json=cuerpo, headers=mundo.token(eq["admin"]))
    assert r.status_code == 422, r.text
    assert r.json()["codigo"] == "texto_invalido"


# ── Plazo de la obra con tope (mismo fuzzing): sin él, 5 000 años de periodos en una petición tumbaban el servicio ──

@pytest.mark.parametrize("inicio, fin", [("0645-05-11", "5902-06-27"), ("2026-10-19", "2040-01-01")])
def test_obra_con_plazo_absurdo_responde_422_sin_crear_periodos(mundo, inicio, fin):
    eq = mundo.f.obra_completa()
    r = mundo.http.post("/api/v1/obras", json={"nombre": f"Obra {inicio}", "fecha_inicio": inicio, "fecha_fin": fin},
                        headers=mundo.token(eq["admin"]))
    assert r.status_code == 422, r.text


def test_ampliar_plazo_mas_alla_de_10_anios_responde_422(mundo):
    eq = mundo.f.obra_completa()
    r = mundo.http.post(f"/api/v1/obras/{eq['proyecto']}/ampliar-plazo",
                        json={"nueva_fecha_fin": "2099-12-31", "motivo": "prueba de tope"}, headers=mundo.token(eq["admin"]))
    assert r.status_code == 422 and r.json()["codigo"] == "plazo_excesivo", r.text
