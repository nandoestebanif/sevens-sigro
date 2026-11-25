package pe.sevens.sigro.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.intentosAgotados
import pe.sevens.sigro.dominio.MAX_INTENTOS_AUTOMATICOS
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.ResultadoSincronizacion
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.plataforma.ManejarAtras
import pe.sevens.sigro.plataforma.Plataforma
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.draw.clip
import pe.sevens.sigro.ui.componentes.DeslizarParaActualizar
import pe.sevens.sigro.ui.componentes.Ilustracion
import pe.sevens.sigro.ui.componentes.Ticket
import pe.sevens.sigro.recursos.Res
import pe.sevens.sigro.recursos.sevens_logo
import pe.sevens.sigro.recursos.sevens_logo_blanco
import pe.sevens.sigro.ui.componentes.FondoPlano
import pe.sevens.sigro.ui.componentes.planoInicio
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Apariencia
import pe.sevens.sigro.ui.tema.Paleta
import pe.sevens.sigro.dominio.casosdeuso.SincronizarCola
import pe.sevens.sigro.ui.componentes.rememberFuente
import pe.sevens.sigro.ui.componentes.hundirAlTocar
import pe.sevens.sigro.ui.componentes.Modulo
import pe.sevens.sigro.ui.componentes.DibujoObra
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState

data class EstadoInicio(
    val insumosEnCelular: Int = 0,
    val actualizandoCatalogo: Boolean = false,
    val enviando: Boolean = false,
    val mensaje: String? = null,
)

/** Lo que el inicio muestra de la obra: cuenta lo registrado en el celular, no el saldo (regla 2). */
data class ResumenObra(
    val salidasHoy: Int,
    val porEnviar: Int,
    val rechazadas: Int,
    /** Pendientes que agotaron sus intentos automáticos: se envían solo con «Enviar ahora». */
    val noSePudieronEnviar: Int,
    /** Los 3 más recientes de cualquier tipo: salida, entrada, ingreso, asistencia… (los mismos de «Mis registros»). */
    val ultimas: List<FilaRegistro>,
)

/**
 * Resume la lista unida de registros (salidas + cola general, ver [unirRegistros]).
 * «Por enviar» y «rechazadas» cuentan todos los tipos, igual que «Mis registros»; «salidas hoy» solo las salidas.
 * `dia` da la fecha local de una hora; dos horas con el mismo `dia` son del mismo día.
 */
fun resumirObra(
    registros: List<FilaRegistro>,
    ahoraMs: Long,
    dia: (Long) -> String = { Plataforma.formatearHora(it).substringBefore(' ') },
): ResumenObra {
    val hoy = dia(ahoraMs)
    return ResumenObra(
        salidasHoy = registros.count { it.salida != null && dia(it.horaMs) == hoy },
        porEnviar = registros.count { it.estado == EstadoEnvio.PENDIENTE },
        rechazadas = registros.count { it.estado == EstadoEnvio.RECHAZADO },
        noSePudieronEnviar = registros.count { intentosAgotados(it.estado, it.intentos) },
        ultimas = registros.sortedByDescending { it.horaMs }.take(3),
    )
}

