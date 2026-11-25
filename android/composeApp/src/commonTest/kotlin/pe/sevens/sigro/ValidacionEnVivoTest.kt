package pe.sevens.sigro

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pe.sevens.sigro.dominio.NuevoTrabajador
import pe.sevens.sigro.dominio.Revision
import pe.sevens.sigro.dominio.ValidacionEnVivo
import pe.sevens.sigro.dominio.ValidarSalida
import pe.sevens.sigro.dominio.ValidarTrabajador
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/* Validación mientras se escribe: qué se marca y cuándo (sin pantalla, con tiempo virtual). */
@OptIn(ExperimentalCoroutinesApi::class)
class ValidacionEnVivoTest {
    /** Un formulario de salida de mentira: el texto de cada campo y lo que se ve en pantalla. */
    private class Formulario(alcance: TestScope) {
        var cantidad = ""
        var motivo = ""
        var visto = Revision<String>()
        val enVivo = ValidacionEnVivo(
            alcance,
            validar = { ValidarSalida.errores(null, cantidad, motivo, "Juan Quispe").filterKeys { it != "insumo" } },
            publicar = { visto = it },
        )
        fun escribirCantidad(v: String) { cantidad = v; enVivo.cambio("cantidad") }
    }

    @Test
    fun unErrorNuevoEsperaLaPausaAlEscribir() = runTest {
        val f = Formulario(this)
        f.escribirCantidad("0")
        runCurrent()
        assertTrue(f.visto.errores.isEmpty(), "a mitad de «0,5» no se regaña")
        advanceTimeBy(ValidacionEnVivo.PAUSA_MS + 1)
        assertEquals("Debe ser mayor que cero", f.visto.errores["cantidad"], "si se detiene en «0», tras la pausa sí se avisa")
    }

    @Test
    fun loCorregidoDejaDeMarcarseAlInstante() = runTest {
        val f = Formulario(this)
        f.escribirCantidad("abc"); advanceUntilIdle()
        assertEquals("Solo números, con hasta 4 decimales", f.visto.errores["cantidad"])
        f.escribirCantidad("12")
        runCurrent() // sin esperar la pausa
        assertFalse("cantidad" in f.visto.errores, "premiar pronto: al corregir, el rojo se va ya")
        assertTrue("cantidad" in f.visto.correctos, "y queda el ✓")
    }

    @Test
    fun unErrorQueSigueCambiaDeMensajeSinParpadear() = runTest {
        val f = Formulario(this)
        f.escribirCantidad("abc"); advanceUntilIdle()
        f.escribirCantidad("0")
        runCurrent()
        assertEquals("Debe ser mayor que cero", f.visto.errores["cantidad"], "el error ya visible se actualiza, no desaparece")
    }

    @Test
    fun noSeMarcaLoQueNoSeTocoHastaGuardar() = runTest {
        val f = Formulario(this)
        f.escribirCantidad("5"); advanceUntilIdle()
        assertFalse("motivo" in f.visto.errores, "el motivo vacío no se marca mientras no lo toque")
        val errores = f.enVivo.alGuardar()
        assertEquals(setOf("motivo"), errores.keys, "al guardar se revisa todo")
        assertEquals(errores, f.visto.errores)
    }

    @Test
    fun alSalirDelCampoSeVeSinPausa() = runTest {
        val f = Formulario(this)
        f.motivo = "ab"
        f.enVivo.salir("motivo")
        assertEquals("Di en qué se usará (mínimo 3 letras)", f.visto.errores["motivo"])
    }

    @Test
    fun escribirOtraVezCancelaLaPausaAnterior() = runTest {
        val f = Formulario(this)
        f.escribirCantidad("0")
        advanceTimeBy(ValidacionEnVivo.PAUSA_MS - 100)
        f.escribirCantidad("0,") // sigue escribiendo: la pausa vuelve a empezar
        advanceTimeBy(ValidacionEnVivo.PAUSA_MS - 100)
        assertTrue(f.visto.errores.isEmpty())
        f.escribirCantidad("0,5"); advanceUntilIdle()
        assertTrue(f.visto.errores.isEmpty() && "cantidad" in f.visto.correctos)
    }

    @Test
    fun reiniciarDejaElFormularioLimpio() = runTest {
        val f = Formulario(this)
        f.enVivo.alGuardar()
        f.enVivo.reiniciar()
        assertEquals(Revision(), f.visto)
        f.cantidad = ""; f.enVivo.revisarYa()
        assertTrue(f.visto.errores.isEmpty(), "tras reiniciar nada cuenta como tocado")
    }

    @Test
    fun soloQuedaLoQueSigueMal() {
        val visibles = mapOf("a" to "viejo", "b" to "x")
        assertEquals(mapOf("a" to "nuevo"), ValidacionEnVivo.soloLoQueSigue(visibles, mapOf("a" to "nuevo", "c" to "y")))
    }

    @Test
    fun elCostoDiceQueEstaMal() {
        assertEquals("Escribe el costo unitario en soles", pe.sevens.sigro.dominio.ValidarAlmacen.costo(""))
        assertEquals("Solo números, con hasta 6 decimales", pe.sevens.sigro.dominio.ValidarAlmacen.costo("45,abc"))
        assertEquals("Debe ser mayor que cero", pe.sevens.sigro.dominio.ValidarAlmacen.costo("0"))
        assertEquals(null, pe.sevens.sigro.dominio.ValidarAlmacen.costo("45,50"))
    }

    @Test
    fun laFechaDeIngresoTieneQueExistir() {
        assertTrue(ValidarTrabajador.fechaExiste("2026-11-25"))
        assertTrue(ValidarTrabajador.fechaExiste("2028-02-29"), "bisiesto")
        assertFalse(ValidarTrabajador.fechaExiste("2026-02-29"))
        assertFalse(ValidarTrabajador.fechaExiste("2026-13-01"))
        assertFalse(ValidarTrabajador.fechaExiste("2026-04-31"))
        val n = NuevoTrabajador("Juan Quispe", "DNI", "12345678", pe.sevens.sigro.dominio.Categoria.PEON, "Concreto", "2026-02-30")
        assertEquals("Esa fecha no existe: revisa el mes y el día", ValidarTrabajador.errores(n, emptyList())["fechaIngreso"])
    }
}
