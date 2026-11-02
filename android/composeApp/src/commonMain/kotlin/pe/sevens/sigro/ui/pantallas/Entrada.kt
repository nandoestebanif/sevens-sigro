package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
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
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.PendienteRecepcion
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.ValidarAlmacen
import pe.sevens.sigro.dominio.ValidarSalida
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Marcador
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.planoEntrada
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 2.3 · Entrada de compra (lienzo Entrada.dc.html, HU-35 / HU-36). Las compras de la obra llegan de
 * GET /obras/{id}/compras y lo pendiente de cada línea, del servidor (GET …/compras/{id}/recepcion).
 * Lo recibido va por la cola sin señal a POST /almacen/recepciones con su uuid_cliente; «llegó todo»
 * viaja sin líneas y el servidor recibe el total pendiente. El costo lo pone el servidor.
 */

/**
 * HU-35: no se recibe más de lo comprado; lo que falte queda pendiente. Devuelve índice de línea → error;
 * la clave -1 es un error de toda la entrada.
 */
fun validarRecepcion(
    compra: CompraParaRecibir,
    recibido: Map<Int, String>,
    pendientes: Map<Int, PendienteRecepcion> = emptyMap(),
    todo: Boolean = false,
): Map<Int, String> = buildMap {
    if (todo) return@buildMap // sin líneas: el servidor recibe el total pendiente
    compra.lineas.forEachIndexed { i, l ->
        val txt = recibido[i].orEmpty()
        val e = ValidarAlmacen.cantidad(txt, permitirCero = true)
        val q = ValidarSalida.normalizarCantidad(txt).toDoubleOrNull() ?: 0.0
        val tope = l.insumoId?.let { pendientes[it] }
        when {
            txt.isBlank() -> {} // vacío = no llegó en esta entrega
            e != null -> put(i, e)
            tope != null && q > (tope.pendiente.toDoubleOrNull() ?: Double.MAX_VALUE) ->
                put(i, "Queda pendiente ${tope.pendiente} ${tope.unidad}: no se recibe más de lo comprado")
            q > (l.cantidad.toDoubleOrNull() ?: Double.MAX_VALUE) -> put(i, "No se puede recibir más de lo comprado (${l.cantidad} ${l.unidad})")
            q > 0.0 && l.insumoId == null -> put(i, "Este insumo no está en el catálogo del celular: actualízalo desde «Más» con señal")
        }
    }
    val alguna = todo || compra.lineas.indices.any { (ValidarSalida.normalizarCantidad(recibido[it].orEmpty()).toDoubleOrNull() ?: 0.0) > 0.0 }
    if (!alguna) put(-1, "Escribe al menos una cantidad recibida")
}

data class EstadoEntrada(
    val cargando: Boolean = false,
    val compras: List<CompraParaRecibir> = emptyList(),
    val aviso: String? = null,
    val compra: CompraParaRecibir? = null,
    val pendientes: Map<Int, PendienteRecepcion> = emptyMap(),
    val almacenId: Int? = null,
    val llegoTodo: Boolean = true,
    val recibido: Map<Int, String> = emptyMap(),
    val errores: Map<Int, String> = emptyMap(),
    val guardando: Boolean = false,
    val guardada: Boolean = false,
)

class EntradaViewModel(
    private val obra: RepositorioObra,
    private val registros: RepositorioRegistros,
    private val catalogo: RepositorioCatalogo,
    private val programador: ProgramadorSincronizacion,
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoEntrada())
    val estado = _e.asStateFlow()

    fun cargar(obraId: Int) {
        _e.update { it.copy(cargando = true, aviso = null) }
        viewModelScope.launch {
            try {
                val c = obra.compras(obraId)
                val a = runCatching { obra.almacenes(obraId).firstOrNull()?.id }.getOrNull()
                _e.update { it.copy(cargando = false, compras = c, almacenId = a, aviso = if (c.isEmpty()) "No hay compras registradas por recibir en esta obra." else null) }
            } catch (x: ErrorSigro) {
                val msg = if (x.esTransitorio) "Las compras se cargan con señal. Vuelve a intentarlo cuando haya señal." else x.message
                _e.update { it.copy(cargando = false, aviso = msg) }
            }
        }
    }

    /**
     * Al elegir la compra se propone «llegó todo». Cada línea busca su insumo en el catálogo del celular por el
     * código (la recepción lo pide por id) y, con señal, lo pendiente que dice el servidor.
     */
    fun elegir(obraId: Int, c: CompraParaRecibir) {
        _e.update { it.copy(compra = c, llegoTodo = true, recibido = c.lineas.indices.associateWith { i -> c.lineas[i].cantidad }, errores = emptyMap()) }
        viewModelScope.launch {
            val conId = c.copy(lineas = c.lineas.map { l ->
                l.copy(insumoId = runCatching { catalogo.buscar(l.insumoCodigo) }.getOrDefault(emptyList()).firstOrNull { it.codigo == l.insumoCodigo }?.id)
            })
            val pend = runCatching { obra.estadoRecepcion(obraId, c.id) }.getOrDefault(emptyList()).associateBy { it.insumoId }
            _e.update { s -> if (s.compra?.id != c.id) s else s.copy(compra = conId, pendientes = pend) }
        }
    }

    fun alternarModo() = _e.update { s ->
        val c = s.compra ?: return@update s
        val todo = !s.llegoTodo
        s.copy(llegoTodo = todo, recibido = c.lineas.indices.associateWith { i -> if (todo) c.lineas[i].cantidad else "" }, errores = emptyMap())
    }

    fun recibido(i: Int, v: String) = _e.update { it.copy(recibido = it.recibido + (i to v), errores = it.errores - i - (-1)) }

    fun guardar(obraId: Int) {
        val s = _e.value
        val c = s.compra ?: return
        val errores = validarRecepcion(c, s.recibido, s.pendientes, s.llegoTodo)
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val lineas = c.lineas.mapIndexedNotNull { i, l ->
                val v = s.recibido[i].orEmpty()
                if ((ValidarSalida.normalizarCantidad(v).toDoubleOrNull() ?: 0.0) > 0.0) l to v else null
            }
            registros.registrarAlmacen(obraId, RegistroAlmacen.Entrada(s.almacenId, c, lineas, todo = s.llegoTodo))
            programador.programar()
            _e.update { it.copy(guardando = false, guardada = true) }
        }
    }

    fun otra() = _e.update { EstadoEntrada(compras = it.compras, almacenId = it.almacenId) }
}

