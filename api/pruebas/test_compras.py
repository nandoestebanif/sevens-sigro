"""Módulo compras: proveedor, compra y detalle; solicitud de materiales y su estado derivado."""
from datetime import date

import pytest

from app.modulos.compras.dominio import ruc_valido
from pruebas.conftest import CHECK, FORANEA, RESTRICT, UNICO, rechaza
from pruebas.fabrica import ruc_nuevo


@pytest.fixture
def obra(f):
    eq = f.obra_con_equipo()
    eq["proveedor"] = f.proveedor()
    return eq


# ====================================================================== proveedor (HU-29/30)
class TestProveedor:
    def test_ruc_real_de_sevens_se_acepta_como_validado(self, db, f):
        f.proveedor(ruc="20606456892", validado=True)

    def test_el_ruc_es_unico(self, db, f):
        f.proveedor(ruc="20606456892")
        with rechaza(db, UNICO, "uq_proveedor_ruc"):
            f.proveedor(ruc="20606456892")

    @pytest.mark.parametrize("ruc", ["20606456893", "10000000000", "30606456892", "2060645689"])
    def test_validado_exige_ruc_con_digito_verificador_correcto(self, db, f, ruc):
        with rechaza(db, CHECK, "ck_proveedor_validado_ruc_correcto"):
            f.proveedor(ruc=ruc, validado=True)

    @pytest.mark.parametrize("documento", ["123456", "12345678", "123456789", "1234567890123"])
    def test_los_documentos_irregulares_de_la_lista_real_entran_sin_validar(self, db, f, documento):
        """12 de los 626 proveedores del cliente traen 6/8/9/13 dígitos: se cargan, no se validan."""
        f.proveedor(ruc=documento, validado=False)

    def test_validado_y_fecha_de_validacion_van_juntos(self, db, f):
        with rechaza(db, CHECK, "ck_proveedor_validacion_con_fecha"):
            db.execute("INSERT INTO compras.proveedor (ruc, razon_social, estado_validacion) VALUES (%s, 'X SAC', 'validado')",
                       (ruc_nuevo(),))

    def test_la_base_y_el_dominio_coinciden_en_que_es_un_ruc_valido(self, db):
        muestras = ["20606456892", "20606456893", "10456789015", "20100070970", "20", "", "20606456892 ", "abcdefghijk"]
        for m in muestras:
            en_base = db.execute("SELECT compras.ruc_valido(%s) AS v", (m,)).fetchone()["v"]
            assert en_base == ruc_valido(m), m

    def test_el_ruc_no_se_edita(self, db, f):
        p = f.proveedor()
        with rechaza(db, RESTRICT, "RUC"):
            db.execute("UPDATE compras.proveedor SET ruc = '20606456892' WHERE id = %s", (p,))

    def test_la_baja_es_un_estado_con_fecha(self, db, f):
        p = f.proveedor()
        with rechaza(db, RESTRICT, "SIGRO-R10"):
            db.execute("DELETE FROM compras.proveedor WHERE id = %s", (p,))
        with rechaza(db, CHECK, "ck_proveedor_baja_con_fecha"):
            db.execute("UPDATE compras.proveedor SET estado = 'baja' WHERE id = %s", (p,))
        db.execute("UPDATE compras.proveedor SET estado = 'baja', fecha_baja = now() WHERE id = %s", (p,))

    def test_un_proveedor_de_baja_no_admite_compras_nuevas(self, db, f, obra):
        db.execute("UPDATE compras.proveedor SET estado='baja', fecha_baja=now() WHERE id = %s", (obra["proveedor"],))
        with rechaza(db, CHECK, "SIGRO-C1"):
            f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])


