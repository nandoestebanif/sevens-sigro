package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import pe.sevens.sigro.dominio.CatalogoTexto
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.FilaStock
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.StockServidor
import pe.sevens.sigro.plataforma.Plataforma
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Etiqueta
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.Separador
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.planoStock
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 2.7 · Stock del almacén (lienzo Stock.dc.html, HU-43). Regla 2: el saldo NO es un campo y la app NO lo
 * calcula; lo devuelve el servidor (GET /obras/{id}/almacen/stock) con su momento. Sin señal se muestra el
 * último saldo recibido con la hora en que llegó. Nunca suma ni resta salidas guardadas en el celular:
 * la pantalla solo filtra por texto lo que el servidor mandó.
 */

/** Lo que se muestra como saldo: el del servidor, o el marcador si todavía no llegó. */
fun saldoParaMostrar(saldoServidor: String?, unidad: String): String =
    saldoServidor?.let { "${sinCerosDeMas(it)} $unidad" } ?: "[lo calcula el servidor]"

/** Filtra por texto las filas del servidor (sin tildes ni mayúsculas). No toca las cantidades. */
fun filtrarStock(filas: List<FilaStock>, q: String): List<FilaStock> =
    if (q.isBlank()) filas else {
        val n = CatalogoTexto.normalizar(q)
        filas.filter { n.split(' ').all { w -> CatalogoTexto.normalizar(it.descripcion + " " + it.codigo).contains(w) } }
    }

/** De dónde sale el número y de cuándo es. */
fun origenDelStock(s: StockServidor, formatear: (Long) -> String = Plataforma::formatearHora): String =
    if (s.desdeCelular) "Sin señal: último saldo recibido el ${formatear(s.recibidoMs)}"
    else "Calculado por el servidor" + (s.corte?.let { " · corte ${it.take(16).replace('T', ' ')}" } ?: "")

const val VISTA_TODA_LA_OBRA = "Toda la obra"

data class EstadoStock(
    val almacenes: List<AlmacenObra> = emptyList(),
    val vista: String = VISTA_TODA_LA_OBRA,
    val q: String = "",
    val cargando: Boolean = false,
    val stock: StockServidor? = null,
    val error: String? = null,
) {
    val vistas: List<String> get() = almacenes.map { it.nombre } + VISTA_TODA_LA_OBRA
    val almacenId: Int? get() = almacenes.firstOrNull { it.nombre == vista }?.id
}

class StockViewModel(private val obra: RepositorioObra) : ViewModel() {
    private val _e = MutableStateFlow(EstadoStock())
    val estado = _e.asStateFlow()

    /** Almacenes de la obra y el stock del primero (o de toda la obra). */
    fun cargar(obraId: Int) {
        viewModelScope.launch {
            val a = runCatching { obra.almacenes(obraId) }.getOrDefault(emptyList())
            _e.update { it.copy(almacenes = a, vista = a.firstOrNull()?.nombre ?: VISTA_TODA_LA_OBRA) }
            consultar(obraId)
        }
    }

    fun vista(obraId: Int, v: String) { _e.update { it.copy(vista = v) }; consultar(obraId) }
    fun buscar(q: String) = _e.update { it.copy(q = q) }

    fun consultar(obraId: Int) {
        _e.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val s = obra.stock(obraId, _e.value.almacenId)
                _e.update { it.copy(cargando = false, stock = s) }
            } catch (x: ErrorSigro) {
                val msg = if (x.esTransitorio) "Sin señal y todavía no hay un saldo recibido en este celular. Vuelve a intentar con señal." else x.message
                _e.update { it.copy(cargando = false, stock = null, error = msg) }
            }
        }
    }
}

@Composable
fun PantallaStock(obraId: Int, alVolver: () -> Unit, vm: StockViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }
    val filas = remember(e.stock, e.q) { filtrarStock(e.stock?.filas.orEmpty(), e.q) }

    PantallaVidrio(
        "Stock", plano = { planoStock(it) }, alVolver = alVolver,
        subtitulo = e.stock?.let { origenDelStock(it) } ?: "Calculado por el servidor, nunca en el celular",
        acciones = {
            IconButton(onClick = { vm.consultar(obraId) }, enabled = !e.cargando, modifier = Modifier.size(48.dp).vidrio(CircleShape, sombra = 4.dp)) {
                if (e.cargando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Paleta.Naranja)
                else Icon(Icons.Filled.Sync, contentDescription = "Actualizar saldo", tint = Paleta.Tinta)
            }
        },
    ) {
        Opciones(e.vistas, e.vista, { it }, { vm.vista(obraId, it) })
        CampoVidrio(e.q, vm::buscar, "Buscar insumo", marcador = "Nombre o código")
        if (e.stock?.desdeCelular == true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Paleta.PendienteTexto, modifier = Modifier.size(18.dp))
                Text("Es el último saldo que mandó el servidor; puede haber cambiado.", fontSize = 13.sp, color = Paleta.PendienteTexto)
            }
        }
        e.error?.let { AvisoRojo(it) }
        val bajos = filas.count { it.bajoPuntoReposicion == true }
        if (bajos > 0) {
            Text(
                "$bajos ${if (bajos == 1) "insumo por debajo" else "insumos por debajo"} del punto de reposición",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Ambar,
            )
        }
        if (filas.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp))) {
                filas.take(80).forEachIndexed { i, f ->
                    if (i > 0) Separador()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(f.descripcion, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Código ${f.codigo} · últ. mov. ${f.ultimoMovimientoEn.take(16).replace('T', ' ')}", fontSize = 12.sp, color = Paleta.Texto3)
                            if (f.bajoPuntoReposicion == true) Etiqueta("Reponer", Paleta.PendienteTexto, Paleta.PendienteFondo)
                        }
                        Column(Modifier.padding(start = 8.dp).widthIn(max = 120.dp), horizontalAlignment = Alignment.End) {
                            Text(
                                sinCerosDeMas(f.cantidad), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End,
                                color = if (f.bajoPuntoReposicion == true) Paleta.Ambar else Paleta.Tinta,
                            )
                            Text(f.unidad, fontSize = 12.sp, color = Paleta.Texto3, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        } else if (e.stock != null) {
            TarjetaVidrio {
                Text(if (e.q.isBlank()) "Este almacén aún no tiene movimientos" else "Ningún insumo con ese nombre en el stock", fontSize = 15.sp, color = Paleta.Texto2)
            }
        }
        Text("La app nunca calcula el saldo: lo muestra tal como lo mandó el servidor, con su hora.", fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.Texto3)
    }
}
