package pe.sevens.sigro

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import pe.sevens.sigro.ui.tema.Apariencia
import pe.sevens.sigro.ui.tema.Paleta
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/* Modo oscuro: cada pareja texto/fondo que usa la app pasa WCAG AA (4,5 : 1) en claro y en oscuro. */
class TemaOscuroTest {
    @AfterTest fun claro() { Apariencia.oscuro = false }

    private fun contraste(a: Color, b: Color): Double {
        val (x, y) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
        return (x / y).toDouble()
    }

    private fun parejas() = listOf(
        "texto principal / fondo" to (Paleta.Tinta to Paleta.FondoBase),
        "texto principal / superficie" to (Paleta.Tinta to Paleta.Superficie),
        "texto secundario / superficie" to (Paleta.Texto3 to Paleta.Superficie),
        "naranja / superficie" to (Paleta.Naranja to Paleta.Superficie),
        "botón principal" to (Paleta.SobreTinta to Paleta.Tinta),
        "texto sobre naranja" to (Paleta.SobreAcento to Paleta.Naranja),
        "aviso naranja" to (Paleta.NaranjaTexto to Paleta.NaranjaClaro),
        "aviso ámbar" to (Paleta.AvisoTexto to Paleta.AvisoFondo),
        "por enviar" to (Paleta.PendienteTexto to Paleta.PendienteFondo),
        "enviado" to (Paleta.EnviadoTexto to Paleta.EnviadoFondo),
        "rechazado" to (Paleta.RechazadoTexto to Paleta.RechazadoFondo),
    )

    @Test
    fun todasLasParejasPasanAAEnClaroYEnOscuro() {
        for (oscuro in listOf(false, true)) {
            Apariencia.oscuro = oscuro
            parejas().forEach { (nombre, p) ->
                val r = contraste(p.first, p.second)
                assertTrue(r >= 4.5, "${if (oscuro) "oscuro" else "claro"} · $nombre: ${(r * 100).toInt() / 100.0} : 1")
            }
        }
    }

    @Test
    fun elModoCambiaLosColores() {
        val claro = Paleta.FondoBase
        Apariencia.oscuro = true
        assertNotEquals(claro, Paleta.FondoBase)
        assertTrue(Paleta.FondoBase.luminance() < 0.05f, "el fondo oscuro es oscuro de verdad")
    }
}
