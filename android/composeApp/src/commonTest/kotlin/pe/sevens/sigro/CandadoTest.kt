package pe.sevens.sigro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pe.sevens.sigro.dominio.AjusteHuella
import pe.sevens.sigro.dominio.DisponibilidadHuella
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.ReglaCandado
import pe.sevens.sigro.dominio.RepositorioCandado
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.ResultadoHuella
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.ui.pantallas.CandadoViewModel
import pe.sevens.sigro.ui.pantallas.EstadoRaiz
import pe.sevens.sigro.ui.pantallas.sesionDe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* Entrar con huella: cuándo se bloquea, qué pasa con cada respuesta de la huella y qué se guarda. */
@OptIn(ExperimentalCoroutinesApi::class)
class CandadoTest {
    private val minuto = 60_000L
    private val activa = AjusteHuella(activa = true, minutos = 15, ofrecida = true)
    private val principal = StandardTestDispatcher()
    @BeforeTest fun antes() = Dispatchers.setMain(principal)
    @AfterTest fun despues() = Dispatchers.resetMain()

    @Test
    fun seBloqueaSoloConLaHuellaActivaYPasadoElTiempo() {
        assertFalse(ReglaCandado.vencido(AjusteHuella(), 0, 99 * minuto), "sin huella activa nunca se bloquea")
        assertFalse(ReglaCandado.vencido(activa, 0, 14 * minuto))
        assertTrue(ReglaCandado.vencido(activa, 0, 15 * minuto))
        assertTrue(ReglaCandado.vencido(activa, null, 0), "sin último uso guardado: ante la duda, el candado")
        assertTrue(ReglaCandado.vencido(activa, 10 * minuto, 5 * minuto), "reloj del celular atrasado: se bloquea")
    }

    @Test
    fun laOfertaSaleUnaSolaVezYSoloSiElCelularPuede() {
        assertTrue(ReglaCandado.ofrecer(AjusteHuella(), DisponibilidadHuella.DISPONIBLE))
        assertFalse(ReglaCandado.ofrecer(AjusteHuella(ofrecida = true), DisponibilidadHuella.DISPONIBLE), "ya dijo «ahora no»")
        assertFalse(ReglaCandado.ofrecer(AjusteHuella(), DisponibilidadHuella.SIN_REGISTRAR))
        assertFalse(ReglaCandado.ofrecer(activa, DisponibilidadHuella.DISPONIBLE), "ya la tiene")
    }

    @Test
    fun elAjusteSeGuardaComoTextoYUnoDanadoApagaLaHuella() {
        val a = AjusteHuella(activa = true, minutos = 30, ofrecida = true)
        assertEquals(a, ReglaCandado.decodificar(ReglaCandado.codificar(a)))
        assertEquals(AjusteHuella(), ReglaCandado.decodificar("basura"))
        assertEquals(15, ReglaCandado.decodificar("activa=1;min=7;ofrecida=1").minutos, "minutos que no están en la lista: los de siempre")
    }

    private class Sesiones(id: Int?) : RepositorioSesion {
        val sesionFlujo = MutableStateFlow(id?.let { Sesion("s", "t", it, "Adiel Luján", "adiel", listOf(ObraAsignada(1, "Multifamiliar Flores", "administrador"))) })
        var cerro = false
        override val sesion: Flow<Sesion?> = sesionFlujo
        override val obraElegida: Flow<ObraAsignada?> = MutableStateFlow(null)
        override suspend fun servidorGuardado() = "s"
        override suspend fun iniciarSesion(servidor: String, usuario: String, clave: String): Sesion = error("no")
        override suspend fun elegirObra(obra: ObraAsignada) {}
        override suspend fun cambiarDeObra() {}
        override suspend fun cerrarSesion() { cerro = true; sesionFlujo.value = null }
    }

    private class Guardado(var ajustes: MutableMap<Int, AjusteHuella> = mutableMapOf(), var uso: Long? = null) : RepositorioCandado {
        override suspend fun ajuste(usuarioId: Int) = ajustes[usuarioId] ?: AjusteHuella()
        override suspend fun guardar(usuarioId: Int, ajuste: AjusteHuella) { ajustes[usuarioId] = ajuste }
        override suspend fun ultimoUso() = uso
        override suspend fun guardarUltimoUso(ms: Long) { uso = ms }
    }

