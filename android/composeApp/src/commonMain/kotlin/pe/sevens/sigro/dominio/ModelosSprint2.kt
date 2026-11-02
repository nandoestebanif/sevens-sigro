package pe.sevens.sigro.dominio

/*
 * Modelos del Sprint 2 en la app: catálogo (HU-08, HU-10, HU-17), mano de obra (HU-62, HU-63),
 * la cola general de registros y los registros de almacén que esperan su servicio.
 * Igual que Modelos.kt: no dependen de Ktor, de Room ni de Compose.
 */

// ── Catálogo ────────────────────────────────────────────────────────────────────────────────

/**
 * Ficha de un insumo del catálogo único (regla 8). Los campos nulos son los que la copia del
 * celular no guarda: sin señal se ve código, nombre y unidad; lo demás llega con señal.
 */
data class FichaInsumo(
    val id: Int,
    val codigo: String,
    val descripcion: String,
    val unidadCompra: String,
    val unidadS10: String? = null,
    val factorConversion: String? = null,
    val tipo: String? = null,
    val activo: Boolean? = null,
    val verificado: Boolean? = null,
) {
    val tipoLegible: String
        get() = when (tipo) {
            "material" -> "Material"
            "mano_obra" -> "Mano de obra"
            "equipo" -> "Equipo"
            "subcontrato" -> "Subcontrato"
            null -> "—"
            else -> tipo
        }

    /** HU-17: sin factor declarado ambas unidades son la misma. */
    val tieneFactor: Boolean
        get() = factorConversion != null && unidadS10 != null && unidadS10 != unidadCompra &&
            factorConversion.toDoubleOrNull()?.let { it != 1.0 } == true

    fun comoInsumo() = Insumo(id, codigo, descripcion, unidadCompra)
}

/** Resultado de buscar en el catálogo: con señal viene del servidor; sin señal, de la copia del celular. */
data class BusquedaInsumos(val insumos: List<FichaInsumo>, val desdeCelular: Boolean)

/** Lo que el almacenero llena en «Insumo nuevo» (HU-08). El código lo pone el servidor. */
data class NuevoInsumo(
    val tipo: String,
    val material: String,
    val medida: String,
    val marca: String,
    val norma: String,
    val unidad: String,
) {
    /** El nombre estandarizado que se guarda: «MATERIAL - MEDIDA - NORMA - MARCA», como el S10. */
    val descripcion: String
        get() = listOf(material, medida, norma, marca).map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(" - ").uppercase()
}

object CatalogoTexto {
    private val tildes = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')

    /** HU-10: ignora mayúsculas, tildes y espacios de más. */
    fun normalizar(s: String): String =
        s.lowercase().map { tildes[it] ?: it }.joinToString("").trim().replace(Regex("\\s+"), " ")

    /** Coincide si cada palabra buscada aparece en el nombre o el código (en cualquier orden). */
    fun coincide(i: FichaInsumo, consulta: String): Boolean {
        val q = normalizar(consulta)
        if (q.isEmpty()) return true
        val texto = normalizar(i.descripcion) + " " + normalizar(i.codigo)
        return q.split(' ').all { texto.contains(it) }
    }

    /**
     * HU-10 / HU-08: antes de crear, los insumos que ya se parecen al nuevo. Se comparan las palabras
     * de más de dos letras del material y la medida.
     */
    fun parecidos(nuevo: NuevoInsumo, catalogo: List<FichaInsumo>): List<FichaInsumo> {
        val palabras = normalizar(nuevo.material + " " + nuevo.medida).split(' ').filter { it.length > 2 }
        if (palabras.isEmpty()) return emptyList()
        return catalogo.filter { c -> val n = normalizar(c.descripcion); palabras.all { n.contains(it) } }
    }

    /** Mismo nombre estandarizado que uno existente: no se registra dos veces (HU-08). */
    fun duplicado(nuevo: NuevoInsumo, catalogo: List<FichaInsumo>): FichaInsumo? {
        val d = normalizar(nuevo.descripcion)
        return catalogo.firstOrNull { normalizar(it.descripcion) == d }
    }
}

