package pe.sevens.sigro.ui.componentes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import pe.sevens.sigro.ui.tema.Paleta

/*
 * Base de los fondos del Sprint 2 y fondos de Materiales (diseno/LEEME.md: «pasarlas al vidrio con
 * estanterías de almacén»). Los de Obras, Mi celular, Catálogo y Mano de obra salen del lienzo con
 * diseno/generador/a_compose.py (PlanosLienzo.kt). Son decorativos: nunca datos de la obra.
 */

/**
 * Dibuja en coordenadas del lienzo con la MISMA escala en x y en y (el ancho manda: 390).
 * `alto` es el alto visible en esas coordenadas: lo que el lienzo pone abajo se ancla ahí.
 */
internal inline fun DrawScope.enLienzoPorAncho(bloque: DrawScope.(alto: Float) -> Unit) {
    val k = size.width / ANCHO_LIENZO
    val alto = size.height / k
    withTransform({ scale(k, k, pivot = Offset.Zero) }) { bloque(alto) }
}

/** Degradado, cuadrícula de 24 y de 120 y manchas difusas, hasta el alto visible. */
internal fun DrawScope.baseLienzo(alto: Float, c0: Color, c1: Color, c2: Color, manchas: List<Triple<Offset, Float, Color>>) {
    drawRect(
        Brush.linearGradient(0f to c0, 0.55f to c1, 1f to c2, start = Offset.Zero, end = Offset(ANCHO_LIENZO, alto)),
        size = androidx.compose.ui.geometry.Size(ANCHO_LIENZO, alto),
    )
    rejilla(alto, 24f, Color(0xFF94A3B8).copy(alpha = 0.4f), 0.5f)
    rejilla(alto, 120f, Color(0xFF64748B).copy(alpha = 0.3f), 0.8f)
    manchas.forEach { (centro, radio, color) ->
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), centro, radio), radio, centro)
    }
}

private fun DrawScope.rejilla(alto: Float, paso: Float, color: Color, grosor: Float) {
    var x = 0f
    while (x <= ANCHO_LIENZO) { drawLine(color, Offset(x, 0f), Offset(x, alto), grosor); x += paso }
    var y = 0f
    while (y <= alto) { drawLine(color, Offset(0f, y), Offset(ANCHO_LIENZO, y), grosor); y += paso }
}

private val Naranja = Color(0xFFFB923C)
private val Azul = Color(0xFF60A5FA)
private val Agua = Color(0xFF2DD4BF)

// ── Mis registros: tablilla con hojas y check ─────────────────────────────────────────────────
private val tablilla by lazy { svg("M262 470h100v140h-100zM292 462h40v16h-40zM278 500h68M278 524h68M278 548h48M278 572h60") }
private val checkTablilla by lazy { svg("M300 640l14 14 28-30") }
private val sobres by lazy { svg("M30 700h120v80H30zM30 700l60 44 60-44") }

fun DrawScope.planoRegistros(m: TextMeasurer) = enLienzoPorAncho { alto ->
    baseLienzo(alto, Color(0xFFECF0F6), Color(0xFFF2F1EE), Color(0xFFF5EAE0), listOf(
        Triple(Offset(320f, 200f), 190f, Naranja.copy(alpha = 0.45f)), Triple(Offset(50f, 560f), 210f, Azul.copy(alpha = 0.38f)),
        Triple(Offset(330f, alto - 60f), 150f, Agua.copy(alpha = 0.3f)),
    ))
    withTransform({ translate(0f, alto - 844f) }) {
        trazo(tablilla, 0.5f, 1.4f)
        trazo(checkTablilla, 0.6f, 2f, Paleta.Naranja)
        trazo(sobres, 0.45f, 1.3f)
        rotulo(m, "BITÁCORA", 278f, 630f, 0.6f, tam = 9f)
    }
}

// ── Entrada de compra: camión que descarga sobre pallets ──────────────────────────────────────
private val camion by lazy { svg("M200 640h110v70H200zM310 660h40l24 26v24h-64zM318 668h26l14 16h-40z") }
private val ruedas by lazy { svg("M220 710a14 14 0 1 0 28 0a14 14 0 1 0 -28 0M326 710a14 14 0 1 0 28 0a14 14 0 1 0 -28 0") }
private val palletEntrada by lazy { svg("M30 780h140M30 792h140M38 780v12M100 780v12M162 780v12M44 740h48v40H44zM100 750h50v30h-50z") }
private val flechaEntra by lazy { svg("M190 680h-60M144 666l-14 14 14 14") }

