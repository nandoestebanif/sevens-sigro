package pe.sevens.sigro.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.flow.first
import pe.sevens.sigro.dominio.HoraLima
import pe.sevens.sigro.dominio.RepositorioStock
import pe.sevens.sigro.dominio.RepositorioTrabajadores
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.StockObra
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.EncabezadoVidrio
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.NuevaSalida
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.ValidarSalida
import pe.sevens.sigro.plataforma.reducirMovimiento
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
    /** Lo último usado en esta obra (desde este celular), para no escribirlo cada vez. */
    val sugerencias: SugerenciasSalida = SugerenciasSalida(),
    /** El último stock que dio el SERVIDOR (con su hora): la app no lo calcula, solo lo muestra (regla 2). */
    val stock: StockObra? = null,
)

class SalidaViewModel(
    private val catalogo: RepositorioCatalogo,
    private val salidas: RepositorioSalidas,
    private val programador: ProgramadorSincronizacion,
    obra: RepositorioObra,
    private val stock: RepositorioStock? = null,
    private val trabajadores: RepositorioTrabajadores? = null,
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoSalida())
    val estado = _e.asStateFlow()
    private var busquedaJob: Job? = null

    /** M6: de qué almacén sale (HU-40 lo exige). */
    val almacen = SelectorAlmacen(obra, viewModelScope)
    fun cargarAlmacenes(obraId: Int) = almacen.cargar(obraId)

    /** Al abrir: almacenes, lo último usado y el último stock del servidor (sin señal, la copia guardada). */
    fun cargar(obraId: Int) {
        almacen.cargar(obraId)
        viewModelScope.launch {
            val previas = runCatching { salidas.salidasDeObra(obraId).first() }.getOrDefault(emptyList())
            val nombres = runCatching { trabajadores?.deObra(obraId)?.first()?.filter { it.fechaSalida == null }?.map { it.nombre } }.getOrNull().orEmpty()
            _e.update { it.copy(sugerencias = sugerenciasSalida(previas, nombres)) }
        }
        viewModelScope.launch {
            val s = runCatching { stock?.stock(obraId) }.getOrNull()
            _e.update { it.copy(stock = s) }
        }
    }

    /** HU-38: una salida rechazada se registra de nuevo con sus datos ya puestos (es un registro nuevo, con su uuid). */
    fun precargar(base: SalidaRegistrada) = _e.update {
        it.copy(insumo = base.insumo, cantidad = base.cantidad, motivo = base.motivo, quienRetira = base.quienRetira, frente = base.frente.orEmpty())
    }
    fun elegirAlmacen(obraId: Int, a: AlmacenObra) {
        almacen.elegir(obraId, a)
        _e.update { it.copy(errores = it.errores - "almacen") }
    }

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
        val alm = almacen.estado.value
        val errores = ValidarSalida.errores(s.insumo, s.cantidad, s.motivo, s.quienRetira) +
            (if (alm.falta) mapOf("almacen" to "Elige de qué almacén sale") else emptyMap())
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            salidas.registrar(NuevaSalida(obraId, s.insumo!!, s.cantidad, s.motivo, s.quienRetira, s.frente, alm.elegido?.id))
            programador.programar() // si hay señal se envía ya; si no, cuando vuelva
            _e.update { it.copy(guardando = false, guardada = true) }
        }
    }

    /** Otra salida: se conserva quién retira y el frente, que suelen repetirse en la misma jornada. */
    fun otra() = _e.update { EstadoSalida(quienRetira = it.quienRetira, frente = it.frente) }
}

/* Lienzo Salida.dc.html y Guardada.dc.html, pasados al vidrio sobre el fondo de estanterías. */

private val NaranjaTexto get() = Paleta.NaranjaTexto

