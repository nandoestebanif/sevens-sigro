package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.ui.componentes.BarraSuperior
import pe.sevens.sigro.ui.componentes.TarjetaAccion
import pe.sevens.sigro.ui.tema.LocalColoresEstado

data class EstadoInicio(
    val insumosEnCelular: Int = 0,
    val actualizandoCatalogo: Boolean = false,
    val enviando: Boolean = false,
    val mensaje: String? = null,
)

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

@Composable
fun PantallaInicio(
    sesion: Sesion,
    obra: ObraAsignada,
    irA: (Destino) -> Unit,
    vm: InicioViewModel = koinViewModel(),
) {
    val e by vm.estado.collectAsState()
    val pendientes by vm.pendientes.collectAsState()
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(e.mensaje) {
        e.mensaje?.let { avisos.showSnackbar(it); vm.mensajeVisto() }
    }

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = obra.nombre,
                subtitulo = "${sesion.nombre} · ${obra.rolLegible}",
                acciones = {
                    if (sesion.obras.size > 1) {
                        IconButton(onClick = vm::cambiarDeObra) { Icon(Icons.Filled.SwapHoriz, contentDescription = "Cambiar de obra") }
                    }
                    IconButton(onClick = vm::salir) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir") }
                },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BandaEnvio(pendientes, e.enviando, vm::enviarAhora)

            TarjetaAccion(
                Icons.Filled.Unarchive, "Registrar salida",
                "Material que sale del almacén, con motivo y quién retira",
                alPulsar = { irA(Destino.Salida) }, destacada = true,
            )
            TarjetaAccion(
                Icons.Filled.Outbox, "Mis registros",
                "Lo que registraste en esta obra y su estado de envío",
                alPulsar = { irA(Destino.Pendientes) }, insignia = pendientes,
            )
            TarjetaAccion(
                Icons.Filled.Inventory, "Stock del almacén",
                "Próximamente: el saldo que calcula el servidor",
                alPulsar = {}, habilitada = false,
            )
            TarjetaAccion(
                Icons.Filled.PhoneAndroid, "Mi celular",
                "Código del dispositivo y su autorización en la obra",
                alPulsar = { irA(Destino.Dispositivo) },
            )

            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${e.insumosEnCelular} insumos del catálogo guardados para buscar sin señal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.actualizarCatalogo() }, enabled = !e.actualizandoCatalogo) {
                    if (e.actualizandoCatalogo) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar")
                }
            }
        }
    }
}

/** Franja de estado de envío: lo primero que ve el almacenero al abrir la app. */
@Composable
private fun BandaEnvio(pendientes: Int, enviando: Boolean, alEnviar: () -> Unit) {
    val c = LocalColoresEstado.current
    val hayPendientes = pendientes > 0
    Surface(
        color = if (hayPendientes) c.pendienteFondo else c.enviadoFondo,
        contentColor = if (hayPendientes) c.pendiente else c.enviado,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (hayPendientes) Icons.Filled.CloudUpload else Icons.Filled.CloudDone, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                if (hayPendientes) "$pendientes ${if (pendientes == 1) "registro" else "registros"} por enviar"
                else "Todo enviado al servidor",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (hayPendientes) {
                TextButton(onClick = alEnviar, enabled = !enviando) {
                    Text(if (enviando) "Enviando…" else "Enviar ahora")
                }
            }
        }
    }
}