fun DrawScope.planoEntrada(m: TextMeasurer) = enLienzoPorAncho { alto ->
    baseLienzo(alto, Color(0xFFEAF0F5), Color(0xFFF2F1EE), Color(0xFFF6EBDF), listOf(
        Triple(Offset(330f, 160f), 200f, Azul.copy(alpha = 0.38f)), Triple(Offset(40f, 520f), 200f, Naranja.copy(alpha = 0.45f)),
        Triple(Offset(320f, alto - 40f), 160f, Naranja.copy(alpha = 0.4f)),
    ))
    withTransform({ translate(0f, alto - 844f) }) {
        trazo(camion, 0.5f, 1.4f)
        trazo(ruedas, 0.5f, 1.4f)
        trazo(palletEntrada, 0.45f, 1.3f)
        trazo(flechaEntra, 0.6f, 1.6f, Paleta.Naranja)
        rotulo(m, "GUÍA DE REMISIÓN", 210f, 630f, 0.6f, tam = 9f)
    }
}

// ── Ingreso sin compra: caja que entra al estante ─────────────────────────────────────────────
private val estanteIngreso by lazy { svg("M250 120v300M370 120v300M250 200h120M250 290h120M250 380h120M244 420h132") }
private val cajasIngreso by lazy { svg("M262 160h40v40h-40zM312 170h44v30h-44zM262 250h56v40h-56zM326 256h30v34h-30z") }
private val cajaSuelta by lazy { svg("M40 720l50-24 50 24v56l-50 24-50-24zM40 720l50 24 50-24M90 744v56") }
private val flechaSube by lazy { svg("M160 740c40-10 70-60 80-120M226 632l14-12 8 18") }

fun DrawScope.planoIngreso(m: TextMeasurer) = enLienzoPorAncho { alto ->
    baseLienzo(alto, Color(0xFFEDF1F6), Color(0xFFF3F1EE), Color(0xFFF6E9DF), listOf(
        Triple(Offset(320f, 240f), 200f, Naranja.copy(alpha = 0.45f)), Triple(Offset(50f, 700f), 200f, Agua.copy(alpha = 0.3f)),
        Triple(Offset(330f, alto - 30f), 150f, Azul.copy(alpha = 0.36f)),
    ))
    trazo(estanteIngreso, 0.5f, 1.8f, cuadrado = true)
    trazo(cajasIngreso, 0.45f, 1.2f)
    withTransform({ translate(0f, alto - 844f) }) {
        trazo(cajaSuelta, 0.5f, 1.3f)
        trazo(flechaSube, 0.55f, 1.4f, Paleta.Naranja, discontinuo = floatArrayOf(5f, 4f))
        rotulo(m, "SIN COMPRA", 40f, 690f, 0.6f, tam = 9f)
    }
}

// ── Traslado entre obras: dos almacenes y la ruta ─────────────────────────────────────────────
private val almacenA by lazy { svg("M30 640l50-30 50 30v80H30zM60 720v-40h40v40") }
private val almacenB by lazy { svg("M260 640l50-30 50 30v80H260zM290 720v-40h40v40") }
private val ruta by lazy { svg("M130 690C180 650 210 730 260 690") }
private val flechaRuta by lazy { svg("M248 682l12 8-14 6") }

fun DrawScope.planoTraslado(m: TextMeasurer) = enLienzoPorAncho { alto ->
    baseLienzo(alto, Color(0xFFE9EFF5), Color(0xFFF2F1EE), Color(0xFFF5EAE0), listOf(
        Triple(Offset(60f, 180f), 190f, Azul.copy(alpha = 0.38f)), Triple(Offset(340f, 480f), 200f, Naranja.copy(alpha = 0.45f)),
        Triple(Offset(80f, alto - 40f), 160f, Agua.copy(alpha = 0.3f)),
    ))
    withTransform({ translate(0f, alto - 844f) }) {
        trazo(almacenA, 0.5f, 1.4f)
        trazo(almacenB, 0.5f, 1.4f)
        trazo(ruta, 0.6f, 1.6f, Paleta.Naranja, discontinuo = floatArrayOf(6f, 5f))
        trazo(flechaRuta, 0.6f, 1.6f, Paleta.Naranja)
        rotulo(m, "OBRA A", 52f, 740f, 0.6f, tam = 9f)
        rotulo(m, "OBRA B", 284f, 740f, 0.6f, tam = 9f)
    }
}

