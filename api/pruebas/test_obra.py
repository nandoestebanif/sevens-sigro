"""Módulo obra: plazo, periodos, usuarios, asignación y dispositivos (R5, R9, R10)."""
from datetime import date, timedelta

import pytest

from app.modulos.obra.dominio import generar_periodos
from pruebas.conftest import CHECK, EXCLUSION, FORANEA, RESTRICT, UNICO, rechaza


def periodos_en_base(db, proyecto):
    filas = db.execute(
        "SELECT numero, fecha_inicio, fecha_fin FROM obra.periodo WHERE proyecto_id = %s ORDER BY numero", (proyecto,)
    ).fetchall()
    return [(r["numero"], r["fecha_inicio"], r["fecha_fin"]) for r in filas]


# ================================================================== proyecto y periodos
class TestProyecto:
    def test_nace_en_ejecucion(self, db, f):
        p = f.proyecto()
        assert db.execute("SELECT estado FROM obra.proyecto WHERE id = %s", (p,)).fetchone()["estado"] == "en_ejecucion"

    def test_fin_anterior_al_inicio_se_rechaza(self, db, f):
        with rechaza(db, CHECK, "ck_proyecto_plazo"):
            f.proyecto(inicio=date(2026, 10, 20), fin=date(2026, 10, 19))

    def test_nombre_vacio_se_rechaza(self, db, f):
        with rechaza(db, CHECK, "ck_proyecto_nombre"):
            f.proyecto(nombre="   ")

    def test_dos_obras_no_comparten_nombre_aunque_cambien_mayusculas(self, db, f):
        f.proyecto(nombre="Multifamiliar Flores")
        with rechaza(db, UNICO, "uq_proyecto_nombre"):
            f.proyecto(nombre="MULTIFAMILIAR flores")

    def test_dia_de_corte_fuera_de_rango(self, db, f):
        with rechaza(db, CHECK, "ck_proyecto_dia_corte"):
            f.proyecto(dia_corte=8)

    def test_no_se_borra(self, db, f):
        p = f.proyecto()
        with rechaza(db, RESTRICT, "SIGRO-R10"):
            db.execute("DELETE FROM obra.proyecto WHERE id = %s", (p,))

    def test_no_se_vacia_con_truncate(self, db, f):
        with rechaza(db, RESTRICT, "SIGRO-R10"):
            db.execute("TRUNCATE obra.proyecto CASCADE")

    def test_inicio_y_dia_de_corte_no_se_editan(self, db, f):
        p = f.proyecto()
        with rechaza(db, RESTRICT):
            db.execute("UPDATE obra.proyecto SET fecha_inicio = fecha_inicio + 1 WHERE id = %s", (p,))
        with rechaza(db, RESTRICT):
            db.execute("UPDATE obra.proyecto SET dia_corte = 5 WHERE id = %s", (p,))

    def test_una_obra_cerrada_no_se_reabre(self, db, f):
        p = f.proyecto()
        db.execute("UPDATE obra.proyecto SET estado = 'cerrado' WHERE id = %s", (p,))
        with rechaza(db, RESTRICT, "no se reabre"):
            db.execute("UPDATE obra.proyecto SET estado = 'en_ejecucion' WHERE id = %s", (p,))


