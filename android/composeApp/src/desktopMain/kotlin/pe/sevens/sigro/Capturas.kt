package pe.sevens.sigro

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.skia.EncodedImageFormat
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoDispositivo
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.NuevaSalida
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioDispositivo
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.ResultadoSincronizacion
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.ui.pantallas.DispositivoViewModel
import pe.sevens.sigro.ui.pantallas.EntrarViewModel
import pe.sevens.sigro.ui.pantallas.InicioViewModel
import pe.sevens.sigro.ui.pantallas.PantallaDispositivo
import pe.sevens.sigro.ui.pantallas.PantallaEntrar
import pe.sevens.sigro.ui.pantallas.PantallaInicio
import pe.sevens.sigro.ui.pantallas.PantallaObras
import pe.sevens.sigro.ui.pantallas.PantallaPendientes
import pe.sevens.sigro.ui.pantallas.PantallaSalida
import pe.sevens.sigro.ui.pantallas.PendientesViewModel
import pe.sevens.sigro.ui.pantallas.SalidaViewModel
import pe.sevens.sigro.ui.tema.TemaSigro
import java.io.File

/*
 * Capturas del diseño con datos de ejemplo, sin servidor ni emulador.
 * Los insumos salen del catálogo real del cliente (api/semillas/catalogo-cliente.json);
 * las personas son ejemplos y lo dicen.
 *   ./gradlew :composeApp:capturas
 */

@Serializable private data class FilaCatalogo(val codigo: String, val nombre: String, val um: String)

private fun catalogoReal(): List<Insumo> {
    val f = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "api/semillas/catalogo-cliente.json") }.firstOrNull { it.exists() }
        ?: return emptyList()
    return Json { ignoreUnknownKeys = true }.decodeFromString<List<FilaCatalogo>>(f.readText())
        .mapIndexed { i, c -> Insumo(i + 1, c.codigo, c.nombre, c.um.lowercase()) }
}

private val catalogo = catalogoReal()
private fun insumo(nombre: String) = catalogo.firstOrNull { it.descripcion.startsWith(nombre) }
    ?: Insumo(0, "—", nombre, "und")

private val obra = ObraAsignada(1, "Multifamiliar Flores", "almacenero")
private val sesionEjemplo = Sesion(
    "https://sigro-gateway.onrender.com", "x", 1, "Almacenero Ejemplo", "almacenero",
    listOf(obra, ObraAsignada(2, "Obra de ejemplo 2", "ssoma")),
)

/** `colgada`: el servidor no responde nunca (para ver «el plano se dibuja» con el logo). */
private class SesionFalsa(private val falla: Boolean = false, private val colgada: Boolean = false) : RepositorioSesion {
    override val sesion: Flow<Sesion?> = flowOf(sesionEjemplo)
    override val obraElegida: Flow<ObraAsignada?> = flowOf(obra)
    override suspend fun servidorGuardado() = "https://sigro-gateway.onrender.com"
    override suspend fun iniciarSesion(servidor: String, usuario: String, clave: String): Sesion =
        if (colgada) kotlinx.coroutines.awaitCancellation()
        else if (falla) throw ErrorSigro("credenciales", "Usuario o clave incorrectos.", 401) else sesionEjemplo
    override suspend fun elegirObra(obra: ObraAsignada) {}
    override suspend fun cambiarDeObra() {}
    override suspend fun cerrarSesion() {}
}

private object CatalogoFalso : RepositorioCatalogo {
    override suspend fun buscar(texto: String) = catalogo.filter { it.descripcion.contains(texto, true) }.take(8)
    override suspend fun actualizar() = catalogo.size
    override suspend fun cuantosHay() = catalogo.size
}

private class SalidasFalsas : RepositorioSalidas {
    private val ahora = System.currentTimeMillis()
    private fun s(n: Int, ins: Insumo, cant: String, motivo: String, estado: EstadoEnvio, rechazo: String? = null, nota: String? = null) =
        SalidaRegistrada("uuid-$n", 1, ins, cant, motivo, "Maestro de obra (ejemplo)", if (n % 2 == 0) "Cuadrilla de encofrado" else null,
            ahora - n * 1_800_000L, estado, rechazo, nota, if (estado == EstadoEnvio.ENVIADO) 1 else n)
    val lista = MutableStateFlow(
        listOf(
            s(1, insumo("CEMENTO PORTLAND TIPO I"), "25", "Vaciado de columnas 2.º piso", EstadoEnvio.PENDIENTE, nota = "Sin señal o servidor ocupado: se reintenta solo."),
            s(2, insumo("ARENA GRUESA"), "3.5", "Tarrajeo de muros eje B", EstadoEnvio.PENDIENTE),
            s(3, insumo("ALAMBRE NEGRO"), "40", "Amarre de acero de losa", EstadoEnvio.RECHAZADO, rechazo = "No hay stock suficiente en el almacén para esta salida."),
            s(4, insumo("CLAVOS"), "5", "Encofrado de vigas", EstadoEnvio.ENVIADO),
            s(5, insumo("PIEDRA CHANCADA"), "6", "Vaciado de zapatas", EstadoEnvio.ENVIADO),
        )
    )
    override fun salidasDeObra(obraId: Int) = lista
    override val pendientes: Flow<Int> = lista.map { l -> l.count { it.estado == EstadoEnvio.PENDIENTE } }
    override suspend fun registrar(nueva: NuevaSalida) = lista.value.first()
    override suspend fun sincronizar() = ResultadoSincronizacion(0, 0, 2)
}

