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
    ENTRADA_COMPRA("Entrada de compra", true), // POST /obras/{id}/almacen/recepciones (M5)
    INGRESO_SIN_COMPRA("Ingreso sin compra", true), // POST /obras/{id}/almacen/ingresos (A5)
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
    /** A1: lo que dijo el servidor al recibirlo. */
    val registradoPor: String? = null,
    val fechaServidor: String? = null,
)

/** Nota con la que queda un registro de almacén mientras no exista su servicio. */
const val NOTA_SIN_SERVICIO_ALMACEN = "Se envía cuando exista el servicio almacén: queda guardado en el celular."

// ── Almacén ─────────────────────────────────────────────────────────────────────────────────
// La entrada de compra y el ingreso sin compra ya se envían; traslado, conteo y cierre se guardan y esperan.

/** Línea con insumo y cantidad: entrada, ingreso, traslado y conteo la usan igual. */
data class LineaAlmacen(val insumo: Insumo, val cantidad: String)

/** Compra de la obra para recibir (HU-35). Viene de GET /obras/{id}/compras. */
data class CompraParaRecibir(
    val id: Int,
    val proveedor: String,
    val comprobante: String,
    val fecha: String,
    val lineas: List<LineaCompra>,
) {
    /** Con el estado de recepción del servidor cada línea sabe su insumo y su pendiente: se puede recibir una parte. */
    val conPendientes: Boolean get() = lineas.isNotEmpty() && lineas.all { it.insumoId != null && it.pendiente != null }

    /** Ya se recibió completa (según el servidor). */
    val recibidaCompleta: Boolean
        get() = conPendientes && lineas.all { (ValidarSalida.normalizarCantidad(it.pendiente!!).toDoubleOrNull() ?: 0.0) <= 0.0 }
}

/**
 * Línea de compra en unidad de compra. `insumoId` y `pendiente` llegan de
 * GET /obras/{id}/almacen/compras/{compra_id}/recepcion; sin señal quedan nulos.
 */
data class LineaCompra(
    val insumoCodigo: String,
    val descripcion: String,
    val cantidad: String,
    val unidad: String,
    val insumoId: Int? = null,
    val pendiente: String? = null,
) {
    /** Lo que todavía se puede recibir: el pendiente del servidor o, si no se sabe, lo comprado. */
    val recibible: String get() = pendiente ?: cantidad
}

data class AlmacenObra(val id: Int, val nombre: String)

/**
 * M6 · qué almacén usar. Con uno solo activo se elige solo; con varios, el último elegido en esa obra si
 * sigue activo (si no, el almacenero elige). `lista` nula = no se pudo consultar (sin señal): se usa el
 * último elegido, que se guarda en el celular justamente para eso.
 */
object ElegirAlmacen {
    fun inicial(lista: List<AlmacenObra>?, recordado: AlmacenObra?): AlmacenObra? = when {
        lista == null -> recordado
        lista.size == 1 -> lista.first()
        else -> lista.firstOrNull { it.id == recordado?.id }
    }

    /** Hay que preguntar: varios almacenes y ninguno elegido. */
    fun falta(lista: List<AlmacenObra>, elegido: AlmacenObra?): Boolean = elegido == null && lista.size > 1

    /**
     * Une las líneas de la compra con el estado de recepción. El servidor arma ambas listas en el orden de
     * `compra_detalle.id`; si no calzan (cantidad u orden distinto), no se adivina: quedan sin pendiente.
     */
    fun unirPendientes(lineas: List<LineaCompra>, estado: List<EstadoLineaRecepcion>): List<LineaCompra> {
        if (lineas.size != estado.size) return lineas
        val calzan = lineas.zip(estado).all { (l, e) ->
            l.unidad == e.unidad && l.cantidad.toDoubleOrNull() == e.pedido.toDoubleOrNull()
        }
        if (!calzan) return lineas
        return lineas.zip(estado).map { (l, e) -> l.copy(insumoId = e.insumoId, pendiente = e.pendiente) }
    }
}

/**
 * HU-36 · lo que responde POST /obras/{id}/almacen/recepciones/verificacion, ya sin Ktor. Lo calcula SIEMPRE el
 * servidor (regla 2): la app solo avisa con esto. Cantidades en la unidad del S10 (la del requerimiento).
 * `requerido` nulo = el insumo no está en el requerimiento de la obra (no se avisa).
 */
