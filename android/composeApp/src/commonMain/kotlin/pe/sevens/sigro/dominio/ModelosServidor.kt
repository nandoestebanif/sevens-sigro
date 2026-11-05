package pe.sevens.sigro.dominio

/*
 * Lo que la app muestra tal como lo devolvió el servidor: quién registró y a qué hora (A1) y el stock (A2).
 * Nada de esto se calcula en el celular (regla 2): solo se guarda y se formatea.
 */

/**
 * A1 · Recibo del servidor al aceptar un registro (201, o 200 en un reenvío). `fechaServidor` es la hora
 * que vale para auditar (ISO-8601, como la manda la API). `registradoPor` es el nombre si se pudo saber.
 */
data class ReciboServidor(val registradoPorId: Int?, val registradoPor: String?, val fechaServidor: String?)

/**
 * Hora de Lima (UTC−5, sin horario de verano desde 1994) para fechas del servidor, sin depender de la zona
 * del celular ni de librerías de fecha: el mismo resultado en Android, iOS y las pruebas.
 */
object HoraLima {
    private const val DESFASE_MIN = -5 * 60
    private val iso = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$""")

    /** «2026-11-05T17:20:31.5Z» → «05/11/2026 12:20». Sin zona se toma como UTC. null si no se entiende. */
    fun deIso(texto: String?): String? {
        val m = iso.matchEntire(texto?.trim() ?: return null) ?: return null
        val (y, mo, d, h, mi) = m.destructured
        val zona = m.groupValues[7]
        val minutosUtc = diasDesdeEpoca(y.toInt(), mo.toInt(), d.toInt()) * 1440L + h.toInt() * 60 + mi.toInt() - desfase(zona)
        return formatear(minutosUtc + DESFASE_MIN, conFecha = true)
    }

    /** Milisegundos desde 1970 → «14:05» en Lima. */
    fun horaDeMs(ms: Long): String = formatear((ms).floorDiv(60_000L) + DESFASE_MIN, conFecha = false)

    private fun desfase(zona: String): Int {
        if (zona.isEmpty() || zona == "Z") return 0
        val signo = if (zona[0] == '-') -1 else 1
        val digitos = zona.substring(1).replace(":", "")
        return signo * (digitos.substring(0, 2).toInt() * 60 + digitos.substring(2, 4).toInt())
    }

    private fun formatear(minutos: Long, conFecha: Boolean): String {
        val dias = (minutos).floorDiv(1440L)
        val enElDia = (minutos).mod(1440L).toInt()
        val hora = "${dos(enElDia / 60)}:${dos(enElDia % 60)}"
        if (!conFecha) return hora
        val (y, m, d) = civil(dias)
        return "${dos(d)}/${dos(m)}/$y $hora"
    }

    private fun dos(n: Int) = n.toString().padStart(2, '0')

    // Algoritmos de días civiles de Howard Hinnant (calendario gregoriano proléptico).
    private fun diasDesdeEpoca(y0: Int, m: Int, d: Int): Long {
        val y = (if (m <= 2) y0 - 1 else y0).toLong()
        val era = (y).floorDiv(400L)
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    private fun civil(z0: Long): Triple<Int, Int, Int> {
        val z = z0 + 719468
        val era = (z).floorDiv(146097L)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return Triple(y, m, d)
    }
}

/** «12.5000» → «12.5», «40.0000» → «40»: solo quita ceros de formato, no redondea ni calcula. */
fun cifraLegible(texto: String): String =
    if ('.' in texto) texto.trimEnd('0').trimEnd('.') else texto

// ── A2 · Stock ──────────────────────────────────────────────────────────────────────────────

/** Una fila de GET /obras/{id}/almacen/stock. `almacen` nulo en el consolidado de la obra. */
data class FilaStock(
    val almacenId: Int?,
    val almacen: String?,
    val insumoId: Int,
    val codigo: String,
    val descripcion: String,
    val unidad: String,
    val cantidad: String,
    val bajoPuntoReposicion: Boolean?,
)

/**
 * El stock tal como lo dio el servidor. `corte` = hora del último movimiento considerado (la del servidor).
 * `desdeCelular` = no hubo señal y se muestra la última copia guardada, recibida a las `recibidoMs`.
 */
data class StockObra(
    val corte: String?,
    val porAlmacen: List<FilaStock>,
    val consolidado: List<FilaStock>,
    val advertencias: List<String>,
    val recibidoMs: Long,
    val desdeCelular: Boolean,
)

interface RepositorioStock {
    /** Con señal, del servidor (y queda guardado); sin señal, la última copia guardada. Sin copia: ErrorSigro. */
    suspend fun stock(obraId: Int): StockObra
}
