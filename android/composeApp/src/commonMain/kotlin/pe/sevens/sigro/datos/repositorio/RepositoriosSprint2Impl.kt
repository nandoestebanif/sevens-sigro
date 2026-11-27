package pe.sevens.sigro.datos.repositorio

import pe.sevens.sigro.dominio.RecepcionPendiente
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.sevens.sigro.datos.local.InsumoDao
import pe.sevens.sigro.datos.local.TrabajadorDao
import pe.sevens.sigro.datos.local.TrabajadorLocalEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.BajaIn
import pe.sevens.sigro.datos.remoto.InsumoDto
import pe.sevens.sigro.datos.remoto.InsumoIn
import pe.sevens.sigro.datos.remoto.LineaCantidadDto
import pe.sevens.sigro.datos.remoto.TrabajadorDto
import pe.sevens.sigro.datos.remoto.TrabajadorIn
import pe.sevens.sigro.datos.remoto.VerificacionIn
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.BusquedaInsumos
import pe.sevens.sigro.dominio.CatalogoTexto
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.Cotejo
import pe.sevens.sigro.dominio.ResultadoCotejo
import kotlinx.serialization.json.contentOrNull
import pe.sevens.sigro.dominio.ElegirAlmacen
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoLineaRecepcion
import pe.sevens.sigro.dominio.ExcesoLinea
import pe.sevens.sigro.dominio.FichaInsumo
import pe.sevens.sigro.dominio.HorasTrabajador
import pe.sevens.sigro.dominio.LineaCompra
import pe.sevens.sigro.dominio.NuevoInsumo
import pe.sevens.sigro.dominio.NuevoTrabajador
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.RepositorioInsumos
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.RepositorioTrabajadores
import pe.sevens.sigro.dominio.Trabajador
import pe.sevens.sigro.dominio.ValidarSalida
import pe.sevens.sigro.dominio.VerificacionRecepcion

