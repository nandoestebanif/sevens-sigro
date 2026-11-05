package pe.sevens.sigro

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.sevens.sigro.datos.local.AjusteDao
import pe.sevens.sigro.datos.local.AjusteEntity
import pe.sevens.sigro.datos.local.RegistroDao
import pe.sevens.sigro.datos.local.RegistroLocalEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.datos.repositorio.RepositorioRegistrosImpl
import pe.sevens.sigro.dominio.EstadoAsistencia
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.LineaAlmacen
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.NOTA_SIN_SERVICIO_ALMACEN
import pe.sevens.sigro.dominio.OrigenIngreso
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.TipoRegistro
import pe.sevens.sigro.dominio.Trabajador
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Cola general (asistencia HU-63 y almacén) sin base ni servidor. Las mismas reglas que SincronizacionTest
 * prueba para las salidas: nace con uuid, se guarda antes de enviar, el reenvío no duplica, el rechazo
 * queda con su motivo. Y lo propio: lo de almacén NO se envía mientras no exista su servicio.
 */

private class AjustesFalsos : AjusteDao {
    val m = MutableStateFlow(mapOf("servidor" to "http://api.prueba", "token" to "tkn"))
    override suspend fun guardar(a: AjusteEntity) { m.value = m.value + (a.clave to a.valor) }
    override suspend fun leer(clave: String) = m.value[clave]
    override fun observar(): Flow<List<AjusteEntity>> = m.map { it.map { (k, v) -> AjusteEntity(k, v) } }
    override suspend fun quitar(claves: List<String>) { m.value = m.value - claves.toSet() }
}

private class RegistrosEnMemoria : RegistroDao {
    val filas = MutableStateFlow<List<RegistroLocalEntity>>(emptyList())
    override suspend fun insertar(r: RegistroLocalEntity) {
        check(filas.value.none { it.uuidCliente == r.uuidCliente }) { "uuid repetido" }
        filas.value = filas.value + r
    }
    override fun observarDeObra(obraId: Int) = filas.map { l -> l.filter { it.obraId == obraId } }
    override suspend fun pendientes(tipos: List<String>) =
        filas.value.filter { it.estado == "PENDIENTE" && it.tipo in tipos }.sortedBy { it.horaDispositivoMs }
    override fun contarPendientes(tipos: List<String>) = filas.map { l -> l.count { it.estado == "PENDIENTE" && it.tipo in tipos } }
    override suspend fun clavesVigentes(obraId: Int, prefijo: String) =
        filas.value.filter { it.obraId == obraId && it.clave?.startsWith(prefijo) == true && it.estado != "RECHAZADO" }.mapNotNull { it.clave }
    override suspend fun marcar(uuid: String, estado: String, motivoRechazo: String?, nota: String?, ahora: Long) {
        filas.value = filas.value.map {
            if (it.uuidCliente == uuid) it.copy(estado = estado, motivoRechazo = motivoRechazo, nota = nota, intentos = it.intentos + 1, ultimoIntentoMs = ahora) else it
        }
    }
    fun una(uuid: String) = filas.value.first { it.uuidCliente == uuid }
}

