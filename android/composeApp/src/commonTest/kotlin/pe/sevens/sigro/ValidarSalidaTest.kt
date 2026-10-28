package pe.sevens.sigro

import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.ValidarSalida
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidarSalidaTest {
    private val arena = Insumo(3, "PRUEBA-002", "Arena gruesa", "m3")

    @Test
    fun salidaCompletaNoTieneErrores() {
        assertTrue(ValidarSalida.errores(arena, "1,25", "Tarrajeo muro eje A", "Luis Quispe").isEmpty())
    }

    @Test
    fun exigeInsumoCantidadMotivoYQuienRetira() {
        val e = ValidarSalida.errores(null, "", "", "")
        assertEquals(setOf("insumo", "cantidad", "motivo", "quienRetira"), e.keys)
    }

    @Test
    fun cantidadDebeSerPositivaYNumerica() {
        assertTrue("cantidad" in ValidarSalida.errores(arena, "0", "uso", "Ana Ruiz"))
        assertTrue("cantidad" in ValidarSalida.errores(arena, "-3", "uso", "Ana Ruiz"))
        assertTrue("cantidad" in ValidarSalida.errores(arena, "dos", "uso", "Ana Ruiz"))
        assertTrue("cantidad" in ValidarSalida.errores(arena, "1.1234567", "uso", "Ana Ruiz"))
    }

    @Test
    fun comaDecimalSeNormaliza() = assertEquals("12.5", ValidarSalida.normalizarCantidad(" 12,5 "))
}
