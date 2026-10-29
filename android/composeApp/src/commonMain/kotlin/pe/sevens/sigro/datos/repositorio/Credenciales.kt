package pe.sevens.sigro.datos.repositorio

import pe.sevens.sigro.datos.local.AjusteDao
import pe.sevens.sigro.datos.local.AjusteEntity
import pe.sevens.sigro.plataforma.Plataforma
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Claves de la tabla `ajuste`. Un solo lugar para no escribir el texto dos veces. */
internal object Clave {
    const val SERVIDOR = "servidor"
    const val TOKEN = "token"
    const val USUARIO_ID = "usuario_id"
    const val NOMBRE = "nombre"
    const val USUARIO = "usuario"
    const val OBRAS = "obras" // JSON
    const val OBRA_ID = "obra_id"
    const val DISPOSITIVO = "dispositivo"

    val deSesion = listOf(TOKEN, USUARIO_ID, NOMBRE, USUARIO, OBRAS, OBRA_ID)
}

/** Lectura cómoda de la sesión guardada, para los repositorios que necesitan servidor + token. */
internal class Credenciales(private val ajustes: AjusteDao) {
    suspend fun servidor(): String? = ajustes.leer(Clave.SERVIDOR)
    suspend fun token(): String? = ajustes.leer(Clave.TOKEN)
    suspend fun guardar(clave: String, valor: String) = ajustes.guardar(AjusteEntity(clave, valor))

    /** HU-15: código que la app genera en la primera instalación y conserva (no se borra al salir). */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun identificadorDispositivo(): String =
        ajustes.leer(Clave.DISPOSITIVO) ?: "sigro-${Plataforma.nombre}-${Uuid.random()}".also {
            guardar(Clave.DISPOSITIVO, it)
        }
}
