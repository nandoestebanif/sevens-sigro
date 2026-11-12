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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import pe.sevens.sigro.dominio.CatalogoTexto
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.FilaStock
import pe.sevens.sigro.dominio.HoraLima
import pe.sevens.sigro.dominio.RepositorioStock
import pe.sevens.sigro.dominio.StockObra
import pe.sevens.sigro.dominio.cifraLegible
import pe.sevens.sigro.ui.componentes.AvisoNaranja
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
 * 2.7 · Stock del almacén (lienzo Stock.dc.html, HU-43), conectado (A2) a GET /obras/{id}/almacen/stock.
 * Regla 2: el saldo NO es un campo y la app NO lo calcula. Se muestra por almacén y consolidado tal como lo
 * devolvió el servidor, con su fecha de corte. Sin señal, la última respuesta guardada con su hora.
 */

/** Lo que se muestra como saldo: el del servidor, o el marcador si todavía no llegó. */
fun saldoParaMostrar(saldoServidor: String?, unidad: String): String =
    saldoServidor?.let { "${cifraLegible(it)} $unidad" } ?: "[lo calcula el servidor]"

const val VISTA_TODA_LA_OBRA = "Toda la obra"

/** Pestañas: un almacén por nombre (en el orden del servidor) y, al final, el consolidado de la obra. */
fun vistasStock(s: StockObra?): List<String> =
    (s?.porAlmacen.orEmpty().mapNotNull { it.almacen }.distinct()) + VISTA_TODA_LA_OBRA

/**
 * Las filas de una vista, filtradas por texto (nombre o código, sin tildes). Solo elige y filtra filas del
 * servidor: nunca suma almacenes ni ajusta con lo registrado en el celular. «Toda la obra» es el consolidado
 * que manda el servidor.
 */
fun filasStock(s: StockObra?, vista: String, texto: String): List<FilaStock> {
    if (s == null) return emptyList()
    val base = if (vista == VISTA_TODA_LA_OBRA) s.consolidado else s.porAlmacen.filter { it.almacen == vista }
    val q = CatalogoTexto.normalizar(texto)
    if (q.isEmpty()) return base
    return base.filter { f ->
        val t = CatalogoTexto.normalizar(f.descripcion) + " " + CatalogoTexto.normalizar(f.codigo)
        q.split(' ').all { t.contains(it) }
    }
}

/** De cuándo es lo que se ve: el corte del servidor, o (sin señal) la hora en que llegó la copia guardada. */
fun textoCorte(s: StockObra): String = when {
    s.desdeCelular -> "Sin señal · actualizado a las ${HoraLima.horaDeMs(s.recibidoMs)}"
    s.corte == null -> "El servidor aún no tiene movimientos en esta obra"
    else -> "Corte del servidor: ${HoraLima.deIso(s.corte) ?: s.corte} (hora de Lima)"
}

data class EstadoStock(
    val cargando: Boolean = false,
    val stock: StockObra? = null,
    val vista: String = VISTA_TODA_LA_OBRA,
    val texto: String = "",
    val error: String? = null,
) {
    val vistas: List<String> get() = vistasStock(stock)
    val filas: List<FilaStock> get() = filasStock(stock, vista, texto)
}

class StockViewModel(private val repo: RepositorioStock) : ViewModel() {
    private val _e = MutableStateFlow(EstadoStock())
    val estado = _e.asStateFlow()

    fun cargar(obraId: Int) {
        _e.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val s = repo.stock(obraId)
                _e.update { e ->
                    // Se queda en el almacén elegido si sigue existiendo; si no, el primero (o la obra).
                    val vista = e.vista.takeIf { it in vistasStock(s) } ?: vistasStock(s).first()
                    e.copy(cargando = false, stock = s, vista = vista)
                }
            } catch (x: ErrorSigro) {
                _e.update { it.copy(cargando = false, error = x.message) }
            }
        }
    }

    fun vista(v: String) = _e.update { it.copy(vista = v) }
    fun buscar(t: String) = _e.update { it.copy(texto = t) }
}

@Composable
fun PantallaStock(obraId: Int, alVolver: () -> Unit, vm: StockViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }

    PantallaVidrio("Stock", plano = { planoStock(it) }, alVolver = alVolver, subtitulo = "Calculado por el servidor, nunca en el celular") {
        val s = e.stock
        TarjetaVidrio(Modifier.semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (s?.desdeCelular == true) Icons.Filled.CloudOff else Icons.Filled.CloudSync,
                    contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(26.dp),
                )
                Text(
                    when {
                        s != null -> textoCorte(s)
                        e.cargando -> "Consultando el stock al servidor…"
                        else -> "Sin stock para mostrar"
                    },
                    fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta,
                    modifier = Modifier.weight(1f),
                )
                if (e.cargando) CircularProgressIndicator(color = Paleta.Naranja, modifier = Modifier.size(22.dp))
            }
            if (s?.desdeCelular == true) {
                Text(
                    "Es la última copia que llegó del servidor. Lo que registraste después no está sumado ni restado aquí.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.Texto2,
                )
            }
            if (!e.cargando) TextButton(onClick = { vm.cargar(obraId) }) { Text("Actualizar", color = Paleta.Naranja, fontWeight = FontWeight.Bold) }
        }
        e.error?.let { AvisoRojo(it) }
        s?.advertencias?.forEach { AvisoNaranja(it) }
        if (s != null) {
            Opciones(e.vistas, e.vista, { it }, vm::vista)
            CampoVidrio(e.texto, vm::buscar, "Buscar insumo", marcador = "Nombre o código")
            val filas = e.filas
            if (filas.isEmpty()) {
                Text(
                    if (e.texto.isBlank()) "No hay saldo registrado en esta vista." else "Ningún insumo coincide con la búsqueda.",
                    fontSize = 15.sp, color = Paleta.Texto2,
                )
            } else {
                Column(Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp))) {
                    filas.forEachIndexed { i, f ->
                        if (i > 0) Separador()
                        FilaDeStock(f)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaDeStock(f: FilaStock) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(f.descripcion, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Código ${f.codigo}" + (f.almacen?.let { " · $it" } ?: ""), fontSize = 13.sp, color = Paleta.Texto3)
            if (f.bajoPuntoReposicion == true) Etiqueta("Bajo el punto de reposición", Paleta.RechazadoTexto, Paleta.RechazadoFondo)
        }
        Text(
            saldoParaMostrar(f.cantidad, f.unidad), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Paleta.Tinta,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