@Composable
fun PantallaSalida(obraId: Int, alVolver: () -> Unit, base: SalidaRegistrada? = null, vm: SalidaViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    val almacen by vm.almacen.estado.collectAsState()
    LaunchedEffect(obraId) {
        vm.cargar(obraId)
        if (base != null) vm.precargar(base)
    }
    FondoPlano(plano = { planoSalida(it) }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding()
                .navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            EncabezadoVidrio(if (base != null && !e.guardada) "Registrar de nuevo" else "Registrar salida", null, alVolver)
            if (e.guardada) {
                Confirmacion(e, alOtra = vm::otra, alVolver = alVolver)
                return@Column
            }
            Column(
                Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (base?.motivoRechazo != null) AvisoRojo("La anterior no se registró: ${base.motivoRechazo}")
                Column(
                    Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ElegirAlmacenObra(almacen, { vm.elegirAlmacen(obraId, it) }, e.errores["almacen"])
                    if (e.insumo == null) {
                        CampoVidrio(
                            e.busqueda, vm::buscar, "Insumo", marcador = "Nombre o código",
                            error = e.errores["insumo"],
                            finalIcono = { Icon(Icons.Filled.Search, contentDescription = null, tint = Paleta.Texto3, modifier = Modifier.padding(end = 10.dp)) },
                        )
                        if (e.resultados.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().background(Paleta.Superficie.copy(alpha = 0.9f), RoundedCornerShape(20.dp))) {
                                e.resultados.take(8).forEachIndexed { i, ins ->
                                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Paleta.Separador))
                                    Column(
                                        Modifier.fillMaxWidth().clickable { vm.elegir(ins) }.heightIn(min = 60.dp)
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(Texto.nombre(ins.descripcion), fontSize = 16.sp, color = Paleta.Tinta)
                                        Text("${ins.codigo} · ${Texto.unidad(ins.unidad)}", fontSize = 14.sp, color = Paleta.Texto3)
                                    }
                                }
                            }
                        } else if (e.busqueda.trim().length >= 2) {
                            Text("Nada coincide. Prueba con otra palabra.", fontSize = 15.sp, color = Paleta.Texto2)
                        }
                    } else {
                        InsumoElegido(e.insumo!!, vm::quitarInsumo)
                    }

                    CampoVidrio(
                        e.cantidad, vm::cantidad, "Cantidad",
                        teclado = KeyboardType.Decimal, sufijo = e.insumo?.let { Texto.unidad(it.unidad, e.cantidad) } ?: "", error = e.errores["cantidad"],
                        ayuda = stockConocido(e.stock, e.insumo, almacen.elegido?.id),
                    )
                }

                Column(
                    Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CampoVidrio(e.motivo, vm::motivo, "Motivo", marcador = "Ej.: vaciado de losa 3.er piso", error = e.errores["motivo"])
                    Sugerencias(e.sugerencias.motivos, e.motivo, vm::motivo)
                    CampoVidrio(e.quienRetira, vm::quienRetira, "Retira", marcador = "Nombre de quien lo lleva", error = e.errores["quienRetira"])
                    Sugerencias(e.sugerencias.quienes, e.quienRetira, vm::quienRetira)
                    CampoVidrio(e.frente, vm::frente, "Frente o cuadrilla (opcional)")
                    Sugerencias(e.sugerencias.frentes, e.frente, vm::frente)
                }

                BotonNegro("Guardar salida", { vm.guardar(obraId) }, icono = Icons.Filled.Save, cargando = e.guardando, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Lo último usado, en píldoras: tocar una la escribe en el campo. Se ocultan las que ya coinciden con lo escrito. */
@Composable
private fun Sugerencias(opciones: List<String>, actual: String, alElegir: (String) -> Unit) {
    val visibles = opciones.filter { !it.equals(actual.trim(), ignoreCase = true) }
    if (visibles.isEmpty()) return
    // Una sola fila que se desliza: no alarga el formulario.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        visibles.forEach { o ->
            Box(
                Modifier.heightIn(min = 44.dp).background(Paleta.Superficie.copy(alpha = 0.85f), RoundedCornerShape(22.dp))
                    .border(1.dp, Paleta.BordeCampo, RoundedCornerShape(22.dp))
                    .clickable(onClickLabel = "Usar «$o»") { alElegir(o) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta, maxLines = 1) }
        }
    }
}

@Composable
private fun InsumoElegido(i: Insumo, alQuitar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Paleta.NaranjaClaro, RoundedCornerShape(14.dp)).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Texto.nombre(i.descripcion), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = NaranjaTexto)
            Text("${i.codigo} · en ${Texto.unidad(i.unidad)}", fontSize = 14.sp, color = NaranjaTexto)
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
        val que = e.insumo?.let { "${Texto.cantidadConUnidad(e.cantidad, it.unidad)} de ${Texto.nombre(it.descripcion)}" } ?: "La salida"
        Text(
            "$que. Se envía sola en cuanto haya señal.",
            fontSize = 17.sp, lineHeight = 25.sp, color = Paleta.Texto3, textAlign = TextAlign.Center,
        )
        ChipEstado(EstadoEnvio.PENDIENTE)
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonNegro("Registrar otra salida", alOtra)
            BotonNegro("Volver al inicio", alVolver, contorno = true)
        }
    }
}
