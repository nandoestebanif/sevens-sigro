package pe.sevens.sigro.datos.repositorio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import pe.sevens.sigro.datos.local.AjusteDao
import pe.sevens.sigro.datos.local.RegistroDao
import pe.sevens.sigro.datos.local.RegistroLocalEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.JornadaIn
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoAsistencia
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.NOTA_SIN_SERVICIO_ALMACEN
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RegistroLocal
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.ResultadoSincronizacion
import pe.sevens.sigro.dominio.TipoRegistro
import pe.sevens.sigro.dominio.ValidarSalida
import pe.sevens.sigro.plataforma.Plataforma
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Cola general sin señal: la misma mecánica que RepositorioSalidasImpl (HU-37 / HU-38) para la
 * asistencia y los registros de almacén.
 *  - regla 3: cada registro nace con su uuid_cliente y su cuerpo queda fijo; reenviar es seguro;
 *  - HU-38: un rechazo queda con su motivo; nada se borra de la cola;
 *  - los tipos sin servicio (almacén) se guardan y NO se envían: no cuentan como pendientes para el
 *    trabajo de fondo, así WorkManager no reintenta en vano.
 */
class RepositorioRegistrosImpl(
    private val api: ApiSigro,
    private val registros: RegistroDao,
    ajustes: AjusteDao,
    private val reloj: () -> Long = Plataforma::ahoraMs,
) : RepositorioRegistros {
    private val cred = Credenciales(ajustes)
    private val unaALaVez = Mutex()
    private val tiposConServicio = TipoRegistro.entries.filter { it.conServicio }.map { it.name }

    override fun deObra(obraId: Int): Flow<List<RegistroLocal>> =
        registros.observarDeObra(obraId).map { l -> l.map(::aDominio) }

    override val pendientes: Flow<Int> = registros.contarPendientes(tiposConServicio)

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun registrarAsistencia(obraId: Int, fecha: String, marcas: List<MarcaAsistencia>): List<RegistroLocal> {
        val yaEstan = yaMarcados(obraId, fecha)
        val nuevas = marcas.filter { it.trabajador.id !in yaEstan }
        val hora = reloj()
        return nuevas.mapIndexed { i, m ->
            val uuid = Uuid.random().toString()
            val cuerpo = JornadaIn(
                trabajadorId = m.trabajador.id, fecha = fecha, estado = m.estado.api,
                horasNormales = m.normalesEnvio, horasExtra = m.extraEnvio, uuidCliente = uuid,
            )
            val detalle = when (m.estado) {
                EstadoAsistencia.PRESENTE ->
                    "Presente · ${m.horasNormales} h" + (if (m.horasExtra > 0) " + ${m.horasExtra} extra" else "") + " · $fecha"
                else -> "${m.estado.legible} · $fecha"
            }
            val fila = nueva(
                uuid, obraId, TipoRegistro.JORNADA, m.trabajador.nombre, detalle,
                jsonSigro.encodeToString(JornadaIn.serializer(), cuerpo), claveJornada(m.trabajador.id, fecha), hora + i, null,
            )
            registros.insertar(fila)
            aDominio(fila)
        }
    }

    override suspend fun yaMarcados(obraId: Int, fecha: String): Set<Int> =
        registros.clavesVigentes(obraId, "jornada:")
            .filter { it.endsWith(":$fecha") }
            .mapNotNull { it.split(':').getOrNull(1)?.toIntOrNull() }
            .toSet()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun registrarAlmacen(obraId: Int, registro: RegistroAlmacen): RegistroLocal {
        val uuid = Uuid.random().toString()
        val hora = reloj()
        val (titulo, detalle) = resumen(registro)
        // Regla 5: el celular viaja en el cuerpo; el servidor rechaza (403) si no está autorizado en la obra.
        val dispositivo = cred.identificadorDispositivo()
        val fila = nueva(
            uuid, obraId, registro.tipo, titulo, detalle, cuerpoAlmacen(uuid, hora, dispositivo, registro).toString(),
            null, hora, if (registro.tipo.conServicio) null else NOTA_SIN_SERVICIO_ALMACEN,
        )
        registros.insertar(fila)
        return aDominio(fila)
    }

    override suspend fun sincronizar(): ResultadoSincronizacion = unaALaVez.withLock {
        val cola = registros.pendientes(tiposConServicio)
        if (cola.isEmpty()) return@withLock ResultadoSincronizacion(0, 0, 0)
        val servidor = cred.servidor()
        val token = cred.token()
        if (servidor == null || token == null) return@withLock ResultadoSincronizacion(0, 0, cola.size)
        var enviados = 0
        var rechazados = 0
        for (r in cola) {
            val tipo = TipoRegistro.valueOf(r.tipo)
            try {
                api.enviarRegistro(servidor, token, ruta(tipo, r.obraId), r.cuerpo)
                registros.marcar(r.uuidCliente, EstadoEnvio.ENVIADO.name, null, null, reloj())
                enviados++
            } catch (e: ErrorSigro) {
                when (val d = decidirEnvio(e, tipo.legible.lowercase())) {
                    is DecisionEnvio.Reintentar -> {
                        registros.marcar(r.uuidCliente, EstadoEnvio.PENDIENTE.name, null, d.nota, reloj())
                        // Si falla por señal, sesión o servidor, fallarán todas: se corta y se reintenta después.
                        if (d.cortar) break
                    }
                    DecisionEnvio.YaRecibido -> {
                        registros.marcar(r.uuidCliente, EstadoEnvio.ENVIADO.name, null, null, reloj()); enviados++
                    }
                    is DecisionEnvio.Rechazar -> {
                        registros.marcar(r.uuidCliente, EstadoEnvio.RECHAZADO.name, d.motivo, null, reloj()); rechazados++
                    }
                }
            }
        }
        ResultadoSincronizacion(enviados, rechazados, registros.pendientes(tiposConServicio).size)
    }

    private fun nueva(
        uuid: String, obraId: Int, tipo: TipoRegistro, titulo: String, detalle: String, cuerpo: String,
        clave: String?, hora: Long, nota: String?,
    ) = RegistroLocalEntity(
        uuidCliente = uuid, obraId = obraId, tipo = tipo.name, titulo = titulo, detalle = detalle, cuerpo = cuerpo,
        clave = clave, horaDispositivoMs = hora, estado = EstadoEnvio.PENDIENTE.name, motivoRechazo = null,
        nota = nota, intentos = 0, ultimoIntentoMs = null,
    )

    private fun aDominio(e: RegistroLocalEntity) = RegistroLocal(
        uuidCliente = e.uuidCliente, obraId = e.obraId, tipo = TipoRegistro.valueOf(e.tipo), titulo = e.titulo,
        detalle = e.detalle, horaDispositivoMs = e.horaDispositivoMs, estado = EstadoEnvio.valueOf(e.estado),
        motivoRechazo = e.motivoRechazo, nota = e.nota, intentos = e.intentos,
    )

    internal companion object {
        fun claveJornada(trabajadorId: Int, fecha: String) = "jornada:$trabajadorId:$fecha"

        /** Rutas del contrato (api/docs/propuestas/LEEME.md). Solo JORNADA existe hoy en el servidor. */
        fun ruta(tipo: TipoRegistro, obraId: Int): String = when (tipo) {
            TipoRegistro.JORNADA -> "/obras/$obraId/jornadas"
            TipoRegistro.ENTRADA_COMPRA -> "/obras/$obraId/almacen/recepciones"
            TipoRegistro.INGRESO_SIN_COMPRA -> "/obras/$obraId/almacen/ingresos"
            TipoRegistro.TRASLADO -> "/obras/$obraId/almacen/traslados"
            TipoRegistro.CONTEO -> "/obras/$obraId/almacen/conteos"
            TipoRegistro.CIERRE -> "/obras/$obraId/cierre"
        }

        private fun cant(c: String) = ValidarSalida.normalizarCantidad(c)

        fun resumen(r: RegistroAlmacen): Pair<String, String> = when (r) {
            is RegistroAlmacen.Entrada -> "Entrada · ${r.compra.proveedor}" to
                r.recibido.joinToString(" · ") { (l, c) -> "${cant(c)} ${l.unidad} ${l.descripcion}" }
            is RegistroAlmacen.IngresoSinCompra -> r.linea.insumo.descripcion to
                "${cant(r.linea.cantidad)} ${r.linea.insumo.unidad} · ${r.origen.legible} · S/ ${cant(r.costoUnitario)} c/u"
            is RegistroAlmacen.Traslado -> "Traslado a ${r.obraDestino}" to
                r.lineas.joinToString(" · ") { "${cant(it.cantidad)} ${it.insumo.unidad} ${it.insumo.descripcion}" }
            is RegistroAlmacen.Conteo -> "Conteo físico del ${r.fecha}" to
                "${r.lineas.size} ${if (r.lineas.size == 1) "insumo contado" else "insumos contados"}"
            is RegistroAlmacen.Cierre -> "Cierre de obra" to "Pedido de cierre del ${r.fecha}"
        }

        /** El cuerpo que viajará al servicio almacén, armado ya para que el reenvío sea idéntico. */
        fun cuerpoAlmacen(uuid: String, hora: Long, dispositivo: String, r: RegistroAlmacen): JsonObject = buildJsonObject {
            put("uuid_cliente", uuid)
            put("hora_dispositivo_ms", hora)
            put("dispositivo", dispositivo)
            when (r) {
                is RegistroAlmacen.Entrada -> {
                    put("almacen_id", r.almacenId)
                    put("compra_id", r.compra.id)
                    put("con_foto_guia", r.conFotoGuia)
                    putJsonArray("lineas") {
                        r.recibido.forEach { (l, c) -> addJsonObject { put("insumo_codigo", l.insumoCodigo); put("cantidad", cant(c)) } }
                    }
                }
                is RegistroAlmacen.IngresoSinCompra -> { // IngresoIn del servicio almacén
                    put("almacen_id", r.almacenId)
                    put("insumo_id", r.linea.insumo.id)
                    put("cantidad", cant(r.linea.cantidad))
                    put("unidad", r.linea.insumo.unidad) // unidad de compra: el servidor convierte con el factor
                    put("costo_unitario", cant(r.costoUnitario))
                    put("origen", r.origen.api)
                    put("origen_detalle", r.detalle)
                }
                is RegistroAlmacen.Traslado -> {
                    put("almacen_id", r.almacenId)
                    put("obra_destino_id", r.obraDestinoId)
                    putJsonArray("lineas") {
                        r.lineas.forEach { addJsonObject { put("insumo_id", it.insumo.id); put("cantidad", cant(it.cantidad)) } }
                    }
                }
                is RegistroAlmacen.Conteo -> {
                    put("almacen_id", r.almacenId)
                    put("fecha", r.fecha)
                    putJsonArray("lineas") {
                        r.lineas.forEach { (l, m) ->
                            addJsonObject { put("insumo_id", l.insumo.id); put("contado", cant(l.cantidad)); put("motivo", m?.legible) }
                        }
                    }
                }
                is RegistroAlmacen.Cierre -> {
                    put("almacen_id", r.almacenId)
                    put("fecha", r.fecha)
                    put("observacion", r.observacion)
                }
            }
        }
    }
}
