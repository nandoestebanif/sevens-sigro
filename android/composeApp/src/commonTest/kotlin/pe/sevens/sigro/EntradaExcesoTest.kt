package pe.sevens.sigro

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.datos.repositorio.BovedaSesion
import pe.sevens.sigro.datos.repositorio.RepositorioObraImpl
import pe.sevens.sigro.datos.repositorio.RepositorioRegistrosImpl
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.ExcesoLinea
import pe.sevens.sigro.dominio.ExcesoRequerido
import pe.sevens.sigro.dominio.LineaCompra
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.MotivoExceso
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RegistroLocal
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.ResultadoSincronizacion
import pe.sevens.sigro.dominio.TipoRegistro
import pe.sevens.sigro.dominio.VerificacionRecepcion
import pe.sevens.sigro.ui.pantallas.EntradaViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * HU-36 (D11, T-298) en la app: antes de guardar la entrada se pregunta al servidor si se pasa lo requerido en el
 * S10; si se pasa, se avisa con sus cifras y se pide el motivo (avisa, no bloquea). Sin base ni servidor.
 * Ejemplo del lienzo: clavo, requerido 60 kg, ya recibido 55 kg, con esta entrada 65 kg.
 */

private val clavo = LineaCompra("0230010", "CLAVO 1 1/2\" ESTRIADO", "10", "kg", insumoId = 9, pendiente = "10")
private val acero = LineaCompra("0203010", "ACERO CORRUGADO FY=4200 1/2\" x 9 m", "10", "varillas", insumoId = 5, pendiente = "10")
private val compra = CompraParaRecibir(20, "Ferretería", "F001-4498", "2026-11-10", listOf(clavo, acero))

private val excesoClavo = VerificacionRecepcion(
    hayExceso = true,
    lineas = listOf(
        ExcesoLinea(9, "0230010", "kg", "60.0000", "55.0000", "65.0000", "5.0000", excede = true),
        ExcesoLinea(5, "0203010", "kg", "36357.0000", "0.0000", "89.4000", null, excede = false),
    ),
)

/** Lo que el doble responde a la verificación. */
private sealed interface Respuesta {
    data class Ok(val v: VerificacionRecepcion?) : Respuesta
    data class Falla(val e: ErrorSigro) : Respuesta
}

private class ObraFalsa(var respuesta: Respuesta) : RepositorioObra {
    val consultas = mutableListOf<List<Pair<Int, String>>?>()
    override suspend fun almacenes(obraId: Int) = listOf(AlmacenObra(3, "Principal"))
    override suspend fun almacenRecordado(obraId: Int): AlmacenObra? = null
    override suspend fun recordarAlmacen(obraId: Int, almacen: AlmacenObra) {}
    override suspend fun compras(obraId: Int) = listOf(compra)
    override suspend fun conPendientes(obraId: Int, compra: CompraParaRecibir) = compra
    override suspend fun obrasDestino(obraId: Int) = emptyList<ObraAsignada>()
    override suspend fun verificarRecepcion(obraId: Int, compraId: Int, lineas: List<Pair<Int, String>>?): VerificacionRecepcion? {
        consultas += lineas
        return when (val r = respuesta) {
            is Respuesta.Ok -> r.v
            is Respuesta.Falla -> throw r.e
        }
    }
}

private class RegistrosFalsos : RepositorioRegistros {
    val guardados = mutableListOf<RegistroAlmacen>()
    override fun deObra(obraId: Int): Flow<List<RegistroLocal>> = flowOf(emptyList())
    override val pendientes: Flow<Int> = flowOf(0)
    override suspend fun registrarAsistencia(obraId: Int, fecha: String, marcas: List<MarcaAsistencia>) = emptyList<RegistroLocal>()
    override suspend fun yaMarcados(obraId: Int, fecha: String) = emptySet<Int>()
    override suspend fun registrarAlmacen(obraId: Int, registro: RegistroAlmacen): RegistroLocal {
        guardados += registro
        return RegistroLocal("u1", obraId, registro.tipo, "", "", 1L, EstadoEnvio.PENDIENTE, null, null, 0)
    }
    override suspend fun sincronizar() = ResultadoSincronizacion(0, 0, 0)
}

class EntradaExcesoTest {
    @BeforeTest fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm(obra: ObraFalsa, registros: RegistrosFalsos): EntradaViewModel {
        val vm = EntradaViewModel(obra, registros, object : ProgramadorSincronizacion { override fun programar() {} })
        vm.cargar(1)
        vm.elegir(1, compra)
        return vm
    }

