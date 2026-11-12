package pe.sevens.sigro.datos.repositorio

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.sevens.sigro.dominio.ReciboServidor

/**
 * A1 · Del cuerpo del 201/200 sale quién registró y la hora del servidor. Salidas, ingresos y jornadas los
 * traen arriba (`registrado_por`, `fecha_servidor`); una recepción, en cada uno de sus `movimientos`.
 *
 * La API da `registrado_por` como id de usuario, no como nombre. Si es quien tiene la sesión abierta (el caso
 * normal: registra desde su celular), se usa su nombre; si no, «usuario N.º …». Si algún día la API manda
 * `registrado_por_nombre`, se usa ese (pedido a Adiel en el PR).
 */
internal fun reciboDe(respuesta: JsonObject?, usuarioSesionId: Int?, nombreSesion: String?): ReciboServidor? {
    if (respuesta == null) return null
    val fuente = if ("registrado_por" in respuesta) respuesta
    else runCatching { respuesta["movimientos"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull() ?: return null
    fun texto(clave: String) = runCatching { fuente[clave]?.jsonPrimitive?.contentOrNull }.getOrNull()
    val id = runCatching { fuente["registrado_por"]?.jsonPrimitive?.intOrNull }.getOrNull()
    val fecha = texto("fecha_servidor")
    if (id == null && fecha == null) return null
    val nombre = texto("registrado_por_nombre")
        ?: if (id != null && id == usuarioSesionId && !nombreSesion.isNullOrBlank()) nombreSesion else id?.let { "usuario N.º $it" }
    return ReciboServidor(id, nombre, fecha)
}

/** El recibo de una respuesta, con el usuario de la sesión guardada en el celular. */
internal suspend fun Credenciales.recibo(respuesta: JsonObject?): ReciboServidor? =
    reciboDe(respuesta, leer(Clave.USUARIO_ID)?.toIntOrNull(), leer(Clave.NOMBRE))
