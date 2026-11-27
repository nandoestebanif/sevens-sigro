package pe.sevens.sigro.dominio

/*
 * HU-73 · Cotejo del QR contra las compras de la obra (contrato, sección 3.1). Es la misma regla que el servidor:
 * con señal manda el servidor (POST /compras/cotejar-qr); sin señal la app usa esta, con las compras que ya bajó.
 */

/** Los seis resultados del contrato, con el código que devuelve el servidor. */
enum class ResultadoCotejo(val codigo: String) {
    RECIBIR("recibir"),
    RECIBIR_CON_AVISOS("recibir_con_avisos"),
    YA_RECIBIDA("ya_recibida"),
    ANULADA("anulada"),
    SIN_COMPRA("sin_compra"),
    NO_ES_DE_SEVENS("no_es_de_sevens");

    /** Se puede abrir la recepción. */
    val sePuedeRecibir: Boolean get() = this == RECIBIR || this == RECIBIR_CON_AVISOS

    companion object {
        fun deCodigo(c: String?): ResultadoCotejo? = entries.firstOrNull { it.codigo == c }
    }
}

/** Lo que hace falta saber de una compra para cotejarla (venga del servidor o de lo que la app ya bajó). */
data class CompraCotejable(
    val id: Int?,
    val proveedorRuc: String?,
    val tipoComprobante: String,
    val numeroComprobante: String,
    val total: String?,
    val igv: String?,
    val estado: String,
    /** `null`: no se sabe (sin la consulta del pendiente); se trata como «queda». */
    val quedaPorRecibir: Boolean?,
    /** aaaa-mm-dd; `null` si no se sabe (no se compara). */
    val fecha: String? = null,
    /** PEN o USD: el símbolo de los avisos. */
    val moneda: String = "PEN",
    val motivoAnulacion: String? = null,
)

data class Cotejo(val resultado: ResultadoCotejo, val compraId: Int? = null, val avisos: List<String> = emptyList())

object CotejoQr {
    /** Diferencia que se tolera por redondeo: un céntimo. */
    const val TOLERANCIA_CENTIMOS = 1L

    fun cotejar(qr: QrFactura, compras: List<CompraCotejable>, rucSevens: String = RUC_SEVENS): Cotejo {
        if (qr.adquirenteTipo != "6" || qr.adquirenteDocumento != rucSevens) return Cotejo(ResultadoCotejo.NO_ES_DE_SEVENS)
        // contrato 3.1: con el mismo proveedor y serie-número, gana 1) el mismo tipo, 2) la vigente, 3) la más reciente (id mayor)
        val c = compras.filter { it.proveedorRuc == qr.rucEmisor && normalizarNumero(it.numeroComprobante) == qr.numeroComprobante }
            .sortedWith(compareBy<CompraCotejable>({ it.tipoComprobante != qr.tipoComprobante }, { it.estado == "anulada" }, { -(it.id ?: 0) }))
            .firstOrNull() ?: return Cotejo(ResultadoCotejo.SIN_COMPRA)
        if (c.estado == "anulada") {
            val motivo = c.motivoAnulacion?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
            return Cotejo(ResultadoCotejo.ANULADA, c.id, listOf("La compra ${qr.numeroComprobante} está anulada" + (motivo?.let { ": $it." } ?: ".")))
        }
        if (c.quedaPorRecibir == false) return Cotejo(ResultadoCotejo.YA_RECIBIDA, c.id)
        val m = simbolo(c.moneda)
        val avisos = buildList {
            diferencia("El total", qr.total, c.total, m)?.let(::add)
            diferencia("El IGV", qr.igv, c.igv, m)?.let(::add)
            if (c.fecha != null && c.fecha.take(10) != qr.fecha) add("La fecha del QR (${dma(qr.fecha)}) no coincide con la de la compra (${dma(c.fecha.take(10))}).")
            if (c.tipoComprobante != qr.tipoComprobante) add("El QR es de una ${qr.tipoComprobante} y la compra se registró como ${c.tipoComprobante}.")
        }
        return Cotejo(if (avisos.isEmpty()) ResultadoCotejo.RECIBIR else ResultadoCotejo.RECIBIR_CON_AVISOS, c.id, avisos)
    }

    /** «F001-00001234» → «F001-1234»: la serie en mayúsculas y el número sin ceros a la izquierda. */
    fun normalizarNumero(n: String): String {
        val t = n.trim().uppercase()
        val i = t.indexOf('-')
        if (i < 0) return t
        return t.substring(0, i) + "-" + t.substring(i + 1).trimStart('0').ifEmpty { "0" }
    }

    private fun diferencia(que: String, delQr: String, deLaCompra: String?, m: String): String? {
        val q = QrFactura.centimos(delQr) ?: return null
        val c = QrFactura.centimos(deLaCompra) ?: return null // la compra no lo trae: no se puede comparar
        val d = kotlin.math.abs(q - c)
        if (d <= TOLERANCIA_CENTIMOS) return null
        return "$que del QR (${soles(q, m)}) difiere en ${soles(d, m)} del registrado en la compra (${soles(c, m)})."
    }

    /** PEN → «S/», USD → «US$». */
    fun simbolo(moneda: String?): String = if (moneda.equals("USD", ignoreCase = true)) "US$" else "S/"

    private fun dma(iso: String): String = iso.split('-').let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else iso }

    /** 118000 → «S/ 1 180.00» (miles con espacio, como en el contrato). */
    fun soles(centimos: Long, simbolo: String = "S/"): String {
        val e = (centimos / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        return "$simbolo $e.${(centimos % 100).toString().padStart(2, '0')}"
    }
}
