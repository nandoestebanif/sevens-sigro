package pe.sevens.sigro.datos.remoto

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.content.TextContent
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.sevens.sigro.dominio.ErrorSigro

/*
 * Cliente de la API de Adiel (/api/v1). Es el equivalente en la app del `http.ts` de la web:
 * un solo lugar habla con el servidor, convierte nombres y traduce errores {codigo, mensaje}.
 * Las pantallas nunca ven Ktor.
 */

@Serializable data class LoginIn(val usuario: String, val clave: String)
@Serializable data class UsuarioMinDto(val id: Int, val nombre: String, val usuario: String)
@Serializable data class ObraRolDto(val id: Int, val nombre: String, val rol: String)
@Serializable data class SesionDto(
    val token: String,
    @SerialName("expira_en") val expiraEn: Int,
    val usuario: UsuarioMinDto,
    val obras: List<ObraRolDto>,
)
@Serializable data class YoDto(val usuario: UsuarioMinDto, val obras: List<ObraRolDto>)

@Serializable data class InsumoDto(
    val id: Int,
    val codigo: String,
    val descripcion: String,
    @SerialName("unidad_s10") val unidadS10: String,
    @SerialName("unidad_compra") val unidadCompra: String,
    val tipo: String,
    val activo: Boolean,
    // Sprint 2 (ficha del insumo, HU-17): opcionales para no romper con una API anterior.
    @SerialName("factor_conversion") val factorConversion: String? = null,
    val verificado: Boolean? = null,
)

@Serializable data class InsumoIn(val descripcion: String, val unidad: String, val tipo: String = "material")

@Serializable data class TrabajadorIn(
    val nombre: String,
    @SerialName("tipo_documento") val tipoDocumento: String,
    val documento: String,
    val categoria: String,
    val cuadrilla: String,
    @SerialName("fecha_ingreso") val fechaIngreso: String,
)
/** HU-62 c2 · cuerpo de POST /obras/{id}/trabajadores/{tid}/baja: la baja es una fecha, no un borrado (regla 10). */
@Serializable data class BajaIn(@SerialName("fecha_salida") val fechaSalida: String)

/** HU-64 · una fila de GET /obras/{id}/horas-acumuladas: un trabajador, en un periodo, con una categoría. */
@Serializable data class HorasDto(
    @SerialName("periodo_id") val periodoId: Int,
    @SerialName("periodo_numero") val periodoNumero: Int,
    @SerialName("trabajador_id") val trabajadorId: Int,
    val trabajador: String,
    val cuadrilla: String,
    val categoria: String,
    @SerialName("dias_presente") val diasPresente: Int,
    @SerialName("dias_ausente") val diasAusente: Int,
    @SerialName("dias_permiso") val diasPermiso: Int,
    @SerialName("horas_normales") val horasNormales: String,
    @SerialName("horas_extra") val horasExtra: String,
    @SerialName("ultima_jornada") val ultimaJornada: String? = null,
)

/* ── préstamo de herramientas (migración 0023) ── */
@Serializable data class PrestamoDto(
    val id: Int,
    val almacen: String,
    @SerialName("insumo_codigo") val insumoCodigo: String,
    val descripcion: String,
    val unidad: String,
    val cantidad: String,
    val responsable: String,
    @SerialName("devolver_el") val devolverEl: String? = null,
    @SerialName("prestado_en") val prestadoEn: String,
    @SerialName("devuelto_en") val devueltoEn: String? = null,
    @SerialName("estado_devolucion") val estadoDevolucion: String? = null,
    @SerialName("nota_devolucion") val notaDevolucion: String? = null,
    val atrasado: Boolean = false,
)
@Serializable data class HerramientaDisponibleDto(
    @SerialName("almacen_id") val almacenId: Int,
    val almacen: String,
    @SerialName("insumo_id") val insumoId: Int,
    @SerialName("insumo_codigo") val insumoCodigo: String,
    val descripcion: String,
    val unidad: String,
    @SerialName("en_almacen") val enAlmacen: String,
    val prestado: String,
    val disponible: String,
)
@Serializable data class PanelPrestamosDto(val prestamos: List<PrestamoDto>, val herramientas: List<HerramientaDisponibleDto> = emptyList())
@Serializable data class PrestamoIn(
    @SerialName("uuid_cliente") val uuidCliente: String,
    @SerialName("almacen_id") val almacenId: Int,
    @SerialName("insumo_id") val insumoId: Int,
    val cantidad: String,
    val responsable: String,
    @SerialName("devolver_el") val devolverEl: String? = null,
    val nota: String? = null,
    val dispositivo: String? = null,
)
@Serializable data class DevolucionIn(val estado: String, val nota: String? = null)
@Serializable data class DevolucionDto(val advertencias: List<String> = emptyList())

