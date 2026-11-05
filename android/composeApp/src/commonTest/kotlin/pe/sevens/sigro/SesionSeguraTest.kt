package pe.sevens.sigro

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.datos.repositorio.BovedaSesion
import pe.sevens.sigro.datos.repositorio.Credenciales
import pe.sevens.sigro.datos.repositorio.RepositorioObraImpl
import pe.sevens.sigro.datos.repositorio.RepositorioSalidasImpl
import pe.sevens.sigro.datos.repositorio.RepositorioSesionImpl
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.NuevaSalida
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * M1 (token cifrado) y M2 (plugin Auth de Ktor), sin base ni servidor:
 *  - el token vive en AlmacenSeguro, nunca en la tabla `ajuste`; el de una versión anterior se muda;
 *  - cada petición lleva «Bearer <token>» puesto por el plugin; el login no lleva ninguno;
 *  - un 401 cierra la sesión (vuelve a «Entrar») y la cola sigue intacta.
 */

private class Pedido(val ruta: String, val autorizacion: String?)

/** Arma la app de datos completa (como Koin) sobre un servidor falso que responde según la ruta. */
private class Mundo(
    ajustesIniciales: Map<String, String> = mapOf("servidor" to "http://api.prueba"),
    seguroInicial: Map<String, String> = emptyMap(),
    val responder: (ruta: String) -> Pair<HttpStatusCode, String>,
) {
    val pedidos = mutableListOf<Pedido>()
    val ajustes = AjustesDePrueba(ajustesIniciales)
    val seguro = AlmacenSeguroEnMemoria(seguroInicial)
    val boveda = BovedaSesion(seguro, ajustes)
    private val motor = MockEngine { req ->
        val ruta = req.url.encodedPath
        pedidos += Pedido(ruta, req.headers[HttpHeaders.Authorization])
        val (estado, cuerpo) = responder(ruta)
        respond(cuerpo, estado, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val api = ApiSigro(crearClienteHttp(motor) { boveda.token() }, jsonSigro) { boveda.cerrarSesion() }
    val cred = Credenciales(ajustes, boveda)
    val sesion = RepositorioSesionImpl(api, ajustes, boveda)
    val salidasDao = SalidasDePrueba()
    val salidas = RepositorioSalidasImpl(api, salidasDao, cred) { 1_000L }
    val obra = RepositorioObraImpl(api, cred)
}

private fun sesionJson(token: String) =
    """{"token":"$token","expira_en":3600,"usuario":{"id":5,"nombre":"Almacenero Prueba","usuario":"alm"},"obras":[{"id":1,"nombre":"Obra 1","rol":"almacenero"}]}"""

private const val ALMACENES = """[{"id":3,"nombre":"Almacén principal","tipo":"principal","estado":"activo"}]"""

class SesionSeguraTest {

    @Test
    fun alEntrarElTokenVaAlAlmacenCifradoYNoAAjuste() = runTest {
        val m = Mundo { HttpStatusCode.OK to sesionJson("tkn-1") }
        m.sesion.iniciarSesion("http://api.prueba", "alm", "x")
        assertEquals("tkn-1", m.seguro.m[BovedaSesion.CLAVE_TOKEN])
        assertFalse("token" in m.ajustes.m.value, "la tabla ajuste no guarda el token")
        assertEquals("tkn-1", m.sesion.sesion.first()?.token)
    }

    @Test
    fun elTokenDeUnaVersionAnteriorSeMudaYSeBorraDeAjuste() = runTest {
        val m = Mundo(
            ajustesIniciales = mapOf("servidor" to "http://api.prueba", "token" to "viejo", "usuario_id" to "5", "nombre" to "Alm"),
        ) { HttpStatusCode.OK to "[]" }
        val s = m.sesion.sesion.first()
        assertEquals("viejo", s?.token, "la sesión sigue abierta tras actualizar la app")
        assertEquals("viejo", m.seguro.m[BovedaSesion.CLAVE_TOKEN])
        assertFalse("token" in m.ajustes.m.value, "migrado: ya no está en ajuste")
    }

    @Test
    fun siYaHabiaTokenCifradoElViejoSoloSeBorra() = runTest {
        val m = Mundo(
            ajustesIniciales = mapOf("servidor" to "http://api.prueba", "token" to "viejo", "usuario_id" to "5"),
            seguroInicial = mapOf(BovedaSesion.CLAVE_TOKEN to "nuevo"),
        ) { HttpStatusCode.OK to "[]" }
        assertEquals("nuevo", m.boveda.token())
        assertFalse("token" in m.ajustes.m.value)
    }

    @Test
    fun elPluginPoneElBearerYElLoginNoLlevaToken() = runTest {
        val m = Mundo { ruta -> if (ruta.endsWith("/auth/login")) HttpStatusCode.OK to sesionJson("tkn-1") else HttpStatusCode.OK to ALMACENES }
        m.sesion.iniciarSesion("http://api.prueba", "alm", "x")
        m.obra.almacenes(1)
        assertNull(m.pedidos[0].autorizacion, "el login no manda token")
        assertEquals("/api/v1/obras/1/almacenes", m.pedidos[1].ruta)
        assertEquals("Bearer tkn-1", m.pedidos[1].autorizacion)
    }

    @Test
    fun alVolverAEntrarSeUsaElTokenNuevo() = runTest {
        var token = "tkn-1"
        val m = Mundo { ruta -> if (ruta.endsWith("/auth/login")) HttpStatusCode.OK to sesionJson(token) else HttpStatusCode.OK to ALMACENES }
        m.sesion.iniciarSesion("http://api.prueba", "alm", "x")
        m.obra.almacenes(1)
        m.sesion.cerrarSesion()
        token = "tkn-2"
        m.sesion.iniciarSesion("http://api.prueba", "alm", "x")
        m.obra.almacenes(1)
        assertEquals("Bearer tkn-2", m.pedidos.last().autorizacion, "Ktor no se queda con el token anterior")
    }

    @Test
    fun claveIncorrectaNoCierraNiBorraNada() = runTest {
        val m = Mundo(seguroInicial = mapOf(BovedaSesion.CLAVE_TOKEN to "tkn-1")) {
            HttpStatusCode.Unauthorized to """{"codigo":"credenciales_invalidas","mensaje":"Usuario o clave incorrectos."}"""
        }
        val e = assertFailsWith<ErrorSigro> { m.sesion.iniciarSesion("http://api.prueba", "alm", "mala") }
        assertEquals("Usuario o clave incorrectos.", e.message)
        assertEquals("tkn-1", m.seguro.m[BovedaSesion.CLAVE_TOKEN], "un 401 del login no es «sesión vencida»")
    }

    @Test
    fun un401CierraLaSesionYVuelveAEntrarSinBorrarLaCola() = runTest {
        val m = Mundo { ruta ->
            if (ruta.endsWith("/auth/login")) HttpStatusCode.OK to sesionJson("tkn-1")
            else HttpStatusCode.Unauthorized to """{"codigo":"token_invalido","mensaje":"La sesión venció."}"""
        }
        m.sesion.iniciarSesion("http://api.prueba", "alm", "x")
        val dispositivo = m.cred.identificadorDispositivo()
        val s = m.salidas.registrar(NuevaSalida(1, Insumo(7, "C-1", "Cemento", "bls"), "2", "Losa", "Juan Pérez", null, 3))
        assertNotNull(m.sesion.sesion.first())

        val r = m.salidas.sincronizar()

        assertEquals(1, r.siguenPendientes)
        assertEquals("Bearer tkn-1", m.pedidos.last().autorizacion)
        assertNull(m.sesion.sesion.first(), "sin sesión: la app vuelve a «Entrar»")
        assertNull(m.seguro.m[BovedaSesion.CLAVE_TOKEN], "el token vencido se borra")
        val fila = m.salidasDao.una(s.uuidCliente)
        assertEquals("PENDIENTE", fila.estado, "la cola NO se borra: se envía al volver a entrar")
        assertTrue(fila.nota!!.contains("vuelve a ingresar"))
        assertEquals("http://api.prueba", m.ajustes.m.value["servidor"], "el servidor se conserva para volver a entrar")
        assertEquals(dispositivo, m.cred.identificadorDispositivo(), "el código del celular no cambia (HU-15)")

        // Sin sesión no se insiste: el siguiente envío ni siquiera llama al servidor.
        val antes = m.pedidos.size
        m.salidas.sincronizar()
        assertEquals(antes, m.pedidos.size)
    }
}
