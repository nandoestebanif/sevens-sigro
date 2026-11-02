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
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RegistroLocal
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.TipoRegistro
import pe.sevens.sigro.dominio.NOTA_SIN_SERVICIO_ALMACEN
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.ResultadoSincronizacion
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.ui.pantallas.DispositivoViewModel
import pe.sevens.sigro.ui.pantallas.EntrarViewModel
import pe.sevens.sigro.ui.pantallas.InicioViewModel
import pe.sevens.sigro.ui.pantallas.ObrasViewModel
import pe.sevens.sigro.ui.pantallas.CatalogoViewModel
import pe.sevens.sigro.ui.pantallas.InsumoNuevoViewModel
import pe.sevens.sigro.ui.pantallas.PantallaCatalogo
import pe.sevens.sigro.ui.pantallas.PantallaFichaInsumo
import pe.sevens.sigro.ui.pantallas.PantallaInsumoNuevo
import pe.sevens.sigro.dominio.RepositorioInsumos
import pe.sevens.sigro.dominio.BusquedaInsumos
import pe.sevens.sigro.dominio.FichaInsumo
import pe.sevens.sigro.dominio.NuevoInsumo
import pe.sevens.sigro.dominio.CatalogoTexto
import pe.sevens.sigro.dominio.RepositorioTrabajadores
import pe.sevens.sigro.dominio.Trabajador
import pe.sevens.sigro.dominio.NuevoTrabajador
import pe.sevens.sigro.ui.pantallas.TrabajadoresViewModel
import pe.sevens.sigro.ui.pantallas.TrabajadorNuevoViewModel
import pe.sevens.sigro.ui.pantallas.AsistenciaViewModel
import pe.sevens.sigro.ui.pantallas.PantallaTrabajadores
import pe.sevens.sigro.ui.pantallas.PantallaTrabajadorNuevo
import pe.sevens.sigro.ui.pantallas.PantallaAsistencia
import pe.sevens.sigro.ui.pantallas.PantallaStock
import pe.sevens.sigro.ui.pantallas.StockViewModel
import pe.sevens.sigro.ui.pantallas.EntradaViewModel
import pe.sevens.sigro.ui.pantallas.PantallaEntrada
import pe.sevens.sigro.ui.pantallas.CierreViewModel
import pe.sevens.sigro.ui.pantallas.PantallaCierre
import pe.sevens.sigro.ui.pantallas.ConteoViewModel
import pe.sevens.sigro.ui.pantallas.PantallaConteo
import pe.sevens.sigro.ui.pantallas.TrasladoViewModel
import pe.sevens.sigro.ui.pantallas.PantallaTraslado
import pe.sevens.sigro.ui.pantallas.IngresoSinCompraViewModel
import pe.sevens.sigro.ui.pantallas.PantallaIngresoSinCompra
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.LineaCompra
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

/** Cola general de ejemplo: una asistencia por enviar y un ingreso que espera al servicio almacén. */
private class RegistrosFalsos : RepositorioRegistros {
    private val ahora = System.currentTimeMillis()
    val lista = MutableStateFlow(
        listOf(
            RegistroLocal("r-1", 1, TipoRegistro.JORNADA, "Trabajador 01 (ejemplo)", "Presente · 8 h + 2 extra", ahora - 600_000L, EstadoEnvio.PENDIENTE, null, null, 0),
            RegistroLocal("r-2", 1, TipoRegistro.INGRESO_SIN_COMPRA, insumo("ARENA GRUESA").descripcion, "1.5 m3 · Devolución de cuadrilla", ahora - 2_400_000L, EstadoEnvio.PENDIENTE, null, "Sin señal o servidor ocupado: se reintenta solo.", 1),
        )
    )
    override fun deObra(obraId: Int) = lista
    override val pendientes: Flow<Int> = lista.map { l -> l.count { it.estado == EstadoEnvio.PENDIENTE && it.tipo.conServicio } }
    override suspend fun registrarAsistencia(obraId: Int, fecha: String, marcas: List<MarcaAsistencia>) = emptyList<RegistroLocal>()
    override suspend fun yaMarcados(obraId: Int, fecha: String) = emptySet<Int>()
    override suspend fun registrarAlmacen(obraId: Int, registro: RegistroAlmacen) = lista.value.last()
    override suspend fun sincronizar() = ResultadoSincronizacion(0, 0, 1)
}

