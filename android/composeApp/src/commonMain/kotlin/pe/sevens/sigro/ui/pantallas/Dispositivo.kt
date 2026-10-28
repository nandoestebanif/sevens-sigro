package pe.sevens.sigro.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoDispositivo
import pe.sevens.sigro.dominio.RepositorioDispositivo
import pe.sevens.sigro.ui.componentes.AvisoError
import pe.sevens.sigro.ui.componentes.AvisoInfo
import pe.sevens.sigro.ui.componentes.BarraSuperior
import pe.sevens.sigro.ui.componentes.BotonGrande

/* HU-15 · el celular pide ser el dispositivo autorizado de la obra. */

data class EstadoPantallaDispositivo(
    val dispositivo: EstadoDispositivo? = null,
    val cargando: Boolean = false,
    val error: String? = null,
)

class DispositivoViewModel(private val repo: RepositorioDispositivo) : ViewModel() {
    private val _e = MutableStateFlow(EstadoPantallaDispositivo())
    val estado = _e.asStateFlow()

    fun cargar(obraId: Int) = ejecutar { repo.estado(obraId) }
    fun solicitar(obraId: Int) = ejecutar { repo.solicitarAutorizacion(obraId) }

    private fun ejecutar(accion: suspend () -> EstadoDispositivo) {
        _e.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val d = accion()
                _e.update { it.copy(dispositivo = d, cargando = false) }
            } catch (x: ErrorSigro) {
                val id = runCatching { repo.identificador() }.getOrNull()
                _e.update { it.copy(cargando = false, error = x.message, dispositivo = it.dispositivo ?: id?.let { EstadoDispositivo(it, null) }) }
            }
        }
    }
}

@Composable
fun PantallaDispositivo(obraId: Int, alVolver: () -> Unit, vm: DispositivoViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }

    Scaffold(
        topBar = { BarraSuperior("Mi celular", "Autorización para registrar en esta obra", alVolver = alVolver) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Código de este celular", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(e.dispositivo?.identificador ?: "…", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                    Text("Estado: ${e.dispositivo?.estadoLegible ?: "consultando…"}", style = MaterialTheme.typography.titleMedium)
                }
            }
            AnimatedVisibility(e.error != null) { AvisoError(e.error.orEmpty()) }

            val estado = e.dispositivo?.estado
            if (estado == null || estado == "revocado") {
                BotonGrande("Pedir autorización", { vm.solicitar(obraId) }, icono = Icons.Filled.VerifiedUser, cargando = e.cargando)
            }
            AvisoInfo(
                "El administrador autoriza el celular desde la web. Un registro que llegue de un celular no autorizado " +
                    "lo rechaza el servidor y queda en «Mis registros» con el motivo.",
            )
        }
    }
}
