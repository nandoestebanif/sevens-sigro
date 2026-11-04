package pe.sevens.sigro

import pe.sevens.sigro.dominio.Asistencia
import pe.sevens.sigro.dominio.CatalogoTexto
import pe.sevens.sigro.dominio.Categoria
import pe.sevens.sigro.dominio.EstadoAsistencia
import pe.sevens.sigro.dominio.FichaInsumo
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.NuevoInsumo
import pe.sevens.sigro.dominio.NuevoTrabajador
import pe.sevens.sigro.dominio.Trabajador
import pe.sevens.sigro.dominio.ValidarAlmacen
import pe.sevens.sigro.dominio.ValidarInsumo
import pe.sevens.sigro.dominio.ValidarTrabajador
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* Lógica de dominio del Sprint 2 en la app: catálogo, trabajadores, asistencia y almacén. */

// Insumos reales del catálogo del cliente (api/semillas/catalogo-cliente.json).
private val catalogo = listOf(
    FichaInsumo(1151, "1151", "CEMENTO PORTLAND TIPO I - 42.5 KG", "bolsas"),
    FichaInsumo(1147, "1147", "FIERRO CORRUGADO DE 1/2\" x 9m - ASTM A615/A706 - GRADO 60", "unidades"),
    FichaInsumo(1432, "1432", "ALAMBRE RECOCIDO N°16", "kilogramos"),
)

private fun t(id: Int, doc: String, salida: String? = null) =
    Trabajador(id, 1, "Trabajador 0$id", "DNI", doc, "peon", "Cuadrilla A", "2026-10-01", salida)

class DominioSprint2Test {

    @Test
    fun busquedaIgnoraMayusculasTildesYEspacios() {
        assertTrue(CatalogoTexto.coincide(catalogo[0], "  cemento   pórtland "))
        assertTrue(CatalogoTexto.coincide(catalogo[1], "fierro 1/2"))
        assertTrue(CatalogoTexto.coincide(catalogo[2], "1432"), "también por código")
        assertFalse(CatalogoTexto.coincide(catalogo[2], "cemento"))
    }

    @Test
    fun insumoNuevoMuestraParecidosYDetectaDuplicado() {
        val n = NuevoInsumo("material", "Fierro corrugado", "1/2", "", "", "unidades")
        assertEquals(listOf(1147), CatalogoTexto.parecidos(n, catalogo).map { it.id })
        assertNull(CatalogoTexto.duplicado(n, catalogo))
        val igual = NuevoInsumo("material", "Alambre recocido N°16", "", "", "", "kilogramos")
        assertNotNull(CatalogoTexto.duplicado(igual, catalogo), "HU-08: no se registra dos veces")
        assertEquals("FIERRO CORRUGADO - 1/2", n.descripcion)
    }

    @Test
    fun insumoExigeMaterialYUnidad() {
        val e = ValidarInsumo.errores(NuevoInsumo("material", "", "", "", "", ""))
        assertTrue("material" in e && "unidad" in e)
    }

    @Test
    fun trabajadorExigeLoDeLaHuYNoSeRepiteEnLaObra() {
        val ok = NuevoTrabajador("Trabajador 09", "DNI", "12345678", Categoria.OFICIAL, "Cuadrilla B", "2026-11-01")
        assertTrue(ValidarTrabajador.errores(ok, emptyList()).isEmpty())
        val e = ValidarTrabajador.errores(ok.copy(nombre = "", categoria = null, cuadrilla = " ", documento = "123"), emptyList())
        assertEquals(setOf("nombre", "categoria", "cuadrilla", "documento"), e.keys)
        val repetido = ValidarTrabajador.errores(ok, listOf(t(3, "12345678")))
        assertTrue(repetido["documento"]!!.contains("Trabajador 03"), "no dos veces en la misma obra")
    }

    @Test
    fun trabajadorDeBajaNoApareceEnJornadasSiguientes() {
        val baja = t(7, "87654321", salida = "2026-10-20")
        assertTrue(baja.activoEl("2026-10-20"))
        assertFalse(baja.activoEl("2026-10-21"), "HU-62: dado de baja no aparece en la asistencia siguiente")
        assertFalse(t(1, "1").activoEl("2026-09-30"), "antes de su ingreso tampoco")
    }

    @Test
    fun resumenDeAsistenciaCuentaHorasSoloDePresentes() {
        val m = listOf(
            MarcaAsistencia(t(1, "1"), EstadoAsistencia.PRESENTE, 8, 2),
            MarcaAsistencia(t(2, "2"), EstadoAsistencia.AUSENTE, 8, 0),
            MarcaAsistencia(t(3, "3"), EstadoAsistencia.PERMISO, 8, 0),
        )
        val r = Asistencia.resumir(m)
        assertEquals(1, r.presentes); assertEquals(1, r.ausentes); assertEquals(1, r.permisos)
        assertEquals(10, r.horas); assertEquals(2, r.extras)
        assertEquals(8, Asistencia.ajustarNormales(m[0], +3).horasNormales, "tope de 8 normales")
        assertEquals(0, Asistencia.ajustarExtra(m[1], -1).horasExtra, "no baja de cero")
    }

    @Test
    fun cantidadesYCostoDelAlmacen() {
        assertNull(ValidarAlmacen.cantidad("12,5"))
        assertNotNull(ValidarAlmacen.cantidad("0"))
        assertNull(ValidarAlmacen.cantidad("0", permitirCero = true), "en el conteo, contar cero es válido")
        assertNotNull(ValidarAlmacen.costo(""), "HU-41: el costo es obligatorio")
    }
}