@Serializable data class DniDto(
    val dni: String = "",
    val nombres: String = "",
    @SerialName("apellido_paterno") val apellidoPaterno: String = "",
    @SerialName("apellido_materno") val apellidoMaterno: String = "",
) {
    val nombreCompleto get() = listOf(nombres, apellidoPaterno, apellidoMaterno).map(String::trim).filter(String::isNotEmpty).joinToString(" ")
}

@Serializable data class TrabajadorDto(
    val id: Int,
    val nombre: String,
    @SerialName("tipo_documento") val tipoDocumento: String,
    val documento: String,
    val categoria: String,
    val cuadrilla: String,
    @SerialName("fecha_ingreso") val fechaIngreso: String,
    @SerialName("fecha_salida") val fechaSalida: String? = null,
)

/** HU-63. `uuid_cliente` es la clave de idempotencia: un reenvío devuelve la jornada original (200). */
@Serializable data class JornadaIn(
    @SerialName("trabajador_id") val trabajadorId: Int,
    val fecha: String,
    val estado: String,
    @SerialName("horas_normales") val horasNormales: String,
    @SerialName("horas_extra") val horasExtra: String,
    @SerialName("uuid_cliente") val uuidCliente: String,
)

/** HU-63 c3 · cuerpo de POST /obras/{id}/jornadas/{jid}/correccion. */
@Serializable data class CorreccionJornadaIn(
    val estado: String,
    @SerialName("horas_normales") val horasNormales: String,
    @SerialName("horas_extra") val horasExtra: String,
    val motivo: String,
)

/** La jornada vigente que devuelve el servidor (solo lo que la corrección necesita; el resto se ignora). */
@Serializable data class JornadaDto(
    val id: Int,
    @SerialName("trabajador_id") val trabajadorId: Int,
    val fecha: String,
    val estado: String,
    @SerialName("corrige_a_id") val corrigeAId: Int? = null,
)

@Serializable data class ObraDto(val id: Int, val nombre: String, val estado: String? = null, val rol: String? = null)
@Serializable data class AlmacenDto(val id: Int, val nombre: String, val tipo: String, val estado: String)
@Serializable data class LineaCompraDto(
    @SerialName("insumo_codigo") val insumoCodigo: String,
    val descripcion: String,
    val cantidad: String,
    @SerialName("unidad_compra") val unidadCompra: String,
)
@Serializable data class CompraDto(
    val id: Int,
    @SerialName("proveedor_razon_social") val proveedor: String,
    @SerialName("tipo_comprobante") val tipoComprobante: String,
    @SerialName("numero_comprobante") val numeroComprobante: String,
    val fecha: String,
    val estado: String,
    val lineas: List<LineaCompraDto> = emptyList(),
    /** HU-73: para cotejar el QR de la factura sin señal (contrato 3.2). Nulos si el servidor aún no los manda. */
    @SerialName("proveedor_ruc") val proveedorRuc: String? = null,
    val moneda: String? = null,
    val total: JsonPrimitive? = null,
    /** Ya existe en el servidor: {base, igv, total}. Número o texto según el servidor: se lee como primitivo. */
    val totales: TotalesCompraDto? = null,
)
@Serializable data class TotalesCompraDto(val base: JsonPrimitive? = null, val igv: JsonPrimitive? = null, val total: JsonPrimitive? = null)

/** HU-73 · POST /obras/{id}/compras/cotejar-qr (contrato 3.1). */
@Serializable data class CotejoQrIn(val qr: String)
@Serializable data class CompraCotejadaDto(val id: Int, val estado: String? = null)
@Serializable data class CotejoQrDto(
    val resultado: String,
    val compra: CompraCotejadaDto? = null,
    val avisos: List<String> = emptyList(),
)

@Serializable data class DispositivoIn(val identificador: String)
@Serializable data class DispositivoDto(
    val id: Int,
    @SerialName("usuario_id") val usuarioId: Int,
    val identificador: String,
    val estado: String,
)

/** A2 · Una fila de GET /obras/{id}/almacen/stock (StockFilaOut). Cifras como texto decimal, sin tocar. */
@Serializable data class FilaStockDto(
    @SerialName("almacen_id") val almacenId: Int? = null,
    val almacen: String? = null,
    @SerialName("insumo_id") val insumoId: Int,
    @SerialName("insumo_codigo") val insumoCodigo: String,
    val descripcion: String,
    val unidad: String,
    val cantidad: String,
    @SerialName("costo_promedio") val costoPromedio: String? = null,
    val valor: String? = null,
    @SerialName("ultimo_movimiento_en") val ultimoMovimientoEn: String? = null,
    @SerialName("punto_reposicion") val puntoReposicion: String? = null,
    @SerialName("bajo_punto_reposicion") val bajoPuntoReposicion: Boolean? = null,
)

