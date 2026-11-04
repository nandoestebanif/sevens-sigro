package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.CatalogoTexto
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.RepositorioDispositivo
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.recursos.Res
import pe.sevens.sigro.recursos.sevens_logo_blanco
import pe.sevens.sigro.ui.componentes.FondoPlano
import pe.sevens.sigro.ui.componentes.planoObras
import pe.sevens.sigro.ui.componentes.svg
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 1.2 · Elegir obra (lienzo Obras.dc.html). Solo las obras donde la persona está asignada (regla 9),
 * su perfil antes de entrar, un buscador al centro y, en cada obra, el estado de ESTE celular:
 * la autorización es por celular y por obra (regla 5, HU-15).
 */

/** Cómo se ve el celular en una obra. */
enum class CelularEnObra(val texto: String) {
    AUTORIZADO("Este celular está autorizado aquí"),
    PENDIENTE("Celular por autorizar en esta obra"),
    REVOCADO("Celular revocado en esta obra"),
    SIN_SOLICITAR("Este celular aún no pide autorización aquí"),
    SIN_CONSULTAR("Estado del celular: se consulta con señal"),
}

fun celularEnObra(estado: String?, consultado: Boolean): CelularEnObra = when {
    !consultado -> CelularEnObra.SIN_CONSULTAR
    estado == "autorizado" -> CelularEnObra.AUTORIZADO
    estado == "pendiente" -> CelularEnObra.PENDIENTE
    estado == "revocado" -> CelularEnObra.REVOCADO
    else -> CelularEnObra.SIN_SOLICITAR
}

/** Buscador del centro: sin tildes ni mayúsculas, como el catálogo. */
fun filtrarObras(obras: List<ObraAsignada>, q: String): List<ObraAsignada> =
    if (q.isBlank()) obras else obras.filter { CatalogoTexto.normalizar(it.nombre).contains(CatalogoTexto.normalizar(q)) }

