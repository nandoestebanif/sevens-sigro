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
import pe.sevens.sigro.dominio.HorasTrabajador
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.NuevoTrabajador
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RegistroLocal
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.RepositorioTrabajadores
import pe.sevens.sigro.dominio.ResultadoSincronizacion
import pe.sevens.sigro.dominio.Trabajador
import pe.sevens.sigro.dominio.ValidarBaja
import pe.sevens.sigro.dominio.horasLegibles
import pe.sevens.sigro.dominio.resumirHoras
import pe.sevens.sigro.ui.pantallas.HorasViewModel
import pe.sevens.sigro.ui.pantallas.TrabajadoresViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** HU-62 c2 (baja con fecha, nada se borra) y HU-64 (horas acumuladas por trabajador, cuadrilla y periodo). */
@OptIn(ExperimentalCoroutinesApi::class)
class TrabajadoresBajaYHorasTest {
    private val ana = Trabajador(7, 1, "Ana Ruiz", "DNI", "12345678", "oficial", "Cuadrilla A", "2026-10-19", null)

    private fun h(periodo: Int, id: Int, nombre: String, cuadrilla: String, cat: String, n: Double, x: Double, ultima: String) =
        HorasTrabajador(periodo, id, nombre, cuadrilla, cat, 5, 0, 0, n, x, ultima)

    private val filas = listOf(
        h(1, 7, "Ana Ruiz", "Cuadrilla A", "oficial", 40.0, 2.0, "2026-10-24"),
        h(2, 7, "Ana Ruiz", "Cuadrilla A", "oficial", 32.0, 0.0, "2026-10-30"),
        h(1, 8, "Luis Paz", "Cuadrilla A", "peon", 24.0, 4.5, "2026-10-23"),
        h(2, 9, "Rosa Gil", "Cuadrilla B", "operario", 16.0, 0.0, "2026-10-29"),
    )

    private class Repo(var falla: ErrorSigro? = null, val horas: List<HorasTrabajador> = emptyList()) : RepositorioTrabajadores {
        val bajas = mutableListOf<Pair<Int, String>>()
        override fun deObra(obraId: Int) = MutableStateFlow(emptyList<Trabajador>())
        override suspend fun actualizar(obraId: Int) = 0
        override suspend fun registrar(obraId: Int, nuevo: NuevoTrabajador) = error("no se usa")
        override suspend fun darDeBaja(obraId: Int, trabajadorId: Int, fechaSalida: String): Trabajador {
            falla?.let { throw it }
            bajas += trabajadorId to fechaSalida
            return Trabajador(trabajadorId, obraId, "Ana Ruiz", "DNI", "12345678", "oficial", "Cuadrilla A", "2026-10-19", fechaSalida)
        }
        override suspend fun horas(obraId: Int): List<HorasTrabajador> { falla?.let { throw it }; return horas }
    }

    private object SinRegistros : RepositorioRegistros {
        override fun deObra(obraId: Int) = flowOf(emptyList<RegistroLocal>())
        override val pendientes = flowOf(0)
        override suspend fun registrarAsistencia(obraId: Int, fecha: String, marcas: List<MarcaAsistencia>) = emptyList<RegistroLocal>()
        override suspend fun yaMarcados(obraId: Int, fecha: String) = emptySet<Int>()
        override suspend fun registrarAlmacen(obraId: Int, registro: RegistroAlmacen) = error("no se usa")
        override suspend fun sincronizar(manual: Boolean) = ResultadoSincronizacion(0, 0, 0)
    }

    @BeforeTest fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun despues() = Dispatchers.resetMain()

    @Test
    fun laFechaDeSalidaNoPuedeSerAntesDelIngresoNiFutura() {
        assertNull(ValidarBaja.error(ana, "2026-11-20", hoy = "2026-11-24"))
        assertNull(ValidarBaja.error(ana, "2026-10-19", hoy = "2026-11-24"))          // el mismo día que ingresó
        assertEquals("No puede ser antes de su ingreso (2026-10-19)", ValidarBaja.error(ana, "2026-10-01", "2026-11-24"))
        assertEquals("No puede ser una fecha futura", ValidarBaja.error(ana, "2026-12-01", "2026-11-24"))
        assertEquals("Usa el formato AAAA-MM-DD", ValidarBaja.error(ana, "20/11/2026", "2026-11-24"))
    }

