package pe.sevens.sigro

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pe.sevens.sigro.datos.local.TrabajadorDao
import pe.sevens.sigro.datos.local.TrabajadorLocalEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.DniDto
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.datos.repositorio.BovedaSesion
import pe.sevens.sigro.datos.repositorio.RepositorioTrabajadoresImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 26/11: la consulta de RENIEC pasa por el servidor (ruta y respuesta del contrato) y cualquier falla da null: se escribe a mano. */
class TrabajadorDniRepositorioTest {
    private val rutas = mutableListOf<String>()
    private var respuesta: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "{}"
    private var sinRed = false
    private val ajustes = AjustesDePrueba(mapOf("servidor" to "https://api.prueba", "usuario_id" to "5", "nombre" to "SSOMA"))
    private val seguro = AlmacenSeguroEnMemoria(mapOf(BovedaSesion.CLAVE_TOKEN to "tkn"))
    private val motor = MockEngine { req ->
        rutas += "${req.method.value} ${req.url.encodedPath}"
        if (sinRed) throw RuntimeException("sin red")
        respond(respuesta.second, respuesta.first, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    private object SinLocal : TrabajadorDao {
        override suspend fun guardarTodos(lista: List<TrabajadorLocalEntity>) = Unit
        override fun observarDeObra(obraId: Int): Flow<List<TrabajadorLocalEntity>> = flowOf(emptyList())
    }
    private val repo = RepositorioTrabajadoresImpl(
        ApiSigro(crearClienteHttp(motor) { seguro.leer(BovedaSesion.CLAVE_TOKEN) }, jsonSigro),
        SinLocal, credencialesDePrueba(ajustes, seguro),
    )

    @Test
    fun armaElNombreCompletoDesdeLaRespuestaDeReniec() = runTest {
        respuesta = HttpStatusCode.OK to """{"dni":"12345678","nombres":"ANA MARIA","apellido_paterno":"RUIZ","apellido_materno":"PAZ"}"""
        assertEquals("ANA MARIA RUIZ PAZ", repo.consultarDni(7, "12345678"))
        assertEquals("GET /api/v1/obras/7/trabajadores/consulta-dni/12345678", rutas.last())
    }

    @Test
    fun reniecCaidoSinRedORespuestaVaciaDanNull() = runTest {
        respuesta = HttpStatusCode.NotFound to """{"codigo":"reniec_sin_respuesta","mensaje":"RENIEC no respondió"}"""
        assertNull(repo.consultarDni(7, "12345678"))
        respuesta = HttpStatusCode.OK to """{"dni":"12345678"}"""
        assertNull(repo.consultarDni(7, "12345678"))
        sinRed = true
        assertNull(repo.consultarDni(7, "12345678"))
    }

    @Test
    fun elNombreCompletoOmiteLasPartesVacias() {
        assertEquals("LUIS PAZ", DniDto("1", " LUIS ", "PAZ", "").nombreCompleto)
        assertEquals("", DniDto().nombreCompleto)
    }
}
