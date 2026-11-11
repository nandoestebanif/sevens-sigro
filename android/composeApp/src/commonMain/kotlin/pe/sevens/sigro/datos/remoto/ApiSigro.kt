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

    suspend fun solicitarDispositivo(servidor: String, obraId: Int, identificador: String): DispositivoDto =
        llamar { http.post(url(servidor, "/obras/$obraId/dispositivos")) { cuerpo(DispositivoIn(identificador)) } }

    suspend fun registrarInsumo(servidor: String, nuevo: InsumoIn): InsumoDto =
        llamar { http.post(url(servidor, "/catalogo/insumos")) { cuerpo(nuevo) } }

    suspend fun trabajadores(servidor: String, obraId: Int): List<TrabajadorDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/trabajadores")) }

    suspend fun registrarTrabajador(servidor: String, obraId: Int, nuevo: TrabajadorIn): TrabajadorDto =
        llamar { http.post(url(servidor, "/obras/$obraId/trabajadores")) { cuerpo(nuevo) } }

    suspend fun obras(servidor: String): List<ObraDto> =
        llamar { http.get(url(servidor, "/obras")) }

    suspend fun almacenes(servidor: String, obraId: Int): List<AlmacenDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/almacenes")) }

    suspend fun compras(servidor: String, obraId: Int): List<CompraDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/compras")) { parameter("estado", "registrada") } }

    /** HU-35: pedido, recibido y pendiente por línea, en el orden de las líneas de la compra. */
    suspend fun estadoRecepcion(servidor: String, obraId: Int, compraId: Int): List<RecepcionLineaDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/almacen/compras/$compraId/recepcion")) }

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
