package pe.sevens.sigro.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.ui.componentes.AvisoError
import pe.sevens.sigro.ui.componentes.BotonGrande
import pe.sevens.sigro.ui.componentes.Campo

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

@Composable
fun PantallaEntrar(vm: EntrarViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    var verClave by remember { mutableStateOf(false) }
    var verServidor by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .imePadding().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
            }
            Column {
                Text("SIGRO", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Almacén de obra · SEVENS Ingenieros",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            Campo(e.usuario, vm::usuario, "Usuario")
            Campo(
                e.clave, vm::clave, "Clave",
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

            AnimatedVisibility(e.error != null) { AvisoError(e.error.orEmpty()) }

            BotonGrande("Entrar", vm::entrar, icono = Icons.AutoMirrored.Filled.Login, cargando = e.cargando)

            // El servidor se cambia pocas veces (desarrollo → pruebas): queda escondido pero a mano.
            TextButton(onClick = { verServidor = !verServidor }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Servidor: ${e.servidor.removePrefix("http://").removePrefix("https://")}")
                }
            }
            AnimatedVisibility(verServidor) {
                Campo(
                    e.servidor, vm::servidor, "Dirección del servidor",
                    teclado = KeyboardType.Uri,
                    ayuda = "Te la da el administrador del sistema",
                )
            }
        }
    }
}
