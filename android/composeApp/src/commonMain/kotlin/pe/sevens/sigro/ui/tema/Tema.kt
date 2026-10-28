package pe.sevens.sigro.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Tema «obra»: el mismo naranja de seguridad que la web (C2410C) sobre grises pizarra.
 * Pensado para usarse al sol, con guantes y con prisa:
 *  - contraste AA o mejor en texto y botones (naranja 700 sobre blanco = 5,2 : 1);
 *  - texto base de 17 sp y botones de al menos 56 dp de alto;
 *  - el estado nunca se comunica solo con color: siempre va con ícono y palabra.
 */

private val Naranja = Color(0xFFC2410C)
private val NaranjaClaro = Color(0xFFFFEDD5)
private val NaranjaOscuro = Color(0xFF7C2D12)
private val Pizarra900 = Color(0xFF0F172A)
private val Pizarra800 = Color(0xFF1E293B)
private val Pizarra700 = Color(0xFF334155)
private val Pizarra600 = Color(0xFF475569)
private val Pizarra200 = Color(0xFFE2E8F0)
private val Pizarra100 = Color(0xFFF1F5F9)
private val Pizarra50 = Color(0xFFF8FAFC)

private val Claro = lightColorScheme(
    primary = Naranja, onPrimary = Color.White,
    primaryContainer = NaranjaClaro, onPrimaryContainer = NaranjaOscuro,
    secondary = Pizarra700, onSecondary = Color.White,
    secondaryContainer = Pizarra200, onSecondaryContainer = Pizarra900,
    background = Pizarra50, onBackground = Pizarra900,
    surface = Color.White, onSurface = Pizarra900,
    surfaceVariant = Pizarra100, onSurfaceVariant = Pizarra600,
    outline = Color(0xFF94A3B8), outlineVariant = Pizarra200,
    error = Color(0xFFB91C1C), onError = Color.White,
    errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D),
)

private val Oscuro = darkColorScheme(
    // En oscuro el naranja se aclara (para que los botones de texto se lean sobre fondo oscuro)
    // y lo que va encima es marrón muy oscuro, nunca blanco: así ambos pasan AA.
    primary = Color(0xFFFB923C), onPrimary = Color(0xFF431407),
    primaryContainer = NaranjaOscuro, onPrimaryContainer = NaranjaClaro,
    secondary = Color(0xFFCBD5E1), onSecondary = Pizarra900,
    secondaryContainer = Pizarra700, onSecondaryContainer = Pizarra100,
    background = Pizarra900, onBackground = Pizarra100,
    surface = Pizarra800, onSurface = Pizarra100,
    surfaceVariant = Pizarra700, onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B), outlineVariant = Pizarra700,
    error = Color(0xFFF87171), onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D), onErrorContainer = Color(0xFFFEE2E2),
)

/** Colores de estado de envío: van siempre acompañados de ícono y texto. */
@Immutable
data class ColoresEstado(
    val pendiente: Color, val pendienteFondo: Color,
    val enviado: Color, val enviadoFondo: Color,
    val rechazado: Color, val rechazadoFondo: Color,
)

private val EstadoClaro = ColoresEstado(
    pendiente = Color(0xFF92400E), pendienteFondo = Color(0xFFFEF3C7),
    enviado = Color(0xFF166534), enviadoFondo = Color(0xFFDCFCE7),
    rechazado = Color(0xFF991B1B), rechazadoFondo = Color(0xFFFEE2E2),
)
private val EstadoOscuro = ColoresEstado(
    pendiente = Color(0xFFFCD34D), pendienteFondo = Color(0xFF451A03),
    enviado = Color(0xFF86EFAC), enviadoFondo = Color(0xFF052E16),
    rechazado = Color(0xFFFCA5A5), rechazadoFondo = Color(0xFF450A0A),
)

val LocalColoresEstado = staticCompositionLocalOf { EstadoClaro }

private val base = Typography()
private val Tipos = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
)

private val Formas = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

/** Texto de cifras: ancho fijo para que las cantidades se alineen en listas. */
val EstiloCifra = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.2.sp)

@Composable
fun TemaSigro(oscuro: Boolean = isSystemInDarkTheme(), contenido: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalColoresEstado provides if (oscuro) EstadoOscuro else EstadoClaro,
    ) {
        MaterialTheme(
            colorScheme = if (oscuro) Oscuro else Claro,
            typography = Tipos,
            shapes = Formas,
            content = contenido,
        )
    }
}