private class Cola(var responder: (Int) -> Pair<HttpStatusCode, String>?) {
    val cuerpos = mutableListOf<String>()
    val rutas = mutableListOf<String>()
    val filas = RegistrosEnMemoria()
    private val motor = MockEngine { req ->
        cuerpos += req.body.toByteArray().decodeToString()
        rutas += req.url.encodedPath
        val r = responder(cuerpos.size) ?: throw RuntimeException("sin señal")
        respond(r.second, r.first, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    var hora = 5_000L
    val repo = RepositorioRegistrosImpl(ApiSigro(crearClienteHttp(motor), jsonSigro), filas, credencialesDePrueba(AjustesFalsos())) { hora++ }
}

private fun trabajador(id: Int) = Trabajador(id, 1, "Trabajador 0$id", "DNI", "0000000$id", "operario", "Cuadrilla A", "2026-10-01", null)
private val arena = Insumo(423, "423", "ARENA GRUESA", "m3")

class ColaRegistrosTest {

    @Test
    fun asistenciaNaceConUuidSeGuardaSinSenalYNoLlamaAlServidor() = runTest {
        val c = Cola { HttpStatusCode.Created to "{}" }
        val r = c.repo.registrarAsistencia(1, "2026-11-01", listOf(MarcaAsistencia(trabajador(1), EstadoAsistencia.PRESENTE, 8, 2)))
        assertEquals(1, r.size)
        assertEquals(36, r[0].uuidCliente.length)
        assertEquals(EstadoEnvio.PENDIENTE, r[0].estado)
        assertTrue(c.cuerpos.isEmpty(), "registrar no necesita señal")
        val cuerpo = jsonSigro.parseToJsonElement(c.filas.una(r[0].uuidCliente).cuerpo).jsonObject
        assertEquals(r[0].uuidCliente, cuerpo["uuid_cliente"]!!.jsonPrimitive.content, "el uuid viaja dentro del cuerpo (regla 3)")
        assertEquals("8", cuerpo["horas_normales"]!!.jsonPrimitive.content)
        assertEquals("2", cuerpo["horas_extra"]!!.jsonPrimitive.content)
    }

    @Test
    fun ausenteYPermisoVanSinHoras() = runTest {
        val c = Cola { HttpStatusCode.Created to "{}" }
        val r = c.repo.registrarAsistencia(1, "2026-11-01", listOf(MarcaAsistencia(trabajador(2), EstadoAsistencia.AUSENTE, 8, 3)))
        val cuerpo = jsonSigro.parseToJsonElement(c.filas.una(r[0].uuidCliente).cuerpo).jsonObject
        assertEquals("0", cuerpo["horas_normales"]!!.jsonPrimitive.content)
        assertEquals("0", cuerpo["horas_extra"]!!.jsonPrimitive.content)
        assertEquals("ausente", cuerpo["estado"]!!.jsonPrimitive.content)
    }

    @Test
    fun noAdmiteDosAsistenciasDelMismoTrabajadorElMismoDia() = runTest {
        val c = Cola { HttpStatusCode.Created to "{}" }
        val m = listOf(MarcaAsistencia(trabajador(1), EstadoAsistencia.PRESENTE))
        c.repo.registrarAsistencia(1, "2026-11-01", m)
        val otra = c.repo.registrarAsistencia(1, "2026-11-01", m)
        assertTrue(otra.isEmpty(), "HU-63: el registro del día no admite dos asistencias para el mismo trabajador")
        assertEquals(setOf(1), c.repo.yaMarcados(1, "2026-11-01"))
        assertEquals(1, c.repo.registrarAsistencia(1, "2026-11-02", m).size, "otro día sí")
    }

    @Test
    fun sinSenalQuedaPendienteYElReenvioEsIdentico() = runTest {
        val c = Cola { n -> if (n == 1) null else HttpStatusCode.OK to "{}" }
        val r = c.repo.registrarAsistencia(1, "2026-11-01", listOf(MarcaAsistencia(trabajador(1), EstadoAsistencia.PRESENTE)))[0]
        assertEquals(1, c.repo.sincronizar().siguenPendientes)
        assertEquals("PENDIENTE", c.filas.una(r.uuidCliente).estado)
        assertEquals(1, c.repo.sincronizar().enviados)
        assertEquals("ENVIADO", c.filas.una(r.uuidCliente).estado)
        assertEquals(2, c.cuerpos.size)
        assertEquals(c.cuerpos[0], c.cuerpos[1], "regla 3: el reenvío manda el mismo cuerpo con el mismo uuid")
        assertEquals("/api/v1/obras/1/jornadas", c.rutas.last())
        assertEquals(1, c.filas.filas.value.size, "nada se borra de la cola")
    }

    @Test
    fun rechazoQuedaConSuMotivoYLiberaElDia() = runTest {
        val c = Cola {
            HttpStatusCode.UnprocessableEntity to """{"detail":{"codigo":"fuera_de_plazo","mensaje":"La fecha está fuera del plazo de la obra."}}"""
        }
        val r = c.repo.registrarAsistencia(1, "2026-11-01", listOf(MarcaAsistencia(trabajador(1), EstadoAsistencia.PRESENTE)))[0]
        assertEquals(1, c.repo.sincronizar().rechazados)
        val f = c.filas.una(r.uuidCliente)
        assertEquals("RECHAZADO", f.estado)
        assertEquals("La fecha está fuera del plazo de la obra.", f.motivoRechazo)
        assertTrue(c.repo.yaMarcados(1, "2026-11-01").isEmpty(), "rechazada no cuenta: se puede volver a marcar")
    }

    @Test
    fun almacenSeGuardaConSuNotaYNoSeEnvia() = runTest {
        val c = Cola { HttpStatusCode.Created to "{}" }
        val r = c.repo.registrarAlmacen(
            1, RegistroAlmacen.IngresoSinCompra(3, OrigenIngreso.DEVOLUCION, LineaAlmacen(arena, "1,5"), "60", null),
        )
        assertEquals(TipoRegistro.INGRESO_SIN_COMPRA, r.tipo)
        assertEquals(NOTA_SIN_SERVICIO_ALMACEN, r.nota)
        assertEquals(EstadoEnvio.PENDIENTE, r.estado)
        val s = c.repo.sincronizar()
        assertTrue(c.cuerpos.isEmpty(), "no hay servicio almacén todavía: no se envía")
        assertEquals(0, s.siguenPendientes, "no cuenta como pendiente: el trabajo de fondo no reintenta en vano")
        assertEquals(0, c.repo.pendientes.first())
        val cuerpo = jsonSigro.parseToJsonElement(c.filas.una(r.uuidCliente).cuerpo).jsonObject
        assertEquals(r.uuidCliente, cuerpo["uuid_cliente"]!!.jsonPrimitive.content)
        assertEquals("1.5", cuerpo["cantidad"]!!.jsonPrimitive.content)
    }
}
