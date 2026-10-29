package pe.sevens.sigro

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import pe.sevens.sigro.datos.local.AjusteDao
import pe.sevens.sigro.datos.local.AjusteEntity
import pe.sevens.sigro.datos.local.SalidaDao
import pe.sevens.sigro.datos.local.SalidaLocalEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.datos.repositorio.RepositorioSalidasImpl
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.NuevaSalida
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/*
 * Pruebas de HU-37 y HU-38 sin base ni servidor: Room y la API se reemplazan por dobles.
 * Cubren las reglas críticas de la DoD para la app: idempotencia (mismo uuid al reenviar),
 * nada se pierde (rechazo con motivo), y el corte ante falta de señal.
 */

private class AjustesEnMemoria : AjusteDao {
    val m = MutableStateFlow(mapOf("servidor" to "http://api.prueba", "token" to "tkn"))
    override suspend fun guardar(a: AjusteEntity) { m.value = m.value + (a.clave to a.valor) }
    override suspend fun leer(clave: String) = m.value[clave]
    override fun observar(): Flow<List<AjusteEntity>> = m.map { it.map { (k, v) -> AjusteEntity(k, v) } }
    override suspend fun quitar(claves: List<String>) { m.value = m.value - claves.toSet() }
}

private class SalidasEnMemoria : SalidaDao {
    val filas = MutableStateFlow<List<SalidaLocalEntity>>(emptyList())
    override suspend fun insertar(s: SalidaLocalEntity) {
        check(filas.value.none { it.uuidCliente == s.uuidCliente }) { "uuid repetido" }
        filas.value = filas.value + s
    }
    override fun observarDeObra(obraId: Int) = filas.map { l -> l.filter { it.obraId == obraId } }
    override suspend fun pendientes() = filas.value.filter { it.estado == "PENDIENTE" }.sortedBy { it.horaDispositivoMs }
    override fun contarPendientes() = filas.map { l -> l.count { it.estado == "PENDIENTE" } }
    override suspend fun marcar(uuid: String, estado: String, motivoRechazo: String?, nota: String?, ahora: Long) {
        filas.value = filas.value.map {
            if (it.uuidCliente == uuid) it.copy(estado = estado, motivoRechazo = motivoRechazo, nota = nota, intentos = it.intentos + 1, ultimoIntentoMs = ahora) else it
        }
    }
    fun una(uuid: String) = filas.value.first { it.uuidCliente == uuid }
}

/** Servidor falso: cada petición recibe la respuesta que diga `responder` y queda anotada. */
private class Escenario(var responder: (Int) -> Pair<HttpStatusCode, String>?) {
    val cuerpos = mutableListOf<String>()
    val salidas = SalidasEnMemoria()
    val ajustes = AjustesEnMemoria()
    private val motor = MockEngine { req ->
        cuerpos += req.body.toByteArray().decodeToString()
        val r = responder(cuerpos.size) ?: throw RuntimeException("sin señal")
        respond(r.second, r.first, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    var hora = 1_000L
    val repo = RepositorioSalidasImpl(ApiSigro(crearClienteHttp(motor), jsonSigro), salidas, ajustes) { hora++ }
}

private val cemento = Insumo(7, "PRUEBA-001", "Cemento Portland tipo I", "bls")
private fun nueva(cantidad: String = "12,5") = NuevaSalida(1, cemento, cantidad, "Vaciado de losa", "Juan Pérez", null)

class SincronizacionTest {

    @Test
    fun registrarGuardaPendienteConUuidYHoraDelCelular() = runTest {
        val e = Escenario { HttpStatusCode.Created to "{}" }
        val s = e.repo.registrar(nueva())
        assertEquals(EstadoEnvio.PENDIENTE, s.estado)
        assertEquals(36, s.uuidCliente.length)
        assertEquals("12.5", s.cantidad, "la coma de obra se guarda como punto decimal")
        assertEquals(1_000L, s.horaDispositivoMs)
        assertTrue(e.cuerpos.isEmpty(), "registrar no necesita señal: no llama al servidor")
    }

    @Test
    fun registrarRechazaDatosIncompletos() = runTest {
        val e = Escenario { HttpStatusCode.Created to "{}" }
        assertFailsWith<IllegalArgumentException> { e.repo.registrar(nueva(cantidad = "0")) }
        assertTrue(e.salidas.filas.value.isEmpty())
    }

    @Test
    fun envioCorrectoQuedaEnviadoYNoSeBorra() = runTest {
        val e = Escenario { HttpStatusCode.Created to "{}" }
        val s = e.repo.registrar(nueva())
        val r = e.repo.sincronizar()
        assertEquals(1, r.enviados)
        assertEquals("ENVIADO", e.salidas.una(s.uuidCliente).estado)
        assertEquals(1, e.salidas.filas.value.size, "la fila se conserva como historial")
    }

    @Test
    fun sinSenalSigueEnColaYElReenvioUsaElMismoUuid() = runTest {
        val e = Escenario { n -> if (n == 1) null else HttpStatusCode.OK to "{}" }
        val s = e.repo.registrar(nueva())

        val r1 = e.repo.sincronizar()
        assertEquals(1, r1.siguenPendientes)
        assertEquals("PENDIENTE", e.salidas.una(s.uuidCliente).estado)

        val r2 = e.repo.sincronizar()
        assertEquals(1, r2.enviados)
        val uuids = e.cuerpos.map { jsonSigro.parseToJsonElement(it).toString() }
        assertTrue(uuids.all { s.uuidCliente in it }, "regla 3: el reenvío lleva el mismo uuid_cliente")
    }

    @Test
    fun sinSenalCortaElLoteParaNoGastarBateria() = runTest {
        val e = Escenario { null }
        repeat(3) { e.repo.registrar(nueva()) }
        e.repo.sincronizar()
        assertEquals(1, e.cuerpos.size, "si no hay señal para la primera, no se intenta el resto")
    }

    @Test
    fun rechazoDeNegocioQuedaConSuMotivo() = runTest {
        val e = Escenario {
            HttpStatusCode.UnprocessableEntity to """{"detail":{"codigo":"stock_insuficiente","mensaje":"Solo hay 10 bls de cemento en el almacén."}}"""
        }
        val s = e.repo.registrar(nueva())
        val r = e.repo.sincronizar()
        assertEquals(1, r.rechazados)
        val f = e.salidas.una(s.uuidCliente)
        assertEquals("RECHAZADO", f.estado)
        assertEquals("Solo hay 10 bls de cemento en el almacén.", f.motivoRechazo)
    }

    @Test
    fun servidorSinLaFuncionDejaPendienteConNota() = runTest {
        val e = Escenario { HttpStatusCode.NotFound to """{"detail":"Not Found"}""" }
        val s = e.repo.registrar(nueva())
        e.repo.sincronizar()
        val f = e.salidas.una(s.uuidCliente)
        assertEquals("PENDIENTE", f.estado)
        assertNotNull(f.nota)
    }

    @Test
    fun uuidRepetidoEnElServidorCuentaComoEnviado() = runTest {
        val e = Escenario { HttpStatusCode.Conflict to """{"codigo":"uuid_repetido","mensaje":"Ya recibido"}""" }
        val s = e.repo.registrar(nueva())
        e.repo.sincronizar()
        assertEquals("ENVIADO", e.salidas.una(s.uuidCliente).estado)
    }
}
