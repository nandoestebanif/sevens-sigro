package pe.sevens.sigro.dominio

/**
 * Validaciones de la salida que la app hace ANTES de guardar (HU-40: exige almacén, insumo,
 * cantidad, motivo y persona que retira). Son ayuda para el almacenero, no control:
 * el disponible y el costo los verifica el servidor.
 */
object ValidarSalida {
    private val decimal = Regex("""^\d{1,12}([.,]\d{1,6})?$""")

    /** Devuelve un mapa campo → mensaje; vacío si todo está bien. */
    fun errores(
        insumo: Insumo?,
        cantidad: String,
        motivo: String,
        quienRetira: String,
    ): Map<String, String> = buildMap {
        if (insumo == null) put("insumo", "Elige el insumo que sale")
        val c = cantidad.trim()
        when {
            c.isEmpty() -> put("cantidad", "Escribe la cantidad")
            !decimal.matches(c) -> put("cantidad", "Solo números, con hasta 6 decimales")
            normalizarCantidad(c).toDoubleOrNull()?.let { it <= 0.0 } != false -> put("cantidad", "Debe ser mayor que cero")
        }
        if (motivo.trim().length < 3) put("motivo", "Di en qué se usará (mínimo 3 letras)")
        if (quienRetira.trim().length < 3) put("quienRetira", "Escribe quién retira el material")
    }

    /** «12,5» → «12.5»: en obra se escribe con coma, la API espera punto. */
    fun normalizarCantidad(texto: String): String = texto.trim().replace(',', '.')
}
