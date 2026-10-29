package pe.sevens.sigro.ui.componentes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pe.sevens.sigro.ui.tema.Paleta

/*
 * Fondos de plano por pantalla (diseno/fondos/fondos.py y los lienzos .dc.html).
 * Se dibujan en las coordenadas del lienzo (390 × 844) y se estiran a la pantalla.
 * Son decorativos: nunca datos de la obra.
 */

const val ANCHO_LIENZO = 390f
const val ALTO_LIENZO = 844f

internal fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

/** Dibuja en coordenadas del lienzo. */
internal inline fun DrawScope.enLienzo(bloque: DrawScope.() -> Unit) =
    withTransform({ scale(size.width / ANCHO_LIENZO, size.height / ALTO_LIENZO, pivot = Offset.Zero) }) { bloque() }

private val NaranjaMancha = Color(0xFFFB923C)
private val AzulMancha = Color(0xFF60A5FA)

/** Degradado, cuadrícula de 24 y de 120 y manchas difusas: la base de todos los fondos. */
internal fun DrawScope.fondoBase(c0: Color, c1: Color, c2: Color, manchas: List<Triple<Offset, Float, Color>>) {
    drawRect(Brush.linearGradient(0f to c0, 0.55f to c1, 1f to c2, start = Offset.Zero, end = Offset(ANCHO_LIENZO, ALTO_LIENZO)))
    cuadricula(24f, Color(0xFF94A3B8).copy(alpha = 0.4f), 0.5f)
    cuadricula(120f, Color(0xFF64748B).copy(alpha = 0.3f), 0.8f)
    manchas.forEach { (centro, radio, color) ->
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), centro, radio), radio, centro)
    }
}

internal fun DrawScope.cuadricula(paso: Float, color: Color, grosor: Float) {
    var x = 0f
    while (x <= ANCHO_LIENZO) { drawLine(color, Offset(x, 0f), Offset(x, ALTO_LIENZO), grosor); x += paso }
    var y = 0f
    while (y <= ALTO_LIENZO) { drawLine(color, Offset(0f, y), Offset(ANCHO_LIENZO, y), grosor); y += paso }
}

internal fun DrawScope.trazo(
    p: Path, alfa: Float, grosor: Float, color: Color = Paleta.Pizarra,
    discontinuo: FloatArray? = null, cuadrado: Boolean = false,
) = drawPath(
    p, color.copy(alpha = alfa),
    style = Stroke(
        grosor,
        cap = if (cuadrado) StrokeCap.Square else StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = discontinuo?.let { PathEffect.dashPathEffect(it) },
    ),
)

/** Texto con la línea base en `y`, como en el SVG. */
internal fun DrawScope.rotulo(
    m: TextMeasurer, texto: String, x: Float, y: Float, alfa: Float,
    color: Color = Paleta.Pizarra, tam: Float = 10f, negrita: Boolean = false, centrado: Boolean = false,
) {
    val r = m.measure(texto, TextStyle(color = color.copy(alpha = alfa), fontSize = tam.sp, fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal))
    // sp se mide con la densidad de la pantalla; el lienzo ya está escalado: se compensa.
    val k = tam / r.size.height.toFloat() * 1.2f
    withTransform({ scale(k, k, pivot = Offset(x, y)) }) {
        val dx = if (centrado) r.size.width / 2f else 0f
        drawText(r, topLeft = Offset(x - dx, y - r.firstBaseline))
    }
}

// ── Entrar: planta arquitectónica con ejes A·B·C / 1·2·3 ──────────────────────────────────────

private val ejesPlanta = svg("M50 450v380M195 450v380M340 450v380M20 500h350M20 650h350M20 800h350")
private val murosPlanta = svg("M50 500h100M175 500h165v130M340 670v130H230M200 800H50V700M50 670V500M195 500v100M195 630v170M50 650h110M230 650h110")
private val puertasPlanta = svg("M150 500a25 25 0 0 1 25 25M150 500v25M50 700a30 30 0 0 0 30 -30M50 670h30M230 800a30 30 0 0 1 -30 -30M200 800v-30M336 640v20M344 640v20")
private val cotaPlanta = svg("M372 500v300M366 500h12M366 800h12")

