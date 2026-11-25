package pe.sevens.sigro.plataforma

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
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
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.coroutines.resume

/**
 * Android: BiometricPrompt (huella, rostro o el PIN/patrón del celular). La huella nunca llega a la app: Android
 * solo responde si quien está es el dueño.
 *
 * Desde Android 11 (API 30) la verificación va atada a una llave AES del Keystore creada con
 * `setInvalidatedByBiometricEnrollment(true)`: si alguien agrega una huella nueva al celular, la llave deja de
 * servir (KeyPermanentlyInvalidatedException) y SIGRO pide la contraseña otra vez. En 8–10 no hay esa llave
 * (Android no permite combinarla con el PIN ahí) y se usa la verificación simple.
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

    override suspend fun verificar(titulo: String, detalle: String): ResultadoHuella {
        val a = actividad ?: return ResultadoHuella.Fallo("No se pudo abrir la huella en esta pantalla.")
        val cifrador = if (conLlave) {
            try {
                cifrador()
            } catch (e: KeyPermanentlyInvalidatedException) {
                olvidar()
                return ResultadoHuella.HuellaNueva
            } catch (e: Exception) {
                null // sin llave (p. ej. el Keystore no la pudo crear): se verifica igual, sin atarla
            }
        } else null

        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // con llave: se usa una vez para confirmar que el Keystore la aceptó tras la huella
                    val ok = runCatching { result.cryptoObject?.cipher?.doFinal(byteArrayOf(1)) }.isSuccess
                    if (cont.isActive) cont.resume(if (ok) ResultadoHuella.Ok else ResultadoHuella.Fallo("No se pudo confirmar la huella. Inténtalo de nuevo."))
                }

                override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                    if (!cont.isActive) return
                    val cancelada = codigo == BiometricPrompt.ERROR_USER_CANCELED || codigo == BiometricPrompt.ERROR_CANCELED ||
                        codigo == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    cont.resume(if (cancelada) ResultadoHuella.Cancelada else ResultadoHuella.Fallo(mensaje.toString()))
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
                prompt.authenticate(info) // combinación que este Android no admite con llave: sin atarla
            }
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    override fun olvidar() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    /** Cifrador AES/GCM con la llave de la huella; la crea la primera vez. Lanza KeyPermanentlyInvalidatedException si cambió la huella. */
    private fun cifrador(): Cipher {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            }
            gen.init(spec.build())
            gen.generateKey()
        }
        val llave = ks.getKey(ALIAS, null) as SecretKey
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, llave) }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "sigro_huella"
    }
}
