package pe.sevens.sigro.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pe.sevens.sigro.datos.local.BaseDatosSigro
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.datos.repositorio.RepositorioCatalogoImpl
import pe.sevens.sigro.datos.repositorio.RepositorioDispositivoImpl
import pe.sevens.sigro.datos.repositorio.RepositorioSalidasImpl
import pe.sevens.sigro.datos.repositorio.RepositorioSesionImpl
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioDispositivo
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.ui.pantallas.DispositivoViewModel
import pe.sevens.sigro.ui.pantallas.EntrarViewModel
import pe.sevens.sigro.ui.pantallas.InicioViewModel
import pe.sevens.sigro.ui.pantallas.PendientesViewModel
import pe.sevens.sigro.ui.pantallas.RaizViewModel
import pe.sevens.sigro.ui.pantallas.SalidaViewModel

/*
 * Inyección de dependencias con Koin. Tres capas:
 *  - plataforma (expect): motor HTTP, archivo de la base y programador de sincronización;
 *  - datos: API, DAOs y repositorios;
 *  - presentación: un ViewModel por pantalla.
 * La UI pide interfaces del dominio, nunca clases de Ktor o de Room.
 */

/** Lo que cada plataforma provee: HttpClient, BaseDatosSigro y ProgramadorSincronizacion. */
expect val moduloPlataforma: Module

val moduloDatos = module {
    single { jsonSigro }
    single { ApiSigro(get(), get()) }
    single { get<BaseDatosSigro>().ajustes() }
    single { get<BaseDatosSigro>().salidas() }
    single { get<BaseDatosSigro>().insumos() }
    single<RepositorioSesion> { RepositorioSesionImpl(get(), get()) }
    single<RepositorioCatalogo> { RepositorioCatalogoImpl(get(), get(), get()) }
    single<RepositorioSalidas> { RepositorioSalidasImpl(get(), get(), get()) }
    single<RepositorioDispositivo> { RepositorioDispositivoImpl(get(), get()) }
}

val moduloPresentacion = module {
    viewModel { RaizViewModel(get()) }
    viewModel { EntrarViewModel(get()) }
    viewModel { InicioViewModel(get(), get(), get(), get()) }
    viewModel { SalidaViewModel(get(), get(), get()) }
    viewModel { PendientesViewModel(get()) }
    viewModel { DispositivoViewModel(get()) }
}

fun modulosSigro(): List<Module> = listOf(moduloPlataforma, moduloDatos, moduloPresentacion)
