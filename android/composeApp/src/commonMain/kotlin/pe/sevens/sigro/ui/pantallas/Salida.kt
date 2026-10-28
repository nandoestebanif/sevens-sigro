package pe.sevens.sigro.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.NuevaSalida
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioSalidas
import pe.sevens.sigro.dominio.ValidarSalida
import pe.sevens.sigro.ui.componentes.AvisoInfo
import pe.sevens.sigro.ui.componentes.BarraSuperior
import pe.sevens.sigro.ui.componentes.BotonGrande
import pe.sevens.sigro.ui.componentes.Campo
import pe.sevens.sigro.ui.componentes.Vacio

/* HU-40 · Registrar salida de material + HU-37 · sin conexión. */

data class EstadoSalida(
    val busqueda: String = "",
    val resultados: List<Insumo> = emptyList(),
    val insumo: Insumo? = null,
    val cantidad: String = "",
    val motivo: String = "",
    val quienRetira: String = "",
    val frente: String = "",
    val errores: Map<String, String> = emptyMap(),
    val guardando: Boolean = false,
    val guardada: Boolean = false,
)

class SalidaViewModel(
    private val catalogo: RepositorioCatalogo,
    private val salidas: RepositorioSalidas,
    private val programador: ProgramadorSincronizacion,
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoSalida())
    val estado = _e.asStateFlow()
    private var busquedaJob: Job? = null

    fun buscar(texto: String) {
        _e.update { it.copy(busqueda = texto) }
        busquedaJob?.cancel()
        if (texto.trim().length < 2) { _e.update { it.copy(resultados = emptyList()) }; return }
        busquedaJob = viewModelScope.launch {
            delay(200) // espera a que termine de escribir
            val r = catalogo.buscar(texto)
            _e.update { it.copy(resultados = r) }
        }
    }

    fun elegir(i: Insumo) = _e.update { it.copy(insumo = i, busqueda = "", resultados = emptyList(), errores = it.errores - "insumo") }
    fun quitarInsumo() = _e.update { it.copy(insumo = null) }
    fun cantidad(v: String) = _e.update { it.copy(cantidad = v, errores = it.errores - "cantidad") }
    fun motivo(v: String) = _e.update { it.copy(motivo = v, errores = it.errores - "motivo") }
    fun quienRetira(v: String) = _e.update { it.copy(quienRetira = v, errores = it.errores - "quienRetira") }
    fun frente(v: String) = _e.update { it.copy(frente = v) }

    fun guardar(obraId: Int) {
        val s = _e.value
        val errores = ValidarSalida.errores(s.insumo, s.cantidad, s.motivo, s.quienRetira)
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            salidas.registrar(NuevaSalida(obraId, s.insumo!!, s.cantidad, s.motivo, s.quienRetira, s.frente))
            programador.programar() // si hay señal se envía ya; si no, cuando vuelva
            _e.update { it.copy(guardando = false, guardada = true) }
        }
    }

    /** Otra salida: se conserva quién retira y el frente, que suelen repetirse en la misma jornada. */
    fun otra() = _e.update { EstadoSalida(quienRetira = it.quienRetira, frente = it.frente) }
}

@Composable
fun PantallaSalida(obraId: Int, alVolver: () -> Unit, vm: SalidaViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    Scaffold(
        topBar = { BarraSuperior("Registrar salida", "Se guarda en el celular aunque no haya señal", alVolver = alVolver) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (e.guardada) {
            Confirmacion(Modifier.padding(pad), alOtra = vm::otra, alVolver = alVolver)
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Paso("1", "¿Qué sale?")
            if (e.insumo == null) {
                Campo(
                    e.busqueda, vm::buscar, "Buscar insumo por nombre o código",
                    error = e.errores["insumo"],
                    finalIcono = { Icon(Icons.Filled.Search, contentDescription = null) },
                )
                if (e.resultados.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        e.resultados.take(8).forEachIndexed { i, ins ->
                            if (i > 0) HorizontalDivider()
                            Column(
                                Modifier.fillMaxWidth().clickable { vm.elegir(ins) }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(ins.descripcion, style = MaterialTheme.typography.bodyLarge)
                                Text("${ins.codigo} · ${ins.unidad}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (e.busqueda.trim().length >= 2) {
                    Vacio(Icons.Filled.Search, "Sin coincidencias", "Prueba con otra palabra o actualiza el catálogo desde el inicio.")
                }
            } else {
                InsumoElegido(e.insumo!!, vm::quitarInsumo)
            }

            Paso("2", "¿Cuánto?")
            Campo(
                e.cantidad, vm::cantidad, "Cantidad",
                teclado = KeyboardType.Decimal, sufijo = e.insumo?.unidad, error = e.errores["cantidad"],
                ayuda = "El servidor verifica que haya stock en el almacén",
            )

            Paso("3", "¿Para qué y quién lo lleva?")
            Campo(e.motivo, vm::motivo, "Motivo / uso", error = e.errores["motivo"], ayuda = "Ej.: vaciado de losa del 3.er piso")
            Campo(e.quienRetira, vm::quienRetira, "Quién retira", error = e.errores["quienRetira"])
            Campo(e.frente, vm::frente, "Frente o cuadrilla (opcional)")

            Spacer(Modifier.size(4.dp))
            BotonGrande("Guardar salida", { vm.guardar(obraId) }, icono = Icons.Filled.Save, cargando = e.guardando)
            AvisoInfo("La salida queda «por enviar» y se envía sola cuando hay señal. El costo lo calcula el servidor al recibirla.")
        }
    }
}

@Composable
private fun Paso(n: String, titulo: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(n, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Text(titulo, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun InsumoElegido(i: Insumo, alQuitar: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(i.descripcion, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${i.codigo} · se mide en ${i.unidad}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            IconButton(onClick = alQuitar) { Icon(Icons.Filled.Close, contentDescription = "Cambiar insumo") }
        }
    }
}

@Composable
private fun Confirmacion(modifier: Modifier, alOtra: () -> Unit, alVolver: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        Text("Salida guardada", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Quedó en el celular con su código único. Se envía sola cuando haya señal; un reenvío no la duplica.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BotonGrande("Registrar otra salida", alOtra)
        BotonGrande("Volver al inicio", alVolver, secundario = true)
    }
}