object ValidarInsumo {
    fun errores(n: NuevoInsumo): Map<String, String> = buildMap {
        if (n.material.trim().length < 3) put("material", "Escribe el material (mínimo 3 letras)")
        if (n.unidad.isBlank()) put("unidad", "Elige la unidad en que se compra")
        if (n.descripcion.length > 200) put("material", "El nombre completo pasa de 200 letras: acórtalo")
        if (n.unidad.length > 20) put("unidad", "La unidad tiene como máximo 20 letras")
    }
}

// ── Mano de obra ────────────────────────────────────────────────────────────────────────────

enum class Categoria(val api: String, val legible: String) {
    OPERARIO("operario", "Operario"), OFICIAL("oficial", "Oficial"), PEON("peon", "Peón");

    companion object {
        fun de(api: String): Categoria? = entries.firstOrNull { it.api == api }
    }
}

/** Trabajador de la obra (HU-62). La baja es una fecha de salida: nunca se borra (regla 10). */
data class Trabajador(
    val id: Int,
    val obraId: Int,
    val nombre: String,
    val tipoDocumento: String,
    val documento: String,
    val categoria: String,
    val cuadrilla: String,
    val fechaIngreso: String,
    val fechaSalida: String?,
) {
    val categoriaLegible: String get() = Categoria.de(categoria)?.legible ?: categoria

    /** Activo en una fecha ISO: ingresó antes o ese día y no salió antes. Comparar ISO como texto es seguro. */
    fun activoEl(fecha: String): Boolean = fechaIngreso <= fecha && (fechaSalida == null || fechaSalida >= fecha)
}

data class NuevoTrabajador(
    val nombre: String,
    val tipoDocumento: String,
    val documento: String,
    val categoria: Categoria?,
    val cuadrilla: String,
    val fechaIngreso: String,
)

object ValidarTrabajador {
    private val fechaIso = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** HU-62: exige nombre, documento, categoría y cuadrilla; no dos veces el mismo documento en la obra. */
    fun errores(n: NuevoTrabajador, deLaObra: List<Trabajador>): Map<String, String> = buildMap {
        val doc = n.documento.trim()
        when {
            n.tipoDocumento == "DNI" && !Regex("""^\d{8}$""").matches(doc) -> put("documento", "El DNI tiene 8 dígitos")
            doc.length !in 6..20 -> put("documento", "Entre 6 y 20 caracteres")
            deLaObra.any { it.documento == doc && it.tipoDocumento == n.tipoDocumento } -> {
                val quien = deLaObra.first { it.documento == doc }.nombre
                put("documento", "Ese documento ya está en esta obra ($quien). Puede estar en otra obra, pero no dos veces en la misma.")
            }
        }
        if (n.nombre.trim().length < 3) put("nombre", "Escribe nombres y apellidos")
        if (n.categoria == null) put("categoria", "Elige la categoría")
        if (n.cuadrilla.isBlank()) put("cuadrilla", "Elige o escribe la cuadrilla")
        if (!fechaIso.matches(n.fechaIngreso.trim())) put("fechaIngreso", "Fecha como 2026-11-01")
    }
}

enum class EstadoAsistencia(val api: String, val legible: String) {
    PRESENTE("presente", "Presente"), AUSENTE("ausente", "Ausente"), PERMISO("permiso", "Permiso"),
}

/** Lo que se marca de un trabajador en la asistencia del día (HU-63). Las horas viajan como texto decimal. */
data class MarcaAsistencia(
    val trabajador: Trabajador,
    val estado: EstadoAsistencia,
    val horasNormales: Int = 8,
    val horasExtra: Int = 0,
) {
    /** Solo el presente lleva horas; ausente y permiso van en cero (HU-63). */
    val normalesEnvio: String get() = if (estado == EstadoAsistencia.PRESENTE) horasNormales.toString() else "0"
    val extraEnvio: String get() = if (estado == EstadoAsistencia.PRESENTE) horasExtra.toString() else "0"
}

/** Resumen del día que pinta la pantalla de asistencia. */
data class ResumenAsistencia(val presentes: Int, val ausentes: Int, val permisos: Int, val horas: Int, val extras: Int)

object Asistencia {
    const val MAX_NORMALES = 8
    const val MAX_EXTRA = 6

    fun resumir(marcas: List<MarcaAsistencia>): ResumenAsistencia {
        val p = marcas.filter { it.estado == EstadoAsistencia.PRESENTE }
        return ResumenAsistencia(
            presentes = p.size,
            ausentes = marcas.count { it.estado == EstadoAsistencia.AUSENTE },
            permisos = marcas.count { it.estado == EstadoAsistencia.PERMISO },
            horas = p.sumOf { it.horasNormales + it.horasExtra },
            extras = p.sumOf { it.horasExtra },
        )
    }

