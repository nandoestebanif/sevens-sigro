package pe.sevens.sigro

import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.ExcesoLinea
import pe.sevens.sigro.dominio.ExcesoRequerido
import pe.sevens.sigro.dominio.LineaCompra
import pe.sevens.sigro.dominio.RecepcionPendiente
import pe.sevens.sigro.dominio.VerificacionRecepcion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* HU-35/36 (prueba de aceptación del 18–19/11): la Entrada ofrece solo compras con algo por recibir y, sin señal,
   avisa del exceso sobre lo requerido con la última consulta al servidor. */
class EntradaSinSenalTest {
    // Cemento: requerido 100 bls en el S10, ya recibidos 90; la compra trae 20 bls pendientes (factor 1).
    private val referencia = VerificacionRecepcion(
        hayExceso = true,
        lineas = listOf(ExcesoLinea(7, "0221000000", "bls", "100", "90", "110", "10", true)),
    )
    private fun compra(ref: VerificacionRecepcion? = referencia) = CompraParaRecibir(
        1, "Proveedor", "factura F001-1", "2026-11-18",
        listOf(LineaCompra("0221000000", "CEMENTO", "20", "bls", insumoId = 7, pendiente = "20")),
        referencia = ref,
    )

    @Test fun conTodoLoPendienteSinSenalAvisaElExcesoComoElServidor() {
        val v = ExcesoRequerido.sinSenal(compra(), emptyMap(), todo = true)!!
        assertTrue(v.hayExceso)
        assertEquals("10", ExcesoRequerido.fmt(v.excedidas.single().exceso))
    }

    @Test fun unaParteQueNoPasaLoRequeridoNoPideMotivo() {
        val v = ExcesoRequerido.sinSenal(compra(), mapOf(0 to "8"), todo = false)!!
        assertFalse(v.hayExceso, "90 + 8 = 98 ≤ 100")
    }

    @Test fun unaParteQuePasaLoRequeridoSiPideMotivo() {
        val v = ExcesoRequerido.sinSenal(compra(), mapOf(0 to "15"), todo = false)!!
        assertTrue(v.hayExceso)
        assertEquals("5", ExcesoRequerido.fmt(v.excedidas.single().exceso))
    }

    @Test fun sinReferenciaNoSeInventaNadaYDecideElServidor() {
        assertNull(ExcesoRequerido.sinSenal(compra(ref = null), emptyMap(), todo = true))
    }

    @Test fun soloSeOfrecenComprasConAlgoPorRecibir() {
        assertFalse(RecepcionPendiente.queda(listOf("0", "0.0000")))
        assertTrue(RecepcionPendiente.queda(listOf("0", "2.5")))
    }
}
