package pe.sevens.sigro.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.recursos.Res
import pe.sevens.sigro.recursos.sevens_logo_blanco
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.CapaPlanoQueSeDibuja
import pe.sevens.sigro.ui.componentes.FondoPlano
import pe.sevens.sigro.ui.componentes.LocalHaze
import pe.sevens.sigro.ui.componentes.planoEntrar
import pe.sevens.sigro.ui.componentes.svg
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta
import pe.sevens.sigro.ui.tema.Sora

/* HU-12 · Iniciar sesión. */

data class EstadoEntrar(
    val servidor: String = "",
    val usuario: String = "",
    val clave: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
)

class EntrarViewModel(private val sesiones: RepositorioSesion) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoEntrar())
    val estado = _estado.asStateFlow()

    init {
        viewModelScope.launch { _estado.update { it.copy(servidor = sesiones.servidorGuardado()) } }
    }

    fun servidor(v: String) = _estado.update { it.copy(servidor = v, error = null) }
    fun usuario(v: String) = _estado.update { it.copy(usuario = v, error = null) }
    fun clave(v: String) = _estado.update { it.copy(clave = v, error = null) }

    fun entrar() {
        val e = _estado.value
        if (e.usuario.isBlank() || e.clave.isBlank()) {
            _estado.update { it.copy(error = "Escribe tu usuario y tu clave.") }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                sesiones.iniciarSesion(e.servidor, e.usuario, e.clave)
                // La raíz cambia sola de pantalla al ver la sesión guardada.
                _estado.update { it.copy(cargando = false, clave = "") }
            } catch (x: ErrorSigro) {
                _estado.update { it.copy(cargando = false, error = x.message) }
            }
        }
    }
}

@Composable
fun PantallaEntrar(vm: EntrarViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    var verClave by remember { mutableStateOf(false) }
    var verServidor by remember { mutableStateOf(false) }
    // La capa del plano aparece al pulsar «Entrar» y se queda si entró: la raíz la desvanece al cambiar
    // de pantalla. Si el servidor rechaza, se va y deja el error a la vista.
    var conCapa by remember { mutableStateOf(false) }
    LaunchedEffect(e.cargando, e.error) {
        if (e.cargando) conCapa = true else if (e.error != null) conCapa = false
    }

    FondoPlano(plano = { planoEntrar(it) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
            Box(Modifier.fillMaxWidth()) {
                Heroe(Modifier.fillMaxWidth().height(470.dp))
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 262.dp, start = 16.dp, end = 16.dp)
                        .widthIn(max = 480.dp).fillMaxWidth()
                        .vidrio(RoundedCornerShape(28.dp), fuerte = true, sombra = 20.dp)
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Ingresa para registrar las entradas y salidas de material de tu obra",
                        fontSize = 16.sp, lineHeight = 22.sp, color = Color(0xFF334155),
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    CampoVidrio(
                        e.usuario, vm::usuario, "Usuario", marcador = "Tu usuario",
                        finalIcono = {
                            Icon(Icons.Filled.Engineering, contentDescription = null, tint = Paleta.Ambar, modifier = Modifier.padding(end = 10.dp).size(22.dp))
                        },
                    )
                    CampoVidrio(
                        e.clave, vm::clave, "Clave", marcador = "Tu clave",
                        teclado = KeyboardType.Password,
                        transformacion = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                        finalIcono = {
                            IconButton(onClick = { verClave = !verClave }) {
                                Icon(
                                    if (verClave) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (verClave) "Ocultar clave" else "Mostrar clave",
                                    tint = Color(0xFF64748B),
                                )
                            }
                        },
                    )
                    AnimatedVisibility(e.error != null) {
                        Row(
                            Modifier.fillMaxWidth().background(Paleta.RechazadoFondo, RoundedCornerShape(12.dp)).padding(12.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = "Error", tint = Paleta.ErrorTexto, modifier = Modifier.size(20.dp))
                            Text(e.error.orEmpty(), fontSize = 15.sp, color = Paleta.ErrorTexto)
                        }
                    }
                    BotonNegro("Entrar", vm::entrar, cargando = e.cargando)
                    Text(
                        buildAnnotatedString {
                            append("¿No tienes usuario?\n")
                            withStyle(SpanStyle(color = Paleta.Naranja, fontWeight = FontWeight.SemiBold)) { append("Pídelo al administrador de la obra") }
                        },
                        fontSize = 14.sp, lineHeight = 21.sp, color = Paleta.Texto3,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    // El servidor se cambia pocas veces (pruebas → producción): queda escondido pero a mano.
                    AnimatedVisibility(verServidor) {
                        CampoVidrio(
                            e.servidor, vm::servidor, "Dirección del servidor",
                            teclado = KeyboardType.Uri,
                            ayuda = "Te la da el administrador del sistema",
                        )
                    }
                }
            }
            TextButton(
                onClick = { verServidor = !verServidor },
                modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding()
                    .padding(vertical = 12.dp).heightIn(min = 44.dp).vidrio(RoundedCornerShape(22.dp), sombra = 0.dp),
            ) {
                Icon(Icons.Filled.Dns, contentDescription = null, tint = Paleta.Texto3, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Servidor: ${e.servidor.removePrefix("http://").removePrefix("https://")} · ${if (verServidor) "listo" else "cambiar"}",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Paleta.Texto3,
                )
            }
        }

        AnimatedVisibility(conCapa, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
            CapaPlanoQueSeDibuja()
        }
    }
}

