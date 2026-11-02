package pe.sevens.sigro.datos.remoto

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
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
@Serializable data class RecepcionLineaDto(
    @SerialName("insumo_id") val insumoId: Int,
    @SerialName("unidad_compra") val unidadCompra: String,
    val pedido: String,
    val recibido: String,
    val pendiente: String,
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

/**
 * Salida que viaja al servidor (HU-40). `uuid_cliente` es la clave de idempotencia (regla 3):
 * un reenvío con el mismo uuid no crea nada nuevo. La hora del dispositivo viaja como dato;
 * la hora que vale para auditar la pone el servidor (HU-38).
 */
@Serializable data class SalidaIn(
    @SerialName("uuid_cliente") val uuidCliente: String,
    @SerialName("insumo_id") val insumoId: Int,
    val cantidad: String,
    val motivo: String,
    @SerialName("quien_retira") val quienRetira: String,
    val frente: String? = null,
    @SerialName("hora_dispositivo_ms") val horaDispositivoMs: Long,
    @SerialName("dispositivo") val dispositivo: String,
)

class ApiSigro(private val http: HttpClient, private val json: Json) {

    private fun url(servidor: String, ruta: String) = servidor.trimEnd('/') + "/api/v1" + ruta

    suspend fun iniciarSesion(servidor: String, usuario: String, clave: String): SesionDto =
        llamar { http.post(url(servidor, "/auth/login")) { cuerpo(LoginIn(usuario.trim(), clave)) } }

    suspend fun quienSoy(servidor: String, token: String): YoDto =
        llamar { http.get(url(servidor, "/auth/yo")) { bearerAuth(token) } }

    suspend fun insumos(servidor: String, token: String, q: String? = null): List<InsumoDto> =
        llamar {
            http.get(url(servidor, "/catalogo/insumos")) {
                bearerAuth(token)
                if (!q.isNullOrBlank()) parameter("q", q.trim())
            }
        }

    suspend fun dispositivos(servidor: String, token: String, obraId: Int): List<DispositivoDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/dispositivos")) { bearerAuth(token) } }

    suspend fun solicitarDispositivo(servidor: String, token: String, obraId: Int, identificador: String): DispositivoDto =
        llamar {
            http.post(url(servidor, "/obras/$obraId/dispositivos")) {
                bearerAuth(token)
                cuerpo(DispositivoIn(identificador))
            }
        }

    suspend fun registrarInsumo(servidor: String, token: String, nuevo: InsumoIn): InsumoDto =
        llamar { http.post(url(servidor, "/catalogo/insumos")) { bearerAuth(token); cuerpo(nuevo) } }

    suspend fun trabajadores(servidor: String, token: String, obraId: Int): List<TrabajadorDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/trabajadores")) { bearerAuth(token) } }

    suspend fun registrarTrabajador(servidor: String, token: String, obraId: Int, nuevo: TrabajadorIn): TrabajadorDto =
        llamar { http.post(url(servidor, "/obras/$obraId/trabajadores")) { bearerAuth(token); cuerpo(nuevo) } }

    suspend fun obras(servidor: String, token: String): List<ObraDto> =
        llamar { http.get(url(servidor, "/obras")) { bearerAuth(token) } }

    suspend fun almacenes(servidor: String, token: String, obraId: Int): List<AlmacenDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/almacenes")) { bearerAuth(token) } }

    suspend fun compras(servidor: String, token: String, obraId: Int): List<CompraDto> =
        llamar {
            http.get(url(servidor, "/obras/$obraId/compras")) { bearerAuth(token); parameter("estado", "registrada") }
        }

    suspend fun estadoRecepcion(servidor: String, token: String, obraId: Int, compraId: Int): List<RecepcionLineaDto> =
        llamar { http.get(url(servidor, "/obras/$obraId/almacen/compras/$compraId/recepcion")) { bearerAuth(token) } }

    /**
     * Envía un registro de la cola general: el cuerpo es el JSON que se armó al registrarlo (mismo uuid,
     * mismo contenido en cada reintento). `ruta` va relativa a /api/v1.
     */
    suspend fun enviarRegistro(servidor: String, token: String, ruta: String, cuerpoJson: String) {
        llamar<JsonObject?>(permitirVacio = true) {
            http.post(url(servidor, ruta)) {
                bearerAuth(token)
                setBody(TextContent(cuerpoJson, ContentType.Application.Json))
            }
        }
    }

    /** Como [enviarRegistro], pero devuelve la respuesta: el conteo necesita su id para las líneas y la confirmación. */
    suspend fun enviarConRespuesta(servidor: String, token: String, ruta: String, cuerpoJson: String): JsonObject =
        llamar {
            http.post(url(servidor, ruta)) {
                bearerAuth(token)
                setBody(TextContent(cuerpoJson, ContentType.Application.Json))
            }
        }

    /**
     * POST /obras/{id}/almacen/salidas — contrato propuesto a Adiel para HU-40 (aún no existe en la API).
     * Mientras responda 404, la sincronización deja la salida PENDIENTE con una nota: no se pierde nada.
     */
    suspend fun enviarSalida(servidor: String, token: String, obraId: Int, salida: SalidaIn) {
        llamar<JsonObject?>(permitirVacio = true) {
            http.post(url(servidor, "/obras/$obraId/almacen/salidas")) {
                bearerAuth(token)
                cuerpo(salida)
            }
        }
    }

    private inline fun <reified T> HttpRequestBuilder.cuerpo(v: T) {
        contentType(ContentType.Application.Json)
        setBody(v)
    }

    /** Hace la llamada y traduce cualquier falla a ErrorSigro, con el mismo contrato que la web. */
    private suspend inline fun <reified T> llamar(
        permitirVacio: Boolean = false,
        bloque: () -> HttpResponse,
    ): T {
        val r = try {
            bloque()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // cancelar una corrutina no es «sin conexión»
        } catch (e: Exception) {
            throw ErrorSigro(
                "sin_conexion",
                "No se pudo conectar con el servidor de SIGRO. Revisa la señal o la dirección del servidor.",
            )
        }
        if (!r.status.isSuccess()) throw errorDe(r)
        if (permitirVacio && r.bodyAsText().isBlank()) return null as T
        return r.body()
    }

    private suspend fun errorDe(r: HttpResponse): ErrorSigro {
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
                else -> "El servidor rechazó la operación (${r.status.value})."
            }
        return ErrorSigro(codigo, mensaje, r.status.value)
    }
}
