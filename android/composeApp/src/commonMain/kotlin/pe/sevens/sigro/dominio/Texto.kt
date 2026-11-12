package pe.sevens.sigro.dominio

/*
 * Cómo se MUESTRA lo que viene del S10 y del servidor, sin cambiar el dato: el nombre normalizado, la unidad y la
 * fecha se guardan y se envían tal cual; esto solo decide cómo se leen en pantalla.
 */
object Texto {
    private val CORTAS = setOf("DE", "LA", "EL", "Y", "A", "EN", "SIN", "CON", "POR", "DEL", "LOS", "LAS", "PARA")
    private val SOLO_LETRAS = Regex("^[A-ZÁÉÍÓÚÑÜ]+$")
    /** Siglas de 4 letras o más que se dejan en mayúsculas (normas y materiales). */
    private val SIGLAS = setOf("ASTM", "AISI", "ASME", "CPVC", "HDPE", "PEAD", "ITINTEC", "SENCICO", "NTP", "SAE")

    /**
     * MAYÚSCULAS del S10 → caja amable (la misma regla que el Catálogo de la web): baja las palabras comunes y
     * las de 4 letras o más; conserva siglas, medidas y normas (PVC, KG, ASTM, 1/2", A615/A706). Si el nombre ya
     * viene sobre todo en minúsculas, no se toca.
     */
    fun nombre(s: String): String {
        val t = s.trim()
        if (t.count { it.isLowerCase() } > t.count { it.isUpperCase() }) return t
        val palabras = t.split(' ').map { p ->
            when {
                p in CORTAS -> p.lowercase()
                p in SIGLAS -> p
                p.length >= 4 && SOLO_LETRAS.matches(p) -> p.first() + p.drop(1).lowercase()
                else -> p
            }
        }
        val x = palabras.joinToString(" ")
        return x.replaceFirstChar { it.uppercase() }
    }

    /** Unidad del S10 o del catálogo → palabra que se lee («BOL» → «bolsas»). Desconocida: en minúsculas. */
    fun unidad(u: String, cantidad: String? = null): String {
        val k = u.trim().lowercase()
        val (una, varias) = UNIDADES[k] ?: return u.trim().lowercase()
        val uno = cantidad?.let { ValidarSalida.normalizarCantidad(it).toDoubleOrNull() } == 1.0
        return if (uno) una else varias
    }

    private val UNIDADES: Map<String, Pair<String, String>> = buildMap {
        fun pon(claves: List<String>, una: String, varias: String = una) = claves.forEach { put(it, una to varias) }
        pon(listOf("und", "u", "unid", "unidad", "unidades"), "unidad", "unidades")
        pon(listOf("bol", "bls", "bolsa", "bolsas"), "bolsa", "bolsas")
        pon(listOf("m", "ml", "metro", "metros", "metros lineales"), "metro", "metros")
        pon(listOf("m2", "metro cuadrado", "metros cuadrados"), "m²")
        pon(listOf("m3", "metro cúbico", "metro cubico", "metros cúbicos"), "m³")
        pon(listOf("kg", "kilogramo", "kilogramos"), "kg")
        pon(listOf("gln", "gal", "galon", "galón", "galones"), "galón", "galones")
        pon(listOf("pza", "pieza", "piezas"), "pieza", "piezas")
        pon(listOf("rll", "rollo", "rollos"), "rollo", "rollos")
        pon(listOf("caja", "cajas"), "caja", "cajas")
        pon(listOf("paquete", "paquetes", "pqt"), "paquete", "paquetes")
        pon(listOf("balde", "baldes"), "balde", "baldes")
        pon(listOf("docena", "docenas"), "docena", "docenas")
        pon(listOf("mll", "millar", "millares"), "millar", "millares")
        pon(listOf("p2"), "pie²")
        pon(listOf("glb", "global"), "global")
        pon(listOf("hh"), "hora hombre", "horas hombre")
        pon(listOf("hm"), "hora máquina", "horas máquina")
        pon(listOf("varilla", "varillas", "var"), "varilla", "varillas")
    }

    /** Cantidad sin ceros de más: «25.0000» → «25», «2.5000» → «2.5». */
    fun cantidad(c: String): String {
        val n = ValidarSalida.normalizarCantidad(c)
        if (!n.contains('.')) return n
        return n.trimEnd('0').trimEnd('.')
    }

    /** «25 bolsas», «1 bolsa». */
    fun cantidadConUnidad(c: String, u: String): String = "${cantidad(c)} ${unidad(u, c)}"

    private val DIAS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

    /**
     * «2026-11-12» → «jueves 12/11». Calcula el día de la semana sin depender de la plataforma (Sakamoto).
     * Si no es una fecha ISO, la devuelve tal cual.
     */
    fun fecha(iso: String): String {
        val p = iso.trim().take(10).split('-')
        if (p.size != 3) return iso
        val (a, m, d) = p.map { it.toIntOrNull() ?: return iso }
        val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
        val y = if (m < 3) a - 1 else a
        val dow = (y + y / 4 - y / 100 + y / 400 + t[m - 1] + d) % 7 // 0 = domingo
        val nombre = DIAS[(dow + 6) % 7]
        return "$nombre ${d.toString().padStart(2, '0')}/${m.toString().padStart(2, '0')}"
    }

    /** El código del celular, corto para leerlo en voz alta: los últimos 8 caracteres en grupos de 4. */
    fun codigoCorto(id: String): String {
        val limpio = id.filter { it.isLetterOrDigit() }.takeLast(8).uppercase()
        return if (limpio.length == 8) "${limpio.take(4)}-${limpio.drop(4)}" else limpio
    }
}