class InicioViewModel(
    private val sesiones: RepositorioSesion,
    private val salidas: RepositorioSalidas,
    private val registros: RepositorioRegistros,
    private val catalogo: RepositorioCatalogo,
    private val programador: ProgramadorSincronizacion,
) : ViewModel() {
    private val sincronizarCola = SincronizarCola(salidas, registros)
    private val _estado = MutableStateFlow(EstadoInicio())
    val estado = _estado.asStateFlow()
    /** Por enviar de las dos colas (salidas y la general), para el botón «Enviar ahora» y la insignia. */
    val pendientes = combine(salidas.pendientes, registros.pendientes) { a, b -> a + b }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        programador.programar() // al abrir la app se intenta enviar lo que quedó en cola
        viewModelScope.launch {
            val n = catalogo.cuantosHay()
            _estado.update { it.copy(insumosEnCelular = n) }
            if (n == 0) actualizarCatalogo(silencioso = true) // primera vez: se baja solo
        }
    }

    /** Todo lo registrado en el celular para la obra (salidas, entradas, ingresos, asistencia…), lo más reciente primero. */
    fun registrosDe(obraId: Int) = combine(salidas.salidasDeObra(obraId), registros.deObra(obraId), ::unirRegistros)

    fun actualizarCatalogo(silencioso: Boolean = false) {
        _estado.update { it.copy(actualizandoCatalogo = true) }
        viewModelScope.launch {
            try {
                val n = catalogo.actualizar()
                _estado.update { it.copy(insumosEnCelular = n, actualizandoCatalogo = false, mensaje = if (silencioso) null else "Catálogo al día: $n insumos en el celular") }
            } catch (e: ErrorSigro) {
                _estado.update { it.copy(actualizandoCatalogo = false, mensaje = if (silencioso) null else e.message) }
            }
        }
    }

    fun enviarAhora() {
        _estado.update { it.copy(enviando = true) }
        viewModelScope.launch {
            // Caso de uso: las dos colas, como en «Mis registros». Si una falla (sin señal), la otra igual se intenta.
            val r = sincronizarCola(manual = true)
            val texto = when {
                r.enviados == 0 && r.rechazados == 0 && r.siguenPendientes == 0 -> "Todo está enviado"
                r.siguenPendientes > 0 && r.enviados == 0 -> "Sin señal: ${registros(r.siguenPendientes)} ${if (r.siguenPendientes == 1) "espera" else "esperan"} y se ${if (r.siguenPendientes == 1) "enviará" else "enviarán"} solos"
                r.siguenPendientes > 0 -> "Se enviaron ${r.enviados}; ${registros(r.siguenPendientes)} se ${if (r.siguenPendientes == 1) "enviará" else "enviarán"} con señal"
                r.rechazados > 0 -> "Se enviaron ${r.enviados}; ${r.rechazados} ${if (r.rechazados == 1) "fue rechazado: revísalo" else "fueron rechazados: revísalos"}"
                else -> "Listo: se ${if (r.enviados == 1) "envió 1 registro" else "enviaron ${r.enviados} registros"}"
            }
            _estado.update { it.copy(enviando = false, mensaje = texto) }
        }
    }

    private fun registros(n: Int) = if (n == 1) "1 registro" else "$n registros"

    fun mensajeVisto() = _estado.update { it.copy(mensaje = null) }
    fun salir() = viewModelScope.launch { sesiones.cerrarSesion() }
    fun cambiarDeObra() = viewModelScope.launch { sesiones.cambiarDeObra() }
}

/* Inicio = «Almacén» (diseno/lienzo/Inicio.dc.html): resumen del día, accesos, últimos registros, barra de vidrio. */

