package pe.sevens.sigro.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.NuevaSalida
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.ValidarSalida
import pe.sevens.sigro.plataforma.reducirMovimiento
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.ChipEstado
import pe.sevens.sigro.ui.componentes.FondoPlano
import pe.sevens.sigro.ui.componentes.planoSalida
import pe.sevens.sigro.ui.componentes.svg
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta

/* HU-40 · Registrar salida de material + HU-37 · sin conexión. */

data class EstadoSalida(
    val busqueda: String = "",
    val resultados: List<Insumo> = emptyList(),
    val insumo: Insumo? = null,
    val cantidad: String = "",
    val motivo: String = "",
    val quienRetira: String = "",
    val frente: String = "",
    val errores: Map<String, String> = emptyMap(),
    val guardando: Boolean = false,
    val guardada: Boolean = false,
)

class SalidaViewModel(
    private val catalogo: RepositorioCatalogo,
    private val salidas: RepositorioSalidas,
    private val programador: ProgramadorSincronizacion,
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoSalida())
    val estado = _e.asStateFlow()
    private var busquedaJob: Job? = null

    fun buscar(texto: String) {
        _e.update { it.copy(busqueda = texto) }
        busquedaJob?.cancel()
        if (texto.trim().length < 2) { _e.update { it.copy(resultados = emptyList()) }; return }
        busquedaJob = viewModelScope.launch {
            delay(200) // espera a que termine de escribir
            val r = catalogo.buscar(texto)
            _e.update { it.copy(resultados = r) }
        }
    }

    fun elegir(i: Insumo) = _e.update { it.copy(insumo = i, busqueda = "", resultados = emptyList(), errores = it.errores - "insumo") }
    fun quitarInsumo() = _e.update { it.copy(insumo = null) }
    fun cantidad(v: String) = _e.update { it.copy(cantidad = v, errores = it.errores - "cantidad") }
    fun motivo(v: String) = _e.update { it.copy(motivo = v, errores = it.errores - "motivo") }
    fun quienRetira(v: String) = _e.update { it.copy(quienRetira = v, errores = it.errores - "quienRetira") }
    fun frente(v: String) = _e.update { it.copy(frente = v) }

    fun guardar(obraId: Int) {
        val s = _e.value
        val errores = ValidarSalida.errores(s.insumo, s.cantidad, s.motivo, s.quienRetira)
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            salidas.registrar(NuevaSalida(obraId, s.insumo!!, s.cantidad, s.motivo, s.quienRetira, s.frente))
            programador.programar() // si hay señal se envía ya; si no, cuando vuelva
            _e.update { it.copy(guardando = false, guardada = true) }
        }
    }

    /** Otra salida: se conserva quién retira y el frente, que suelen repetirse en la misma jornada. */
    fun otra() = _e.update { EstadoSalida(quienRetira = it.quienRetira, frente = it.frente) }
}

/* Lienzo Salida.dc.html y Guardada.dc.html, pasados al vidrio sobre el fondo de estanterías. */

private val NaranjaTexto = Color(0xFF7C2D12)

@Composable
fun PantallaSalida(obraId: Int, alVolver: () -> Unit, vm: SalidaViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    FondoPlano(plano = { planoSalida(it) }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding()
                .navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Encabezado(if (e.guardada) null else "Se guarda en el celular aunque no haya señal", alVolver)
            if (e.guardada) {
                Confirmacion(e, alOtra = vm::otra, alVolver = alVolver)
                return@Column
            }
            Column(
                Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Paso("1", "¿Qué sale?")
                    if (e.insumo == null) {
                        CampoVidrio(
                            e.busqueda, vm::buscar, "Buscar insumo por nombre o código", marcador = "Ej.: cemento",
                            error = e.errores["insumo"],
                            finalIcono = { Icon(Icons.Filled.Search, contentDescription = null, tint = Paleta.Texto3, modifier = Modifier.padding(end = 10.dp)) },
                        )
                        if (e.resultados.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp))) {
                                e.resultados.take(8).forEachIndexed { i, ins ->
                                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2E8F0)))
                                    Column(
                                        Modifier.fillMaxWidth().clickable { vm.elegir(ins) }.heightIn(min = 60.dp)
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(ins.descripcion, fontSize = 16.sp, color = Paleta.Tinta)
                                        Text("${ins.codigo} · ${ins.unidad}", fontSize = 14.sp, color = Paleta.Texto3)
                                    }
                                }
                            }
                        } else if (e.busqueda.trim().length >= 2) {
                            Text(
                                "Sin coincidencias. Prueba con otra palabra o actualiza el catálogo desde «Más» en el inicio.",
                                fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.Texto2,
                            )
                        }
                    } else {
                        InsumoElegido(e.insumo!!, vm::quitarInsumo)
                    }

                    Paso("2", "¿Cuánto?")
                    CampoVidrio(
                        e.cantidad, vm::cantidad, "Cantidad",
                        teclado = KeyboardType.Decimal, sufijo = e.insumo?.unidad ?: "—", error = e.errores["cantidad"],
                        ayuda = "El servidor verifica que haya stock en el almacén",
                    )

                    Paso("3", "¿Para qué y quién lo lleva?")
                    CampoVidrio(e.motivo, vm::motivo, "Motivo / uso", marcador = "Ej.: vaciado de losa del 3.er piso", error = e.errores["motivo"])
                    CampoVidrio(e.quienRetira, vm::quienRetira, "Quién retira", error = e.errores["quienRetira"])
                    CampoVidrio(e.frente, vm::frente, "Frente o cuadrilla (opcional)", marcador = "Ej.: cuadrilla de concreto")
                }

                BotonNegro("Guardar salida", { vm.guardar(obraId) }, icono = Icons.Filled.Save, cargando = e.guardando, modifier = Modifier.padding(top = 4.dp))
                AvisoNaranja("La salida queda «por enviar» y se envía sola cuando hay señal. El costo lo calcula el servidor al recibirla.")
            }
        }
    }
}

