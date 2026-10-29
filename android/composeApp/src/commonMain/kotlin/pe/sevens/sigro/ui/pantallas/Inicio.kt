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
import androidx.compose.material3.Button
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.plataforma.ManejarAtras
import pe.sevens.sigro.plataforma.Plataforma
import pe.sevens.sigro.recursos.Res
import pe.sevens.sigro.recursos.sevens_logo
import pe.sevens.sigro.ui.componentes.FondoPlano
import pe.sevens.sigro.ui.componentes.planoInicio
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta

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
    val ultimas: List<SalidaRegistrada>,
)

/** `dia` da la fecha local de una hora; dos horas con el mismo `dia` son del mismo día. */
fun resumirObra(
    salidas: List<SalidaRegistrada>,
    ahoraMs: Long,
    dia: (Long) -> String = { Plataforma.formatearHora(it).substringBefore(' ') },
): ResumenObra {
    val hoy = dia(ahoraMs)
    return ResumenObra(
        salidasHoy = salidas.count { dia(it.horaDispositivoMs) == hoy },
        porEnviar = salidas.count { it.estado == EstadoEnvio.PENDIENTE },
        rechazadas = salidas.count { it.estado == EstadoEnvio.RECHAZADO },
        ultimas = salidas.sortedByDescending { it.horaDispositivoMs }.take(3),
    )
}

class InicioViewModel(
    private val sesiones: RepositorioSesion,
    private val salidas: RepositorioSalidas,
    private val catalogo: RepositorioCatalogo,
    private val programador: ProgramadorSincronizacion,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoInicio())
    val estado = _estado.asStateFlow()
    val pendientes = salidas.pendientes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        programador.programar() // al abrir la app se intenta enviar lo que quedó en cola
        viewModelScope.launch {
            val n = catalogo.cuantosHay()
            _estado.update { it.copy(insumosEnCelular = n) }
            if (n == 0) actualizarCatalogo(silencioso = true) // primera vez: se baja solo
        }
    }

    /** Las salidas de la obra guardadas en el celular, para el resumen y los últimos registros. */
    fun salidasDe(obraId: Int) = salidas.salidasDeObra(obraId)

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
            val r = salidas.sincronizar()
            val texto = when {
                r.enviados == 0 && r.rechazados == 0 && r.siguenPendientes == 0 -> "No hay nada por enviar"
                r.siguenPendientes > 0 -> "Enviados ${r.enviados}. Quedan ${r.siguenPendientes}: se reintentará solo"
                else -> "Enviados ${r.enviados}" + if (r.rechazados > 0) " · ${r.rechazados} rechazados (revisa Pendientes)" else ""
            }
            _estado.update { it.copy(enviando = false, mensaje = texto) }
        }
    }

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
) {
    val e by vm.estado.collectAsState()
    val pendientes by vm.pendientes.collectAsState()
    val flujo = remember(obra.id) { vm.salidasDe(obra.id) }
    val lista by flujo.collectAsState(initial = emptyList())
    val resumen = remember(lista) { resumirObra(lista, Plataforma.ahoraMs()) }
    var conMas by remember { mutableStateOf(false) }
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(e.mensaje) {
        e.mensaje?.let { avisos.showSnackbar(it); vm.mensajeVisto() }
    }
    ManejarAtras(activo = conMas, alVolver = { conMas = false })

    FondoPlano(plano = { planoInicio(it) }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(Res.drawable.sevens_logo), contentDescription = "SEVEN'S Ingenieros SAC",
                    modifier = Modifier.height(64.dp).offset(x = (-4).dp),
                )
                Spacer(Modifier.weight(1f))
                // La obra elegida; tocarla lleva a «Elegir obra», que también muestra el perfil.
                Row(
                    Modifier.heightIn(min = 44.dp).vidrio(RoundedCornerShape(22.dp), sombra = 6.dp)
                        .clickable(onClickLabel = "Cambiar de obra", onClick = { vm.cambiarDeObra() })
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        obra.nombre, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 190.dp),
                    )
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Paleta.Texto3, modifier = Modifier.size(16.dp))
                }
            }

            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(
                    "Almacén", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.035).em, color = Paleta.Tinta,
                    modifier = Modifier.semantics { heading() },
                )
                Text("${sesion.nombre} · ${obra.rolLegible}", fontSize = 14.sp, color = Paleta.Texto3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            TarjetaResumen(resumen, pendientes, e.enviando, alEnviar = vm::enviarAhora, alVerRegistros = { irA(Destino.Pendientes) })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Acceso(Icons.Filled.ArrowUpward, "Salida", principal = true) { irA(Destino.Salida) }
                Acceso(Icons.Filled.ArrowDownward, "Entrada", habilitado = false) {}
                Acceso(Icons.Filled.ViewInAr, "Stock", habilitado = false) {}
                Acceso(Icons.Filled.MoreHoriz, "Más") { conMas = true }
            }

            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Últimos registros", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = { irA(Destino.Pendientes) }) {
                    Text("Ver todo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paleta.Naranja)
                }
            }
            Column(Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp))) {
                if (resumen.ultimas.isEmpty()) {
                    Text(
                        "Aún no registras salidas en esta obra. Las que guardes aparecen aquí, con su estado de envío.",
                        fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.Texto2, modifier = Modifier.padding(16.dp),
                    )
                }
                resumen.ultimas.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Paleta.Separador))
                    FilaRegistro(s) { irA(Destino.Pendientes) }
                }
            }
        }

        Button(
            onClick = { irA(Destino.Salida) },
            shape = RoundedCornerShape(29.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Paleta.Tinta, contentColor = Color.White),
            contentPadding = PaddingValues(start = 18.dp, end = 22.dp),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                .padding(end = 18.dp, bottom = 108.dp).height(58.dp)
                .shadow(14.dp, RoundedCornerShape(29.dp), spotColor = Paleta.Tinta.copy(alpha = 0.45f)),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Paleta.NaranjaVivo, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Registrar salida", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 176.dp))

        BarraInferior(
            pendientes,
            alRegistros = { irA(Destino.Pendientes) },
            alPerfil = { vm.cambiarDeObra() },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
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
            HojaMas(
                e,
                alCelular = { conMas = false; irA(Destino.Dispositivo) },
                alActualizar = { vm.actualizarCatalogo() },
                alSalir = { conMas = false; vm.salir() },
                alCerrar = { conMas = false },
            )
        }
    }
}