class TestPeriodos:
    @pytest.mark.parametrize("corte", range(1, 8))
    @pytest.mark.parametrize("inicio,fin", [
        (date(2026, 10, 19), date(2026, 12, 6)),    # lunes a domingo
        (date(2026, 10, 14), date(2026, 11, 4)),    # miércoles a miércoles
        (date(2026, 10, 14), date(2026, 10, 14)),   # un solo día
        (date(2026, 12, 28), date(2027, 2, 3)),     # cruza de año
    ])
    def test_la_base_genera_exactamente_lo_que_calcula_el_dominio(self, db, f, inicio, fin, corte):
        p = f.proyecto(inicio=inicio, fin=fin, dia_corte=corte)
        esperado = [(x.numero, x.inicio, x.fin) for x in generar_periodos(inicio, fin, corte)]
        assert periodos_en_base(db, p) == esperado

    def test_ampliar_plazo_deja_la_misma_particion_que_generar_desde_cero(self, db, f):
        for corte in range(1, 8):
            p = f.proyecto(inicio=date(2026, 10, 14), fin=date(2026, 11, 4), dia_corte=corte)
            nuevos = db.execute("SELECT obra.ampliar_plazo(%s, %s, %s, %s) AS n",
                                (p, date(2026, 11, 27), "adicional de obra", f.usuario())).fetchone()["n"]
            esperado = [(x.numero, x.inicio, x.fin) for x in generar_periodos(date(2026, 10, 14), date(2026, 11, 27), corte)]
            assert periodos_en_base(db, p) == esperado
            assert nuevos >= 1

    def test_los_movimientos_ya_hechos_conservan_su_periodo_al_ampliar(self, db, f):
        eq = f.obra_con_equipo(inicio=date(2026, 10, 14), fin=date(2026, 10, 28))
        antes = db.execute("SELECT id FROM obra.periodo WHERE proyecto_id = %s AND numero = 1", (eq["proyecto"],)).fetchone()["id"]
        db.execute("SELECT obra.ampliar_plazo(%s, %s, %s, %s)", (eq["proyecto"], date(2026, 11, 30), "adicional", eq["residente"]))
        despues = db.execute("SELECT id FROM obra.periodo WHERE proyecto_id = %s AND numero = 1", (eq["proyecto"],)).fetchone()["id"]
        assert antes == despues                     # el periodo 1 sigue siendo la misma fila

    def test_un_periodo_cerrado_no_se_extiende_al_ampliar_el_plazo(self, db, f):
        p = f.proyecto(inicio=date(2026, 10, 14), fin=date(2026, 10, 21), dia_corte=7)   # P2 = lun 19 a mié 21
        u = f.usuario()
        db.execute("UPDATE obra.periodo SET estado='cerrado', cerrado_en=now(), cerrado_por=%s WHERE proyecto_id=%s AND numero=2", (u, p))
        db.execute("SELECT obra.ampliar_plazo(%s, %s, %s, %s)", (p, date(2026, 11, 8), "adicional", u))
        ps = periodos_en_base(db, p)
        assert ps[1] == (2, date(2026, 10, 19), date(2026, 10, 21))      # se queda corto: está cerrado
        assert ps[2][1] == date(2026, 10, 22)                            # el siguiente arranca al día siguiente

    def test_dos_periodos_de_una_obra_no_se_solapan(self, db, f):
        p = f.proyecto()
        with rechaza(db, EXCLUSION, "ex_periodo_sin_solape"):
            db.execute(
                "INSERT INTO obra.periodo (proyecto_id, numero, fecha_inicio, fecha_fin) VALUES (%s, 999, %s, %s)",
                (p, date(2026, 10, 20), date(2026, 10, 22)),
            )

    def test_numero_de_periodo_unico_por_obra(self, db, f):
        p = f.proyecto()
        with rechaza(db, UNICO):
            db.execute("INSERT INTO obra.periodo (proyecto_id, numero, fecha_inicio, fecha_fin) VALUES (%s, 1, '2030-01-01', '2030-01-07')", (p,))

    def test_cerrar_un_periodo_es_irreversible(self, db, f):
        p, u = f.proyecto(), f.usuario()
        db.execute("UPDATE obra.periodo SET estado='cerrado', cerrado_en=now(), cerrado_por=%s WHERE proyecto_id=%s AND numero=1", (u, p))
        with rechaza(db, RESTRICT, "cerrado"):
            db.execute("UPDATE obra.periodo SET estado='abierto', cerrado_en=NULL, cerrado_por=NULL WHERE proyecto_id=%s AND numero=1", (p,))

    def test_el_cierre_exige_fecha(self, db, f):
        p = f.proyecto()
        with rechaza(db, CHECK, "ck_periodo_cierre"):
            db.execute("UPDATE obra.periodo SET estado='cerrado' WHERE proyecto_id=%s AND numero=1", (p,))

    def test_el_fin_de_un_periodo_no_se_edita_a_mano(self, db, f):
        p = f.proyecto()
        with rechaza(db, RESTRICT):
            db.execute("UPDATE obra.periodo SET fecha_fin = fecha_fin + 1 WHERE proyecto_id=%s AND numero=1", (p,))

    def test_no_se_borran_periodos(self, db, f):
        p = f.proyecto()
        with rechaza(db, RESTRICT, "SIGRO-R10"):
            db.execute("DELETE FROM obra.periodo WHERE proyecto_id = %s", (p,))


