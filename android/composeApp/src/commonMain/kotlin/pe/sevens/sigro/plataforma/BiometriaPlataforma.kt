package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import pe.sevens.sigro.dominio.Biometria

/**
 * La huella de la plataforma, recordada en la composición: en Android BiometricPrompt necesita la actividad
 * (FragmentActivity) de la pantalla, por eso es @Composable.
 */
@Composable
expect fun rememberBiometria(): Biometria
