package pe.sevens.sigro.ui.componentes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import pe.sevens.sigro.ui.tema.Paleta

/*
 * Color por módulo (rediseño v2, 24/11): cada parte de la app tiene su color y su ilustración, así el
 * almacenero sabe dónde está sin leer. Son los mismos colores de los accesos del inicio.
 */
enum class Modulo(val color: Color, val dibujo: Ilustracion) {
    SALIDA(Paleta.TicketAmarillo, Ilustracion.CEMENTO),
    ENTRADA(Paleta.TicketMenta, Ilustracion.FACTURA),
    INGRESO(Paleta.TicketLila, Ilustracion.CAMION),
    PERSONAL(Paleta.TicketDurazno, Ilustracion.CASCO),
    STOCK(Paleta.TicketRosa, Ilustracion.GRUA),
    REGISTROS(Paleta.TicketMenta, Ilustracion.FACTURA),
    CATALOGO(Paleta.TicketAmarillo, Ilustracion.CEMENTO),
}

/**
 * Cabecera de color del módulo: banda con esquinas inferiores redondas, botón «volver» negro, título grande,
 * subtítulo e ilustración. Con [pasos] muestra una barra por paso y las completadas en negro (avance del
 * formulario: no cambia el orden ni la validación). Todo lo de encima va en negro (#161717, AA con los cinco colores).
 */
@Composable
fun CabeceraModulo(
    modulo: Modulo,
    titulo: String,
    alVolver: () -> Unit,
    subtitulo: String? = null,
    pasos: Pair<Int, Int>? = null,
    conInsets: Boolean = true,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    val negro = Paleta.SobreTicket
    CompositionLocalProvider(LocalContentColor provides negro) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)).background(modulo.color),
        ) {
            // franja de luz e ilustración (decorativas)
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 30.dp, y = (-30).dp).size(260.dp, 14.dp).rotate(-14f)
                    .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f)),
            )
            DibujoObra(modulo.dibujo, Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 14.dp), ancho = 112.dp)
            Column(
                Modifier.fillMaxWidth().then(if (conInsets) Modifier.statusBarsPadding() else Modifier)
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = alVolver, modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = negro, contentColor = modulo.color),
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                    Box(Modifier.weight(1f))
                    acciones()
                }
                if (pasos != null) {
                    Text(
                        "PASO ${minOf(pasos.first + 1, pasos.second)} DE ${pasos.second}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.08.em, color = negro, modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Text(
                    titulo, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, color = negro,
                    modifier = Modifier.padding(top = if (pasos == null) 10.dp else 0.dp, end = 110.dp).semantics { heading() },
                )
                if (subtitulo != null) Text(subtitulo, fontSize = 13.sp, color = negro.copy(alpha = 0.78f), modifier = Modifier.padding(end = 110.dp))
                if (pasos != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp, end = 110.dp)
                            .semantics(mergeDescendants = true) { contentDescription = "${pasos.first} de ${pasos.second} pasos completos" },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(pasos.second) { k ->
                            Box(
                                Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50))
                                    .background(if (k < pasos.first) negro else negro.copy(alpha = 0.2f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Se hunde un poco al tocarlo (96 %), como un botón físico. Va con el `interactionSource` del clickable.
 * Es corta (120 ms) para no estorbar a quien trabaja rápido.
 */
@Composable
fun Modifier.hundirAlTocar(fuente: MutableInteractionSource): Modifier {
    val apretado by fuente.collectIsPressedAsState()
    val escala by animateFloatAsState(if (apretado) 0.96f else 1f, tween(120), label = "hundir")
    return this.scale(escala)
}

@Composable
fun rememberFuente() = remember { MutableInteractionSource() }
