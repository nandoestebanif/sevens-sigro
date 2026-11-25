package pe.sevens.sigro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.sevens.sigro.dominio.EstadoDevolucion
import pe.sevens.sigro.dominio.HerramientaDisponible
import pe.sevens.sigro.dominio.PanelHerramientas
import pe.sevens.sigro.dominio.PrestamoHerramienta
import pe.sevens.sigro.dominio.ValidarPrestamo

class HerramientasTest {
    private val taladro = HerramientaDisponible(1, "Almacén central", 3, "0337010001", "TALADRO PERCUTOR", "und", "3", "2", "1")
    private fun prestamo(id: Int, devuelto: String? = null, atrasado: Boolean = false) = PrestamoHerramienta(
        id, "Central", "0337", "Taladro", "und", "1", "Luis", null, "2026-11-25T10:00:00Z", devuelto, null, null, atrasado,
    )

    @Test
    fun noSePrestaMasDeLoDisponible() {
        assertEquals("Solo hay 1 disponibles", ValidarPrestamo.errores(taladro, "2", "Luis Quispe")["cantidad"])
        assertTrue(ValidarPrestamo.errores(taladro, "1", "Luis Quispe").isEmpty())
    }

    @Test
    fun exigeHerramientaYQuienSeLaLleva() {
        val e = ValidarPrestamo.errores(null, "1", "Lu")
        assertTrue("herramienta" in e && "responsable" in e)
        assertEquals("Máximo 4 decimales", ValidarPrestamo.errores(taladro, "0,12345", "Luis Quispe")["cantidad"])
    }

    @Test
    fun danadaOPerdidaExigeNota() {
        assertTrue(ValidarPrestamo.errorDevolucion(EstadoDevolucion.PERDIDO, " ") != null)
        assertNull(ValidarPrestamo.errorDevolucion(EstadoDevolucion.BUENO, ""))
        assertEquals(EstadoDevolucion.DANADO, EstadoDevolucion.deApi("danado"))
    }

    @Test
    fun cuentaLasPrestadasYAtrasadas() {
        val p = PanelHerramientas(listOf(prestamo(1, atrasado = true), prestamo(2), prestamo(3, devuelto = "2026-11-25T12:00:00Z")), emptyList())
        assertEquals(2, p.sinDevolver.size)
        assertEquals(1, p.atrasados)
    }
}

class FiltroTipoRegistrosTest {
    private fun fila(tipo: String, estado: pe.sevens.sigro.dominio.EstadoEnvio = pe.sevens.sigro.dominio.EstadoEnvio.ENVIADO) =
        pe.sevens.sigro.ui.pantallas.FilaRegistro("u-$tipo-$estado", tipo, "t", null, "d", 0, estado, null, null, 0)

    @Test
    fun ofreceSoloLosTiposQueHayEnOrden() {
        val l = listOf(fila("Asistencia"), fila("Salida"), fila("Conteo físico"), fila("Salida"))
        assertEquals(listOf("Todo", "Salida", "Conteo físico", "Asistencia"), pe.sevens.sigro.ui.pantallas.tiposDe(l))
    }

    @Test
    fun filtraPorTipoYSeCombinaConElEstado() {
        val l = listOf(fila("Salida"), fila("Salida", pe.sevens.sigro.dominio.EstadoEnvio.RECHAZADO), fila("Entrada de compra"))
        assertEquals(2, pe.sevens.sigro.ui.pantallas.filtrarPorTipo(l, "Salida").size)
        assertEquals(3, pe.sevens.sigro.ui.pantallas.filtrarPorTipo(l, "Todo").size)
        val rechazadas = pe.sevens.sigro.ui.pantallas.filtrarRegistros(l, pe.sevens.sigro.ui.pantallas.FiltroRegistros.RECHAZADOS)
        assertEquals(1, pe.sevens.sigro.ui.pantallas.filtrarPorTipo(rechazadas, "Salida").size)
    }
}

class VistaPorRolTest {
    @Test
    fun cadaRolVeLoSuyo() {
        val alm = pe.sevens.sigro.dominio.VistaPorRol.accesos("almacenero")
        val sso = pe.sevens.sigro.dominio.VistaPorRol.accesos("ssoma")
        val res = pe.sevens.sigro.dominio.VistaPorRol.accesos("residente")
        assertTrue(pe.sevens.sigro.dominio.AccesoApp.SALIDA in alm && pe.sevens.sigro.dominio.AccesoApp.PERSONAL !in alm, "el almacenero no registra personal")
        assertTrue(pe.sevens.sigro.dominio.AccesoApp.PERSONAL in sso && pe.sevens.sigro.dominio.AccesoApp.SALIDA !in sso, "el SSOMA no registra almacén")
        assertTrue(pe.sevens.sigro.dominio.AccesoApp.SALIDA !in res && pe.sevens.sigro.dominio.AccesoApp.PERSONAL !in res, "el residente consulta")
        assertTrue(pe.sevens.sigro.dominio.VistaPorRol.registra("ssoma") && !pe.sevens.sigro.dominio.VistaPorRol.registra("gerente"))
    }
}