private object DispositivoFalso : RepositorioDispositivo {
    override suspend fun identificador() = "sigro-android-3f2a9c1e-7b4d-4e8a-9c0f-1d2e3f4a5b6c"
    override suspend fun estado(obraId: Int) = EstadoDispositivo(identificador(), "pendiente")
    override suspend fun solicitarAutorizacion(obraId: Int) = estado(obraId)
}

private val sinProgramar = object : ProgramadorSincronizacion { override fun programar() {} }

private fun capturar(dir: File, nombre: String, oscuro: Boolean = false, ms: Int = 1500, preparar: () -> Unit = {}, contenido: @Composable () -> Unit) {
    preparar()
    Thread.sleep(400) // los ViewModel actualizan su estado en el hilo principal de Swing
    ImageComposeScene(width = 412 * 2, height = 892 * 2, density = Density(2f)) {
        TemaSigro(oscuro = oscuro) { contenido() }
    }.use { escena ->
        var t = 0L
        repeat(ms / 50) { escena.render(t); t += 50_000_000L; Thread.sleep(15) } // deja correr efectos y animaciones
        val png = escena.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(dir, "$nombre.png").writeBytes(png)
        println("✓ $nombre.png")
    }
}

fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "build/capturas").apply { mkdirs() }
    println("Catálogo real cargado: ${catalogo.size} insumos")

    val entrar = EntrarViewModel(SesionFalsa())
    capturar(dir, "01-entrar", preparar = { entrar.usuario("almacenero"); entrar.clave("********") }) { PantallaEntrar(entrar) }

    val entrarMal = EntrarViewModel(SesionFalsa(falla = true))
    val entrando = EntrarViewModel(SesionFalsa(colgada = true))
    capturar(dir, "01b-entrar-plano-a-medias", ms = 1100, preparar = { entrando.usuario("almacenero"); entrando.clave("********"); entrando.entrar() }) { PantallaEntrar(entrando) }
    val esperando = EntrarViewModel(SesionFalsa(colgada = true))
    capturar(dir, "01c-entrar-preparando-obra", ms = 3200, preparar = { esperando.usuario("almacenero"); esperando.clave("********"); esperando.entrar() }) { PantallaEntrar(esperando) }

    capturar(dir, "02-entrar-error", preparar = { entrarMal.usuario("almacenero"); entrarMal.clave("x"); entrarMal.entrar() }) { PantallaEntrar(entrarMal) }

    capturar(dir, "03-elegir-obra") { PantallaObras(sesionEjemplo, {}, {}) }

    val salidas = SalidasFalsas()
    capturar(dir, "04-inicio") { PantallaInicio(sesionEjemplo, obra, {}, InicioViewModel(SesionFalsa(), salidas, CatalogoFalso, sinProgramar)) }
    capturar(dir, "04-inicio-oscuro", oscuro = true) { PantallaInicio(sesionEjemplo, obra, {}, InicioViewModel(SesionFalsa(), salidas, CatalogoFalso, sinProgramar)) }

    val buscar = SalidaViewModel(CatalogoFalso, salidas, sinProgramar)
    capturar(dir, "05-salida-buscar", preparar = { buscar.buscar("cemento") }) { PantallaSalida(1, {}, buscar) }

    val llena = SalidaViewModel(CatalogoFalso, salidas, sinProgramar)
    capturar(dir, "06-salida-llena", preparar = {
        llena.elegir(insumo("CEMENTO PORTLAND TIPO I")); llena.cantidad("25"); llena.motivo("Vaciado de columnas 2.º piso")
        llena.quienRetira("Maestro de obra (ejemplo)"); llena.frente("Cuadrilla de concreto")
    }) { PantallaSalida(1, {}, llena) }

    val conErrores = SalidaViewModel(CatalogoFalso, salidas, sinProgramar)
    capturar(dir, "07-salida-validacion", preparar = { conErrores.cantidad("0"); conErrores.guardar(1) }) { PantallaSalida(1, {}, conErrores) }

    val guardada = SalidaViewModel(CatalogoFalso, salidas, sinProgramar)
    capturar(dir, "08-salida-guardada", preparar = {
        guardada.elegir(insumo("ARENA GRUESA")); guardada.cantidad("3,5"); guardada.motivo("Tarrajeo"); guardada.quienRetira("Maestro de obra (ejemplo)"); guardada.guardar(1)
    }) { PantallaSalida(1, {}, guardada) }

    capturar(dir, "09-mis-registros") { PantallaPendientes(1, {}, PendientesViewModel(salidas)) }
    capturar(dir, "09-mis-registros-oscuro", oscuro = true) { PantallaPendientes(1, {}, PendientesViewModel(salidas)) }

    capturar(dir, "10-mi-celular") { PantallaDispositivo(1, {}, DispositivoViewModel(DispositivoFalso)) }
    kotlin.system.exitProcess(0)
}
