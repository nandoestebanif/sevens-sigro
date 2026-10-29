package pe.sevens.sigro.sincronizacion

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioSalidas
import java.util.concurrent.TimeUnit

/**
 * HU-38 en Android: WorkManager ejecuta el envío SOLO cuando hay red y sobrevive a que
 * el sistema cierre la app o el celular se reinicie (por eso Android nativo, ADR-02).
 */
class SincronizacionWorker(contexto: Context, params: WorkerParameters) :
    CoroutineWorker(contexto, params), KoinComponent {

    private val salidas: RepositorioSalidas by inject()

    override suspend fun doWork(): Result {
        val r = salidas.sincronizar()
        // Quedan pendientes: WorkManager reintenta con espera creciente, sin gastar batería.
        return if (r.siguenPendientes > 0) Result.retry() else Result.success()
    }
}

class ProgramadorAndroid(private val contexto: Context) : ProgramadorSincronizacion {
    private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun programar() {
        val wm = WorkManager.getInstance(contexto)
        // 1) envío inmediato en cuanto haya señal (REPLACE: el último registro dispara el envío)
        wm.enqueueUniqueWork(
            "sigro-enviar-ahora",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SincronizacionWorker>()
                .setConstraints(conRed)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
        // 2) red de seguridad: cada 15 min (mínimo de Android) por si algo quedó en cola
        wm.enqueueUniquePeriodicWork(
            "sigro-enviar-periodico",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SincronizacionWorker>(15, TimeUnit.MINUTES).setConstraints(conRed).build(),
        )
    }
}
