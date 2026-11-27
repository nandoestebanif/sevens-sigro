package pe.sevens.sigro

import pe.sevens.sigro.dominio.Busqueda
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* Buscar en las listas del celular (27/11): con cientos de insumos, solo sale lo que se busca y lo más parecido primero. */
class BusquedaTest {
    private val insumos = listOf("ALAMBRE NEGRO RECOCIDO # 16", "CEMENTO CONDUCTIVO BOLSA DE 25KG", "CEMENTO PORTLAND TIPO I", "ARENA GRUESA", "CLAVOS PARA MADERA C/C 3\"")

    @Test fun sinTildesNiMayusculasYCadaPalabraEnCualquierOrden() {
        assertEquals(listOf("CEMENTO PORTLAND TIPO I"), Busqueda.filtrar(insumos, "tipo cemento") { listOf(it) })
        assertTrue(Busqueda.coincide(listOf("Andamio Metálico"), "metalico"))
        assertTrue(Busqueda.coincide(listOf("Peón Istaña"), "ISTANA peon"))
    }

    @Test fun primeroLoQueEmpiezaConLoEscrito() {
        // «ce» está dentro de «recocido»? no; pero «cla» empieza en CLAVOS y solo está dentro de otras palabras en ninguna
        assertEquals(listOf("CEMENTO CONDUCTIVO BOLSA DE 25KG", "CEMENTO PORTLAND TIPO I"), Busqueda.filtrar(insumos, "cem") { listOf(it) })
        val r = Busqueda.filtrar(listOf("ALAMBRE GRUESO", "ARENA GRUESA"), "gru") { listOf(it) }
        assertEquals(2, r.size)
        assertEquals(0, Busqueda.relevancia(listOf("FINO DE OBRA"), "fin"))
        assertEquals(1, Busqueda.relevancia(listOf("ARENA FINA"), "fin"))
        assertEquals(2, Busqueda.relevancia(listOf("PASAFINO"), "fin"))
    }

    @Test fun sinConsultaDevuelveTodoYElCodigoTambienCuenta() {
        assertEquals(insumos, Busqueda.filtrar(insumos, "  ") { listOf(it) })
        assertEquals(listOf("x"), Busqueda.filtrar(listOf("x", "y"), "0221") { if (it == "x") listOf("CEMENTO", "0221000093") else listOf("ARENA", "0303") })
    }
}
