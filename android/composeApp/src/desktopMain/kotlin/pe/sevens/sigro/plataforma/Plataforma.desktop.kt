package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object Plataforma {
    actual val nombre: String = "pc"
    actual val servidorPorDefecto: String = "http://localhost:8000"
    actual fun ahoraMs(): Long = System.currentTimeMillis()
    actual fun formatearHora(ms: Long): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("es-PE")).format(Date(ms))
}

@Composable
actual fun ManejarAtras(activo: Boolean, alVolver: () -> Unit) {
    // En la PC se vuelve con la flecha de la barra superior.
}

@Composable
actual fun reducirMovimiento(): Boolean = false