# ============================================================================= compra (HU-32)
class TestCompra:
    def test_registra_con_base_igv_y_total(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], base="1000.00", igv="180.00")
        assert db.execute("SELECT total FROM compras.compra WHERE id = %s", (c,)).fetchone()["total"] == 1180

    def test_el_total_es_base_mas_igv(self, db, f, obra):
        with rechaza(db, CHECK, "ck_compra_montos"):
            f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], base="1000.00", igv="180.00", total="1100.00")

    def test_montos_negativos_se_rechazan(self, db, f, obra):
        with rechaza(db, CHECK, "ck_compra_montos"):
            f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], base="-10.00", igv="0.00", total="-10.00")

    def test_no_dos_comprobantes_con_el_mismo_tipo_y_numero_del_mismo_proveedor(self, db, f, obra):
        f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], numero="F001-4512")
        with rechaza(db, UNICO, "uq_compra_proveedor_id_tipo_comprobante_numero_comprobante"):
            f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], numero="F001-4512")

    def test_el_mismo_numero_si_puede_repetirse_en_otro_proveedor_o_tipo(self, db, f, obra):
        f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], numero="F001-4512")
        f.compra(obra["proyecto"], f.proveedor(), obra["residente"], numero="F001-4512")
        f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], numero="F001-4512", tipo="boleta")

    @pytest.mark.parametrize("numero", ["4512", "F001_4512", "F0012-4512", "F001-", "F001-45121234567"])
    def test_formato_del_numero_de_comprobante(self, db, f, obra, numero):
        with rechaza(db, CHECK, "ck_compra_numero_comprobante"):
            f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], numero=numero)

    def test_una_obra_cerrada_no_admite_compras_nuevas(self, db, f, obra):
        db.execute("UPDATE obra.proyecto SET estado='cerrado' WHERE id = %s", (obra["proyecto"],))
        with rechaza(db, CHECK, "SIGRO-C2"):
            f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])

    def test_registrar_la_compra_no_toca_ningun_movimiento(self, db, f, obra):
        """El esquema compras no referencia movimientos: el stock lo mueve la recepción en almacén."""
        refs = db.execute(
            "SELECT count(*) AS n FROM information_schema.constraint_column_usage u "
            "JOIN information_schema.table_constraints c ON c.constraint_name = u.constraint_name "
            "WHERE c.table_schema = 'compras' AND u.table_name = 'movimiento'"
        ).fetchone()["n"]
        assert refs == 0

    # ------------------------------------------------------- nada se edita, se anula (R10)
    def test_una_compra_registrada_no_se_edita(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        for set_ in ("total = 1", "base_imponible = 1", "proveedor_id = proveedor_id", "numero_comprobante = 'F002-1'",
                     "fecha = fecha + 1"):
            if set_ == "proveedor_id = proveedor_id":
                continue         # no cambia nada: no cuenta como edición
            with rechaza(db, RESTRICT, "no se edita"):
                db.execute(f"UPDATE compras.compra SET {set_} WHERE id = %s", (c,))

    def test_se_anula_con_motivo_autor_y_fecha(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        db.execute("UPDATE compras.compra SET estado='anulada', motivo_anulacion='factura duplicada', "
                   "anulada_por=%s, anulada_en=now() WHERE id=%s", (obra["residente"], c))
        assert db.execute("SELECT estado FROM compras.compra WHERE id=%s", (c,)).fetchone()["estado"] == "anulada"

    def test_anular_sin_motivo_se_rechaza(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        with rechaza(db, CHECK, "ck_compra_anulacion"):
            db.execute("UPDATE compras.compra SET estado='anulada', anulada_por=%s, anulada_en=now() WHERE id=%s",
                       (obra["residente"], c))

    def test_una_compra_anulada_no_se_toca_mas(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        db.execute("UPDATE compras.compra SET estado='anulada', motivo_anulacion='x', anulada_por=%s, anulada_en=now() WHERE id=%s",
                   (obra["residente"], c))
        with rechaza(db, RESTRICT, "ya está anulada"):
            db.execute("UPDATE compras.compra SET motivo_anulacion='otro' WHERE id=%s", (c,))

    def test_anular_y_editar_a_la_vez_se_rechaza(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        with rechaza(db, RESTRICT, "no se edita"):
            db.execute("UPDATE compras.compra SET estado='anulada', motivo_anulacion='x', anulada_por=%s, anulada_en=now(), "
                       "base_imponible = 1, total = 1 + igv WHERE id=%s", (obra["residente"], c))

    def test_no_se_borra(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        with rechaza(db, RESTRICT, "SIGRO-R10"):
            db.execute("DELETE FROM compras.compra WHERE id = %s", (c,))


class TestDetalleYAdjuntos:
    def test_detalle_con_cantidad_y_precio(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        f.detalle(c, f.insumo(), cantidad="36357", unidad="kg", precio="4.54")

    @pytest.mark.parametrize("cantidad,precio,restriccion", [("0", "1", "ck_compra_detalle_cantidad"),
                                                              ("-5", "1", "ck_compra_detalle_cantidad"),
                                                              ("5", "-1", "ck_compra_detalle_precio")])
    def test_cantidad_positiva_y_precio_no_negativo(self, db, f, obra, cantidad, precio, restriccion):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        with rechaza(db, CHECK, restriccion):
            f.detalle(c, f.insumo(), cantidad=cantidad, precio=precio)

    def test_un_insumo_aparece_una_vez_por_compra(self, db, f, obra):
        c, i = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"]), f.insumo()
        f.detalle(c, i)
        with rechaza(db, UNICO, "uq_compra_detalle_compra_id_insumo_id"):
            f.detalle(c, i)

    def test_el_insumo_debe_existir_en_el_catalogo(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        with rechaza(db, FORANEA, "fk_compra_detalle_insumo_id_insumo"):
            f.detalle(c, 999_999_999)

    def test_el_detalle_es_un_libro(self, db, f, obra):
        d = f.detalle(f.compra(obra["proyecto"], obra["proveedor"], obra["residente"]), f.insumo())
        with rechaza(db, RESTRICT, "SIGRO-R1"):
            db.execute("UPDATE compras.compra_detalle SET cantidad = 1 WHERE id = %s", (d,))
        with rechaza(db, RESTRICT, "SIGRO-R1"):
            db.execute("DELETE FROM compras.compra_detalle WHERE id = %s", (d,))

    def test_adjunta_el_comprobante_pero_solo_pdf_o_imagen(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        sql = ("INSERT INTO compras.compra_adjunto (compra_id, archivo, nombre_original, tipo_mime, tamano_bytes, sha256, subido_por) "
               "VALUES (%s, 'k/1', 'f.pdf', %s, 1000, %s, %s)")
        db.execute(sql, (c, "application/pdf", "a" * 64, obra["residente"]))
        with rechaza(db, CHECK, "ck_compra_adjunto_tipo_mime"):
            db.execute(sql, (c, "application/x-msdownload", "b" * 64, obra["residente"]))

    def test_el_mismo_archivo_no_se_adjunta_dos_veces(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        sql = ("INSERT INTO compras.compra_adjunto (compra_id, archivo, nombre_original, tipo_mime, tamano_bytes, sha256, subido_por) "
               "VALUES (%s, 'k/1', 'f.pdf', 'application/pdf', 1000, %s, %s)")
        db.execute(sql, (c, "a" * 64, obra["residente"]))
        with rechaza(db, UNICO):
            db.execute(sql, (c, "a" * 64, obra["residente"]))

    def test_tamano_maximo_de_adjunto(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        with rechaza(db, CHECK, "ck_compra_adjunto_tamano"):
            db.execute("INSERT INTO compras.compra_adjunto (compra_id, archivo, nombre_original, tipo_mime, tamano_bytes, sha256, subido_por) "
                       "VALUES (%s, 'k/1', 'f.pdf', 'application/pdf', %s, %s, %s)", (c, 16 * 1024 * 1024, "c" * 64, obra["residente"]))


# ============================================================ solicitud de materiales (CR-04)
class TestSolicitud:
    def test_el_numero_es_correlativo_por_obra(self, db, f, obra):
        otra = f.obra_con_equipo()
        nums = [db.execute("SELECT numero FROM compras.solicitud WHERE id = %s", (f.solicitud(obra["proyecto"], obra["residente"]),)).fetchone()["numero"]
                for _ in range(3)]
        n_otra = db.execute("SELECT numero FROM compras.solicitud WHERE id = %s", (f.solicitud(otra["proyecto"], otra["residente"]),)).fetchone()["numero"]
        assert nums == [1, 2, 3] and n_otra == 1              # cada obra cuenta desde 1

    def test_el_cliente_no_puede_elegir_el_numero(self, db, f, obra):
        s = db.execute("INSERT INTO compras.solicitud (proyecto_id, numero, emitida_por) VALUES (%s, 77, %s) RETURNING numero",
                       (obra["proyecto"], obra["residente"])).fetchone()
        assert s["numero"] == 1

    def test_una_solicitud_revertida_no_deja_huecos(self, db, f, obra):
        f.solicitud(obra["proyecto"], obra["residente"])                      # N.º 1
        with rechaza(db, CHECK):                                              # se consume el 2 y se revierte
            db.execute("INSERT INTO compras.solicitud (proyecto_id, emitida_por, estado) VALUES (%s, %s, 'invalido')",
                       (obra["proyecto"], obra["residente"]))
        n = db.execute("SELECT numero FROM compras.solicitud WHERE id = %s", (f.solicitud(obra["proyecto"], obra["residente"]),)).fetchone()["numero"]
        assert n == 2

    def test_nace_emitida_y_no_se_edita(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        assert db.execute("SELECT estado FROM compras.solicitud WHERE id=%s", (s,)).fetchone()["estado"] == "emitida"
        with rechaza(db, RESTRICT, "SIGRO-S1"):
            db.execute("UPDATE compras.solicitud SET fecha = fecha + 1 WHERE id = %s", (s,))
        with rechaza(db, RESTRICT, "SIGRO-S1"):
            db.execute("UPDATE compras.solicitud SET numero = 99 WHERE id = %s", (s,))

    def test_se_anula_con_motivo_y_luego_se_emite_otra(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        db.execute("UPDATE compras.solicitud SET estado='anulada', motivo_anulacion='cantidad equivocada', anulada_por=%s, anulada_en=now() "
                   "WHERE id=%s", (obra["residente"], s))
        s2 = f.solicitud(obra["proyecto"], obra["residente"])
        assert db.execute("SELECT numero FROM compras.solicitud WHERE id=%s", (s2,)).fetchone()["numero"] == 2

    def test_anular_exige_motivo(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        with rechaza(db, CHECK, "ck_solicitud_anulacion"):
            db.execute("UPDATE compras.solicitud SET estado='anulada', anulada_por=%s, anulada_en=now() WHERE id=%s",
                       (obra["residente"], s))

    def test_no_se_borra(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        with rechaza(db, RESTRICT, "SIGRO-R10"):
            db.execute("DELETE FROM compras.solicitud WHERE id = %s", (s,))

    def test_el_estado_no_es_un_valor_que_alguien_escriba(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        for inventado in ("comprada", "recibida", "solicitada", "comprada_en_parte"):
            with rechaza(db, CHECK):          # solo existen 'emitida' y 'anulada'; el avance se deriva
                db.execute("UPDATE compras.solicitud SET estado = %s WHERE id = %s", (inventado, s))

    # ------------------------------------------------------------------------- líneas
    def test_una_linea_puede_describir_un_insumo_aun_no_catalogado(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        f.linea(s, None, cantidad="12", unidad="und", libre="Andamio tubular 1.5 m")

    def test_una_linea_sin_insumo_ni_descripcion_se_rechaza(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        with rechaza(db, CHECK, "ck_solicitud_linea_describe"):
            f.linea(s, None)

    def test_la_cantidad_es_positiva(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        with rechaza(db, CHECK, "ck_solicitud_linea_cantidad"):
            f.linea(s, f.insumo(), cantidad="0")

    def test_un_insumo_no_se_repite_dentro_de_una_solicitud(self, db, f, obra):
        s, i = f.solicitud(obra["proyecto"], obra["residente"]), f.insumo()
        f.linea(s, i)
        with rechaza(db, UNICO, "uq_solicitud_linea_solicitud_insumo"):
            f.linea(s, i)

    def test_varias_lineas_libres_si_pueden_convivir(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        f.linea(s, None, libre="Cosa A")
        f.linea(s, None, libre="Cosa B")

    def test_las_lineas_son_un_libro_y_no_se_agregan_a_una_solicitud_anulada(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        ln = f.linea(s, f.insumo())
        with rechaza(db, RESTRICT, "SIGRO-R1"):
            db.execute("UPDATE compras.solicitud_linea SET cantidad = 1 WHERE id = %s", (ln,))
        db.execute("UPDATE compras.solicitud SET estado='anulada', motivo_anulacion='x', anulada_por=%s, anulada_en=now() WHERE id=%s",
                   (obra["residente"], s))
        with rechaza(db, CHECK, "SIGRO-S2"):
            f.linea(s, f.insumo())

    # ------------------------------------------------------- la compra se enlaza (opcional)
    def test_una_compra_puede_nacer_sin_solicitud(self, db, f, obra):
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"])
        assert db.execute("SELECT solicitud_id FROM compras.compra WHERE id=%s", (c,)).fetchone()["solicitud_id"] is None

    def test_la_compra_enlazada_debe_ser_de_la_misma_obra_que_la_solicitud(self, db, f, obra):
        otra = f.obra_con_equipo()
        s_otra = f.solicitud(otra["proyecto"], otra["residente"])
        with rechaza(db, FORANEA, "fk_compra_solicitud_id_solicitud"):
            f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], solicitud=s_otra)

    def test_no_se_compra_contra_una_solicitud_anulada(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        db.execute("UPDATE compras.solicitud SET estado='anulada', motivo_anulacion='x', anulada_por=%s, anulada_en=now() WHERE id=%s",
                   (obra["residente"], s))
        with rechaza(db, CHECK, "SIGRO-S3"):
            f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], solicitud=s)


class TestEstadoDerivado:
    """El estado se DERIVA de las compras; nadie lo escribe (modelo-datos, solicitud)."""

    def estado(self, db, s):
        return db.execute("SELECT estado_derivado FROM compras.solicitud_estado WHERE solicitud_id = %s", (s,)).fetchone()["estado_derivado"]

    def test_recorre_solicitada_comprada_en_parte_comprada(self, db, f, obra):
        cemento, acero = f.insumo(), f.insumo()
        s = f.solicitud(obra["proyecto"], obra["residente"])
        f.linea(s, cemento, cantidad="100", unidad="bol")
        f.linea(s, acero, cantidad="500", unidad="var")
        assert self.estado(db, s) == "solicitada"

        c1 = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], solicitud=s)
        f.detalle(c1, cemento, cantidad="40", unidad="bol")
        assert self.estado(db, s) == "comprada_en_parte"

        c2 = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], solicitud=s)
        f.detalle(c2, cemento, cantidad="60", unidad="bol")
        f.detalle(c2, acero, cantidad="500", unidad="var")
        assert self.estado(db, s) == "comprada"

    def test_la_cantidad_pendiente_por_linea(self, db, f, obra):
        cemento = f.insumo()
        s = f.solicitud(obra["proyecto"], obra["residente"])
        f.linea(s, cemento, cantidad="100", unidad="bol")
        f.detalle(f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], solicitud=s), cemento, cantidad="35", unidad="bol")
        fila = db.execute("SELECT * FROM compras.solicitud_linea_avance WHERE solicitud_id = %s", (s,)).fetchone()
        assert (fila["cantidad_solicitada"], fila["cantidad_comprada"], fila["cantidad_pendiente"]) == (100, 35, 65)

    def test_comprar_de_mas_no_da_pendiente_negativo(self, db, f, obra):
        cemento = f.insumo()
        s = f.solicitud(obra["proyecto"], obra["residente"])
        f.linea(s, cemento, cantidad="100", unidad="bol")
        f.detalle(f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], solicitud=s), cemento, cantidad="130", unidad="bol")
        fila = db.execute("SELECT cantidad_pendiente FROM compras.solicitud_linea_avance WHERE solicitud_id = %s", (s,)).fetchone()
        assert fila["cantidad_pendiente"] == 0                    # el requerimiento avisa, no bloquea (R7)
        assert self.estado(db, s) == "comprada"

    def test_una_compra_anulada_deja_de_contar(self, db, f, obra):
        cemento = f.insumo()
        s = f.solicitud(obra["proyecto"], obra["residente"])
        f.linea(s, cemento, cantidad="100", unidad="bol")
        c = f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], solicitud=s)
        f.detalle(c, cemento, cantidad="100", unidad="bol")
        assert self.estado(db, s) == "comprada"
        db.execute("UPDATE compras.compra SET estado='anulada', motivo_anulacion='error', anulada_por=%s, anulada_en=now() WHERE id=%s",
                   (obra["residente"], c))
        assert self.estado(db, s) == "solicitada"

    def test_las_compras_sin_solicitud_o_de_otra_solicitud_no_cuentan(self, db, f, obra):
        cemento = f.insumo()
        s1, s2 = f.solicitud(obra["proyecto"], obra["residente"]), f.solicitud(obra["proyecto"], obra["residente"])
        f.linea(s1, cemento, cantidad="100", unidad="bol")
        f.detalle(f.compra(obra["proyecto"], obra["proveedor"], obra["residente"]), cemento, cantidad="100", unidad="bol")
        f.detalle(f.compra(obra["proyecto"], obra["proveedor"], obra["residente"], solicitud=s2), cemento, cantidad="100", unidad="bol")
        assert self.estado(db, s1) == "solicitada"

    def test_una_solicitud_anulada_figura_anulada(self, db, f, obra):
        s = f.solicitud(obra["proyecto"], obra["residente"])
        f.linea(s, f.insumo())
        db.execute("UPDATE compras.solicitud SET estado='anulada', motivo_anulacion='x', anulada_por=%s, anulada_en=now() WHERE id=%s",
                   (obra["residente"], s))
        assert self.estado(db, s) == "anulada"

    def test_una_solicitud_sin_lineas_figura_solicitada(self, db, f, obra):
        assert self.estado(db, f.solicitud(obra["proyecto"], obra["residente"])) == "solicitada"
