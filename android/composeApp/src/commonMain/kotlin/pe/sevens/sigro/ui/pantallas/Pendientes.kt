package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.RegistroLocal
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.SalidaRegistrada
import pe.sevens.sigro.plataforma.Plataforma
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.ChipEstado
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.planoRegistros
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 2.5 · Mis registros (lienzo Registros.dc.html, HU-37 / HU-38): todo lo registrado en esta obra desde
 * el celular (salidas, asistencia y almacén) con su estado de envío. Un rechazo se ve con su motivo;
 * lo que sigue en cola dice por qué. Lo de almacén espera a su servicio y lo dice.
 */

/** Una fila de la lista, venga de la cola de salidas o de la cola general. */
data class FilaRegistro(
    val clave: String,
    val tipo: String,
    val titulo: String,
    val cantidad: String?,
    val detalle: String,
    val horaMs: Long,
    val estado: EstadoEnvio,
    val motivoRechazo: String?,
    val nota: String?,
    val intentos: Int,
)

/** Une las dos colas, lo más reciente primero. No suma ni calcula nada de stock (regla 2). */
fun unirRegistros(salidas: List<SalidaRegistrada>, registros: List<RegistroLocal>): List<FilaRegistro> =
    (salidas.map {
        FilaRegistro(
            it.uuidCliente, "Salida", it.insumo.descripcion, "${it.cantidad} ${it.insumo.unidad}",
            "${it.motivo} · retira ${it.quienRetira}" + (it.frente?.let { f -> " · $f" } ?: ""),
            it.horaDispositivoMs, it.estado, it.motivoRechazo, it.nota, it.intentos,
        )
    } + registros.map {
        FilaRegistro(it.uuidCliente, it.tipo.legible, it.titulo, null, it.detalle, it.horaDispositivoMs, it.estado, it.motivoRechazo, it.nota, it.intentos)
    }).sortedByDescending { it.horaMs }

class PendientesViewModel(private val salidas: RepositorioSalidas, private val registros: RepositorioRegistros) : ViewModel() {
    private val _enviando = MutableStateFlow(false)
    val enviando = _enviando.asStateFlow()

    fun de(obraId: Int): Flow<List<FilaRegistro>> = combine(salidas.salidasDeObra(obraId), registros.deObra(obraId), ::unirRegistros)

    /** HU-38: envía las dos colas sin esperar al reintento. Lo de almacén no se envía todavía. */
    fun enviarAhora() {
        _enviando.value = true
        viewModelScope.launch {
            runCatching { salidas.sincronizar() }
            runCatching { registros.sincronizar() }
            _enviando.value = false
        }
    }
}

@Composable
fun PantallaPendientes(obraId: Int, alVolver: () -> Unit, vm: PendientesViewModel = koinViewModel()) {
    val flujo = remember(obraId) { vm.de(obraId) }
    val lista by flujo.collectAsState(initial = emptyList())
    val enviando by vm.enviando.collectAsState()
    val porEnviar = lista.count { it.estado == EstadoEnvio.PENDIENTE }

    PantallaVidrio(
        "Mis registros", plano = { planoRegistros(it) }, alVolver = alVolver,
        subtitulo = if (porEnviar > 0) "$porEnviar por enviar" else "Todo enviado",
    ) {
        if (porEnviar > 0) BotonNegro("Enviar ahora", vm::enviarAhora, icono = Icons.Filled.CloudUpload, colorIcono = Paleta.NaranjaVivo, cargando = enviando)
        if (lista.isEmpty()) {
            TarjetaVidrio {
                Text("Aún no registraste nada en esta obra", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                Text("Las salidas, la asistencia y los registros de almacén que guardes aparecen aquí con su estado de envío.", fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.Texto2)
            }
        }
        lista.forEach { Fila(it) }
    }
}

@Composable
private fun Fila(f: FilaRegistro) {
    TarjetaVidrio(Modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(f.tipo.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = Paleta.Naranja)
                Text(f.titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
            }
            ChipEstado(f.estado)
        }
        if (f.cantidad != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(f.cantidad, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta, modifier = Modifier.weight(1f))
                Text(Plataforma.formatearHora(f.horaMs), fontSize = 13.sp, color = Paleta.Texto3)
            }
            Text(f.detalle, fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.Texto2)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(f.detalle, fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.Texto2, modifier = Modifier.weight(1f))
                Text(Plataforma.formatearHora(f.horaMs), fontSize = 13.sp, color = Paleta.Texto3, modifier = Modifier.padding(start = 8.dp))
            }
        }
        // HU-38: un rechazo queda con su motivo; una nota explica por qué sigue en cola.
        f.motivoRechazo?.let { AvisoRojo("Rechazada: $it") }
        if (f.estado == EstadoEnvio.PENDIENTE && f.nota != null) {
            Text(
                f.nota + if (f.intentos > 0) " (intentos: ${f.intentos})" else "",
                fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.PendienteTexto,
                modifier = Modifier.fillMaxWidth().background(Paleta.PendienteFondo, RoundedCornerShape(12.dp)).padding(10.dp),
            )
        }
    }
}
