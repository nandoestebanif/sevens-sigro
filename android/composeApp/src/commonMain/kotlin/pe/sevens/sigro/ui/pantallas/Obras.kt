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
import pe.sevens.sigro.dominio.EstadoDispositivo
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
import pe.sevens.sigro.ui.componentes.Ilustracion
import pe.sevens.sigro.ui.componentes.DibujoObra
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import pe.sevens.sigro.plataforma.Plataforma

/*
 * 1.2 · Elegir obra (rediseño v3, 25/11). Solo las obras donde la persona está asignada (regla 9): portada
 * negra con quién entró y cuánto falta enviar, y cada obra como ticket de color con su rol y el estado de ESTE
 * celular ahí (la autorización es por celular y por obra: regla 5, HU-15). El buscador sale con más de tres.
 */

/** Cómo se ve el celular en una obra. */
enum class CelularEnObra(val texto: String) {
    AUTORIZADO("Este celular está autorizado aquí"),
    PENDIENTE("Celular por autorizar en esta obra"),
    REVOCADO("Celular revocado en esta obra"),
    SIN_SOLICITAR("Este celular aún no pide autorización aquí"),
    SIN_CONSULTAR("Estado del celular: se consulta con señal"),
    NO_VISIBLE("Estado del celular: míralo en «Mi celular»"),
}

fun celularEnObra(estado: String?, consultado: Boolean, visible: Boolean = true): CelularEnObra = when {
    !consultado -> CelularEnObra.SIN_CONSULTAR
    estado == "autorizado" -> CelularEnObra.AUTORIZADO
    estado == "pendiente" || estado == EstadoDispositivo.YA_SOLICITADO -> CelularEnObra.PENDIENTE
    !visible && estado == null -> CelularEnObra.NO_VISIBLE
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
                        .fold({ celularEnObra(it.estado, consultado = true, visible = it.visible) }, { CelularEnObra.SIN_CONSULTAR })
                }
            }.awaitAll()
            _celular.value = r.toMap()
        }
    }
}

/** Saludo según la hora del celular (0–23). */
fun saludoPorHora(hora: Int): String = when (hora) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }

@Composable
fun PantallaObras(sesion: Sesion, alElegir: (ObraAsignada) -> Unit, alSalir: () -> Unit, vm: ObrasViewModel = koinViewModel()) {
    val celular by vm.celular.collectAsState()
    val q by vm.q.collectAsState()
    val porEnviar by vm.porEnviar.collectAsState()
    LaunchedEffect(sesion.obras) { vm.consultar(sesion.obras) }
    val obras = remember(sesion.obras, q) { filtrarObras(sesion.obras, q) }
    var eligiendo by remember { mutableStateOf(false) }

    FondoPlano(plano = { planoObras(it) }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PortadaObras(sesion, porEnviar, alSalir)
                if (conBuscador(sesion.obras.size)) BuscadorObra(q, vm::buscar)
                obras.forEach { o ->
                    val i = sesion.obras.indexOfFirst { it.id == o.id }
                    TicketObra(o, i, celular[o.id] ?: CelularEnObra.SIN_CONSULTAR, habilitado = !eligiendo) { eligiendo = true; alElegir(o) }
                }
                Text(
                    if (obras.isEmpty()) "No tienes una obra con ese nombre.\nEl administrador te asigna desde la web."
                    else "¿Falta una obra? El administrador te asigna desde la web.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = Paleta.Texto3, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

/** Portada negra: logo, salir, quién entró (iniciales, nombre, usuario), la pregunta y las dos cifras. */
@Composable
private fun PortadaObras(sesion: Sesion, porEnviar: Int, alSalir: () -> Unit) {
    val hora = Plataforma.formatearHora(Plataforma.ahoraMs()).substringAfter(' ').substringBefore(':').toIntOrNull() ?: 12
    val amarillo = Paleta.TicketAmarillo
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Paleta.Noche)) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = (-30).dp).size(250.dp)
                .background(Brush.radialGradient(listOf(amarillo.copy(alpha = 0.33f), Color.Transparent)), CircleShape),
        )
        DibujoObra(Ilustracion.GRUA, Modifier.align(Alignment.TopEnd).padding(top = 70.dp), ancho = 150.dp)
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(Res.drawable.sevens_logo_blanco), contentDescription = "SEVEN'S Ingenieros SAC", modifier = Modifier.height(40.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = alSalir, modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.10f), CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión", tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
            Row(
                Modifier.padding(top = 18.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(52.dp).background(amarillo, CircleShape), contentAlignment = Alignment.Center) {
                    Text(iniciales(sesion.nombre), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Paleta.SobreTicket)
                }
                Column(Modifier.padding(end = 110.dp)) {
                    Text("${saludoPorHora(hora)},", fontSize = 13.sp, color = Color(0xFFB4B5B5))
                    Text(sesion.nombre, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${sesion.usuario}", fontSize = 12.sp, color = Color(0xFF8F9090), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                buildAnnotatedString {
                    append("¿En qué ")
                    withStyle(SpanStyle(color = amarillo)) { append("obra") }
                    append(" trabajas hoy?")
                },
                fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em, color = Color.White,
                modifier = Modifier.padding(top = 16.dp, end = 110.dp).semantics { heading() },
            )
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Cifra("${sesion.obras.size}", if (sesion.obras.size == 1) "obra asignada" else "obras asignadas", Color.White)
                Cifra("$porEnviar", "por enviar", if (porEnviar > 0) Color(0xFFF5C451) else Color.White)
            }
        }
    }
}

@Composable
private fun Cifra(valor: String, etiqueta: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF2B2C2C)).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(valor, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
        Text(etiqueta, fontSize = 12.sp, color = Color(0xFFB4B5B5))
    }
}

@Composable
internal fun BuscadorObra(q: String, alCambiar: (String) -> Unit, ayuda: String = "Buscar obra por nombre") {
    var enfocado by remember { mutableStateOf(false) }
    BasicTextField(
        value = q, onValueChange = alCambiar, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Paleta.Tinta),
        cursorBrush = SolidColor(Paleta.Naranja),
        modifier = Modifier.fillMaxWidth().onFocusChanged { enfocado = it.isFocused }.semantics { contentDescription = ayuda },
        decorationBox = { campo ->
            Row(
                Modifier.fillMaxWidth().height(52.dp).vidrio(RoundedCornerShape(26.dp), fuerte = true, sombra = 2.dp)
                    .border(1.5.dp, if (enfocado) Paleta.Naranja else Color.Transparent, RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Paleta.Texto3, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text(ayuda, style = MaterialTheme.typography.bodyLarge, color = Paleta.Texto3)
                    campo()
                }
            }
        },
    )
}
