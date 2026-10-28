package pe.sevens.sigro.dominio

import kotlinx.coroutines.flow.Flow

/*
 * Contratos que la UI usa. La implementación (Ktor + Room) vive en `datos/`;
 * en las pruebas se reemplaza por dobles sin tocar las pantallas.
 */

interface RepositorioSesion {
    /** null mientras no haya sesión. Sobrevive al cierre de la app (Room). */
    val sesion: Flow<Sesion?>
    val obraElegida: Flow<ObraAsignada?>
    suspend fun servidorGuardado(): String
    suspend fun iniciarSesion(servidor: String, usuario: String, clave: String): Sesion
    suspend fun elegirObra(obra: ObraAsignada)
    suspend fun cambiarDeObra()
    suspend fun cerrarSesion()
}

interface RepositorioCatalogo {
    /** Busca en la copia local: funciona sin señal. */
    suspend fun buscar(texto: String): List<Insumo>
    /** Baja el catálogo del servidor. Devuelve cuántos insumos quedaron en el celular. */
    suspend fun actualizar(): Int
    suspend fun cuantosHay(): Int
}

data class ResultadoSincronizacion(val enviados: Int, val rechazados: Int, val siguenPendientes: Int)

interface RepositorioSalidas {
    fun salidasDeObra(obraId: Int): Flow<List<SalidaRegistrada>>
    val pendientes: Flow<Int>
    /** HU-37: guarda en el celular con uuid y hora del dispositivo. No necesita señal. */
    suspend fun registrar(nueva: NuevaSalida): SalidaRegistrada
    /** HU-38: envía la cola. Seguro de repetir: el uuid hace idempotente cada envío. */
    suspend fun sincronizar(): ResultadoSincronizacion
}

interface RepositorioDispositivo {
    /** HU-15: el código se genera la primera vez y no cambia mientras la app esté instalada. */
    suspend fun identificador(): String
    suspend fun estado(obraId: Int): EstadoDispositivo
    suspend fun solicitarAutorizacion(obraId: Int): EstadoDispositivo
}

/** Pide a la plataforma que sincronice cuando haya señal (WorkManager en Android, BGTask en iOS). */
interface ProgramadorSincronizacion {
    fun programar()
}
