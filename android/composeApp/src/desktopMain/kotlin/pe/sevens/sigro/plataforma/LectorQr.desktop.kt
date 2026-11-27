package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Escritorio (solo para ver la app en la PC y generar capturas): sin cámara. */
@Composable
actual fun LectorQr(modifier: Modifier, alLeer: (String) -> Unit) {}

actual val lectorQrDisponible: Boolean = false