/** Catálogo real del cliente como si viniera del servidor. El factor del acero es un [marcador] del ejemplo. */
private object InsumosFalsos : RepositorioInsumos {
    private val todos = catalogo.map { FichaInsumo(it.id, it.codigo, it.descripcion, it.unidad, it.unidad, "1", "material", true, true) }
    override suspend fun buscar(texto: String) = BusquedaInsumos(todos.filter { CatalogoTexto.coincide(it, texto) }, desdeCelular = false)
    override suspend fun registrar(nuevo: NuevoInsumo) = FichaInsumo(9999, "9999", nuevo.descripcion, nuevo.unidad)
}

/** Trabajadores de ejemplo: las personas no son datos del cliente y lo dicen. */
private object TrabajadoresFalsos : RepositorioTrabajadores {
    private val cats = listOf("operario", "oficial", "oficial", "peon", "operario", "peon", "peon")
    val lista = MutableStateFlow((1..7).map { n ->
        Trabajador(n, 1, "Trabajador 0$n (ejemplo)", "DNI", "[documento]", cats[n - 1], if (n <= 3) "Cuadrilla A" else "Cuadrilla B",
            "2026-10-01", if (n == 7) "2026-10-20" else null)
    })
    override fun deObra(obraId: Int) = lista
    override suspend fun actualizar(obraId: Int) = lista.value.size
    override suspend fun registrar(obraId: Int, nuevo: NuevoTrabajador) = lista.value.first()
}

/** Lecturas de obra de ejemplo: el proveedor y la otra obra son [marcador]; los insumos, del catálogo real. */
private object ObraFalsa : RepositorioObra {
    override suspend fun almacenes(obraId: Int) = listOf(AlmacenObra(1, "Almacén principal"))
    override suspend fun compras(obraId: Int) = listOf(
        CompraParaRecibir(
            10, "[Proveedor]", "Factura [número]", "2026-10-27",
            listOf("CEMENTO PORTLAND TIPO I", "ARENA GRUESA", "CLAVOS").map { insumo(it) }.map { LineaCompra(it.codigo, it.descripcion, "10", it.unidad) },
        ),
    )
    override suspend fun stock(obraId: Int, almacenId: Int?) = pe.sevens.sigro.dominio.StockServidor(
        listOf("CEMENTO PORTLAND TIPO I", "ARENA GRUESA", "PIEDRA CHANCADA").mapIndexed { i, n -> insumo(n) }.mapIndexed { i, x ->
            pe.sevens.sigro.dominio.FilaStock(x.id, x.codigo, x.descripcion, x.unidad, "[saldo]", "2026-11-01T16:35", i == 1)
        },
        "2026-11-01T16:35:00", System.currentTimeMillis(), desdeCelular = false,
    )
    override suspend fun estadoRecepcion(obraId: Int, compraId: Int) = emptyList<pe.sevens.sigro.dominio.PendienteRecepcion>()
    override suspend fun obrasDestino(obraId: Int) = listOf(ObraAsignada(2, "[Otra obra registrada en SIGRO]", ""))
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

    val salidas = SalidasFalsas()
    val registros = RegistrosFalsos()
    capturar(dir, "03-elegir-obra") { PantallaObras(sesionEjemplo, {}, {}, ObrasViewModel(DispositivoFalso, salidas, registros)) }

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

    capturar(dir, "09-mis-registros") { PantallaPendientes(1, {}, PendientesViewModel(salidas, registros)) }
    capturar(dir, "09-mis-registros-oscuro", oscuro = true) { PantallaPendientes(1, {}, PendientesViewModel(salidas, registros)) }