/** A2 · StockOut: por almacén y consolidado de la obra, con el corte (hora del último movimiento considerado). */
@Serializable data class StockDto(
    val criterio: String? = null,
    val corte: String? = null,
    @SerialName("por_almacen") val porAlmacen: List<FilaStockDto> = emptyList(),
    val consolidado: List<FilaStockDto> = emptyList(),
    val advertencias: List<String> = emptyList(),
)

/** Estado de recepción de una línea de compra (HU-35), en unidad de compra. Lo calcula el servidor. */
@Serializable data class RecepcionLineaDto(
    @SerialName("insumo_id") val insumoId: Int,
    @SerialName("unidad_compra") val unidadCompra: String,
    val pedido: String,
    val recibido: String,
    val pendiente: String,
)

/** HU-36 · VerificacionIn: lo mismo que se va a recibir (sin líneas = todo lo pendiente). */
@Serializable data class VerificacionIn(
    @SerialName("compra_id") val compraId: Int,
    val lineas: List<LineaCantidadDto>? = null,
)

@Serializable data class LineaCantidadDto(@SerialName("insumo_id") val insumoId: Int, val cantidad: String)

/** HU-36 · VerificacionLineaOut, en la unidad del S10. */
@Serializable data class VerificacionLineaDto(
    @SerialName("insumo_id") val insumoId: Int,
    @SerialName("insumo_codigo") val insumoCodigo: String,
    val unidad: String,
    val requerido: String? = null,
    @SerialName("ya_recibido") val yaRecibido: String,
    @SerialName("con_esta") val conEsta: String,
    val exceso: String? = null,
    val excede: Boolean,
)

@Serializable data class VerificacionDto(
    @SerialName("compra_id") val compraId: Int,
    @SerialName("hay_exceso") val hayExceso: Boolean,
    val lineas: List<VerificacionLineaDto> = emptyList(),
)

/**
 * Salida que viaja al servidor (HU-40). `uuid_cliente` es la clave de idempotencia (regla 3):
 * un reenvío con el mismo uuid no crea nada nuevo. La hora del dispositivo viaja como dato;
 * la hora que vale para auditar la pone el servidor (HU-38).
 * `unidad` es la de compra (la que guarda el catálogo del celular): el servidor la convierte con el
 * factor del insumo. `almacen_id` nulo solo si la obra tiene un único almacén activo.
 */
@Serializable data class SalidaIn(
    @SerialName("uuid_cliente") val uuidCliente: String,
    @SerialName("almacen_id") val almacenId: Int? = null,
    @SerialName("insumo_id") val insumoId: Int,
    val cantidad: String,
    val unidad: String? = null,
    val motivo: String,
    @SerialName("quien_retira") val quienRetira: String,
    val frente: String? = null,
    @SerialName("hora_dispositivo_ms") val horaDispositivoMs: Long,
    @SerialName("dispositivo") val dispositivo: String,
)

/**
 * El token NO pasa por aquí: lo pone el plugin Auth de Ktor (ver ClienteHttp.kt). Un 401 en una ruta con
 * sesión cierra la sesión (`alVencerSesion`) y deja las colas intactas; el error sigue su camino para que
 * la cola anote «vuelve a ingresar».
 */
/** A4: lo que ve el almacenero ante un error de red o un servidor que no responde (502/503/504). */
const val MENSAJE_SIN_RESPUESTA = "El servidor no responde. Revisa la dirección o inténtalo en un minuto."
private val SIN_RESPUESTA = setOf(502, 503, 504)