@Composable
fun PantallaInicio(
    sesion: Sesion,
    obra: ObraAsignada,
    irA: (Destino) -> Unit,
    vm: InicioViewModel = koinViewModel(),
    alElegirObra: (ObraAsignada) -> Unit = {},
) {
    val e by vm.estado.collectAsState()
    val pendientes by vm.pendientes.collectAsState()
    val flujo = remember(obra.id) { vm.registrosDe(obra.id) }
    val lista by flujo.collectAsState(initial = emptyList())
    val resumen = remember(lista) { resumirObra(lista, Plataforma.ahoraMs()) }
    var conMas by remember { mutableStateOf(false) }
    var cambiandoObra by remember { mutableStateOf(false) }
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(e.mensaje) {
        e.mensaje?.let { avisos.showSnackbar(it); vm.mensajeVisto() }
    }
    ManejarAtras(activo = conMas || cambiandoObra, alVolver = { conMas = false; cambiandoObra = false })

    FondoPlano(plano = { planoInicio(it) }) {
        DeslizarParaActualizar(e.enviando, { vm.enviarAhora(); vm.actualizarCatalogo(silencioso = true) }, Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Rediseño v2: cabecera grande con la obra, el estado de la cola y la grúa
                CabeceraInicio(sesion, obra, resumen, pendientes, alCambiarObra = { cambiandoObra = true })
                CifrasDeHoy(resumen, alVerRegistros = { irA(Destino.Pendientes) }, alVerRechazados = { irA(Destino.Rechazados) })
                AvisosCola(resumen, pendientes, e.enviando, alEnviar = vm::enviarAhora, alVerRegistros = { irA(Destino.Pendientes) })
                // Accesos en mosaico: Salida (lo que más se usa) grande; Entrada e Ingreso al lado; Personal abajo
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Acceso(Icons.Filled.ArrowUpward, "Salida", "Registrar lo que sale", Modulo.SALIDA, grande = true,
                        modifier = Modifier.weight(1.15f).fillMaxHeight()) { irA(Destino.Salida) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Acceso(Icons.Filled.ArrowDownward, "Entrada", "Lo que llegó de una compra", Modulo.ENTRADA) { irA(Destino.Entrada) }
                        Acceso(Icons.Filled.MoveDown, "Ingreso", "Devoluciones y préstamos", Modulo.INGRESO) { irA(Destino.IngresoSinCompra) }
                    }
                }
                Acceso(Icons.Filled.Groups, "Personal", "Trabajadores y asistencia de hoy", Modulo.PERSONAL, ancho = true) { irA(Destino.Trabajadores) }
                OutlinedButton(
                    onClick = { conMas = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = Paleta.Tinta, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Más · Catálogo, Stock y lo que viene", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
                }
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Hoy en la obra", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TextButton(onClick = { irA(Destino.Pendientes) }) {
                        Text("Ver todo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paleta.Naranja)
                    }
                }
                Column(Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp))) {
                    if (resumen.ultimas.isEmpty()) {
                        Text(
                            "Todavía no hay registros en esta obra.",
                            fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.Texto2, modifier = Modifier.padding(16.dp),
                        )
                    }
                    resumen.ultimas.forEachIndexed { i, s ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Paleta.Separador))
                        FilaUltimo(s) { irA(Destino.Pendientes) }
                    }
                }
            }
        }

        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))

        BarraInferior(
            pendientes,
            alRegistros = { irA(Destino.Pendientes) },
            alStock = { irA(Destino.Stock) },
            alPerfil = { irA(Destino.Perfil) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(conMas, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color(0x470F172A))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Cerrar") { conMas = false },
            )
        }
        AnimatedVisibility(
            conMas,
            enter = slideInVertically { it }, exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            HojaMas(irA = { conMas = false; irA(it) }, alCerrar = { conMas = false })
        }

        // Rediseño v3: «cambiar obra» abre una hoja sobre el inicio (ya no saca a la pantalla de entrada)
        AnimatedVisibility(cambiandoObra, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color(0x8C0A0A0A))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Cerrar") { cambiandoObra = false },
            )
        }
        AnimatedVisibility(
            cambiandoObra,
            enter = slideInVertically { it }, exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            HojaCambiarObra(sesion, obra, alElegir = { cambiandoObra = false; alElegirObra(it) }, alCerrar = { cambiandoObra = false })
        }
    }
}

