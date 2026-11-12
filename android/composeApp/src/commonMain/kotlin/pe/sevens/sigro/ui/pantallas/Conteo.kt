package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.LineaAlmacen
import pe.sevens.sigro.dominio.MotivoDiferencia
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.ValidarAlmacen
import pe.sevens.sigro.plataforma.Plataforma
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.AvisoSinServicioAlmacen
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.Separador
import pe.sevens.sigro.ui.componentes.planoConteo
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 2.8 · Conteo físico (lienzo Conteo.dc.html, HU-46). El saldo teórico lo pone el servidor y la
 * diferencia también: la app solo anota lo contado (cero es válido) y, por si hay diferencia, su motivo.
 * Cada diferencia genera un ajuste valorizado al costo promedio vigente, en el servidor. Se guarda en el
 * celular y se envía cuando exista el servicio almacén.
 */

data class LineaConteo(val insumo: Insumo, val contado: String = "", val motivo: MotivoDiferencia? = null)

/** HU-46: al menos un insumo; lo contado es una cantidad válida (cero también). */
fun validarConteo(lineas: List<LineaConteo>): Map<String, String> = buildMap {
    if (lineas.isEmpty()) put("lineas", "Agrega al menos un insumo contado")
    lineas.forEachIndexed { i, l -> ValidarAlmacen.cantidad(l.contado, permitirCero = true)?.let { put("$i", it) } }
}

data class EstadoConteo(
    val fecha: String,
    val lineas: List<LineaConteo> = emptyList(),
    val almacenId: Int? = null,
    val errores: Map<String, String> = emptyMap(),
    val guardando: Boolean = false,
    val guardado: Boolean = false,
)

class ConteoViewModel(
    catalogo: RepositorioCatalogo,
    private val obra: RepositorioObra,
    private val registros: RepositorioRegistros,
) : ViewModel() {
    val buscador = BuscadorInsumos(catalogo, viewModelScope)
    private val _e = MutableStateFlow(EstadoConteo(fecha = Plataforma.fechaIso(Plataforma.ahoraMs())))
    val estado = _e.asStateFlow()

    fun cargar(obraId: Int) {
        viewModelScope.launch {
            val a = runCatching { obra.almacenes(obraId).firstOrNull()?.id }.getOrNull()
            _e.update { it.copy(almacenId = a) }
        }
    }

    fun agregar(i: Insumo) {
        buscador.limpiar()
        _e.update { s -> if (s.lineas.any { it.insumo.id == i.id }) s else s.copy(lineas = s.lineas + LineaConteo(i), errores = s.errores - "lineas") }
    }
    private fun cambiar(idx: Int, f: (LineaConteo) -> LineaConteo) =
        _e.update { s -> s.copy(lineas = s.lineas.mapIndexed { i, l -> if (i == idx) f(l) else l }, errores = s.errores - "$idx") }
    fun contado(idx: Int, v: String) = cambiar(idx) { it.copy(contado = v) }
    fun motivo(idx: Int, m: MotivoDiferencia) = cambiar(idx) { it.copy(motivo = if (it.motivo == m) null else m) }
    fun quitar(idx: Int) = _e.update { s -> s.copy(lineas = s.lineas.filterIndexed { i, _ -> i != idx }, errores = emptyMap()) }

    fun guardar(obraId: Int) {
        val s = _e.value
        val errores = validarConteo(s.lineas)
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            registros.registrarAlmacen(obraId, RegistroAlmacen.Conteo(s.almacenId, s.fecha, s.lineas.map { LineaAlmacen(it.insumo, it.contado) to it.motivo }))
            _e.update { it.copy(guardando = false, guardado = true) }
        }
    }

    fun otro() = _e.update { EstadoConteo(fecha = it.fecha, almacenId = it.almacenId) }
}

@Composable
fun PantallaConteo(obraId: Int, alVolver: () -> Unit, vm: ConteoViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    val b by vm.buscador.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }

    PantallaVidrio("Conteo físico", plano = { planoConteo(it) }, alVolver = alVolver, subtitulo = "Almacén de la obra · ${Texto.fecha(e.fecha)}") {
        if (e.guardado) {
            GuardadoEnCelular(
                "Conteo guardado", "El conteo de ${e.lineas.size} ${if (e.lineas.size == 1) "insumo" else "insumos"} quedó en el celular con su código único.",
                "Registrar otro conteo", vm::otro, alVolver,
            )
            return@PantallaVidrio
        }
        AvisoSinServicioAlmacen()
        FormularioAlmacen {
            PasoAlmacen("1", "¿Qué contaste?")
            e.lineas.forEachIndexed { i, l ->
                if (i > 0) Separador()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.insumo.descripcion, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
                            Text("Sistema [lo pone el servidor] · diferencia [la calcula el servidor]", fontSize = 13.sp, color = Paleta.Texto3)
                        }
                        IconButton(onClick = { vm.quitar(i) }) { Icon(Icons.Filled.Close, contentDescription = "Quitar ${l.insumo.descripcion}", tint = Paleta.Texto3) }
                    }
                    CampoVidrio(l.contado, { vm.contado(i, it) }, "Contado", teclado = KeyboardType.Decimal, sufijo = l.insumo.unidad, error = e.errores["$i"])
                    Text("Motivo si hay diferencia", fontSize = 13.sp, color = Paleta.Texto3)
                    Opciones(MotivoDiferencia.entries, l.motivo, { it.legible }, { vm.motivo(i, it) })
                }
            }
            ElegirInsumo(b, null, vm.buscador::buscar, vm::agregar, {}, error = e.errores["lineas"], etiqueta = if (e.lineas.isEmpty()) "Agregar insumo" else "Agregar otro insumo")
        }
        AvisoNaranja("Cada diferencia genera un ajuste valorizado al costo promedio vigente. Nada se edita a mano: lo calcula el servidor.")
        e.errores["lineas"]?.let { AvisoRojo(it) }
        BotonNegro("Confirmar conteo", { vm.guardar(obraId) }, icono = Icons.Filled.FactCheck, colorIcono = Paleta.NaranjaVivo, cargando = e.guardando)
    }
}
