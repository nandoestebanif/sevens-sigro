package pe.sevens.sigro.dominio

/*
 * Buscar en listas del celular (insumos, herramientas, trabajadores): sin tildes ni mayúsculas, cada palabra en
 * cualquier orden («cemento tipo» encuentra «CEMENTO PORTLAND TIPO I») y primero lo que EMPIEZA con lo escrito.
 * Kotlin puro: se prueba solo (BusquedaTest).
 */
object Busqueda {
    private val TILDES = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')

    fun normalizar(s: String): String =
        s.lowercase().map { TILDES[it] ?: it }.joinToString("").replace(Regex("\\s+"), " ").trim()

    private fun palabras(consulta: String) = normalizar(consulta).split(' ').filter { it.isNotEmpty() }

    /** Todas las palabras buscadas aparecen en alguno de los textos de la fila. Sin consulta, todo coincide. */
    fun coincide(textos: List<String>, consulta: String): Boolean {
        val ps = palabras(consulta)
        if (ps.isEmpty()) return true
        val t = textos.joinToString(" ") { normalizar(it) }
        return ps.all { t.contains(it) }
    }

    /** 0: un texto empieza con lo escrito · 1: alguna palabra empieza con la primera buscada · 2: solo lo contiene. */
    fun relevancia(textos: List<String>, consulta: String): Int {
        val q = normalizar(consulta)
        val ts = textos.map { normalizar(it) }
        if (ts.any { it.startsWith(q) }) return 0
        val primera = palabras(consulta).firstOrNull() ?: return 0
        return if (ts.any { t -> t.split(' ').any { it.startsWith(primera) } }) 1 else 2
    }

    /** Filtra y ordena por relevancia (empate: como venía la lista). */
    fun <T> filtrar(lista: List<T>, consulta: String, textos: (T) -> List<String>): List<T> {
        if (palabras(consulta).isEmpty()) return lista
        return lista.filter { coincide(textos(it), consulta) }.sortedBy { relevancia(textos(it), consulta) }
    }

    /** Desde cuántas letras se muestra una lista de resultados (con una sola letra sale media obra). */
    const val MIN_LETRAS = 2
}
