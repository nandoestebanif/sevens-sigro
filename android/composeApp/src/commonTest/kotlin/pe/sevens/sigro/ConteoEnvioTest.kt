package pe.sevens.sigro

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.datos.repositorio.BovedaSesion
import pe.sevens.sigro.datos.repositorio.RepositorioRegistrosImpl
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.LineaAlmacen
import pe.sevens.sigro.dominio.MotivoDiferencia
import pe.sevens.sigro.dominio.RegistroAlmacen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** HU-46: el conteo (incidencias) se envía en tres pasos que se pueden repetir sin duplicar. */
class ConteoEnvioTest {
    private val rutas = mutableListOf<String>()
    private val cuerpos = mutableListOf<String>()
    private var responder: (String) -> Pair<HttpStatusCode, String>? = { null }
    private val ajustes = AjustesDePrueba(mapOf("servidor" to "https://api.prueba", "usuario_id" to "5", "nombre" to "Almacenero"))
    private val seguro = AlmacenSeguroEnMemoria(mapOf(BovedaSesion.CLAVE_TOKEN to "tkn"))
    private val motor = MockEngine { req ->
        rutas += req.url.encodedPath
        cuerpos += req.body.toByteArray().decodeToString()
        val r = responder(req.url.encodedPath) ?: throw RuntimeException("sin señal")
        respond(r.second, r.first, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    private val api = ApiSigro(crearClienteHttp(motor) { seguro.leer(BovedaSesion.CLAVE_TOKEN) }, jsonSigro)
    private val dao = RegistrosDePrueba()
    private val registros = RepositorioRegistrosImpl(api, dao, credencialesDePrueba(ajustes, seguro)) { 1_793_898_000_000L }
    private val cemento = Insumo(7, "1151", "CEMENTO", "bls")
    private val arena = Insumo(8, "0201", "ARENA", "m3")
    private val conteo = RegistroAlmacen.Conteo(3, "2026-11-25", listOf(LineaAlmacen(cemento, "10") to MotivoDiferencia.PERDIDA, LineaAlmacen(arena, "2") to null))

    @Test
    fun abreCuentaCadaInsumoYConfirma() = runTest {
        responder = { r -> when {
            r.endsWith("/conteos") -> HttpStatusCode.Created to """{"id":44,"estado":"borrador","lineas":[]}"""
            r.endsWith("/lineas") -> HttpStatusCode.Created to """{"id":44}"""
            r.endsWith("/confirmar") -> HttpStatusCode.OK to """{"id":44,"estado":"confirmado"}"""
            else -> null
        } }
        registros.registrarAlmacen(1, conteo)
        val r = registros.sincronizar(manual = true)
        assertEquals(1, r.enviados)
        assertEquals(listOf("/api/v1/obras/1/almacen/conteos", "/api/v1/obras/1/almacen/conteos/44/lineas",
            "/api/v1/obras/1/almacen/conteos/44/lineas", "/api/v1/obras/1/almacen/conteos/44/confirmar"), rutas)
        val abrir = jsonSigro.parseToJsonElement(cuerpos[0]).jsonObject
        assertFalse("lineas" in abrir, "abrir el conteo no lleva líneas (el servidor rechaza campos de más)")
        val linea = jsonSigro.parseToJsonElement(cuerpos[1]).jsonObject
        assertEquals("10", linea["cantidad_contada"]!!.jsonPrimitive.content)
        assertEquals("Pérdida", linea["motivo"]!!.jsonPrimitive.content)
        assertEquals("bls", linea["unidad"]!!.jsonPrimitive.content)
    }

    @Test
    fun alReintentarNoRepiteLoYaContado() = runTest {
        responder = { r -> when {
            r.endsWith("/conteos") -> HttpStatusCode.OK to """{"id":44,"estado":"borrador","lineas":[{"insumo_id":7}]}"""
            r.endsWith("/lineas") -> HttpStatusCode.Conflict to """{"codigo":"insumo_ya_contado","mensaje":"Ese insumo ya se contó en este conteo."}"""
            r.endsWith("/confirmar") -> HttpStatusCode.OK to """{"id":44,"estado":"confirmado"}"""
            else -> null
        } }
        registros.registrarAlmacen(1, conteo)
        assertEquals(1, registros.sincronizar(manual = true).enviados)
        assertEquals(1, rutas.count { it.endsWith("/lineas") }, "el cemento ya estaba contado: solo se envía la arena")
    }

    @Test
    fun unaDiferenciaSinMotivoQuedaRechazada() = runTest {
        responder = { r -> when {
            r.endsWith("/conteos") -> HttpStatusCode.Created to """{"id":44,"estado":"borrador","lineas":[]}"""
            r.endsWith("/lineas") -> HttpStatusCode.UnprocessableEntity to """{"codigo":"diferencia_sin_motivo","mensaje":"Toda diferencia exige un motivo."}"""
            else -> null
        } }
        registros.registrarAlmacen(1, conteo)
        val r = registros.sincronizar(manual = true)
        assertEquals(1, r.rechazados)
    }
}
