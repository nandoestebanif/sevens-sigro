package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.FilaHoras
import pe.sevens.sigro.dominio.HorasTrabajador
import pe.sevens.sigro.dominio.RepositorioTrabajadores
import pe.sevens.sigro.dominio.ResumenHoras
import pe.sevens.sigro.dominio.horasLegibles
import pe.sevens.sigro.dominio.resumirHoras
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.Separador
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.TituloSeccion
import pe.sevens.sigro.ui.componentes.planoTrabajadores
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 5.4 · Horas acumuladas (HU-64). Las calcula el servidor con las jornadas VIGENTES (ya corregidas); la app solo las
 * agrupa por trabajador o por cuadrilla y filtra por semana. Horas normales y extra siempre por separado (c1), por
 * trabajador, cuadrilla y periodo (c2), con la fecha de la última jornada considerada (c3). Necesita señal.
 */

enum class VistaHoras(val legible: String) { TRABAJADOR("Por trabajador"), CUADRILLA("Por cuadrilla") }

data class EstadoHoras(
    val cargando: Boolean = false,
    val filas: List<HorasTrabajador> = emptyList(),
    val cargado: Boolean = false,
    val periodo: Int? = null,
    val vista: VistaHoras = VistaHoras.TRABAJADOR,
    val aviso: String? = null,
) {
    val resumen: ResumenHoras get() = resumirHoras(filas, periodo)
}

class HorasViewModel(private val trabajadores: RepositorioTrabajadores) : ViewModel() {
    private val _e = MutableStateFlow(EstadoHoras())
    val estado = _e.asStateFlow()

    fun cargar(obraId: Int) {
        _e.update { it.copy(cargando = true, aviso = null) }
        viewModelScope.launch {
            try {
                val filas = trabajadores.horas(obraId)
                _e.update { it.copy(cargando = false, cargado = true, filas = filas, periodo = it.periodo?.takeIf { p -> filas.any { f -> f.periodo == p } }) }
            } catch (x: ErrorSigro) {
                val m = if (x.esTransitorio) "Sin señal: las horas las calcula el servidor. Vuelve a intentarlo con conexión." else x.message
                _e.update { it.copy(cargando = false, aviso = m) }
            }
        }
    }

    fun periodo(p: Int?) = _e.update { it.copy(periodo = p) }
    fun vista(v: VistaHoras) = _e.update { it.copy(vista = v) }
}

@Composable
fun PantallaHoras(obraId: Int, obraNombre: String, alVolver: () -> Unit, vm: HorasViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }
    val r = e.resumen

    PantallaVidrio(
        "Horas acumuladas", plano = { planoTrabajadores(it) }, alVolver = alVolver, subtitulo = obraNombre,
        acciones = {
            IconButton(onClick = { vm.cargar(obraId) }, enabled = !e.cargando, modifier = Modifier.size(48.dp).vidrio(CircleShape, sombra = 4.dp)) {
                if (e.cargando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Paleta.Naranja)
                else Icon(Icons.Filled.Sync, contentDescription = "Actualizar horas", tint = Paleta.Tinta)
            }
        },
    ) {
        e.aviso?.let { AvisoNaranja(it) }
        if (e.cargado && e.filas.isEmpty()) {
            TarjetaVidrio {
                Text("Aún no hay jornadas enviadas", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                Text("Las horas aparecen cuando la asistencia llega al servidor.", fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.Texto2)
            }
            return@PantallaVidrio
        }
        if (e.filas.isEmpty()) return@PantallaVidrio

        // Totales del filtro elegido: normales y extra separadas (c1) y la última jornada considerada (c3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Total(horasLegibles(r.horasNormales), "Normales", Modifier.weight(1f))
            Total(horasLegibles(r.horasExtra), "Extra", Modifier.weight(1f))
        }
        Text(
            r.ultimaJornada?.let { "Última jornada considerada: $it" } ?: "Sin jornadas en este periodo",
            fontSize = 13.sp, color = Paleta.Texto3, modifier = Modifier.padding(start = 4.dp),
        )

        TituloSeccion("Periodo", Modifier.padding(start = 4.dp, top = 4.dp))
        Opciones(listOf<Int?>(null) + r.periodos, e.periodo, { if (it == null) "Toda la obra" else "Semana $it" }, vm::periodo)
        Opciones(VistaHoras.entries.toList(), e.vista, { it.legible }, vm::vista)

        val filas = if (e.vista == VistaHoras.TRABAJADOR) r.porTrabajador else r.porCuadrilla
        Column(Modifier.fillMaxWidth().vidrio(RoundedCornerShape(24.dp))) {
            filas.forEachIndexed { i, f -> if (i > 0) Separador(); FilaHorasVista(f) }
        }
        Text("Solo cuenta la versión vigente de cada jornada: si se corrigió, vale la corrección.", fontSize = 13.sp, color = Paleta.Texto3)
    }
}

@Composable
private fun Total(valor: String, etiqueta: String, modifier: Modifier) {
    Column(modifier.vidrio(RoundedCornerShape(20.dp), sombra = 4.dp).padding(12.dp).semantics(mergeDescendants = true) {}) {
        Text(valor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta)
        Text("Horas ${etiqueta.lowercase()}", fontSize = 13.sp, color = Paleta.Texto2)
    }
}

@Composable
private fun FilaHorasVista(f: FilaHoras) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(f.nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${f.detalle} · ${f.diasPresente} ${if (f.diasPresente == 1) "día" else "días"}" + (f.ultimaJornada?.let { " · hasta $it" } ?: ""),
                fontSize = 13.sp, color = Paleta.Texto3, maxLines = 2,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(horasLegibles(f.horasNormales), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
            if (f.horasExtra > 0) Text("+ ${horasLegibles(f.horasExtra)} extra", fontSize = 13.sp, color = Paleta.NaranjaOscuro)
        }
    }
}