// ── Conteo físico: estante con marcas de conteo ───────────────────────────────────────────────
private val estanteConteo by lazy { svg("M30 560v240M150 560v240M30 620h120M30 700h120M30 780h120M24 800h132") }
private val cajasConteo by lazy { svg("M42 584h34v36H42zM84 590h52v30H84zM42 664h48v36H42zM98 670h38v30H98zM42 744h30v36H42zM80 750h56v30H80z") }
private val marcas by lazy { svg("M250 600v40M262 600v40M274 600v40M286 600v40M244 630l50-20M250 680v40M262 680v40") }

fun DrawScope.planoConteo(m: TextMeasurer) = enLienzoPorAncho { alto ->
    baseLienzo(alto, Color(0xFFEDF0F6), Color(0xFFF2F0EE), Color(0xFFF5EADF), listOf(
        Triple(Offset(330f, 150f), 200f, Agua.copy(alpha = 0.3f)), Triple(Offset(40f, 480f), 200f, Naranja.copy(alpha = 0.45f)),
        Triple(Offset(330f, alto - 40f), 160f, Azul.copy(alpha = 0.36f)),
    ))
    withTransform({ translate(0f, alto - 844f) }) {
        trazo(estanteConteo, 0.5f, 1.8f, cuadrado = true)
        trazo(cajasConteo, 0.45f, 1.2f)
        trazo(marcas, 0.6f, 1.6f, Paleta.Naranja)
        rotulo(m, "CONTEO", 250f, 760f, 0.6f, tam = 9f)
    }
}

// ── Cierre de obra: estante vacío y candado ───────────────────────────────────────────────────
private val estanteCierre by lazy { svg("M240 560v240M370 560v240M240 640h130M240 720h130M234 800h142") }
private val candado by lazy { svg("M60 680h70v60H60zM74 680v-20a21 21 0 0 1 42 0v20M95 702v16") }

fun DrawScope.planoCierre(m: TextMeasurer) = enLienzoPorAncho { alto ->
    baseLienzo(alto, Color(0xFFEBEFF5), Color(0xFFF2F0EE), Color(0xFFF5E9E0), listOf(
        Triple(Offset(320f, 180f), 200f, Naranja.copy(alpha = 0.4f)), Triple(Offset(50f, 560f), 200f, Azul.copy(alpha = 0.36f)),
        Triple(Offset(330f, alto - 40f), 150f, Naranja.copy(alpha = 0.4f)),
    ))
    withTransform({ translate(0f, alto - 844f) }) {
        trazo(estanteCierre, 0.45f, 1.8f, cuadrado = true)
        trazo(candado, 0.55f, 1.6f, Paleta.Naranja)
        rotate(-90f, Offset(384f, 680f)) { rotulo(m, "CIERRE", 384f, 680f, 0.6f, tam = 9f) }
    }
}

// ── Stock: pasillo de estanterías en isométrico ───────────────────────────────────────────────
private val pasillo by lazy { svg("M30 620l90-50 90 50v170l-90 50-90-50zM30 620l90 50 90-50M120 670v170M30 680l90 50 90-50M30 740l90 50 90-50") }
private val estanteStock by lazy { svg("M260 120v280M370 120v280M260 190h110M260 260h110M260 330h110M254 400h122") }
private val cajasStock by lazy { svg("M270 150h30v40h-30zM306 160h54v30h-54zM270 226h44v34h-44zM320 230h40v30h-40zM272 300h34v30h-34z") }

fun DrawScope.planoStock(m: TextMeasurer) = enLienzoPorAncho { alto ->
    baseLienzo(alto, Color(0xFFE8EEF6), Color(0xFFF3F1EE), Color(0xFFF6E9DF), listOf(
        Triple(Offset(330f, 260f), 200f, Naranja.copy(alpha = 0.45f)), Triple(Offset(40f, 640f), 210f, Azul.copy(alpha = 0.38f)),
        Triple(Offset(300f, alto - 40f), 150f, Agua.copy(alpha = 0.3f)),
    ))
    trazo(estanteStock, 0.5f, 1.8f, cuadrado = true)
    trazo(cajasStock, 0.45f, 1.2f)
    withTransform({ translate(0f, alto - 844f) }) {
        trazo(pasillo, 0.45f, 1.3f)
        rotulo(m, "PASILLO 1", 46f, 600f, 0.6f, tam = 9f)
    }
}
