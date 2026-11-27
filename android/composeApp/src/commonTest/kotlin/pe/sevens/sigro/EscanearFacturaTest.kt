package pe.sevens.sigro

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.datos.repositorio.BovedaSesion
import pe.sevens.sigro.datos.repositorio.RepositorioObraImpl
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.Cotejo
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.LineaCompra
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.ResultadoCotejo
import pe.sevens.sigro.ui.pantallas.EntradaViewModel
import pe.sevens.sigro.ui.pantallas.EscanearViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* HU-73 · escanear la factura: con señal manda el servidor; sin señal se coteja en el celular con lo que se bajó. */

private const val QR = "20999999990|01|F001|00001234|180.00|1180.00|2026-11-20|6|20606456892|"
private val compraQr = CompraParaRecibir(
    12, "Proveedor de prueba", "factura F001-1234", "2026-11-20", listOf(LineaCompra("1151", "CEMENTO", "10", "bls")),
    proveedorRuc = "20999999990", total = "1180.00", igv = "180.00", tipoComprobante = "factura", numeroComprobante = "F001-1234",
)

private class ObraQr(var cotejo: () -> Cotejo?) : RepositorioObra {
    var llamadas = 0
    override suspend fun almacenes(obraId: Int) = listOf(AlmacenObra(3, "Almacén principal"))
    override suspend fun almacenRecordado(obraId: Int): AlmacenObra? = null
    override suspend fun recordarAlmacen(obraId: Int, almacen: AlmacenObra) {}
    override suspend fun compras(obraId: Int) = listOf(compraQr)
    override suspend fun conPendientes(obraId: Int, compra: CompraParaRecibir) = compra
    override suspend fun obrasDestino(obraId: Int) = emptyList<ObraAsignada>()
    override suspend fun cotejarQr(obraId: Int, texto: String): Cotejo? { llamadas++; return cotejo() }
}

class EscanearFacturaTest {
    @BeforeTest fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun despues() = Dispatchers.resetMain()

    @Test
    fun sinSenalSeCotejaEnElCelularYSeOfreceRecibir() {
        val vm = EscanearViewModel(ObraQr { throw ErrorSigro("sin_conexion", "sin señal") })
        vm.leido(1, QR, listOf(compraQr))
        val r = assertNotNull(vm.estado.value.resultado)
        assertEquals(ResultadoCotejo.RECIBIR, r.cotejo.resultado)
        assertEquals(compraQr, r.compra)
        assertTrue(r.enElCelular, "se dice que lo confirma el servidor después")
    }

    @Test
    fun conSenalMandaElServidorAunqueElCelularDijeraOtraCosa() {
        val vm = EscanearViewModel(ObraQr { Cotejo(ResultadoCotejo.YA_RECIBIDA, 12) })
        vm.leido(1, QR, listOf(compraQr))
        val r = vm.estado.value.resultado!!
        assertEquals(ResultadoCotejo.YA_RECIBIDA, r.cotejo.resultado)
        assertNull(r.compra, "no se ofrece recibir")
        assertFalse(r.enElCelular)
    }

    @Test
    fun unServidorSinElCotejoNoBloquea() {
        val vm = EscanearViewModel(ObraQr { null })
        vm.leido(1, QR, listOf(compraQr))
        assertEquals(ResultadoCotejo.RECIBIR, vm.estado.value.resultado!!.cotejo.resultado)
    }

    @Test
    fun unQrQueNoEsFacturaSeAvisaSinLlamarAlServidor() {
        val obra = ObraQr { error("no debió llamarse") }
        val vm = EscanearViewModel(obra)
        vm.leido(1, "https://ejemplo.pe/promo", listOf(compraQr))
        assertEquals(0, obra.llamadas)
        assertNotNull(vm.estado.value.aviso)
        assertNull(vm.estado.value.resultado)
    }

    @Test
    fun elMismoQrLeidoVariasVecesSeAtiendeUnaSola() {
        val obra = ObraQr { Cotejo(ResultadoCotejo.RECIBIR, 12) }
        val vm = EscanearViewModel(obra)
        repeat(5) { vm.leido(1, QR, listOf(compraQr)) }
        assertEquals(1, obra.llamadas)
        vm.otra()
        vm.leido(1, QR, listOf(compraQr))
        assertEquals(2, obra.llamadas, "«Escanear otra» permite volver a leerlo")
    }

    @Test
    fun siElServidorLoRechazaComoQrSeAvisaYSeSigueEscaneando() {
        val vm = EscanearViewModel(ObraQr { throw ErrorSigro("qr_no_reconocido", "No es el QR de una factura electrónica.", 422) })
        vm.leido(1, QR, listOf(compraQr))
        assertNull(vm.estado.value.resultado)
        assertEquals("No es el QR de una factura electrónica.", vm.estado.value.aviso)
    }

