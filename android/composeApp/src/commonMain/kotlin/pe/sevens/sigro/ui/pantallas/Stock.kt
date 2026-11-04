package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Icon
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
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.Separador
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.planoStock
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 2.7 · Stock del almacén (lienzo Stock.dc.html, HU-43). Regla 2: el saldo NO es un campo y la app NO lo
 * calcula; lo devuelve el servidor con su hora. Mientras no exista el servicio almacén, la pantalla busca
 * el insumo y dice que el saldo vendrá del servidor: nunca suma ni resta salidas guardadas en el celular.
 */

/** Lo que se muestra como saldo: el del servidor, o el marcador si todavía no llegó. */
fun saldoParaMostrar(saldoServidor: String?, unidad: String): String =
    saldoServidor?.let { "$it $unidad" } ?: "[lo calcula el servidor]"

const val VISTA_TODA_LA_OBRA = "Toda la obra"

data class EstadoStock(val vistas: List<String> = listOf(VISTA_TODA_LA_OBRA), val vista: String = VISTA_TODA_LA_OBRA, val almacenes: List<AlmacenObra> = emptyList())

class StockViewModel(catalogo: RepositorioCatalogo, private val obra: RepositorioObra) : ViewModel() {
    val buscador = BuscadorInsumos(catalogo, viewModelScope)
    private val _e = MutableStateFlow(EstadoStock())
    val estado = _e.asStateFlow()

    /** Los almacenes de la obra (GET /obras/{id}/almacenes). Sin señal queda «Toda la obra». */
    fun cargar(obraId: Int) {
        viewModelScope.launch {
            val a = runCatching { obra.almacenes(obraId) }.getOrDefault(emptyList())
            _e.update { it.copy(almacenes = a, vistas = a.map { x -> x.nombre } + VISTA_TODA_LA_OBRA, vista = a.firstOrNull()?.nombre ?: VISTA_TODA_LA_OBRA) }
        }
    }

    fun vista(v: String) = _e.update { it.copy(vista = v) }
}

@Composable
fun PantallaStock(obraId: Int, alVolver: () -> Unit, vm: StockViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    val b by vm.buscador.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }

    PantallaVidrio("Stock", plano = { planoStock(it) }, alVolver = alVolver, subtitulo = "Calculado por el servidor, nunca en el celular") {
        Opciones(e.vistas, e.vista, { it }, vm::vista)
        CampoVidrio(b.texto, vm.buscador::buscar, "Buscar insumo", marcador = "Nombre o código")
        TarjetaVidrio(Modifier.semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.CloudSync, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(26.dp))
                Text("El saldo llega del servidor", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
            }
            Text(
                "Esta pantalla mostrará el saldo de cada insumo con la hora del último movimiento cuando exista el servicio almacén. " +
                    "La app nunca lo calcula por su cuenta: sin señal se verá el último saldo recibido con su hora.",
                fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.Texto2,
            )
        }
        if (b.resultados.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp))) {
                b.resultados.take(20).forEachIndexed { i, ins ->
                    if (i > 0) Separador()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(ins.descripcion, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Código ${ins.codigo} · ${e.vista}", fontSize = 13.sp, color = Paleta.Texto3)
                        }
                        Text(saldoParaMostrar(null, ins.unidad), fontSize = 13.sp, color = Paleta.Texto3, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