    capturar(dir, "10-mi-celular") { PantallaDispositivo(1, {}, "Multifamiliar Flores", DispositivoViewModel(DispositivoFalso)) }
    val cat = CatalogoViewModel(InsumosFalsos)
    capturar(dir, "11-catalogo-buscar", preparar = { cat.buscar("fierro corrugado") }) { PantallaCatalogo({}, {}, {}, cat) }
    val fierro = catalogo.firstOrNull { it.descripcion.startsWith("FIERRO CORRUGADO") } ?: insumo("FIERRO")
    capturar(dir, "12-catalogo-ficha") {
        PantallaFichaInsumo(FichaInsumo(fierro.id, fierro.codigo, fierro.descripcion, "unidades", "kg", "1", "material", true, false), {}, {})
    }
    val nuevo = InsumoNuevoViewModel(InsumosFalsos)
    capturar(dir, "13-catalogo-nuevo", ms = 2500, preparar = { nuevo.material("Fierro corrugado"); nuevo.medida("1/2"); nuevo.unidad("unidades") }) { PantallaInsumoNuevo({}, {}, nuevo) }
    capturar(dir, "14-trabajadores") { PantallaTrabajadores(1, "Multifamiliar Flores", {}, {}, TrabajadoresViewModel(TrabajadoresFalsos, registros)) }
    val tn = TrabajadorNuevoViewModel(TrabajadoresFalsos)
    capturar(dir, "15-trabajador-nuevo", preparar = { tn.documento("1234567"); tn.nombre("Trabajador 08 (ejemplo)") }) {
        PantallaTrabajadorNuevo(1, {}, tn, TrabajadoresViewModel(TrabajadoresFalsos, registros))
    }
    val asis = AsistenciaViewModel(TrabajadoresFalsos, registros, sinProgramar)
    capturar(dir, "16-asistencia", preparar = { asis.cargar(1); Thread.sleep(300); asis.extra(1, 2); asis.estado(4, pe.sevens.sigro.dominio.EstadoAsistencia.AUSENTE) }) {
        PantallaAsistencia(1, {}, {}, asis)
    }
    val stock = StockViewModel(ObraFalsa)
    capturar(dir, "17-stock", preparar = { stock.cargar(1) }) { PantallaStock(1, {}, stock) }
    val entrada = EntradaViewModel(ObraFalsa, registros, CatalogoFalso, sinProgramar)
    capturar(dir, "18-entrada", preparar = { entrada.cargar(1); Thread.sleep(300); entrada.estado.value.compras.firstOrNull()?.let { entrada.elegir(1, it) }; entrada.alternarModo(); entrada.recibido(0, "6") }) {
        PantallaEntrada(1, {}, entrada)
    }
    val ingreso = IngresoSinCompraViewModel(CatalogoFalso, ObraFalsa, registros, sinProgramar)
    capturar(dir, "19-ingreso-sin-compra", preparar = { ingreso.origen(pe.sevens.sigro.dominio.OrigenIngreso.OTRA_OBRA); ingreso.elegir(insumo("ARENA GRUESA")); ingreso.cantidad("1,5"); ingreso.guardar(1) }) {
        PantallaIngresoSinCompra(1, {}, ingreso)
    }
    val traslado = TrasladoViewModel(CatalogoFalso, ObraFalsa, registros, sinProgramar)
    capturar(dir, "20-traslado", preparar = { traslado.cargar(1, sesionEjemplo.obras); Thread.sleep(300); traslado.estado.value.destinos.firstOrNull()?.let(traslado::destino); traslado.agregar(insumo("CEMENTO PORTLAND TIPO I")); traslado.cantidad(0, "20") }) {
        PantallaTraslado(1, "Multifamiliar Flores", sesionEjemplo.obras, {}, traslado)
    }
    val conteo = ConteoViewModel(CatalogoFalso, ObraFalsa, registros, sinProgramar)
    capturar(dir, "21-conteo", preparar = { conteo.agregar(insumo("CEMENTO PORTLAND TIPO I")); conteo.contado(0, "36"); conteo.motivo(0, pe.sevens.sigro.dominio.MotivoDiferencia.MERMA) }) {
        PantallaConteo(1, {}, conteo)
    }
    capturar(dir, "22-cierre") { PantallaCierre(1, "Multifamiliar Flores", {}, CierreViewModel(ObraFalsa, registros)) }
    kotlin.system.exitProcess(0)
}
