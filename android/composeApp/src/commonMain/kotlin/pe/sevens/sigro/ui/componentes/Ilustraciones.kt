package pe.sevens.sigro.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Ilustraciones de obra del rediseño (24/11): las mismas de la web (web/src/ui/Ilustraciones.tsx),
 * dibujadas con Canvas sobre un lienzo de 140 × 124. Son decorativas: no llegan al lector de pantalla.
 * Pesan nada (no son imágenes) y se ven nítidas en cualquier densidad de pantalla.
 */

enum class Ilustracion { FACTURA, CAMION, CEMENTO, GRUA, CASCO }

private const val W = 140f
private const val H = 124f
private val Negro = Color(0xFF161717)
private val Amarillo = Color(0xFFFAD85D)

@Composable
fun DibujoObra(cual: Ilustracion, modifier: Modifier = Modifier, ancho: Dp = 72.dp) {
    Canvas(modifier.size(ancho, ancho * (H / W)).clearAndSetSemantics { }) {
        scale(size.width / W, size.height / H, pivot = Offset.Zero) {
            when (cual) {
                Ilustracion.FACTURA -> factura()
                Ilustracion.CAMION -> camion()
                Ilustracion.CEMENTO -> cemento()
                Ilustracion.GRUA -> grua()
                Ilustracion.CASCO -> casco()
            }
        }
    }
}

private fun DrawScope.sombra(rx: Float = 54f) =
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(70f - rx, 104f), Size(rx * 2, 16f))

private fun DrawScope.caja(x: Float, y: Float, w: Float, h: Float, c: Color, r: Float = 0f) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun DrawScope.relleno(d: String, c: Color) = drawPath(svg(d), c)
private fun DrawScope.linea(d: String, c: Color, ancho: Float, guiones: Boolean = false) = drawPath(
    svg(d), c,
    style = Stroke(ancho, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (guiones) PathEffect.dashPathEffect(floatArrayOf(3f, 3f)) else null),
)

private fun DrawScope.factura() {
    sombra(52f)
    relleno("M96 12 L106 18 V100 L96 106 Z", Color(0xFFB9E8DE))
    relleno("M34 12 H96 V106 l-7.75-6 -7.75 6 -7.75-6 -7.75 6 -7.75-6 -7.75 6 -7.75-6 -7.75 6 Z", Color.White)
    caja(34f, 12f, 62f, 18f, Negro)
    caja(48f, 19f, 34f, 4f, Amarillo, 2f)
    listOf(38f to 46f, 50f to 34f, 62f to 40f).forEach { (y, w) -> caja(42f, y, w, 5f, Color(0xFFD9DEDE), 2.5f) }
    caja(42f, 76f, 46f, 12f, Amarillo, 4f)
    // monedas
    drawOval(Color(0xFFC99500), Offset(100f, 99f), Size(28f, 10f)); caja(100f, 92f, 28f, 12f, Color(0xFFE6B422))
    drawOval(Color(0xFFFFE680), Offset(100f, 87f), Size(28f, 10f)); caja(100f, 80f, 28f, 12f, Color(0xFFE6B422))
    drawOval(Color(0xFFFFF0A8), Offset(100f, 75f), Size(28f, 10f))
}

private fun DrawScope.camion() {
    sombra(60f)
    relleno("M10 40 H84 V92 H10 Z", Color.White)
    relleno("M84 40 L92 34 V86 L84 92 Z", Color(0xFFD9D9D9))
    relleno("M10 40 L18 34 H92 L84 40 Z", Color(0xFFF4F4F4))
    listOf(18f to 56f, 40f to 56f, 29f to 46f, 18f to 66f, 40f to 66f).forEach { (x, y) ->
        caja(x, y, 20f, 10f, Amarillo, 1.5f)
        drawRoundRect(Negro, Offset(x, y), Size(20f, 10f), CornerRadius(1.5f), style = Stroke(1.2f))
    }
    relleno("M92 52 H114 L128 70 V94 H92 Z", Negro)
    relleno("M97 57 H112 L122 70 H97 Z", Color(0xFF9FD8FF))
    caja(6f, 90f, 124f, 8f, Color(0xFF353636), 3f)
    listOf(32f, 108f).forEach { x ->
        drawCircle(Negro, 11f, Offset(x, 100f)); drawCircle(Color(0xFFD2D3D3), 4.5f, Offset(x, 100f))
    }
}