@Composable
private fun TarjetaResumen(r: ResumenObra, pendientesTotales: Int, enviando: Boolean, alEnviar: () -> Unit, alVerRegistros: () -> Unit) {
    Column(Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Cifra(r.salidasHoy, if (r.salidasHoy == 1) "Salida hoy" else "Salidas hoy", Paleta.Tinta, null)
            Cifra(r.porEnviar, "Por enviar", Paleta.Ambar, alVerRegistros, conBorde = true)
            Cifra(r.rechazadas, if (r.rechazadas == 1) "Rechazada" else "Rechazadas", Paleta.Rojo, alVerRegistros, conBorde = true)
        }
        // La cola se envía sola; el botón es para no esperar al reintento (HU-38).
        if (pendientesTotales > 0) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Paleta.Separador))
            TextButton(onClick = alEnviar, enabled = !enviando, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                if (enviando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Paleta.Naranja)
                else Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (enviando) "Enviando…" else "Enviar ahora · $pendientesTotales ${if (pendientesTotales == 1) "registro" else "registros"}",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Naranja,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Cifra(valor: Int, etiqueta: String, color: Color, alPulsar: (() -> Unit)?, conBorde: Boolean = false) {
    Row(Modifier.weight(1f)) {
        if (conBorde) Box(Modifier.width(1.dp).height(52.dp).background(Paleta.Tinta.copy(alpha = 0.10f)))
        Column(
            Modifier.weight(1f)
                .then(if (alPulsar != null) Modifier.clickable(onClickLabel = "Ver registros", onClick = alPulsar) else Modifier)
                .padding(start = if (conBorde) 14.dp else 0.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("$valor", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(etiqueta, fontSize = 13.sp, color = Paleta.Texto2)
        }
    }
}

@Composable
private fun RowScope.Acceso(icono: ImageVector, texto: String, principal: Boolean = false, habilitado: Boolean = true, alPulsar: () -> Unit) {
    val forma = RoundedCornerShape(22.dp)
    Column(
        Modifier.weight(1f).alpha(if (habilitado) 1f else 0.5f)
            .clickable(enabled = habilitado, role = Role.Button, onClick = alPulsar)
            .semantics(mergeDescendants = true) { if (!habilitado) contentDescription = "$texto, próximamente" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            if (principal) Modifier.size(62.dp).shadow(10.dp, forma, spotColor = Paleta.Naranja.copy(alpha = 0.5f)).background(Paleta.Naranja, forma)
            else Modifier.size(62.dp).vidrio(forma, sombra = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icono, contentDescription = null, tint = if (principal) Color.White else Paleta.Naranja, modifier = Modifier.size(26.dp))
        }
        Text(texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
        if (!habilitado) Text("Pronto", fontSize = 11.sp, color = Paleta.Texto3, modifier = Modifier.offset(y = (-6).dp))
    }
}

@Composable
private fun FilaRegistro(s: SalidaRegistrada, alPulsar: () -> Unit) {
    val (icono, color, fondo, palabra) = when (s.estado) {
        EstadoEnvio.PENDIENTE -> Cuatro(Icons.Filled.Schedule, Paleta.PendienteTexto, Paleta.PendienteFondo, "por enviar")
        EstadoEnvio.ENVIADO -> Cuatro(Icons.Filled.Check, Paleta.EnviadoTexto, Paleta.EnviadoFondo, "enviada")
        EstadoEnvio.RECHAZADO -> Cuatro(Icons.Filled.ErrorOutline, Paleta.RechazadoTexto, Paleta.RechazadoFondo, "rechazada")
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = alPulsar).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(fondo, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.insumo.descripcion, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${s.cantidad} ${s.insumo.unidad} · $palabra", fontSize = 13.sp, color = Paleta.Texto2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Plataforma.formatearHora(s.horaDispositivoMs).substringAfter(' '), fontSize = 13.sp, color = Paleta.Texto2)
    }
}

private data class Cuatro(val icono: ImageVector, val color: Color, val fondo: Color, val palabra: String)

@Composable
private fun BarraInferior(pendientes: Int, alRegistros: () -> Unit, alPerfil: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().height(76.dp).vidrio(RoundedCornerShape(38.dp), fuerte = true, sombra = 16.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemBarra(Icons.Filled.Home, "Inicio", activo = true) {}
        ItemBarra(Icons.AutoMirrored.Filled.FormatListBulleted, "Registros", insignia = pendientes, alPulsar = alRegistros)
        ItemBarra(Icons.Filled.ViewInAr, "Stock", habilitado = false) {}
        ItemBarra(Icons.Filled.Engineering, "Perfil", alPulsar = alPerfil)
    }
}

@Composable
private fun RowScope.ItemBarra(
    icono: ImageVector, texto: String, activo: Boolean = false, habilitado: Boolean = true, insignia: Int = 0, alPulsar: () -> Unit,
) {
    Column(
        Modifier.weight(1f).fillMaxSize().alpha(if (habilitado) 1f else 0.45f)
            .clickable(enabled = habilitado && !activo, role = Role.Tab, onClick = alPulsar)
            .semantics(mergeDescendants = true) {
                contentDescription = when {
                    !habilitado -> "$texto, próximamente"
                    insignia > 0 -> "$texto, $insignia por enviar"
                    else -> texto
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.width(62.dp).height(34.dp).then(
                if (activo) Modifier.shadow(8.dp, RoundedCornerShape(17.dp), spotColor = Paleta.Naranja.copy(alpha = 0.5f)).background(Paleta.Naranja, RoundedCornerShape(17.dp))
                else Modifier,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icono, contentDescription = null, tint = if (activo) Color.White else Paleta.Texto3, modifier = Modifier.size(22.dp))
            if (insignia > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = (-10).dp).size(18.dp)
                        .background(Paleta.Ambar, CircleShape).border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (insignia > 9) "9+" else "$insignia", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
        Text(texto, fontSize = 12.sp, fontWeight = if (activo) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (activo) Paleta.NaranjaOscuro else Paleta.Texto3)
    }
}

/** Hoja «Más»: las demás operaciones del almacén. Las que aún no existen se ven, pero dicen «Pronto». */
@Composable
private fun HojaMas(e: EstadoInicio, alCelular: () -> Unit, alActualizar: () -> Unit, alSalir: () -> Unit, alCerrar: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp)
            .vidrio(RoundedCornerShape(32.dp), fuerte = true, sombra = 24.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(5.dp).background(Paleta.Tinta.copy(alpha = 0.18f), RoundedCornerShape(3.dp)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Más operaciones", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = alCerrar, modifier = Modifier.size(44.dp).background(Paleta.Tinta.copy(alpha = 0.06f), CircleShape)) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Paleta.Tinta, modifier = Modifier.size(18.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionMas(Icons.Filled.MoveDown, "Ingreso sin compra")
            OpcionMas(Icons.Filled.SwapHoriz, "Traslado entre obras")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionMas(Icons.Filled.FactCheck, "Conteo físico")
            OpcionMas(Icons.Filled.Lock, "Cerrar obra")
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).clickable(onClick = alCelular)
                .heightIn(min = 52.dp).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(22.dp))
            Text("Mi celular en esta obra", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
        }
        Text("OTROS RECURSOS", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em, color = Paleta.Texto3)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionMas(Icons.Filled.Search, "Catálogo", centrada = true)
            OpcionMas(Icons.Filled.Build, "Herramientas", centrada = true)
            OpcionMas(Icons.Filled.Groups, "Personal", centrada = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${e.insumosEnCelular} insumos del catálogo guardados para buscar sin señal",
                fontSize = 13.sp, color = Paleta.Texto2, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = alActualizar, enabled = !e.actualizandoCatalogo) {
                if (e.actualizandoCatalogo) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Paleta.Naranja)
                else Icon(Icons.Filled.Sync, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Actualizar", fontWeight = FontWeight.Bold, color = Paleta.Naranja)
            }
        }
        TextButton(onClick = alSalir, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Paleta.Texto3, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Cerrar sesión", fontWeight = FontWeight.SemiBold, color = Paleta.Texto3)
        }
    }
}

@Composable
private fun RowScope.OpcionMas(icono: ImageVector, texto: String, centrada: Boolean = false) {
    Column(
        Modifier.weight(1f).alpha(0.55f).background(Color.White, RoundedCornerShape(20.dp))
            .padding(horizontal = if (centrada) 8.dp else 14.dp, vertical = if (centrada) 12.dp else 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$texto, próximamente" },
        horizontalAlignment = if (centrada) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(if (centrada) 6.dp else 8.dp),
    ) {
        Icon(icono, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(24.dp))
        Text(texto, fontSize = if (centrada) 13.sp else 14.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
        Text("Pronto", fontSize = 12.sp, color = Paleta.Texto3)
    }
}
