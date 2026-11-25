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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import pe.sevens.sigro.dominio.AjusteHuella
import pe.sevens.sigro.dominio.Biometria
import pe.sevens.sigro.dominio.ClaveReciente
import pe.sevens.sigro.dominio.CuentaConHuella
import pe.sevens.sigro.dominio.ResultadoSecreto
import pe.sevens.sigro.dominio.DisponibilidadHuella
import pe.sevens.sigro.dominio.MINUTOS_BLOQUEO
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.ReglaCandado
import pe.sevens.sigro.dominio.RepositorioCandado
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.ResultadoHuella
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.plataforma.ManejarAtras
import pe.sevens.sigro.plataforma.Plataforma
import pe.sevens.sigro.recursos.Res
import pe.sevens.sigro.recursos.sevens_logo_blanco
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.Ticket
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.tema.Paleta

/* Entrar con huella (rediseño v4, 25/11): candado de la app, oferta al entrar y Perfil › Seguridad. */

data class EstadoCandado(
    val usuarioId: Int? = null,
    val ajuste: AjusteHuella = AjusteHuella(),
    val bloqueada: Boolean = false,
    /** Ya se leyó el ajuste guardado (antes no se decide nada, para no mostrar el inicio un instante). */
    val listo: Boolean = false,
    val mensaje: String? = null,
    /** Esta cuenta puede entrar con la huella aunque cierre sesión (su contraseña está cifrada con la huella). */
    val entraConHuella: Boolean = false,
)

