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
import pe.sevens.sigro.recursos.Res
import pe.sevens.sigro.recursos.sevens_logo
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.tema.Apariencia
import pe.sevens.sigro.ui.tema.TemaSigro

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

/**
 * Pantalla de entrada con componentes estándar de Material 3:
 * Surface (fondo) → Card (formulario) → OutlinedTextField (vía CampoVidrio) → Button (vía BotonNegro).
 * No guarda estado propio salvo «ver clave» y «ver servidor»: lo demás vive en el ViewModel (MVVM).
 */
@Composable
fun PantallaEntrar(vm: EntrarViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    var verClave by remember { mutableStateOf(false) }
    var verServidor by remember { mutableStateOf(false) }

    TemaSigro(oscuro = Apariencia.oscuro) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().imePadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Image(
                    painterResource(Res.drawable.sevens_logo), contentDescription = "SEVEN'S Ingenieros SAC",
                    modifier = Modifier.padding(top = 32.dp).height(96.dp).widthIn(max = 260.dp),
                )
                Text(
                    "Almacén de obra", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Card(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Ingresa para registrar las entradas y salidas de material de tu obra",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        BotonNegro("Entrar", vm::entrar, cargando = e.cargando)
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