class TestAmpliarPlazo:
    def test_deja_rastro_con_autor_motivo_y_fechas(self, db, f):
        eq = f.obra_con_equipo()
        db.execute("SELECT obra.ampliar_plazo(%s, %s, %s, %s)", (eq["proyecto"], date(2026, 12, 31), "adicional de acabados", eq["residente"]))
        h = db.execute("SELECT * FROM obra.proyecto_plazo_historial WHERE proyecto_id = %s", (eq["proyecto"],)).fetchall()
        assert len(h) == 1
        assert h[0]["fecha_fin_anterior"] == date(2026, 12, 6) and h[0]["fecha_fin_nueva"] == date(2026, 12, 31)
        assert h[0]["autor"] == eq["residente"] and h[0]["motivo"] == "adicional de acabados"
        assert db.execute("SELECT fecha_fin FROM obra.proyecto WHERE id=%s", (eq["proyecto"],)).fetchone()["fecha_fin"] == date(2026, 12, 31)

    def test_acortar_o_repetir_la_fecha_se_rechaza(self, db, f):
        eq = f.obra_con_equipo()
        for nueva in (date(2026, 12, 6), date(2026, 11, 1)):
            with rechaza(db, CHECK, "posterior"):
                db.execute("SELECT obra.ampliar_plazo(%s, %s, %s, %s)", (eq["proyecto"], nueva, "x", eq["residente"]))

    def test_sin_motivo_se_rechaza(self, db, f):
        eq = f.obra_con_equipo()
        with rechaza(db, CHECK, "ck_proyecto_plazo_historial_motivo"):
            db.execute("SELECT obra.ampliar_plazo(%s, %s, %s, %s)", (eq["proyecto"], date(2026, 12, 31), "  ", eq["residente"]))

    def test_cambiar_el_plazo_con_update_directo_se_rechaza(self, db, f):
        p = f.proyecto()
        with rechaza(db, RESTRICT, "ampliar_plazo"):
            db.execute("UPDATE obra.proyecto SET fecha_fin = '2027-12-31' WHERE id = %s", (p,))

    def test_una_obra_cerrada_no_se_amplia(self, db, f):
        eq = f.obra_con_equipo()
        db.execute("UPDATE obra.proyecto SET estado = 'cerrado' WHERE id = %s", (eq["proyecto"],))
        with rechaza(db, CHECK, "cerrada"):
            db.execute("SELECT obra.ampliar_plazo(%s, %s, %s, %s)", (eq["proyecto"], date(2026, 12, 31), "x", eq["residente"]))

    def test_el_historial_es_un_libro_que_no_se_modifica(self, db, f):
        eq = f.obra_con_equipo()
        db.execute("SELECT obra.ampliar_plazo(%s, %s, %s, %s)", (eq["proyecto"], date(2026, 12, 31), "x", eq["residente"]))
        with rechaza(db, RESTRICT, "SIGRO-R1"):
            db.execute("UPDATE obra.proyecto_plazo_historial SET motivo = 'otro' WHERE proyecto_id = %s", (eq["proyecto"],))
        with rechaza(db, RESTRICT, "SIGRO-R1"):
            db.execute("DELETE FROM obra.proyecto_plazo_historial WHERE proyecto_id = %s", (eq["proyecto"],))