class CandadoViewModel(
    private val sesiones: RepositorioSesion,
    private val repo: RepositorioCandado,
    /** Lo que espera envío (salidas + cola general); en Koin: [pendientesDe]. */
    pendientes: Flow<Int> = flowOf(0),
    private val reloj: () -> Long = { Plataforma.ahoraMs() },
    /** La contraseña recién escrita (solo en memoria): se cifra con la huella si la activa al entrar. */
    private val clave: ClaveReciente = ClaveReciente(reloj),
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoCandado())
    val estado = _e.asStateFlow()

    /** Lo que espera envío: se ve en el candado (se sigue enviando aunque la app esté bloqueada). */
    val porEnviar = pendientes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Último toque en la pantalla (en memoria; se guarda al salir de la app). */
    private var ultimoUso: Long? = null
    private var sesion: Sesion? = null

    init {
        viewModelScope.launch {
            var primera = true
            sesiones.sesion.distinctUntilChanged { a, b -> a?.usuarioId == b?.usuarioId }.collect { s ->
                sesion = s
                val id = s?.usuarioId
                if (id == null) {
                    _e.value = EstadoCandado(listo = true)
                } else {
                    val ajuste = repo.ajuste(id)
                    val bloquear = if (primera) {
                        // al abrir la app con la sesión guardada: el candado decide con el último uso guardado
                        ultimoUso = repo.ultimoUso()
                        ReglaCandado.vencido(ajuste, ultimoUso, reloj())
                    } else {
                        ultimoUso = reloj(); false // acaba de entrar con su contraseña: no se le vuelve a pedir
                    }
                    val conCuenta = runCatching { repo.cuenta()?.usuarioId == id }.getOrDefault(false)
                    _e.value = EstadoCandado(id, ajuste, bloqueada = bloquear, listo = true, entraConHuella = conCuenta)
                }
                primera = false
            }
        }
    }

    /** Un toque en la pantalla: reinicia la cuenta de los minutos sin usarla. */
    fun tocar() { if (!_e.value.bloqueada) ultimoUso = reloj() }

    /** Cada tanto, y al volver a la app: ¿pasó el tiempo elegido? */
    fun revisar() {
        val s = _e.value
        if (s.usuarioId != null && !s.bloqueada && ReglaCandado.vencido(s.ajuste, ultimoUso, reloj())) _e.update { it.copy(bloqueada = true) }
    }

    /** La app pasa a segundo plano: se guarda la hora para que el candado valga aunque Android cierre la app. */
    fun alSalir() {
        if (_e.value.bloqueada) return
        val ahora = reloj()
        ultimoUso = ahora
        viewModelScope.launch { runCatching { repo.guardarUltimoUso(ahora) } }
    }

    /** Lo que respondió la huella al desbloquear. */
    fun alDesbloquear(r: ResultadoHuella) = when (r) {
        ResultadoHuella.Ok -> { _e.update { it.copy(bloqueada = false, mensaje = null) }; alSalir() }
        ResultadoHuella.Cancelada -> Unit
        is ResultadoHuella.Fallo -> _e.update { it.copy(mensaje = r.mensaje) }
        ResultadoHuella.HuellaNueva -> {
            // alguien agregó una huella al celular: ya no se confía; se apaga y se pide la contraseña
            guardarAjuste { it.copy(activa = false, ofrecida = false) }
            olvidarCuentaPropia()
            _e.update { it.copy(bloqueada = false, mensaje = MENSAJE_HUELLA_NUEVA) }
            cerrarSesion()
        }
    }

    /** Lo que respondió la huella al activarla (desde la oferta o desde Perfil). */
    fun alActivar(r: ResultadoHuella) = when (r) {
        ResultadoHuella.Ok -> { guardarAjuste { it.copy(activa = true, ofrecida = true) }; _e.update { it.copy(mensaje = "Listo: la próxima vez entras con tu huella.") } }
        ResultadoHuella.Cancelada -> Unit
        is ResultadoHuella.Fallo -> _e.update { it.copy(mensaje = r.mensaje) }
        ResultadoHuella.HuellaNueva -> _e.update { it.copy(mensaje = "La huella del celular cambió. Vuelve a activarla.") }
    }

    /**
     * Activa la huella. Si la contraseña recién escrita sigue en memoria y el celular puede (Android 11+), en el mismo
     * diálogo se cifra con la huella: así podrá entrar con ella aunque cierre sesión. Si no, queda solo el candado.
     */
    suspend fun activar(bio: Biometria) {
        val s = sesion ?: return
        val reciente = clave.tomar(s.usuario)
        if (reciente == null || !bio.guardaClaves()) return alActivar(bio.verificar("Activa tu huella en SIGRO", s.nombre))
        when (val r = bio.cifrar("Activa tu huella en SIGRO", s.nombre, reciente)) {
            is ResultadoSecreto.Ok -> {
                runCatching { repo.guardarCuenta(CuentaConHuella(s.usuarioId, s.usuario, s.nombre, s.servidor, r.valor)) }
                _e.update { it.copy(entraConHuella = true) }
                alActivar(ResultadoHuella.Ok)
            }
            ResultadoSecreto.Cancelada -> clave.guardar(s.usuario, reciente) // la devuelve: puede intentarlo otra vez
            is ResultadoSecreto.Fallo -> alActivar(ResultadoHuella.Fallo(r.mensaje))
            ResultadoSecreto.HuellaNueva -> alActivar(ResultadoHuella.HuellaNueva)
            ResultadoSecreto.NoSoportado -> alActivar(bio.verificar("Activa tu huella en SIGRO", s.nombre))
        }
    }

    /** ¿Tiene la contraseña recién escrita? Si no, Perfil la pide una vez para poder entrar con huella después. */
    fun hayClaveReciente(): Boolean = sesion?.let { clave.hay(it.usuario) } ?: false
    fun recordarClave(c: String) { sesion?.let { if (c.isNotEmpty()) clave.guardar(it.usuario, c) } }

    fun ahoraNo() { clave.olvidar(); guardarAjuste { it.copy(ofrecida = true) } }
    fun desactivar() {
        guardarAjuste { it.copy(activa = false, ofrecida = true) }
        olvidarCuentaPropia()
    }
    fun minutos(m: Int) = guardarAjuste { it.copy(minutos = ReglaCandado.minutosValidos(m)) }
    fun bloquearAhora() { if (_e.value.ajuste.activa) _e.update { it.copy(bloqueada = true) } }
    fun mensajeVisto() = _e.update { it.copy(mensaje = null) }

    /**
     * Cierra la sesión (la cola NO se borra: se envía cuando vuelva a entrar su dueño). Con [olvidarHuella] («no soy
     * yo») también se apaga la huella y se borra la contraseña cifrada: quien tenga el celular no puede entrar como él.
     */
    fun cerrarSesion(olvidarHuella: Boolean = false) {
        if (olvidarHuella) { guardarAjuste { it.copy(activa = false, ofrecida = false) }; olvidarCuentaPropia() }
        clave.olvidar()
        _e.update { it.copy(bloqueada = false) }
        viewModelScope.launch { sesiones.cerrarSesion() }
    }

    /**
     * Desde el candado, «entrar con mi contraseña»: se cierra la sesión (la cola queda), la huella se conserva y la
     * pantalla de entrar abre el formulario con su usuario, sin pedir huella.
     */
    fun entrarConContrasena() {
        clave.preferirContrasena()
        cerrarSesion(olvidarHuella = false)
    }

    /** Borra la contraseña cifrada si es de esta persona (la de otra cuenta en el mismo celular no se toca). */
    private fun olvidarCuentaPropia() {
        val id = _e.value.usuarioId ?: return
        _e.update { it.copy(entraConHuella = false) }
        viewModelScope.launch { runCatching { if (repo.cuenta()?.usuarioId == id) repo.olvidarCuenta() } }
    }

    private fun guardarAjuste(cambio: (AjusteHuella) -> AjusteHuella) {
        val id = _e.value.usuarioId ?: return
        val nuevo = cambio(_e.value.ajuste)
        _e.update { it.copy(ajuste = nuevo) }
        viewModelScope.launch { runCatching { repo.guardar(id, nuevo) } }
    }

    companion object {
        const val MENSAJE_HUELLA_NUEVA = "Se agregó una huella nueva en el celular. Por seguridad, entra con tu contraseña y vuelve a activar la huella."
        /** Cada cuánto se revisa, con la app abierta, si ya pasó el tiempo sin usarla. */
        const val REVISAR_CADA_MS = 20_000L
    }
}

