package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.plataforma.Plataforma
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.AvisoSinServicioAlmacen
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Marcador
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.TituloSeccion
import pe.sevens.sigro.ui.componentes.planoCierre
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 2.10 · Cierre de obra (lienzo Cierre.dc.html, HU-47). Los saldos por confirmar y su costo promedio los
 * trae el servidor; hasta que exista el servicio almacén se muestran como [marcador]. La app guarda el
 * pedido de cierre (con la confirmación del almacenero) y lo envía cuando exista el servicio.
 */

data class EstadoCierre(
    val fecha: String,
    val confirmo: Boolean = false,
    val observacion: String = "",
    val almacenId: Int? = null,
    val guardando: Boolean = false,
    val guardado: Boolean = false,
) {
    /** Sin la confirmación de que contó lo que queda, no se pide el cierre. */
    val puedeGuardar: Boolean get() = confirmo && !guardando
}

class CierreViewModel(private val obra: RepositorioObra, private val registros: RepositorioRegistros) : ViewModel() {
    private val _e = MutableStateFlow(EstadoCierre(fecha = Plataforma.fechaIso(Plataforma.ahoraMs())))
    val estado = _e.asStateFlow()

    fun cargar(obraId: Int) {
        viewModelScope.launch {
            val a = runCatching { obra.almacenes(obraId).firstOrNull()?.id }.getOrNull()
            _e.update { it.copy(almacenId = a) }
        }
    }

    fun confirmar(v: Boolean) = _e.update { it.copy(confirmo = v) }
    fun observacion(v: String) = _e.update { it.copy(observacion = v) }

    fun guardar(obraId: Int) {
        val s = _e.value
        if (!s.puedeGuardar) return
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            registros.registrarAlmacen(obraId, RegistroAlmacen.Cierre(s.almacenId, s.fecha, s.observacion.trim().ifEmpty { null }))
            _e.update { it.copy(guardando = false, guardado = true) }
        }
    }
}

@Composable
fun PantallaCierre(obraId: Int, obraNombre: String, alVolver: () -> Unit, vm: CierreViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }

    PantallaVidrio("Cerrar obra", plano = { planoCierre(it) }, alVolver = alVolver, subtitulo = "El saldo pasa al almacén central con su costo") {
        if (e.guardado) {
            GuardadoEnCelular(
                "Pedido de cierre guardado", "El pedido de cierre de $obraNombre quedó en el celular con su código único.",
                "Volver a la pantalla de cierre", alVolver, alVolver,
            )
            return@PantallaVidrio
        }
        AvisoSinServicioAlmacen()
        TarjetaVidrio {
            TituloSeccion("Saldos por confirmar")
            Marcador("Saldo de cada insumo y su costo promedio: los trae el servidor almacén")
        }
        TarjetaVidrio {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { vm.confirmar(!e.confirmo) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(checked = e.confirmo, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Paleta.Naranja))
                Text("Conté lo que queda en el almacén y confirmo los saldos", fontSize = 15.sp, color = Paleta.Tinta)
            }
            CampoVidrio(e.observacion, vm::observacion, "Observación (opcional)", marcador = "Ej.: sacos abiertos en estante 2")
        }
        AvisoNaranja("Al cerrar se genera el acta de cierre: lo comprado, lo consumido y lo transferido. Las herramientas se cierran en su propia pantalla.")
        BotonNegro("Cerrar obra y transferir saldo", { vm.guardar(obraId) }, icono = Icons.Filled.Lock, colorIcono = Paleta.NaranjaVivo, cargando = e.guardando, habilitado = e.confirmo)
    }
}
