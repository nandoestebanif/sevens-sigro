package pe.sevens.sigro.dominio.casosdeuso

import pe.sevens.sigro.dominio.NuevaSalida
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.ResultadoSincronizacion
import pe.sevens.sigro.dominio.SalidaRegistrada

/*
 * Casos de uso: una acción del almacenero = una clase con un solo método (`invoke`).
 * Dependen SOLO de interfaces del dominio (repositorios y programador), nunca de Ktor, Room ni Android:
 * así se prueban con dobles y el ViewModel no repite la secuencia de pasos.
 */

/**
 * HU-40 · Registrar una salida sin depender de la señal.
 * 1) se guarda en el celular como PENDIENTE con su uuid_cliente (offline-first: nunca espera a la red);
 * 2) se pide la sincronización: si hay señal se envía ya, si no, cuando vuelva.
 */
class RegistrarSalida(
    private val salidas: RepositorioSalidas,
    private val programador: ProgramadorSincronizacion,
) {
    suspend operator fun invoke(nueva: NuevaSalida): SalidaRegistrada {
        val guardada = salidas.registrar(nueva)
        programador.programar()
        return guardada
    }
}

/**
 * HU-38 · Enviar todo lo pendiente: la cola de salidas y la cola general (entradas, ingresos, asistencia).
 * Solo SUBE: lo que se baja del servidor vive en otras tablas y no puede pisar lo creado sin señal.
 * Si una cola falla (sin señal), la otra igual se intenta. `manual` = «Enviar ahora»: incluye los que
 * agotaron sus intentos automáticos.
 */
class SincronizarCola(
    private val salidas: RepositorioSalidas,
    private val registros: RepositorioRegistros,
) {
    suspend operator fun invoke(manual: Boolean = false): ResultadoSincronizacion {
        val a = intentar { salidas.sincronizar(manual) }
        val b = intentar { registros.sincronizar(manual) }
        return ResultadoSincronizacion(a.enviados + b.enviados, a.rechazados + b.rechazados, a.siguenPendientes + b.siguenPendientes)
    }

    private inline fun intentar(cola: () -> ResultadoSincronizacion): ResultadoSincronizacion =
        try { cola() } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e // no se traga la cancelación
            ResultadoSincronizacion(0, 0, 0)
        }
}