/** Lo que espera envío en el celular: salidas y la cola general (asistencia, ingresos…). */
fun pendientesDe(salidas: RepositorioSalidas, registros: RepositorioRegistros): Flow<Int> =
    combine(salidas.pendientes, registros.pendientes) { a, b -> a + b }

/** Mide el uso: cada toque en cualquier parte de la app reinicia la cuenta (no consume el toque). */
fun Modifier.medirUso(vm: CandadoViewModel): Modifier = pointerInput(vm) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            vm.tocar()
        }
    }
}

/**
 * Lo que va encima de toda la app: el candado si está bloqueada y, una sola vez tras entrar, la oferta de la huella.
 * [puedeOfrecer]: ya está dentro de una obra y no se está entrando a otra.
 */
@Composable
fun BoxScope.CapaCandado(vm: CandadoViewModel, sesion: Sesion?, obra: ObraAsignada?, puedeOfrecer: Boolean, biometria: Biometria) {
    val e by vm.estado.collectAsState()
    val porEnviar by vm.porEnviar.collectAsState()
    val alcance = rememberCoroutineScope()
    LaunchedEffect(vm) { while (true) { delay(CandadoViewModel.REVISAR_CADA_MS); vm.revisar() } }

    val disponible = remember(e.usuarioId) { biometria.disponibilidad() }
    // ya tenía la huella solo como candado y acaba de escribir su contraseña: se le ofrece guardar el acceso
    val completar = !e.entraConHuella && biometria.guardaClaves() && vm.hayClaveReciente()
    val ofrecer = e.listo && puedeOfrecer && !e.bloqueada && sesion != null && ReglaCandado.ofrecer(e.ajuste, disponible, completar)
    AnimatedVisibility(ofrecer, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color(0x8C0A0A0A)).clickable(remember { MutableInteractionSource() }, null) { vm.ahoraNo() })
    }
    AnimatedVisibility(ofrecer, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        HojaActivarHuella(
            alActivar = { alcance.launch { vm.activar(biometria) } },
            alAhoraNo = vm::ahoraNo,
        )
    }

    // mientras se lee el ajuste guardado, con sesión abierta se tapa todo: el inicio no se asoma ni un instante
    if (sesion != null && !e.listo) Box(Modifier.fillMaxSize().background(Paleta.Noche))
    if (e.bloqueada && sesion != null) {
        PantallaBloqueada(
            nombre = sesion.nombre, rol = obra?.rolLegible, obraNombre = obra?.nombre, porEnviar = porEnviar,
            minutos = e.ajuste.minutos, disponible = disponible, mensaje = e.mensaje,
            alDesbloquear = { alcance.launch { vm.alDesbloquear(biometria.verificar("Entra a SIGRO", listOfNotNull(sesion.nombre, obra?.nombre).joinToString(" · "))) } },
            alCerrarSesion = { biometria.olvidar(); biometria.olvidarClave(); vm.cerrarSesion(olvidarHuella = true) },
            alContrasena = vm::entrarConContrasena,
        )
    }
}

