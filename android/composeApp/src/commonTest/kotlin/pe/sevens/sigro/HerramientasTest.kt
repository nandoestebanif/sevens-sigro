package pe.sevens.sigro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.sevens.sigro.dominio.EstadoDevolucion
import pe.sevens.sigro.dominio.HerramientaDisponible
import pe.sevens.sigro.dominio.PanelHerramientas
import pe.sevens.sigro.dominio.PrestamoHerramienta
import pe.sevens.sigro.dominio.ValidarPrestamo

class HerramientasTest {
    private val taladro = HerramientaDisponible(1, "Almacén central", 3, "0337010001", "TALADRO PERCUTOR", "und", "3", "2", "1")
    private fun prestamo(id: Int, devuelto: String? = null, atrasado: Boolean = false) = PrestamoHerramienta(
        id, "Central", "0337", "Taladro", "und", "1", "Luis", null, "2026-11-25T10:00:00Z", devuelto, null, null, atrasado,
    )

    @Test
    fun noSePrestaMasDeLoDisponible() {
        assertEquals("Solo hay 1 disponibles", ValidarPrestamo.errores(taladro, "2", "Luis Quispe")["cantidad"])
        assertTrue(ValidarPrestamo.errores(taladro, "1", "Luis Quispe").isEmpty())
    }

    @Test
    fun exigeHerramientaYQuienSeLaLleva() {
        val e = ValidarPrestamo.errores(null, "1", "Lu")
        assertTrue("herramienta" in e && "responsable" in e)
        assertEquals("Máximo 4 decimales", ValidarPrestamo.errores(taladro, "0,12345", "Luis Quispe")["cantidad"])
    }

    @Test
    fun danadaOPerdidaExigeNota() {
        assertTrue(ValidarPrestamo.errorDevolucion(EstadoDevolucion.PERDIDO, " ") != null)
        assertNull(ValidarPrestamo.errorDevolucion(EstadoDevolucion.BUENO, ""))
        assertEquals(EstadoDevolucion.DANADO, EstadoDevolucion.deApi("danado"))
    }

    @Test
    fun cuentaLasPrestadasYAtrasadas() {
        val p = PanelHerramientas(listOf(prestamo(1, atrasado = true), prestamo(2), prestamo(3, devuelto = "2026-11-25T12:00:00Z")), emptyList())
        assertEquals(2, p.sinDevolver.size)
        assertEquals(1, p.atrasados)
    }
}
