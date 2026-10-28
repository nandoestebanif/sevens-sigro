package pe.sevens.sigro.sincronizacion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioSalidas

/**
 * HU-38 en iOS. Hoy: intenta enviar al registrar, al abrir la app y con «Enviar ahora».
 * Pendiente (Sprint 2): registrar una tarea BGAppRefreshTask en iOSApp.swift que llame a
 * `RepositorioSalidas.sincronizar()` cuando el sistema lo permita. iOS no garantiza el
 * momento del envío en segundo plano como WorkManager: se documenta en el ADR-07.
 */
class ProgramadorIos(private val salidas: RepositorioSalidas) : ProgramadorSincronizacion {
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun programar() {
        ambito.launch { runCatching { salidas.sincronizar() } }
    }
}