/** La app bloqueada: quién es, la huella grande, la cola que sigue enviándose y «no soy yo». */
@Composable
fun PantallaBloqueada(
    nombre: String, rol: String?, obraNombre: String?, porEnviar: Int, minutos: Int,
    disponible: DisponibilidadHuella, mensaje: String?, alDesbloquear: () -> Unit, alCerrarSesion: () -> Unit,
    /** No puede poner la huella: sale a «Entrar» con su usuario puesto para escribir la contraseña (su huella queda). */
    alContrasena: () -> Unit = {},
    pedirAlAbrir: Boolean = true,
) {
    ManejarAtras(activo = true) {} // «atrás» no deja ver lo de debajo
    // al aparecer se abre el diálogo de la huella solo (una vez): es lo que la persona viene a hacer
    LaunchedEffect(Unit) { if (pedirAlAbrir && disponible == DisponibilidadHuella.DISPONIBLE) alDesbloquear() }
    val amarillo = Paleta.TicketAmarillo
    Box(
        Modifier.fillMaxSize().background(Paleta.Noche)
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-50).dp).size(300.dp)
                .background(Brush.radialGradient(listOf(amarillo.copy(alpha = 0.22f), Color.Transparent)), CircleShape),
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(Res.drawable.sevens_logo_blanco), contentDescription = "SEVEN'S Ingenieros SAC", modifier = Modifier.height(38.dp))
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFD2D3D3), modifier = Modifier.size(14.dp))
                    Text("Bloqueada", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD2D3D3))
                }
            }
            Box(Modifier.padding(top = 30.dp).size(64.dp).background(amarillo, CircleShape), contentAlignment = Alignment.Center) {
                Text(iniciales(nombre), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Paleta.SobreTicket)
            }
            Text("Hola de nuevo,", fontSize = 14.sp, color = Color(0xFFB4B5B5), modifier = Modifier.padding(top = 12.dp))
            Text(nombre, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
            if (obraNombre != null) {
                Text(
                    buildAnnotatedString { rol?.let { append("$it · ") }; withStyle(SpanStyle(color = amarillo)) { append(obraNombre) } },
                    fontSize = 13.sp, color = Color(0xFF8F9090), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                )
            }
            val puede = disponible == DisponibilidadHuella.DISPONIBLE
            Box(
                Modifier.padding(top = 22.dp).size(172.dp).clip(CircleShape)
                    .clickable(enabled = puede, role = Role.Button, onClickLabel = "Entrar con tu huella", onClick = alDesbloquear),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(amarillo.copy(alpha = 0.28f), Color.Transparent)), CircleShape))
                Box(Modifier.size(136.dp).border(2.dp, amarillo.copy(alpha = 0.35f), CircleShape))
                Box(Modifier.size(104.dp).background(if (puede) amarillo else Color(0xFF353636), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = if (puede) Paleta.SobreTicket else Color(0xFF8F9090), modifier = Modifier.size(56.dp))
                }
            }
            Text(
                if (puede) "Toca para entrar con tu huella" else "Este celular ya no tiene huella ni bloqueo de pantalla",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                if (puede) "Se bloqueó tras $minutos minutos sin usarla, para que nadie registre a tu nombre." else "Entra con tu contraseña: cierra la sesión y vuelve a entrar.",
                fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xFF8F9090), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 290.dp).padding(top = 6.dp),
            )
            mensaje?.let {
                Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9A9A), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (porEnviar > 0) {
                Row(
                    Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paleta.TicketMenta.copy(alpha = 0.1f))
                        .border(1.dp, Paleta.TicketMenta.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(30.dp).background(Paleta.TicketMenta, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Paleta.SobreTicket, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(if (porEnviar == 1) "1 registro por enviar." else "$porEnviar registros por enviar.") }
                            append(" Se siguen enviando aunque esté bloqueada.")
                        },
                        fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFFD2D3D3),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            if (puede) BotonNegro("Usar mi huella o el PIN del celular", alDesbloquear, icono = Icons.Filled.Fingerprint, colorIcono = Paleta.SobreTicket)
            // si no puede poner la huella (guantes, dedo herido): su contraseña. El fondo es negro en los dos modos.
            androidx.compose.material3.OutlinedButton(
                onClick = alContrasena, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A4B4B)),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Entrar con mi contraseña", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "No soy ${nombre.substringBefore(' ')} · cerrar sesión", fontSize = 13.sp, color = Color(0xFFA9AAAA),
                modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = alCerrarSesion).padding(10.dp),
            )
        }
    }
}

