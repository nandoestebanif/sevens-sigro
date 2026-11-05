package pe.sevens.sigro

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
import pe.sevens.sigro.datos.repositorio.RepositorioSalidasImpl
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.ElegirAlmacen
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.EstadoLineaRecepcion
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.LineaCompra
import pe.sevens.sigro.dominio.NuevaSalida
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.TipoRegistro
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.ui.pantallas.SelectorAlmacen
import pe.sevens.sigro.ui.pantallas.unirRegistros
import pe.sevens.sigro.ui.pantallas.validarRecepcion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * M6 (elegir almacén y mostrar el `mensaje` de los rechazos) y M5 (entrada de compra conectada por la
 * cola sin señal), sin base ni servidor.
 */

private class Servidor(val responder: (ruta: String, n: Int) -> Pair<HttpStatusCode, String>?) {
    val rutas = mutableListOf<String>()
    val cuerpos = mutableListOf<String>()
    val ajustes = AjustesDePrueba()
    val seguro = AlmacenSeguroEnMemoria(mapOf(BovedaSesion.CLAVE_TOKEN to "tkn"))
    val cred = credencialesDePrueba(ajustes, seguro)
    private val motor = MockEngine { req ->
        rutas += req.url.encodedPath
        cuerpos += req.body.toByteArray().decodeToString()
        val r = responder(req.url.encodedPath, rutas.size) ?: throw RuntimeException("sin señal")
        respond(r.second, r.first, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val api = ApiSigro(crearClienteHttp(motor) { seguro.leer(BovedaSesion.CLAVE_TOKEN) }, jsonSigro)
    val registrosDao = RegistrosDePrueba()
    val registros = RepositorioRegistrosImpl(api, registrosDao, cred) { 9_000L }
    val salidasDao = SalidasDePrueba()
    val salidas = RepositorioSalidasImpl(api, salidasDao, cred) { 9_000L }
    val obra = RepositorioObraImpl(api, cred)
    fun ultimoCuerpo(): JsonObject = jsonSigro.parseToJsonElement(cuerpos.last()).jsonObject
}

private val cemento = LineaCompra("1151", "CEMENTO PORTLAND TIPO I", "100", "bls")
private val arena = LineaCompra("423", "ARENA GRUESA", "6", "m3")
private val compra = CompraParaRecibir(10, "Ferretería", "F001-1", "2026-11-01", listOf(cemento, arena))
private val conPendiente = compra.copy(lineas = listOf(cemento.copy(insumoId = 71, pendiente = "40"), arena.copy(insumoId = 42, pendiente = "6")))

private class ObraEnMemoria(var lista: List<AlmacenObra>?, var recordado: AlmacenObra? = null) : RepositorioObra {
    override suspend fun almacenes(obraId: Int) = lista ?: throw ErrorSigro("sin_conexion", "sin señal")
    override suspend fun almacenRecordado(obraId: Int) = recordado
    override suspend fun recordarAlmacen(obraId: Int, almacen: AlmacenObra) { recordado = almacen }
    override suspend fun compras(obraId: Int) = listOf(compra)
    override suspend fun conPendientes(obraId: Int, compra: CompraParaRecibir) = compra
    override suspend fun obrasDestino(obraId: Int) = emptyList<ObraAsignada>()
}

private val principal = AlmacenObra(3, "Almacén principal")
private val patio = AlmacenObra(4, "Patio de fierro")

class AlmacenEntradaTest {

    // ── M6 · almacén ────────────────────────────────────────────────────────────────────────

    @Test
    fun conUnSoloAlmacenSeEligeSolo() {
        assertEquals(principal, ElegirAlmacen.inicial(listOf(principal), null))
        assertFalse(ElegirAlmacen.falta(listOf(principal), principal))
    }

    @Test
    fun conVariosSeUsaElUltimoElegidoOSePregunta() {
        assertEquals(patio, ElegirAlmacen.inicial(listOf(principal, patio), patio))
        assertNull(ElegirAlmacen.inicial(listOf(principal, patio), null))
        assertNull(ElegirAlmacen.inicial(listOf(principal, patio), AlmacenObra(99, "Cerrado")), "un almacén que ya no está activo no se usa")
        assertTrue(ElegirAlmacen.falta(listOf(principal, patio), null))
    }

    @Test
    fun sinSenalSeUsaElUltimoElegido() {
        assertEquals(patio, ElegirAlmacen.inicial(null, patio))
        assertNull(ElegirAlmacen.inicial(null, null))
    }

    @Test
    fun elSelectorRecuerdaElAlmacenParaUsarloSinSenal() = runTest {
        val obra = ObraEnMemoria(listOf(principal))
        SelectorAlmacen(obra, this).cargar(1)
        advanceUntilIdle()
        assertEquals(principal, obra.recordado, "con señal y un solo almacén: se elige y se guarda")

        obra.lista = null // sin señal
        val sinSenal = SelectorAlmacen(obra, this)
        sinSenal.cargar(1)
        advanceUntilIdle()
        assertEquals(principal, sinSenal.estado.value.elegido)
        assertTrue(sinSenal.estado.value.sinLista)
    }

    @Test
    fun soloSeOfrecenAlmacenesActivosYSeRecuerdanPorObra() = runTest {
        val s = Servidor { _, _ ->
            HttpStatusCode.OK to """[{"id":3,"nombre":"Principal","tipo":"principal","estado":"activo"},{"id":5,"nombre":"Viejo","tipo":"secundario","estado":"cerrado"}]"""
        }
        assertEquals(listOf(AlmacenObra(3, "Principal")), s.obra.almacenes(1))
        assertEquals("/api/v1/obras/1/almacenes", s.rutas.single())
        s.obra.recordarAlmacen(1, AlmacenObra(3, "Principal"))
        assertEquals(AlmacenObra(3, "Principal"), s.obra.almacenRecordado(1))
        assertNull(s.obra.almacenRecordado(2), "cada obra con el suyo")
    }

    @Test
    fun laSalidaViajaConSuAlmacenYSuUnidadDeCompra() = runTest {
        val s = Servidor { _, _ -> HttpStatusCode.Created to "{}" }
        s.salidas.registrar(NuevaSalida(1, Insumo(7, "C-1", "Cemento", "bls"), "2,5", "Losa", "Juan Pérez", null, almacenId = 3))
        s.salidas.sincronizar()
        val c = s.ultimoCuerpo()
        assertEquals(3, c["almacen_id"]!!.jsonPrimitive.int)
        assertEquals("bls", c["unidad"]!!.jsonPrimitive.content, "el servidor convierte con el factor del insumo")
        assertEquals("2.5", c["cantidad"]!!.jsonPrimitive.content)
    }

    @Test
    fun un422QuedaRechazadoConElMensajeDelServidorEnMisRegistros() = runTest {
        val mensaje = "Solo hay 10 bls de CEMENTO en Almacén principal."
        val s = Servidor { _, _ -> HttpStatusCode.UnprocessableEntity to """{"codigo":"stock_insuficiente","mensaje":"$mensaje"}""" }
        val r = s.salidas.registrar(NuevaSalida(1, Insumo(7, "C-1", "Cemento", "bls"), "20", "Losa", "Juan Pérez", null, 3))
        assertEquals(1, s.salidas.sincronizar().rechazados)
        val filas = unirRegistros(s.salidas.salidasDeObra(1).first(), emptyList())
        assertEquals(EstadoEnvio.RECHAZADO, filas.single().estado)
        assertEquals(mensaje, filas.single().motivoRechazo, "«Mis registros» muestra el mensaje tal cual lo dio el servidor")
        assertEquals(mensaje, s.salidasDao.una(r.uuidCliente).motivoRechazo)
    }

    @Test
    fun un404ConCodigoEsRechazoYUnoSinCodigoEsRutaQueFalta() = runTest {
        val conCodigo = Servidor { _, _ -> HttpStatusCode.NotFound to """{"codigo":"almacen_no_existe","mensaje":"Ese almacén no existe o no está activo en esa obra."}""" }
        val a = conCodigo.salidas.registrar(NuevaSalida(1, Insumo(7, "C-1", "Cemento", "bls"), "2", "Losa", "Juan Pérez", null, 99))
        conCodigo.salidas.sincronizar()
        assertEquals("RECHAZADO", conCodigo.salidasDao.una(a.uuidCliente).estado)
        assertEquals("Ese almacén no existe o no está activo en esa obra.", conCodigo.salidasDao.una(a.uuidCliente).motivoRechazo)

        val sinRuta = Servidor { _, _ -> HttpStatusCode.NotFound to """{"detail":"Not Found"}""" }
        val b = sinRuta.salidas.registrar(NuevaSalida(1, Insumo(7, "C-1", "Cemento", "bls"), "2", "Losa", "Juan Pérez", null, 3))
        sinRuta.salidas.sincronizar()
        assertEquals("PENDIENTE", sinRuta.salidasDao.una(b.uuidCliente).estado)
    }

    // ── M5 · entrada de compra ──────────────────────────────────────────────────────────────

    @Test
    fun elPendienteDelServidorSeUneALasLineasDeLaCompra() = runTest {
        val s = Servidor { _, _ ->
            HttpStatusCode.OK to """[{"insumo_id":71,"unidad_compra":"bls","pedido":"100.0000","recibido":"60.0000","pendiente":"40.0000"},
                {"insumo_id":42,"unidad_compra":"m3","pedido":"6.0000","recibido":"0.0000","pendiente":"6.0000"}]"""
        }
        val c = s.obra.conPendientes(1, compra)
        assertEquals("/api/v1/obras/1/almacen/compras/10/recepcion", s.rutas.single())
        assertEquals(listOf(71, 42), c.lineas.map { it.insumoId })
        assertEquals("40.0000", c.lineas[0].pendiente)
        assertTrue(c.conPendientes)
    }

    @Test
    fun siLasListasNoCalzanNoSeAdivina() {
        val otraUnidad = listOf(EstadoLineaRecepcion(71, "kg", "100", "0", "100"), EstadoLineaRecepcion(42, "m3", "6", "0", "6"))
        assertEquals(compra.lineas, ElegirAlmacen.unirPendientes(compra.lineas, otraUnidad))
        assertEquals(compra.lineas, ElegirAlmacen.unirPendientes(compra.lineas, otraUnidad.take(1)))
    }

    @Test
    fun noSeRecibeMasDeLoPendiente() {
        assertEquals(setOf(0), validarRecepcion(conPendiente, mapOf(0 to "41", 1 to "6")).keys)
        assertTrue(validarRecepcion(conPendiente, mapOf(0 to "40", 1 to "6")).isEmpty())
        assertTrue(validarRecepcion(compra, mapOf(0 to "100")).isEmpty(), "sin pendiente se usa lo comprado como tope")
    }

    @Test
    fun llegoTodoViajaSinLineasALaRutaDeRecepcionesConSuUuid() = runTest {
        val s = Servidor { _, n -> if (n == 1) null else HttpStatusCode.Created to "{}" }
        val r = s.registros.registrarAlmacen(1, RegistroAlmacen.Entrada(3, conPendiente, conPendiente.lineas.map { it to it.recibible }, todo = true))
        assertEquals(TipoRegistro.ENTRADA_COMPRA, r.tipo)
        assertNull(r.nota, "ya tiene servicio: no lleva la nota de «se envía cuando exista»")
        assertEquals(1, s.registros.pendientes.first(), "cuenta como pendiente: el trabajo de fondo la envía")

        assertEquals(1, s.registros.sincronizar().siguenPendientes, "sin señal queda en la cola")
        assertEquals(1, s.registros.sincronizar().enviados)
        assertEquals("/api/v1/obras/1/almacen/recepciones", s.rutas.last())
        assertEquals(s.cuerpos[0], s.cuerpos[1], "regla 3: el reenvío manda el mismo cuerpo con el mismo uuid")

        val c = s.ultimoCuerpo()
        assertEquals(r.uuidCliente, c["uuid_cliente"]!!.jsonPrimitive.content)
        assertEquals(10, c["compra_id"]!!.jsonPrimitive.int)
        assertEquals(3, c["almacen_id"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, c["lineas"], "sin líneas = el servidor recibe todo lo pendiente")
        assertTrue(c["dispositivo"]!!.jsonPrimitive.content.startsWith("sigro-"), "regla 5: viaja el código del celular")
        assertEquals(
            setOf("uuid_cliente", "hora_dispositivo_ms", "dispositivo", "compra_id", "almacen_id", "lineas"), c.keys,
            "RecepcionIn rechaza campos de más",
        )
        assertEquals("ENVIADO", s.registrosDao.una(r.uuidCliente).estado)
    }

    @Test
    fun recibirUnaParteLlevaElInsumoDeCadaLinea() = runTest {
        val s = Servidor { _, _ -> HttpStatusCode.Created to "{}" }
        s.registros.registrarAlmacen(1, RegistroAlmacen.Entrada(null, conPendiente, listOf(conPendiente.lineas[0] to "12,5"), todo = false))
        s.registros.sincronizar()
        val lineas = s.ultimoCuerpo()["lineas"]!!.jsonArray
        assertEquals(1, lineas.size)
        assertEquals(71, lineas[0].jsonObject["insumo_id"]!!.jsonPrimitive.int)
        assertEquals("12.5", lineas[0].jsonObject["cantidad"]!!.jsonPrimitive.content)
    }

    @Test
    fun laEntradaRechazadaMuestraElMensajeDelServidor() = runTest {
        val mensaje = "1151: se compró 100 bls, ya se recibió 100 y quedan 0; no se puede recibir 5."
        val s = Servidor { _, _ -> HttpStatusCode.UnprocessableEntity to """{"codigo":"recepcion_excede_compra","mensaje":"$mensaje"}""" }
        val r = s.registros.registrarAlmacen(1, RegistroAlmacen.Entrada(3, conPendiente, listOf(conPendiente.lineas[0] to "5"), todo = false))
        assertEquals(1, s.registros.sincronizar().rechazados)
        val fila = unirRegistros(emptyList(), s.registros.deObra(1).first()).single()
        assertEquals(EstadoEnvio.RECHAZADO, fila.estado)
        assertEquals(mensaje, fila.motivoRechazo)
        assertEquals(1, s.registrosDao.filas.value.size, "el rechazo no se borra")
        assertEquals(r.uuidCliente, fila.clave)
    }

    @Test
    fun loQueAunNoTieneContratoSigueSinEnviarse() = runTest {
        val s = Servidor { _, _ -> HttpStatusCode.Created to "{}" }
        s.registros.registrarAlmacen(1, RegistroAlmacen.Cierre(3, "2026-11-05", null))
        s.registros.sincronizar()
        assertTrue(s.rutas.isEmpty())
    }
}
