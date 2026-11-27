package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * HU-73 · Visor de la cámara que lee códigos QR y entrega el texto de cada uno que ve. Android: CameraX + ML Kit
 * Barcode Scanning con el modelo dentro de la app (lee sin señal). iOS y escritorio: todavía no (ver [lectorQrDisponible]).
 * El permiso de cámara lo resuelve la pantalla antes de mostrarlo (con [pe.sevens.sigro.dominio.Camara]).
 */
@Composable
expect fun LectorQr(modifier: Modifier, alLeer: (String) -> Unit)

/** Esta plataforma puede leer QR con la cámara. */
expect val lectorQrDisponible: Boolean