data class ExcesoLinea(
    val insumoId: Int,
    val insumoCodigo: String,
    val unidad: String,
    val requerido: String?,
    val yaRecibido: String,
    val conEsta: String,
    val exceso: String?,
    val excede: Boolean,
)

data class VerificacionRecepcion(val hayExceso: Boolean, val lineas: List<ExcesoLinea>) {
    val excedidas: List<ExcesoLinea> get() = lineas.filter { it.excede }
}

/** D11 · por qué se recibe más de lo requerido en el S10 (lienzo de la app, Entrada · «Pasa lo requerido»). */
enum class MotivoExceso(val legible: String) {
    CAMBIO("Cambio pedido por el residente"),
    REPOSICION("Reponer material dañado"),
    S10_CORTO("El S10 se quedó corto"),
    OTRO("Otro motivo"),
}

object ExcesoRequerido {
    private fun num(t: String?) = t?.let { ValidarSalida.normalizarCantidad(it).toDoubleOrNull() }

    /** Texto del motivo que viaja como `motivo_exceso` (máx. 300). «Otro» exige que se escriba. */
    fun motivo(m: MotivoExceso?, detalle: String?): String? = when (m) {
        null -> null
        MotivoExceso.OTRO -> detalle?.trim()?.take(300)?.ifEmpty { null }
        else -> m.legible
    }

    /** Una línea del aviso: «Clavo: requerido 60 kg · ya recibido 55 kg · con esta entrada 65 kg (5 kg de más)». */
    fun detalle(l: ExcesoLinea, nombre: String): String =
        "$nombre: requerido en el S10 ${fmt(l.requerido)} ${l.unidad} · ya recibido ${fmt(l.yaRecibido)} ${l.unidad} · " +
            "con esta entrada ${fmt(l.conEsta)} ${l.unidad} (${fmt(l.exceso)} ${l.unidad} de más)"

    /**
     * «No, recibir solo hasta lo requerido»: baja lo recibido de cada línea que se pasa. El exceso viene en la unidad
     * del S10 y lo recibido en la de compra; el factor sale de la propia respuesta (lo que suma esta entrada en el
     * S10 ÷ lo que se envió en unidad de compra), sin adivinar. Redondea HACIA ABAJO a 2 decimales para no pasarse.
     * Devuelve índice de línea → nueva cantidad (en unidad de compra).
     */
    fun hastaLoRequerido(lineas: List<LineaCompra>, recibido: Map<Int, String>, v: VerificacionRecepcion): Map<Int, String> {
        val porInsumo = v.excedidas.associateBy { it.insumoId }
        return buildMap {
            lineas.forEachIndexed { i, l ->
                val x = porInsumo[l.insumoId ?: return@forEachIndexed] ?: return@forEachIndexed
                val enviado = num(recibido[i]) ?: return@forEachIndexed
                val sumaS10 = (num(x.conEsta) ?: return@forEachIndexed) - (num(x.yaRecibido) ?: 0.0)
                if (enviado <= 0.0 || sumaS10 <= 0.0) return@forEachIndexed
                val factor = sumaS10 / enviado
                val nuevo = (enviado - (num(x.exceso) ?: 0.0) / factor).coerceAtLeast(0.0)
                put(i, fmt(kotlin.math.floor(nuevo * 100 + 1e-9) / 100))
            }
        }
    }

    fun fmt(t: String?): String = num(t)?.let { fmt(it) } ?: "—"

    fun fmt(d: Double): String {
        val r = kotlin.math.round(d * 100) / 100
        val entero = r.toLong()
        val cent = kotlin.math.round((r - entero) * 100).toInt()
        return if (cent == 0) "$entero" else "$entero." + cent.toString().padStart(2, '0').trimEnd('0')
    }
}

/** Una fila de GET …/compras/{id}/recepcion, ya sin Ktor. Cantidades en unidad de compra. */
data class EstadoLineaRecepcion(val insumoId: Int, val unidad: String, val pedido: String, val recibido: String, val pendiente: String)

/** HU-41. `api` = los valores de `ORIGENES` del servicio almacén. «Otro» y «saldo de otra obra» exigen detalle. */
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