private fun DrawScope.cemento() {
    sombra()
    relleno("M98 30 L116 40 L116 102 L98 109 Z", Color(0xFFB0A589))
    drawPath(svg("M28 28 Q63 19 98 30 L98 109 Q63 115 28 106 Z"), Brush.linearGradient(listOf(Color(0xFFFBF7EE), Color(0xFFC4B99F)), Offset(28f, 20f), Offset(98f, 112f)))
    relleno("M28 28 Q63 19 98 30 L116 40 Q80 31 46 35 Z", Color(0xFFFFFDF7))
    linea("M30 32 Q63 24 96 33", Color(0xFFB5AA90), 1.4f, guiones = true)
    relleno("M28 58 Q63 51 98 60 L98 82 Q63 75 28 81 Z", Negro)
    caja(44f, 64f, 38f, 6f, Amarillo, 3f)
    caja(46f, 90f, 34f, 4f, Color(0xFF9D927A), 2f)
}

private fun DrawScope.grua() {
    sombra(56f)
    relleno("M14 56 L52 56 L52 108 L14 108 Z", Color.White)
    relleno("M52 56 L64 50 L64 102 L52 108 Z", Color(0xFFD9D9D9))
    relleno("M14 56 L26 50 L64 50 L52 56 Z", Color(0xFFF4F4F4))
    listOf(20f to 64f, 38f to 64f, 20f to 80f, 38f to 80f).forEach { (x, y) -> caja(x, y, 8f, 8f, Color(0xFF9A9B9B), 1.5f) }
    caja(29f, 96f, 9f, 12f, Color(0xFF9A9B9B), 1.5f)
    linea("M92 108 L92 20 M104 108 L104 20", Negro, 2.6f)
    linea("M92 100 L104 88 L92 76 L104 64 L92 52 L104 40 L92 28", Negro, 2.6f)
    linea("M34 18 L132 18 M34 26 L126 26 M34 18 L34 26", Negro, 2.6f)
    linea("M98 6 L34 18 M98 6 L132 18 M98 6 L98 20", Negro, 2.6f)
    caja(116f, 27f, 16f, 12f, Negro, 2f)
    linea("M50 26 L50 46", Negro, 1.6f)
    caja(30f, 46f, 40f, 7f, Amarillo, 2f)
    drawRoundRect(Negro, Offset(30f, 46f), Size(40f, 7f), CornerRadius(2f), style = Stroke(1.4f))
    caja(84f, 106f, 28f, 6f, Negro, 2f)
}

private fun DrawScope.casco() {
    sombra(56f)
    drawPath(
        svg("M24 82 Q24 26 70 24 Q116 26 116 82 Z"),
        Brush.radialGradient(listOf(Color(0xFFFFF3B0), Amarillo, Color(0xFFD9A514)), Offset(50f, 40f), 90f),
    )
    relleno("M61 25 Q70 19 79 25 L82 80 L58 80 Z", Color(0xFFFFE680))
    linea("M36 80 Q38 50 54 34", Color.White.copy(alpha = 0.6f), 5f)
    relleno("M8 84 Q70 70 132 84 Q134 98 70 98 Q6 98 8 84 Z", Color(0xFFC99500))
    linea("M8 84 Q70 70 132 84", Color(0xFFFFE680), 2f)
    rotate(-18f, Offset(107f, 98f)) { caja(88f, 94f, 38f, 8f, Negro, 4f) }
}
