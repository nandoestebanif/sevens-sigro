package pe.sevens.sigro.dominio

/**
 * Validaciones de la salida que la app hace ANTES de guardar (HU-40: exige almacén, insumo,
 * cantidad, motivo y persona que retira). Son ayuda para el almacenero, no control:
 * el disponible y el costo los verifica el servidor.
 */
object ValidarSalida {
    /** Devuelve un mapa campo → mensaje; vacío si todo está bien. */
    fun errores(
        insumo: Insumo?,
        cantidad: String,
        motivo: String,
        quienRetira: String,
    ): Map<String, String> = buildMap {
        if (insumo == null) put("insumo", "Elige el insumo que sale")
        ValidarAlmacen.cantidad(cantidad)?.let { put("cantidad", it) }   // mismos topes que el servidor
        when {
            motivo.trim().length < 3 -> put("motivo", "Di en qué se usará (mínimo 3 letras)")
            motivo.trim().length > Limites.MOTIVO -> put("motivo", "Máximo ${Limites.MOTIVO} caracteres")
        }
        when {
            quienRetira.trim().length < 3 -> put("quienRetira", "Escribe quién retira el material")
            quienRetira.trim().length > Limites.QUIEN_RETIRA -> put("quienRetira", "Máximo ${Limites.QUIEN_RETIRA} caracteres")
        }
    }

    /** «12,5» → «12.5»: en obra se escribe con coma, la API espera punto. */
    fun normalizarCantidad(texto: String): String = texto.trim().replace(',', '.')
}
