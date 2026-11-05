package pe.sevens.sigro.datos.repositorio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.sevens.sigro.datos.local.SalidaDao
import pe.sevens.sigro.datos.local.SalidaLocalEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.SalidaIn
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.NuevaSalida
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.ResultadoSincronizacion
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.ValidarSalida
import pe.sevens.sigro.plataforma.Plataforma
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * HU-37 + HU-38. Todo se guarda primero en el celular y después se envía.
 * Las reglas que este archivo protege:
 *  - regla 3: cada salida nace con su uuid_cliente; reenviar es seguro;
 *  - regla 2: aquí no se calcula saldo;
 *  - HU-38: un rechazo queda con su motivo; nada se borra de la cola.
 */
class RepositorioSalidasImpl(
    private val api: ApiSigro,
    private val salidas: SalidaDao,
    private val cred: Credenciales,
    private val reloj: () -> Long = Plataforma::ahoraMs,
) : RepositorioSalidas {
    private val unaALaVez = Mutex() // el botón «Enviar ahora» y el trabajo de fondo no envían en paralelo

    override fun salidasDeObra(obraId: Int): Flow<List<SalidaRegistrada>> =
        salidas.observarDeObra(obraId).map { it.map(::aDominio) }

    override val pendientes: Flow<Int> = salidas.contarPendientes()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun registrar(nueva: NuevaSalida): SalidaRegistrada {
        val errores = ValidarSalida.errores(nueva.insumo, nueva.cantidad, nueva.motivo, nueva.quienRetira)
        require(errores.isEmpty()) { errores.values.joinToString(" · ") }
        val fila = SalidaLocalEntity(
            uuidCliente = Uuid.random().toString(),
            obraId = nueva.obraId,
            almacenId = nueva.almacenId,
            insumoId = nueva.insumo.id,
            insumoCodigo = nueva.insumo.codigo,
            insumoDescripcion = nueva.insumo.descripcion,
            unidad = nueva.insumo.unidad,
            cantidad = ValidarSalida.normalizarCantidad(nueva.cantidad),
            motivo = nueva.motivo.trim(),
            quienRetira = nueva.quienRetira.trim(),
            frente = nueva.frente?.trim()?.ifEmpty { null },
            horaDispositivoMs = reloj(),
            estado = EstadoEnvio.PENDIENTE.name,
            motivoRechazo = null,
            nota = null,
            intentos = 0,
            ultimoIntentoMs = null,
        )
        salidas.insertar(fila)
        return aDominio(fila)
    }

    override suspend fun sincronizar(): ResultadoSincronizacion = unaALaVez.withLock {
        val cola = salidas.pendientes()
        if (cola.isEmpty()) return@withLock ResultadoSincronizacion(0, 0, 0)
        val servidor = cred.servidor()
        if (servidor == null || !cred.haySesion()) {
            return@withLock ResultadoSincronizacion(0, 0, cola.size) // se envía al volver a entrar
        }
        val dispositivo = cred.identificadorDispositivo()
        var enviados = 0
        var rechazados = 0
        for (s in cola) {
            try {
                val respuesta = api.enviarSalida(servidor, s.obraId, s.aEnvio(dispositivo))
                salidas.marcar(s.uuidCliente, EstadoEnvio.ENVIADO.name, null, null, reloj())
                // A1: quién registró y la hora del servidor, tal como vinieron en el 201/200.
                cred.recibo(respuesta)?.let { salidas.anotarRecibo(s.uuidCliente, it.registradoPorId, it.registradoPor, it.fechaServidor) }
                enviados++
            } catch (e: ErrorSigro) {
                when (val decision = decidirEnvio(e, "salidas")) {
                    is DecisionEnvio.Reintentar -> {
                        salidas.marcar(s.uuidCliente, EstadoEnvio.PENDIENTE.name, null, decision.nota, reloj())
                        // Si falla por señal, sesión o servidor, fallarán todas: se corta y se reintenta después.
                        if (decision.cortar) break
                    }
                    DecisionEnvio.YaRecibido -> {
                        salidas.marcar(s.uuidCliente, EstadoEnvio.ENVIADO.name, null, null, reloj()); enviados++
                    }
                    is DecisionEnvio.Rechazar -> {
                        salidas.marcar(s.uuidCliente, EstadoEnvio.RECHAZADO.name, decision.motivo, null, reloj()); rechazados++
                    }
                }
            }
        }
        ResultadoSincronizacion(enviados, rechazados, salidas.pendientes().size)
    }

    private fun SalidaLocalEntity.aEnvio(dispositivo: String) = SalidaIn(
        uuidCliente = uuidCliente, almacenId = almacenId, insumoId = insumoId, cantidad = cantidad, unidad = unidad, motivo = motivo,
        quienRetira = quienRetira, frente = frente, horaDispositivoMs = horaDispositivoMs, dispositivo = dispositivo,
    )

    private fun aDominio(e: SalidaLocalEntity) = SalidaRegistrada(
        uuidCliente = e.uuidCliente,
        obraId = e.obraId,
        insumo = Insumo(e.insumoId, e.insumoCodigo, e.insumoDescripcion, e.unidad),
        cantidad = e.cantidad,
        motivo = e.motivo,
        quienRetira = e.quienRetira,
        frente = e.frente,
        horaDispositivoMs = e.horaDispositivoMs,
        estado = EstadoEnvio.valueOf(e.estado),
        motivoRechazo = e.motivoRechazo,
        nota = e.nota,
        intentos = e.intentos,
        registradoPor = e.registradoPor,
        fechaServidor = e.fechaServidor,
    )
}
