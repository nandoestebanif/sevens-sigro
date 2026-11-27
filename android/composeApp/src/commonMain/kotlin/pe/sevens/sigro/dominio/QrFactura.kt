package pe.sevens.sigro.dominio

/*
 * HU-73 · El QR de la factura electrónica de SUNAT (contrato: docs/contratos/hu-73-escanear-factura.md, sección 2).
 * RUC emisor | tipo | serie | número | IGV | total | fecha | tipo doc. adquirente | doc. adquirente | resumen…
 * Kotlin puro: se prueba en desktopTest con los MISMOS casos que el backend (docs/contratos/hu-73-qr-casos.json),
 * así la app y el servidor no pueden leer distinto el mismo QR. Ante la duda no se adivina: QR no reconocido.
 */

/** RUC de SEVENS INGENIEROS SAC: el adquirente que deben llevar sus facturas. */
const val RUC_SEVENS = "20606456892"

/** Datos del QR ya normalizados (montos con 2 decimales, fecha ISO, número sin ceros a la izquierda). */
data class QrFactura(
    val rucEmisor: String,
    /** «factura» o «boleta», como se guarda en compras.compra. */
    val tipoComprobante: String,
    /** «F001-1234»: SERIE-NÚMERO, igual que numero_comprobante en la base. */
    val numeroComprobante: String,
    val igv: String,
    val total: String,
    /** aaaa-mm-dd */
    val fecha: String,
    val adquirenteTipo: String,
    val adquirenteDocumento: String,
) {
    /** El comprobante está emitido al RUC de SEVENS (tipo de documento 6 = RUC). */
    val esDeSevens: Boolean get() = adquirenteTipo == "6" && adquirenteDocumento == RUC_SEVENS

    companion object {
        private val TIPOS = mapOf("01" to "factura", "03" to "boleta")
        private val SERIE = Regex("^[A-Za-z0-9]{4}$")
        private val NUMERO = Regex("^[0-9]{1,8}$")
        private val MONTO = Regex("^([0-9]{1,12})(?:\\.([0-9]{1,2}))?$")
        private val ISO = Regex("^([0-9]{4})-([0-9]{2})-([0-9]{2})$")
        private val DMA = Regex("^([0-9]{2})/([0-9]{2})/([0-9]{4})$")

        /** Lee el texto del QR. `null` si no es el QR de una factura o boleta electrónica válida. */
        fun leer(texto: String?): QrFactura? {
            val c = texto?.trim()?.split('|')?.map { it.trim() } ?: return null
            if (c.size < 9) return null
            val ruc = c[0].takeIf { rucValido(it) } ?: return null
            val tipo = TIPOS[c[1]] ?: return null // 07/08 (notas) y otros no se reciben
            val serie = c[2].takeIf { SERIE.matches(it) }?.uppercase() ?: return null
            val numero = c[3].takeIf { NUMERO.matches(it) }?.trimStart('0')?.ifEmpty { "0" } ?: return null
            val igv = monto(c[4]) ?: return null
            val total = monto(c[5]) ?: return null
            val fecha = fecha(c[6]) ?: return null
            val tipoDoc = c[7].takeIf { it.length in 1..2 } ?: return null
            val doc = c[8].takeIf { it.isNotEmpty() && it.length <= 15 } ?: return null
            return QrFactura(ruc, tipo, "$serie-$numero", igv, total, fecha, tipoDoc, doc)
        }

        /** Dígito verificador del RUC (módulo 11), el mismo que valida el backend. */
        fun rucValido(ruc: String): Boolean {
            if (ruc.length != 11 || !ruc.all { it.isDigit() }) return false
            val pesos = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)
            val suma = (0 until 10).sumOf { (ruc[it] - '0') * pesos[it] }
            val dv = when (val r = 11 - suma % 11) { 10 -> 0; 11 -> 1; else -> r }
            return dv == ruc[10] - '0'
        }

        /** «1180» → «1180.00», «36.6» → «36.60». */
        fun monto(v: String): String? {
            val m = MONTO.matchEntire(v) ?: return null
            val entero = m.groupValues[1].trimStart('0').ifEmpty { "0" }
            return "$entero.${m.groupValues[2].padEnd(2, '0')}"
        }

        /** Céntimos de un monto ya normalizado (o de cualquier decimal con punto). `null` si no es un monto. */
        fun centimos(v: String?): Long? {
            val n = v?.trim()?.let { monto(it) } ?: return null
            val (e, d) = n.split('.')
            return e.toLong() * 100 + d.toLong()
        }

        private fun fecha(v: String): String? {
            val (a, m, d) = ISO.matchEntire(v)?.destructured?.let { (a, m, d) -> Triple(a, m, d) }
                ?: DMA.matchEntire(v)?.destructured?.let { (d, m, a) -> Triple(a, m, d) }
                ?: return null
            val mes = m.toInt()
            val dia = d.toInt()
            if (mes !in 1..12 || dia < 1) return null
            val anio = a.toInt()
            val bisiesto = anio % 4 == 0 && (anio % 100 != 0 || anio % 400 == 0)
            val dias = intArrayOf(31, if (bisiesto) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[mes - 1]
            return if (dia <= dias) "$a-$m-$d" else null
        }
    }
}
