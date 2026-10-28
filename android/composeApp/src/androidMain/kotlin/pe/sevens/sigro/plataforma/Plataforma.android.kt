package pe.sevens.sigro.plataforma

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object Plataforma {
    actual val nombre: String = "android"
    actual val servidorPorDefecto: String = "http://10.0.2.2:8000"
    actual fun ahoraMs(): Long = System.currentTimeMillis()
    actual fun formatearHora(ms: Long): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("es-PE")).format(Date(ms))
}

@Composable
actual fun ManejarAtras(activo: Boolean, alVolver: () -> Unit) = BackHandler(enabled = activo, onBack = alVolver)
