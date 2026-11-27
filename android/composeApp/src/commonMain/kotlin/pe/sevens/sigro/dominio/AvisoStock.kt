package pe.sevens.sigro.dominio

/**
 * Regla 2: el saldo lo decide el servidor. Si lo que se pide pasa el último stock conocido, la app AVISA pero no
 * bloquea: esa copia puede estar vieja (pudo llegar una compra desde la web) y, sin señal, no hay cómo saberlo.
 * Al enviarse, el servidor la valida y, si no alcanza, la rechaza con su motivo.
 */
object AvisoStock {
    fun mensaje(cantidadPedida: String, stockConocido: String?, unidad: String, hora: String?): String? {
        val pide = cantidadPedida.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val hay = stockConocido?.trim()?.toDoubleOrNull() ?: return null
        if (pide <= hay) return null
        val cuando = hora?.let { " ($it)" } ?: ""
        return "Pides más de lo que había en el último stock que viste: ${Texto.cantidadConUnidad(stockConocido, unidad)}$cuando. " +
            "Se guarda igual; si no alcanza, el servidor la rechaza con su motivo."
    }
}
