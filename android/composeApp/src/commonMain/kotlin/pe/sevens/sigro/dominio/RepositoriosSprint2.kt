package pe.sevens.sigro.dominio

import kotlinx.coroutines.flow.Flow

/* Contratos del Sprint 2 que la UI usa. Implementación en datos/ (Ktor + Room); dobles en commonTest. */

/** Catálogo completo (HU-08, HU-10, HU-17). La copia de búsqueda sin señal sigue en [RepositorioCatalogo]. */
interface RepositorioInsumos {
    /** Con señal busca en el servidor; sin señal, en la copia del celular (y lo dice). */
    suspend fun buscar(texto: String): BusquedaInsumos
    /** POST /catalogo/insumos: necesita señal. El servidor pone el código único. */
    suspend fun registrar(nuevo: NuevoInsumo): FichaInsumo
}

/** Trabajadores de la obra (HU-62). La lista se guarda en el celular para tomar asistencia sin señal. */
interface RepositorioTrabajadores {
    fun deObra(obraId: Int): Flow<List<Trabajador>>
    /** Baja la lista del servidor y la deja en el celular. Devuelve cuántos hay. */
    suspend fun actualizar(obraId: Int): Int
    /** POST /obras/{id}/trabajadores: necesita señal. */
    suspend fun registrar(obraId: Int, nuevo: NuevoTrabajador): Trabajador
}

/**
 * Cola general sin señal (HU-37 / HU-38 para lo que no es una salida). Cada registro nace con su
 * `uuid_cliente`, se guarda en el celular antes de enviarse, y un rechazo queda con su motivo.
 */
interface RepositorioRegistros {
    fun deObra(obraId: Int): Flow<List<RegistroLocal>>
    /** Cuántos esperan envío, contando solo los que ya tienen servicio. */
    val pendientes: Flow<Int>
    /** HU-63: una jornada por trabajador; un trabajador ya marcado ese día no se marca dos veces. */
    suspend fun registrarAsistencia(obraId: Int, fecha: String, marcas: List<MarcaAsistencia>): List<RegistroLocal>
    /** Trabajadores que ya tienen jornada guardada en el celular ese día (no rechazada). */
    suspend fun yaMarcados(obraId: Int, fecha: String): Set<Int>
    /** Registros de almacén: se guardan, no se envían hasta que exista el servicio (fase 2). */
    suspend fun registrarAlmacen(obraId: Int, registro: RegistroAlmacen): RegistroLocal
    /** Envía lo que tiene servicio. Seguro de repetir: el uuid hace idempotente cada envío. */
    suspend fun sincronizar(): ResultadoSincronizacion
}

/** Lo que guardan las pantallas de almacén. El cuerpo sigue el contrato propuesto (api/docs/propuestas). */
sealed interface RegistroAlmacen {
    val tipo: TipoRegistro

    /** `todo`: llegó todo lo pendiente (la recepción viaja sin líneas y el servidor recibe el total pendiente). */
    data class Entrada(val almacenId: Int?, val compra: CompraParaRecibir, val recibido: List<Pair<LineaCompra, String>>, val todo: Boolean) : RegistroAlmacen {
        override val tipo get() = TipoRegistro.ENTRADA_COMPRA
    }
    data class IngresoSinCompra(val almacenId: Int?, val origen: OrigenIngreso, val linea: LineaAlmacen, val costoUnitario: String, val detalle: String?) : RegistroAlmacen {
        override val tipo get() = TipoRegistro.INGRESO_SIN_COMPRA
    }
    data class Traslado(
        val almacenId: Int?, val obraDestinoId: Int, val obraDestino: String, val destinoAlmacenId: Int, val motivo: String,
        val lineas: List<LineaAlmacen>,
    ) : RegistroAlmacen {
        override val tipo get() = TipoRegistro.TRASLADO
    }
    data class Conteo(val almacenId: Int?, val fecha: String, val lineas: List<Pair<LineaAlmacen, MotivoDiferencia?>>) : RegistroAlmacen {
        override val tipo get() = TipoRegistro.CONTEO
    }
    data class Cierre(val almacenId: Int?, val fecha: String, val observacion: String?) : RegistroAlmacen {
        override val tipo get() = TipoRegistro.CIERRE
    }
}

/** Lecturas de la obra que el almacén necesita: compras por recibir, almacenes y obras destino. */
interface RepositorioObra {
    suspend fun almacenes(obraId: Int): List<AlmacenObra>
    suspend fun compras(obraId: Int): List<CompraParaRecibir>
    /** Lo pedido, recibido y pendiente de una compra según el servidor (necesita señal). */
    suspend fun estadoRecepcion(obraId: Int, compraId: Int): List<PendienteRecepcion>
    /** Obras a las que se puede trasladar (GET /obras), sin la propia. */
    suspend fun obrasDestino(obraId: Int): List<ObraAsignada>
}
