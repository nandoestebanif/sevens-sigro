package pe.sevens.sigro.plataforma

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android: la clave AES-256 nace y vive en el Android Keystore (no se puede leer ni copiar fuera del
 * celular); SharedPreferences solo guarda «iv + texto cifrado» en Base64.
 *
 * No se usa EncryptedSharedPreferences: androidx.security-crypto quedó deprecada (1.1.0-alpha07) y para
 * un solo valor no hace falta DataStore. Es el mismo esquema que esa librería, sin la dependencia.
 *
 * Si la clave se pierde (restauración, cambio de bloqueo en algunos equipos), descifrar falla: se borra
 * el valor y se pide volver a ingresar. Las colas de registros no dependen de esto.
 */
class AlmacenSeguroAndroid(contexto: Context) : AlmacenSeguro {
    private val prefs = contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    override suspend fun leer(clave: String): String? = withContext(Dispatchers.IO) {
        val guardado = prefs.getString(clave, null) ?: return@withContext null
        runCatching { descifrar(guardado) }.getOrElse {
            prefs.edit().remove(clave).apply()
            null
        }
    }

    override suspend fun guardar(clave: String, valor: String) = withContext(Dispatchers.IO) {
        check(prefs.edit().putString(clave, cifrar(valor)).commit()) { "No se pudo guardar en el almacén seguro" }
    }

    override suspend fun borrar(clave: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(clave).commit()
        Unit
    }

    private fun cifrar(texto: String): String {
        val c = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, clave()) }
        val iv = c.iv
        val cifrado = c.doFinal(texto.encodeToByteArray())
        return Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + cifrado, Base64.NO_WRAP)
    }

    private fun descifrar(guardado: String): String {
        val bytes = Base64.decode(guardado, Base64.NO_WRAP)
        val largoIv = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + largoIv)
        val c = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, iv)) }
        return c.doFinal(bytes.copyOfRange(1 + largoIv, bytes.size)).decodeToString()
    }

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ARCHIVO = "sigro_seguro"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "sigro_sesion"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
    }
}