    fun ajustarNormales(m: MarcaAsistencia, delta: Int) = m.copy(horasNormales = (m.horasNormales + delta).coerceIn(0, MAX_NORMALES))
    fun ajustarExtra(m: MarcaAsistencia, delta: Int) = m.copy(horasExtra = (m.horasExtra + delta).coerceIn(0, MAX_EXTRA))
}

// ── Cola general de registros ───────────────────────────────────────────────────────────────

/**
 * Qué se registra en el celular además de las salidas. `conServicio` dice si el servidor ya lo recibe:
 * los de almacén esperan al servicio almacén y NO se envían todavía (quedan «por enviar» con su nota).
 */
enum class TipoRegistro(val legible: String, val conServicio: Boolean) {
    JORNADA("Asistencia", true),
    ENTRADA_COMPRA("Entrada de compra", false),
    INGRESO_SIN_COMPRA("Ingreso sin compra", true),
    TRASLADO("Traslado", false),
    CONTEO("Conteo físico", false),
    CIERRE("Cierre de obra", false),
}

/** Registro de la cola general tal como lo ve la pantalla. El cuerpo que viaja no sale de datos/. */
data class RegistroLocal(
    val uuidCliente: String,
    val obraId: Int,
    val tipo: TipoRegistro,
    val titulo: String,
    val detalle: String,
    val horaDispositivoMs: Long,
    val estado: EstadoEnvio,
    val motivoRechazo: String?,
    val nota: String?,
    val intentos: Int,
)

/** Nota con la que queda un registro de almacén mientras no exista su servicio. */
const val NOTA_SIN_SERVICIO_ALMACEN = "Se envía cuando exista el servicio almacén: queda guardado en el celular."

// ── Almacén (UI lista; se envía en la fase 2) ───────────────────────────────────────────────

/** Línea con insumo y cantidad: entrada, ingreso, traslado y conteo la usan igual. */
data class LineaAlmacen(val insumo: Insumo, val cantidad: String)

/** Compra de la obra para recibir (HU-35). Viene de GET /obras/{id}/compras. */
data class CompraParaRecibir(
    val id: Int,
    val proveedor: String,
    val comprobante: String,
    val fecha: String,
    val lineas: List<LineaCompra>,
)

/** `insumoId` se resuelve con el catálogo del celular por el código (la recepción lo pide por id). */
data class LineaCompra(val insumoCodigo: String, val descripcion: String, val cantidad: String, val unidad: String, val insumoId: Int? = null)

data class AlmacenObra(val id: Int, val nombre: String)

/** Orígenes que acepta POST /almacen/ingresos (`ORIGENES` del servicio almacén). */
enum class OrigenIngreso(val api: String, val legible: String, val exigeDetalle: Boolean = false) {
    DEVOLUCION("devolucion_cuadrilla", "Devolución de cuadrilla"),
    OTRA_OBRA("saldo_otra_obra", "Saldo de otra obra", exigeDetalle = true),
    PRESTAMO("prestamo", "Préstamo"),
    SOBRANTE("sobrante", "Sobrante"),
    OTRO("otro", "Otro", exigeDetalle = true),
}

enum class MotivoDiferencia(val legible: String) { MERMA("Merma"), ERROR("Error de registro"), PERDIDA("Pérdida"), OTRO("Otro") }

object ValidarAlmacen {
    private val decimal = Regex("""^\d{1,12}([.,]\d{1,6})?$""")

    /** Cantidad positiva con hasta 6 decimales; null si está bien. */
    fun cantidad(texto: String, permitirCero: Boolean = false): String? {
        val c = texto.trim()
        return when {
            c.isEmpty() -> "Escribe la cantidad"
            !decimal.matches(c) -> "Solo números, con hasta 6 decimales"
            !permitirCero && (ValidarSalida.normalizarCantidad(c).toDoubleOrNull() ?: 0.0) <= 0.0 -> "Debe ser mayor que cero"
            else -> null
        }
    }

    /** HU-41: el costo unitario es obligatorio, sin él no se valoriza. */
    fun costo(texto: String): String? = if (cantidad(texto) != null) "Escribe el costo unitario en soles" else null
}
