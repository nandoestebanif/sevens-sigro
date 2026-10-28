package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.plataforma.Plataforma
import pe.sevens.sigro.ui.componentes.AvisoError
import pe.sevens.sigro.ui.componentes.BarraSuperior
import pe.sevens.sigro.ui.componentes.BotonGrande
import pe.sevens.sigro.ui.componentes.ChipEstado
import pe.sevens.sigro.ui.componentes.Vacio
import pe.sevens.sigro.ui.tema.EstiloCifra

/* HU-37 / HU-38 · lo registrado en esta obra y su estado de envío. */

class PendientesViewModel(private val salidas: RepositorioSalidas) : ViewModel() {
    private val _enviando = MutableStateFlow(false)
    val enviando = _enviando.asStateFlow()

    fun de(obraId: Int) = salidas.salidasDeObra(obraId)

    fun enviarAhora() {
        _enviando.value = true
        viewModelScope.launch { salidas.sincronizar(); _enviando.value = false }
    }
}

@Composable
fun PantallaPendientes(obraId: Int, alVolver: () -> Unit, vm: PendientesViewModel = koinViewModel()) {
    val flujo = remember(obraId) { vm.de(obraId) }
    val lista by flujo.collectAsState(initial = emptyList())
    val enviando by vm.enviando.collectAsState()
    val porEnviar = lista.count { it.estado == EstadoEnvio.PENDIENTE }

    Scaffold(
        topBar = { BarraSuperior("Mis registros", if (porEnviar > 0) "$porEnviar por enviar" else "Todo enviado", alVolver = alVolver) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (porEnviar > 0) {
                item { BotonGrande("Enviar ahora", vm::enviarAhora, icono = Icons.Filled.CloudUpload, cargando = enviando) }
            }
            if (lista.isEmpty()) {
                item { Vacio(Icons.Filled.Inbox, "Aún no registraste nada", "Las salidas que registres en esta obra aparecerán aquí.") }
            }
            items(lista, key = { it.uuidCliente }) { FilaSalida(it) }
        }
    }
}

@Composable
private fun FilaSalida(s: SalidaRegistrada) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.insumo.descripcion, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                ChipEstado(s.estado)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.cantidad} ${s.insumo.unidad}", style = EstiloCifra, modifier = Modifier.weight(1f))
                Text(Plataforma.formatearHora(s.horaDispositivoMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${s.motivo} · retira ${s.quienRetira}" + (s.frente?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // HU-38: un rechazo queda con su motivo; una nota explica por qué sigue en cola.
            s.motivoRechazo?.let { AvisoError("Rechazada: $it") }
            if (s.estado == EstadoEnvio.PENDIENTE && s.nota != null) {
                Text("${s.nota} (intentos: ${s.intentos})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