/** La oferta, una sola vez tras entrar: ticket amarillo, tres razones y «Activar» / «Ahora no». */
@Composable
fun HojaActivarHuella(alActivar: () -> Unit, alAhoraNo: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(MaterialTheme.colorScheme.background)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp).background(Paleta.Tinta.copy(alpha = 0.18f), RoundedCornerShape(3.dp)))
        Ticket(Paleta.TicketAmarillo, Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(76.dp).background(Paleta.SobreTicket, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Paleta.TicketAmarillo, modifier = Modifier.size(40.dp))
            }
            Column(Modifier.padding(18.dp).padding(end = 84.dp)) {
                Text("NUEVO", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em, color = Paleta.SobreTicket)
                Text("¿Entras con tu huella la próxima vez?", fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).em, color = Paleta.SobreTicket, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
            }
        }
        Razon(Icons.Filled.VerifiedUser, "Si dejas el celular, se bloquea solo a los 15 minutos. Nadie registra a tu nombre.")
        Razon(Icons.Filled.Fingerprint, "Sin escribir la contraseña con guantes. Funciona sin señal.")
        Razon(Icons.Filled.Lock, "Tu huella se queda en el celular: SIGRO nunca la ve.")
        BotonNegro("Activar huella", alActivar, icono = Icons.Filled.Fingerprint, colorIcono = Paleta.SobreTicket)
        BotonNegro("Ahora no", alAhoraNo, contorno = true)
        Text("Lo cambias cuando quieras en Perfil.", fontSize = 11.sp, color = Paleta.Texto3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Razon(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(icono, contentDescription = null, tint = Paleta.EnviadoTexto, modifier = Modifier.size(20.dp))
        Text(texto, fontSize = 13.sp, lineHeight = 19.sp, color = Paleta.Texto2)
    }
}

