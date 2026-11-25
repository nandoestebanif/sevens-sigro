package pe.sevens.sigro.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pe.sevens.sigro.ui.tema.Paleta
import pe.sevens.sigro.ui.tema.Apariencia
import pe.sevens.sigro.ui.tema.TemaSigro

/*
 * Componentes base de la app sobre Material 3 (Compose Multiplatform).
 *
 * Los nombres (FondoPlano, vidrio, BotonNegro, CampoVidrio…) vienen de la versión anterior y se
 * conservan para no tocar las 30 pantallas que los usan. Lo que cambió es lo de adentro: ya no hay
 * desenfoque ni dibujos propios; cada pieza es un componente estándar de Material 3 con los colores
 * del tema (MaterialTheme.colorScheme). Si mañana cambia el diseño, se cambia aquí y nada más.
 */

/**
 * Fondo de pantalla: aplica el tema de la app y pinta el color «background» de Material.
 * El parámetro `plano` se dejó por compatibilidad (antes dibujaba un plano de obra detrás); hoy no se usa.
 */
@Composable
fun FondoPlano(
    @Suppress("UNUSED_PARAMETER") plano: DrawScope.(TextMeasurer) -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable BoxScope.() -> Unit,
) {
    TemaSigro(oscuro = Apariencia.oscuro) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), content = contenido)
    }
}

/**
 * Superficie de tarjeta para un Column/Row que ya existe: fondo «surface», borde «outlineVariant»
 * y una sombra corta (elevación 1–3 dp, como una Card de Material). Para algo nuevo, mejor usar [TarjetaVidrio].
 */
@Composable
fun Modifier.vidrio(forma: Shape, fuerte: Boolean = false, sombra: Dp = 10.dp): Modifier {
    // Rediseño: tarjeta plana como en la web. En oscuro, #2B2C2C sin borde ni sombra (la diferencia con el
    // fondo #242424 basta); en claro, blanca con un borde fino y una sombra mínima para separarla del fondo.
    val colores = MaterialTheme.colorScheme
    val elevacion = if (Apariencia.oscuro || sombra == 0.dp) 0.dp else if (fuerte) 2.dp else 1.dp
    val base = this.shadow(elevacion, forma).clip(forma).background(colores.surface)
    return if (Apariencia.oscuro) base else base.border(1.dp, colores.outlineVariant, forma)
}

/**
 * Botón principal: `Button` de Material 3 en el amarillo de la marca con texto negro (en claro y en oscuro).
 * `contorno = true` da el secundario: `OutlinedButton`. Mínimo 56 dp de alto y esquinas de 16: se usa con guantes.
 */
@Composable
fun BotonNegro(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    @Suppress("UNUSED_PARAMETER") colorIcono: Color = Color.Unspecified,
    habilitado: Boolean = true,
    cargando: Boolean = false,
    contorno: Boolean = false,
) {
    val m = modifier.fillMaxWidth().heightIn(min = 56.dp)
    val forma = RoundedCornerShape(16.dp)
    // El contenido es el mismo para los dos tipos de botón: spinner o ícono, y el texto.
    val contenido: @Composable () -> Unit = {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            Spacer(Modifier.width(12.dp))
        } else if (icono != null) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp))
    }
    if (contorno) {
        OutlinedButton(onClick = alPulsar, enabled = habilitado && !cargando, modifier = m, shape = forma) { contenido() }
    } else {
        Button(
            onClick = alPulsar, enabled = habilitado && !cargando, modifier = m, shape = forma,
            colors = ButtonDefaults.buttonColors(containerColor = Paleta.Principal, contentColor = Paleta.SobrePrincipal),
        ) { contenido() }
    }
}

/**
 * Campo de texto: `OutlinedTextField` de Material 3. La etiqueta flota sobre el borde al escribir,
 * `supportingText` muestra la ayuda o el error, e `isError` pinta el borde con el color de error.
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
    val pie = error ?: ayuda
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        placeholder = marcador?.let { { Text(it) } },
        supportingText = pie?.let { { Text(it) } },
        isError = error != null,
        suffix = sufijo?.let { { Text(it) } },
        trailingIcon = finalIcono,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        visualTransformation = transformacion,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Aviso informativo que no bloquea (regla 7): Card con el color «primaryContainer» del tema. */
@Composable
fun AvisoNaranja(mensaje: String, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(mensaje, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
    }
}
