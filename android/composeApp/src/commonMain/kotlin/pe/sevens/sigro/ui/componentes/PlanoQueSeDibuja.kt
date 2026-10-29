package pe.sevens.sigro.ui.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import pe.sevens.sigro.plataforma.reducirMovimiento
import pe.sevens.sigro.recursos.Res
import pe.sevens.sigro.recursos.sevens_logo
import pe.sevens.sigro.ui.tema.Paleta

private val CurvaTrazo = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
private const val DURACION_TRAZO = 900
private const val APARECE_LOGO_MS = 2300L

/**
 * «El plano se dibuja»: mientras el servidor responde al iniciar sesión.
 * Los 8 grupos de trazos se dibujan en cascada (~2,3 s); luego aparece el logo con «Preparando tu obra…»
 * y se queda lo que tarde el servidor. La salida (desvanecer y crecer 6 %) la hace la raíz al cambiar de pantalla.
 * Con «reducir movimiento» se muestra ya dibujado.
 */
@Composable
fun CapaPlanoQueSeDibuja(modifier: Modifier = Modifier) {
    val reducir = reducirMovimiento()
    val grupos = remember {
        gruposPlanoQueSeDibuja.map { d -> subtrazos(d).map { p -> PathMeasure().apply { setPath(p, false) } } }
    }
    val progreso = remember { List(grupos.size) { Animatable(if (reducir) 1f else 0f) } }
    var conLogo by remember { mutableStateOf(reducir) }
    LaunchedEffect(Unit) {
        if (reducir) return@LaunchedEffect
        progreso.forEachIndexed { i, a ->
            launch { delay(retrasosPlano[i]); a.animateTo(1f, tween(DURACION_TRAZO, easing = CurvaTrazo)) }
        }
        delay(APARECE_LOGO_MS)
        conLogo = true
    }
    val cota by animateFloatAsState(if (conLogo) 1f else 0f, tween(if (reducir) 0 else 300))
    val medidor = rememberTextMeasurer()

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            fondoPlanoQueSeDibuja()
            enLienzo {
                val lapiz = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                grupos.forEachIndexed { i, medidas ->
                    val p = progreso[i].value
                    if (p <= 0f) return@forEachIndexed
                    medidas.forEach { m ->
                        val tramo = Path()
                        m.getSegment(0f, m.length * p, tramo, true)
                        drawPath(tramo, Paleta.Texto2, style = lapiz)
                    }
                }
                if (cota > 0f) rotulo(medidor, "27.00 m", 178f, 700f, cota, Paleta.Naranja, tam = 12f)
            }
        }
        AnimatedVisibility(
            conLogo,
            enter = if (reducir) fadeIn(tween(0)) else
                fadeIn(tween(400)) + scaleIn(tween(500, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.1f)), initialScale = 0.94f) +
                    slideInVertically(tween(500)) { 12 },
            modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(painterResource(Res.drawable.sevens_logo), contentDescription = "SEVEN'S Ingenieros SAC", modifier = Modifier.width(210.dp))
                Text("Preparando tu obra…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Pizarra)
            }
        }
    }
}
