package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.ui.componentes.AvisoSinServicioAlmacen
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.ChipEstado
import pe.sevens.sigro.ui.componentes.Separador
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta

/*
 * Piezas comunes de las pantallas de almacén (Materiales, página 2 del lienzo). Mientras no exista el
 * servicio almacén, lo que se registra aquí se guarda en el celular con su uuid_cliente y NO se envía:
 * cada pantalla lo dice. Ninguna calcula saldo (regla 2).
 */

/** Búsqueda de insumos en la copia del celular (funciona sin señal), como en la salida. */
class BuscadorInsumos(private val catalogo: RepositorioCatalogo, private val ambito: CoroutineScope) {
    data class Estado(val texto: String = "", val resultados: List<Insumo> = emptyList())

    private val _e = MutableStateFlow(Estado())
    val estado = _e.asStateFlow()
    private var job: Job? = null

    fun buscar(texto: String) {
        _e.update { it.copy(texto = texto) }
        job?.cancel()
        if (texto.trim().length < 2) { _e.update { it.copy(resultados = emptyList()) }; return }
        job = ambito.launch {
            delay(200)
            val r = catalogo.buscar(texto)
            _e.update { it.copy(resultados = r) }
        }
    }

    fun limpiar() = _e.update { Estado() }
}

/** Buscar y elegir un insumo. Con uno elegido, se ve en naranja claro con su unidad. */
@Composable
fun ElegirInsumo(
    estado: BuscadorInsumos.Estado,
    elegido: Insumo?,
    alBuscar: (String) -> Unit,
    alElegir: (Insumo) -> Unit,
    alQuitar: () -> Unit,
    error: String? = null,
    etiqueta: String = "Insumo",
) {
    if (elegido != null) {
        Row(
            Modifier.fillMaxWidth().background(Paleta.NaranjaClaro, RoundedCornerShape(14.dp)).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(elegido.descripcion, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7C2D12))
                Text("${elegido.codigo} · se mide en ${elegido.unidad}", fontSize = 13.sp, color = Color(0xFF7C2D12))
            }
            IconButton(onClick = alQuitar) { Icon(Icons.Filled.Close, contentDescription = "Cambiar insumo", tint = Color(0xFF7C2D12)) }
        }
        return
    }
    CampoVidrio(
        estado.texto, alBuscar, etiqueta, marcador = "Nombre o código", error = error,
        finalIcono = { Icon(Icons.Filled.Search, contentDescription = null, tint = Paleta.Texto3, modifier = Modifier.padding(end = 10.dp)) },
    )
    if (estado.resultados.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(18.dp))) {
            estado.resultados.take(6).forEachIndexed { i, ins ->
                if (i > 0) Separador()
                Column(
                    Modifier.fillMaxWidth().clickable { alElegir(ins) }.heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(ins.descripcion, fontSize = 15.sp, color = Paleta.Tinta)
                    Text("${ins.codigo} · ${ins.unidad}", fontSize = 13.sp, color = Paleta.Texto3)
                }
            }
        }
    } else if (estado.texto.trim().length >= 2) {
        Text("Sin coincidencias en el celular. Actualiza el catálogo desde «Más» con señal.", fontSize = 14.sp, color = Paleta.Texto2)
    }
}

/** Confirmación común: guardado en el celular, «por enviar», y el aviso del servicio almacén. */
@Composable
fun GuardadoEnCelular(titulo: String, detalle: String, textoOtro: String, alOtro: () -> Unit, alVolver: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().vidrio(RoundedCornerShape(28.dp), fuerte = true, sombra = 16.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(56.dp))
        Text(titulo, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, modifier = Modifier.semantics { heading() })
        Text(detalle, fontSize = 16.sp, lineHeight = 23.sp, color = Paleta.Texto3, textAlign = TextAlign.Center)
        ChipEstado(EstadoEnvio.PENDIENTE)
        AvisoSinServicioAlmacen()
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonNegro(textoOtro, alOtro)
            BotonNegro("Volver al inicio", alVolver, contorno = true)
        }
    }
}

/** Paso numerado de un formulario («1 ¿Qué llegó?»). */
@Composable
fun PasoAlmacen(n: String, titulo: String) {
    Row(Modifier.padding(top = 2.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Text(n, color = Paleta.Naranja, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Box(Modifier.size(10.dp))
        Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Paleta.Tinta)
    }
}

/** Tarjeta de formulario con su espacio (para no repetir el modificador). */
@Composable
fun FormularioAlmacen(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().vidrio(RoundedCornerShape(26.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = contenido,
    )
}

/** Espacio vertical pequeño, para cuando un Spacer no basta en una fila. */
@Composable
fun Hueco() = Box(Modifier.height(4.dp))
