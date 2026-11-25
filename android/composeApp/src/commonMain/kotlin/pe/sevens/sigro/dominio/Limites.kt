package pe.sevens.sigro.dominio

/**
 * Topes del contrato del servidor (backend-spring, almacen/Contratos.java). La app los aplica MIENTRAS se escribe:
 * un registro guardado sin señal ya no puede ser rechazado después por pasarse de dígitos o de largo.
 *
 *  - cantidad: NUMERIC(18,4) → hasta 14 enteros y 4 decimales
 *  - costo unitario: NUMERIC(18,6) → hasta 12 enteros y 6 decimales
 *  - motivo 300 · quién retira 120 · frente 120 · detalle de origen 300 caracteres
 */
object Limites {
    const val ENTEROS_CANTIDAD = 14
    const val DECIMALES_CANTIDAD = 4
    const val ENTEROS_COSTO = 12
    const val DECIMALES_COSTO = 6
    const val MOTIVO = 300
    const val QUIEN_RETIRA = 120
    const val FRENTE = 120
    const val DETALLE = 300

    /**
     * Filtro en tiempo real de un número: acepta dígitos y UNA coma o punto, sin pasar de [enteros] ni de [decimales].
     * Devuelve el texto nuevo si cabe; si la tecla lo pasaría, devuelve el [anterior] (la tecla no entra).
     * Los espacios se quitan: «1 000» pegado de WhatsApp queda «1000».
     */
    fun numero(anterior: String, nuevo: String, enteros: Int, decimales: Int): String {
        val t = nuevo.replace(" ", "").replace("\u00A0", "")
        if (t.length < anterior.length) return t // borrar siempre se puede
        return if (Regex("""^\d{0,$enteros}([.,]\d{0,$decimales})?$""").matches(t)) t else anterior
    }

    fun cantidad(anterior: String, nuevo: String) = numero(anterior, nuevo, ENTEROS_CANTIDAD, DECIMALES_CANTIDAD)
    fun costo(anterior: String, nuevo: String) = numero(anterior, nuevo, ENTEROS_COSTO, DECIMALES_COSTO)

    /** Texto con tope: lo que pase de [max] (al pegar) se corta. */
    fun texto(nuevo: String, max: Int): String = if (nuevo.length <= max) nuevo else nuevo.take(max)

    /** «245/300» cuando ya se usó el 80 % del tope, para que se vea antes de llegar; null si aún falta. */
    fun contador(texto: String, max: Int): String? = if (texto.length * 10 >= max * 8) "${texto.length}/$max" else null

    /** Mensaje si el número tiene más decimales o enteros de los que acepta el servidor; null si cabe. */
    fun excesoNumero(texto: String, enteros: Int, decimales: Int): String? {
        val t = texto.trim().replace(',', '.')
        val partes = t.split('.')
        return when {
            partes[0].length > enteros -> "Máximo $enteros cifras enteras"
            partes.size > 1 && partes[1].length > decimales -> "Máximo $decimales decimales"
            else -> null
        }
    }
}
