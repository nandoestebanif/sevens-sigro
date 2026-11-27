package pe.sevens.sigro.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.Biometria
import pe.sevens.sigro.dominio.ClaveReciente
import pe.sevens.sigro.dominio.CuentaConHuella
import pe.sevens.sigro.dominio.RepositorioCandado
import pe.sevens.sigro.dominio.ReglaCandado
import pe.sevens.sigro.dominio.ResultadoSecreto
import pe.sevens.sigro.plataforma.rememberBiometria
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import pe.sevens.sigro.recursos.Res
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.tema.Apariencia
import pe.sevens.sigro.ui.tema.TemaSigro
import pe.sevens.sigro.recursos.sevens_logo_blanco
import pe.sevens.sigro.ui.tema.Paleta
import pe.sevens.sigro.ui.componentes.Ilustracion
import pe.sevens.sigro.ui.componentes.DibujoObra
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background

/* HU-12 · Iniciar sesión. */

data class EstadoEntrar(
    val servidor: String = "",
    val usuario: String = "",
    val clave: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    /** La cuenta que entra con huella en este celular (si la hay): se ofrece «Entrar como … con tu huella». */
    val cuentaHuella: CuentaConHuella? = null,
    /** Eligió «Entrar con otra cuenta»: se ve el formulario aunque haya cuenta con huella. */
    val conFormulario: Boolean = false,
)

class EntrarViewModel(
    private val sesiones: RepositorioSesion,
    private val candado: RepositorioCandado? = null,
    private val claveReciente: ClaveReciente? = null,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoEntrar())
    val estado = _estado.asStateFlow()

    init {
        viewModelScope.launch { _estado.update { it.copy(servidor = sesiones.servidorGuardado()) } }
        viewModelScope.launch {
            val c = runCatching { candado?.cuenta() }.getOrNull()
            // desde el candado pidió «entrar con mi contraseña»: formulario directo, con su usuario puesto
            val conContrasena = claveReciente?.tomarPreferirContrasena() == true
            _estado.update { it.copy(cuentaHuella = c, conFormulario = conContrasena, usuario = if (conContrasena) c?.usuario ?: it.usuario else it.usuario) }
        }
    }

    /** «No soy …»: el formulario vacío, para otra cuenta. */
    fun otraCuenta() = _estado.update { it.copy(conFormulario = true, usuario = "", clave = "", error = null) }

    /** No puede poner la huella (guantes, dedo herido, sensor sucio): su usuario puesto y que escriba la contraseña. */
    fun conContrasena() = _estado.update { s -> s.copy(conFormulario = true, usuario = s.cuentaHuella?.usuario ?: s.usuario, clave = "", error = null) }
    fun volverAHuella() = _estado.update { it.copy(conFormulario = false, error = null) }

    /**
     * Entrar con la huella: abre la contraseña cifrada y entra como siempre (el servidor la verifica). Si la contraseña
     * cambió o se agregó una huella al celular, se olvida el acceso con huella y se pide la contraseña escrita.
     */
    suspend fun entrarConHuella(bio: Biometria) {
        val c = _estado.value.cuentaHuella ?: return
        when (val r = bio.descifrar("Entra a SIGRO", "${c.nombre} · @${c.usuario}", c.sobre)) {
            is ResultadoSecreto.Ok -> {
                _estado.update { it.copy(cargando = true, error = null) }
                try {
                    sesiones.iniciarSesion(c.servidor, c.usuario, r.valor)
                    claveReciente?.guardar(c.usuario, r.valor)
                    _estado.update { it.copy(cargando = false) }
                } catch (x: ErrorSigro) {
                    if (x is ErrorSigro.SesionVencida) {
                        // la contraseña cambió (en la web o el administrador la renovó): la guardada ya no sirve
                        olvidarHuella(bio, "Tu contraseña cambió. Escríbela una vez y vuelve a activar la huella.", c.usuario)
                    } else {
                        _estado.update { it.copy(cargando = false, error = x.message) } // sin señal: el acceso con huella queda
                    }
                }
            }
            ResultadoSecreto.Cancelada -> Unit
            is ResultadoSecreto.Fallo -> _estado.update { it.copy(error = r.mensaje) }
            ResultadoSecreto.HuellaNueva -> olvidarHuella(bio, "Se agregó una huella nueva en el celular. Por seguridad, entra con tu contraseña.", c.usuario)
            ResultadoSecreto.NoSoportado -> _estado.update { it.copy(conFormulario = true) }
        }
    }

    private suspend fun olvidarHuella(bio: Biometria, mensaje: String, usuario: String) {
        bio.olvidarClave()
        runCatching { candado?.olvidarCuenta() }
        _estado.update { it.copy(cargando = false, cuentaHuella = null, conFormulario = true, usuario = usuario, error = mensaje) }
    }

    private suspend fun olvidarHuellaAjena(usuario: String, bio: Biometria?) {
        val c = _estado.value.cuentaHuella ?: return
        if (!ReglaCandado.esOtraCuenta(c, usuario)) return
        bio?.olvidar(); bio?.olvidarClave()
        runCatching {
            candado?.olvidarCuenta()
            candado?.let { it.guardar(c.usuarioId, it.ajuste(c.usuarioId).copy(activa = false, ofrecida = false)) }
        }
        _estado.update { it.copy(cuentaHuella = null) }
    }

    fun servidor(v: String) = _estado.update { it.copy(servidor = v, error = null) }
    fun usuario(v: String) = _estado.update { it.copy(usuario = v, error = null) }
    fun clave(v: String) = _estado.update { it.copy(clave = v, error = null) }

    /**
     * Entrar con contraseña. Si entra una cuenta distinta de la que tenía la huella en este celular, la huella de la
     * otra se borra (regla 5: un celular, un responsable): así, al cerrar sesión, no aparece «Entrar como …» de otra
     * persona ni queda su contraseña cifrada en un celular que ya no usa.
     */
    fun entrar(bio: Biometria? = null) {
        val e = _estado.value
        if (e.usuario.isBlank() || e.clave.isBlank()) {
            _estado.update { it.copy(error = "Escribe tu usuario y tu clave.") }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                sesiones.iniciarSesion(e.servidor, e.usuario, e.clave)
                olvidarHuellaAjena(e.usuario, bio)
                // solo en memoria y por unos minutos: si activa la huella al entrar, se cifra con ella
                claveReciente?.guardar(e.usuario, e.clave)
                // La raíz cambia sola de pantalla al ver la sesión guardada.
                _estado.update { it.copy(cargando = false, clave = "") }
            } catch (x: ErrorSigro) {
                _estado.update { it.copy(cargando = false, error = x.message) }
            }
        }
    }
}

