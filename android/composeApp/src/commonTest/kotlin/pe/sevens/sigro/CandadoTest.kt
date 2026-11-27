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
import pe.sevens.sigro.dominio.Biometria
import pe.sevens.sigro.dominio.ClaveReciente
import pe.sevens.sigro.dominio.ResultadoSecreto
import pe.sevens.sigro.ui.pantallas.EntrarViewModel
import pe.sevens.sigro.dominio.CuentaConHuella
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
import pe.sevens.sigro.ui.pantallas.AvisoDeCelular
import pe.sevens.sigro.ui.pantallas.avisoDeCelular
import pe.sevens.sigro.dominio.EstadoDispositivo
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
        val a = AjusteHuella(activa = true, minutos = 5, ofrecida = true)
        assertEquals(a, ReglaCandado.decodificar(ReglaCandado.codificar(a)))
        assertEquals(AjusteHuella(), ReglaCandado.decodificar("basura"))
        assertEquals(0, ReglaCandado.decodificar("activa=1;min=7;ofrecida=1").minutos, "minutos que no están en la lista: al abrir la app")
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
        var cuentaGuardada: CuentaConHuella? = null
        override suspend fun cuenta() = cuentaGuardada
        override suspend fun guardarCuenta(c: CuentaConHuella) { cuentaGuardada = c }
        override suspend fun olvidarCuenta() { cuentaGuardada = null }
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
    fun bloquearAhoraSigueBloqueadaAunqueSeCierreLaApp() = runTest(principal) {
        // 27/11, prueba en celular: «Bloquear ahora» y cerrar la app la dejaba abierta al volver
        val ahora = 100 * minuto
        val guardado = Guardado(mutableMapOf(7 to activa), uso = 99 * minuto)
        val vm = CandadoViewModel(Sesiones(7), guardado, reloj = { ahora })
        advanceUntilIdle()
        assertFalse(vm.estado.value.bloqueada)
        vm.bloquearAhora(); advanceUntilIdle()
        vm.alSalir(); advanceUntilIdle() // pasa a segundo plano estando bloqueada: no se pisa el bloqueo
        assertEquals(CandadoViewModel.BLOQUEADA, guardado.uso)
        val reabierta = CandadoViewModel(Sesiones(7), guardado, reloj = { ahora + 1_000 })
        advanceUntilIdle()
        assertTrue(reabierta.estado.value.bloqueada, "al reabrir un segundo después sigue bloqueada")
        reabierta.alDesbloquear(ResultadoHuella.Ok); advanceUntilIdle()
        assertEquals(ahora + 1_000, guardado.uso, "desbloqueada, vuelve a contar desde ahora")
    }

    @Test
    fun alEntrarConContrasenaSeBorraUnBloqueoGuardado() = runTest(principal) {
        val sesiones = Sesiones(null)
        val guardado = Guardado(mutableMapOf(7 to activa), uso = CandadoViewModel.BLOQUEADA)
        val vm = CandadoViewModel(sesiones, guardado, reloj = { 50 * minuto })
        advanceUntilIdle()
        sesiones.sesionFlujo.value = Sesion("s", "t", 7, "Adiel Luján", "adiel", emptyList()); advanceUntilIdle()
        assertFalse(vm.estado.value.bloqueada)
        assertEquals(50 * minuto, guardado.uso)
    }

    @Test
    fun huellaActivaSinSuContrasenaGuardadaSeVuelveAOfrecer() = runTest(principal) {
        // 27/11: Adiel entraba y la huella «venía activada», pero su contraseña cifrada ya no estaba en el celular
        val sesiones = Sesiones(null)
        val guardado = Guardado(mutableMapOf(7 to activa))
        val vm = CandadoViewModel(sesiones, guardado, reloj = { 0 })
        advanceUntilIdle()
        sesiones.sesionFlujo.value = Sesion("s", "t", 7, "Adiel Luján", "adiel", emptyList()); advanceUntilIdle()
        assertTrue(vm.estado.value.recienEntrado)
        vm.revisarVinculo(guardaClaves = false); advanceUntilIdle()
        assertTrue(guardado.ajustes.getValue(7).activa, "en Android 10 o menos la huella solo desbloquea: se queda")
        vm.revisarVinculo(guardaClaves = true); advanceUntilIdle()
        assertFalse(guardado.ajustes.getValue(7).activa)
        assertFalse(guardado.ajustes.getValue(7).ofrecida, "se le ofrece activarla con la contraseña que acaba de escribir")
    }

    @Test
    fun conSuContrasenaGuardadaNoSeTocaLaHuella() = runTest(principal) {
        val sesiones = Sesiones(null)
        val guardado = Guardado(mutableMapOf(7 to activa)).apply { cuentaGuardada = CuentaConHuella(7, "adiel", "Adiel Luján", "s", "sobre") }
        val vm = CandadoViewModel(sesiones, guardado, reloj = { 0 })
        advanceUntilIdle()
        sesiones.sesionFlujo.value = Sesion("s", "t", 7, "Adiel Luján", "adiel", emptyList()); advanceUntilIdle()
        vm.revisarVinculo(guardaClaves = true); advanceUntilIdle()
        assertTrue(guardado.ajustes.getValue(7).activa)
    }

    @Test
    fun laContrasenaDePerfilSeCompruebaAntesDeCifrarla() = runTest(principal) {
        val sesiones = object : RepositorioSesion by Sesiones(7) {
            override suspend fun comprobarClave(servidor: String, usuario: String, clave: String) = clave == "Correcta2026"
        }
        val claveReciente = ClaveReciente({ 0 })
        val vm = CandadoViewModel(sesiones, Guardado(mutableMapOf(7 to activa)), reloj = { 0 }, clave = claveReciente)
        advanceUntilIdle()
        assertEquals("La contraseña no es correcta.", vm.comprobarClave("mal"))
        assertFalse(claveReciente.hay("adiel"), "una contraseña equivocada no se cifra con la huella")
        assertNull(vm.comprobarClave("Correcta2026"))
        assertTrue(claveReciente.hay("adiel"))
        val sinSenal = CandadoViewModel(object : RepositorioSesion by Sesiones(7) {
            override suspend fun comprobarClave(servidor: String, usuario: String, clave: String): Boolean = throw pe.sevens.sigro.dominio.ErrorSigro("sin_conexion", "sin señal")
        }, Guardado(mutableMapOf(7 to activa)), reloj = { 0 })
        advanceUntilIdle()
        assertTrue(sinSenal.comprobarClave("x")!!.contains("señal"))
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

    // ── entrar con huella después de cerrar sesión ─────────────────────────────────────────

    /** Huella de mentira: anota lo que se cifró y responde lo que se le pida. */
    private class HuellaFalsa(
        var alCifrar: (String) -> ResultadoSecreto = { ResultadoSecreto.Ok("sobre($it)") },
        var alDescifrar: (String) -> ResultadoSecreto = { s -> ResultadoSecreto.Ok(s.removePrefix("sobre(").removeSuffix(")")) },
        val guarda: Boolean = true,
    ) : Biometria {
        var verificaciones = 0
        var olvidoClave = false
        override fun disponibilidad() = DisponibilidadHuella.DISPONIBLE
        override suspend fun verificar(titulo: String, detalle: String): ResultadoHuella { verificaciones++; return ResultadoHuella.Ok }
        override fun guardaClaves() = guarda
        override suspend fun cifrar(titulo: String, detalle: String, secreto: String) = alCifrar(secreto)
        override suspend fun descifrar(titulo: String, detalle: String, sobre: String) = alDescifrar(sobre)
        override fun olvidarClave() { olvidoClave = true }
    }

    @Test
    fun laContrasenaRecienteSeEntregaUnaVezYVence() {
        var ahora = 0L
        val c = ClaveReciente({ ahora }, vigenciaMs = 10 * minuto)
        c.guardar("Adiel", "Clave2026x")
        assertTrue(c.hay("adiel"), "sin importar mayúsculas")
        assertNull(c.tomar("otro"), "no se entrega a otra cuenta")
        c.guardar("adiel", "Clave2026x")
        assertEquals("Clave2026x", c.tomar("adiel"))
        assertNull(c.tomar("adiel"), "se entrega una sola vez")
        c.guardar("adiel", "Clave2026x"); ahora = 11 * minuto
        assertNull(c.tomar("adiel"), "a los 11 minutos ya venció")
    }

    @Test
    fun laCuentaConHuellaSeGuardaComoTexto() {
        val c = CuentaConHuella(7, "adiel", "Adiel Luján", "https://sigro-api.onrender.com", "aXY=:Y2lm")
        assertEquals(c, ReglaCandado.decodificarCuenta(ReglaCandado.codificarCuenta(c)))
        assertNull(ReglaCandado.decodificarCuenta(""), "borrada")
        assertNull(ReglaCandado.decodificarCuenta("7\nadiel"), "dañada")
        assertEquals("Adiel", c.primerNombre)
    }

    @Test
    fun alActivarJustoAlEntrarSeCifraLaContrasenaConLaHuella() = runTest(principal) {
        val guardado = Guardado(); val clave = ClaveReciente({ 0 })
        val vm = CandadoViewModel(Sesiones(7), guardado, reloj = { 0 }, clave = clave)
        advanceUntilIdle()
        clave.guardar("adiel", "Clave2026x")
        val huella = HuellaFalsa()
        vm.activar(huella); advanceUntilIdle()
        assertEquals("sobre(Clave2026x)", guardado.cuentaGuardada?.sobre, "se guarda solo cifrada")
        assertEquals("adiel", guardado.cuentaGuardada?.usuario)
        assertTrue(guardado.ajustes.getValue(7).activa)
        assertTrue(vm.estado.value.entraConHuella)
        assertEquals(0, huella.verificaciones, "un solo diálogo: el de cifrar")
        assertNull(clave.tomar("adiel"), "la contraseña ya no queda en memoria")
    }

    @Test
    fun sinContrasenaEnMemoriaQuedaSoloElCandado() = runTest(principal) {
        val guardado = Guardado()
        val vm = CandadoViewModel(Sesiones(7), guardado, reloj = { 0 }, clave = ClaveReciente({ 0 }))
        advanceUntilIdle()
        val huella = HuellaFalsa()
        vm.activar(huella); advanceUntilIdle()
        assertEquals(1, huella.verificaciones)
        assertNull(guardado.cuentaGuardada)
        assertTrue(guardado.ajustes.getValue(7).activa)
        assertFalse(vm.estado.value.entraConHuella)
    }

    @Test
    fun siCancelaAlCifrarPuedeIntentarloOtraVez() = runTest(principal) {
        val clave = ClaveReciente({ 0 })
        val vm = CandadoViewModel(Sesiones(7), Guardado(), reloj = { 0 }, clave = clave)
        advanceUntilIdle()
        clave.guardar("adiel", "Clave2026x")
        vm.activar(HuellaFalsa(alCifrar = { ResultadoSecreto.Cancelada })); advanceUntilIdle()
        assertTrue(clave.hay("adiel"), "la contraseña vuelve a memoria para el segundo intento")
    }

    @Test
    fun noSoyYoBorraLaContrasenaCifradaPeroCerrarSesionNo() = runTest(principal) {
        val guardado = Guardado(mutableMapOf(7 to activa)).apply { cuentaGuardada = CuentaConHuella(7, "adiel", "Adiel", "s", "x") }
        val vm = CandadoViewModel(Sesiones(7), guardado, reloj = { 0 })
        advanceUntilIdle()
        vm.cerrarSesion(); advanceUntilIdle()
        assertTrue(guardado.cuentaGuardada != null, "cerrar sesión deja la huella: es para volver a entrar con ella")
        val guardado2 = Guardado(mutableMapOf(7 to activa)).apply { cuentaGuardada = CuentaConHuella(7, "adiel", "Adiel", "s", "x") }
        val vm2 = CandadoViewModel(Sesiones(7), guardado2, reloj = { 0 })
        advanceUntilIdle()
        vm2.cerrarSesion(olvidarHuella = true); advanceUntilIdle()
        assertNull(guardado2.cuentaGuardada, "«no soy yo»: nadie más entra como él")
        assertFalse(guardado2.ajustes.getValue(7).activa)
    }

    /** Sesiones que anotan el inicio de sesión y pueden rechazar la contraseña (401). */
    private class Entradas(val acepta: Boolean = true, val sinSenal: Boolean = false) : RepositorioSesion {
        var entro: Triple<String, String, String>? = null
        override val sesion: Flow<Sesion?> = MutableStateFlow(null)
        override val obraElegida: Flow<ObraAsignada?> = MutableStateFlow(null)
        override suspend fun servidorGuardado() = "https://sigro-api.onrender.com"
        override suspend fun iniciarSesion(servidor: String, usuario: String, clave: String): Sesion {
            if (sinSenal) throw pe.sevens.sigro.dominio.ErrorSigro("sin_conexion", "Sin señal", null)
            if (!acepta) throw pe.sevens.sigro.dominio.ErrorSigro("credenciales", "Usuario o clave incorrectos.", 401)
            entro = Triple(servidor, usuario, clave)
            return Sesion(servidor, "t", 7, "Adiel", usuario, emptyList())
        }
        override suspend fun elegirObra(obra: ObraAsignada) {}
        override suspend fun cambiarDeObra() {}
        override suspend fun cerrarSesion() {}
    }

    private val cuenta = CuentaConHuella(7, "adiel", "Adiel Luján", "https://sigro-api.onrender.com", "sobre(Clave2026x)")

    @Test
    fun conLaHuellaEntraComoSiempreSinEscribirLaContrasena() = runTest(principal) {
        val entradas = Entradas(); val guardado = Guardado().apply { cuentaGuardada = cuenta }
        val vm = EntrarViewModel(entradas, guardado, ClaveReciente({ 0 }))
        advanceUntilIdle()
        assertEquals(cuenta, vm.estado.value.cuentaHuella, "la pantalla ofrece «Entrar como Adiel»")
        vm.entrarConHuella(HuellaFalsa()); advanceUntilIdle()
        assertEquals(Triple(cuenta.servidor, "adiel", "Clave2026x"), entradas.entro, "el servidor verifica la contraseña de siempre")
    }

    @Test
    fun siLaContrasenaCambioSeOlvidaLaHuellaYSePideEscrita() = runTest(principal) {
        val guardado = Guardado().apply { cuentaGuardada = cuenta }
        val vm = EntrarViewModel(Entradas(acepta = false), guardado, ClaveReciente({ 0 }))
        advanceUntilIdle()
        val huella = HuellaFalsa()
        vm.entrarConHuella(huella); advanceUntilIdle()
        assertNull(guardado.cuentaGuardada); assertTrue(huella.olvidoClave)
        assertTrue(vm.estado.value.conFormulario)
        assertEquals("adiel", vm.estado.value.usuario, "el usuario queda escrito")
        assertTrue(vm.estado.value.error!!.contains("contraseña cambió"))
    }

    @Test
    fun sinSenalLaHuellaSeQuedaParaElSiguienteIntento() = runTest(principal) {
        val guardado = Guardado().apply { cuentaGuardada = cuenta }
        val vm = EntrarViewModel(Entradas(sinSenal = true), guardado, ClaveReciente({ 0 }))
        advanceUntilIdle()
        vm.entrarConHuella(HuellaFalsa()); advanceUntilIdle()
        assertEquals(cuenta, guardado.cuentaGuardada)
        assertEquals("Sin señal", vm.estado.value.error)
    }

    @Test
    fun unaHuellaNuevaEnElCelularObligaAEscribirLaContrasena() = runTest(principal) {
        val guardado = Guardado().apply { cuentaGuardada = cuenta }
        val vm = EntrarViewModel(Entradas(), guardado, ClaveReciente({ 0 }))
        advanceUntilIdle()
        vm.entrarConHuella(HuellaFalsa(alDescifrar = { ResultadoSecreto.HuellaNueva })); advanceUntilIdle()
        assertNull(guardado.cuentaGuardada)
        assertTrue(vm.estado.value.error!!.contains("huella nueva"))
    }

    @Test
    fun siEntraOtraCuentaConSuContrasenaSeBorraLaHuellaDeLaAnterior() = runTest(principal) {
        // 25/11: la huella de Adiel (SSOMA) seguía en el celular y, al cerrar la sesión de Nicolás, pedía la de Adiel
        val guardado = Guardado(mutableMapOf(7 to AjusteHuella(activa = true, ofrecida = true))).apply { cuentaGuardada = cuenta }
        val vm = EntrarViewModel(Entradas(), guardado, ClaveReciente({ 0 }))
        advanceUntilIdle()
        val huella = HuellaFalsa()
        vm.otraCuenta(); vm.usuario("nicolas"); vm.clave("Clave2026y"); vm.entrar(huella); advanceUntilIdle()
        assertNull(guardado.cuentaGuardada, "ya no aparece «Entrar como Adiel»")
        assertTrue(huella.olvidoClave, "la contraseña cifrada de Adiel se borra del Keystore")
        assertFalse(guardado.ajustes.getValue(7).activa, "la huella de Adiel queda apagada")
        assertNull(vm.estado.value.cuentaHuella)
    }

    @Test
    fun siEntraLaMismaCuentaConSuContrasenaLaHuellaSeQueda() = runTest(principal) {
        val guardado = Guardado().apply { cuentaGuardada = cuenta }
        val vm = EntrarViewModel(Entradas(), guardado, ClaveReciente({ 0 }))
        advanceUntilIdle()
        val huella = HuellaFalsa()
        vm.conContrasena(); vm.usuario(" Adiel "); vm.clave("Clave2026x"); vm.entrar(huella); advanceUntilIdle()
        assertEquals(cuenta, guardado.cuentaGuardada)
        assertFalse(huella.olvidoClave)
    }

    @Test
    fun otraCuentaSeComparaSinMayusculasNiEspacios() {
        assertFalse(ReglaCandado.esOtraCuenta(cuenta, " ADIEL "))
        assertTrue(ReglaCandado.esOtraCuenta(cuenta, "nicolas"))
    }

    @Test
    fun entrarConContrasenaLaDejaEnMemoriaParaActivarLaHuella() = runTest(principal) {
        val clave = ClaveReciente({ 0 })
        val vm = EntrarViewModel(Entradas(), Guardado(), clave)
        advanceUntilIdle()
        vm.usuario("adiel"); vm.clave("Clave2026x"); vm.entrar(); advanceUntilIdle()
        assertTrue(clave.hay("adiel"))
        assertEquals("", vm.estado.value.clave, "la pantalla no la conserva")
    }

    @Test
    fun siNoPuedePonerLaHuellaEntraConSuContrasena() = runTest(principal) {
        val guardado = Guardado().apply { cuentaGuardada = cuenta }
        val vm = EntrarViewModel(Entradas(), guardado, ClaveReciente({ 0 }))
        advanceUntilIdle()
        vm.conContrasena()
        assertTrue(vm.estado.value.conFormulario)
        assertEquals("adiel", vm.estado.value.usuario, "su usuario ya puesto: solo escribe la contraseña")
        assertEquals(cuenta, guardado.cuentaGuardada, "la huella se queda para la próxima")
        vm.otraCuenta()
        assertEquals("", vm.estado.value.usuario, "otra cuenta: el formulario vacío")
    }

    @Test
    fun desdeElCandadoEntrarConContrasenaAbreElFormularioSinPedirHuella() = runTest(principal) {
        val clave = ClaveReciente({ 0 })
        val sesiones = Sesiones(7); val guardado = Guardado(mutableMapOf(7 to activa)).apply { cuentaGuardada = cuenta }
        val candado = CandadoViewModel(sesiones, guardado, reloj = { 0 }, clave = clave)
        advanceUntilIdle()
        candado.entrarConContrasena(); advanceUntilIdle()
        assertTrue(sesiones.cerro)
        assertEquals(cuenta, guardado.cuentaGuardada, "la huella no se borra: solo esta vez va con contraseña")
        val entrar = EntrarViewModel(Entradas(), guardado, clave)
        advanceUntilIdle()
        assertTrue(entrar.estado.value.conFormulario)
        assertEquals("adiel", entrar.estado.value.usuario)
        val otraVez = EntrarViewModel(Entradas(), guardado, clave)
        advanceUntilIdle()
        assertFalse(otraVez.estado.value.conFormulario, "la preferencia vale una sola vez")
    }

    @Test
    fun siElCelularNoPuedeAbrirLaHuellaSeDiceYSeQuedaEnLaPantalla() = runTest(principal) {
        val guardado = Guardado().apply { cuentaGuardada = cuenta }
        val vm = EntrarViewModel(Entradas(), guardado, ClaveReciente({ 0 }))
        advanceUntilIdle()
        vm.entrarConHuella(HuellaFalsa(alDescifrar = { ResultadoSecreto.Fallo("Demasiados intentos. Prueba más tarde.") })); advanceUntilIdle()
        assertEquals("Demasiados intentos. Prueba más tarde.", vm.estado.value.error)
        assertEquals(cuenta, guardado.cuentaGuardada, "un fallo del sensor no borra la huella")
        assertTrue(vm.estado.value.conFormulario, "tras un fallo se ofrece la contraseña")
        assertEquals(cuenta.usuario, vm.estado.value.usuario, "con el usuario ya escrito")
        val otro = EntrarViewModel(Entradas(), guardado, ClaveReciente({ 0 }))
        advanceUntilIdle()
        otro.entrarConHuella(HuellaFalsa(alDescifrar = { ResultadoSecreto.Cancelada })); advanceUntilIdle()
        assertFalse(otro.estado.value.conFormulario, "cancelar el diálogo deja la pantalla de la huella")
    }

    @Test
    fun alAbrirLaAppPideLaHuellaComoUnBancoConUnMinutoDeGracia() {
        val alAbrir = AjusteHuella(activa = true, minutos = 0, ofrecida = true)
        assertEquals(0, AjusteHuella().minutos, "por defecto: al abrir la app")
        assertFalse(ReglaCandado.vencido(alAbrir, 0, 99 * minuto), "con la app en uso nunca se bloquea sola")
        assertFalse(ReglaCandado.vencido(alAbrir, 0, 30_000, alVolver = true), "salió a la cámara o a WhatsApp 30 s: no molesta")
        assertTrue(ReglaCandado.vencido(alAbrir, 0, minuto, alVolver = true), "volvió al minuto: pide la huella")
        assertTrue(ReglaCandado.vencido(alAbrir, null, 0, alVolver = true), "sin último uso: pide la huella")
        assertEquals("Al abrir la app", ReglaCandado.legible(0))
        assertEquals("5 min sin usarla", ReglaCandado.legible(5))
    }

    @Test
    fun conCincoOQuinceMinutosSeBloqueaAunqueVuelvaAntes() {
        assertFalse(ReglaCandado.vencido(activa, 0, 10 * minuto, alVolver = true), "volvió a los 10 de 15: sigue abierta")
        assertTrue(ReglaCandado.vencido(activa, 0, 15 * minuto, alVolver = false), "a los 15 en primer plano: se bloquea")
    }

    @Test
    fun elAvisoDelCelularDiceSiPuedeRegistrarEnLaObra() {
        assertNull(avisoDeCelular(null), "sin datos todavía: no se muestra nada")
        assertNull(avisoDeCelular(EstadoDispositivo("x", null, visible = false)), "su rol no lo ve: no se inventa")
        assertEquals(AvisoDeCelular.AUTORIZADO, avisoDeCelular(EstadoDispositivo("x", "autorizado")))
        assertEquals(AvisoDeCelular.PENDIENTE, avisoDeCelular(EstadoDispositivo("x", "pendiente")))
        assertEquals(AvisoDeCelular.SIN_AUTORIZAR, avisoDeCelular(EstadoDispositivo("x", null)))
        assertEquals(AvisoDeCelular.SIN_AUTORIZAR, avisoDeCelular(EstadoDispositivo("x", "revocado")))
    }

    @Test
    fun alVolverALaPantallaDeEntrarSeLeeOtraVezSiHayHuella() = runTest(principal) {
        val guardado = Guardado()
        val vm = EntrarViewModel(Entradas(), guardado, ClaveReciente({ 0 }))
        advanceUntilIdle(); vm.alAparecer(); advanceUntilIdle()
        assertNull(vm.estado.value.cuentaHuella, "sin huella: usuario y contraseña")
        guardado.cuentaGuardada = cuenta // activó la huella y cerró sesión: el mismo ViewModel vuelve a mostrarse
        vm.alAparecer(); advanceUntilIdle()
        assertEquals(cuenta, vm.estado.value.cuentaHuella, "ofrece «Entrar con tu huella» sin cerrar la app")
        guardado.cuentaGuardada = null // apagó la huella
        vm.alAparecer(); advanceUntilIdle()
        assertNull(vm.estado.value.cuentaHuella, "ya no ofrece la huella")
    }
}
