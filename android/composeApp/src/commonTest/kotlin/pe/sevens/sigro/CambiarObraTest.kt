package pe.sevens.sigro

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.ui.pantallas.CelularEnObra
import pe.sevens.sigro.ui.pantallas.ENTRANDO_MS
import pe.sevens.sigro.ui.pantallas.EstadoRaiz
import pe.sevens.sigro.ui.pantallas.RaizViewModel
import pe.sevens.sigro.ui.pantallas.indiceDeObra
import pe.sevens.sigro.ui.pantallas.obrasDe
import pe.sevens.sigro.ui.pantallas.avisoColaAlCambiar
import pe.sevens.sigro.ui.pantallas.colorCelular
import pe.sevens.sigro.ui.pantallas.colorDeObra
import pe.sevens.sigro.ui.pantallas.conBuscador
import pe.sevens.sigro.ui.pantallas.dibujoDeObra
import pe.sevens.sigro.ui.pantallas.otrasObras
import pe.sevens.sigro.ui.pantallas.saludoPorHora
import pe.sevens.sigro.ui.tema.Paleta
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* Elegir y cambiar de obra (rediseño v3): reglas de la vista, sin base ni servidor. */
@OptIn(ExperimentalCoroutinesApi::class)
class CambiarObraTest {
    private val obras = listOf(
        ObraAsignada(1, "Multifamiliar Flores", "administrador"),
        ObraAsignada(2, "Obra de ejemplo 2", "residente"),
        ObraAsignada(3, "Obra de ejemplo 3", "almacenero"),
    )

    private val principal = StandardTestDispatcher()
    @BeforeTest fun antes() = Dispatchers.setMain(principal)
    @AfterTest fun despues() = Dispatchers.resetMain()

    @Test
    fun laHojaOfreceLasOtrasObrasEnElOrdenDeLaSesion() {
        assertEquals(listOf(1, 3), otrasObras(obras, actualId = 2).map { it.id })
        assertEquals(emptyList(), otrasObras(obras.take(1), actualId = 1), "con una sola obra no hay a dónde cambiar")
    }

    @Test
    fun cadaObraConservaSuColorYSuDibujoEnTodasLasPantallas() {
        assertEquals(Paleta.TicketAmarillo, colorDeObra(0), "la primera obra es amarilla, como la marca")
        assertEquals(colorDeObra(0), colorDeObra(5), "los colores se turnan de cinco en cinco")
        assertNotEquals(colorDeObra(0), colorDeObra(1), "dos obras seguidas no comparten color")
        assertEquals(dibujoDeObra(2), dibujoDeObra(7))
        assertEquals(colorDeObra(4), colorDeObra(-1), "un índice -1 (obra no encontrada) no revienta")
    }

    @Test
    fun elBuscadorSaleSoloConMasDeTresObras() {
        assertTrue(!conBuscador(3) && conBuscador(4))
    }

    @Test
    fun elAvisoDeLaColaDiceQueNadaSePierde() {
        assertNull(avisoColaAlCambiar(0), "sin nada por enviar no hay aviso")
        assertEquals("1 registro por enviar. Cambiar de obra no lo pierde: se envía a la obra donde lo hiciste.", avisoColaAlCambiar(1))
        assertTrue(avisoColaAlCambiar(4)!!.startsWith("4 registros por enviar."))
    }

    @Test
    fun elEstadoDelCelularSeLeeSobreLaEtiquetaNegra() {
        val negro = Color(0xFF161717)
        fun contraste(a: Color, b: Color): Double {
            val (x, y) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
            return (x / y).toDouble()
        }
        CelularEnObra.entries.forEach { c ->
            assertTrue(contraste(colorCelular(c), negro) >= 4.5, "${c.name} pasa AA sobre la etiqueta negra")
        }
    }

    @Test
    fun entrandoTomaElColorDeLaObraEnCualquierEstado() {
        val sesion = Sesion("https://api.prueba", "t", 1, "Adiel Luján", "adiel", obras)
        assertEquals(obras, obrasDe(EstadoRaiz.ElegirObra(sesion)), "al entrar")
        assertEquals(obras, obrasDe(EstadoRaiz.Dentro(sesion, obras[0])), "al cambiar desde el inicio")
        assertEquals(emptyList(), obrasDe(EstadoRaiz.SinSesion))
        assertEquals(2, indiceDeObra(obras, obras[2]))
        assertEquals(0, indiceDeObra(emptyList(), obras[2]), "si no está en la lista, el primer color (no revienta)")
    }

    @Test
    fun elSaludoVaConLaHora() {
        assertEquals("Buenos días", saludoPorHora(7))
        assertEquals("Buenas tardes", saludoPorHora(15))
        assertEquals("Buenas noches", saludoPorHora(22))
        assertEquals("Buenas noches", saludoPorHora(3))
    }

    @Test
    fun alElegirSeMuestraEntrandoMientrasSeGuardaLaObraYLuegoSeQuita() = runTest(principal) {
        val sesiones = SesionesQueAnotan()
        val vm = RaizViewModel(sesiones)
        vm.elegir(obras[1])
        runCurrent()
        assertEquals(obras[1], vm.entrando.value, "«Entrando a …» tapa el cambio desde el primer instante")
        assertEquals(listOf(2), sesiones.elegidas, "la obra se guarda debajo de la pantalla de entrada")
        advanceTimeBy(ENTRANDO_MS - 1)
        assertEquals(obras[1], vm.entrando.value, "se ve el tiempo mínimo para leerla")
        advanceUntilIdle()
        assertNull(vm.entrando.value, "después se descubre la obra nueva")
    }

    private inner class SesionesQueAnotan : RepositorioSesion {
        val elegidas = mutableListOf<Int>()
        private val elegida = MutableStateFlow<ObraAsignada?>(obras[0])
        override val sesion: Flow<Sesion?> = MutableStateFlow(Sesion("https://api.prueba", "t", 1, "Adiel Luján", "adiel", obras))
        override val obraElegida: Flow<ObraAsignada?> = elegida
        override suspend fun servidorGuardado() = "https://api.prueba"
        override suspend fun iniciarSesion(servidor: String, usuario: String, clave: String): Sesion = error("no se usa")
        override suspend fun elegirObra(obra: ObraAsignada) { elegidas += obra.id; elegida.value = obra }
        override suspend fun cambiarDeObra() { elegida.value = null }
        override suspend fun cerrarSesion() {}
    }
}
