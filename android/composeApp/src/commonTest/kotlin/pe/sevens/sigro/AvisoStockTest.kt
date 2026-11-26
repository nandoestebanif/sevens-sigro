package pe.sevens.sigro

import pe.sevens.sigro.dominio.AvisoStock
import pe.sevens.sigro.dominio.FilaStock
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.StockObra
import pe.sevens.sigro.ui.pantallas.avisoSobreStock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 26/11: pedir más del último stock conocido AVISA pero no bloquea (regla 2: el saldo lo decide el servidor). */
class AvisoStockTest {
    @Test
    fun avisaSoloCuandoPasaElUltimoStockConocido() {
        assertNull(AvisoStock.mensaje("112", "112.0000", "bls", null))         // justo lo que hay: sin aviso
        assertNull(AvisoStock.mensaje("", "112", "bls", null))                 // sin cantidad todavía
        assertNull(AvisoStock.mensaje("114", null, "bls", null))               // sin stock conocido: no se inventa
        val m = AvisoStock.mensaje("114,5", "112.0000", "bls", "25/11/2026 15:45")!!
        assertTrue(m.startsWith("Pides más de lo que había en el último stock que viste: 112"))
        assertTrue("(25/11/2026 15:45)" in m && "Se guarda igual" in m)
    }

    @Test
    fun usaElAlmacenElegidoYSinMovimientosCuentaComoCero() {
        val f = { alm: Int?, cant: String -> FilaStock(alm, alm?.let { "Almacén $it" }, 7, "1151", "CEMENTO", "bls", cant, false) }
        val stock = StockObra(null, listOf(f(1, "112"), f(2, "500")), listOf(f(null, "612")), emptyList(), 0L, true)
        val cemento = Insumo(7, "1151", "CEMENTO", "bls")
        assertEquals(true, avisoSobreStock(stock, cemento, 1, "114") != null)
        assertNull(avisoSobreStock(stock, cemento, 2, "114"))
        assertNull(avisoSobreStock(stock, cemento, null, "600"))               // toda la obra: 612
        val arena = Insumo(9, "0423", "ARENA", "m3")
        assertEquals(true, avisoSobreStock(stock, arena, 1, "1") != null)      // nunca entró a ese almacén
        assertNull(avisoSobreStock(null, cemento, 1, "999"))                  // sin copia guardada: no hay con qué comparar
    }
}
