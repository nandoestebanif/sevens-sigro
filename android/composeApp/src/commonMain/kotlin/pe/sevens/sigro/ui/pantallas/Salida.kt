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
import pe.sevens.sigro.dominio.Limites
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
import pe.sevens.sigro.dominio.ValidacionEnVivo
import pe.sevens.sigro.plataforma.reducirMovimiento
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.ChipEstado
import pe.sevens.sigro.ui.componentes.FondoPlano
import pe.sevens.sigro.ui.componentes.planoSalida
import pe.sevens.sigro.ui.componentes.svg
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta
import pe.sevens.sigro.dominio.casosdeuso.RegistrarSalida
import pe.sevens.sigro.ui.componentes.Ilustracion
import pe.sevens.sigro.ui.componentes.Ticket
import androidx.compose.ui.unit.em
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import pe.sevens.sigro.ui.componentes.rememberFuente
import pe.sevens.sigro.ui.componentes.hundirAlTocar
import pe.sevens.sigro.ui.componentes.DibujoObra
import pe.sevens.sigro.ui.componentes.Modulo
import pe.sevens.sigro.ui.componentes.CabeceraModulo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.PaddingValues

/* HU-40 · Registrar salida de material + HU-37 · sin conexión. */

data class EstadoSalida(
    val busqueda: String = "",
    val resultados: List<Insumo> = emptyList(),
    /** Lo que hay en el almacén elegido según el último stock del servidor; null = sin stock guardado (cae al catálogo). */
    val disponibles: List<InsumoConStock>? = null,
    /** Trabajadores activos de la obra: «Retira» se elige entre ellos. */
    val trabajadoresObra: List<String> = emptyList(),
    val buscandoQuien: Boolean = false,
    val insumo: Insumo? = null,
    val cantidad: String = "",
    val motivo: String = "",
    val quienRetira: String = "",
    val frente: String = "",
    val errores: Map<String, String> = emptyMap(),
    /** Campos ya revisados que están bien (✓), de la validación en vivo. */
    val correctos: Set<String> = emptySet(),
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
    private val registrarSalida = RegistrarSalida(salidas, programador)
    private val _e = MutableStateFlow(EstadoSalida())
    val estado = _e.asStateFlow()
    private var busquedaJob: Job? = null

    /** Validación mientras se escribe (las mismas reglas que al guardar). */
    private val enVivo = ValidacionEnVivo(viewModelScope, ::validarTodo, { r -> _e.update { it.copy(errores = r.errores, correctos = r.correctos) } })

    private fun validarTodo(): Map<String, String> {
        val s = _e.value
        return ValidarSalida.errores(s.insumo, s.cantidad, s.motivo, s.quienRetira) +
            (errorQuienRetira(s.quienRetira, s.trabajadoresObra)?.takeIf { s.quienRetira.trim().length >= 3 }?.let { mapOf("quienRetira" to it) } ?: emptyMap()) +
            (if (almacen.estado.value.falta) mapOf("almacen" to "Elige de qué almacén sale") else emptyMap())
    }

    /** Salió de un campo: su error (si lo hay) se muestra sin esperar la pausa. */
    fun salirDe(campo: String) = enVivo.salir(campo)

    /** M6: de qué almacén sale (HU-40 lo exige). */
    val almacen = SelectorAlmacen(obra, viewModelScope)
    fun cargarAlmacenes(obraId: Int) = almacen.cargar(obraId)

    /** Al abrir: almacenes, lo último usado y el último stock del servidor (sin señal, la copia guardada). */
    fun cargar(obraId: Int) {
        almacen.cargar(obraId)
        viewModelScope.launch {
            val previas = runCatching { salidas.salidasDeObra(obraId).first() }.getOrDefault(emptyList())
            runCatching { trabajadores?.actualizar(obraId) } // con señal trae la lista al día; sin señal usa la guardada
            val nombres = runCatching { trabajadores?.deObra(obraId)?.first()?.filter { it.fechaSalida == null }?.map { it.nombre } }.getOrNull().orEmpty()
            _e.update { it.copy(sugerencias = sugerenciasSalida(previas, nombres), trabajadoresObra = nombres) }
        }
        viewModelScope.launch {
            val s = runCatching { stock?.stock(obraId) }.getOrNull()
            _e.update { it.copy(stock = s) }
            recalcularDisponibles()
        }
    }

    /** HU-38: una salida rechazada se registra de nuevo con sus datos ya puestos (es un registro nuevo, con su uuid). */
    fun precargar(base: SalidaRegistrada) = _e.update {
        it.copy(insumo = base.insumo, cantidad = base.cantidad, motivo = base.motivo, quienRetira = base.quienRetira, frente = base.frente.orEmpty())
    }
    fun elegirAlmacen(obraId: Int, a: AlmacenObra) {
        almacen.elegir(obraId, a)
        enVivo.salir("almacen")
        recalcularDisponibles(a.id)
    }

    /** Solo lo que hay en el almacén elegido (dato del servidor, no calculado aquí). */
    private fun recalcularDisponibles(almacenId: Int? = almacen.estado.value.elegido?.id) =
        _e.update { it.copy(disponibles = insumosConStock(it.stock, almacenId, it.busqueda)) }

    fun buscar(texto: String) {
        _e.update { it.copy(busqueda = texto) }
        if (_e.value.stock != null) { recalcularDisponibles(); return }
        busquedaJob?.cancel()
        if (texto.trim().length < 2) { _e.update { it.copy(resultados = emptyList()) }; return }
        busquedaJob = viewModelScope.launch {
            delay(200) // espera a que termine de escribir
            val r = catalogo.buscar(texto)
            _e.update { it.copy(resultados = r) }
        }
    }

    fun elegir(i: Insumo) { _e.update { it.copy(insumo = i, busqueda = "", resultados = emptyList()) }; enVivo.salir("insumo") }
    fun quitarInsumo() { _e.update { it.copy(insumo = null) }; enVivo.revisarYa(); recalcularDisponibles() }

    /** [alInstante]: vino de un atajo o una sugerencia (no se está escribiendo), se revisa sin pausa. */
    fun cantidad(v: String, alInstante: Boolean = false) { _e.update { it.copy(cantidad = Limites.cantidad(it.cantidad, v)) }; revisar("cantidad", alInstante) }
    fun motivo(v: String, alInstante: Boolean = false) { _e.update { it.copy(motivo = Limites.texto(v, Limites.MOTIVO)) }; revisar("motivo", alInstante) }
    fun quienRetira(v: String, alInstante: Boolean = false) {
        _e.update { it.copy(quienRetira = Limites.texto(v, Limites.QUIEN_RETIRA), buscandoQuien = !alInstante && it.trabajadoresObra.isNotEmpty()) }
        revisar("quienRetira", alInstante)
    }
    fun elegirQuien(nombre: String) { _e.update { it.copy(quienRetira = nombre, buscandoQuien = false) }; enVivo.salir("quienRetira") }
    private fun revisar(campo: String, alInstante: Boolean) = if (alInstante) enVivo.salir(campo) else enVivo.cambio(campo)
    fun frente(v: String) = _e.update { it.copy(frente = Limites.texto(v, Limites.FRENTE)) }

    fun guardar(obraId: Int) {
        val s = _e.value
        val alm = almacen.estado.value
        if (enVivo.alGuardar().isNotEmpty()) return
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            // caso de uso: guarda en el celular y pide la sincronización (si hay señal se envía ya; si no, cuando vuelva)
            registrarSalida(NuevaSalida(obraId, s.insumo!!, s.cantidad, s.motivo, s.quienRetira, s.frente, alm.elegido?.id))
            _e.update { it.copy(guardando = false, guardada = true) }
        }
    }

    /** Otra salida: se conserva quién retira y el frente, que suelen repetirse en la misma jornada. */
    fun otra() {
        _e.update { EstadoSalida(quienRetira = it.quienRetira, frente = it.frente, stock = it.stock, trabajadoresObra = it.trabajadoresObra, sugerencias = it.sugerencias) }
        enVivo.reiniciar()
        recalcularDisponibles()
    }
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
            // Rediseño v2: cabecera amarilla del módulo Salida con el avance del formulario (insumo, cantidad, motivo y quién)
            val hechos = listOf(
                e.insumo != null,
                (e.cantidad.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0,
                e.motivo.isNotBlank() && e.quienRetira.isNotBlank(),
            ).count { it }
            CabeceraModulo(
                Modulo.SALIDA, if (base != null && !e.guardada) "Registrar de nuevo" else if (e.guardada) "Salida guardada" else "Registrar salida",
                alVolver, pasos = if (e.guardada) null else hechos to 3, conInsets = false,
            )
            Spacer(Modifier.height(16.dp))
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
                        val disp = insumosConStock(e.stock, almacen.elegido?.id, e.busqueda)
                        if (disp != null) {
                            // Solo lo que hay en el almacén elegido (último stock del servidor).
                            if (disp.isNotEmpty()) {
                                ListaVidrio(disp.take(8)) { x ->
                                    FilaElegible(Texto.nombre(x.insumo.descripcion),
                                        "${x.insumo.codigo} · hay ${Texto.cantidadConUnidad(x.cantidad, x.insumo.unidad)}") { vm.elegir(x.insumo) }
                                }
                                if (disp.size > 8) Text("Hay ${disp.size} insumos con stock: escribe para acotar.", fontSize = 14.sp, color = Paleta.Texto2)
                            } else if (e.busqueda.isNotBlank()) {
                                Text("No hay stock de eso en este almacén.", fontSize = 15.sp, color = Paleta.Texto2)
                            } else {
                                Text("Este almacén no tiene stock según la última actualización.", fontSize = 15.sp, color = Paleta.Texto2)
                            }
                        } else if (e.resultados.isNotEmpty()) {
                            Text("Sin stock guardado en el celular: se muestra todo el catálogo. Abre «Stock» con señal para ver solo lo que hay.",
                                fontSize = 14.sp, color = Paleta.Texto2)
                            ListaVidrio(e.resultados.take(8)) { ins ->
                                FilaElegible(Texto.nombre(ins.descripcion), "${ins.codigo} · ${Texto.unidad(ins.unidad)}") { vm.elegir(ins) }
                            }
                        } else if (e.busqueda.trim().length >= 2) {
                            Text("Nada coincide. Prueba con otra palabra.", fontSize = 15.sp, color = Paleta.Texto2)
                        }
                    } else {
                        InsumoElegido(e.insumo!!, vm::quitarInsumo)
                    }

                    CampoVidrio(
                        e.cantidad, { vm.cantidad(it) }, "Cantidad",
                        teclado = KeyboardType.Decimal, sufijo = e.insumo?.let { Texto.unidad(it.unidad, e.cantidad) } ?: "", error = e.errores["cantidad"],
                        ayuda = stockConocido(e.stock, e.insumo, almacen.elegido?.id),
                        alSalir = { vm.salirDe("cantidad") }, correcto = "cantidad" in e.correctos,
                    )
                    // Atajos de cantidad: con guantes es más rápido tocar +5 que escribir
                    AtajosCantidad(e.cantidad) { vm.cantidad(it, alInstante = true) }
                }

                Column(
                    Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CampoVidrio(
                        e.motivo, { vm.motivo(it) }, "Motivo", marcador = "Ej.: vaciado de losa 3.er piso", error = e.errores["motivo"],
                        ayuda = Limites.contador(e.motivo, Limites.MOTIVO),
                        alSalir = { vm.salirDe("motivo") }, correcto = "motivo" in e.correctos,
                    )
                    Sugerencias(e.sugerencias.motivos, e.motivo) { vm.motivo(it, alInstante = true) }
                    CampoVidrio(
                        e.quienRetira, { vm.quienRetira(it) }, "Retira",
                        marcador = if (e.trabajadoresObra.isNotEmpty()) "Busca entre los ${e.trabajadoresObra.size} trabajadores de la obra" else "Nombre de quien lo lleva",
                        error = e.errores["quienRetira"], alSalir = { vm.salirDe("quienRetira") },
                        finalIcono = { Icon(Icons.Filled.Search, contentDescription = null, tint = Paleta.Texto3, modifier = Modifier.padding(end = 10.dp)) },
                    )
                    if (e.trabajadoresObra.isNotEmpty() && e.buscandoQuien) {
                        val quienes = trabajadoresQueCoinciden(e.trabajadoresObra, e.quienRetira)
                        if (quienes.isEmpty()) Text("Nadie de la obra se llama así.", fontSize = 15.sp, color = Paleta.Texto2)
                        else ListaVidrio(quienes.take(6)) { n -> FilaElegible(n, null) { vm.elegirQuien(n) } }
                    } else if (e.trabajadoresObra.isEmpty()) {
                        Sugerencias(e.sugerencias.quienes, e.quienRetira) { vm.quienRetira(it, alInstante = true) }
                    }
                    CampoVidrio(e.frente, vm::frente, "Frente o cuadrilla (opcional)")
                    Sugerencias(e.sugerencias.frentes, e.frente, vm::frente)
                }

                BotonNegro("Guardar salida", { vm.guardar(obraId) }, icono = Icons.Filled.Save, cargando = e.guardando, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Lista de opciones sobre vidrio, separadas por una línea (insumos, trabajadores). */
@Composable
private fun <T> ListaVidrio(items: List<T>, fila: @Composable (T) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Paleta.Superficie.copy(alpha = 0.9f), RoundedCornerShape(20.dp))) {
        items.forEachIndexed { i, x ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Paleta.Separador))
            fila(x)
        }
    }
}

