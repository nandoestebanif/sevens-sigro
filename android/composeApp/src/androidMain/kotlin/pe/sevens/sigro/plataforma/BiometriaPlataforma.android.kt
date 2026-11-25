package pe.sevens.sigro.plataforma

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import pe.sevens.sigro.dominio.Biometria
import pe.sevens.sigro.dominio.DisponibilidadHuella
import pe.sevens.sigro.dominio.ResultadoHuella
import pe.sevens.sigro.dominio.ResultadoSecreto
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/**
 * Android: BiometricPrompt (huella, rostro o el PIN/patrón del celular). La huella nunca llega a la app: Android
 * solo responde si quien está es el dueño.
 *
 * Desde Android 11 (API 30) hay dos llaves AES/GCM en el Keystore, creadas con `setInvalidatedByBiometricEnrollment`
 * y que solo se usan tras la huella:
 * - `sigro_huella` (candado): se usa una vez en cada desbloqueo para confirmar que la huella es la de siempre.
 * - `sigro_huella_clave` (entrar después de cerrar sesión): cifra la contraseña. El «sobre» guardado es
 *   `iv:cifrado` en Base64; sin la huella no se abre, ni copiando la base de la app.
 * Si alguien agrega una huella nueva al celular, las llaves se invalidan (KeyPermanentlyInvalidatedException) y se
 * pide la contraseña otra vez. En Android 8–10 no se pueden combinar con el PIN: solo hay candado, sin llave.
 */
@Composable
actual fun rememberBiometria(): Biometria {
    val contexto = LocalContext.current
    return remember(contexto) { BiometriaAndroid(contexto.fragmentActivity()) }
}

private tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}

/** Lo que devolvió el diálogo: listo (con el cifrador ya autorizado), cancelado, «usar contraseña» o error. */
private sealed interface Respuesta {
    data class Lista(val cifrador: Cipher?) : Respuesta
    data object Cancelada : Respuesta
    data object Negativo : Respuesta
    data class Error(val mensaje: String) : Respuesta
}

private class BiometriaAndroid(private val actividad: FragmentActivity?) : Biometria {
    private val conLlave = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    private val autenticadores = if (conLlave) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    override fun disponibilidad(): DisponibilidadHuella {
        val a = actividad ?: return DisponibilidadHuella.NO_DISPONIBLE
        return when (BiometricManager.from(a).canAuthenticate(autenticadores)) {
            BiometricManager.BIOMETRIC_SUCCESS -> DisponibilidadHuella.DISPONIBLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> DisponibilidadHuella.SIN_REGISTRAR
            else -> DisponibilidadHuella.NO_DISPONIBLE
        }
    }