    // ── dominio ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun hastaLoRequeridoBajaSoloLaLineaQueSePasa() {
        val recibido = mapOf(0 to "10", 1 to "10")
        assertEquals(mapOf(0 to "5"), ExcesoRequerido.hastaLoRequerido(compra.lineas, recibido, excesoClavo), "el clavo: 10 − 5 = 5 kg; el acero no se toca")
    }

    @Test
    fun hastaLoRequeridoConvierteConElFactorDeLaRespuestaYRedondeaHaciaAbajo() {
        // acero: 10 varillas = 89.4 kg; requerido 60 kg ⇒ exceso 29.4 kg = 3.2886 varillas ⇒ 6.71 (nunca 6.72: se pasaría)
        val v = VerificacionRecepcion(true, listOf(ExcesoLinea(5, "0203010", "kg", "60.0000", "0.0000", "89.4000", "29.4000", true)))
        assertEquals(mapOf(1 to "6.71"), ExcesoRequerido.hastaLoRequerido(compra.lineas, mapOf(0 to "", 1 to "10"), v))
    }

    @Test
    fun hastaLoRequeridoNuncaQuedaNegativo() {
        val v = VerificacionRecepcion(true, listOf(ExcesoLinea(9, "0230010", "kg", "50", "60", "65", "15", true)))
        assertEquals(mapOf(0 to "0"), ExcesoRequerido.hastaLoRequerido(compra.lineas, mapOf(0 to "5"), v), "ya se había pasado antes: esta entrega no suma")
    }

    @Test
    fun elMotivoOtroExigeTexto() {
        assertEquals("Cambio pedido por el residente", ExcesoRequerido.motivo(MotivoExceso.CAMBIO, null))
        assertNull(ExcesoRequerido.motivo(MotivoExceso.OTRO, "   "))
        assertEquals("Lo pidió SSOMA", ExcesoRequerido.motivo(MotivoExceso.OTRO, " Lo pidió SSOMA "))
        assertNull(ExcesoRequerido.motivo(null, "algo"))
        assertEquals(300, ExcesoRequerido.motivo(MotivoExceso.OTRO, "x".repeat(400))!!.length, "la API acepta hasta 300")
    }

    @Test
    fun elAvisoMuestraLasCifrasDelServidor() {
        assertEquals(
            "Clavo: requerido en el S10 60 kg · ya recibido 55 kg · con esta entrada 65 kg (5 kg de más)",
            ExcesoRequerido.detalle(excesoClavo.lineas[0], "Clavo"),
        )
    }

    // ── ViewModel ───────────────────────────────────────────────────────────────────────────

    @Test
    fun siSePasaLoRequeridoAvisaYNoGuardaHastaTenerElMotivo() = runTest {
        val obra = ObraFalsa(Respuesta.Ok(excesoClavo))
        val reg = RegistrosFalsos()
        val vm = vm(obra, reg)

        vm.guardar(1)
        assertNotNull(vm.estado.value.exceso, "el servidor dijo que se pasa: se avisa")
        assertTrue(reg.guardados.isEmpty(), "todavía no se guarda")
        assertEquals(listOf<List<Pair<Int, String>>?>(null), obra.consultas, "«llegó todo» se verifica sin líneas, igual que se recibirá")

        vm.guardar(1)
        assertEquals(EntradaViewModel.ERROR_MOTIVO, vm.estado.value.errores.keys.single(), "sin motivo no se guarda")
        assertTrue(reg.guardados.isEmpty())

        vm.motivo(MotivoExceso.S10_CORTO)
        vm.guardar(1)
        val e = reg.guardados.single() as RegistroAlmacen.Entrada
        assertEquals("El S10 se quedó corto", e.motivoExceso)
        assertTrue(vm.estado.value.guardada)
        assertEquals(1, obra.consultas.size, "con el aviso ya dado no se vuelve a preguntar")
    }

    @Test
    fun cambiarUnaCantidadBorraElAvisoYSeVuelveAVerificar() = runTest {
        val obra = ObraFalsa(Respuesta.Ok(excesoClavo))
        val vm = vm(obra, RegistrosFalsos())
        vm.guardar(1)
        vm.alternarModo() // llegó una parte
        assertNull(vm.estado.value.exceso)
        vm.recibido(0, "4")
        vm.recibido(1, "10")
        obra.respuesta = Respuesta.Ok(VerificacionRecepcion(false, emptyList()))
        vm.guardar(1)
        assertEquals(listOf(9 to "4", 5 to "10"), obra.consultas.last(), "una parte viaja con insumo y cantidad en unidad de compra")
        assertTrue(vm.estado.value.guardada)
    }

    @Test
    fun recibirSoloHastaLoRequeridoPasaAUnaParte() = runTest {
        val vm = vm(ObraFalsa(Respuesta.Ok(excesoClavo)), RegistrosFalsos())
        vm.guardar(1)
        vm.hastaLoRequerido()
        val s = vm.estado.value
        assertFalse(s.llegoTodo)
        assertEquals("5", s.recibido[0], "clavo hasta lo requerido")
        assertEquals("10", s.recibido[1], "el acero queda igual")
        assertNull(s.exceso, "se vuelve a verificar al guardar")
    }

    @Test
    fun sinPasarseSeGuardaSinMotivo() = runTest {
        val reg = RegistrosFalsos()
        val vm = vm(ObraFalsa(Respuesta.Ok(VerificacionRecepcion(false, emptyList()))), reg)
        vm.guardar(1)
        assertNull((reg.guardados.single() as RegistroAlmacen.Entrada).motivoExceso)
        assertFalse(vm.estado.value.sinVerificar)
    }

    @Test
    fun sinSenalSeGuardaIgualYSeDice() = runTest {
        val reg = RegistrosFalsos()
        val vm = vm(ObraFalsa(Respuesta.Falla(ErrorSigro("sin_conexion", "sin señal"))), reg)
        vm.guardar(1)
        assertEquals(1, reg.guardados.size, "regla 7 y HU-37: sin señal no se bloquea")
        assertTrue(vm.estado.value.sinVerificar)
    }

    @Test
    fun unRechazoDelServidorNoSeGuarda() = runTest {
        val reg = RegistrosFalsos()
        val msg = "0230010: se compró 10 kg, ya se recibió 8 y quedan 2; no se puede recibir 10."
        val vm = vm(ObraFalsa(Respuesta.Falla(ErrorSigro("recepcion_excede_compra", msg, 422))), reg)
        vm.guardar(1)
        assertTrue(reg.guardados.isEmpty(), "pasar lo COMPRADO sí se bloquea (HU-35)")
        assertEquals(msg, vm.estado.value.errores[-1])
    }

    // ── datos: API y cuerpo de la cola ──────────────────────────────────────────────────────

    private class Servidor(val estado: HttpStatusCode, val cuerpo: String) {
        val rutas = mutableListOf<String>()
        val cuerpos = mutableListOf<String>()
        val seguro = AlmacenSeguroEnMemoria(mapOf(BovedaSesion.CLAVE_TOKEN to "tkn"))
        val cred = credencialesDePrueba(AjustesDePrueba(), seguro)
        private val motor = MockEngine { req ->
            rutas += req.url.encodedPath
            cuerpos += req.body.toByteArray().decodeToString()
            respond(cuerpo, estado, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = ApiSigro(crearClienteHttp(motor) { seguro.leer(BovedaSesion.CLAVE_TOKEN) }, jsonSigro)
        val obra = RepositorioObraImpl(api, cred)
    }

    @Test
    fun laVerificacionLlamaAlServidorYLeeSuRespuesta() = runTest {
        val s = Servidor(
            HttpStatusCode.OK,
            """{"compra_id":20,"hay_exceso":true,"lineas":[{"insumo_id":9,"insumo_codigo":"0230010","unidad":"kg","requerido":"60.0000",
               "ya_recibido":"55.0000","con_esta":"65.0000","exceso":"5.0000","excede":true}]}""",
        )
        val v = s.obra.verificarRecepcion(1, 20, listOf(9 to "10,5"))!!
        assertEquals("/api/v1/obras/1/almacen/recepciones/verificacion", s.rutas.single())
        val cuerpo = jsonSigro.parseToJsonElement(s.cuerpos.single()).jsonObject
        assertEquals(20, cuerpo["compra_id"]!!.jsonPrimitive.int)
        assertEquals("10.5", cuerpo["lineas"]!!.jsonArray.single().jsonObject["cantidad"]!!.jsonPrimitive.content, "coma decimal normalizada")
        assertTrue(v.hayExceso)
        assertEquals("5.0000", v.excedidas.single().exceso)
    }

    @Test
    fun unServidorSinHu36NoBloquea() = runTest {
        val s = Servidor(HttpStatusCode.NotFound, """{"detail":"Not Found"}""")
        assertNull(s.obra.verificarRecepcion(1, 20, null))
    }

    @Test
    fun elMotivoViajaComoMotivoExcesoSoloSiSePasa() {
        val con = RepositorioRegistrosImpl.cuerpoAlmacen("u", 1L, "cel", RegistroAlmacen.Entrada(3, compra, emptyList(), todo = true, motivoExceso = "El S10 se quedó corto"))
        assertEquals("El S10 se quedó corto", con["motivo_exceso"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, con["lineas"])
        val sin = RepositorioRegistrosImpl.cuerpoAlmacen("u", 1L, "cel", RegistroAlmacen.Entrada(3, compra, emptyList(), todo = true))
        assertFalse("motivo_exceso" in sin, "sin exceso el cuerpo queda igual que antes (mismo reenvío)")
        assertEquals(TipoRegistro.ENTRADA_COMPRA, RegistroAlmacen.Entrada(3, compra, emptyList(), true).tipo)
    }
}