/** Perfil › Seguridad: huella sí/no, a los cuántos minutos se bloquea y «Bloquear ahora». */
@Composable
fun SeccionSeguridad(vm: CandadoViewModel, biometria: Biometria) {
    val e by vm.estado.collectAsState()
    val alcance = rememberCoroutineScope()
    val disponible = remember { biometria.disponibilidad() }
    var activando by remember { mutableStateOf(false) }
    // desde Perfil la contraseña ya no está en memoria: se pide una vez para poder entrar con huella tras cerrar sesión
    var pidiendoClave by remember { mutableStateOf(false) }
    val activar = { activando = true; alcance.launch { vm.activar(biometria); activando = false }; Unit }
    if (pidiendoClave) {
        DialogoClave(
            alConfirmar = { c -> pidiendoClave = false; vm.recordarClave(c); activar() },
            alOmitir = { pidiendoClave = false; activar() },
            alCerrar = { pidiendoClave = false },
        )
    }
    TarjetaVidrio {
        Text("Seguridad", fontSize = 13.sp, color = Paleta.Texto3)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(Paleta.NaranjaClaro, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Entrar con huella", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
                Text(
                    when (disponible) {
                        DisponibilidadHuella.DISPONIBLE -> "O con el PIN o patrón del celular"
                        DisponibilidadHuella.SIN_REGISTRAR -> "Configura una huella o un bloqueo de pantalla en el celular"
                        DisponibilidadHuella.NO_DISPONIBLE -> "Este celular no tiene huella ni bloqueo de pantalla"
                    },
                    fontSize = 12.sp, lineHeight = 16.sp, color = Paleta.Texto3,
                )
            }
            Switch(
                checked = e.ajuste.activa,
                enabled = disponible == DisponibilidadHuella.DISPONIBLE && !activando,
                onCheckedChange = { si ->
                    if (si) {
                        if (biometria.guardaClaves() && !vm.hayClaveReciente()) pidiendoClave = true else activar()
                    } else { biometria.olvidar(); biometria.olvidarClave(); vm.desactivar() }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Paleta.TicketAmarillo, checkedThumbColor = Paleta.SobreTicket),
            )
        }
        if (e.ajuste.activa) {
            Text(
                if (e.entraConHuella) "✓ También entras con tu huella aunque cierres sesión."
                else if (!biometria.guardaClaves()) "En este celular (Android 10 o menos) la huella solo desbloquea la app: tras cerrar sesión se entra con contraseña."
                else "Desbloquea la app. Para entrar con huella tras cerrar sesión, apágala y vuelve a activarla con tu contraseña.",
                fontSize = 12.sp, lineHeight = 17.sp, color = if (e.entraConHuella) Paleta.EnviadoTexto else Paleta.Texto3,
            )
            Text("Bloquear tras este tiempo sin usarla", fontSize = 13.sp, color = Paleta.Texto2, modifier = Modifier.padding(top = 4.dp))
            Opciones(MINUTOS_BLOQUEO, e.ajuste.minutos, { "$it min" }, vm::minutos, colorActivo = Paleta.Naranja)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = "Bloquear ahora", onClick = vm::bloquearAhora)
                    .heightIn(min = 52.dp).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).background(Paleta.SuperficieSuave, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Bloquear ahora", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
                    Text("Antes de prestar el celular", fontSize = 12.sp, color = Paleta.Texto3)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Paleta.Gris)
            }
            Text(
                "Si alguien agrega una huella nueva al celular, SIGRO pide la contraseña otra vez antes de volver a usar la huella.",
                fontSize = 12.sp, lineHeight = 17.sp, color = Paleta.Texto3,
            )
        }
        e.mensaje?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Texto2, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

/** Perfil › activar huella: la contraseña, una sola vez, para cifrarla con la huella. Se puede omitir (solo candado). */
@Composable
private fun DialogoClave(alConfirmar: (String) -> Unit, alOmitir: () -> Unit, alCerrar: () -> Unit) {
    var clave by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = alCerrar,
        icon = { Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Paleta.Naranja) },
        title = { Text("Escribe tu contraseña una vez") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Se guarda cifrada con tu huella: así entras con la huella aunque cierres sesión. SIGRO no puede leerla sin tu huella.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = Paleta.Texto2,
                )
                pe.sevens.sigro.ui.componentes.CampoVidrio(
                    clave, { clave = it }, "Contraseña", teclado = androidx.compose.ui.text.input.KeyboardType.Password,
                    transformacion = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { alConfirmar(clave) }, enabled = clave.isNotEmpty()) {
                Text("Seguir con la huella", fontWeight = FontWeight.Bold, color = Paleta.Naranja)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = alOmitir) { Text("Solo desbloquear", color = Paleta.Texto2) }
        },
    )
}
