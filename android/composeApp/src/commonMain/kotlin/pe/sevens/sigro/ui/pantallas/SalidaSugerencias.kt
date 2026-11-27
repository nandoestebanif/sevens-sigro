package pe.sevens.sigro.ui.pantallas

import pe.sevens.sigro.dominio.AvisoStock
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
private fun filaStock(stock: StockObra, insumo: Insumo, almacenId: Int?) =
    stock.porAlmacen.firstOrNull { it.insumoId == insumo.id && (almacenId == null || it.almacenId == almacenId) }
        ?: if (almacenId == null) stock.consolidado.firstOrNull { it.insumoId == insumo.id } else null

fun stockConocido(stock: StockObra?, insumo: Insumo?, almacenId: Int?): String? {
    if (stock == null || insumo == null) return null
    val fila = filaStock(stock, insumo, almacenId)
    val hora = HoraLima.deIso(stock.corte)
    val cuanto = fila?.let { Texto.cantidadConUnidad(it.cantidad, insumo.unidad) } ?: "sin movimientos"
    return "Último stock: $cuanto" + (hora?.let { " · $it" } ?: "")
}

/** Aviso (no bloquea) si lo pedido pasa el último stock conocido de ese almacén; «sin movimientos» cuenta como 0. */
fun avisoSobreStock(stock: StockObra?, insumo: Insumo?, almacenId: Int?, cantidad: String): String? {
    if (stock == null || insumo == null) return null
    val fila = if (almacenId == null) stock.consolidado.firstOrNull { it.insumoId == insumo.id }   // sin almacén: toda la obra
        else stock.porAlmacen.firstOrNull { it.insumoId == insumo.id && it.almacenId == almacenId }
    val conocido = fila?.cantidad ?: "0"
    return AvisoStock.mensaje(cantidad, conocido, insumo.unidad, HoraLima.deIso(stock.corte))
}


/* ── Revisión del 17/11 (prueba de Fernando en celular) ─────────────────────────────────────────────
   1. La salida solo ofrece lo que hay en el almacén elegido, no todo el catálogo.
   2. «Retira» se elige entre los trabajadores activos de la obra. */

/** Un insumo que se puede sacar: el dato es el último saldo que dio el SERVIDOR (regla 2: no se calcula aquí). */
data class InsumoConStock(val insumo: Insumo, val cantidad: String)

/**
 * Insumos con saldo mayor a cero en el almacén elegido (o en toda la obra si aún no se eligió), filtrados por
 * nombre o código. `null` cuando el celular todavía no tiene ningún stock guardado: entonces no se puede filtrar
 * y la pantalla cae al catálogo, diciéndolo.
 */
fun insumosConStock(stock: StockObra?, almacenId: Int?, texto: String): List<InsumoConStock>? {
    if (stock == null) return null
    val filas = if (almacenId != null) stock.porAlmacen.filter { it.almacenId == almacenId } else stock.consolidado
    val t = texto.trim().lowercase()
    return filas
        .filter { (it.cantidad.toDoubleOrNull() ?: 0.0) > 0.0 }
        .filter { t.isEmpty() || it.descripcion.lowercase().contains(t) || it.codigo.lowercase().contains(t) }
        .distinctBy { it.insumoId }
        .sortedBy { it.descripcion.lowercase() }
        .map { InsumoConStock(Insumo(it.insumoId, it.codigo, it.descripcion, it.unidad), it.cantidad) }
}

/** Trabajadores activos (sin fecha de salida) cuyo nombre contiene lo escrito, sin importar tildes ni mayúsculas. */
fun trabajadoresQueCoinciden(nombres: List<String>, texto: String): List<String> {
    fun norm(s: String) = s.lowercase()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')
        .replace(Regex("\\s+"), " ").trim()
    val t = norm(texto)
    return nombres.distinctBy { norm(it) }.filter { t.isEmpty() || norm(it).contains(t) }.sortedBy { norm(it) }
}

/** Con la lista de la obra cargada, quien retira debe ser uno de ellos (así el registro dice exactamente quién fue). */
fun errorQuienRetira(quien: String, trabajadores: List<String>): String? {
    if (trabajadores.isEmpty()) return null // sin lista en el celular: se acepta lo escrito, como antes
    val ok = trabajadores.any { it.trim().equals(quien.trim(), ignoreCase = true) }
    return if (ok) null else "Elige a alguien de la obra. Si no aparece, regístralo primero como trabajador de la obra."
}
