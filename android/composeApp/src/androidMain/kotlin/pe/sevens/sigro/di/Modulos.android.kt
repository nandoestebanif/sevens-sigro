package pe.sevens.sigro.di

import androidx.room.Room
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.sevens.sigro.datos.local.BaseDatosSigro
import pe.sevens.sigro.datos.local.construirBaseDatos
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.sincronizacion.ProgramadorAndroid

actual val moduloPlataforma: Module = module {
    single { crearClienteHttp(OkHttp.create()) }
    single<BaseDatosSigro> {
        val ctx = androidContext()
        construirBaseDatos(
            Room.databaseBuilder<BaseDatosSigro>(ctx, ctx.getDatabasePath("sigro.db").absolutePath)
        )
    }
    single<ProgramadorSincronizacion> { ProgramadorAndroid(androidContext()) }
}
