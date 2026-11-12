package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.ElegirAlmacen
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoEnvio
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.ui.componentes.AvisoSinServicioAlmacen
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.ChipEstado
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.Separador
import pe.sevens.sigro.ui.componentes.vidrio
import pe.sevens.sigro.ui.tema.Paleta

/*
 * Piezas comunes de las pantallas de almacén (Materiales, página 2 del lienzo). Todo se guarda primero en
 * el celular con su uuid_cliente. La salida y la entrada de compra ya se envían; lo que aún no tiene
 * contrato en la app lo dice en pantalla. Ninguna calcula saldo (regla 2).
 */

/**
 * M6 · elegir el almacén de la obra (GET /obras/{id}/almacenes). Con uno solo se elige solo; con varios
 * pregunta. Lo elegido queda en el celular, así sin señal se usa el último almacén de esa obra.
 */
class SelectorAlmacen(private val obra: RepositorioObra, private val ambito: CoroutineScope) {
    data class Estado(
        val opciones: List<AlmacenObra> = emptyList(),
        val elegido: AlmacenObra? = null,
        val cargando: Boolean = false,
        /** No se pudo consultar la lista (sin señal o sin sesión): se muestra el último elegido, si hay. */
        val sinLista: Boolean = false,
    ) {
        val falta: Boolean get() = ElegirAlmacen.falta(opciones, elegido)
    }

    private val _e = MutableStateFlow(Estado())
    val estado = _e.asStateFlow()

    fun cargar(obraId: Int) {
        _e.update { it.copy(cargando = true) }
        ambito.launch {
            val recordado = obra.almacenRecordado(obraId)
            val lista = try { obra.almacenes(obraId) } catch (e: ErrorSigro) { null }
            val elegido = ElegirAlmacen.inicial(lista, recordado)
            if (elegido != null && elegido != recordado) obra.recordarAlmacen(obraId, elegido)
            _e.value = Estado(opciones = lista.orEmpty(), elegido = elegido, sinLista = lista == null)
        }
    }

    fun elegir(obraId: Int, a: AlmacenObra) {
        _e.update { it.copy(elegido = a) }
        ambito.launch { obra.recordarAlmacen(obraId, a) }
    }
}

/** Píldoras con los almacenes cuando hay varios; una línea cuando es uno solo o se usa el último elegido. */
@Composable
fun ElegirAlmacenObra(estado: SelectorAlmacen.Estado, alElegir: (AlmacenObra) -> Unit, error: String? = null) {
    when {
        estado.opciones.size > 1 -> {
            Text("¿De qué almacén?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
            Opciones(estado.opciones, estado.elegido, { it.nombre }, alElegir)
            error?.let { Text(it, fontSize = 13.sp, color = Paleta.RechazadoTexto) }
        }
        estado.elegido != null -> Text(
            "Almacén: ${estado.elegido.nombre}" + if (estado.sinLista) " (el último que usaste en esta obra)" else "",
            fontSize = 14.sp, color = Paleta.Texto2,
        )
        estado.sinLista && !estado.cargando -> Text(
            "Sin señal: se usa el último almacén elegido.",
            fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.Texto3,
        )
    }
}

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
                Text(Texto.nombre(elegido.descripcion), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7C2D12))
                Text("${elegido.codigo} · en ${Texto.unidad(elegido.unidad)}", fontSize = 13.sp, color = Color(0xFF7C2D12))
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
                    Text(Texto.nombre(ins.descripcion), fontSize = 15.sp, color = Paleta.Tinta)
                    Text("${ins.codigo} · ${Texto.unidad(ins.unidad)}", fontSize = 13.sp, color = Paleta.Texto3)
                }
            }
        }
    } else if (estado.texto.trim().length >= 2) {
        Text("Sin coincidencias en el celular. Actualiza el catálogo desde «Más» con señal.", fontSize = 14.sp, color = Paleta.Texto2)
    }
}

/** Confirmación común: guardado en el celular, «por enviar», y (si aún no se envía) el aviso del servicio almacén. */
@Composable
fun GuardadoEnCelular(
    titulo: String, detalle: String, textoOtro: String, alOtro: () -> Unit, alVolver: () -> Unit,
    conServicio: Boolean = false,
) {
    Column(
        Modifier.fillMaxWidth().vidrio(RoundedCornerShape(28.dp), fuerte = true, sombra = 16.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Paleta.Naranja, modifier = Modifier.size(56.dp))
        Text(titulo, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta, modifier = Modifier.semantics { heading() })
        Text(detalle, fontSize = 16.sp, lineHeight = 23.sp, color = Paleta.Texto3, textAlign = TextAlign.Center)
        ChipEstado(EstadoEnvio.PENDIENTE)
        if (conServicio) {
            Text("Se envía sola en cuanto haya señal.", fontSize = 14.sp, lineHeight = 20.sp, color = Paleta.Texto2, textAlign = TextAlign.Center)
        } else {
            AvisoSinServicioAlmacen()
        }
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