private val edificioIzq = svg("M40 250V90h150v160M40 130h150M40 170h150M40 210h150M77 90v160M115 90v160M152 90v160")
private val edificioDer = svg("M215 250V60h120v190M215 100h120M215 140h120M215 180h120M215 220h120M255 60v190M295 60v190")
private val grua = svg("M335 60l30-30M365 30v220M345 30h40")
private val cuerpo = svg("M196 470c0-100 24-170 54-170s54 70 54 170z")
private val casco = svg("M212 248a38 32 0 0 1 76 0z")
private val chaleco = svg("M222 330l28 22 28-22v140h-56z")
private val cintas = svg("M222 370h56M222 395h56")

/**
 * Franja superior: ilustración de obra (luego, una foto), logo en blanco y «ALMACÉN DE OBRA».
 * Se funde con el plano por abajo y es fuente del vidrio: la tarjeta la desenfoca donde se monta.
 */
@Composable
private fun Heroe(modifier: Modifier) {
    val haze = LocalHaze.current
    Box(
        modifier
            .then(if (haze != null) Modifier.hazeSource(haze, zIndex = 1f) else Modifier)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0.74f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            },
    ) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Ilustración: trabajador con casco frente a un edificio en construcción" }) {
            val sx = size.width / 390f
            val sy = size.height / 470f
            drawRect(Color(0xFF334155))
            withTransform({ scale(sx, sy, Offset.Zero) }) {
                drawRect(Paleta.Noche, Offset(0f, 250f), Size(390f, 220f))
                val gris = Color(0xFF64748B)
                listOf(edificioIzq, edificioDer, grua).forEach { drawPath(it, gris, style = Stroke(3f)) }
                drawCircle(Paleta.Tinta, 34f, Offset(250f, 250f))
                drawPath(cuerpo, Paleta.Tinta)
                drawPath(casco, Color(0xFFF59E0B))
                drawRoundRect(Color(0xFFF59E0B), Offset(206f, 244f), Size(88f, 9f), CornerRadius(4f))
                drawPath(chaleco, Color(0xFFEA580C))
                drawPath(cintas, Color(0xFFFDE68A), style = Stroke(6f))
            }
            drawRect(Brush.verticalGradient(0f to Color(0x4D0F172A), 0.3f to Color(0x330F172A), 0.62f to Color(0x9E0F172A), 1f to Color(0xCC0F172A)))
            val brillo = Offset(320f * sx, 270f * sy)
            drawCircle(Brush.radialGradient(0f to Color(0x59FB923C), 0.7f to Color(0x00FB923C), center = brillo, radius = 150f * sx), 150f * sx, brillo)
        }
        Image(
            painterResource(Res.drawable.sevens_logo_blanco), contentDescription = "SEVEN'S Ingenieros SAC",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 46.dp).height(128.dp).widthIn(max = 300.dp),
        )
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 194.dp).semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val filete = Color(0xD9FDBA74)
            Box(Modifier.width(22.dp).height(1.5.dp).background(filete))
            Text(
                "ALMACÉN DE OBRA",
                style = TextStyle(
                    fontFamily = Sora(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.24.em,
                    color = Color.White, shadow = Shadow(Color(0x730F172A), Offset(0f, 3f), 12f),
                ),
                modifier = Modifier.padding(start = 4.dp), // compensa el tracking del final: queda centrado
            )
            Box(Modifier.width(22.dp).height(1.5.dp).background(filete))
        }
    }
}