class ApiSigro(
    private val http: HttpClient,
    private val json: Json,
    private val alVencerSesion: suspend () -> Unit = {},
) {

    private fun url(servidor: String, ruta: String) = servidor.trimEnd('/') + "/api/v1" + ruta

    /** Tras entrar o salir: que el próximo pedido lea el token nuevo del almacén cifrado. */
    fun olvidarToken() = http.olvidarToken()

    suspend fun iniciarSesion(servidor: String, usuario: String, clave: String): SesionDto =
        llamar(conSesion = false) { http.post(url(servidor, RUTA_LOGIN)) { cuerpo(LoginIn(usuario.trim(), clave)) } }

    suspend fun quienSoy(servidor: String): YoDto =
        llamar { http.get(url(servidor, "/auth/yo")) }

    suspend fun insumos(servidor: String, q: String? = null): List<InsumoDto> =
        llamar {
            http.get(url(servidor, "/catalogo/insumos")) {
                if (!q.isNullOrBlank()) parameter("q", q.trim())
            }
        }

    suspend fun dispositivos(servidor: String, obraId: Int): List<DispositivoDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/dispositivos")) }

    /** HU-15: solo los celulares de quien pregunta en esta obra (el almacenero no puede listar los de todos). */
    suspend fun misDispositivos(servidor: String, obraId: Int): List<DispositivoDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/dispositivos/mios")) }

    suspend fun solicitarDispositivo(servidor: String, obraId: Int, identificador: String): DispositivoDto =
        llamar { http.post(url(servidor, "/obras/$obraId/dispositivos")) { cuerpo(DispositivoIn(identificador)) } }

    suspend fun registrarInsumo(servidor: String, nuevo: InsumoIn): InsumoDto =
        llamar { http.post(url(servidor, "/catalogo/insumos")) { cuerpo(nuevo) } }

    suspend fun trabajadores(servidor: String, obraId: Int): List<TrabajadorDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/trabajadores")) }

    /** RENIEC a través del servidor (Factiliza): el celular nunca lleva el token. 404 si RENIEC no respondió. */
    suspend fun consultarDni(servidor: String, obraId: Int, dni: String): DniDto =
        llamar { http.get(url(servidor, "/obras/$obraId/trabajadores/consulta-dni/$dni")) }

    suspend fun registrarTrabajador(servidor: String, obraId: Int, nuevo: TrabajadorIn): TrabajadorDto =
        llamar { http.post(url(servidor, "/obras/$obraId/trabajadores")) { cuerpo(nuevo) } }

    /** HU-62 c2: el servidor pone la fecha de salida; las jornadas ya registradas se conservan. */
    suspend fun darDeBaja(servidor: String, obraId: Int, trabajadorId: Int, baja: BajaIn): TrabajadorDto =
        llamar { http.post(url(servidor, "/obras/$obraId/trabajadores/$trabajadorId/baja")) { cuerpo(baja) } }

    /** HU-64: horas vigentes (ya con correcciones) por trabajador, periodo y categoría. */
    suspend fun horasAcumuladas(servidor: String, obraId: Int): List<HorasDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/horas-acumuladas")) }

    /** HU-63: la jornada VIGENTE de ese trabajador y día (el servidor ya resuelve la cadena de correcciones). */
    suspend fun jornadasVigentes(servidor: String, obraId: Int, fecha: String, trabajadorId: Int): List<JornadaDto> =
        llamar {
            http.get(url(servidor, "/obras/$obraId/jornadas")) {
                parameter("fecha", fecha)
                parameter("trabajador_id", trabajadorId)
            }
        }

    /** HU-63 c3: inserta la corrección que apunta a la jornada vigente; la original no se toca (R1). */
    suspend fun corregirJornada(servidor: String, obraId: Int, jornadaId: Int, cuerpo: CorreccionJornadaIn): JornadaDto =
        llamar { http.post(url(servidor, "/obras/$obraId/jornadas/$jornadaId/correccion")) { cuerpo(cuerpo) } }

    suspend fun obras(servidor: String): List<ObraDto> =
        llamar { http.get(url(servidor, "/obras")) }

    suspend fun almacenes(servidor: String, obraId: Int): List<AlmacenDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/almacenes")) }

    suspend fun compras(servidor: String, obraId: Int): List<CompraDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/compras")) { parameter("estado", "registrada") } }

    /** HU-73: el servidor coteja el QR de la factura con las compras de la obra. No guarda nada. */
    suspend fun cotejarQr(servidor: String, obraId: Int, qr: String): CotejoQrDto =
        llamar { http.post(url(servidor, "/obras/$obraId/compras/cotejar-qr")) { cuerpo(CotejoQrIn(qr)) } }

    /** HU-35: pedido, recibido y pendiente por línea, en el orden de las líneas de la compra. */
    suspend fun estadoRecepcion(servidor: String, obraId: Int, compraId: Int): List<RecepcionLineaDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/almacen/compras/$compraId/recepcion")) }

    /** HU-36: avisa ANTES de guardar si la recepción pasa lo requerido en el S10. No guarda nada en el servidor. */
    suspend fun verificarRecepcion(servidor: String, obraId: Int, cuerpo: VerificacionIn): VerificacionDto =
        llamar { http.post(url(servidor, "/obras/$obraId/almacen/recepciones/verificacion")) { cuerpo(cuerpo) } }

    /** A2 · Stock por almacén y consolidado. Lo calcula el servidor a partir del libro (regla 2). */
    suspend fun stock(servidor: String, obraId: Int): StockDto =
        llamar { http.get(url(servidor, "/obras/$obraId/almacen/stock")) }

    /**
     * Envía un registro de la cola general: el cuerpo es el JSON que se armó al registrarlo (mismo uuid,
     * mismo contenido en cada reintento). `ruta` va relativa a /api/v1. Devuelve lo que respondió el servidor
     * (A1: de ahí salen quién registró y la hora del servidor).
     */
    suspend fun enviarRegistro(servidor: String, ruta: String, cuerpoJson: String): JsonObject? =
        llamar<JsonObject?>(permitirVacio = true) {
            http.post(url(servidor, ruta)) { setBody(TextContent(cuerpoJson, ContentType.Application.Json)) }
        }

    /** POST /obras/{id}/almacen/salidas (HU-40). Reenvío con el mismo uuid ⇒ 200 con el original. */
    suspend fun enviarSalida(servidor: String, obraId: Int, salida: SalidaIn): JsonObject? =
        llamar<JsonObject?>(permitirVacio = true) {
            http.post(url(servidor, "/obras/$obraId/almacen/salidas")) { cuerpo(salida) }
        }

    suspend fun prestamos(servidor: String, obraId: Int): PanelPrestamosDto =
        llamar { http.get(url(servidor, "/obras/$obraId/almacen/prestamos")) { parameter("estado", "todos") } }

    suspend fun prestar(servidor: String, obraId: Int, p: PrestamoIn): PrestamoDto =
        llamar { http.post(url(servidor, "/obras/$obraId/almacen/prestamos")) { cuerpo(p) } }

    suspend fun devolver(servidor: String, obraId: Int, prestamoId: Int, d: DevolucionIn): DevolucionDto =
        llamar { http.post(url(servidor, "/obras/$obraId/almacen/prestamos/$prestamoId/devolucion")) { cuerpo(d) } }

    private inline fun <reified T> HttpRequestBuilder.cuerpo(v: T) {
        contentType(ContentType.Application.Json)
        setBody(v)
    }

    /**
     * Hace la llamada y traduce cualquier falla a ErrorSigro, con el mismo contrato que la web.
     * `conSesion = false` solo para el login: ahí un 401 es «clave incorrecta», no «sesión vencida».
     */
    private suspend inline fun <reified T> llamar(
        permitirVacio: Boolean = false,
        conSesion: Boolean = true,
        bloque: () -> HttpResponse,
    ): T {
        val r = try {
            bloque()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // cancelar una corrutina no es «sin conexión»
        } catch (e: Exception) {
            throw ErrorSigro("sin_conexion", MENSAJE_SIN_RESPUESTA)
        }
        if (!r.status.isSuccess()) {
            val error = errorDe(r)
            if (conSesion && r.status.value == 401) {
                http.olvidarToken()
                alVencerSesion() // vuelve a «Entrar»; las colas no se tocan
            }
            throw error
        }
        if (permitirVacio && r.bodyAsText().isBlank()) return null as T
        return r.body()
    }

    private suspend fun errorDe(r: HttpResponse): ErrorSigro {
        // A4: el servidor dormido o caído (Render responde 502/503/504 mientras despierta) se dice igual que sin red.
        if (r.status.value in SIN_RESPUESTA) return ErrorSigro("servidor_no_responde", MENSAJE_SIN_RESPUESTA, r.status.value)
        val texto = runCatching { r.bodyAsText() }.getOrDefault("")
        val obj = runCatching { json.parseToJsonElement(texto).jsonObject }.getOrNull()
        // FastAPI responde {codigo, mensaje} o {detail: {codigo, mensaje}}
        val d = obj?.get("detail")?.let { runCatching { it.jsonObject }.getOrNull() } ?: obj
        val codigo = d?.get("codigo")?.jsonPrimitive?.content ?: "http_${r.status.value}"
        val mensaje = d?.get("mensaje")?.jsonPrimitive?.content?.removePrefix("Value error, ")
            ?: when (r.status.value) {
                401 -> "Usuario o clave incorrectos, o la sesión venció."
                403 -> "No tienes permiso para esto en esta obra."
                404 -> "El servidor todavía no ofrece esta función."
                408, 429 -> "El servidor está ocupado. Se reintenta solo en un momento."
                else -> "El servidor rechazó la operación (${r.status.value})."
            }
        return ErrorSigro(codigo, mensaje, r.status.value)
    }
}
