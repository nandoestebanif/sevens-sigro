package pe.sevens.sigro

import pe.sevens.sigro.dominio.FilaStock
import pe.sevens.sigro.dominio.StockObra
import pe.sevens.sigro.ui.pantallas.errorQuienRetira
import pe.sevens.sigro.ui.pantallas.insumosConStock
import pe.sevens.sigro.ui.pantallas.trabajadoresQueCoinciden
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Revisión del 17/11: la salida ofrece solo lo del almacén elegido y «Retira» se elige entre los trabajadores. */
class SalidaAlmacenTest {
    private fun fila(alm: Int?, id: Int, cod: String, desc: String, cant: String) =
        FilaStock(alm, alm?.let { "Almacén $it" }, id, cod, desc, "BOL", cant, false)

    private val stock = StockObra(
        corte = "2026-11-17T23:31:00Z",
        porAlmacen = listOf(
            fila(1, 93, "0221000093", "CEMENTO CONDUCTIVO BOLSA DE 25KG", "78"),
            fila(1, 7, "0202000007", "ALAMBRE NEGRO RECOCIDO # 16", "2"),
            fila(1, 4, "0203020004", "ACERO CORRUGADO FY=4200", "0"),
            fila(2, 5, "0221000000", "CEMENTO PORTLAND TIPO I", "10"),
        ),
        consolidado = listOf(
            fila(null, 93, "0221000093", "CEMENTO CONDUCTIVO BOLSA DE 25KG", "78"),
            fila(null, 7, "0202000007", "ALAMBRE NEGRO RECOCIDO # 16", "2"),
            fila(null, 5, "0221000000", "CEMENTO PORTLAND TIPO I", "10"),
        ),
        advertencias = emptyList(), recibidoMs = 0, desdeCelular = false,
    )

    @Test fun soloOfreceLoQueHayEnElAlmacenElegido() {
        val r = insumosConStock(stock, 1, "")!!
        assertEquals(listOf(7, 93), r.map { it.insumo.id }) // el acero en 0 y el cemento del almacén 2 no salen
    }

    @Test fun buscaPorNombreOCodigoDentroDelAlmacen() {
        assertEquals(listOf(93), insumosConStock(stock, 1, "ce")!!.map { it.insumo.id })
        assertEquals(listOf(7), insumosConStock(stock, 1, "0202")!!.map { it.insumo.id })
        assertEquals(listOf(5), insumosConStock(stock, 2, "cemento")!!.map { it.insumo.id })
    }

    @Test fun sinAlmacenElegidoUsaElConsolidadoYMuestraLaCantidadDelServidor() {
        val r = insumosConStock(stock, null, "")!!
        assertEquals(3, r.size)
        assertEquals("78", r.first { it.insumo.id == 93 }.cantidad)
    }

    @Test fun sinStockGuardadoNoFiltra() = assertNull(insumosConStock(null, 1, "ce"))

    @Test fun retiraBuscaEntreLosTrabajadoresSinTildes() {
        val obra = listOf("Fernando Esteban Istaña Flores", "José Ramírez", "Henry Ticona")
        assertEquals(listOf("Fernando Esteban Istaña Flores"), trabajadoresQueCoinciden(obra, "istana"))
        assertEquals(listOf("José Ramírez"), trabajadoresQueCoinciden(obra, "jose"))
        assertEquals(3, trabajadoresQueCoinciden(obra, "").size)
    }

    @Test fun retiraDebeSerAlguienDeLaObraCuandoHayLista() {
        val obra = listOf("Henry Ticona")
        assertNull(errorQuienRetira("henry ticona", obra))
        assertNotNull(errorQuienRetira("Pedro", obra))
        assertNull(errorQuienRetira("Pedro", emptyList())) // sin lista en el celular se acepta lo escrito
    }
}

class AsistenciaRevisionTest {
    private fun t(id: Int, nombre: String, cuadrilla: String) = pe.sevens.sigro.dominio.Trabajador(
        id, 1, nombre, "DNI", "4000000$id", "OPERARIO", cuadrilla, "2026-11-01", null)
    private val marcas = listOf(
        pe.sevens.sigro.dominio.MarcaAsistencia(t(1, "Fernando Istaña", "cuadrilla"), pe.sevens.sigro.dominio.EstadoAsistencia.PRESENTE),
        pe.sevens.sigro.dominio.MarcaAsistencia(t(2, "Andrey Mestanza", "encofrado"), pe.sevens.sigro.dominio.EstadoAsistencia.PRESENTE),
    )

    @Test fun buscaPorNombreOCuadrillaSinTildes() {
        assertEquals(listOf(1), pe.sevens.sigro.dominio.AsistenciaDia.filtrar(marcas, "istana").map { it.trabajador.id })
        assertEquals(listOf(2), pe.sevens.sigro.dominio.AsistenciaDia.filtrar(marcas, "ENCOF").map { it.trabajador.id })
        assertEquals(2, pe.sevens.sigro.dominio.AsistenciaDia.filtrar(marcas, " ").size)
    }

    @Test fun lasExtrasNoSeMezclanConLasNormales() {
        val r = pe.sevens.sigro.dominio.ResumenAsistencia(presentes = 1, ausentes = 0, permisos = 0, horas = 11, extras = 3)
        assertEquals("8 normales · 3 extra", pe.sevens.sigro.dominio.AsistenciaDia.textoHoras(r))
    }

    @Test fun ofreceHoyYLosSeisDiasAnteriores() {
        val dias = pe.sevens.sigro.dominio.AsistenciaDia.diasElegibles(10 * 86_400_000L, { "d" + it / 86_400_000L })
        assertEquals(listOf("d10", "d9", "d8", "d7", "d6", "d5", "d4"), dias)
    }
}