@Composable
private fun Encabezado(subtitulo: String?, alVolver: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = alVolver, modifier = Modifier.size(48.dp).vidrio(CircleShape, sombra = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Paleta.Tinta)
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Registrar salida", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta, modifier = Modifier.semantics { heading() })
            if (subtitulo != null) Text(subtitulo, fontSize = 14.sp, color = Paleta.Texto3)
        }
    }
}

@Composable
private fun Paso(n: String, titulo: String) {
    Row(Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Text(n, color = Paleta.Naranja, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Paleta.Tinta)
    }
}

@Composable
private fun InsumoElegido(i: Insumo, alQuitar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Paleta.NaranjaClaro, RoundedCornerShape(14.dp)).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(i.descripcion, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = NaranjaTexto)
            Text("${i.codigo} · se mide en ${i.unidad}", fontSize = 14.sp, color = NaranjaTexto)
        }
        IconButton(onClick = alQuitar) { Icon(Icons.Filled.Close, contentDescription = "Cambiar insumo", tint = NaranjaTexto) }
    }
}

private val circuloCheck = svg("M12 2a10 10 0 1 1 0 20a10 10 0 1 1 0 -20")
private val trazoCheck = svg("M7.5 12.5l3 3 6-6.5")

/** Check que se dibuja al guardar (propuesta de animación del lienzo, < 0,5 s). */
@Composable
private fun CheckGuardado() {
    val reducir = reducirMovimiento()
    val progreso = remember { Animatable(if (reducir) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reducir) progreso.animateTo(1f, tween(450)) }
    val medidas = remember { listOf(circuloCheck, trazoCheck).map { p -> PathMeasure().apply { setPath(p, false) } } }
    Canvas(Modifier.size(76.dp).semantics { contentDescription = "Guardada" }) {
        val k = size.width / 24f
        val lapiz = Stroke(1.8f * k, cap = StrokeCap.Round)
        medidas.forEachIndexed { i, m ->
            // El círculo hace la primera mitad del tiempo y el check la segunda.
            val p = ((progreso.value - i * 0.5f) * 2f).coerceIn(0f, 1f)
            if (p > 0f) {
                val tramo = Path()
                m.getSegment(0f, m.length * p, tramo, true)
                tramo.transform(androidx.compose.ui.graphics.Matrix().apply { scale(k, k) })
                drawPath(tramo, Paleta.Naranja, style = lapiz)
            }
        }
    }
}

@Composable
private fun Confirmacion(e: EstadoSalida, alOtra: () -> Unit, alVolver: () -> Unit) {
    Column(
        Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth()
            .vidrio(RoundedCornerShape(28.dp), fuerte = true, sombra = 16.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CheckGuardado()
        Text("Salida guardada", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, modifier = Modifier.semantics { heading() })
        val que = e.insumo?.let { "La salida de ${e.cantidad} ${it.unidad} de ${it.descripcion}" } ?: "La salida"
        Text(
            "$que quedó en el celular con su código único. Se envía sola cuando haya señal; un reenvío no la duplica.",
            fontSize = 17.sp, lineHeight = 25.sp, color = Paleta.Texto3, textAlign = TextAlign.Center,
        )
        ChipEstado(EstadoEnvio.PENDIENTE)
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonNegro("Registrar otra salida", alOtra)
            BotonNegro("Volver al inicio", alVolver, contorno = true)
        }
    }
}
