package pe.sevens.sigro

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.MENSAJE_SIN_RESPUESTA
import pe.sevens.sigro.datos.remoto.crearClienteHttp
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.datos.repositorio.BovedaSesion
import pe.sevens.sigro.datos.repositorio.DecisionEnvio
import pe.sevens.sigro.datos.repositorio.RepositorioDispositivoImpl
import pe.sevens.sigro.datos.repositorio.RepositorioRegistrosImpl
import pe.sevens.sigro.datos.repositorio.RepositorioSalidasImpl
import pe.sevens.sigro.datos.repositorio.RepositorioSesionImpl
import pe.sevens.sigro.datos.repositorio.RepositorioStockImpl
import pe.sevens.sigro.datos.repositorio.Servidores
import pe.sevens.sigro.datos.repositorio.decidirEnvio
import pe.sevens.sigro.datos.repositorio.reciboDe
import pe.sevens.sigro.dominio.EstadoDispositivo
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.FilaStock
import pe.sevens.sigro.dominio.HoraLima
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.LineaAlmacen
import pe.sevens.sigro.dominio.NuevaSalida
import pe.sevens.sigro.dominio.OrigenIngreso
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.StockObra
import pe.sevens.sigro.ui.pantallas.CelularEnObra
import pe.sevens.sigro.ui.pantallas.EstadoPantallaDispositivo
import pe.sevens.sigro.ui.pantallas.VISTA_TODA_LA_OBRA
import pe.sevens.sigro.ui.pantallas.celularEnObra
import pe.sevens.sigro.ui.pantallas.detalleRegistro
import pe.sevens.sigro.ui.pantallas.filasStock
import pe.sevens.sigro.ui.pantallas.lineaRegistro
import pe.sevens.sigro.ui.pantallas.textoCorte
import pe.sevens.sigro.ui.pantallas.unirRegistros
import pe.sevens.sigro.ui.pantallas.validarIngreso
import pe.sevens.sigro.ui.pantallas.vistasStock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Arreglos de la prueba del 05/11 (A1–A5), sin base ni servidor: Room y la API se reemplazan por dobles.
 */

