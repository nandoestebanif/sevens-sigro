package pe.sevens.sigro

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.NuevaSalida
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RegistroLocal
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.ResultadoSincronizacion
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.casosdeuso.RegistrarSalida
import pe.sevens.sigro.dominio.casosdeuso.SincronizarCola
import kotlin.test.Test
import kotlin.test.assertEquals

/** Bitácora compartida: en qué orden se llamó a cada dependencia. */
private class Bitacora { val pasos = mutableListOf<String>() }

private class SalidasEspia(val b: Bitacora, val resultado: () -> ResultadoSincronizacion) : RepositorioSalidas {
    override fun salidasDeObra(obraId: Int): Flow<List<SalidaRegistrada>> = flowOf(emptyList())
    override val pendientes: Flow<Int> = flowOf(0)
    override suspend fun registrar(nueva: NuevaSalida): SalidaRegistrada {
        b.pasos += "guardar en el celular"
        return SalidaRegistrada("uuid-1", nueva.obraId, nueva.insumo, nueva.cantidad, nueva.motivo, nueva.quienRetira, nueva.frente,
            1L, EstadoEnvio.PENDIENTE, null, null, 0)
    }
    override suspend fun sincronizar(manual: Boolean): ResultadoSincronizacion { b.pasos += "salidas manual=$manual"; return resultado() }
}

private class RegistrosEspia(val b: Bitacora, val resultado: () -> ResultadoSincronizacion) : RepositorioRegistros {
    override fun deObra(obraId: Int): Flow<List<RegistroLocal>> = flowOf(emptyList())
    override val pendientes: Flow<Int> = flowOf(0)
    override suspend fun registrarAsistencia(obraId: Int, fecha: String, marcas: List<MarcaAsistencia>) = emptyList<RegistroLocal>()
    override suspend fun yaMarcados(obraId: Int, fecha: String) = emptySet<Int>()
    override suspend fun registrarAlmacen(obraId: Int, registro: RegistroAlmacen): RegistroLocal = error("no se usa")
    override suspend fun sincronizar(manual: Boolean): ResultadoSincronizacion { b.pasos += "registros manual=$manual"; return resultado() }
}

class CasosDeUsoTest {
    private val cemento = Insumo(7, "PRUEBA-001", "Cemento", "bls")

    @Test
    fun registrarSalidaGuardaPrimeroYLuegoPideSincronizar() = runTest {
        val b = Bitacora()
        val programador = object : ProgramadorSincronizacion { override fun programar() { b.pasos += "programar envío" } }
        val caso = RegistrarSalida(SalidasEspia(b) { ResultadoSincronizacion(0, 0, 0) }, programador)

        val s = caso(NuevaSalida(1, cemento, "5", "Vaciado", "Juan Pérez", null))

        assertEquals(listOf("guardar en el celular", "programar envío"), b.pasos, "offline-first: nunca espera a la red")
        assertEquals(EstadoEnvio.PENDIENTE, s.estado)
    }

    @Test
    fun sincronizarColaSumaLasDosColasYPasaElModo() = runTest {
        val b = Bitacora()
        val caso = SincronizarCola(SalidasEspia(b) { ResultadoSincronizacion(2, 1, 0) }, RegistrosEspia(b) { ResultadoSincronizacion(3, 0, 1) })

        val r = caso(manual = true)

        assertEquals(ResultadoSincronizacion(5, 1, 1), r)
        assertEquals(listOf("salidas manual=true", "registros manual=true"), b.pasos)
    }

    @Test
    fun siUnaColaFallaLaOtraIgualSeEnvia() = runTest {
        val b = Bitacora()
        val caso = SincronizarCola(SalidasEspia(b) { throw IllegalStateException("sin señal") }, RegistrosEspia(b) { ResultadoSincronizacion(1, 0, 0) })

        val r = caso()

        assertEquals(1, r.enviados)
        assertEquals(listOf("salidas manual=false", "registros manual=false"), b.pasos)
    }
}
