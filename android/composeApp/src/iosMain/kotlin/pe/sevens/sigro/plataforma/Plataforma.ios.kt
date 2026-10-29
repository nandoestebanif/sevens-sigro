package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

actual object Plataforma {
    actual val nombre: String = "ios"
    // Ambiente de pruebas en Render. El simulador comparte red con la Mac: http://localhost:8000 llega a la API local.
    actual val servidorPorDefecto: String = "https://sigro-gateway.onrender.com"
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

@Composable
actual fun reducirMovimiento(): Boolean = UIAccessibilityIsReduceMotionEnabled()