    /**
     * La contraseña se ata a una llave que pide SOLO la huella (BIOMETRIC_STRONG), el patrón que recomienda Android y
     * el que usan las apps de bancos: anda desde Android 8. Con «huella o PIN» atado a una llave falla en varios
     * celulares (Samsung, Xiaomi), por eso aquí no se combina con el PIN; el diálogo ofrece «Usar mi contraseña».
     */
    override fun guardaClaves(): Boolean {
        val a = actividad ?: return false
        return BiometricManager.from(a).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // ── candado ──────────────────────────────────────────────────────────────────────────────
    override suspend fun verificar(titulo: String, detalle: String): ResultadoHuella {
        val cifrador = if (conLlave) {
            try {
                cifradorNuevo(ALIAS_CANDADO)
            } catch (e: KeyPermanentlyInvalidatedException) {
                olvidar(); return ResultadoHuella.HuellaNueva
            } catch (e: Exception) {
                null // sin llave (p. ej. el Keystore no la pudo crear): se verifica igual, sin atarla
            }
        } else null
        return when (val r = pedir(titulo, detalle, cifrador)) {
            // con llave: se usa una vez para confirmar que el Keystore la aceptó tras la huella
            is Respuesta.Lista -> if (runCatching { r.cifrador?.doFinal(byteArrayOf(1)) }.isSuccess) ResultadoHuella.Ok
                else ResultadoHuella.Fallo("No se pudo confirmar la huella. Inténtalo de nuevo.")
            Respuesta.Cancelada, Respuesta.Negativo -> ResultadoHuella.Cancelada
            is Respuesta.Error -> ResultadoHuella.Fallo(r.mensaje)
        }
    }

    override fun olvidar() = borrar(ALIAS_CANDADO)

    // ── contraseña para entrar después de cerrar sesión ──────────────────────────────────────
    override suspend fun cifrar(titulo: String, detalle: String, secreto: String): ResultadoSecreto {
        if (!guardaClaves()) return ResultadoSecreto.NoSoportado
        val cifrador = try {
            borrar(ALIAS_CLAVE) // cada activación estrena llave: la anterior (de otra cuenta o contraseña) no sirve más
            val llaveNueva = crearSoloHuella(ALIAS_CLAVE)
            Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, llaveNueva) }
        } catch (e: Exception) {
            return ResultadoSecreto.Fallo("Este celular no pudo crear la llave de la huella (${e.javaClass.simpleName}).")
        }
        return when (val r = pedirSoloHuella(titulo, detalle, cifrador, negativo = "Cancelar")) {
            is Respuesta.Lista -> runCatching {
                val c = r.cifrador ?: error("sin llave")
                val datos = c.doFinal(secreto.encodeToByteArray())
                ResultadoSecreto.Ok(b64(c.iv) + ":" + b64(datos))
            }.getOrElse { ResultadoSecreto.Fallo("No se pudo guardar el acceso con huella. Inténtalo de nuevo.") }
            Respuesta.Cancelada, Respuesta.Negativo -> ResultadoSecreto.Cancelada
            is Respuesta.Error -> ResultadoSecreto.Fallo(r.mensaje)
        }
    }

    override suspend fun descifrar(titulo: String, detalle: String, sobre: String): ResultadoSecreto {
        if (!guardaClaves()) return ResultadoSecreto.NoSoportado
        val partes = sobre.split(':')
        if (partes.size != 2) return ResultadoSecreto.Fallo("El acceso con huella guardado está dañado: entra con tu contraseña.")
        val cifrador = try {
            val llave = llave(ALIAS_CLAVE) ?: return ResultadoSecreto.HuellaNueva // la llave ya no está: se guardó en otro celular o se borró
            Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.DECRYPT_MODE, llave, GCMParameterSpec(128, deB64(partes[0]))) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            olvidarClave(); return ResultadoSecreto.HuellaNueva
        } catch (e: Exception) {
            return ResultadoSecreto.Fallo("No se pudo abrir el acceso con huella: entra con tu contraseña.")
        }
        return when (val r = pedirSoloHuella(titulo, detalle, cifrador, negativo = "Usar mi contraseña")) {
            is Respuesta.Lista -> runCatching { ResultadoSecreto.Ok(r.cifrador!!.doFinal(deB64(partes[1])).decodeToString()) }
                .getOrElse { ResultadoSecreto.Fallo("No se pudo abrir el acceso con huella: entra con tu contraseña.") }
            Respuesta.Cancelada -> ResultadoSecreto.Cancelada
            Respuesta.Negativo -> ResultadoSecreto.UsarContrasena
            is Respuesta.Error -> ResultadoSecreto.Fallo(r.mensaje)
        }
    }

    override fun olvidarClave() = borrar(ALIAS_CLAVE)

    // ── piezas ───────────────────────────────────────────────────────────────────────────────
    /** Abre el diálogo del sistema. Con [exigirLlave], si este Android no acepta la llave, falla (no se autentica sin ella). */
    private suspend fun pedir(titulo: String, detalle: String, cifrador: Cipher?, exigirLlave: Boolean = false): Respuesta {
        val a = actividad ?: return Respuesta.Error("No se pudo abrir la huella en esta pantalla.")
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(Respuesta.Lista(result.cryptoObject?.cipher))
                }

                override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                    if (!cont.isActive) return
                    val cancelada = codigo == BiometricPrompt.ERROR_USER_CANCELED || codigo == BiometricPrompt.ERROR_CANCELED ||
                        codigo == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    cont.resume(if (cancelada) Respuesta.Cancelada else Respuesta.Error(mensaje.toString()))
                }
                // onAuthenticationFailed (huella que no coincide): el diálogo sigue abierto y lo dice solo
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(titulo)
                .setSubtitle(detalle)
                .setAllowedAuthenticators(autenticadores) // con DEVICE_CREDENTIAL no lleva botón «Cancelar» propio
                .setConfirmationRequired(false)
                .build()
            try {
                if (cifrador != null) prompt.authenticate(info, BiometricPrompt.CryptoObject(cifrador)) else prompt.authenticate(info)
            } catch (e: IllegalArgumentException) {
                if (exigirLlave) { if (cont.isActive) cont.resume(Respuesta.Error("Este celular no puede guardar el acceso con huella.")) }
                else prompt.authenticate(info) // combinación que este Android no admite con llave: el candado sin atarla
            }
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    /** Diálogo de SOLO huella (para la llave de la contraseña), con un botón propio: [negativo]. */
    private suspend fun pedirSoloHuella(titulo: String, detalle: String, cifrador: Cipher, negativo: String): Respuesta {
        val a = actividad ?: return Respuesta.Error("No se pudo abrir la huella en esta pantalla.")
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(Respuesta.Lista(result.cryptoObject?.cipher))
                }

                override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                    if (!cont.isActive) return
                    cont.resume(
                        when (codigo) {
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> Respuesta.Negativo
                            BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> Respuesta.Cancelada
                            else -> Respuesta.Error(mensaje.toString())
                        },
                    )
                }
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(titulo)
                .setSubtitle(detalle)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setNegativeButtonText(negativo)
                .setConfirmationRequired(false)
                .build()
            try {
                prompt.authenticate(info, BiometricPrompt.CryptoObject(cifrador))
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Respuesta.Error("Este celular no permitió usar la huella con la llave (${e.javaClass.simpleName})."))
            }
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    /** Llave AES/GCM que solo se usa tras la huella (no el PIN), una vez por uso, invalidada si se agrega una huella. */
    private fun crearSoloHuella(alias: String): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION") spec.setUserAuthenticationValidityDurationSeconds(-1) // cada uso, solo con huella
        }
        gen.init(spec.build())
        return gen.generateKey()
    }

    /** Cifrador para cifrar con la llave [alias] (la crea si no existe). Lanza KeyPermanentlyInvalidatedException si cambió la huella. */
    private fun cifradorNuevo(alias: String): Cipher {
        val llave = llave(alias) ?: crear(alias)
        return Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, llave) }
    }

    private fun llave(alias: String): SecretKey? =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.getKey(alias, null) as? SecretKey

    private fun crear(alias: String): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        }
        gen.init(spec.build())
        return gen.generateKey()
    }

    private fun borrar(alias: String) {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias) }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun deB64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS_CANDADO = "sigro_huella"
        const val ALIAS_CLAVE = "sigro_huella_clave"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
    }
}
