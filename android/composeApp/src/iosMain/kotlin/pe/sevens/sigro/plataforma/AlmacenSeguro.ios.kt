@file:OptIn(ExperimentalForeignApi::class)

package pe.sevens.sigro.plataforma

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS: Keychain (contraseña genérica). «Tras el primer desbloqueo y solo en este dispositivo»: la tarea de
 * fondo puede leer el token con el celular bloqueado, y el token no viaja a otro iPhone en una copia.
 *
 * OJO: escrito en Windows, donde Kotlin/Native no compila para iOS. Falta compilarlo en una Mac
 * (LEEME, «iOS») antes de darlo por bueno.
 */
class AlmacenSeguroIos(private val servicio: String = "pe.sevens.sigro") : AlmacenSeguro {

    override suspend fun leer(clave: String): String? = memScoped {
        val resultado = alloc<CFTypeRefVar>()
        val estado = conConsulta(clave, kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) {
            SecItemCopyMatching(it, resultado.ptr)
        }
        if (estado != errSecSuccess) return@memScoped null
        val datos = CFBridgingRelease(resultado.value) as? NSData ?: return@memScoped null
        NSString.create(data = datos, encoding = NSUTF8StringEncoding)?.toString()
    }

    override suspend fun guardar(clave: String, valor: String) {
        borrar(clave)
        @Suppress("CAST_NEVER_SUCCEEDS")
        val datos = (valor as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: error("Token no codificable")
        val refDatos = CFBridgingRetain(datos)
        try {
            val estado = conConsulta(
                clave, kSecValueData to refDatos, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ) { SecItemAdd(it, null) }
            check(estado == errSecSuccess) { "Keychain rechazó el guardado ($estado)" }
        } finally {
            CFRelease(refDatos)
        }
    }

    override suspend fun borrar(clave: String) {
        conConsulta(clave) { SecItemDelete(it) }
    }

    /** Arma la consulta base (clase, servicio, cuenta) + extras, la usa y libera todo lo que retuvo. */
    private fun <T> conConsulta(clave: String, vararg extras: Pair<CFStringRef?, CFTypeRef?>, uso: (CFMutableDictionaryRef?) -> T): T {
        val refServicio = CFBridgingRetain(servicio)
        val refCuenta = CFBridgingRetain(clave)
        val dic = CFDictionaryCreateMutable(null, (3 + extras.size).convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            CFDictionaryAddValue(dic, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(dic, kSecAttrService, refServicio)
            CFDictionaryAddValue(dic, kSecAttrAccount, refCuenta)
            extras.forEach { (k, v) -> CFDictionaryAddValue(dic, k, v) }
            return uso(dic)
        } finally {
            CFRelease(dic)
            CFRelease(refServicio)
            CFRelease(refCuenta)
        }
    }
}
