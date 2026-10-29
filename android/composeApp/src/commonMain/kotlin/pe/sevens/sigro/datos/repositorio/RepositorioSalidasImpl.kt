package pe.sevens.sigro.datos.repositorio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.sevens.sigro.datos.local.AjusteDao
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
    ajustes: AjusteDao,
    private val reloj: () -> Long = Plataforma::ahoraMs,
) : RepositorioSalidas {
    private val cred = Credenciales(ajustes)
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
        val token = cred.token()
        if (servidor == null || token == null) {
            return@withLock ResultadoSincronizacion(0, 0, cola.size) // se envía al volver a entrar
        }
        val dispositivo = cred.identificadorDispositivo()
        var enviados = 0
        var rechazados = 0
        for (s in cola) {
            try {
                api.enviarSalida(servidor, token, s.obraId, s.aEnvio(dispositivo))
                salidas.marcar(s.uuidCliente, EstadoEnvio.ENVIADO.name, null, null, reloj())
                enviados++
            } catch (e: ErrorSigro) {
                when (val decision = decidir(e)) {
                    is Decision.Reintentar -> {
                        salidas.marcar(s.uuidCliente, EstadoEnvio.PENDIENTE.name, null, decision.nota, reloj())
                        // Si falla por señal, sesión o servidor, fallarán todas: se corta y se reintenta después.
                        if (decision.cortar) break
                    }
                    Decision.YaRecibido -> {
                        salidas.marcar(s.uuidCliente, EstadoEnvio.ENVIADO.name, null, null, reloj()); enviados++
                    }
                    is Decision.Rechazar -> {
                        salidas.marcar(s.uuidCliente, EstadoEnvio.RECHAZADO.name, decision.motivo, null, reloj()); rechazados++
                    }
                }
            }
        }
        ResultadoSincronizacion(enviados, rechazados, salidas.pendientes().size)
    }

    private sealed interface Decision {
        data class Reintentar(val nota: String, val cortar: Boolean) : Decision
        data object YaRecibido : Decision
        data class Rechazar(val motivo: String) : Decision
    }

    private fun decidir(e: ErrorSigro): Decision = when {
        e.esTransitorio -> Decision.Reintentar("Sin señal o servidor ocupado: se reintenta solo.", cortar = true)
        e.estadoHttp == 401 -> Decision.Reintentar("Tu sesión venció: vuelve a ingresar y se envía.", cortar = true)
        e.estadoHttp == 404 -> Decision.Reintentar("El servidor aún no recibe salidas: queda guardada en el celular.", cortar = true)
        e.estadoHttp == 409 && e.codigo.contains("uuid") -> Decision.YaRecibido // el servidor ya la tenía (regla 3)
        else -> Decision.Rechazar(e.message) // regla de negocio: stock insuficiente, dispositivo no autorizado…
    }

    private fun SalidaLocalEntity.aEnvio(dispositivo: String) = SalidaIn(
        uuidCliente = uuidCliente, insumoId = insumoId, cantidad = cantidad, motivo = motivo,
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
    )
}