/**
 * Pantalla de entrada con componentes estándar de Material 3:
 * Surface (fondo) → Card (formulario) → OutlinedTextField (vía CampoVidrio) → Button (vía BotonNegro).
 * No guarda estado propio salvo «ver clave» y «ver servidor»: lo demás vive en el ViewModel (MVVM).
 */
@Composable
fun PantallaEntrar(vm: EntrarViewModel = koinViewModel(), biometria: Biometria = rememberBiometria()) {
    val e by vm.estado.collectAsState()
    val alcance = rememberCoroutineScope()
    val cuenta = e.cuentaHuella
    val conHuella = cuenta != null && !e.conFormulario && biometria.guardaClaves()
    var verClave by remember { mutableStateOf(false) }
    var verServidor by remember { mutableStateOf(false) }

    TemaSigro(oscuro = Apariencia.oscuro) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().imePadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Portada negra de la marca (como la web): logo blanco, título con remate amarillo y el camión
                Box(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(min = 300.dp)
                        .clip(RoundedCornerShape(30.dp)).background(Paleta.Noche),
                ) {
                    DibujoObra(Ilustracion.CAMION, Modifier.align(Alignment.BottomEnd).padding(end = 4.dp), ancho = 170.dp)
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            painterResource(Res.drawable.sevens_logo_blanco), contentDescription = "SEVEN'S Ingenieros SAC",
                            modifier = Modifier.height(58.dp),
                        )
                        Text(
                            buildAnnotatedString {
                                append("Almacén ")
                                withStyle(SpanStyle(color = Paleta.TicketAmarillo)) { append("de obra") }
                            },
                            fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                            modifier = Modifier.padding(top = 14.dp).semantics { heading() },
                        )
                        Text(
                            "Ingresa para registrar las entradas y salidas de material de tu obra",
                            fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFFD2D3D3), modifier = Modifier.widthIn(max = 260.dp),
                        )
                        Text(
                            "Funciona sin señal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Paleta.SobreTicket,
                            modifier = Modifier.padding(top = 36.dp).background(Paleta.TicketAmarillo, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                if (conHuella && cuenta != null) {
                    EntrarConHuella(
                        cuenta, e.cargando, e.error, alEntrar = { alcance.launch { vm.entrarConHuella(biometria) } },
                        alContrasena = vm::conContrasena, alOtraCuenta = vm::otraCuenta,
                    )
                    return@Column
                }
                Card(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CampoVidrio(
                            e.usuario, vm::usuario, "Usuario", marcador = "Tu usuario",
                            finalIcono = { Icon(Icons.Filled.Person, contentDescription = null) },
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
                                    )
                                }
                            },
                        )
                        // El error del servidor se anuncia al lector de pantalla (liveRegion).
                        AnimatedVisibility(e.error != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                            ) {
                                androidx.compose.foundation.layout.Row(
                                    Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = "Error")
                                    Text(e.error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        BotonNegro("Entrar", { vm.entrar(biometria) }, cargando = e.cargando)
                        if (cuenta != null && e.conFormulario && biometria.guardaClaves()) {
                            TextButton(onClick = vm::volverAHuella, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Paleta.Naranja)
                                Text("  Entrar como ${cuenta.primerNombre} con tu huella", fontWeight = FontWeight.SemiBold, color = Paleta.Naranja)
                            }
                        }
                        Text(
                            "¿No tienes usuario? Pídelo al administrador de la obra",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                TextButton(onClick = { verServidor = !verServidor }) {
                    Icon(Icons.Filled.Dns, contentDescription = null)
                    Text(
                        "  Servidor: ${e.servidor.removePrefix("http://").removePrefix("https://")} · ${if (verServidor) "listo" else "cambiar"}",
                    )
                }
            }
        }
    }
}

