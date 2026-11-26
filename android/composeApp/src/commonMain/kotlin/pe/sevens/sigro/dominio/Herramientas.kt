package pe.sevens.sigro.dominio

/**
 * Préstamo y devolución de herramientas y equipos (backend: migración 0023). Prestar NO mueve el saldo: la
 * herramienta sigue en la obra; disponible = en almacén − prestado sin devolver. Se registra con señal: el
 * servidor revisa en el acto que haya disponible (dos almaceneros no prestan el mismo taladro dos veces).
 */
data class PrestamoHerramienta(
    val id: Int,
    val almacen: String,
    val codigo: String,
    val descripcion: String,
    val unidad: String,
    val cantidad: String,
    val responsable: String,
    val devolverEl: String?,
    val prestadoEn: String,
    val devueltoEn: String?,
    val estadoDevolucion: EstadoDevolucion?,
    val notaDevolucion: String?,
    val atrasado: Boolean,
)

/** Herramienta con saldo en un almacén: lo que hay, lo prestado y lo que se puede prestar. */
data class HerramientaDisponible(
    val almacenId: Int,
    val almacen: String,
    val insumoId: Int,
    val codigo: String,
    val descripcion: String,
    val unidad: String,
    val enAlmacen: String,
    val prestado: String,
    val disponible: String,
)

enum class EstadoDevolucion(val api: String, val legible: String) {
    BUENO("bueno", "En buen estado"), DANADO("danado", "Dañada"), PERDIDO("perdido", "Perdida");

    companion object { fun deApi(s: String?) = entries.firstOrNull { it.api == s } }
}

data class PanelHerramientas(val prestamos: List<PrestamoHerramienta>, val herramientas: List<HerramientaDisponible>) {
    val sinDevolver get() = prestamos.filter { it.devueltoEn == null }
    val atrasados get() = sinDevolver.count { it.atrasado }
}

interface RepositorioPrestamos {
    suspend fun panel(obraId: Int): PanelHerramientas
    suspend fun prestar(obraId: Int, h: HerramientaDisponible, cantidad: String, responsable: String, devolverEl: String?, nota: String?): PrestamoHerramienta
    suspend fun devolver(obraId: Int, prestamoId: Int, estado: EstadoDevolucion, nota: String?): List<String>
}

/** Reglas del formulario, iguales a las del servidor (Contratos.PRESTAMO / DEVOLUCION). */
object ValidarPrestamo {
    fun errores(h: HerramientaDisponible?, cantidad: String, responsable: String): Map<String, String> = buildMap {
        if (h == null) put("herramienta", "Elige la herramienta")
        ValidarAlmacen.cantidad(cantidad)?.let { put("cantidad", it) }
        if (h != null && "cantidad" !in this) {
            val pide = ValidarSalida.normalizarCantidad(cantidad).toDoubleOrNull() ?: 0.0
            val hay = h.disponible.toDoubleOrNull() ?: 0.0
            if (pide > hay) put("cantidad", "Solo hay ${Texto.cantidad(h.disponible)} disponibles")
        }
        when {
            responsable.trim().length < 3 -> put("responsable", "Escribe quién se la lleva (mínimo 3 letras)")
            responsable.trim().length > Limites.QUIEN_RETIRA -> put("responsable", "Máximo ${Limites.QUIEN_RETIRA} caracteres")
        }
    }

    fun errorDevolucion(estado: EstadoDevolucion, nota: String): String? =
        if (estado != EstadoDevolucion.BUENO && nota.isBlank()) "Si vuelve dañada o se perdió, escribe qué pasó: queda como constancia." else null
}

/**
 * Qué ve cada rol en el celular (regla 9). Es comodidad: el servidor igual responde 403 a lo que no le toca.
 * Almacenero: movimientos de almacén. SSOMA: personal y asistencia. Residente, administrador y gerente: consulta.
 */
enum class AccesoApp { SALIDA, ENTRADA, INGRESO, CONTEO, PERSONAL, HORAS, STOCK, HERRAMIENTAS, CATALOGO }

object VistaPorRol {
    fun accesos(rol: String): Set<AccesoApp> = when (rol) {
        "almacenero" -> setOf(AccesoApp.SALIDA, AccesoApp.ENTRADA, AccesoApp.INGRESO, AccesoApp.CONTEO, AccesoApp.STOCK, AccesoApp.HERRAMIENTAS, AccesoApp.CATALOGO)
        "ssoma" -> setOf(AccesoApp.PERSONAL, AccesoApp.HORAS, AccesoApp.STOCK, AccesoApp.HERRAMIENTAS, AccesoApp.CATALOGO)
        else -> setOf(AccesoApp.HORAS, AccesoApp.STOCK, AccesoApp.HERRAMIENTAS, AccesoApp.CATALOGO)
    }

    /** Registra desde el celular (almacenero o SSOMA) o solo consulta (residente, administrador, gerente). */
    fun registra(rol: String) = rol == "almacenero" || rol == "ssoma"
}
