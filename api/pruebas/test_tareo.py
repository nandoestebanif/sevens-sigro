"""Módulo tareo: trabajador y jornada (R1, R3, R10)."""
from datetime import date
from uuid import uuid4

import pytest

from pruebas.conftest import CHECK, FORANEA, RESTRICT, SIN_PERMISO, UNICO, como_aplicacion, rechaza


@pytest.fixture
def obra(f):
    """Obra del 19-oct al 6-dic, con equipo y un peón ingresado el primer día."""
    eq = f.obra_con_equipo()
    eq["peon"] = f.trabajador(eq["proyecto"], eq["almacenero"])
    return eq


def vigentes(db, trabajador):
    return db.execute("SELECT * FROM tareo.jornada_vigente WHERE trabajador_id = %s ORDER BY fecha", (trabajador,)).fetchall()


# ======================================================================= trabajador (HU-62)
class TestTrabajador:
    def test_documento_dni_son_ocho_digitos(self, db, f, obra):
        for malo in ("1234567", "123456789", "1234567a"):
            with rechaza(db, CHECK, "ck_trabajador_documento"):
                f.trabajador(obra["proyecto"], obra["almacenero"], documento=malo)

    def test_no_dos_veces_en_la_misma_obra(self, db, f, obra):
        f.trabajador(obra["proyecto"], obra["almacenero"], documento="45678912")
        with rechaza(db, UNICO, "uq_trabajador_proyecto_id_documento"):
            f.trabajador(obra["proyecto"], obra["almacenero"], documento="45678912")

    def test_si_puede_estar_en_dos_obras(self, db, f, obra):
        otra = f.proyecto()
        f.trabajador(obra["proyecto"], obra["almacenero"], documento="45678912")
        f.trabajador(otra, obra["almacenero"], documento="45678912")

    def test_categoria_valida(self, db, f, obra):
        with rechaza(db, CHECK, "ck_trabajador_categoria"):
            f.trabajador(obra["proyecto"], obra["almacenero"], categoria="capataz")

    def test_se_incorpora_en_cualquier_momento_del_plazo(self, db, f, obra):
        f.trabajador(obra["proyecto"], obra["almacenero"], ingreso=date(2026, 11, 16))

    def test_no_ingresa_fuera_del_plazo_de_la_obra(self, db, f, obra):
        for fecha in (date(2026, 10, 18), date(2026, 12, 7)):
            with rechaza(db, CHECK, "SIGRO-T5"):
                f.trabajador(obra["proyecto"], obra["almacenero"], ingreso=fecha)

    def test_no_se_borra(self, db, f, obra):
        with rechaza(db, RESTRICT, "SIGRO-R10"):
            db.execute("DELETE FROM tareo.trabajador WHERE id = %s", (obra["peon"],))

    def test_baja_con_fecha_de_salida_conserva_sus_jornadas(self, db, f, obra):
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 20))
        db.execute("UPDATE tareo.trabajador SET fecha_salida = %s WHERE id = %s", (date(2026, 10, 30), obra["peon"]))
        assert len(vigentes(db, obra["peon"])) == 1

    def test_no_se_da_de_baja_antes_de_su_ultima_jornada(self, db, f, obra):
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 28))
        with rechaza(db, CHECK, "SIGRO-T6"):
            db.execute("UPDATE tareo.trabajador SET fecha_salida = %s WHERE id = %s", (date(2026, 10, 25), obra["peon"]))

    def test_la_fecha_de_salida_no_puede_ser_anterior_al_ingreso(self, db, f, obra):
        with rechaza(db, CHECK, "ck_trabajador_salida"):
            db.execute("UPDATE tareo.trabajador SET fecha_salida = %s WHERE id = %s", (date(2026, 10, 1), obra["peon"]))

    def test_la_baja_registrada_no_se_cambia(self, db, f, obra):
        db.execute("UPDATE tareo.trabajador SET fecha_salida = %s WHERE id = %s", (date(2026, 10, 30), obra["peon"]))
        with rechaza(db, RESTRICT, "baja ya está registrada"):
            db.execute("UPDATE tareo.trabajador SET fecha_salida = %s WHERE id = %s", (date(2026, 11, 5), obra["peon"]))

    def test_documento_e_ingreso_no_se_editan(self, db, f, obra):
        with rechaza(db, RESTRICT, "SIGRO-T5"):
            db.execute("UPDATE tareo.trabajador SET documento = '99999999' WHERE id = %s", (obra["peon"],))
        with rechaza(db, RESTRICT, "SIGRO-T5"):
            db.execute("UPDATE tareo.trabajador SET fecha_ingreso = fecha_ingreso + 1 WHERE id = %s", (obra["peon"],))