/**
 * «Entrar como Adiel con tu huella»: quién es, la huella grande y «otra cuenta». El diálogo de la huella se abre solo
 * cuando la persona toca la huella: si el celular lo tiene otra persona, no le salta la huella de alguien más.
 */
@Composable
private fun EntrarConHuella(
    c: CuentaConHuella, cargando: Boolean, error: String?, alEntrar: () -> Unit, alContrasena: () -> Unit, alOtraCuenta: () -> Unit,
) {
    Card(
        Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(60.dp).background(Paleta.TicketAmarillo, CircleShape), contentAlignment = Alignment.Center) {
                Text(iniciales(c.nombre), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Paleta.SobreTicket)
            }
            Text("Hola de nuevo,", fontSize = 14.sp, color = Paleta.Texto3)
            Text(c.nombre, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text("@${c.usuario}", fontSize = 13.sp, color = Paleta.Texto3)
            Box(
                Modifier.padding(top = 6.dp).size(120.dp).clip(CircleShape)
                    .clickable(enabled = !cargando, role = Role.Button, onClickLabel = "Entrar con tu huella", onClick = alEntrar),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Paleta.TicketAmarillo.copy(alpha = 0.3f), Color.Transparent)), CircleShape))
                Box(Modifier.size(88.dp).background(Paleta.TicketAmarillo, CircleShape), contentAlignment = Alignment.Center) {
                    if (cargando) CircularProgressIndicator(Modifier.size(34.dp), color = Paleta.SobreTicket, strokeWidth = 3.dp)
                    else Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Paleta.SobreTicket, modifier = Modifier.size(48.dp))
                }
            }
            error?.let {
                Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            BotonNegro("Entrar con tu huella", alEntrar, icono = Icons.Filled.Fingerprint, cargando = cargando)
            // si no puede poner la huella: su contraseña (o el PIN del celular, en el mismo diálogo de la huella)
            BotonNegro("Entrar con mi contraseña", alContrasena, icono = Icons.Filled.Key, contorno = true, habilitado = !cargando)
            TextButton(onClick = alOtraCuenta) {
                Text("No soy ${c.primerNombre} · entrar con otra cuenta", color = Paleta.Texto2)
            }
            Text(
                "Tu contraseña se guarda cifrada con tu huella: sin ella no se puede usar.",
                fontSize = 12.sp, color = Paleta.Texto3, textAlign = TextAlign.Center,
            )
        }
    }
}
