package pe.sevens.sigro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoAsistencia
import pe.sevens.sigro.dominio.JornadaCorregida
import pe.sevens.sigro.dominio.RepositorioJornadas
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.NuevoTrabajador
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RegistroLocal
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.RepositorioTrabajadores
import pe.sevens.sigro.dominio.ResultadoSincronizacion
import pe.sevens.sigro.dominio.TipoRegistro
import pe.sevens.sigro.dominio.Trabajador
import pe.sevens.sigro.ui.pantallas.AsistenciaViewModel
import pe.sevens.sigro.ui.pantallas.iniciales
import pe.sevens.sigro.ui.pantallas.resumirTrabajadores
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* HU-62 / HU-63 desde la pantalla: la asistencia pasa por la cola sin señal y pide enviar. */

private fun t(id: Int, salida: String? = null, cuadrilla: String = "Cuadrilla A", cat: String = "peon") =
    Trabajador(id, 1, "Trabajador 0$id", "DNI", "0000000$id", cat, cuadrilla, "2026-10-01", salida)

private class TrabajadoresEnMemoria(lista: List<Trabajador>) : RepositorioTrabajadores {
    val flujo = MutableStateFlow(lista)
    override fun deObra(obraId: Int) = flujo
    override suspend fun actualizar(obraId: Int) = flujo.value.size
    override suspend fun registrar(obraId: Int, nuevo: NuevoTrabajador) = error("no se usa")
}

private class ColaEspia(private val ya: Set<Int> = emptySet()) : RepositorioRegistros {
    val asistencias = mutableListOf<List<MarcaAsistencia>>()
    override fun deObra(obraId: Int) = flowOf(emptyList<RegistroLocal>())
    override val pendientes = flowOf(0)
    override suspend fun registrarAsistencia(obraId: Int, fecha: String, marcas: List<MarcaAsistencia>): List<RegistroLocal> {
        asistencias += marcas
        return marcas.map { RegistroLocal("u${it.trabajador.id}", obraId, TipoRegistro.JORNADA, it.trabajador.nombre, "", 0, EstadoEnvio.PENDIENTE, null, null, 0) }
    }
    override suspend fun yaMarcados(obraId: Int, fecha: String) = ya + asistencias.flatten().map { it.trabajador.id }
    override suspend fun registrarAlmacen(obraId: Int, registro: RegistroAlmacen) = error("no se usa")
    override suspend fun sincronizar() = ResultadoSincronizacion(0, 0, 0)
}

private class JornadasEspia(private val falla: ErrorSigro? = null) : RepositorioJornadas {
    val pedidas = mutableListOf<Triple<String, MarcaAsistencia, String>>()
    override suspend fun corregir(obraId: Int, fecha: String, marca: MarcaAsistencia, motivo: String): JornadaCorregida {
        falla?.let { throw it }
        pedidas += Triple(fecha, marca, motivo)
        return JornadaCorregida(99, 10)
    }
}

private val sinProgramar = object : ProgramadorSincronizacion { override fun programar() {} }

@OptIn(ExperimentalCoroutinesApi::class)
class AsistenciaViewModelTest {
    @BeforeTest fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun despues() = Dispatchers.resetMain()

    @Test
    fun guardaEnLaColaSoloLosQueFaltanYPideEnviar() = runTest {
        var programado = 0
        val cola = ColaEspia(ya = setOf(2))
        val vm = AsistenciaViewModel(
            TrabajadoresEnMemoria(listOf(t(1), t(2), t(3, salida = "2026-10-15"))), cola,
            object : ProgramadorSincronizacion { override fun programar() { programado++ } },
            JornadasEspia(),
        )
        vm.cargar(1)
        val e = vm.estado.value
        assertEquals(listOf(1, 2), e.marcas.map { it.trabajador.id }, "el de baja no aparece (HU-62)")
        assertEquals(listOf(1), e.porMarcar.map { it.trabajador.id }, "el ya registrado hoy no se marca dos veces (HU-63)")

        vm.estado(1, EstadoAsistencia.PRESENTE); vm.extra(1, +2)
        vm.guardar(1)
        assertEquals(1, cola.asistencias.size)
        assertEquals(2, cola.asistencias[0].single().horasExtra)
        assertEquals(1, programado, "se pide enviar cuando haya señal")
        assertEquals(1, vm.estado.value.guardadas)
        assertTrue(vm.estado.value.porMarcar.isEmpty())
    }

    @Test
    fun hu63c3_corregirUnaJornadaYaRegistradaExigeMotivoYLaPideAlServidor() = runTest {
        val jornadas = JornadasEspia()
        val vm = AsistenciaViewModel(TrabajadoresEnMemoria(listOf(t(1), t(2))), ColaEspia(ya = setOf(2)), sinProgramar, jornadas)
        vm.cargar(1)
        vm.empezarCorreccion(2)
        vm.estadoCorreccion(EstadoAsistencia.PRESENTE); vm.normalesCorreccion(-4); vm.extraCorreccion(+1)
        vm.confirmarCorreccion(1)
        assertTrue(vm.estado.value.errorCorreccion != null, "sin motivo no se corrige")
        assertTrue(jornadas.pedidas.isEmpty())

        vm.motivo("Llegó tarde: trabajó 4 h")
        vm.confirmarCorreccion(1)
        val (fecha, marca, motivo) = jornadas.pedidas.single()
        assertEquals(vm.estado.value.fecha, fecha)
        assertEquals(2, marca.trabajador.id)
        assertEquals("4", marca.normalesEnvio); assertEquals("1", marca.extraEnvio)
        assertEquals("Llegó tarde: trabajó 4 h", motivo)
        assertEquals(null, vm.estado.value.corrigiendo, "al terminar se cierra el formulario")
        assertTrue(vm.estado.value.corregida!!.contains("constancia"), "dice que la anterior queda como constancia")
    }

    @Test
    fun hu63c3_sinSenalLaCorreccionLoDiceYNoSePierdeLoEscrito() = runTest {
        val vm = AsistenciaViewModel(
            TrabajadoresEnMemoria(listOf(t(1))), ColaEspia(ya = setOf(1)), sinProgramar,
            JornadasEspia(ErrorSigro("sin_conexion", "No se pudo conectar con el servidor.")),
        )
        vm.cargar(1)
        vm.empezarCorreccion(1); vm.estadoCorreccion(EstadoAsistencia.AUSENTE); vm.motivo("No vino, se marcó por error")
        vm.confirmarCorreccion(1)
        val e = vm.estado.value
        assertTrue(e.errorCorreccion!!.startsWith("Para corregir hace falta señal"))
        assertEquals(EstadoAsistencia.AUSENTE, e.corrigiendo?.estado, "lo elegido sigue en pantalla para reintentar")
        assertEquals("0", e.corrigiendo?.normalesEnvio, "ausente viaja sin horas")
    }

    @Test
    fun listaDeTrabajadoresSeparaBajasYAgrupaPorCuadrilla() {
        val r = resumirTrabajadores(
            listOf(t(1, cat = "operario"), t(2, cuadrilla = "Cuadrilla B"), t(3, salida = "2026-10-15")), hoy = "2026-11-01",
        )
        assertEquals(2, r.activos.size)
        assertEquals(listOf(3), r.deBaja.map { it.id }, "regla 10: la baja no se borra, se muestra aparte")
        assertEquals(listOf("Cuadrilla A", "Cuadrilla B"), r.porCuadrilla.map { it.first })
        assertEquals("JP", iniciales("Juan  Pérez"))
    }
}
