package pe.sevens.sigro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.Limites
import pe.sevens.sigro.dominio.ValidarAlmacen
import pe.sevens.sigro.dominio.ValidarSalida

/** Los topes del servidor se aplican al escribir: nada guardado sin señal se rechaza después por pasarse. */
class LimitesTest {
    @Test
    fun laCantidadNoAceptaUnQuintoDecimal() {
        assertEquals("12,5", Limites.cantidad("12,", "12,5"))
        assertEquals("1,2345", Limites.cantidad("1,234", "1,2345"))
        assertEquals("1,2345", Limites.cantidad("1,2345", "1,23456"), "la quinta cifra decimal no entra")
        assertEquals("12", Limites.cantidad("12", "12a"), "una letra no entra")
        assertEquals("1,5", Limites.cantidad("1,5", "1,5,"), "una segunda coma no entra")
        assertEquals("1000", Limites.cantidad("", "1 000"), "al pegar «1 000» se quitan los espacios")
        assertEquals("", Limites.cantidad("5", ""), "borrar todo se permite")
        assertEquals("2,12345", Limites.cantidad("2,123456", "2,12345"), "borrar se permite aunque el valor previo viniera largo")
    }

    @Test
    fun laCantidadNoPasaDe14Enteros() {
        val catorce = "9".repeat(14)
        assertEquals(catorce, Limites.cantidad("", catorce))
        assertEquals(catorce, Limites.cantidad(catorce, catorce + "9"))
    }

    @Test
    fun elCostoAdmiteSeisDecimales() {
        assertEquals("4,123456", Limites.costo("4,12345", "4,123456"))
        assertEquals("4,123456", Limites.costo("4,123456", "4,1234567"))
    }

    @Test
    fun losTextosSeCortanAlTope() {
        assertEquals(Limites.MOTIVO, Limites.texto("x".repeat(400), Limites.MOTIVO).length)
        assertNull(Limites.contador("corto", Limites.MOTIVO))
        assertEquals("240/300", Limites.contador("x".repeat(240), Limites.MOTIVO))
    }

    @Test
    fun laValidacionDiceCualEsElExceso() {
        assertEquals("Máximo 4 decimales", ValidarAlmacen.cantidad("2,12345"))
        assertEquals("Máximo 14 cifras enteras", ValidarAlmacen.cantidad("1".repeat(15)))
        assertNull(ValidarAlmacen.cantidad("2,1234"))
        assertEquals("Máximo 6 decimales", ValidarAlmacen.costo("2,1234567"))
        assertNull(ValidarAlmacen.costo("2,123456"))
    }

    @Test
    fun laSalidaRevisaLosLargosDelServidor() {
        val arena = Insumo(1, "0201", "Arena gruesa", "m3")
        val e = ValidarSalida.errores(arena, "1", "m".repeat(301), "q".repeat(121))
        assertEquals("Máximo 300 caracteres", e["motivo"])
        assertEquals("Máximo 120 caracteres", e["quienRetira"])
        assertTrue(ValidarSalida.errores(arena, "1", "m".repeat(300), "q".repeat(120)).isEmpty())
        assertNotNull(ValidarSalida.errores(arena, "1,12345", "uso", "Ana Ruiz")["cantidad"])
    }
}