/** Servidor falso por ruta; `null` = sin señal. Anota rutas y cuerpos. */
private class Banco(
    ajustes: Map<String, String> = mapOf("servidor" to "http://api.prueba", "usuario_id" to "5", "nombre" to "Nicolás Almacenero"),
    val responder: (ruta: String, metodo: String, n: Int) -> Pair<HttpStatusCode, String>?,
) {
    val rutas = mutableListOf<String>()
    val cuerpos = mutableListOf<String>()
    val ajustes = AjustesDePrueba(ajustes)
    val seguro = AlmacenSeguroEnMemoria(mapOf(BovedaSesion.CLAVE_TOKEN to "tkn"))
    val boveda = BovedaSesion(seguro, this.ajustes)
    val cred = credencialesDePrueba(this.ajustes, seguro)
    private val motor = MockEngine { req ->
        rutas += req.url.encodedPath
        cuerpos += req.body.toByteArray().decodeToString()
        val r = responder(req.url.encodedPath, req.method.value, rutas.size) ?: throw RuntimeException("sin señal")
        respond(r.second, r.first, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val api = ApiSigro(crearClienteHttp(motor) { seguro.leer(BovedaSesion.CLAVE_TOKEN) }, jsonSigro)
    var hora = 1_793_898_000_000L // 05/11/2026 12:00 en Lima
    val salidasDao = SalidasDePrueba()
    val salidas = RepositorioSalidasImpl(api, salidasDao, cred) { hora }
    val registrosDao = RegistrosDePrueba()
    val registros = RepositorioRegistrosImpl(api, registrosDao, cred) { hora }
    val stockDao = StockGuardadoDePrueba()
    val stock = RepositorioStockImpl(api, stockDao, cred) { hora }
    val dispositivo = RepositorioDispositivoImpl(api, cred)
    val sesion = RepositorioSesionImpl(api, this.ajustes, boveda)
    fun ultimoCuerpo() = jsonSigro.parseToJsonElement(cuerpos.last()).jsonObject
}

private val cemento = Insumo(7, "1151", "CEMENTO PORTLAND TIPO I", "bls")
private fun salida(almacen: Int? = 3) = NuevaSalida(1, cemento, "12,5", "Vaciado de losa", "Juan Pérez", null, almacen)

private const val MOVIMIENTO_201 =
    """{"id":90,"uuid_cliente":"x","tipo":"salida","registrado_por":5,"fecha_servidor":"2026-11-05T17:20:31.123456Z","saldo":"10.0000"}"""

private const val STOCK_JSON = """{
  "criterio":"promedio ponderado","corte":"2026-11-05T15:00:00Z",
  "por_almacen":[
    {"almacen_id":3,"almacen":"Principal","insumo_id":7,"insumo_codigo":"1151","descripcion":"CEMENTO PORTLAND TIPO I","unidad":"bls","cantidad":"30.0000","valor":"0","ultimo_movimiento_en":"2026-11-05T15:00:00Z","bajo_punto_reposicion":true},
    {"almacen_id":4,"almacen":"Patio","insumo_id":7,"insumo_codigo":"1151","descripcion":"CEMENTO PORTLAND TIPO I","unidad":"bls","cantidad":"8.0000","valor":"0","ultimo_movimiento_en":"2026-11-05T14:00:00Z"},
    {"almacen_id":3,"almacen":"Principal","insumo_id":42,"insumo_codigo":"423","descripcion":"ARENA GRUESA","unidad":"m3","cantidad":"6.5000","valor":"0","ultimo_movimiento_en":"2026-11-05T13:00:00Z"}
  ],
  "consolidado":[
    {"almacen_id":null,"almacen":null,"insumo_id":7,"insumo_codigo":"1151","descripcion":"CEMENTO PORTLAND TIPO I","unidad":"bls","cantidad":"37.0000","valor":"0","ultimo_movimiento_en":"2026-11-05T15:00:00Z"},
    {"almacen_id":null,"almacen":null,"insumo_id":42,"insumo_codigo":"423","descripcion":"ARENA GRUESA","unidad":"m3","cantidad":"6.5000","valor":"0","ultimo_movimiento_en":"2026-11-05T13:00:00Z"}
  ],
  "advertencias":[]
}"""

class ArreglosPruebaTest {

    // ── A1 · detalle con quién registró y la hora del servidor ─────────────────────────────

    @Test
    fun horaDelServidorEnHoraDeLima() {
        assertEquals("05/11/2026 12:20", HoraLima.deIso("2026-11-05T17:20:31.123456Z"))
        assertEquals("05/11/2026 12:20", HoraLima.deIso("2026-11-05T17:20:31+00:00"))
        assertEquals("05/11/2026 12:20", HoraLima.deIso("2026-11-05T12:20:00-05:00"), "ya en hora de Lima, no cambia")
        assertEquals("31/12/2025 22:00", HoraLima.deIso("2026-01-01T03:00:00Z"), "cruza la medianoche y el año")
        assertEquals("29/02/2028 19:00", HoraLima.deIso("2028-03-01T00:00:00Z"), "año bisiesto")
        assertEquals("05/11/2026 12:20", HoraLima.deIso("2026-11-05T17:20:31"), "sin zona se toma como UTC")
        assertNull(HoraLima.deIso("ayer"))
        assertEquals("12:00", HoraLima.horaDeMs(1_793_898_000_000L))
    }

    @Test
    fun elReciboSaleDeLaRespuestaYElNombreDeLaSesion() {
        fun json(t: String) = jsonSigro.parseToJsonElement(t).jsonObject
        val propio = reciboDe(json(MOVIMIENTO_201), 5, "Nicolás Almacenero")!!
        assertEquals("Nicolás Almacenero", propio.registradoPor)
        assertEquals("2026-11-05T17:20:31.123456Z", propio.fechaServidor)
        assertEquals("usuario N.º 9", reciboDe(json("""{"registrado_por":9,"fecha_servidor":"2026-11-05T17:20:31Z"}"""), 5, "Nicolás")!!.registradoPor)
        assertEquals("Ana", reciboDe(json("""{"registrado_por":9,"registrado_por_nombre":"Ana","fecha_servidor":"x"}"""), 5, "Nicolás")!!.registradoPor)
        val recepcion = json("""{"id":1,"compra_id":10,"movimientos":[{"registrado_por":5,"fecha_servidor":"2026-11-05T17:20:31Z"}],"lineas":[]}""")
        assertEquals("Nicolás", reciboDe(recepcion, 5, "Nicolás")!!.registradoPor, "la recepción lo trae en sus movimientos")
        assertNull(reciboDe(null, 5, "Nicolás"), "409 sin cuerpo: no se inventa")
    }

    @Test
    fun alRecibirEl201SeGuardaQuienRegistroYLaHoraDelServidor() = runTest {
        val b = Banco { _, _, _ -> HttpStatusCode.Created to MOVIMIENTO_201 }
        val s = b.salidas.registrar(salida())
        b.salidas.sincronizar()
        val f = b.salidasDao.una(s.uuidCliente)
        assertEquals(5, f.registradoPorId)
        assertEquals("Nicolás Almacenero", f.registradoPor)
        assertEquals("2026-11-05T17:20:31.123456Z", f.fechaServidor)

        val fila = unirRegistros(b.salidas.salidasDeObra(1).first(), emptyList()).single()
        assertEquals("registró: Nicolás Almacenero · 05/11/2026 12:20", lineaRegistro(fila))
        val detalle = detalleRegistro(fila).toMap()
        assertEquals("CEMENTO PORTLAND TIPO I", detalle["Insumo"])
        assertEquals("12.5 bls", detalle["Cantidad"])
        assertEquals("Vaciado de losa", detalle["Motivo"])
        assertEquals("Juan Pérez", detalle["Quién retira"])
        assertEquals("Enviado", detalle["Estado"])
        assertEquals("registró: Nicolás Almacenero · 05/11/2026 12:20", detalle["Servidor"])
    }

    @Test
    fun elReenvioConElMismoUuidTambienDejaElRecibo() = runTest {
        val b = Banco { _, _, _ -> HttpStatusCode.OK to MOVIMIENTO_201 } // 200: el servidor ya lo tenía
        val s = b.salidas.registrar(salida())
        b.salidas.sincronizar()
        assertEquals("2026-11-05T17:20:31.123456Z", b.salidasDao.una(s.uuidCliente).fechaServidor)
    }

    @Test
    fun loQueNoSeEnvioNoMuestraRecibo() = runTest {
        val b = Banco { _, _, _ -> null }
        b.salidas.registrar(salida())
        b.salidas.sincronizar()
        val fila = unirRegistros(b.salidas.salidasDeObra(1).first(), emptyList()).single()
        assertEquals(EstadoEnvio.PENDIENTE, fila.estado)
        assertNull(lineaRegistro(fila), "la hora del celular no se presenta como la del servidor")
        assertFalse("Servidor" in detalleRegistro(fila).toMap())
    }

    @Test
    fun laColaGeneralTambienGuardaElRecibo() = runTest {
        val b = Banco { _, _, _ -> HttpStatusCode.Created to """{"id":1,"compra_id":10,"movimientos":[{"registrado_por":5,"fecha_servidor":"2026-11-05T17:20:31Z"}]}""" }
        val ingreso = RegistroAlmacen.IngresoSinCompra(3, OrigenIngreso.SOBRANTE, LineaAlmacen(cemento, "2"), "28.5", null)
        val r = b.registros.registrarAlmacen(1, ingreso)
        b.registros.sincronizar()
        val fila = unirRegistros(emptyList(), b.registros.deObra(1).first()).single()
        assertEquals("registró: Nicolás Almacenero · 05/11/2026 12:20", lineaRegistro(fila))
        assertEquals(r.uuidCliente, fila.clave)
    }

    // ── A2 · stock del servidor, con copia sin señal ─────────────────────────────────────────

    @Test
    fun conSenalElStockLlegaDelServidorYQuedaGuardado() = runTest {
        val b = Banco { _, _, _ -> HttpStatusCode.OK to STOCK_JSON }
        val s = b.stock.stock(1)
        assertEquals("/api/v1/obras/1/almacen/stock", b.rutas.single())
        assertFalse(s.desdeCelular)
        assertEquals(3, s.porAlmacen.size)
        assertEquals(listOf("Principal", "Patio", VISTA_TODA_LA_OBRA), vistasStock(s))
        assertEquals("Actualizado 05/11/2026 10:00", textoCorte(s))
        assertTrue(b.stockDao.m.containsKey(1), "se guarda para mostrarlo sin señal")
    }

    @Test
    fun sinSenalSeMuestraLaUltimaCopiaConSuHora() = runTest {
        var conSenal = true
        val b = Banco { _, _, _ -> if (conSenal) HttpStatusCode.OK to STOCK_JSON else null }
        b.stock.stock(1)
        conSenal = false
        b.hora += 3 * 3_600_000L // tres horas después
        val s = b.stock.stock(1)
        assertTrue(s.desdeCelular)
        assertEquals("Sin señal · es el de las 12:00", textoCorte(s), "la hora en que llegó la copia, no la de ahora")
        assertEquals("37.0000", filasStock(s, VISTA_TODA_LA_OBRA, "cemento").single().cantidad)
    }

    @Test
    fun conElServidorDormidoTambienSeUsaLaCopia() = runTest {
        var dormido = false
        val b = Banco { _, _, _ -> if (dormido) HttpStatusCode.ServiceUnavailable to "" else HttpStatusCode.OK to STOCK_JSON }
        b.stock.stock(1)
        dormido = true
        assertTrue(b.stock.stock(1).desdeCelular)
    }

    @Test
    fun sinSenalYSinCopiaSeDiceYNoSeInventa() = runTest {
        val b = Banco { _, _, _ -> null }
        val e = assertFailsWith<ErrorSigro> { b.stock.stock(1) }
        assertTrue(e.message.contains("no hay un stock guardado"))
    }

    @Test
    fun unRechazoDelServidorNoSeTapaConLaCopia() = runTest {
        var permitido = true
        val b = Banco { _, _, _ ->
            if (permitido) HttpStatusCode.OK to STOCK_JSON
            else HttpStatusCode.Forbidden to """{"codigo":"sin_permiso","mensaje":"No tienes permiso en esta obra."}"""
        }
        b.stock.stock(1)
        permitido = false
        assertEquals("No tienes permiso en esta obra.", assertFailsWith<ErrorSigro> { b.stock.stock(1) }.message)
    }

    @Test
    fun elConsolidadoEsElDelServidorYNoUnaSuma() {
        val f = { alm: String?, cant: String -> FilaStock(null, alm, 7, "1151", "CEMENTO PORTLAND TIPO I", "bls", cant, null) }
        // El servidor dice 37 en la obra aunque los almacenes visibles sumen 38: se muestra lo que dijo.
        val s = StockObra(null, listOf(f("Principal", "30"), f("Patio", "8")), listOf(f(null, "37")), emptyList(), 0L, false)
        assertEquals("37", filasStock(s, VISTA_TODA_LA_OBRA, "").single().cantidad)
        assertEquals("8", filasStock(s, "Patio", "").single().cantidad)
        assertEquals(1, filasStock(s, "Principal", "portland").size, "busca sin mayúsculas")
        assertTrue(filasStock(s, "Principal", "arena").isEmpty())
        assertEquals("Todavía no hay movimientos en esta obra", textoCorte(s))
    }

    // ── A3 · Mi celular ───────────────────────────────────────────────────────────────────────

    @Test
    fun un403AlConsultarNoSeMuestraComoSinSolicitar() = runTest {
        val b = Banco { _, _, _ -> HttpStatusCode.Forbidden to """{"codigo":"sin_permiso","mensaje":"No tienes permiso."}""" }
        val d = b.dispositivo.estado(1)
        assertFalse(d.visible)
        assertNotEquals("Sin solicitar", d.estadoLegible)
        assertTrue(EstadoPantallaDispositivo(d).puedePedir, "si no se sabe, puede pedirlo: el servidor dirá si ya estaba")
        assertEquals(CelularEnObra.NO_VISIBLE, celularEnObra(d.estado, consultado = true, visible = d.visible))
    }

    @Test
    fun activoEnElServidorEsAutorizadoEnLaApp() = runTest {
        var codigo = ""
        val b = Banco { ruta, _, _ ->
            assertEquals("/api/v1/obras/1/dispositivos/mios", ruta)
            HttpStatusCode.OK to """[{"id":1,"usuario_id":5,"identificador":"$codigo","estado":"activo"}]"""
        }
        codigo = b.dispositivo.identificador()
        val d = b.dispositivo.estado(1)
        assertEquals(EstadoDispositivo.AUTORIZADO, d.estado, "el servidor guarda «activo»; antes la app no lo reconocía")
        assertEquals("Autorizado", d.estadoLegible)
        assertFalse(EstadoPantallaDispositivo(d).puedePedir, "autorizado: no vuelve a ofrecer «Pedir autorización»")
        assertEquals(CelularEnObra.AUTORIZADO, celularEnObra(d.estado, consultado = true, visible = d.visible))
    }

    @Test
    fun alReinstalarSeAvisaQueHayOtroCelularActivo() = runTest {
        val b = Banco { _, _, _ ->
            HttpStatusCode.OK to """[{"id":1,"usuario_id":5,"identificador":"sigro-android-viejo","estado":"activo"}]"""
        }
        val d = b.dispositivo.estado(1)
        assertNull(d.estado, "este celular (código nuevo) todavía no se pidió")
        assertTrue(d.otroCelularActivo, "el anterior sigue activo: el administrador debe revocarlo")
        assertTrue(EstadoPantallaDispositivo(d).puedePedir)
    }

    @Test
    fun unServidorSinMiosUsaLaListaDeTodos() = runTest {
        var codigo = ""
        val b = Banco { ruta, _, _ ->
            if (ruta.endsWith("/mios")) HttpStatusCode.NotFound to """{"detail":"Not Found"}"""
            else HttpStatusCode.OK to """[{"id":1,"usuario_id":5,"identificador":"$codigo","estado":"pendiente"}]"""
        }
        codigo = b.dispositivo.identificador()
        assertEquals("pendiente", b.dispositivo.estado(1).estado)
        assertEquals(listOf("/api/v1/obras/1/dispositivos/mios", "/api/v1/obras/1/dispositivos"), b.rutas)
    }

    @Test
    fun un409AlPedirEsYaSolicitado() = runTest {
        val b = Banco { _, metodo, _ ->
            if (metodo == "POST") HttpStatusCode.Conflict to """{"codigo":"duplicado","mensaje":"Ya existe."}"""
            else HttpStatusCode.Forbidden to "{}"
        }
        val d = b.dispositivo.solicitarAutorizacion(1)
        assertEquals(EstadoDispositivo.YA_SOLICITADO, d.estado)
        assertEquals("Ya solicitado (pendiente o autorizado)", d.estadoLegible)
        assertFalse(EstadoPantallaDispositivo(d).puedePedir, "no se vuelve a ofrecer el botón")
        assertEquals(CelularEnObra.PENDIENTE, celularEnObra(d.estado, consultado = true))
    }

    // ── A4 · servidor que no responde y dirección vieja ──────────────────────────────────────

    @Test
    fun un503YSinRedDicenLoMismo() = runTest {
        val dormido = Banco { _, _, _ -> HttpStatusCode.ServiceUnavailable to "<html>Service Unavailable</html>" }
        val e1 = assertFailsWith<ErrorSigro> { dormido.stock.stock(1) }
        val sinRed = Banco { _, _, _ -> null }
        val e2 = assertFailsWith<ErrorSigro> { sinRed.dispositivo.estado(1) }
        assertTrue(e1.message.startsWith(MENSAJE_SIN_RESPUESTA))
        assertEquals(MENSAJE_SIN_RESPUESTA, e2.message)
        assertEquals("El servidor no responde. Revisa la dirección o inténtalo en un minuto.", MENSAJE_SIN_RESPUESTA)
        assertTrue(e1.esTransitorio && e2.esTransitorio, "la cola los reintenta, no los rechaza")
    }

    @Test
    fun un429Y408SonTransitoriosYLaColaLosReintenta() = runTest {
        for (http in listOf(408, 429, 500, 502, 503)) {
            val e = ErrorSigro("http_$http", "x", http)
            assertTrue(e.esTransitorio, "$http es transitorio")
            val d = decidirEnvio(e, "salidas")
            assertTrue(d is DecisionEnvio.Reintentar && d.cortar, "$http: queda PENDIENTE y corta el lote")
        }
    }

    @Test
    fun lasReglasDeNegocioSiSeRechazan() = runTest {
        for (http in listOf(400, 403, 422)) {
            val d = decidirEnvio(ErrorSigro("stock_insuficiente", "No alcanza el stock.", http), "salidas")
            assertEquals(DecisionEnvio.Rechazar("No alcanza el stock."), d, "$http se rechaza con su motivo")
        }
        // 404 con código = regla de negocio; 404 sin código = ruta que no existe (se espera).
        assertTrue(decidirEnvio(ErrorSigro("almacen_no_existe", "m", 404), "salidas") is DecisionEnvio.Rechazar)
        assertTrue(decidirEnvio(ErrorSigro("http_404", "m", 404), "salidas") is DecisionEnvio.Reintentar)
    }

    @Test
    fun un429DelServidorLlegaComoTransitorio() = runTest {
        val ocupado = Banco { _, _, _ -> HttpStatusCode.TooManyRequests to "Too Many Requests" }
        val e = assertFailsWith<ErrorSigro> { ocupado.dispositivo.estado(1) }
        assertEquals(429, e.estadoHttp)
        assertTrue(e.esTransitorio, "un tope de peticiones no rechaza lo guardado en el celular")
    }

    @Test
    fun laDireccionDelGatewayRetiradoSeCambiaSola() = runTest {
        assertEquals(Servidores.PRUEBAS, Servidores.migrar("https://sigro-gateway.onrender.com"))
        assertEquals(Servidores.PRUEBAS, Servidores.migrar("https://SIGRO-GATEWAY.onrender.com/"))
        assertEquals("http://10.0.2.2:8000", Servidores.migrar("http://10.0.2.2:8000"))

        val b = Banco(mapOf("servidor" to "https://sigro-gateway.onrender.com")) { _, _, _ -> HttpStatusCode.OK to "[]" }
        assertEquals("https://sigro-api.onrender.com", b.sesion.servidorGuardado(), "lo que ve la pantalla «Entrar»")
        assertEquals("https://sigro-api.onrender.com", b.ajustes.m.value["servidor"], "y queda guardado")

        val c = Banco(mapOf("servidor" to "https://sigro-gateway.onrender.com")) { _, _, _ -> HttpStatusCode.OK to "[]" }
        c.dispositivo.estado(1)
        assertTrue(c.rutas.isNotEmpty())
        assertEquals("https://sigro-api.onrender.com", c.ajustes.m.value["servidor"], "los envíos también usan el nuevo")
    }

    // ── A5 · ingreso sin compra ──────────────────────────────────────────────────────────────

    @Test
    fun losOrigenesSonLosDelServidor() {
        // ORIGENES de api/app/modulos/almacen/dominio.py
        assertEquals(
            setOf("saldo_otra_obra", "devolucion_cuadrilla", "prestamo", "sobrante", "otro"),
            OrigenIngreso.entries.map { it.api }.toSet(),
        )
        assertEquals(setOf(OrigenIngreso.OTRA_OBRA, OrigenIngreso.OTRO), OrigenIngreso.entries.filter { it.exigeDetalle }.toSet())
    }

    @Test
    fun otroYSaldoDeOtraObraExigenSuDetalle() {
        assertEquals(setOf("detalle"), validarIngreso(cemento, "2", "28", OrigenIngreso.OTRA_OBRA, "").keys)
        assertEquals(setOf("detalle"), validarIngreso(cemento, "2", "28", OrigenIngreso.OTRO, "  ").keys)
        assertTrue(validarIngreso(cemento, "2", "28", OrigenIngreso.OTRA_OBRA, "Edificio Los Pinos").isEmpty())
        assertTrue(validarIngreso(cemento, "2", "28", OrigenIngreso.SOBRANTE, "").isEmpty())
    }

    @Test
    fun elIngresoViajaConElFormatoDeIngresoInPorLaCola() = runTest {
        val b = Banco { _, _, n -> if (n == 1) null else HttpStatusCode.Created to MOVIMIENTO_201 }
        val r = b.registros.registrarAlmacen(
            1, RegistroAlmacen.IngresoSinCompra(3, OrigenIngreso.OTRA_OBRA, LineaAlmacen(cemento, "2,5"), "28,50", " Edificio Los Pinos "),
        )
        assertNull(r.nota, "ya tiene servicio: no lleva la nota de «se envía cuando exista»")
        assertEquals(1, b.registros.pendientes.first(), "cuenta como pendiente: el trabajo de fondo lo envía")

        assertEquals(1, b.registros.sincronizar().siguenPendientes, "sin señal queda en la cola")
        assertEquals(1, b.registros.sincronizar().enviados)
        assertEquals("/api/v1/obras/1/almacen/ingresos", b.rutas.last())
        assertEquals(b.cuerpos[0], b.cuerpos[1], "regla 3: el reenvío manda el mismo cuerpo con el mismo uuid")

        val c = b.ultimoCuerpo()
        assertEquals(
            setOf(
                "uuid_cliente", "hora_dispositivo_ms", "dispositivo", "almacen_id", "insumo_id", "cantidad", "unidad",
                "costo_unitario", "origen", "origen_detalle",
            ),
            c.keys, "IngresoIn rechaza campos de más",
        )
        assertEquals(r.uuidCliente, c["uuid_cliente"]!!.jsonPrimitive.content)
        assertEquals("saldo_otra_obra", c["origen"]!!.jsonPrimitive.content)
        assertEquals("Edificio Los Pinos", c["origen_detalle"]!!.jsonPrimitive.content)
        assertEquals("2.5", c["cantidad"]!!.jsonPrimitive.content)
        assertEquals("bls", c["unidad"]!!.jsonPrimitive.content)
        assertEquals("28.50", c["costo_unitario"]!!.jsonPrimitive.content)
        assertEquals("ENVIADO", b.registrosDao.una(r.uuidCliente).estado)
    }

    @Test
    fun sinDetalleElOrigenDetalleViajaNulo() = runTest {
        val b = Banco { _, _, _ -> HttpStatusCode.Created to MOVIMIENTO_201 }
        b.registros.registrarAlmacen(1, RegistroAlmacen.IngresoSinCompra(3, OrigenIngreso.DEVOLUCION, LineaAlmacen(cemento, "1"), "28", null))
        b.registros.sincronizar()
        assertEquals(JsonNull, b.ultimoCuerpo()["origen_detalle"])
        assertEquals("devolucion_cuadrilla", b.ultimoCuerpo()["origen"]!!.jsonPrimitive.content)
    }

    @Test
    fun unRechazoDelIngresoMuestraElMensajeDelServidor() = runTest {
        val mensaje = "La obra «Edificio Los Pinos» está registrada en SIGRO: registra un traslado desde esa obra en vez de un ingreso sin compra."
        val b = Banco { _, _, _ -> HttpStatusCode.UnprocessableEntity to """{"codigo":"usar_traslado","mensaje":"$mensaje"}""" }
        b.registros.registrarAlmacen(
            1, RegistroAlmacen.IngresoSinCompra(3, OrigenIngreso.OTRA_OBRA, LineaAlmacen(cemento, "1"), "28", "Edificio Los Pinos"),
        )
        b.registros.sincronizar()
        val fila = unirRegistros(emptyList(), b.registros.deObra(1).first()).single()
        assertEquals(EstadoEnvio.RECHAZADO, fila.estado)
        assertEquals(mensaje, fila.motivoRechazo)
    }
}
