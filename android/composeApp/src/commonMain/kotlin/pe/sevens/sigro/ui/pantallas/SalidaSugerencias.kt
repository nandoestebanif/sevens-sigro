package pe.sevens.sigro.ui.pantallas

import pe.sevens.sigro.dominio.HoraLima
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.StockObra
import pe.sevens.sigro.dominio.Texto

/* Lógica del formulario de salida que no depende de Compose (se prueba sin pantalla). */

data class SugerenciasSalida(
    val motivos: List<String> = emptyList(),
    val quienes: List<String> = emptyList(),
    val frentes: List<String> = emptyList(),
)

/**
 * Lo más reciente primero, sin repetir (sin importar mayúsculas ni espacios) y sin textos de menos de 3 letras
 * («ya», «27d» no ayudan a nadie). Los trabajadores de la obra completan «quién retira».
 */
fun sugerenciasSalida(salidas: List<SalidaRegistrada>, trabajadores: List<String> = emptyList(), tope: Int = 4): SugerenciasSalida {
    val recientes = salidas.sortedByDescending { it.horaDispositivoMs }
    fun distintos(xs: List<String>) = xs.map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.count(Char::isLetter) >= 3 }
        .distinctBy { it.lowercase() }
        .take(tope)
    return SugerenciasSalida(
        motivos = distintos(recientes.map { it.motivo }),
        quienes = distintos(recientes.map { it.quienRetira } + trabajadores),
        frentes = distintos(recientes.mapNotNull { it.frente }),
    )
}

/** «Último stock: 40 bolsas · 12/11/2026 12:20». Del servidor, del almacén elegido; null si no se sabe. */
fun stockConocido(stock: StockObra?, insumo: Insumo?, almacenId: Int?): String? {
    if (stock == null || insumo == null) return null
    val fila = stock.porAlmacen.firstOrNull { it.insumoId == insumo.id && (almacenId == null || it.almacenId == almacenId) }
        ?: if (almacenId == null) stock.consolidado.firstOrNull { it.insumoId == insumo.id } else null
    val hora = HoraLima.deIso(stock.corte)
    val cuanto = fila?.let { Texto.cantidadConUnidad(it.cantidad, insumo.unidad) } ?: "sin movimientos"
    return "Último stock: $cuanto" + (hora?.let { " · $it" } ?: "")
}

