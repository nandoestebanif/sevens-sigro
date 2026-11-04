package pe.sevens.sigro.datos.repositorio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.sevens.sigro.datos.local.AjusteDao
import pe.sevens.sigro.datos.local.InsumoDao
import pe.sevens.sigro.datos.local.TrabajadorDao
import pe.sevens.sigro.datos.local.TrabajadorLocalEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.InsumoDto
import pe.sevens.sigro.datos.remoto.InsumoIn
import pe.sevens.sigro.datos.remoto.TrabajadorDto
import pe.sevens.sigro.datos.remoto.TrabajadorIn
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.BusquedaInsumos
import pe.sevens.sigro.dominio.CatalogoTexto
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.FichaInsumo
import pe.sevens.sigro.dominio.LineaCompra
import pe.sevens.sigro.dominio.NuevoInsumo
import pe.sevens.sigro.dominio.NuevoTrabajador
import pe.sevens.sigro.datos.remoto.StockDto
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.dominio.FilaStock
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.StockServidor
import pe.sevens.sigro.plataforma.Plataforma
import pe.sevens.sigro.dominio.PendienteRecepcion
import pe.sevens.sigro.dominio.RepositorioInsumos
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.RepositorioTrabajadores
import pe.sevens.sigro.dominio.Trabajador

private suspend fun Credenciales.exigir(): Pair<String, String> {
    val s = servidor()
    val t = token()
    if (s == null || t == null) throw ErrorSigro("sin_sesion", "Vuelve a ingresar.")
    return s to t
}

/** HU-10 con señal (servidor) y sin señal (copia del celular); HU-08 registrar con señal. */
class RepositorioInsumosImpl(
    private val api: ApiSigro,
    private val local: InsumoDao,
    ajustes: AjusteDao,
) : RepositorioInsumos {
    private val cred = Credenciales(ajustes)

    override suspend fun buscar(texto: String): BusquedaInsumos {
        val (s, t) = cred.exigir()
        return try {
            // El servidor filtra por texto; aquí se afina sin tildes ni mayúsculas (HU-10).
            val lista = api.insumos(s, t, texto.ifBlank { null }).map(::aFicha)
            BusquedaInsumos(lista.filter { CatalogoTexto.coincide(it, texto) }.ifEmpty { lista }, desdeCelular = false)
        } catch (e: ErrorSigro) {
            if (!e.esTransitorio) throw e
            val copia = local.buscar(texto.trim()).map { FichaInsumo(it.id, it.codigo, it.descripcion, it.unidad) }
            BusquedaInsumos(copia, desdeCelular = true)
        }
    }

    override suspend fun registrar(nuevo: NuevoInsumo): FichaInsumo {
        val (s, t) = cred.exigir()
        return aFicha(api.registrarInsumo(s, t, InsumoIn(nuevo.descripcion, nuevo.unidad.trim(), nuevo.tipo)))
    }

    private fun aFicha(d: InsumoDto) = FichaInsumo(
        id = d.id, codigo = d.codigo, descripcion = d.descripcion, unidadCompra = d.unidadCompra, unidadS10 = d.unidadS10,
        factorConversion = d.factorConversion, tipo = d.tipo, activo = d.activo, verificado = d.verificado,
    )
}