@Composable
private fun FilaElegible(titulo: String, detalle: String?, alElegir: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Elegir «$titulo»") { alElegir() }.heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(titulo, fontSize = 16.sp, color = Paleta.Tinta)
        if (detalle != null) Text(detalle, fontSize = 14.sp, color = Paleta.Texto3)
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
    // Rediseño: el insumo elegido es un ticket amarillo con la bolsa de cemento, como en el lienzo
    Ticket(Paleta.TicketAmarillo, Modifier.fillMaxWidth(), ilustracion = Ilustracion.CEMENTO, anchoIlustracion = 66.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("INSUMO", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em, color = Paleta.SobreTicket)
            Text(
                Texto.nombre(i.descripcion), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Paleta.SobreTicket,
                modifier = Modifier.padding(end = 70.dp),
            )
            Text("${i.codigo} · en ${Texto.unidad(i.unidad)}", fontSize = 13.sp, color = Paleta.SobreTicket.copy(alpha = 0.8f))
            TextButton(
                onClick = alQuitar,
                modifier = Modifier.padding(top = 4.dp).heightIn(min = 40.dp),
                colors = ButtonDefaults.textButtonColors(containerColor = Paleta.SobreTicket, contentColor = Paleta.TicketAmarillo),
            ) { Text("Cambiar insumo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
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
    // Rediseño v2: la bolsa en un círculo amarillo con el check que se dibuja, y los dos botones abajo
    val haptico = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptico.performHapticFeedback(HapticFeedbackType.LongPress) }
    Column(
        Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.padding(top = 8.dp).size(180.dp)) {
            Box(Modifier.fillMaxSize().background(Paleta.TicketAmarillo, CircleShape), contentAlignment = Alignment.Center) {
                DibujoObra(Ilustracion.CEMENTO, ancho = 128.dp)
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(60.dp).background(Paleta.FondoBase, CircleShape).padding(5.dp)
                    .background(Paleta.TicketMenta, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Paleta.SobreTicket, modifier = Modifier.size(28.dp)) }
        }
        Text("¡Salida guardada!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, modifier = Modifier.semantics { heading() })
        val que = e.insumo?.let { "${Texto.cantidadConUnidad(e.cantidad, it.unidad)} de ${Texto.nombre(it.descripcion)}" } ?: "La salida"
        Text(
            "$que. Se envía sola en cuanto haya señal.",
            fontSize = 16.sp, lineHeight = 23.sp, color = Paleta.Texto3, textAlign = TextAlign.Center,
        )
        ChipEstado(EstadoEnvio.PENDIENTE)
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonNegro("Registrar otra salida", alOtra, icono = Icons.Filled.Add)
            BotonNegro("Volver al inicio", alVolver, contorno = true)
        }
    }
}

/** +1, +5, +10 y +25 sobre lo escrito (y −1): suman en el mismo campo, que sigue validando igual. */
@Composable
private fun AtajosCantidad(actual: String, alCambiar: (String) -> Unit) {
    val n = actual.replace(',', '.').toDoubleOrNull() ?: 0.0
    fun texto(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-1, 1, 5, 10, 25).forEach { d ->
            val fuente = rememberFuente()
            OutlinedButton(
                onClick = { alCambiar(texto(maxOf(0.0, n + d))) },
                enabled = d > 0 || n >= 1.0,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).hundirAlTocar(fuente),
                interactionSource = fuente,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text(if (d > 0) "+$d" else "$d", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta) }
        }
    }
}
