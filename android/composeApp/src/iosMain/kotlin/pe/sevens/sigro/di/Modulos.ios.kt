package pe.sevens.sigro.di

import androidx.room.Room
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import pe.sevens.sigro.datos.local.BaseDatosSigro
import pe.sevens.sigro.datos.local.construirBaseDatos
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.datos.repositorio.BovedaSesion
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.plataforma.AlmacenSeguro
import pe.sevens.sigro.plataforma.AlmacenSeguroIos
import pe.sevens.sigro.sincronizacion.ProgramadorIos

actual val moduloPlataforma: Module = module {
    single { val boveda = get<BovedaSesion>(); crearClienteHttp(Darwin.create()) { boveda.token() } }
    single<AlmacenSeguro> { AlmacenSeguroIos() }
    single<BaseDatosSigro> { construirBaseDatos(Room.databaseBuilder<BaseDatosSigro>(name = rutaBase())) }
    single<ProgramadorSincronizacion> { ProgramadorIos(get(), get()) }
}

@OptIn(ExperimentalForeignApi::class)
private fun rutaBase(): String {
    val docs = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(docs?.path) + "/sigro.db"
}

/** Se llama una vez desde Swift (iOSApp.swift) antes de mostrar la interfaz. */
fun iniciarKoin() {
    startKoin { modules(modulosSigro()) }
}
