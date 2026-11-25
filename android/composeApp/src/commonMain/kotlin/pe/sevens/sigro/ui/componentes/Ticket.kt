package pe.sevens.sigro.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.sevens.sigro.ui.tema.Paleta

/**
 * Tarjeta «ticket» del rediseño (como en la web): fondo de color, una franja de luz en diagonal y, si se
 * pide, una ilustración de obra arriba a la derecha. El texto encima va siempre en negro (#161717): todos
 * los colores de ticket pasan AA con él. El contenido decide su propio padding y alto.
 */
@Composable
fun Ticket(
    color: Color,
    modifier: Modifier = Modifier,
    ilustracion: Ilustracion? = null,
    anchoIlustracion: Dp = 64.dp,
    radio: Dp = 22.dp,
    contenido: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.clip(RoundedCornerShape(radio)).background(color)) {
        // franja de luz: decorativa
        Box(
            Modifier.offset(x = (-30).dp, y = 40.dp).size(180.dp, 12.dp).rotate(-14f)
                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f)),
        )
        if (ilustracion != null) {
            DibujoObra(ilustracion, Modifier.align(Alignment.TopEnd).padding(4.dp), anchoIlustracion)
        }
        CompositionLocalProvider(LocalContentColor provides Paleta.SobreTicket) { contenido() }
    }
}

/** Color del ticket número [i]: se turnan menta, amarillo, lila, rosa y durazno, como en la web. */
fun colorTicket(i: Int): Color = Paleta.Tickets[((i % 5) + 5) % 5]