class ObrasViewModel(
    private val dispositivo: RepositorioDispositivo,
    salidas: RepositorioSalidas,
    registros: RepositorioRegistros,
) : ViewModel() {
    private val _celular = MutableStateFlow<Map<Int, CelularEnObra>>(emptyMap())
    val celular = _celular.asStateFlow()
    private val _q = MutableStateFlow("")
    val q = _q.asStateFlow()

    /** Lo que espera envío en el celular: salidas y la cola general (asistencia). */
    val porEnviar = combine(salidas.pendientes, registros.pendientes) { a, b -> a + b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun buscar(texto: String) = _q.update { texto }

    /** Estado del celular en cada obra, en paralelo. Sin señal o sin permiso de lista: «se consulta con señal». */
    fun consultar(obras: List<ObraAsignada>) {
        viewModelScope.launch {
            val r = obras.map { o ->
                async {
                    o.id to runCatching { dispositivo.estado(o.id) }
                        .fold({ celularEnObra(it.estado, consultado = true) }, { CelularEnObra.SIN_CONSULTAR })
                }
            }.awaitAll()
            _celular.value = r.toMap()
        }
    }
}

@Composable
fun PantallaObras(sesion: Sesion, alElegir: (ObraAsignada) -> Unit, alSalir: () -> Unit, vm: ObrasViewModel = koinViewModel()) {
    val celular by vm.celular.collectAsState()
    val q by vm.q.collectAsState()
    val porEnviar by vm.porEnviar.collectAsState()
    LaunchedEffect(sesion.obras) { vm.consultar(sesion.obras) }
    val obras = remember(sesion.obras, q) { filtrarObras(sesion.obras, q) }

    FondoPlano(plano = { planoObras(it) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth()) {
                FranjaObra(Modifier.fillMaxWidth().height(340.dp))
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Image(painterResource(Res.drawable.sevens_logo_blanco), contentDescription = "SEVEN'S Ingenieros SAC", modifier = Modifier.height(64.dp))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = alSalir, modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.16f), CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Perfil(sesion, porEnviar, Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 100.dp))
            }
            Column(
                Modifier.padding(horizontal = 16.dp).padding(top = 20.dp).widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "¿En qué obra trabajas hoy?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em,
                    color = Paleta.Tinta, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                BuscadorObra(q, vm::buscar)
                obras.forEachIndexed { i, o -> TarjetaObra(o, celular[o.id] ?: CelularEnObra.SIN_CONSULTAR, primera = i == 0) { alElegir(o) } }
                Text(
                    if (obras.isEmpty()) "No tienes una obra con ese nombre.\nEl administrador te asigna desde la web."
                    else "¿Falta una obra? El administrador te asigna desde la web.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.Texto3, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

private val edificioIzq by lazy { svg("M40 250V90h150v160M40 130h150M40 170h150M40 210h150M77 90v160M115 90v160M152 90v160") }
private val edificioDer by lazy { svg("M215 250V60h120v190M215 100h120M215 140h120M215 180h120M215 220h120M255 60v190M295 60v190") }
private val grua by lazy { svg("M335 60l30-30M365 30v220M345 30h40") }

/** Franja superior oscura con la obra (hoy ilustración; luego una foto), como en Entrar. */
@Composable
private fun FranjaObra(modifier: Modifier) {
    Canvas(modifier.clipToBounds().semantics { contentDescription = "Ilustración: edificio en construcción" }) {
        drawRect(Color(0xFF334155))
        val k = size.width / 390f
        withTransform({ scale(k, k, Offset.Zero); translate(0f, 40f) }) {
            drawRect(Paleta.Noche, Offset(0f, 250f), Size(390f, 200f))
            listOf(edificioIzq, edificioDer, grua).forEach { drawPath(it, Color(0xFF64748B), style = Stroke(3f)) }
        }
        drawRect(Brush.verticalGradient(0f to Color(0x59111827), 0.6f to Color(0x8C111827), 1f to Color(0xD9111827)))
    }
}

@Composable
private fun Perfil(sesion: Sesion, porEnviar: Int, modifier: Modifier) {
    val rol = sesion.obras.firstOrNull()?.rolLegible
    Column(
        modifier.fillMaxWidth().vidrio(RoundedCornerShape(28.dp), fuerte = true, sombra = 16.dp).padding(20.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).background(Paleta.NaranjaClaro, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Engineering, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(sesion.nombre, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${sesion.usuario}" + (rol?.let { " · $it" } ?: ""), fontSize = 14.sp, color = Paleta.Texto3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dato("${sesion.obras.size}", if (sesion.obras.size == 1) "obra asignada" else "obras asignadas", Paleta.Tinta)
            Dato("$porEnviar", if (porEnviar == 1) "registro por enviar" else "registros por enviar", if (porEnviar > 0) Paleta.Ambar else Paleta.Tinta)
        }
    }
}

@Composable
private fun RowScope.Dato(valor: String, etiqueta: String, color: Color) {
    Column(
        Modifier.weight(1f).background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(valor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(etiqueta, fontSize = 12.sp, color = Paleta.Texto3)
    }
}

@Composable
private fun BuscadorObra(q: String, alCambiar: (String) -> Unit) {
    var enfocado by remember { mutableStateOf(false) }
    BasicTextField(
        value = q, onValueChange = alCambiar, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Paleta.Tinta),
        cursorBrush = SolidColor(Paleta.Naranja),
        modifier = Modifier.fillMaxWidth().onFocusChanged { enfocado = it.isFocused }.semantics { contentDescription = "Buscar obra" },
        decorationBox = { campo ->
            Row(
                Modifier.fillMaxWidth().height(56.dp).vidrio(RoundedCornerShape(28.dp), fuerte = true, sombra = 4.dp)
                    .border(1.5.dp, if (enfocado) Paleta.Naranja else Color.Transparent, RoundedCornerShape(28.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Paleta.Texto3, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Buscar obra por nombre", style = MaterialTheme.typography.bodyLarge, color = Paleta.Texto3)
                    campo()
                }
            }
        },
    )
}

@Composable
private fun TarjetaObra(o: ObraAsignada, celular: CelularEnObra, primera: Boolean, alPulsar: () -> Unit) {
    val colorCel = when (celular) {
        CelularEnObra.AUTORIZADO -> Paleta.EnviadoTexto
        CelularEnObra.PENDIENTE -> Paleta.PendienteTexto
        CelularEnObra.REVOCADO -> Paleta.RechazadoTexto
        else -> Paleta.Texto3
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).vidrio(RoundedCornerShape(22.dp))
            .clickable(onClickLabel = "Entrar a ${o.nombre}", onClick = alPulsar).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(if (primera) Paleta.NaranjaClaro else Color(0xFFF3F4F6), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Apartment, contentDescription = null, tint = if (primera) Paleta.Naranja else Paleta.Texto3, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(o.nombre, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
            Text(o.rolLegible, fontSize = 13.sp, color = Paleta.Texto3)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = colorCel, modifier = Modifier.size(13.dp))
                Text(celular.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colorCel)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Paleta.Gris, modifier = Modifier.size(20.dp))
    }
}