# ======================================================================= usuarios (R10)
class TestUsuario:
    @pytest.mark.parametrize("clave", ["claro123", "", "md5:abc", "$1$salt$hash", "argon2id$sin-signo-dolar"])
    def test_la_contrasena_nunca_en_claro(self, db, clave):
        with rechaza(db, CHECK, "ck_usuario_hash_clave"):
            db.execute("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES ('X', 'xuser', %s)", (clave,))

    @pytest.mark.parametrize("hash_", ["$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA", "$2b$12$abcdefghijklmnopqrstuv"])
    def test_acepta_argon2_y_bcrypt(self, db, hash_):
        db.execute("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES ('X', %s, %s)", (f"u{abs(hash(hash_))}", hash_))

    def test_el_usuario_es_unico_sin_importar_mayusculas(self, db, f):
        f.usuario("Carlos.Mamani")
        with rechaza(db, UNICO, "uq_usuario_usuario"):
            f.usuario("carlos.mamani")

    def test_la_baja_es_un_estado_con_fecha_no_un_borrado(self, db, f):
        u = f.usuario()
        with rechaza(db, RESTRICT, "SIGRO-R10"):
            db.execute("DELETE FROM obra.usuario WHERE id = %s", (u,))
        with rechaza(db, CHECK, "ck_usuario_baja_con_fecha"):
            db.execute("UPDATE obra.usuario SET estado = 'baja' WHERE id = %s", (u,))
        db.execute("UPDATE obra.usuario SET estado = 'baja', fecha_baja = now() WHERE id = %s", (u,))


# =================================================== asignación: el rol vive junto a la obra (R9)
class TestAsignacion:
    def test_la_misma_persona_es_almacenero_en_una_obra_y_nada_en_otra(self, db, f):
        u, p1, p2 = f.usuario(), f.proyecto(), f.proyecto()
        f.asignar(u, p1, "almacenero")
        n = db.execute("SELECT count(*) AS n FROM obra.asignacion WHERE usuario_id = %s", (u,)).fetchone()["n"]
        assert n == 1       # y en p2 no tiene fila: sin asignación, sin permiso

    def test_la_misma_persona_puede_tener_distinto_rol_en_otra_obra(self, db, f):
        u, p1, p2 = f.usuario(), f.proyecto(), f.proyecto()
        f.asignar(u, p1, "almacenero")
        f.asignar(u, p2, "residente")

    def test_no_dos_roles_vigentes_en_la_misma_obra(self, db, f):
        u, p = f.usuario(), f.proyecto()
        f.asignar(u, p, "almacenero")
        with rechaza(db, UNICO, "uq_asignacion_vigente"):
            f.asignar(u, p, "residente")

    def test_rol_inexistente_se_rechaza(self, db, f):
        with rechaza(db, CHECK, "ck_asignacion_rol"):
            f.asignar(f.usuario(), f.proyecto(), "capataz")

    def test_retirar_y_volver_a_asignar_conserva_el_rastro(self, db, f):
        u, p, adm = f.usuario(), f.proyecto(), f.usuario()
        a = f.asignar(u, p, "almacenero")
        db.execute("UPDATE obra.asignacion SET fecha_retiro = now(), retirado_por = %s WHERE id = %s", (adm, a))
        f.asignar(u, p, "residente")        # ya puede: la anterior no está vigente
        assert db.execute("SELECT count(*) AS n FROM obra.asignacion WHERE usuario_id=%s AND proyecto_id=%s", (u, p)).fetchone()["n"] == 2

    def test_una_asignacion_no_se_edita_ni_se_borra(self, db, f):
        a = f.asignar(f.usuario(), f.proyecto(), "almacenero")
        with rechaza(db, RESTRICT, "SIGRO-R9"):
            db.execute("UPDATE obra.asignacion SET rol = 'administrador' WHERE id = %s", (a,))
        with rechaza(db, RESTRICT, "SIGRO-R10"):
            db.execute("DELETE FROM obra.asignacion WHERE id = %s", (a,))

    def test_una_asignacion_retirada_no_se_retira_otra_vez(self, db, f):
        adm = f.usuario()
        a = f.asignar(f.usuario(), f.proyecto(), "almacenero")
        db.execute("UPDATE obra.asignacion SET fecha_retiro = now(), retirado_por = %s WHERE id = %s", (adm, a))
        with rechaza(db, RESTRICT, "ya fue retirada"):
            db.execute("UPDATE obra.asignacion SET fecha_retiro = now(), retirado_por = %s WHERE id = %s", (adm, a))

    def test_retirar_sin_decir_quien_se_rechaza(self, db, f):
        a = f.asignar(f.usuario(), f.proyecto(), "almacenero")
        with rechaza(db, CHECK, "ck_asignacion_retiro"):
            db.execute("UPDATE obra.asignacion SET fecha_retiro = now() WHERE id = %s", (a,))


