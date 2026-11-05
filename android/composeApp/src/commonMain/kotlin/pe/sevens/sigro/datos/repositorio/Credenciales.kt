package pe.sevens.sigro.datos.repositorio

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.sevens.sigro.datos.local.AjusteDao
import pe.sevens.sigro.datos.local.AjusteEntity
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.plataforma.AlmacenSeguro
import pe.sevens.sigro.plataforma.Plataforma
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Claves de la tabla `ajuste`. Un solo lugar para no escribir el texto dos veces. */
internal object Clave {
    const val SERVIDOR = "servidor"
    /** Solo para migrar: versiones anteriores guardaban aquí el token sin cifrar. Hoy vive en [AlmacenSeguro]. */
    const val TOKEN = "token"
    const val USUARIO_ID = "usuario_id"
    const val NOMBRE = "nombre"
    const val USUARIO = "usuario"
    const val OBRAS = "obras" // JSON
    const val OBRA_ID = "obra_id"
    const val DISPOSITIVO = "dispositivo"
    /** Almacén elegido en cada obra (M6): sirve sin señal. Sobrevive a cerrar sesión. */
    fun almacen(obraId: Int) = "almacen:$obraId"

    val deSesion = listOf(TOKEN, USUARIO_ID, NOMBRE, USUARIO, OBRAS, OBRA_ID)
}

/**
 * Dueña del token de sesión. El token vive cifrado en [AlmacenSeguro], nunca en la tabla `ajuste`:
 *  - la primera lectura mueve el token que dejó una versión anterior en `ajuste` y lo borra de ahí;
 *  - cerrar sesión (a mano o por un 401) borra el token y los datos de sesión, pero NO las colas:
 *    lo registrado sin señal se envía al volver a entrar.
 */
class BovedaSesion(private val seguro: AlmacenSeguro, private val ajustes: AjusteDao) {
    private val candado = Mutex()

    suspend fun token(): String? = candado.withLock {
        migrarTokenAntiguo()
        seguro.leer(CLAVE_TOKEN)
    }

    suspend fun guardarToken(token: String) = candado.withLock {
        seguro.guardar(CLAVE_TOKEN, token)
        ajustes.quitar(listOf(Clave.TOKEN))
    }

    /** Primero el token (lo que da acceso), después los datos que muestran la sesión. */
    suspend fun cerrarSesion() = candado.withLock {
        seguro.borrar(CLAVE_TOKEN)
        ajustes.quitar(Clave.deSesion)
    }

    /** Guardar en el almacén cifrado ANTES de borrar de `ajuste`: si la app muere en medio, no se pierde. */
    private suspend fun migrarTokenAntiguo() {
        val viejo = ajustes.leer(Clave.TOKEN) ?: return
        if (seguro.leer(CLAVE_TOKEN) == null) seguro.guardar(CLAVE_TOKEN, viejo)
        ajustes.quitar(listOf(Clave.TOKEN))
    }

    companion object {
        const val CLAVE_TOKEN = "sigro.token"
    }
}

/** Lectura cómoda de la sesión guardada, para los repositorios que hablan con el servidor. */
class Credenciales(private val ajustes: AjusteDao, private val boveda: BovedaSesion) {
    suspend fun servidor(): String? = ajustes.leer(Clave.SERVIDOR)

    /** Hay sesión si hay token. El token lo pone el plugin Auth de Ktor; los repositorios no lo tocan. */
    suspend fun haySesion(): Boolean = boveda.token() != null

    /** Servidor de la sesión abierta, o «vuelve a ingresar». */
    suspend fun exigir(): String {
        val s = servidor()
        if (s == null || !haySesion()) throw ErrorSigro("sin_sesion", "Vuelve a ingresar.")
        return s
    }

    suspend fun guardar(clave: String, valor: String) = ajustes.guardar(AjusteEntity(clave, valor))
    suspend fun leer(clave: String): String? = ajustes.leer(clave)

    /** HU-15: código que la app genera en la primera instalación y conserva (no se borra al salir). */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun identificadorDispositivo(): String =
        ajustes.leer(Clave.DISPOSITIVO) ?: "sigro-${Plataforma.nombre}-${Uuid.random()}".also {
            guardar(Clave.DISPOSITIVO, it)
        }
}