/** HU-10 con señal (servidor) y sin señal (copia del celular); HU-08 registrar con señal. */
class RepositorioInsumosImpl(
    private val api: ApiSigro,
    private val local: InsumoDao,
    private val cred: Credenciales,
) : RepositorioInsumos {

    override suspend fun buscar(texto: String): BusquedaInsumos {
        val s = cred.exigir()
        return try {
            // El servidor filtra por texto; aquí se afina sin tildes ni mayúsculas (HU-10).
            val lista = api.insumos(s, texto.ifBlank { null }).map(::aFicha)
            BusquedaInsumos(lista.filter { CatalogoTexto.coincide(it, texto) }.ifEmpty { lista }, desdeCelular = false)
        } catch (e: ErrorSigro) {
            if (!e.esTransitorio) throw e
            val copia = local.buscar(texto.trim()).map { FichaInsumo(it.id, it.codigo, it.descripcion, it.unidad) }
            BusquedaInsumos(copia, desdeCelular = true)
        }
    }

    override suspend fun registrar(nuevo: NuevoInsumo): FichaInsumo {
        return aFicha(api.registrarInsumo(cred.exigir(), InsumoIn(nuevo.descripcion, nuevo.unidad.trim(), nuevo.tipo)))
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
    private val cred: Credenciales,
) : RepositorioTrabajadores {

    override fun deObra(obraId: Int): Flow<List<Trabajador>> =
        local.observarDeObra(obraId).map { l -> l.map(::aDominio) }

    override suspend fun actualizar(obraId: Int): Int {
        val lista = api.trabajadores(cred.exigir(), obraId).map { aLocal(obraId, it) }
        local.guardarTodos(lista)
        return lista.size
    }

    override suspend fun registrar(obraId: Int, nuevo: NuevoTrabajador): Trabajador {
        val cat = requireNotNull(nuevo.categoria) { "Falta la categoría" }
        val d = api.registrarTrabajador(
            cred.exigir(), obraId,
            TrabajadorIn(nuevo.nombre.trim(), nuevo.tipoDocumento, nuevo.documento.trim(), cat.api, nuevo.cuadrilla.trim(), nuevo.fechaIngreso.trim()),
        )
        val fila = aLocal(obraId, d)
        local.guardarTodos(listOf(fila))
        return aDominio(fila)
    }

    override suspend fun consultarDni(obraId: Int, dni: String): String? =
        try {
            api.consultarDni(cred.exigir(), obraId, dni).nombreCompleto.ifBlank { null }
        } catch (e: ErrorSigro) {
            null   // sin señal, sin permiso o RENIEC caído: se escribe a mano, nunca bloquea el registro
        }

    override suspend fun darDeBaja(obraId: Int, trabajadorId: Int, fechaSalida: String): Trabajador {
        val fila = aLocal(obraId, api.darDeBaja(cred.exigir(), obraId, trabajadorId, BajaIn(fechaSalida.trim())))
        local.guardarTodos(listOf(fila))
        return aDominio(fila)
    }

    override suspend fun horas(obraId: Int): List<HorasTrabajador> =
        api.horasAcumuladas(cred.exigir(), obraId).map {
            HorasTrabajador(
                it.periodoNumero, it.trabajadorId, it.trabajador, it.cuadrilla, it.categoria, it.diasPresente, it.diasAusente,
                it.diasPermiso, it.horasNormales.toDoubleOrNull() ?: 0.0, it.horasExtra.toDoubleOrNull() ?: 0.0, it.ultimaJornada,
            )
        }

    private fun aLocal(obraId: Int, d: TrabajadorDto) = TrabajadorLocalEntity(
        d.id, obraId, d.nombre, d.tipoDocumento, d.documento, d.categoria, d.cuadrilla, d.fechaIngreso, d.fechaSalida,
    )

    private fun aDominio(e: TrabajadorLocalEntity) = Trabajador(
        e.id, e.obraId, e.nombre, e.tipoDocumento, e.documento, e.categoria, e.cuadrilla, e.fechaIngreso, e.fechaSalida,
    )
}

/** Lecturas que las pantallas de almacén necesitan (con señal), y el almacén elegido (sin señal). */
class RepositorioObraImpl(private val api: ApiSigro, private val cred: Credenciales) : RepositorioObra {

    /** Solo los activos: el servidor rechaza registrar en un almacén cerrado. */
    override suspend fun almacenes(obraId: Int): List<AlmacenObra> =
        api.almacenes(cred.exigir(), obraId).filter { it.estado == "activo" }.map { AlmacenObra(it.id, it.nombre) }

    override suspend fun almacenRecordado(obraId: Int): AlmacenObra? {
        val texto = cred.leer(Clave.almacen(obraId)) ?: return null
        val id = texto.substringBefore('|').toIntOrNull() ?: return null
        return AlmacenObra(id, texto.substringAfter('|'))
    }

    override suspend fun recordarAlmacen(obraId: Int, almacen: AlmacenObra) =
        cred.guardar(Clave.almacen(obraId), "${almacen.id}|${almacen.nombre}")

    override suspend fun conPendientes(obraId: Int, compra: CompraParaRecibir): CompraParaRecibir {
        val estado = api.estadoRecepcion(cred.exigir(), obraId, compra.id)
            .map { EstadoLineaRecepcion(it.insumoId, it.unidadCompra, it.pedido, it.recibido, it.pendiente) }
        return compra.copy(lineas = ElegirAlmacen.unirPendientes(compra.lineas, estado))
    }

    override suspend fun verificarRecepcion(obraId: Int, compraId: Int, lineas: List<Pair<Int, String>>?): VerificacionRecepcion? {
        val cuerpo = VerificacionIn(compraId, lineas?.map { (id, c) -> LineaCantidadDto(id, ValidarSalida.normalizarCantidad(c)) })
        val r = try {
            api.verificarRecepcion(cred.exigir(), obraId, cuerpo)
        } catch (e: ErrorSigro) {
            if (e.estadoHttp == 404 && e.codigo == "http_404") return null // servidor sin HU-36 todavía: no se bloquea
            throw e
        }
        return VerificacionRecepcion(r.hayExceso, r.lineas.map {
            ExcesoLinea(it.insumoId, it.insumoCodigo, it.unidad, it.requerido, it.yaRecibido, it.conEsta, it.exceso, it.excede)
        })
    }

    override suspend fun compras(obraId: Int): List<CompraParaRecibir> {
        val servidor = cred.exigir()
        val vigentes = api.compras(servidor, obraId).filter { it.estado == "registrada" }
        // HU-35 (prueba del 18/11): «registrada» solo dice que no está anulada; lo pendiente lo calcula el servidor
        // por compra. Se ofrecen solo las que aún tienen algo por recibir. Si no se puede saber, la compra se muestra.
        // Ya con señal, cada compra baja también su pendiente por línea y la verificación de «llegó todo» (HU-36):
        // así sin señal se puede recibir una parte y avisar del exceso con su motivo.
        return coroutineScope {
            vigentes.map { c ->
                async {
                    val base = CompraParaRecibir(
                        c.id, c.proveedor, "${c.tipoComprobante} ${c.numeroComprobante}", c.fecha,
                        c.lineas.map { LineaCompra(it.insumoCodigo, it.descripcion, it.cantidad, it.unidadCompra) },
                        proveedorRuc = c.proveedorRuc,
                        total = (c.total ?: c.totales?.total)?.contentOrNull,
                        igv = c.totales?.igv?.contentOrNull,
                        tipoComprobante = c.tipoComprobante,
                        numeroComprobante = c.numeroComprobante,
                        moneda = c.moneda,
                    )
                    val estado = runCatching {
                        api.estadoRecepcion(servidor, obraId, c.id).map { EstadoLineaRecepcion(it.insumoId, it.unidadCompra, it.pedido, it.recibido, it.pendiente) }
                    }.getOrNull() ?: return@async base // sin saber lo pendiente, se muestra como antes
                    if (!RecepcionPendiente.queda(estado.map { it.pendiente })) return@async null // ya recibida por completo
                    val referencia = runCatching { verificarRecepcion(obraId, c.id, null) }.getOrNull()
                    base.copy(lineas = ElegirAlmacen.unirPendientes(base.lineas, estado), referencia = referencia)
                }
            }.awaitAll().filterNotNull()
        }
    }

    override suspend fun cotejarQr(obraId: Int, texto: String): Cotejo? {
        val r = try {
            api.cotejarQr(cred.exigir(), obraId, texto)
        } catch (x: ErrorSigro) {
            // el servidor desplegado aún no tiene el cotejo: la app coteja con lo que ya bajó
            if (x.estadoHttp == 404 || x.estadoHttp == 405) return null
            throw x
        }
        val resultado = ResultadoCotejo.deCodigo(r.resultado) ?: return null
        return Cotejo(resultado, r.compra?.id, r.avisos)
    }

    override suspend fun obrasDestino(obraId: Int): List<ObraAsignada> {
        return api.obras(cred.exigir()).filter { it.id != obraId && it.estado != "cerrada" }.map { ObraAsignada(it.id, it.nombre, it.rol.orEmpty()) }
    }
}
