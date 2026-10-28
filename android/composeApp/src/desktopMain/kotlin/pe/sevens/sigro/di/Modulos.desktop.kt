package pe.sevens.sigro.di

import androidx.room.Room
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.sevens.sigro.datos.local.BaseDatosSigro
import pe.sevens.sigro.datos.local.construirBaseDatos
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioSalidas
import java.io.File

/** Versión de escritorio: solo para probar la interfaz en la PC. Base en ~/.sigro/sigro.db */
actual val moduloPlataforma: Module = module {
    single { crearClienteHttp(OkHttp.create()) }
    single<BaseDatosSigro> {
        val dir = File(System.getProperty("user.home"), ".sigro").apply { mkdirs() }
        construirBaseDatos(Room.databaseBuilder<BaseDatosSigro>(name = File(dir, "sigro.db").absolutePath))
    }
    single<ProgramadorSincronizacion> {
        val salidas: RepositorioSalidas = get()
        object : ProgramadorSincronizacion {
            private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            override fun programar() { ambito.launch { runCatching { salidas.sincronizar() } } }
        }
    }
}