    @Test
    fun darDeBajaEnviaLaFechaYCierraElDialogo() = runTest {
        val repo = Repo()
        val vm = TrabajadoresViewModel(repo, SinRegistros)
        vm.abrirBaja(ana, hoy = "2026-11-24")
        assertEquals("2026-11-24", vm.estado.value.fechaBaja)                         // propone hoy
        vm.fechaBaja("2026-11-20")
        vm.confirmarBaja(1, hoy = "2026-11-24")
        assertEquals(listOf(7 to "2026-11-20"), repo.bajas)
        assertNull(vm.estado.value.bajaDe)
        assertTrue(vm.estado.value.confirmacion!!.contains("jornadas se conservan"))
    }

    @Test
    fun unaFechaInvalidaNoLlegaAlServidorYSinSenalLoDice() = runTest {
        val repo = Repo()
        val vm = TrabajadoresViewModel(repo, SinRegistros)
        vm.abrirBaja(ana, "2026-11-24"); vm.fechaBaja("2026-10-01"); vm.confirmarBaja(1, "2026-11-24")
        assertTrue(repo.bajas.isEmpty())
        assertEquals("No puede ser antes de su ingreso (2026-10-19)", vm.estado.value.errorBaja)

        repo.falla = ErrorSigro("sin_conexion", "sin red")
        vm.fechaBaja("2026-11-20"); vm.confirmarBaja(1, "2026-11-24")
        assertTrue(vm.estado.value.errorBaja!!.startsWith("Sin señal"))
        assertEquals(ana, vm.estado.value.bajaDe)                                       // el diálogo sigue abierto
    }

    @Test
    fun resumirHorasPorTrabajadorCuadrillaYPeriodo() {
        val todo = resumirHoras(filas, periodo = null)
        assertEquals(112.0, todo.horasNormales); assertEquals(6.5, todo.horasExtra)      // normales y extra aparte (c1)
        assertEquals("2026-10-30", todo.ultimaJornada)                                   // última considerada (c3)
        assertEquals(listOf(1, 2), todo.periodos)
        val anaT = todo.porTrabajador.first()
        assertEquals("Ana Ruiz", anaT.nombre); assertEquals(72.0, anaT.horasNormales); assertEquals("2026-10-30", anaT.ultimaJornada)
        assertEquals(listOf("Cuadrilla A" to 96.0, "Cuadrilla B" to 16.0), todo.porCuadrilla.map { it.nombre to it.horasNormales })
        assertEquals("2 trabajadores", todo.porCuadrilla.first().detalle)

        val semana1 = resumirHoras(filas, periodo = 1)                                   // por periodo (c2)
        assertEquals(64.0, semana1.horasNormales)
        assertEquals(listOf("Ana Ruiz", "Luis Paz"), semana1.porTrabajador.map { it.nombre })
        assertEquals("2026-10-24", semana1.ultimaJornada)
        assertEquals(listOf(1, 2), semana1.periodos)                                     // las semanas no desaparecen al filtrar
    }

    @Test
    fun horasLegiblesSinDecimalesDeMas() {
        assertEquals("8 h", horasLegibles(8.0)); assertEquals("4.5 h", horasLegibles(4.5)); assertEquals("0 h", horasLegibles(0.0))
    }

    @Test
    fun horasSinSenalAvisanYConSenalCargan() = runTest {
        val repo = Repo(falla = ErrorSigro("sin_conexion", "sin red"), horas = filas)
        val vm = HorasViewModel(repo)
        vm.cargar(1)
        assertTrue(vm.estado.value.aviso!!.startsWith("Sin señal"))
        repo.falla = null
        vm.cargar(1); vm.periodo(2)
        assertEquals(48.0, vm.estado.value.resumen.horasNormales)
        assertNull(vm.estado.value.aviso)
    }
}
