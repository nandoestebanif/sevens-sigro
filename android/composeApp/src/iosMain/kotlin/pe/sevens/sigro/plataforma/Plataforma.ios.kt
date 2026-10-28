package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

actual object Plataforma {
    actual val nombre: String = "ios"
    // El simulador de iOS comparte red con la Mac: localhost sí llega a la API.
    actual val servidorPorDefecto: String = "http://localhost:8000"
    actual fun ahoraMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
    actual fun formatearHora(ms: Long): String {
        val f = NSDateFormatter()
        f.dateFormat = "dd/MM HH:mm"
        f.locale = NSLocale(localeIdentifier = "es_PE")
        return f.stringFromDate(NSDate.dateWithTimeIntervalSince1970(ms / 1000.0))
    }
}

@Composable
actual fun ManejarAtras(activo: Boolean, alVolver: () -> Unit) {
    // iOS no tiene botón atrás del sistema: la flecha de la barra superior hace el trabajo.
}
