package pe.sevens.sigro

import pe.sevens.sigro.dominio.CompraCotejable
import pe.sevens.sigro.dominio.CotejoQr
import pe.sevens.sigro.dominio.QrFactura
import pe.sevens.sigro.dominio.ResultadoCotejo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** HU-73 · reglas sueltas del QR y del cotejo, en Kotlin común (corren en Android, escritorio e iOS). */
class QrFacturaReglasTest {
    private val qr = QrFactura.leer("20999999990|01|F001|00001234|180.00|1180.00|2026-11-20|6|20606456892|")!!
    private fun compra(id: Int = 12, tipo: String = "factura", estado: String = "registrada", queda: Boolean? = true, total: String? = "1180.00",
                       igv: String? = "180.00", fecha: String? = null, moneda: String = "PEN", motivo: String? = null, numero: String = "F001-1234") =
        CompraCotejable(id, "20999999990", tipo, numero, total, igv, estado, queda, fecha, moneda, motivo)

    @Test
    fun elRucSeValidaConSuDigitoVerificador() {
        assertTrue(QrFactura.rucValido("20606456892"))
        assertFalse(QrFactura.rucValido("20606456893"))
        assertFalse(QrFactura.rucValido("2060645689"))
        assertFalse(QrFactura.rucValido("2060645689A"))
    }

    @Test
    fun losMontosSeNormalizanADosDecimales() {
        assertEquals("1180.00", QrFactura.monto("1180"))
        assertEquals("36.60", QrFactura.monto("36.6"))
        assertEquals("0.50", QrFactura.monto("000.5"))
        assertNull(QrFactura.monto("1,180.00"))
        assertNull(QrFactura.monto("12.345"))
        assertEquals(118000L, QrFactura.centimos("1180"))
        assertNull(QrFactura.centimos(null))
    }

    @Test
    fun lasFechasImposiblesNoSeLeen() {
        assertEquals("2028-02-29", QrFactura.leer("20999999990|01|F001|1|1.00|2.00|29/02/2028|6|20606456892")?.fecha, "año bisiesto")
        assertNull(QrFactura.leer("20999999990|01|F001|1|1.00|2.00|29/02/2027|6|20606456892"))
        assertNull(QrFactura.leer("20999999990|01|F001|1|1.00|2.00|2026-04-31|6|20606456892"))
        assertNull(QrFactura.leer("20999999990|01|F001|1|1.00|2.00|2026-11-00|6|20606456892"))
        assertNull(QrFactura.leer(null))
    }

    @Test
    fun elNumeroCeroYLaSerieEnMinusculas() {
        assertEquals("F001-0", QrFactura.leer("20999999990|01|f001|0000|1.00|2.00|2026-11-20|6|20606456892")?.numeroComprobante)
        assertTrue(qr.esDeSevens)
        assertFalse(QrFactura.leer("20999999990|01|F001|1|1.00|2.00|2026-11-20|1|12345678")!!.esDeSevens)
    }

    @Test
    fun losAvisosUsanLaMonedaYElFormatoDelContrato() {
        assertEquals("S/ 1 234 567.05", CotejoQr.soles(123456705))
        assertEquals("US$ 0.07", CotejoQr.soles(7, CotejoQr.simbolo("usd")))
        assertEquals("S/", CotejoQr.simbolo(null))
        val r = CotejoQr.cotejar(qr, listOf(compra(total = "1062.00", moneda = "USD")))
        assertEquals(listOf("El total del QR (US$ 1 180.00) difiere en US$ 118.00 del registrado en la compra (US$ 1 062.00)."), r.avisos)
    }

    @Test
    fun laFechaSoloSeComparaSiLaCompraLaTiene() {
        assertEquals(ResultadoCotejo.RECIBIR, CotejoQr.cotejar(qr, listOf(compra(fecha = null))).resultado)
        assertEquals(ResultadoCotejo.RECIBIR, CotejoQr.cotejar(qr, listOf(compra(fecha = "2026-11-20T10:00:00"))).resultado)
        assertEquals(
            listOf("La fecha del QR (20/11/2026) no coincide con la de la compra (19/11/2026)."),
            CotejoQr.cotejar(qr, listOf(compra(fecha = "2026-11-19"))).avisos,
        )
    }

    @Test
    fun sinMontosEnLaCompraNoSeInventaUnaDiferencia() {
        assertEquals(ResultadoCotejo.RECIBIR, CotejoQr.cotejar(qr, listOf(compra(total = null, igv = null))).resultado)
    }

    @Test
    fun anuladaConMotivoQueYaTerminaEnPunto() {
        val r = CotejoQr.cotejar(qr, listOf(compra(estado = "anulada", motivo = "  Se registró dos veces.  ", numero = "F001-00001234")))
        assertEquals(listOf("La compra F001-1234 está anulada: Se registró dos veces."), r.avisos)
    }

    @Test
    fun losCodigosDelServidorSeTraducen() {
        assertEquals(ResultadoCotejo.NO_ES_DE_SEVENS, ResultadoCotejo.deCodigo("no_es_de_sevens"))
        assertNull(ResultadoCotejo.deCodigo("otro"))
        assertTrue(ResultadoCotejo.RECIBIR_CON_AVISOS.sePuedeRecibir)
        assertFalse(ResultadoCotejo.ANULADA.sePuedeRecibir)
        assertEquals("F001", CotejoQr.normalizarNumero(" f001 "))
    }
}