# ======================================================== dispositivos: máximo dos por obra (R5)
class TestDispositivo:
    def test_almacenero_y_ssoma_pueden_tener_su_dispositivo(self, db, f):
        eq = f.obra_con_equipo()
        f.dispositivo(eq["proyecto"], eq["almacenero"], activo_por=eq["admin"])
        f.dispositivo(eq["proyecto"], eq["ssoma"], activo_por=eq["admin"])
        n = db.execute("SELECT count(*) AS n FROM obra.dispositivo WHERE proyecto_id=%s AND estado='activo'", (eq["proyecto"],)).fetchone()["n"]
        assert n == 2

    def test_el_tercer_dispositivo_se_rechaza(self, db, f):
        eq = f.obra_con_equipo()
        f.dispositivo(eq["proyecto"], eq["almacenero"], activo_por=eq["admin"])
        f.dispositivo(eq["proyecto"], eq["ssoma"], activo_por=eq["admin"])
        otro = f.usuario()
        f.asignar(otro, eq["proyecto"], "almacenero", eq["admin"])
        pendiente = f.dispositivo(eq["proyecto"], otro)
        with rechaza(db, CHECK, "ya tiene dos dispositivos"):
            f.activar_dispositivo(pendiente, eq["admin"])

    def test_se_puede_solicitar_pero_no_activar_el_tercero_hasta_revocar_uno(self, db, f):
        eq = f.obra_con_equipo()
        d1 = f.dispositivo(eq["proyecto"], eq["almacenero"], activo_por=eq["admin"])
        f.dispositivo(eq["proyecto"], eq["ssoma"], activo_por=eq["admin"])
        otro = f.usuario()
        f.asignar(otro, eq["proyecto"], "almacenero", eq["admin"])
        pendiente = f.dispositivo(eq["proyecto"], otro)
        db.execute("UPDATE obra.dispositivo SET estado='revocado', fecha_revocacion=now(), revocado_por=%s WHERE id=%s", (eq["admin"], d1))
        f.activar_dispositivo(pendiente, eq["admin"])       # ahora sí

    def test_el_residente_no_tiene_dispositivo_de_registro(self, db, f):
        eq = f.obra_con_equipo()
        with rechaza(db, CHECK, "solo el almacenero o el ingeniero SSOMA"):
            f.dispositivo(eq["proyecto"], eq["residente"], activo_por=eq["admin"])

    def test_quien_no_esta_asignado_a_la_obra_no_tiene_dispositivo(self, db, f):
        eq = f.obra_con_equipo()
        ajeno = f.usuario()
        with rechaza(db, CHECK, "SIGRO-R5"):
            f.dispositivo(eq["proyecto"], ajeno, activo_por=eq["admin"])

    def test_un_responsable_no_tiene_dos_dispositivos_activos(self, db, f):
        eq = f.obra_con_equipo()
        f.dispositivo(eq["proyecto"], eq["almacenero"], activo_por=eq["admin"])
        with rechaza(db, UNICO, "uq_dispositivo_activo_por_usuario"):
            f.dispositivo(eq["proyecto"], eq["almacenero"], activo_por=eq["admin"])

    def test_el_mismo_equipo_no_se_autoriza_para_dos_responsables(self, db, f):
        eq = f.obra_con_equipo()
        f.dispositivo(eq["proyecto"], eq["almacenero"], activo_por=eq["admin"])
        # el mismo celular, presentado ahora por el SSOMA de la misma obra
        with rechaza(db, UNICO, "uq_dispositivo_activo_identificador"):
            db.execute(
                "INSERT INTO obra.dispositivo (proyecto_id, usuario_id, identificador, estado, autorizado_por, fecha_autorizacion) "
                "SELECT proyecto_id, %s, identificador, 'activo', %s, now() FROM obra.dispositivo "
                "WHERE proyecto_id = %s AND usuario_id = %s",
                (eq["ssoma"], eq["admin"], eq["proyecto"], eq["almacenero"]),
            )

    def test_activo_exige_quien_lo_autorizo(self, db, f):
        eq = f.obra_con_equipo()
        with rechaza(db, CHECK, "ck_dispositivo_autorizacion"):
            db.execute(
                "INSERT INTO obra.dispositivo (proyecto_id, usuario_id, identificador, estado) VALUES (%s,%s,'android-abcdefgh','activo')",
                (eq["proyecto"], eq["almacenero"]),
            )

    def test_identificador_con_formato_invalido(self, db, f):
        eq = f.obra_con_equipo()
        with rechaza(db, CHECK, "ck_dispositivo_identificador"):
            db.execute("INSERT INTO obra.dispositivo (proyecto_id, usuario_id, identificador) VALUES (%s,%s,'corto')",
                       (eq["proyecto"], eq["almacenero"]))

    def test_un_dispositivo_revocado_no_vuelve(self, db, f):
        eq = f.obra_con_equipo()
        d = f.dispositivo(eq["proyecto"], eq["almacenero"], activo_por=eq["admin"])
        db.execute("UPDATE obra.dispositivo SET estado='revocado', fecha_revocacion=now(), revocado_por=%s WHERE id=%s", (eq["admin"], d))
        with rechaza(db, RESTRICT, "revocado no vuelve"):
            db.execute("UPDATE obra.dispositivo SET estado='activo', autorizado_por=%s, fecha_autorizacion=now(), "
                       "fecha_revocacion=NULL WHERE id=%s", (eq["admin"], d))

    def test_obra_responsable_e_identificador_no_se_editan(self, db, f):
        eq = f.obra_con_equipo()
        d = f.dispositivo(eq["proyecto"], eq["almacenero"])
        with rechaza(db, RESTRICT, "no se editan"):
            db.execute("UPDATE obra.dispositivo SET identificador = 'android-otro-9999' WHERE id = %s", (d,))

    def test_retirar_la_asignacion_revoca_su_dispositivo_en_el_mismo_instante(self, db, f):
        eq = f.obra_con_equipo()
        d = f.dispositivo(eq["proyecto"], eq["almacenero"], activo_por=eq["admin"])
        db.execute("UPDATE obra.asignacion SET fecha_retiro = now(), retirado_por = %s "
                   "WHERE usuario_id = %s AND proyecto_id = %s", (eq["admin"], eq["almacenero"], eq["proyecto"]))
        fila = db.execute("SELECT estado, motivo_revocacion, revocado_por FROM obra.dispositivo WHERE id = %s", (d,)).fetchone()
        assert fila["estado"] == "revocado" and fila["revocado_por"] == eq["admin"]
        assert "asignación retirada" in fila["motivo_revocacion"]

    def test_un_dispositivo_no_se_borra(self, db, f):
        eq = f.obra_con_equipo()
        d = f.dispositivo(eq["proyecto"], eq["almacenero"])
        with rechaza(db, RESTRICT, "SIGRO-R10"):
            db.execute("DELETE FROM obra.dispositivo WHERE id = %s", (d,))

    def test_el_par_dispositivo_obra_sirve_de_clave_foranea_para_movimiento(self, db, f):
        """Contrato con Henry: movimiento referencia (dispositivo_id, proyecto_id)."""
        eq = f.obra_con_equipo()
        d = f.dispositivo(eq["proyecto"], eq["almacenero"], activo_por=eq["admin"])
        otra = f.proyecto()
        db.execute("CREATE TABLE public.mov_prueba (dispositivo_id bigint, proyecto_id bigint, "      # DDL transaccional: se revierte
                   "FOREIGN KEY (dispositivo_id, proyecto_id) REFERENCES obra.dispositivo (id, proyecto_id))")
        db.execute("INSERT INTO mov_prueba VALUES (%s, %s)", (d, eq["proyecto"]))
        with rechaza(db, FORANEA):
            db.execute("INSERT INTO mov_prueba VALUES (%s, %s)", (d, otra))     # dispositivo de OTRA obra
