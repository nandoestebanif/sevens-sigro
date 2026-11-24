package pe.sevens.sigro

import pe.sevens.sigro.datos.repositorio.DecisionEnvio
import pe.sevens.sigro.datos.repositorio.decidirEnvio
import pe.sevens.sigro.dominio.ErrorSigro
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Traducción de {codigo, mensaje, estado HTTP} del servidor a la jerarquía sellada ErrorSigro. */
class ErroresSelladosTest {
    @Test
    fun cadaEstadoHttpCaeEnSuTipo() {
        assertIs<ErrorSigro.SinConexion>(ErrorSigro("sin_conexion", "sin señal"))
        assertIs<ErrorSigro.Servidor>(ErrorSigro("http_503", "caído", 503))
        assertIs<ErrorSigro.Servidor>(ErrorSigro("demasiadas", "espera", 429))
        assertIs<ErrorSigro.SesionVencida>(ErrorSigro("token_vencido", "vuelve a entrar", 401))
        assertIs<ErrorSigro.SinPermiso>(ErrorSigro("sin_permiso", "No tienes permiso", 403))
        assertIs<ErrorSigro.YaRegistrado>(ErrorSigro("uuid_repetido", "ya estaba", 409))
        assertIs<ErrorSigro.Validacion>(ErrorSigro("cantidad", "Debe ser mayor que cero", 422))
        assertIs<ErrorSigro.Negocio>(ErrorSigro("stock_insuficiente", "No alcanza", 409))
    }

    @Test
    fun conservaCodigoMensajeYEstado() {
        val e = ErrorSigro("sin_permiso", "No tienes permiso para esto en esta obra.", 403)
        assertEquals("sin_permiso", e.codigo)
        assertEquals("No tienes permiso para esto en esta obra.", e.message)
        assertEquals(403, e.estadoHttp)
    }

    @Test
    fun soloSinConexionYServidorSonTransitorios() {
        assertTrue(ErrorSigro("sin_conexion", "x").esTransitorio)
        assertTrue(ErrorSigro("http_502", "x", 502).esTransitorio)
        assertFalse(ErrorSigro("sin_permiso", "x", 403).esTransitorio)
        assertFalse(ErrorSigro("cantidad", "x", 422).esTransitorio)
    }

    @Test
    fun laPoliticaDecideSegunElTipo() {
        assertIs<DecisionEnvio.Reintentar>(decidirEnvio(ErrorSigro("sin_conexion", "x"), "salidas"))
        assertIs<DecisionEnvio.Reintentar>(decidirEnvio(ErrorSigro("token", "x", 401), "salidas"))
        assertIs<DecisionEnvio.YaRecibido>(decidirEnvio(ErrorSigro("uuid_repetido", "x", 409), "salidas"))
        assertIs<DecisionEnvio.Rechazar>(decidirEnvio(ErrorSigro("sin_permiso", "x", 403), "salidas"))
        // un 403 NO cierra la sesión: se rechaza ese registro con su motivo y la app sigue
        assertEquals("x", (decidirEnvio(ErrorSigro("sin_permiso", "x", 403), "salidas") as DecisionEnvio.Rechazar).motivo)
    }
}