fun DrawScope.planoEntrar(m: TextMeasurer) = enLienzo {
    fondoBase(
        Color(0xFFEEF1F6), Color(0xFFF2F0EE), Color(0xFFF5EAE1),
        listOf(Triple(Offset(320f, 520f), 200f, NaranjaMancha.copy(alpha = 0.5f)), Triple(Offset(60f, 780f), 200f, AzulMancha.copy(alpha = 0.4f)), Triple(Offset(360f, 820f), 120f, NaranjaMancha.copy(alpha = 0.5f))),
    )
    trazo(ejesPlanta, 0.45f, 0.9f, discontinuo = floatArrayOf(6f, 4f))
    listOf(50f, 195f, 340f).forEach { drawCircle(Paleta.Pizarra.copy(alpha = 0.6f), 10f, Offset(it, 440f), style = Stroke(1.2f)) }
    listOf(500f, 650f, 800f).forEach { drawCircle(Paleta.Pizarra.copy(alpha = 0.6f), 9f, Offset(12f, it), style = Stroke(1.2f)) }
    listOf("A" to 50f, "B" to 195f, "C" to 340f).forEach { (t, x) -> rotulo(m, t, x, 444f, 0.65f, negrita = true, centrado = true) }
    listOf("1" to 504f, "2" to 654f, "3" to 804f).forEach { (t, y) -> rotulo(m, t, 12f, y, 0.65f, negrita = true, centrado = true) }
    trazo(murosPlanta, 0.5f, 3.2f, cuadrado = true)
    trazo(puertasPlanta, 0.5f, 1f)
    for (x in listOf(47f, 192f, 337f)) for (y in listOf(497f, 647f, 797f)) {
        drawRect(Paleta.Pizarra.copy(alpha = 0.5f), Offset(x, y), androidx.compose.ui.geometry.Size(7f, 7f))
    }
    rotulo(m, "ALMACÉN", 72f, 585f, 0.55f); rotulo(m, "OFICINA", 230f, 585f, 0.55f)
    rotulo(m, "VESTUARIO", 232f, 735f, 0.55f); rotulo(m, "PATIO", 78f, 735f, 0.55f)
    trazo(cotaPlanta, 0.55f, 1.1f, Paleta.Naranja)
    rotate(-90f, Offset(384f, 668f)) { rotulo(m, "9.20 m", 384f, 668f, 0.7f, Paleta.Naranja) }
}

// ── El plano se dibuja (Entrar → Elegir obra) ─────────────────────────────────────────────────

/** Los 8 grupos de trazos, en orden: terreno, columnas, vigas, techo, grúa, puertas, ventanas, cota. */
internal val gruposPlanoQueSeDibuja: List<String> = listOf(
    "M20 640H370",
    "M60 640V340M150 640V340M240 640V340M330 640V340",
    "M60 565H330M60 490H330M60 415H330M60 340H330",
    "M60 340L195 300L330 340",
    "M290 640V150M290 150H140M290 150H350M140 150V190M120 190h40v18h-40z",
    "M84 610h40v30M174 610h40v30M264 610h40v30",
    "M80 455h50v25h-50zM170 455h50v25h-50zM260 455h50v25h-50zM80 380h50v25h-50zM170 380h50v25h-50zM260 380h50v25h-50z",
    "M60 680H330M60 672v16M330 672v16",
)

/** Retraso de cada grupo en ms (lienzo Main.dc.html). Cada trazo dura 900 ms. */
internal val retrasosPlano = listOf(0L, 150L, 420L, 700L, 820L, 1050L, 1150L, 1350L)

/** Parte un `d` en sus subtrazos (cada `M`), para dibujar cada uno de 0 a su largo. */
internal fun subtrazos(d: String): List<Path> =
    Regex("M[^M]*").findAll(d).map { svg(it.value) }.toList()

internal fun DrawScope.fondoPlanoQueSeDibuja() = enLienzo {
    drawRect(Color(0xFFF3F5F9))
    cuadricula(24f, Color(0xFF94A3B8).copy(alpha = 0.45f), 0.5f)
    drawCircle(Brush.radialGradient(listOf(NaranjaMancha.copy(alpha = 0.35f), NaranjaMancha.copy(alpha = 0f)), Offset(300f, 200f), 220f), 220f, Offset(300f, 200f))
}