    @Test
    fun unaFacturaDeOtroRucNoSeRecibe() {
        val vm = EscanearViewModel(ObraQr { throw ErrorSigro("sin_conexion", "sin señal") })
        vm.leido(1, "20999999990|01|F001|00001234|180.00|1180.00|2026-11-20|6|20888888889|", listOf(compraQr))
        val r = vm.estado.value.resultado!!
        assertEquals(ResultadoCotejo.NO_ES_DE_SEVENS, r.cotejo.resultado)
        assertTrue(EscanearViewModel.explicar(r).second.contains("20888888889"))
    }

    @Test
    fun entradaAbreLaCompraEscaneadaConSusAvisosYLosOlvidaAlCambiar() = runTest {
        val vm = EntradaViewModel(ObraQr { null }, RegistrosDePrueba().let { pe.sevens.sigro.datos.repositorio.RepositorioRegistrosImpl(
            ApiSigro(crearClienteHttp(MockEngine { throw RuntimeException("sin señal") }) { null }, jsonSigro), it,
            credencialesDePrueba(AjustesDePrueba())) { 0L } }, object : ProgramadorSincronizacion { override fun programar() {} })
        vm.escanear()
        assertTrue(vm.estado.value.escaneando)
        vm.recibirEscaneada(1, compraQr, listOf("El total no cuadra."))
        val e = vm.estado.value
        assertFalse(e.escaneando)
        assertEquals(12, e.compra?.id)
        assertTrue(e.porQr)
        assertEquals(listOf("El total no cuadra."), e.avisosFactura)
        vm.cambiarCompra()
        assertTrue(vm.estado.value.avisosFactura.isEmpty())
    }

    // ── datos: el contrato con el servidor ───────────────────────────────────────────────────

    private class Servidor(val responder: (String) -> Pair<HttpStatusCode, String>) {
        val rutas = mutableListOf<String>()
        val cuerpos = mutableListOf<String>()
        val seguro = AlmacenSeguroEnMemoria(mapOf(BovedaSesion.CLAVE_TOKEN to "tkn"))
        private val motor = MockEngine { req ->
            rutas += req.url.encodedPath
            cuerpos += req.body.toByteArray().decodeToString()
            val (st, cuerpo) = responder(req.url.encodedPath)
            respond(cuerpo, st, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val obra = RepositorioObraImpl(ApiSigro(crearClienteHttp(motor) { seguro.leer(BovedaSesion.CLAVE_TOKEN) }, jsonSigro), credencialesDePrueba(AjustesDePrueba(), seguro))
    }

    @Test
    fun elCotejoDelServidorSeLeeSegunElContrato() = runTest {
        val s = Servidor { HttpStatusCode.OK to """{"comprobante":{"numero_comprobante":"F001-1234"},"resultado":"recibir_con_avisos",
            "compra":{"id":12,"estado":"registrada","total":"1062.00","queda_por_recibir":true},"avisos":["El total no cuadra."]}""" }
        val c = s.obra.cotejarQr(1, QR)!!
        assertEquals("/api/v1/obras/1/compras/cotejar-qr", s.rutas.single())
        assertEquals(QR, jsonSigro.parseToJsonElement(s.cuerpos.single()).jsonObject["qr"]!!.jsonPrimitive.content, "se manda el texto tal cual")
        assertEquals(Cotejo(ResultadoCotejo.RECIBIR_CON_AVISOS, 12, listOf("El total no cuadra.")), c)
    }

    @Test
    fun unServidorSinElEndpointDevuelveNulo() = runTest {
        assertNull(Servidor { HttpStatusCode.NotFound to """{"detail":"Not Found"}""" }.obra.cotejarQr(1, QR))
    }

    @Test
    fun lasComprasTraenRucYMontosAunqueVenganComoNumero() = runTest {
        val s = Servidor { ruta ->
            if (ruta.endsWith("/compras")) HttpStatusCode.OK to """[{"id":12,"proveedor_razon_social":"Proveedor de prueba","proveedor_ruc":"20999999990",
                "tipo_comprobante":"factura","numero_comprobante":"F001-1234","fecha":"2026-11-20","estado":"registrada",
                "totales":{"base":1000.00,"igv":180.00,"total":1180.00},"lineas":[]}]"""
            else HttpStatusCode.NotFound to "{}"
        }
        val c = s.obra.compras(1).single()
        assertEquals("20999999990", c.proveedorRuc)
        assertEquals("F001-1234", c.numeroComprobante)
        assertEquals(118000L, pe.sevens.sigro.dominio.QrFactura.centimos(c.total))
        assertEquals(18000L, pe.sevens.sigro.dominio.QrFactura.centimos(c.igv))
    }
}
