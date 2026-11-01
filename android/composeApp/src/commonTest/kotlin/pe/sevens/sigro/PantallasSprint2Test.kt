package pe.sevens.sigro

import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.RegistroLocal
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.TipoRegistro
import pe.sevens.sigro.ui.pantallas.unirRegistros
import pe.sevens.sigro.ui.pantallas.CelularEnObra
import pe.sevens.sigro.ui.pantallas.celularEnObra
import pe.sevens.sigro.ui.pantallas.filtrarObras
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