@Composable
fun PantallaEntrada(obraId: Int, alVolver: () -> Unit, vm: EntradaViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }

    PantallaVidrio("Entrada", plano = { planoEntrada(it) }, alVolver = alVolver, subtitulo = "Recibe lo que llegó de una compra") {
        if (e.guardada) {
            GuardadoEnCelular(
                "Entrada guardada", "Lo recibido de ${e.compra?.proveedor ?: "la compra"} quedó en el celular con su código único.",
                "Registrar otra entrada", vm::otra, alVolver, conServicio = true,
            )
            return@PantallaVidrio
        }
        val compra = e.compra
        if (compra == null) {
            TarjetaVidrio {
                PasoAlmacen("1", "¿De qué compra?")
                if (e.cargando) CircularProgressIndicator(color = Paleta.Naranja)
                e.aviso?.let { Text(it, fontSize = 15.sp, lineHeight = 21.sp, color = Paleta.Texto2) }
                e.compras.forEach { c ->
                    Column(
                        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                            .clickable(onClickLabel = "Recibir esta compra") { vm.elegir(obraId, c) }.heightIn(min = 60.dp).padding(14.dp)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(c.proveedor, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                        Text("${c.comprobante} · ${c.fecha} · ${c.lineas.size} ${if (c.lineas.size == 1) "insumo" else "insumos"}", fontSize = 13.sp, color = Paleta.Texto3)
                    }
                }
                if (!e.cargando && e.compras.isEmpty()) TextButton(onClick = { vm.cargar(obraId) }) { Text("Volver a intentar", color = Paleta.Naranja, fontWeight = FontWeight.Bold) }
            }
            return@PantallaVidrio
        }
        TarjetaVidrio {
            Text("Compra", fontSize = 13.sp, color = Paleta.Texto3)
            Text("${compra.proveedor} · ${compra.fecha}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
            Text("${compra.comprobante} · ${compra.lineas.size} insumos", fontSize = 14.sp, color = Paleta.Texto2)
        }
        FormularioAlmacen {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { PasoAlmacen("2", "¿Qué llegó?") }
                TextButton(onClick = vm::alternarModo) {
                    Text(if (e.llegoTodo) "Llegó todo · cambiar" else "Llegó una parte · cambiar", color = Paleta.Naranja, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            compra.lineas.forEachIndexed { i, l ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(l.descripcion, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
                    val p = l.insumoId?.let { e.pendientes[it] }
                    Text(
                        "Pedido ${l.cantidad} ${l.unidad} · pendiente " + (p?.let { "${it.pendiente} ${it.unidad} (del servidor)" } ?: "[se ve con señal]"),
                        fontSize = 13.sp, color = Paleta.Texto3,
                    )
                    CampoVidrio(e.recibido[i].orEmpty(), { vm.recibido(i, it) }, "Recibido", teclado = KeyboardType.Decimal, sufijo = l.unidad, error = e.errores[i])
                }
            }
            Text("No se puede recibir más de lo comprado. Lo que falte queda pendiente para otra entrega.", fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.Texto3)
        }
        TarjetaVidrio {
            PasoAlmacen("3", "Foto de la guía de remisión")
            Marcador("Foto de la guía: llega con la cámara del celular (recomendada, no obligatoria)")
        }
        e.errores[-1]?.let { AvisoRojo(it) }
        BotonNegro("Guardar entrada", { vm.guardar(obraId) }, icono = Icons.Filled.Save, colorIcono = Paleta.NaranjaVivo, cargando = e.guardando)
        AvisoNaranja("El costo de la entrada sale de la compra y lo calcula el servidor (costo promedio ponderado).")
    }
}