/** HU-62. La lista vive en el celular para la asistencia sin señal; el servidor es quien la manda. */
class RepositorioTrabajadoresImpl(
    private val api: ApiSigro,
    private val local: TrabajadorDao,
    ajustes: AjusteDao,
) : RepositorioTrabajadores {
    private val cred = Credenciales(ajustes)

    override fun deObra(obraId: Int): Flow<List<Trabajador>> =
        local.observarDeObra(obraId).map { l -> l.map(::aDominio) }

    override suspend fun actualizar(obraId: Int): Int {
        val (s, t) = cred.exigir()
        val lista = api.trabajadores(s, t, obraId).map { aLocal(obraId, it) }
        local.guardarTodos(lista)
        return lista.size
    }

    override suspend fun registrar(obraId: Int, nuevo: NuevoTrabajador): Trabajador {
        val (s, t) = cred.exigir()
        val cat = requireNotNull(nuevo.categoria) { "Falta la categoría" }
        val d = api.registrarTrabajador(
            s, t, obraId,
            TrabajadorIn(nuevo.nombre.trim(), nuevo.tipoDocumento, nuevo.documento.trim(), cat.api, nuevo.cuadrilla.trim(), nuevo.fechaIngreso.trim()),
        )
        val fila = aLocal(obraId, d)
        local.guardarTodos(listOf(fila))
        return aDominio(fila)
    }

    private fun aLocal(obraId: Int, d: TrabajadorDto) = TrabajadorLocalEntity(
        d.id, obraId, d.nombre, d.tipoDocumento, d.documento, d.categoria, d.cuadrilla, d.fechaIngreso, d.fechaSalida,
    )

    private fun aDominio(e: TrabajadorLocalEntity) = Trabajador(
        e.id, e.obraId, e.nombre, e.tipoDocumento, e.documento, e.categoria, e.cuadrilla, e.fechaIngreso, e.fechaSalida,
    )
}

/** Lecturas que las pantallas de almacén necesitan (con señal; el stock, además, el último recibido). */
class RepositorioObraImpl(
    private val api: ApiSigro,
    ajustes: AjusteDao,
    private val reloj: () -> Long = Plataforma::ahoraMs,
) : RepositorioObra {
    private val cred = Credenciales(ajustes)

    override suspend fun stock(obraId: Int, almacenId: Int?): StockServidor {
        val (s, t) = cred.exigir()
        val clave = "stock:$obraId:${almacenId ?: "obra"}"
        return try {
            val texto = api.stockTexto(s, t, obraId, almacenId)
            val ahora = reloj()
            cred.guardar(clave, "$ahora|$texto")
            aStock(texto, almacenId, ahora, desdeCelular = false)
        } catch (e: ErrorSigro) {
            if (!e.esTransitorio) throw e
            val guardado = cred.leer(clave) ?: throw e
            aStock(guardado.substringAfter('|'), almacenId, guardado.substringBefore('|').toLongOrNull() ?: 0L, desdeCelular = true)
        }
    }

    private fun aStock(texto: String, almacenId: Int?, recibidoMs: Long, desdeCelular: Boolean): StockServidor {
        val d = jsonSigro.decodeFromString(StockDto.serializer(), texto)
        val filas = (if (almacenId == null) d.consolidado else d.porAlmacen).map {
            FilaStock(it.insumoId, it.insumoCodigo, it.descripcion, it.unidad, it.cantidad, it.ultimoMovimientoEn, it.bajoPuntoReposicion)
        }
        return StockServidor(filas, d.corte, recibidoMs, desdeCelular)
    }

    override suspend fun almacenes(obraId: Int): List<AlmacenObra> {
        val (s, t) = cred.exigir()
        return api.almacenes(s, t, obraId).filter { it.estado != "cerrado" }.map { AlmacenObra(it.id, it.nombre) }
    }

    override suspend fun compras(obraId: Int): List<CompraParaRecibir> {
        val (s, t) = cred.exigir()
        return api.compras(s, t, obraId).filter { it.estado == "registrada" }.map { c ->
            CompraParaRecibir(
                c.id, c.proveedor, "${c.tipoComprobante} ${c.numeroComprobante}", c.fecha,
                c.lineas.map { LineaCompra(it.insumoCodigo, it.descripcion, it.cantidad, it.unidadCompra) },
            )
        }
    }

    override suspend fun estadoRecepcion(obraId: Int, compraId: Int): List<PendienteRecepcion> {
        val (s, t) = cred.exigir()
        return api.estadoRecepcion(s, t, obraId, compraId).map { PendienteRecepcion(it.insumoId, it.unidadCompra, it.pedido, it.recibido, it.pendiente) }
    }

    override suspend fun obrasDestino(obraId: Int): List<ObraAsignada> {
        val (s, t) = cred.exigir()
        return api.obras(s, t).filter { it.id != obraId && it.estado != "cerrada" }.map { ObraAsignada(it.id, it.nombre, it.rol.orEmpty()) }
    }
}
