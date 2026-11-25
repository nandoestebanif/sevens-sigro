package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import pe.sevens.sigro.dominio.Biometria
import pe.sevens.sigro.dominio.SinBiometria

/** Escritorio (solo para ver la app en la PC y generar capturas): sin huella. */
@Composable
actual fun rememberBiometria(): Biometria = SinBiometria
