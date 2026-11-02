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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.sevens.sigro.datos.local.AjusteDao
import pe.sevens.sigro.datos.local.AjusteEntity
import pe.sevens.sigro.datos.local.RegistroDao
import pe.sevens.sigro.datos.local.RegistroLocalEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.datos.repositorio.RepositorioObraImpl
import pe.sevens.sigro.datos.repositorio.RepositorioRegistrosImpl
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.EstadoAsistencia
import pe.sevens.sigro.dominio.LineaCompra
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.LineaAlmacen
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.MotivoDiferencia
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
    val repo = RepositorioRegistrosImpl(ApiSigro(crearClienteHttp(motor), jsonSigro), filas, AjustesFalsos()) { hora++ }
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
    fun cierreSeGuardaConSuNotaYNoSeEnvia() = runTest {
        val c = Cola { HttpStatusCode.Created to "{}" }
        val r = c.repo.registrarAlmacen(1, RegistroAlmacen.Cierre(3, "2026-11-01", null))
        assertEquals(TipoRegistro.CIERRE, r.tipo)
        assertEquals(NOTA_SIN_SERVICIO_ALMACEN, r.nota)
        assertEquals(EstadoEnvio.PENDIENTE, r.estado)
        val s = c.repo.sincronizar()
        assertTrue(c.cuerpos.isEmpty(), "HU-47 aún no tiene servicio: no se envía")
        assertEquals(0, s.siguenPendientes, "no cuenta como pendiente: el trabajo de fondo no reintenta en vano")
        assertEquals(0, c.repo.pendientes.first())
    }

    // ── Servicio almacén (PR #19): ingreso sin compra ─────────────────────────────────────

    private suspend fun Cola.ingreso() = repo.registrarAlmacen(
        1, RegistroAlmacen.IngresoSinCompra(3, OrigenIngreso.DEVOLUCION, LineaAlmacen(arena, "1,5"), "60", null),
    )

    @Test
    fun ingresoViajaConSuContratoYElCelular() = runTest {
        val c = Cola { HttpStatusCode.Created to "{}" }
        val r = c.ingreso()
        assertEquals(null, r.nota, "con servicio no lleva la nota de «sin servicio»")
        assertEquals(1, c.repo.sincronizar().enviados)
        assertEquals("/api/v1/obras/1/almacen/ingresos", c.rutas.single())
        val cuerpo = jsonSigro.parseToJsonElement(c.cuerpos.single()).jsonObject
        assertEquals(r.uuidCliente, cuerpo["uuid_cliente"]!!.jsonPrimitive.content)
        assertEquals("devolucion_cuadrilla", cuerpo["origen"]!!.jsonPrimitive.content)
        assertEquals("1.5", cuerpo["cantidad"]!!.jsonPrimitive.content)
        assertTrue(cuerpo["dispositivo"]!!.jsonPrimitive.content.startsWith("sigro-"), "regla 5: el celular viaja en el cuerpo")
        assertEquals("ENVIADO", c.filas.una(r.uuidCliente).estado)
    }

    @Test
    fun reenvioQueDevuelve200CuentaComoEnviado() = runTest {
        val c = Cola { HttpStatusCode.OK to """{"id": 9, "uuid_cliente": "x"}""" }
        val r = c.ingreso()
        c.repo.sincronizar()
        assertEquals("ENVIADO", c.filas.una(r.uuidCliente).estado, "regla 3: 200 con el original no es error")
    }

    private fun rechazoCon(status: HttpStatusCode, codigo: String, mensaje: String) = runTest {
        val c = Cola { status to """{"codigo":"$codigo","mensaje":"$mensaje"}""" }
        val r = c.ingreso()
        assertEquals(1, c.repo.sincronizar().rechazados, codigo)
        val f = c.filas.una(r.uuidCliente)
        assertEquals("RECHAZADO", f.estado, codigo)
        assertEquals(mensaje, f.motivoRechazo, codigo)
    }

    @Test fun celularNoAutorizadoQuedaRechazadoConSuMotivo() =
        rechazoCon(HttpStatusCode.Forbidden, "dispositivo_no_autorizado", "Este registro solo se acepta desde un celular autorizado.")

    @Test fun periodoCerradoQuedaRechazado() =
        rechazoCon(HttpStatusCode.Conflict, "periodo_cerrado", "El periodo de esa fecha está cerrado.")

    @Test fun saldoDeOtraObraRegistradaPideTraslado() =
        rechazoCon(HttpStatusCode.UnprocessableEntity, "usar_traslado", "La obra está registrada en SIGRO: registra un traslado.")

    @Test fun uuidReutilizadoEsUnConflictoNoUnReenvio() =
        rechazoCon(HttpStatusCode.Conflict, "uuid_reutilizado", "Ese identificador ya se usó para otro movimiento.")

    // ── Entrada de compra → POST /almacen/recepciones ──────────────────────────────────────

    private val compra = CompraParaRecibir(10, "[Proveedor]", "Factura [número]", "2026-10-27", listOf(
        LineaCompra("1151", "CEMENTO PORTLAND TIPO I - 42.5 KG", "100", "bolsas", insumoId = 1151),
        LineaCompra("423", "ARENA GRUESA", "6", "metro cúbico", insumoId = 423),
    ))

    @Test
    fun recepcionLlegoTodoViajaSinLineas() = runTest {
        val c = Cola { HttpStatusCode.Created to "{}" }
        c.repo.registrarAlmacen(1, RegistroAlmacen.Entrada(2, compra, compra.lineas.map { it to it.cantidad }, todo = true))
        c.repo.sincronizar()
        assertEquals("/api/v1/obras/1/almacen/recepciones", c.rutas.single())
        val cuerpo = jsonSigro.parseToJsonElement(c.cuerpos.single()).jsonObject
        assertEquals(JsonNull, cuerpo["lineas"], "sin líneas = el total pendiente")
        assertEquals("10", cuerpo["compra_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun recepcionParcialViajaPorInsumoId() = runTest {
        val c = Cola { HttpStatusCode.Created to "{}" }
        c.repo.registrarAlmacen(1, RegistroAlmacen.Entrada(2, compra, listOf(compra.lineas[0] to "60"), todo = false))
        c.repo.sincronizar()
        val l = jsonSigro.parseToJsonElement(c.cuerpos.single()).jsonObject["lineas"]!!.jsonArray.single().jsonObject
        assertEquals("1151", l["insumo_id"]!!.jsonPrimitive.content)
        assertEquals("60", l["cantidad"]!!.jsonPrimitive.content)
    }

    // ── Traslado → POST /almacen/traslados ─────────────────────────────────────────────────

    @Test
    fun trasladoViajaConAlmacenDestinoYMotivo() = runTest {
        val c = Cola { HttpStatusCode.Created to "{}" }
        c.repo.registrarAlmacen(1, RegistroAlmacen.Traslado(3, 2, "Otra obra", 7, "Falta cemento allá", listOf(LineaAlmacen(arena, "2"))))
        c.repo.sincronizar()
        assertEquals("/api/v1/obras/1/almacen/traslados", c.rutas.single())
        val cuerpo = jsonSigro.parseToJsonElement(c.cuerpos.single()).jsonObject
        assertEquals("7", cuerpo["destino_almacen_id"]!!.jsonPrimitive.content)
        assertEquals("2", cuerpo["destino_obra_id"]!!.jsonPrimitive.content)
        assertEquals("Falta cemento allá", cuerpo["motivo"]!!.jsonPrimitive.content)
        assertEquals("423", cuerpo["lineas"]!!.jsonArray.single().jsonObject["insumo_id"]!!.jsonPrimitive.content)
    }

    // ── Conteo → abrir, contar las líneas que faltan y confirmar ──────────────────────────

    private val cemento = Insumo(1151, "1151", "CEMENTO PORTLAND TIPO I - 42.5 KG", "bolsas")

    @Test
    fun conteoAbreCuentaYConfirma() = runTest {
        val c = Cola { n ->
            when (n) {
                1 -> HttpStatusCode.Created to """{"id": 5, "estado": "borrador", "lineas": []}"""
                else -> HttpStatusCode.Created to """{"id": 5, "estado": "borrador", "lineas": []}"""
            }
        }
        c.repo.registrarAlmacen(1, RegistroAlmacen.Conteo(3, "2026-11-01", listOf(
            LineaAlmacen(arena, "4,5") to null, LineaAlmacen(cemento, "36") to MotivoDiferencia.MERMA,
        )))
        assertEquals(1, c.repo.sincronizar().enviados)
        assertEquals(
            listOf("/api/v1/obras/1/almacen/conteos", "/api/v1/obras/1/almacen/conteos/5/lineas",
                "/api/v1/obras/1/almacen/conteos/5/lineas", "/api/v1/obras/1/almacen/conteos/5/confirmar"),
            c.rutas,
        )
        val apertura = jsonSigro.parseToJsonElement(c.cuerpos[0]).jsonObject
        assertTrue("lineas" !in apertura, "ConteoIn no admite campos de más (extra=forbid)")
        assertEquals("4.5", jsonSigro.parseToJsonElement(c.cuerpos[1]).jsonObject["cantidad_contada"]!!.jsonPrimitive.content)
        assertEquals("Merma", jsonSigro.parseToJsonElement(c.cuerpos[2]).jsonObject["motivo"]!!.jsonPrimitive.content)
    }

    @Test
    fun conteoReenviadoNoRepiteLoQueYaLlego() = runTest {
        // 1.er intento: abre y cuenta la arena, se corta la señal en el cemento. 2.º: el servidor devuelve el conteo con la arena.
        val c = Cola { n ->
            when (n) {
                1 -> HttpStatusCode.Created to """{"id": 5, "estado": "borrador", "lineas": []}"""
                2 -> HttpStatusCode.Created to """{"id": 5, "estado": "borrador", "lineas": [{"insumo_id": 423}]}"""
                3 -> null
                4 -> HttpStatusCode.OK to """{"id": 5, "estado": "borrador", "lineas": [{"insumo_id": 423}]}"""
                else -> HttpStatusCode.Created to """{"id": 5, "estado": "confirmado", "lineas": [{"insumo_id": 423}, {"insumo_id": 1151}]}"""
            }
        }
        val r = c.repo.registrarAlmacen(1, RegistroAlmacen.Conteo(3, "2026-11-01", listOf(
            LineaAlmacen(arena, "4.5") to null, LineaAlmacen(cemento, "36") to MotivoDiferencia.MERMA,
        )))
        assertEquals(1, c.repo.sincronizar().siguenPendientes)
        assertEquals("PENDIENTE", c.filas.una(r.uuidCliente).estado)
        c.repo.sincronizar()
        assertEquals("ENVIADO", c.filas.una(r.uuidCliente).estado)
        val lineasEnviadas = c.rutas.filter { it.endsWith("/lineas") }.size
        assertEquals(3, lineasEnviadas, "arena una vez; cemento: el intento sin señal y el bueno")
        assertEquals(c.cuerpos[0], c.cuerpos[3], "la apertura se reenvía idéntica (mismo uuid_cliente)")
    }

    @Test
    fun conteoConDiferenciaSinMotivoQuedaRechazado() = runTest {
        val c = Cola { n ->
            if (n == 1) HttpStatusCode.Created to """{"id": 5, "estado": "borrador", "lineas": []}"""
            else HttpStatusCode.UnprocessableEntity to """{"codigo":"diferencia_sin_motivo","mensaje":"El sistema tiene 38 y se contó 36: toda diferencia exige un motivo."}"""
        }
        val r = c.repo.registrarAlmacen(1, RegistroAlmacen.Conteo(3, "2026-11-01", listOf(LineaAlmacen(cemento, "36") to null)))
        c.repo.sincronizar()
        assertEquals("RECHAZADO", c.filas.una(r.uuidCliente).estado)
        assertTrue(c.filas.una(r.uuidCliente).motivoRechazo!!.contains("exige un motivo"))
    }

    // ── Stock: el del servidor; sin señal, el último recibido con su hora ────────────────

    @Test
    fun stockSinSenalMuestraElUltimoRecibidoConSuHora() = runTest {
        val json = """{"criterio":"x","corte":"2026-11-01T16:35:00","por_almacen":[{"almacen_id":3,"almacen":"Principal","insumo_id":1151,"insumo_codigo":"1151","descripcion":"CEMENTO","unidad":"bolsas","cantidad":"38.0000","costo_promedio":"22","valor":"836","ultimo_movimiento_en":"2026-11-01T16:35:00"}],"consolidado":[]}"""
        var conSenal = true
        val ajustes = AjustesFalsos()
        val motor = MockEngine { if (conSenal) respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) else throw RuntimeException("sin señal") }
        val repo = RepositorioObraImpl(ApiSigro(crearClienteHttp(motor), jsonSigro), ajustes) { 7_000L }
        val a = repo.stock(1, 3)
        assertEquals("38.0000", a.filas.single().cantidad)
        assertEquals(false, a.desdeCelular)
        conSenal = false
        val b = repo.stock(1, 3)
        assertEquals(true, b.desdeCelular, "sin señal: lo último que mandó el servidor, marcado")
        assertEquals(7_000L, b.recibidoMs)
        assertEquals("38.0000", b.filas.single().cantidad, "regla 2: el número es del servidor, no de la app")
    }
}
