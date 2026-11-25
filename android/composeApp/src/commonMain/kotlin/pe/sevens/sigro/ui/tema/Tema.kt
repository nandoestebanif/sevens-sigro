package pe.sevens.sigro.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import pe.sevens.sigro.recursos.Res
import pe.sevens.sigro.recursos.poppins_bold
import pe.sevens.sigro.recursos.poppins_medium
import pe.sevens.sigro.recursos.poppins_regular
import pe.sevens.sigro.recursos.poppins_semibold

/*
 * Tema «obra» · rediseño negro y amarillo (24/11), el mismo de la web:
 * negro #161717 / #242424 / #2B2C2C y el amarillo de seguridad #FAD85D como acción principal, con texto
 * negro encima (16 : 1). Pensado para usarse al sol, con guantes y con prisa:
 *  - contraste AA o mejor en texto y botones, en claro y en oscuro (TemaOscuroTest);
 *  - Poppins en toda la app, la misma letra de la web, y botones de al menos 52 dp de alto;
 *  - el estado nunca se comunica solo con color: siempre va con ícono y palabra.
 */

private val Amarillo = Color(0xFFFAD85D)
private val AmarilloOscuro = Color(0xFF7A5D00)
private val Negro = Color(0xFF161717)
private val Fondo = Color(0xFF242424)
private val Tarjeta = Color(0xFF2B2C2C)
private val Tarjeta2 = Color(0xFF353636)
private val Linea = Color(0xFF3D3E3E)
private val Gris = Color(0xFFB4B5B5)

private val Claro = lightColorScheme(
    primary = AmarilloOscuro, onPrimary = Color.White,
    primaryContainer = Amarillo, onPrimaryContainer = Negro,
    secondary = Color(0xFF3D3E3E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E2DD), onSecondaryContainer = Negro,
    background = Color(0xFFF1F1EE), onBackground = Negro,
    surface = Color.White, onSurface = Negro,
    surfaceVariant = Color(0xFFF4F4F1), onSurfaceVariant = Color(0xFF5C5D5D),
    outline = Color(0xFFA9AAAA), outlineVariant = Color(0xFFE2E2DD),
    error = Color(0xFFB42318), onError = Color.White,
    errorContainer = Color(0xFFFDE8E6), onErrorContainer = Color(0xFF7F1D1D),
    // Superficies de Material 3 (barra inferior, menús, hojas): blancas y grises cálidos, no el lila por defecto.
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F4F1), surfaceContainer = Color(0xFFF4F4F1),
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFE2E2DD), surfaceTint = Color.Transparent,
)

private val Oscuro = darkColorScheme(
    // En oscuro el amarillo va tal cual y lo de encima es negro: botones y opciones elegidas en amarillo.
    primary = Amarillo, onPrimary = Negro,
    primaryContainer = Amarillo, onPrimaryContainer = Negro,
    secondary = Color(0xFFD2D3D3), onSecondary = Negro,
    secondaryContainer = Tarjeta2, onSecondaryContainer = Color.White,
    background = Fondo, onBackground = Color.White,
    surface = Tarjeta, onSurface = Color.White,
    surfaceVariant = Tarjeta2, onSurfaceVariant = Gris,
    outline = Color(0xFF6D6E6E), outlineVariant = Linea,
    error = Color(0xFFFF9A9A), onError = Negro,
    errorContainer = Color(0xFF3A1F1F), onErrorContainer = Color(0xFFFFC2C2),
    surfaceContainerLowest = Negro, surfaceContainerLow = Fondo, surfaceContainer = Tarjeta,
    surfaceContainerHigh = Tarjeta, surfaceContainerHighest = Tarjeta2, surfaceTint = Color.Transparent,
)

/** Colores de estado de envío: van siempre acompañados de ícono y texto. */
@Immutable
data class ColoresEstado(
    val pendiente: Color, val pendienteFondo: Color,
    val enviado: Color, val enviadoFondo: Color,
    val rechazado: Color, val rechazadoFondo: Color,
)

private val EstadoClaro = ColoresEstado(
    pendiente = Color(0xFF8A5A00), pendienteFondo = Color(0xFFFFF4D6),
    enviado = Color(0xFF0B7A64), enviadoFondo = Color(0xFFDDF8F2),
    rechazado = Color(0xFFB42318), rechazadoFondo = Color(0xFFFDE8E6),
)
private val EstadoOscuro = ColoresEstado(
    pendiente = Color(0xFFF5C451), pendienteFondo = Color(0xFF2E2A1C),
    enviado = Color(0xFF2EE6C5), enviadoFondo = Color(0xFF12302B),
    rechazado = Color(0xFFFF9A9A), rechazadoFondo = Color(0xFF3A1F1F),
)

val LocalColoresEstado = staticCompositionLocalOf { EstadoClaro }

/*
 * Tipografía: la escala de Material 3 con Poppins (la misma letra de la web). La fuente va en el paquete
 * (composeResources/font, licencia OFL en files/), así se ve igual sin señal y en Android e iOS.
 * Formas: las de Material 3, un poco más redondas, como las tarjetas de la web.
 */
@Composable
private fun poppins() = FontFamily(
    Font(Res.font.poppins_regular, FontWeight.Normal),
    Font(Res.font.poppins_medium, FontWeight.Medium),
    Font(Res.font.poppins_semibold, FontWeight.SemiBold),
    Font(Res.font.poppins_bold, FontWeight.Bold),
    Font(Res.font.poppins_bold, FontWeight.ExtraBold),
)

/** La escala de Material 3 con otra familia de letra en todos sus estilos. */
internal fun Typography.conLetra(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f), displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f), headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f), titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f), bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp),
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
            typography = Typography().conLetra(poppins()),
            shapes = Formas,
            content = contenido,
        )
    }
}