    @Test
    fun alAbrirLaAppPasadoElTiempoSeAbreBloqueada() = runTest(principal) {
        var ahora = 100 * minuto
        val vm = CandadoViewModel(Sesiones(7), Guardado(mutableMapOf(7 to activa), uso = 80 * minuto), reloj = { ahora })
        advanceUntilIdle()
        assertTrue(vm.estado.value.bloqueada, "pasaron 20 min desde que la dejó")
        vm.alDesbloquear(ResultadoHuella.Ok); advanceUntilIdle()
        assertFalse(vm.estado.value.bloqueada)
        ahora += 14 * minuto; vm.revisar()
        assertFalse(vm.estado.value.bloqueada, "a los 14 minutos todavía no")
        ahora += 2 * minuto; vm.revisar()
        assertTrue(vm.estado.value.bloqueada, "a los 16, sí")
    }

    @Test
    fun usarLaAppReiniciaLaCuenta() = runTest(principal) {
        var ahora = 0L
        val vm = CandadoViewModel(Sesiones(7), Guardado(mutableMapOf(7 to activa), uso = 0), reloj = { ahora })
        advanceUntilIdle()
        ahora = 10 * minuto; vm.tocar()
        ahora = 20 * minuto; vm.revisar()
        assertFalse(vm.estado.value.bloqueada, "tocó a los 10: van 10, no 20")
    }

    @Test
    fun cancelarNoDesbloqueaYUnFalloSeMuestra() = runTest(principal) {
        val vm = CandadoViewModel(Sesiones(7), Guardado(mutableMapOf(7 to activa), uso = null), reloj = { 0 })
        advanceUntilIdle()
        vm.alDesbloquear(ResultadoHuella.Cancelada)
        assertTrue(vm.estado.value.bloqueada)
        vm.alDesbloquear(ResultadoHuella.Fallo("Demasiados intentos"))
        assertTrue(vm.estado.value.bloqueada); assertEquals("Demasiados intentos", vm.estado.value.mensaje)
    }

    @Test
    fun unaHuellaNuevaApagaLaHuellaYPideLaContrasena() = runTest(principal) {
        val sesiones = Sesiones(7); val guardado = Guardado(mutableMapOf(7 to activa), uso = null)
        val vm = CandadoViewModel(sesiones, guardado, reloj = { 0 })
        advanceUntilIdle()
        vm.alDesbloquear(ResultadoHuella.HuellaNueva); advanceUntilIdle()
        assertTrue(sesiones.cerro, "se cierra la sesión: entra con su contraseña")
        assertFalse(guardado.ajustes.getValue(7).activa)
        assertFalse(guardado.ajustes.getValue(7).ofrecida, "al volver a entrar se le ofrece de nuevo")
    }

    @Test
    fun recienEntradoConContrasenaNoSeBloquea() = runTest(principal) {
        val sesiones = Sesiones(null)
        val vm = CandadoViewModel(sesiones, Guardado(mutableMapOf(7 to activa), uso = 0), reloj = { 999 * minuto })
        advanceUntilIdle()
        sesiones.sesionFlujo.value = Sesion("s", "t", 7, "Adiel Luján", "adiel", emptyList())
        advanceUntilIdle()
        assertFalse(vm.estado.value.bloqueada, "acaba de escribir su contraseña")
        assertTrue(vm.estado.value.ajuste.activa)
    }

    @Test
    fun activarAhoraNoYMinutosSeGuardanPorUsuario() = runTest(principal) {
        val guardado = Guardado()
        val vm = CandadoViewModel(Sesiones(7), guardado, reloj = { 0 })
        advanceUntilIdle()
        vm.ahoraNo(); advanceUntilIdle()
        assertEquals(AjusteHuella(ofrecida = true), guardado.ajustes[7])
        vm.alActivar(ResultadoHuella.Ok); vm.minutos(5); advanceUntilIdle()
        assertEquals(AjusteHuella(activa = true, minutos = 5, ofrecida = true), guardado.ajustes[7])
        vm.alSalir(); advanceUntilIdle()
        assertEquals(0L, guardado.uso, "al salir se guarda la hora")
        vm.bloquearAhora(); assertTrue(vm.estado.value.bloqueada)
        vm.desactivar(); advanceUntilIdle()
        assertFalse(guardado.ajustes.getValue(7).activa)
    }

    @Test
    fun laSesionSeLeeEnCualquierEstado() {
        val s = Sesion("s", "t", 7, "A", "a", emptyList())
        assertEquals(s, sesionDe(EstadoRaiz.ElegirObra(s)))
        assertEquals(s, sesionDe(EstadoRaiz.Dentro(s, ObraAsignada(1, "O", "almacenero"))))
        assertNull(sesionDe(EstadoRaiz.SinSesion))
    }
}