# ====================================================================== jornada (HU-63)
class TestJornada:
    def test_registra_presente_con_horas_normales_y_extra_por_separado(self, db, f, obra):
        j = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="8", extra="2")
        fila = db.execute("SELECT horas_normales, horas_extra FROM tareo.jornada WHERE id = %s", (j,)).fetchone()
        assert (fila["horas_normales"], fila["horas_extra"]) == (8, 2)

    def test_el_servidor_asigna_el_periodo_desde_la_fecha(self, db, f, obra):
        j = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 28))
        per = db.execute("SELECT p.numero, p.fecha_inicio, p.fecha_fin FROM tareo.jornada j JOIN obra.periodo p ON p.id = j.periodo_id "
                         "WHERE j.id = %s", (j,)).fetchone()
        assert per["numero"] == 2 and per["fecha_inicio"] <= date(2026, 10, 28) <= per["fecha_fin"]

    def test_el_cliente_no_puede_imponer_el_periodo(self, db, f, obra):
        """Aunque el cliente mande otro periodo_id, el servidor lo reemplaza por el de la fecha."""
        otro = db.execute("SELECT id FROM obra.periodo WHERE proyecto_id = %s AND numero = 5", (obra["proyecto"],)).fetchone()["id"]
        j = db.execute(
            "INSERT INTO tareo.jornada (proyecto_id, trabajador_id, fecha, estado, horas_normales, categoria, periodo_id, registrado_por) "
            "VALUES (%s, %s, %s, 'presente', 8, 'oficial', %s, %s) RETURNING id, periodo_id, categoria",
            (obra["proyecto"], obra["peon"], date(2026, 10, 20), otro, obra["almacenero"]),
        ).fetchone()
        assert j["periodo_id"] != otro and j["categoria"] == "peon"      # tampoco impone la categoría

    @pytest.mark.parametrize("fecha", [date(2026, 10, 18), date(2026, 12, 7)])
    def test_fuera_del_plazo_de_la_obra_se_rechaza(self, db, f, obra, fecha):
        # el peón ingresó el 19; para probar el plazo se usa uno con ingreso amplio
        t = f.trabajador(obra["proyecto"], obra["almacenero"], ingreso=date(2026, 10, 19))
        with rechaza(db, CHECK, "SIGRO-T1"):
            f.jornada(obra["proyecto"], t, obra["almacenero"], fecha=fecha)

    def test_no_hay_jornada_antes_de_que_ingrese(self, db, f, obra):
        t = f.trabajador(obra["proyecto"], obra["almacenero"], ingreso=date(2026, 11, 16))
        with rechaza(db, CHECK, "SIGRO-T2"):
            f.jornada(obra["proyecto"], t, obra["almacenero"], fecha=date(2026, 11, 10))

    def test_no_hay_jornada_despues_de_su_salida(self, db, f, obra):
        db.execute("UPDATE tareo.trabajador SET fecha_salida = %s WHERE id = %s", (date(2026, 10, 30), obra["peon"]))
        with rechaza(db, CHECK, "SIGRO-T2"):
            f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 11, 2))

    def test_un_trabajador_no_tiene_dos_asistencias_el_mismo_dia(self, db, f, obra):
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"])
        with rechaza(db, UNICO, "uq_jornada_original"):
            f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="4")

    def test_trabajador_de_otra_obra_se_rechaza(self, db, f, obra):
        otra = f.obra_con_equipo()
        with rechaza(db, FORANEA, "fk_jornada_trabajador_id_trabajador"):
            f.jornada(otra["proyecto"], obra["peon"], otra["almacenero"])

    @pytest.mark.parametrize("estado,normales,extra,restriccion", [
        ("presente", "0", "0", "ck_jornada_horas_segun_estado"),    # presente sin horas
        ("ausente", "8", "0", "ck_jornada_horas_segun_estado"),     # ausente con horas
        ("permiso", "0", "2", "ck_jornada_horas_segun_estado"),
        ("presente", "25", "0", "ck_jornada_horas_rango"),
        ("presente", "16", "9", "ck_jornada_horas_rango"),          # suma > 24
        ("presente", "-1", "0", "ck_jornada_horas_rango"),
        ("vacaciones", "0", "0", "ck_jornada_estado"),
    ])
    def test_horas_coherentes_con_el_estado(self, db, f, obra, estado, normales, extra, restriccion):
        with rechaza(db, CHECK, restriccion):
            f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], estado=estado, normales=normales, extra=extra)

    def test_ausente_y_permiso_se_registran_sin_horas(self, db, f, obra):
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 20), estado="ausente", normales="0")
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 21), estado="permiso", normales="0")

    def test_se_puede_trabajar_solo_horas_extra_un_feriado(self, db, f, obra):
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="0", extra="6")


