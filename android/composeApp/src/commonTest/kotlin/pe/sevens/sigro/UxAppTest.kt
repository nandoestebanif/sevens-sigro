package pe.sevens.sigro

import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.FilaStock
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.StockObra
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.ui.pantallas.FiltroRegistros
import pe.sevens.sigro.ui.pantallas.filtrarRegistros
import pe.sevens.sigro.ui.pantallas.stockConocido
import pe.sevens.sigro.ui.pantallas.sugerenciasSalida
import pe.sevens.sigro.ui.pantallas.unirRegistros
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* Mejoras de experiencia de la app (revisión del 12/11): cómo se muestran los datos, sin cambiar los datos. */

private val cemento = Insumo(7, "0221000093", "CEMENTO CONDUCTIVO BOLSA DE 25KG", "BOL")

private fun salida(motivo: String, quien: String, frente: String?, hora: Long, estado: EstadoEnvio = EstadoEnvio.ENVIADO) =
    SalidaRegistrada("u$hora", 1, cemento, "12", motivo, quien, frente, hora, estado, if (estado == EstadoEnvio.RECHAZADO) "No hay stock" else null, null, 0)

class UxAppTest {

    @Test
    fun losNombresDelS10SeLeenEnCajaAmableSinTocarSiglasNiMedidas() {
        assertEquals("Cemento Conductivo Bolsa de 25KG", Texto.nombre("CEMENTO CONDUCTIVO BOLSA DE 25KG"))
        assertEquals("Fierro Corrugado de 1/2\" x 9m - ASTM A615/A706 - Grado 60", Texto.nombre("FIERRO CORRUGADO DE 1/2\" x 9m - ASTM A615/A706 - GRADO 60"))
        assertEquals("Tubo PVC de 4\"", Texto.nombre("TUBO PVC DE 4\""))
        assertEquals("Arena gruesa", Texto.nombre("Arena gruesa"), "si ya viene en caja mixta no se toca")
    }

    @Test
    fun lasUnidadesSeLeenComoPalabras() {
        assertEquals("bolsas", Texto.unidad("BOL"))
        assertEquals("bolsa", Texto.unidad("BOL", "1"))
        assertEquals("m³", Texto.unidad("m3"))
        assertEquals("m³", Texto.unidad("METRO CÚBICO"))
        assertEquals("unidades", Texto.unidad("und"))
        assertEquals("galones", Texto.unidad("GALONES"))
        assertEquals("xyz", Texto.unidad("XYZ"), "una desconocida se muestra en minúsculas, sin inventar")
        assertEquals("25 bolsas", Texto.cantidadConUnidad("25.0000", "BOL"))
        assertEquals("2.5 m³", Texto.cantidadConUnidad("2,5000", "m3"))
    }

    @Test
    fun fechasYCodigoLegibles() {
        assertEquals("jueves 12/11", Texto.fecha("2026-11-12"))
        assertEquals("domingo 08/11", Texto.fecha("2026-11-08"))
        assertEquals("lunes 01/01", Texto.fecha("2024-01-01"))
        assertEquals("no es fecha", Texto.fecha("no es fecha"))
        assertEquals("5BAC-F4A8", Texto.codigoCorto("sigro-android-299b2033-59a2-4525-9535-23345bacf4a8"))
    }

    @Test
    fun lasSugerenciasSonLoUltimoUsadoSinRepetirNiBasura() {
        val s = sugerenciasSalida(
            listOf(
                salida("Vaciado de losa", "Juan Pérez", "Cuadrilla A", 1),
                salida("27d", "ya", "ya", 2),                       // lo de la prueba: no se sugiere
                salida("vaciado  de losa", "Juan Pérez", null, 3),  // repetido con otra caja y espacios
                salida("Tarrajeo eje B", "Rosa Díaz", "Cuadrilla B", 4),
            ),
            trabajadores = listOf("Carlos Quispe", "Rosa Díaz"),
        )
        assertEquals(listOf("Tarrajeo eje B", "vaciado de losa"), s.motivos)
        assertEquals(listOf("Rosa Díaz", "Juan Pérez", "Carlos Quispe"), s.quienes)
        assertEquals(listOf("Cuadrilla B", "Cuadrilla A"), s.frentes)
    }

    @Test
    fun elStockQueSeMuestraEsElDelServidorConSuHora() {
        val fila = FilaStock(3, "Almacén central", 7, cemento.codigo, cemento.descripcion, "BOL", "40.0000", false)
        val stock = StockObra("2026-11-12T17:20:00Z", listOf(fila), listOf(fila.copy(almacenId = null, almacen = null)), emptyList(), 0L, false)
        assertEquals("Último stock: 40 bolsas · 12/11/2026 12:20", stockConocido(stock, cemento, 3))
        assertEquals("Último stock: sin movimientos · 12/11/2026 12:20", stockConocido(stock, cemento, 9), "otro almacén: no se suma ni se adivina")
        assertNull(stockConocido(null, cemento, 3), "sin respuesta del servidor no se muestra nada")
        assertNull(stockConocido(stock, null, 3))
    }

    @Test
    fun rechazadosSeFiltranYTraenLaSalidaParaRegistrarlaDeNuevo() {
        val lista = unirRegistros(listOf(salida("Losa", "Juan Pérez", null, 1), salida("Muro", "Rosa Díaz", null, 2, EstadoEnvio.RECHAZADO)), emptyList())
        val r = filtrarRegistros(lista, FiltroRegistros.RECHAZADOS)
        assertEquals(1, r.size)
        assertEquals("Muro", r.single().salida?.motivo, "con la salida original se precarga el formulario")
        assertTrue(filtrarRegistros(lista, FiltroRegistros.POR_ENVIAR).isEmpty())
        assertEquals(2, filtrarRegistros(lista, FiltroRegistros.TODOS).size)
    }
}
