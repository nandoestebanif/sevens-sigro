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
)

data class Cotejo(val resultado: ResultadoCotejo, val compraId: Int? = null, val avisos: List<String> = emptyList())

object CotejoQr {
    /** Diferencia que se tolera por redondeo: un céntimo. */
    const val TOLERANCIA_CENTIMOS = 1L

    fun cotejar(qr: QrFactura, compras: List<CompraCotejable>, rucSevens: String = RUC_SEVENS): Cotejo {
        if (qr.adquirenteTipo != "6" || qr.adquirenteDocumento != rucSevens) return Cotejo(ResultadoCotejo.NO_ES_DE_SEVENS)
        val candidatas = compras.filter { it.proveedorRuc == qr.rucEmisor && normalizarNumero(it.numeroComprobante) == qr.numeroComprobante }
        // si hay una vigente y otra anulada con el mismo comprobante, cuenta la vigente
        val c = candidatas.firstOrNull { it.estado != "anulada" } ?: candidatas.firstOrNull() ?: return Cotejo(ResultadoCotejo.SIN_COMPRA)
        if (c.estado == "anulada") return Cotejo(ResultadoCotejo.ANULADA, c.id)
        if (c.quedaPorRecibir == false) return Cotejo(ResultadoCotejo.YA_RECIBIDA, c.id)
        val avisos = buildList {
            diferencia("total", qr.total, c.total)?.let(::add)
            diferencia("IGV", qr.igv, c.igv)?.let(::add)
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

    private fun diferencia(que: String, delQr: String, deLaCompra: String?): String? {
        val q = QrFactura.centimos(delQr) ?: return null
        val c = QrFactura.centimos(deLaCompra) ?: return null // la compra no lo trae: no se puede comparar
        val d = kotlin.math.abs(q - c)
        if (d <= TOLERANCIA_CENTIMOS) return null
        return "El $que del QR (${soles(q)}) difiere en ${soles(d)} del registrado en la compra (${soles(c)})."
    }

    /** 118000 → «S/ 1 180.00» (miles con espacio, como en el contrato). */
    fun soles(centimos: Long): String {
        val e = (centimos / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        return "S/ $e.${(centimos % 100).toString().padStart(2, '0')}"
    }
}
