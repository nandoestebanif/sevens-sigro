"""Pruebas de la lógica de dominio SIN levantar la base de datos."""
from datetime import date, timedelta

import pytest

from app.modulos.compras.dominio import digito_verificador_ruc, ruc_valido
from app.modulos.obra.dominio import generar_periodos


# ------------------------------------------------------------------ periodos (HU-03)
def test_obra_que_empieza_lunes_y_termina_domingo_da_semanas_completas():
    ps = generar_periodos(date(2026, 10, 19), date(2026, 11, 8))   # lunes a domingo, 3 semanas
    assert [p.dias for p in ps] == [7, 7, 7]
    assert [p.numero for p in ps] == [1, 2, 3]


def test_primer_periodo_corto_si_arranca_a_mitad_de_semana():
    ps = generar_periodos(date(2026, 10, 14), date(2026, 10, 25))   # miércoles
    assert (ps[0].inicio, ps[0].fin) == (date(2026, 10, 14), date(2026, 10, 18))
    assert ps[0].dias == 5


def test_ultimo_periodo_puede_ser_mas_corto_que_una_semana():
    ps = generar_periodos(date(2026, 10, 19), date(2026, 11, 4))    # termina miércoles
    assert ps[-1].dias == 3
    assert ps[-1].fin == date(2026, 11, 4)


def test_obra_de_un_solo_dia():
    ps = generar_periodos(date(2026, 10, 14), date(2026, 10, 14))
    assert len(ps) == 1 and ps[0].dias == 1


def test_dia_de_corte_distinto_del_domingo():
    ps = generar_periodos(date(2026, 10, 19), date(2026, 11, 8), dia_corte=5)   # corte viernes
    assert all(p.fin.isoweekday() == 5 for p in ps[:-1])
    assert ps[0].fin == date(2026, 10, 23)


def test_fin_anterior_al_inicio_se_rechaza():
    with pytest.raises(ValueError):
        generar_periodos(date(2026, 10, 20), date(2026, 10, 19))


@pytest.mark.parametrize("corte", [0, 8, -1])
def test_dia_de_corte_fuera_de_rango_se_rechaza(corte):
    with pytest.raises(ValueError):
        generar_periodos(date(2026, 10, 19), date(2026, 11, 8), dia_corte=corte)


@pytest.mark.parametrize("corte", range(1, 8))
@pytest.mark.parametrize("dias", [1, 2, 6, 7, 8, 13, 14, 15, 90, 365])
@pytest.mark.parametrize("inicio", [date(2026, 10, 14), date(2026, 10, 19), date(2026, 12, 31)])
def test_los_periodos_cubren_el_plazo_sin_huecos_ni_solapes(inicio, dias, corte):
    fin = inicio + timedelta(days=dias - 1)
    ps = generar_periodos(inicio, fin, corte)
    assert ps[0].inicio == inicio and ps[-1].fin == fin
    assert sum(p.dias for p in ps) == dias
    for a, b in zip(ps, ps[1:]):
        assert b.inicio == a.fin + timedelta(days=1)          # contiguos
        assert a.fin.isoweekday() == corte                    # todos cortan en su día
        assert a.dias <= 7


def test_ampliar_plazo_continua_la_numeracion():
    primero = generar_periodos(date(2026, 10, 19), date(2026, 11, 1))
    resto = generar_periodos(primero[-1].fin + timedelta(days=1), date(2026, 11, 22), desde_numero=primero[-1].numero)
    assert [p.numero for p in resto] == [3, 4, 5]


# ------------------------------------------------------------------------ RUC (HU-31)
def test_ruc_real_de_sevens_es_valido():
    assert ruc_valido("20606456892")


def test_digito_verificador_de_sevens():
    assert digito_verificador_ruc("2060645689") == 2


@pytest.mark.parametrize("ruc", [
    "20606456893",   # dígito verificador incorrecto
    "2060645689",    # 10 dígitos
    "206064568921",  # 12 dígitos
    "30606456892",   # prefijo que no corresponde a un RUC
    "2060645689a",
    "",
])
def test_ruc_invalido(ruc):
    assert not ruc_valido(ruc)


# ------------------------------------------------------------------ HU-36 exceso sobre lo requerido
from decimal import Decimal as D  # noqa: E402

from app.modulos.almacen.dominio import exceso_sobre_requerido  # noqa: E402


def test_dentro_de_lo_requerido_no_hay_exceso():
    assert exceso_sobre_requerido(D("300"), D("100"), D("200")) == D("0")        # justo lo requerido
    assert exceso_sobre_requerido(D("300"), D("0"), D("120")) == D("0")


def test_el_exceso_es_lo_que_pasa_de_lo_requerido_contando_lo_ya_recibido():
    assert exceso_sobre_requerido(D("300"), D("250"), D("100")) == D("50.0000")
    assert exceso_sobre_requerido(D("5000"), D("0"), D("5696")) == D("696.0000")  # acero: 800 varillas × 7,12 kg


def test_si_ya_estaba_excedido_cualquier_recepcion_vuelve_a_exceder():
    assert exceso_sobre_requerido(D("100"), D("130"), D("10")) == D("40.0000")


def test_un_insumo_fuera_del_requerimiento_no_tiene_nada_que_exceder():
    assert exceso_sobre_requerido(None, D("0"), D("999")) is None               # el requerimiento avisa, no bloquea (regla 7)


# ------------------------------------------------------------------ HU-33 precio contra lo habitual
from app.modulos.compras.dominio import desviacion_pct, fuera_de_lo_habitual, referencia_en_unidad_de_compra  # noqa: E402


def test_la_referencia_del_acero_pasa_de_kg_a_varilla_con_el_factor():
    assert referencia_en_unidad_de_compra(D("4.213483"), D("7.12")).quantize(D("0.01")) == D("30.00")   # S/ por kg × kg por varilla
    assert referencia_en_unidad_de_compra(None, D("7.12")) is None                       # sin recepciones no hay promedio


def test_la_desviacion_se_mide_en_porcentaje_hacia_arriba_y_hacia_abajo():
    assert desviacion_pct(D("33.00"), D("30.00")) == D("10.00")
    assert desviacion_pct(D("26.40"), D("30.00")) == D("-12.00")
    assert desviacion_pct(D("30.00"), None) is None


def test_solo_se_marca_lo_que_pasa_el_limite_y_sin_referencia_nada():
    assert fuera_de_lo_habitual(D("10.00"), D("10")) is False        # justo el límite: dentro
    assert fuera_de_lo_habitual(D("10.01"), D("10")) is True
    assert fuera_de_lo_habitual(D("-12.00"), D("10")) is True       # también si se paga mucho menos
    assert fuera_de_lo_habitual(None, D("10")) is False
