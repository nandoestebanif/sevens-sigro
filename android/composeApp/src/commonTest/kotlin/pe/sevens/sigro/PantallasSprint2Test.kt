package pe.sevens.sigro

import pe.sevens.sigro.dominio.ObraAsignada
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
}
