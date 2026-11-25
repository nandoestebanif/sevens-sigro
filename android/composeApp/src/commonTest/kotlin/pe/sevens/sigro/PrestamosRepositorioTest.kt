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
import pe.sevens.sigro.datos.repositorio.RepositorioPrestamosImpl
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoDevolucion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Préstamos de herramientas contra un servidor falso: rutas, cuerpos y respuestas del contrato del backend. */
class PrestamosRepositorioTest {
    private val rutas = mutableListOf<String>()
    private val cuerpos = mutableListOf<String>()
    private var respuesta: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "{}"
    private val ajustes = AjustesDePrueba(mapOf("servidor" to "https://api.prueba", "usuario_id" to "5", "nombre" to "Almacenero"))
    private val seguro = AlmacenSeguroEnMemoria(mapOf(BovedaSesion.CLAVE_TOKEN to "tkn"))
    private val motor = MockEngine { req ->
        rutas += "${req.method.value} ${req.url.encodedPath}?${req.url.encodedQuery}"
        cuerpos += req.body.toByteArray().decodeToString()
        respond(respuesta.second, respuesta.first, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    private val repo = RepositorioPrestamosImpl(
        ApiSigro(crearClienteHttp(motor) { seguro.leer(BovedaSesion.CLAVE_TOKEN) }, jsonSigro),
        credencialesDePrueba(ajustes, seguro),
    )

    private val prestamoJson = """{"id":5,"almacen":"Central","insumo_codigo":"0337","descripcion":"TALADRO","unidad":"und","cantidad":"2.0000",
        "responsable":"Luis Quispe","devolver_el":"2026-11-20","prestado_en":"2026-11-25T10:00:00Z","devuelto_en":null,
        "estado_devolucion":null,"nota_devolucion":null,"atrasado":true,"campo_nuevo":1}"""

    @Test
    fun leeElPanelConPrestamosYDisponibles() = runTest {
        respuesta = HttpStatusCode.OK to """{"sin_devolver":1,"atrasados":1,"prestamos":[$prestamoJson],
            "herramientas":[{"almacen_id":1,"almacen":"Central","insumo_id":3,"insumo_codigo":"0337","descripcion":"TALADRO","unidad":"und",
            "en_almacen":"3.0000","prestado":"2.0000","disponible":"1.0000"}]}"""
        val p = repo.panel(7)
        assertEquals("GET /api/v1/obras/7/almacen/prestamos?estado=todos", rutas.last())
        assertEquals(1, p.atrasados)
        assertEquals("Luis Quispe", p.sinDevolver.single().responsable)
        assertEquals("1.0000", p.herramientas.single().disponible)
        assertEquals(3, p.herramientas.single().insumoId)
    }

    @Test
    fun prestarEnviaElContratoDelServidor() = runTest {
        respuesta = HttpStatusCode.Created to prestamoJson
        val h = pe.sevens.sigro.dominio.HerramientaDisponible(1, "Central", 3, "0337", "TALADRO", "und", "3", "0", "3")
        val p = repo.prestar(7, h, "2,5", "  Luis Quispe ", "2026-11-30", " ")
        val c = jsonSigro.parseToJsonElement(cuerpos.last()).jsonObject
        assertEquals("POST /api/v1/obras/7/almacen/prestamos?", rutas.last())
        assertEquals("2.5", c["cantidad"]!!.jsonPrimitive.content)
        assertEquals("Luis Quispe", c["responsable"]!!.jsonPrimitive.content)
        assertEquals(1, c["almacen_id"]!!.jsonPrimitive.content.toInt())
        assertTrue(c["uuid_cliente"]!!.jsonPrimitive.content.length == 36)
        assertNull(c["nota"], "una nota en blanco no viaja")
        assertEquals(5, p.id)
        assertTrue(p.atrasado)
    }

    @Test
    fun devolverYErroresDelServidor() = runTest {
        respuesta = HttpStatusCode.OK to """{"id":5,"advertencias":["Quedó registrada como perdida."]}"""
        val adv = repo.devolver(7, 5, EstadoDevolucion.PERDIDO, "se perdió en el frente B")
        assertEquals("POST /api/v1/obras/7/almacen/prestamos/5/devolucion?", rutas.last())
        assertEquals("perdido", jsonSigro.parseToJsonElement(cuerpos.last()).jsonObject["estado"]!!.jsonPrimitive.content)
        assertEquals(listOf("Quedó registrada como perdida."), adv)

        respuesta = HttpStatusCode.Conflict to """{"codigo":"ya_devuelto","mensaje":"Ese préstamo ya se devolvió."}"""
        val e = assertFailsWith<ErrorSigro> { repo.devolver(7, 5, EstadoDevolucion.BUENO, null) }
        assertEquals("ya_devuelto", e.codigo)
    }
}
