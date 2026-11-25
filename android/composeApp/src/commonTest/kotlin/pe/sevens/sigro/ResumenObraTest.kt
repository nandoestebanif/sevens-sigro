package pe.sevens.sigro

import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.RegistroLocal
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.TipoRegistro
import pe.sevens.sigro.ui.pantallas.resumirObra
import pe.sevens.sigro.ui.pantallas.unirRegistros
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

    private fun registro(n: Int, tipo: TipoRegistro, hora: Long, estado: EstadoEnvio) =
        RegistroLocal("reg-$n", 1, tipo, "Registro $n", "detalle", hora, estado, null, null, 0)

    private fun resumen(salidas: List<SalidaRegistrada>, otros: List<RegistroLocal> = emptyList()) =
        resumirObra(unirRegistros(salidas, otros), ahora, porDia)

    @Test
    fun cuentaSalidasDeHoyPorEnviarYRechazadas() {
        val r = resumen(
            listOf(
                salida(1, ahora - 3_600_000L, EstadoEnvio.PENDIENTE),
                salida(2, ahora - 2 * 3_600_000L, EstadoEnvio.ENVIADO),
                salida(3, ahora - dia, EstadoEnvio.RECHAZADO), // ayer
                salida(4, ahora - 2 * dia, EstadoEnvio.PENDIENTE), // anteayer, sigue en cola
            ),
        )
        assertEquals(2, r.salidasHoy)
        assertEquals(2, r.porEnviar)
        assertEquals(1, r.rechazadas)
    }

    @Test
    fun ultimasSonLasTresMasRecientes() {
        val lista = (1..5).map { salida(it, ahora - it * 60_000L, EstadoEnvio.ENVIADO) }.shuffled()
        assertEquals(listOf("uuid-1", "uuid-2", "uuid-3"), resumen(lista).ultimas.map { it.clave })
    }

    /** Antes solo salían salidas: una entrada o una asistencia más reciente también debe verse en el inicio. */
    @Test
    fun ultimasIncluyenEntradasYAsistencia() {
        val r = resumen(
            listOf(salida(1, ahora - 30 * 60_000L, EstadoEnvio.ENVIADO)),
            listOf(
                registro(1, TipoRegistro.ENTRADA_COMPRA, ahora - 5 * 60_000L, EstadoEnvio.PENDIENTE),
                registro(2, TipoRegistro.JORNADA, ahora - 10 * 60_000L, EstadoEnvio.PENDIENTE),
            ),
        )
        assertEquals(listOf("Entrada de compra", "Asistencia", "Salida"), r.ultimas.map { it.tipo })
        assertEquals(1, r.salidasHoy) // la entrada no cuenta como salida
    }

    /** «Por enviar» y «rechazadas» suman todos los tipos: el inicio dice lo mismo que «Mis registros». */
    @Test
    fun porEnviarCuentaTodasLasColas() {
        val r = resumen(
            listOf(salida(1, ahora, EstadoEnvio.PENDIENTE)),
            listOf(
                registro(1, TipoRegistro.INGRESO_SIN_COMPRA, ahora, EstadoEnvio.PENDIENTE),
                registro(2, TipoRegistro.JORNADA, ahora, EstadoEnvio.RECHAZADO),
            ),
        )
        assertEquals(2, r.porEnviar)
        assertEquals(1, r.rechazadas)
    }

    @Test
    fun obraSinRegistrosQuedaEnCero() {
        val r = resumen(emptyList())
        assertEquals(0, r.salidasHoy + r.porEnviar + r.rechazadas)
        assertTrue(r.ultimas.isEmpty())
    }
}
