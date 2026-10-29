package pe.sevens.sigro

import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.ui.pantallas.resumirObra
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Resumen del inicio: cuenta lo que hay en el celular; no calcula saldo (regla 2). */
class ResumenObraTest {
    private val arena = Insumo(3, "PRUEBA-002", "Arena gruesa", "m3")
    private val dia = 86_400_000L
    private val porDia: (Long) -> String = { (it / dia).toString() }
    private val ahora = 10 * dia + 15 * 3_600_000L

    private fun salida(n: Int, hora: Long, estado: EstadoEnvio) =
        SalidaRegistrada("uuid-$n", 1, arena, "$n", "uso", "Ana Ruiz", null, hora, estado, null, null, 0)

    @Test
    fun cuentaSalidasDeHoyPorEnviarYRechazadas() {
        val r = resumirObra(
            listOf(
                salida(1, ahora - 3_600_000L, EstadoEnvio.PENDIENTE),
                salida(2, ahora - 2 * 3_600_000L, EstadoEnvio.ENVIADO),
                salida(3, ahora - dia, EstadoEnvio.RECHAZADO), // ayer
                salida(4, ahora - 2 * dia, EstadoEnvio.PENDIENTE), // anteayer, sigue en cola
            ),
            ahora, porDia,
        )
        assertEquals(2, r.salidasHoy)
        assertEquals(2, r.porEnviar)
        assertEquals(1, r.rechazadas)
    }

    @Test
    fun ultimasSonLasTresMasRecientes() {
        val lista = (1..5).map { salida(it, ahora - it * 60_000L, EstadoEnvio.ENVIADO) }.shuffled()
        assertEquals(listOf("uuid-1", "uuid-2", "uuid-3"), resumirObra(lista, ahora, porDia).ultimas.map { it.uuidCliente })
    }

    @Test
    fun obraSinRegistrosQuedaEnCero() {
        val r = resumirObra(emptyList(), ahora, porDia)
        assertEquals(0, r.salidasHoy + r.porEnviar + r.rechazadas)
        assertTrue(r.ultimas.isEmpty())
    }
}