/** Cabecera negra de la marca: logo, estado de la cola, saludo, la obra en amarillo, el rol y la grúa. */
@Composable
private fun CabeceraInicio(sesion: Sesion, obra: ObraAsignada, r: ResumenObra, pendientes: Int, alCambiarObra: () -> Unit) {
    val hora = Plataforma.formatearHora(Plataforma.ahoraMs()).substringAfter(' ').substringBefore(':').toIntOrNull() ?: 12
    val saludo = when (hora) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Paleta.Noche)) {
        // brillo amarillo y grúa (decorativos)
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = (-40).dp).size(240.dp)
                .background(Brush.radialGradient(listOf(Paleta.TicketAmarillo.copy(alpha = 0.32f), Color.Transparent)), CircleShape),
        )
        DibujoObra(Ilustracion.GRUA, Modifier.align(Alignment.TopEnd).padding(top = 58.dp), ancho = 150.dp)
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(Res.drawable.sevens_logo_blanco), contentDescription = "SEVEN'S Ingenieros SAC", modifier = Modifier.height(40.dp))
                Spacer(Modifier.weight(1f))
                EstadoCola(pendientes, r.noSePudieronEnviar)
            }
            Text("$saludo, ${sesion.nombre.substringBefore(' ')}", fontSize = 14.sp, color = Color(0xFFB4B5B5), modifier = Modifier.padding(top = 10.dp))
            Text(
                buildAnnotatedString {
                    append("Almacén de ")
                    withStyle(SpanStyle(color = Paleta.TicketAmarillo)) { append(obra.nombre) }
                },
                fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em, color = Color.White,
                modifier = Modifier.padding(end = 120.dp).semantics { heading() },
            )
            Row(
                Modifier.padding(top = 8.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClickLabel = "Cambiar de obra", onClick = alCambiarObra).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${obra.rolLegible} · cambiar obra", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD2D3D3))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFA9AAAA), modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Píldora con el estado de la cola: lo primero que el almacenero quiere saber. Siempre con palabra, no solo color. */
@Composable
private fun EstadoCola(pendientes: Int, atascados: Int) {
    val (texto, color) = when {
        atascados > 0 -> "$atascados sin enviar" to Color(0xFFFF9A9A)
        pendientes > 0 -> "$pendientes por enviar" to Color(0xFFF5C451)
        else -> "Todo enviado" to Paleta.TicketMenta
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Las tres cifras del día en mini tickets de color; la cifra cuenta hasta su valor al aparecer. */
@Composable
private fun CifrasDeHoy(r: ResumenObra, alVerRegistros: () -> Unit, alVerRechazados: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniCifra(r.salidasHoy, if (r.salidasHoy == 1) "Salida hoy" else "Salidas hoy", Paleta.TicketAmarillo, Icons.Filled.ArrowUpward, null)
        MiniCifra(r.porEnviar, "Por enviar", Paleta.TicketMenta, Icons.Filled.CloudUpload, alVerRegistros)
        MiniCifra(r.rechazadas, if (r.rechazadas == 1) "Rechazada" else "Rechazadas", Paleta.TicketRosa, Icons.Filled.ErrorOutline,
            if (r.rechazadas > 0) alVerRechazados else null)
    }
}

@Composable
private fun RowScope.MiniCifra(valor: Int, etiqueta: String, color: Color, icono: ImageVector, alPulsar: (() -> Unit)?) {
    val animado by animateIntAsState(valor, tween(600), label = etiqueta)
    val fuente = rememberFuente()
    Ticket(
        color,
        Modifier.weight(1f).hundirAlTocar(fuente)
            .then(if (alPulsar != null) Modifier.clickable(fuente, indication = null, onClickLabel = "Ver $etiqueta", onClick = alPulsar) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "$valor $etiqueta" },
        radio = 20.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("$animado", fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = Paleta.SobreTicket, modifier = Modifier.weight(1f))
                Box(Modifier.size(26.dp).background(Paleta.SobreTicket, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                }
            }
            Text(etiqueta, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Paleta.SobreTicket, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Lo que pide acción de la cola: los atascados tras los reintentos y «Enviar ahora» (HU-38). */
@Composable
private fun AvisosCola(r: ResumenObra, pendientesTotales: Int, enviando: Boolean, alEnviar: () -> Unit, alVerRegistros: () -> Unit) {
    if (r.noSePudieronEnviar > 0) {
        Text(
            "${r.noSePudieronEnviar} sin enviar tras ${MAX_INTENTOS_AUTOMATICOS} intentos: revisa la señal o la dirección del servidor y toca «Enviar ahora».",
            fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.RechazadoTexto,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paleta.RechazadoFondo)
                .clickable(onClick = alVerRegistros).padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
    if (pendientesTotales > 0) {
        OutlinedButton(
            onClick = alEnviar, enabled = !enviando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp),
        ) {
            if (enviando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Paleta.Naranja)
            else Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (enviando) "Enviando…" else "Enviar ahora · $pendientesTotales ${if (pendientesTotales == 1) "registro" else "registros"}",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta,
            )
        }
    }
}

/** Acceso del inicio: ticket del color de su módulo, con ilustración, ícono, nombre y una línea de qué hace. */
@Composable
private fun Acceso(
    icono: ImageVector, texto: String, detalle: String, modulo: Modulo,
    modifier: Modifier = Modifier, grande: Boolean = false, ancho: Boolean = false, alPulsar: () -> Unit,
) {
    val fuente = rememberFuente()
    Ticket(
        modulo.color,
        modifier.fillMaxWidth().heightIn(min = if (ancho) 96.dp else 120.dp).hundirAlTocar(fuente)
            .clickable(fuente, indication = null, role = Role.Button, onClickLabel = texto, onClick = alPulsar)
            .semantics(mergeDescendants = true) {},
        ilustracion = modulo.dibujo, anchoIlustracion = if (grande) 100.dp else 66.dp,
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp).padding(end = if (ancho) 80.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(34.dp).background(Paleta.SobreTicket, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(icono, contentDescription = null, tint = modulo.color, modifier = Modifier.size(18.dp))
            }
            Text(texto, fontSize = if (grande) 22.sp else 15.sp, fontWeight = FontWeight.Bold, color = Paleta.SobreTicket, modifier = Modifier.padding(top = 4.dp))
            Text(detalle, fontSize = 11.sp, lineHeight = 14.sp, color = Paleta.SobreTicket.copy(alpha = 0.78f))
        }
    }
}

@Composable
private fun FilaUltimo(s: FilaRegistro, alPulsar: () -> Unit) {
    val (icono, color, fondo, palabra) = when (s.estado) {
        EstadoEnvio.PENDIENTE -> Cuatro(Icons.Filled.Schedule, Paleta.PendienteTexto, Paleta.PendienteFondo, "por enviar")
        EstadoEnvio.ENVIADO -> Cuatro(Icons.Filled.Check, Paleta.EnviadoTexto, Paleta.EnviadoFondo, "enviado")
        EstadoEnvio.RECHAZADO -> Cuatro(Icons.Filled.ErrorOutline, Paleta.RechazadoTexto, Paleta.RechazadoFondo, "rechazado")
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = alPulsar).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // el cuadro lleva el color del módulo (salida, entrada, ingreso, asistencia); el estado va en palabra abajo
        val (iconoTipo, colorTipo) = tipoVisual(s.tipo)
        Box(Modifier.size(42.dp).background(colorTipo, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(iconoTipo, contentDescription = null, tint = Paleta.SobreTicket, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Tipo arriba (Salida, Entrada de compra, Asistencia…): así se distingue de un vistazo.
            Text(s.tipo.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Paleta.Naranja)
            Text(Texto.nombre(s.titulo), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(s.salida?.let { Texto.cantidadConUnidad(it.cantidad, it.insumo.unidad) } ?: s.detalle, palabra).joinToString(" · "), fontSize = 13.sp, color = Paleta.Texto2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Plataforma.formatearHora(s.horaMs).substringAfter(' '), fontSize = 13.sp, color = Paleta.Texto2)
    }
}

/** Ícono y color del módulo de cada tipo de registro. */
private fun tipoVisual(tipo: String): Pair<ImageVector, Color> {
    val t = tipo.lowercase()
    return when {
        t.startsWith("salida") -> Icons.Filled.ArrowUpward to Modulo.SALIDA.color
        t.startsWith("entrada") -> Icons.Filled.ArrowDownward to Modulo.ENTRADA.color
        t.startsWith("ingreso") -> Icons.Filled.MoveDown to Modulo.INGRESO.color
        t.startsWith("asistencia") || t.startsWith("trabajador") -> Icons.Filled.Groups to Modulo.PERSONAL.color
        else -> Icons.Filled.Inventory2 to Modulo.STOCK.color
    }
}

private data class Cuatro(val icono: ImageVector, val color: Color, val fondo: Color, val palabra: String)

/**
 * Barra de navegación inferior estándar de Material 3: `NavigationBar` con un `NavigationBarItem` por destino.
 * `BadgedBox` + `Badge` ponen el número de registros por enviar sobre el ícono (y el lector lo dice en voz).
 */
@Composable
private fun BarraInferior(pendientes: Int, alRegistros: () -> Unit, alStock: () -> Unit, alPerfil: () -> Unit, modifier: Modifier) {
    // Rediseño: barra negra flotante con esquinas redondas y la pestaña elegida en amarillo
    val colores = NavigationBarItemDefaults.colors(
        selectedIconColor = Paleta.SobrePrincipal, selectedTextColor = Paleta.Principal, indicatorColor = Paleta.Principal,
        unselectedIconColor = Color(0xFFA9AAAA), unselectedTextColor = Color(0xFFA9AAAA),
    )
    NavigationBar(
        modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp).clip(RoundedCornerShape(24.dp)),
        containerColor = Paleta.Noche, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        NavigationBarItem(
            colors = colores,
            selected = true, onClick = {},
            icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text("Inicio") },
        )
        NavigationBarItem(
            colors = colores,
            selected = false, onClick = alRegistros,
            icon = {
                BadgedBox(badge = { if (pendientes > 0) Badge { Text(if (pendientes > 9) "9+" else "$pendientes") } }) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null)
                }
            },
            label = { Text("Registros") },
            modifier = Modifier.semantics { if (pendientes > 0) contentDescription = "Registros, $pendientes por enviar" },
        )
        NavigationBarItem(
            colors = colores,
            selected = false, onClick = alStock,
            icon = { Icon(Icons.Filled.ViewInAr, contentDescription = null) }, label = { Text("Stock") },
        )
        NavigationBarItem(
            colors = colores,
            selected = false, onClick = alPerfil,
            icon = { Icon(Icons.Filled.Engineering, contentDescription = null) }, label = { Text("Perfil") },
        )
    }
}

/**
 * Hoja «Más»: las demás operaciones. Lo de la cuenta (celular, catálogo, cerrar sesión) está en Perfil.
 * Traslado, conteo y cierre se guardan en el celular pero el servidor todavía no los recibe (HU-42, 46, 47 son de
 * la siguiente iteración): se ven con «Pronto» en vez de dejar registrar algo que no llegará.
 */
@Composable
private fun HojaMas(irA: (Destino) -> Unit, alCerrar: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp)
            .vidrio(RoundedCornerShape(32.dp), fuerte = true, sombra = 24.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(5.dp).background(Paleta.Tinta.copy(alpha = 0.18f), RoundedCornerShape(3.dp)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Más", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = alCerrar, modifier = Modifier.size(44.dp).background(Paleta.Tinta.copy(alpha = 0.06f), CircleShape)) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Paleta.Tinta, modifier = Modifier.size(18.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionMas(Icons.Filled.Search, "Catálogo", centrada = true) { irA(Destino.Catalogo) }
            OpcionMas(Icons.Filled.Groups, "Personal", centrada = true) { irA(Destino.Trabajadores) }
            OpcionMas(Icons.Filled.ViewInAr, "Stock", centrada = true) { irA(Destino.Stock) }
        }
        Text("PRÓXIMAMENTE", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em, color = Paleta.Texto3)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionMas(Icons.Filled.SwapHoriz, "Traslado", centrada = true, alPulsar = null)
            OpcionMas(Icons.Filled.FactCheck, "Conteo", centrada = true, alPulsar = null)
            OpcionMas(Icons.Filled.Lock, "Cierre", centrada = true, alPulsar = null)
            OpcionMas(Icons.Filled.Build, "Herramientas", centrada = true, alPulsar = null)
        }
    }
}

@Composable
private fun RowScope.OpcionMas(icono: ImageVector, texto: String, centrada: Boolean = false, alPulsar: (() -> Unit)?) {
    val pronto = alPulsar == null
    Column(
        Modifier.weight(1f).alpha(if (pronto) 0.55f else 1f).background(Paleta.Superficie, RoundedCornerShape(20.dp))
            .clickable(enabled = !pronto, role = Role.Button) { alPulsar?.invoke() }
            .heightIn(min = 44.dp)
            .padding(horizontal = if (centrada) 8.dp else 14.dp, vertical = if (centrada) 12.dp else 14.dp)
            .semantics(mergeDescendants = true) { if (pronto) contentDescription = "$texto, próximamente" },
        horizontalAlignment = if (centrada) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(if (centrada) 6.dp else 8.dp),
    ) {
        Icon(icono, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(24.dp))
        Text(texto, fontSize = if (centrada) 13.sp else 14.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
        if (pronto) Text("Pronto", fontSize = 12.sp, color = Paleta.Texto3)
    }
}
