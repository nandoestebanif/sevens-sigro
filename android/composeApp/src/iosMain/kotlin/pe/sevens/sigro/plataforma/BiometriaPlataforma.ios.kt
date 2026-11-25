package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import pe.sevens.sigro.dominio.Biometria
import pe.sevens.sigro.dominio.SinBiometria

/** iOS: Face ID / Touch ID (LocalAuthentication) queda para cuando se publique en iOS; por ahora, sin huella. */
@Composable
actual fun rememberBiometria(): Biometria = SinBiometria
