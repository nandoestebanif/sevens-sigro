package pe.sevens.sigro

import pe.sevens.sigro.dominio.CamaraNoDisponible
import pe.sevens.sigro.dominio.PermisoCamara
import pe.sevens.sigro.dominio.ResultadoFoto
import pe.sevens.sigro.dominio.estadoPermiso
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Los estados del permiso de cámara, decididos sin Android (la regla vive en el dominio). */
class PermisoCamaraTest {
    @Test
    fun concedidoGanaSiempre() {
        assertEquals(PermisoCamara.CONCEDIDO, estadoPermiso(concedido = true, yaSePregunto = true, sistemaPermiteExplicar = false))
    }

    @Test
    fun laPrimeraVezNoEsUnaNegativa() {
        assertEquals(PermisoCamara.SIN_PREGUNTAR, estadoPermiso(concedido = false, yaSePregunto = false, sistemaPermiteExplicar = false))
    }

    @Test
    fun negadoUnaVezSePuedeVolverAPedir() {
        assertEquals(PermisoCamara.DENEGADO, estadoPermiso(concedido = false, yaSePregunto = true, sistemaPermiteExplicar = true))
    }

    @Test
    fun negadoParaSiempreSoloDesdeAjustes() {
        // Android deja de devolver «explicar» cuando el usuario marcó «no volver a preguntar»
        assertEquals(PermisoCamara.DENEGADO_PARA_SIEMPRE, estadoPermiso(concedido = false, yaSePregunto = true, sistemaPermiteExplicar = false))
    }

    @Test
    fun sinCamaraNoSeOfreceLaFoto() = runTest {
        assertEquals(PermisoCamara.NO_DISPONIBLE, CamaraNoDisponible.permiso())
        assertEquals(ResultadoFoto.SinPermiso(PermisoCamara.NO_DISPONIBLE), CamaraNoDisponible.tomarFoto("x"))
    }
}