# ======================================================== corregir = insertar otra (R1, HU-63)
class TestCorreccion:
    def test_la_correccion_deja_constancia_del_original(self, db, f, obra):
        j1 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="8")
        j2 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="6", corrige_a=j1, motivo="se fue temprano")
        assert db.execute("SELECT horas_normales FROM tareo.jornada WHERE id = %s", (j1,)).fetchone()["horas_normales"] == 8   # intacto
        v = vigentes(db, obra["peon"])
        assert len(v) == 1 and v[0]["id"] == j2 and v[0]["horas_normales"] == 6

    def test_se_puede_corregir_una_correccion_y_la_cadena_es_lineal(self, db, f, obra):
        j1 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="8")
        j2 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="6", corrige_a=j1, motivo="m1")
        j3 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="7", corrige_a=j2, motivo="m2")
        assert [v["id"] for v in vigentes(db, obra["peon"])] == [j3]

    def test_cada_jornada_se_corrige_a_lo_sumo_una_vez(self, db, f, obra):
        """Sin esto la historia de un día se bifurca y habría dos 'vigentes'."""
        j1 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"])
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="6", corrige_a=j1, motivo="m1")
        with rechaza(db, UNICO, "uq_jornada_correccion_unica"):
            f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="5", corrige_a=j1, motivo="m2")

    def test_la_correccion_exige_motivo(self, db, f, obra):
        j1 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"])
        for motivo in (None, "   "):
            with rechaza(db, CHECK, "ck_jornada_correccion"):
                f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], normales="6", corrige_a=j1, motivo=motivo)

    def test_un_motivo_sin_correccion_se_rechaza(self, db, f, obra):
        with rechaza(db, CHECK, "ck_jornada_correccion"):
            f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], motivo="sin nada que corregir")

    def test_la_correccion_es_del_mismo_trabajador_y_la_misma_fecha(self, db, f, obra):
        otro = f.trabajador(obra["proyecto"], obra["almacenero"])
        j1 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 20))
        with rechaza(db, FORANEA, "fk_jornada_corrige_a_id_jornada"):      # otro trabajador
            f.jornada(obra["proyecto"], otro, obra["almacenero"], fecha=date(2026, 10, 20), corrige_a=j1, motivo="x")
        with rechaza(db, FORANEA, "fk_jornada_corrige_a_id_jornada"):      # otra fecha
            f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 21), corrige_a=j1, motivo="x")

    def test_corregir_no_puede_cambiar_el_trabajador_ni_el_dia_de_lo_corregido(self, db, f, obra):
        j1 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"])
        with rechaza(db, RESTRICT, "SIGRO-R1"):
            db.execute("UPDATE tareo.jornada SET fecha = fecha + 1 WHERE id = %s", (j1,))

    def test_una_correccion_puede_cambiar_ausente_por_presente(self, db, f, obra):
        j1 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], estado="ausente", normales="0")
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], estado="presente", normales="8", corrige_a=j1, motivo="sí vino")
        assert vigentes(db, obra["peon"])[0]["estado"] == "presente"


