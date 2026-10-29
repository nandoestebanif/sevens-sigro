package pe.sevens.sigro.plataforma

import androidx.activity.compose.BackHandler
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object Plataforma {
    actual val nombre: String = "android"
    // Ambiente de pruebas en Render. Para la API en la PC desde el emulador: http://10.0.2.2:8000
    actual val servidorPorDefecto: String = "https://sigro-gateway.onrender.com"
    actual fun ahoraMs(): Long = System.currentTimeMillis()
    actual fun formatearHora(ms: Long): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("es-PE")).format(Date(ms))
}

@Composable
actual fun ManejarAtras(activo: Boolean, alVolver: () -> Unit) = BackHandler(enabled = activo, onBack = alVolver)

/** Android no tiene «reducir movimiento» propio: «Quitar animaciones» deja la escala de animación en 0. */
@Composable
actual fun reducirMovimiento(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
