package pe.sevens.sigro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import pe.sevens.sigro.dominio.HorasTrabajador
import pe.sevens.sigro.dominio.NuevoTrabajador
import pe.sevens.sigro.dominio.RepositorioTrabajadores
import pe.sevens.sigro.dominio.Trabajador
import pe.sevens.sigro.ui.pantallas.TrabajadorNuevoViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 26/11: al escribir los 8 dígitos del DNI, la app pide a RENIEC (a través del servidor) el nombre.
    Sin señal o sin respuesta se escribe a mano: nunca bloquea el registro, ni pisa lo que la persona escribió. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrabajadorDniReniecTest {
    private class Repo(val respuesta: String?, val gente: List<Trabajador> = emptyList()) : RepositorioTrabajadores {
        val consultas = mutableListOf<String>()
        override fun deObra(obraId: Int) = MutableStateFlow(gente)
        override suspend fun actualizar(obraId: Int) = 0
        override suspend fun registrar(obraId: Int, nuevo: NuevoTrabajador): Trabajador = error("no se usa")
        override suspend fun darDeBaja(obraId: Int, trabajadorId: Int, fechaSalida: String): Trabajador = error("no se usa")
        override suspend fun horas(obraId: Int) = emptyList<HorasTrabajador>()
        override suspend fun consultarDni(obraId: Int, dni: String): String? { consultas += dni; return respuesta }
    }

    @BeforeTest fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm(repo: Repo) = TrabajadorNuevoViewModel(repo).also { it.obra(1); it.conocer(repo.gente) }

    @Test
    fun conLosOchoDigitosCompletaElNombreDesdeReniec() {
        val repo = Repo("ANA MARIA RUIZ PAZ")
        val v = vm(repo)
        v.documento("1234567")
        assertTrue(repo.consultas.isEmpty())                        // con 7 dígitos no se consulta
        v.documento("12345678")
        assertEquals(listOf("12345678"), repo.consultas)
        assertEquals("ANA MARIA RUIZ PAZ", v.estado.value.nombre)
        assertEquals("Nombre según RENIEC", v.estado.value.avisoDni)
        assertFalse(v.estado.value.consultandoDni)
    }

    @Test
    fun sinSenalSeEscribeAManoYLoDice() {
        val v = vm(Repo(null))
        v.documento("12345678")
        assertEquals("", v.estado.value.nombre)
        assertEquals("Sin señal o RENIEC no respondió: escribe el nombre a mano.", v.estado.value.avisoDni)
    }

    @Test
    fun noPisaUnNombreEscritoAManoNiConsultaUnDniRepetido() {
        val escrito = Repo("OTRO NOMBRE")
        val v = vm(escrito)
        v.nombre("Luis Paz")
        v.documento("87654321")
        assertEquals("Luis Paz", v.estado.value.nombre)
        assertTrue(escrito.consultas.isEmpty())

        val ana = Trabajador(7, 1, "Ana Ruiz", "DNI", "12345678", "oficial", "Cuadrilla A", "2026-10-19", null)
        val repetido = Repo("ANA RUIZ", listOf(ana))
        vm(repetido).documento("12345678")
        assertTrue(repetido.consultas.isEmpty())
    }

    @Test
    fun siCambiaElDniSeBorraElNombreQuePusoReniec() {
        val v = vm(Repo("ANA MARIA RUIZ PAZ"))
        v.documento("12345678")
        v.documento("1234567")
        assertEquals("", v.estado.value.nombre)
    }
}