# =================================================================== el libro no se toca (R1)
class TestLibro:
    def test_update_y_delete_se_rechazan_hasta_para_el_dueno(self, db, f, obra):
        j = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"])
        with rechaza(db, RESTRICT, "SIGRO-R1"):
            db.execute("UPDATE tareo.jornada SET horas_normales = 1 WHERE id = %s", (j,))
        with rechaza(db, RESTRICT, "SIGRO-R1"):
            db.execute("DELETE FROM tareo.jornada WHERE id = %s", (j,))
        with rechaza(db, RESTRICT, "SIGRO-R1"):
            db.execute("TRUNCATE tareo.jornada")

    def test_el_usuario_de_la_aplicacion_ni_siquiera_tiene_permiso(self, db, f, obra):
        j = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"])
        como_aplicacion(db)
        with rechaza(db, SIN_PERMISO):
            db.execute("UPDATE tareo.jornada SET horas_normales = 1 WHERE id = %s", (j,))
        with rechaza(db, SIN_PERMISO):
            db.execute("DELETE FROM tareo.jornada WHERE id = %s", (j,))

    def test_un_reenvio_con_el_mismo_uuid_no_duplica(self, db, f, obra):
        u = uuid4()
        sql = ("INSERT INTO tareo.jornada (uuid_cliente, proyecto_id, trabajador_id, fecha, estado, horas_normales, "
               "categoria, periodo_id, registrado_por) VALUES (%s, %s, %s, %s, 'presente', 8, 'peon', 1, %s)")
        args = (u, obra["proyecto"], obra["peon"], date(2026, 10, 20), obra["almacenero"])
        db.execute(sql, args)
        with rechaza(db, UNICO):                 # el reenvío idéntico no inserta nada
            db.execute(sql, args)
        with rechaza(db, UNICO, "uq_jornada_uuid_cliente"):   # y el mismo uuid nunca nombra a dos jornadas
            db.execute(sql, (u, obra["proyecto"], obra["peon"], date(2026, 10, 21), obra["almacenero"]))

    def test_el_uuid_cliente_es_opcional(self, db, f, obra):
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 20))
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 21))   # ambos sin uuid


# ============================================ periodo cerrado: nuevas no, correcciones sí (D-T2)
class TestPeriodoCerrado:
    def _cerrar(self, db, proyecto, numero, por):
        db.execute("UPDATE obra.periodo SET estado='cerrado', cerrado_en=now(), cerrado_por=%s WHERE proyecto_id=%s AND numero=%s",
                   (por, proyecto, numero))

    def test_no_admite_jornadas_nuevas(self, db, f, obra):
        self._cerrar(db, obra["proyecto"], 1, obra["residente"])
        with rechaza(db, CHECK, "SIGRO-T4"):
            f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 20))

    def test_si_admite_correcciones_con_motivo(self, db, f, obra):
        j1 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 20))
        self._cerrar(db, obra["proyecto"], 1, obra["residente"])
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 20), normales="6",
                  corrige_a=j1, motivo="corrección posterior al cierre")


# ===================================================== categoría congelada al registrar (D-T1)
class TestCategoriaCongelada:
    def test_un_ascenso_no_reprecia_las_horas_pasadas(self, db, f, obra):
        j1 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 20))
        db.execute("UPDATE tareo.trabajador SET categoria = 'oficial' WHERE id = %s", (obra["peon"],))
        j2 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 21))
        cats = {r["id"]: r["categoria"] for r in db.execute("SELECT id, categoria FROM tareo.jornada WHERE id IN (%s, %s)", (j1, j2))}
        assert cats[j1] == "peon" and cats[j2] == "oficial"


# ================================================================ horas acumuladas (HU-64)
class TestHorasAcumuladas:
    def test_suma_solo_lo_vigente(self, db, f, obra):
        j1 = f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 20), normales="8", extra="2")
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 20), normales="6", extra="0",
                  corrige_a=j1, motivo="se fue temprano")
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 21), normales="8")
        f.jornada(obra["proyecto"], obra["peon"], obra["almacenero"], fecha=date(2026, 10, 22), estado="ausente", normales="0")
        fila = db.execute("SELECT * FROM tareo.horas_acumuladas WHERE trabajador_id = %s", (obra["peon"],)).fetchone()
        assert fila["horas_normales"] == 14 and fila["horas_extra"] == 0       # 6 + 8: el 8+2 original no cuenta
        assert fila["dias_presente"] == 2 and fila["dias_ausente"] == 1
