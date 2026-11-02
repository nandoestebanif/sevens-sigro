package pe.sevens.sigro

import pe.sevens.sigro.dominio.EstadoDispositivo
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.RegistroLocal
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.TipoRegistro
import pe.sevens.sigro.ui.pantallas.unirRegistros
import pe.sevens.sigro.dominio.FichaInsumo
import pe.sevens.sigro.ui.pantallas.EstadoCatalogo
import pe.sevens.sigro.ui.pantallas.sinCerosDeMas
import pe.sevens.sigro.ui.pantallas.textoFactor
import pe.sevens.sigro.ui.pantallas.saldoParaMostrar
import pe.sevens.sigro.ui.pantallas.CelularEnObra
import pe.sevens.sigro.ui.pantallas.EstadoPantallaDispositivo
import pe.sevens.sigro.ui.pantallas.celularEnObra
import pe.sevens.sigro.ui.pantallas.filtrarObras
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/* Lógica de las pantallas del Sprint 2 que vive fuera de Compose (la pantalla solo pinta). */

class PantallasSprint2Test {

    @Test
    fun obrasSeBuscanSinTildesNiMayusculas() {
        val obras = listOf(ObraAsignada(1, "Multifamiliar Flores", "almacenero"), ObraAsignada(2, "Edificio Pachacámac", "ssoma"))
        assertEquals(listOf(2), filtrarObras(obras, "pachacamac").map { it.id })
        assertEquals(listOf(1), filtrarObras(obras, "  FLORES ").map { it.id })
        assertEquals(2, filtrarObras(obras, "").size)
    }

    @Test
    fun estadoDelCelularPorObra() {
        assertEquals(CelularEnObra.AUTORIZADO, celularEnObra("autorizado", consultado = true))
        assertEquals(CelularEnObra.PENDIENTE, celularEnObra("pendiente", consultado = true))
        assertEquals(CelularEnObra.SIN_SOLICITAR, celularEnObra(null, consultado = true))
        assertEquals(CelularEnObra.SIN_CONSULTAR, celularEnObra("autorizado", consultado = false), "sin señal no se adivina")
    }

    @Test
    fun misRegistrosUneLasDosColasConLoMasRecientePrimero() {
        val salida = SalidaRegistrada(
            "s1", 1, Insumo(1, "1151", "CEMENTO PORTLAND TIPO I - 42.5 KG", "bolsas"), "25", "Vaciado", "Maestro de obra",
            null, 2_000L, EstadoEnvio.RECHAZADO, "No hay stock suficiente.", null, 1,
        )
        val jornada = RegistroLocal("r1", 1, TipoRegistro.JORNADA, "Trabajador 01", "Presente · 8 h", 3_000L, EstadoEnvio.PENDIENTE, null, null, 0)
        val filas = unirRegistros(listOf(salida), listOf(jornada))
        assertEquals(listOf("r1", "s1"), filas.map { it.clave })
        assertEquals("25 bolsas", filas[1].cantidad)
        assertEquals("No hay stock suficiente.", filas[1].motivoRechazo, "HU-38: el rechazo se ve con su motivo")
        assertEquals("Asistencia", filas[0].tipo)
    }

    @Test
    fun miCelularSoloPidePermisoSinSolicitarORevocado() {
        fun p(estado: String?) = EstadoPantallaDispositivo(EstadoDispositivo("sigro-android-x", estado)).puedePedir
        assertTrue(p(null)); assertTrue(p("revocado"))
        assertFalse(p("pendiente")); assertFalse(p("autorizado"))
        assertFalse(EstadoPantallaDispositivo().puedePedir, "mientras consulta no se ofrece el botón")
    }

    @Test
    fun fichaMuestraElFactorSoloSiHayUnoDeclarado() {
        val acero = FichaInsumo(1, "1147", "FIERRO CORRUGADO DE 1/2\" x 9m", "unidades", "kg", "8.9400", "material", true, true)
        assertEquals("1 unidades = 8.94 kg", textoFactor(acero))
        assertTrue(textoFactor(acero.copy(factorConversion = "1", unidadS10 = "unidades"))!!.startsWith("Sin factor"), "HU-17: sin factor, misma unidad")
        assertEquals(null, textoFactor(acero.copy(factorConversion = null)), "sin señal no se inventa")
        assertEquals("10", sinCerosDeMas("10"))
    }

    @Test
    fun resumenDeLaBusquedaDelCatalogo() {
        assertEquals("Sin coincidencias", EstadoCatalogo(q = "zzz").resumen)
        assertTrue(EstadoCatalogo(q = "fierro", total = 90, resultados = List(40) { FichaInsumo(it, "$it", "X", "u") }).resumen.contains("afina"))
    }

    @Test
    fun stockNoSeCalculaEnElCelular() {
        assertEquals("[lo calcula el servidor]", saldoParaMostrar(null, "bolsas"), "regla 2: sin saldo del servidor no hay número")
        assertEquals("38 bolsas", saldoParaMostrar("38", "bolsas"))
    }
}
