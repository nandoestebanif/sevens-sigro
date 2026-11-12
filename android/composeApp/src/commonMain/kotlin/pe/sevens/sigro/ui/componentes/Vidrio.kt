package pe.sevens.sigro.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import pe.sevens.sigro.plataforma.vidrioSinDesenfoque
import pe.sevens.sigro.ui.tema.Apariencia
import pe.sevens.sigro.ui.tema.Paleta
import pe.sevens.sigro.ui.tema.TemaSigro

/*
 * Estilo vidrio (diseno/LEEME.md): tarjetas translúcidas sobre un fondo de plano distinto por pantalla.
 * El desenfoque real lo hace Haze con lo que está detrás; en Android < 12 Haze no puede desenfocar
 * y pinta el blanco de respaldo, más opaco para que el texto se siga leyendo. El mismo respaldo se usa
 * en gama baja y con «reducir movimiento» (vidrioSinDesenfoque).
 */

/**
 * Invierte la luz y gira el tono 180°: blanco → negro, gris oscuro → gris claro, y el naranja sigue naranja.
 * (−H · c + 255, con H la rotación de tono de 180° que conserva la luminancia; cada fila de H suma 1.)
 */
private val INVERTIR_CONSERVANDO_COLOR = ColorMatrix(
    floatArrayOf(
        0.574f, -1.430f, -0.144f, 0f, 255f,
        -0.426f, -0.430f, -0.144f, 0f, 255f,
        -0.426f, -1.430f, 0.856f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** El fondo de plano de la pantalla: lo que el vidrio desenfoca. Null fuera de [FondoPlano]. */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

// Funciones y no valores: el estilo sigue al tema (claro u oscuro) cada vez que se pinta.
private val EstiloVidrio: HazeStyle get() = HazeStyle(
    backgroundColor = Paleta.FondoBase,
    tint = HazeTint(Paleta.Vidrio),
    blurRadius = 18.dp,
    noiseFactor = 0f,
    fallbackTint = HazeTint(Paleta.Superficie.copy(alpha = 0.82f)),
)
private val EstiloVidrioFuerte: HazeStyle get() = HazeStyle(
    backgroundColor = Paleta.FondoBase,
    tint = HazeTint(Paleta.VidrioFuerte),
    blurRadius = 22.dp,
    noiseFactor = 0f,
    fallbackTint = HazeTint(Paleta.Superficie.copy(alpha = 0.9f)),
)

/** Pantalla en estilo vidrio: tema claro siempre (el diseño no tiene versión oscura) y su plano de fondo. */
@Composable
fun FondoPlano(
    plano: DrawScope.(TextMeasurer) -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable BoxScope.() -> Unit,
) {
    // Sin desenfoque no hay fuente de Haze: vidrio() ve LocalHaze = null y pinta el blanco de respaldo.
    val haze = if (vidrioSinDesenfoque()) null else rememberHazeState()
    val medidor = rememberTextMeasurer()
    TemaSigro(oscuro = Apariencia.oscuro) {
        Box(modifier.fillMaxSize().background(Paleta.FondoBase)) {
            // El plano acompaña, no compite: al 55 % el texto sobre él gana contraste y la pantalla se lee más limpia.
            // En oscuro el plano se pinta «invertido con su color»: el papel claro pasa a negro y los trazos oscuros a
            // líneas claras, sin perder el naranja de las cotas. Así el negro queda negro y el vidrio tiene algo que desenfocar.
            val oscuro = Apariencia.oscuro
            Canvas(
                Modifier.fillMaxSize().graphicsLayer { alpha = if (oscuro) 0.42f else 0.55f }
                    .then(if (haze != null) Modifier.hazeSource(haze) else Modifier),
            ) {
                if (!oscuro) {
                    plano(medidor)
                } else {
                    drawIntoCanvas { lienzo ->
                        lienzo.saveLayer(Rect(Offset.Zero, size), Paint().apply { colorFilter = ColorFilter.colorMatrix(INVERTIR_CONSERVANDO_COLOR) })
                        plano(medidor)
                        lienzo.restore()
                    }
                }
            }
            CompositionLocalProvider(LocalHaze provides haze) { contenido() }
        }
    }
}

/** Vidrio: blanco 58 % + desenfoque 18 px + borde blanco 75 % + sombra suave. «Fuerte»: 78 % y 22 px. */
@Composable
fun Modifier.vidrio(forma: Shape, fuerte: Boolean = false, sombra: Dp = 10.dp): Modifier {
    val haze = LocalHaze.current
    val recorte = this
        .shadow(sombra, forma, ambientColor = Paleta.Tinta.copy(alpha = 0.10f), spotColor = Paleta.Tinta.copy(alpha = 0.16f))
        .clip(forma)
    val relleno = if (haze != null) {
        // inputScale Auto: Haze desenfoca una copia a 1/3 de resolución y la estira. Con 18–22 dp de
        // desenfoque no se distingue a la vista y cada tarjeta procesa ~9 veces menos píxeles por cuadro.
        recorte.hazeEffect(haze, if (fuerte) EstiloVidrioFuerte else EstiloVidrio) { inputScale = HazeInputScale.Auto }
    } else {
        recorte.background(Paleta.Superficie.copy(alpha = if (fuerte) 0.9f else 0.82f))
    }
    return relleno.border(1.dp, if (fuerte) Paleta.BordeVidrioFuerte else Paleta.BordeVidrio, forma)
}

/** Botón principal del diseño: píldora negra de 58 dp. `contorno` es el secundario (borde negro). */
@Composable
fun BotonNegro(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    colorIcono: Color = Paleta.SobrePrincipal,
    habilitado: Boolean = true,
    cargando: Boolean = false,
    contorno: Boolean = false,
) {
    val forma = RoundedCornerShape(29.dp)
    val m = modifier.fillMaxWidth().height(58.dp)
    val contenido: @Composable () -> Unit = {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = if (contorno) Paleta.Tinta else Paleta.SobrePrincipal)
            Spacer(Modifier.width(12.dp))
        } else if (icono != null) {
            Icon(icono, contentDescription = null, tint = if (contorno) Paleta.Tinta else colorIcono, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
    if (contorno) {
        OutlinedButton(
            onClick = alPulsar, enabled = habilitado && !cargando, modifier = m, shape = forma,
            border = BorderStroke(1.5.dp, Paleta.Tinta),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Paleta.Tinta),
        ) { contenido() }
    } else {
        Button(
            onClick = alPulsar, enabled = habilitado && !cargando,
            modifier = m.shadow(if (habilitado) 12.dp else 0.dp, forma, spotColor = Paleta.Tinta.copy(alpha = 0.4f)),
            shape = forma,
            colors = ButtonDefaults.buttonColors(
                containerColor = Paleta.Principal, contentColor = Paleta.SobrePrincipal,
                disabledContainerColor = Paleta.Gris, disabledContentColor = Paleta.SobreTinta,
            ),
        ) { contenido() }
    }
}

/**
 * Campo del diseño: etiqueta arriba, caja de 56 dp con borde fino y fondo blanco 65 %,
 * ayuda o error debajo. Mismos parámetros que [Campo] para cambiar uno por otro.
 */
@Composable
fun CampoVidrio(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    marcador: String? = null,
    error: String? = null,
    ayuda: String? = null,
    teclado: KeyboardType = KeyboardType.Text,
    sufijo: String? = null,
    transformacion: VisualTransformation = VisualTransformation.None,
    finalIcono: @Composable (() -> Unit)? = null,
) {
    var enfocado by remember { mutableStateOf(false) }
    val borde = when {
        error != null -> Paleta.Rojo
        enfocado -> Paleta.Naranja
        else -> Paleta.BordeCampo
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(etiqueta, fontSize = 14.sp, color = Paleta.Texto3)
        BasicTextField(
            value = valor,
            onValueChange = alCambiar,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = teclado),
            visualTransformation = transformacion,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Paleta.Tinta),
            cursorBrush = SolidColor(Paleta.Naranja),
            modifier = Modifier.fillMaxWidth().onFocusChanged { enfocado = it.isFocused }
                .semantics { contentDescription = etiqueta },
            decorationBox = { campo ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .background(Paleta.Superficie.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                        .border(if (enfocado || error != null) 2.dp else 1.dp, borde, RoundedCornerShape(14.dp))
                        .padding(start = 16.dp, end = if (finalIcono != null) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (valor.isEmpty() && marcador != null) {
                            Text(marcador, style = MaterialTheme.typography.bodyLarge, color = Paleta.NeutroSuave)
                        }
                        campo()
                    }
                    if (sufijo != null) Text(sufijo, fontWeight = FontWeight.SemiBold, color = Paleta.Texto2, modifier = Modifier.padding(start = 8.dp))
                    finalIcono?.invoke()
                }
            },
        )
        val pie = error ?: ayuda
        if (pie != null) {
            Text(pie, fontSize = 13.sp, color = if (error != null) Paleta.RechazadoTexto else Paleta.Texto3)
        }
    }
}

/** Aviso del diseño sobre naranja claro (información que no bloquea: regla 7). */
@Composable
fun AvisoNaranja(mensaje: String, modifier: Modifier = Modifier) {
    Text(
        mensaje, fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.NaranjaTexto,
        modifier = modifier.fillMaxWidth().background(Paleta.NaranjaClaro, RoundedCornerShape(14.dp)).padding(14.dp),
    )
}
