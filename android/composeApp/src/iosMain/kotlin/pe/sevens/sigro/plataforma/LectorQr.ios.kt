package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** iOS: el lector con AVFoundation queda para cuando se compile en una Mac; por ahora no está disponible. */
@Composable
actual fun LectorQr(modifier: Modifier, alLeer: (String) -> Unit) {}

actual val lectorQrDisponible: Boolean = false
