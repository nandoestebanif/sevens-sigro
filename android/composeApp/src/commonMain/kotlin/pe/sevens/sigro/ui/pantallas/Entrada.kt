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
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RegistroAlmacen
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
 * 2.3 · Entrada de compra (lienzo Entrada.dc.html, HU-35 / HU-36), conectada (M5):
 *  - las compras llegan de GET /obras/{id}/compras y lo pendiente de cada una de
 *    GET /obras/{id}/almacen/compras/{compra_id}/recepcion (lo calcula el servidor);
 *  - lo recibido se guarda en el celular con su uuid_cliente y viaja por la misma cola sin señal a
 *    POST /obras/{id}/almacen/recepciones. «Llegó todo» va sin líneas: el servidor recibe lo pendiente.
 */

/**
 * HU-35: no se recibe más de lo pendiente (o de lo comprado, si no se sabe el pendiente); lo que falte queda
 * para otra entrega. Devuelve índice de línea → error; la clave -1 es un error de toda la entrada.
 */
fun validarRecepcion(compra: CompraParaRecibir, recibido: Map<Int, String>): Map<Int, String> = buildMap {
    compra.lineas.forEachIndexed { i, l ->
        val txt = recibido[i].orEmpty()
        val e = ValidarAlmacen.cantidad(txt, permitirCero = true)
        val tope = ValidarSalida.normalizarCantidad(l.recibible).toDoubleOrNull() ?: Double.MAX_VALUE
        when {
            txt.isBlank() -> {} // vacío = no llegó en esta entrega
            e != null -> put(i, e)
            (ValidarSalida.normalizarCantidad(txt).toDoubleOrNull() ?: 0.0) > tope ->
                put(
                    i,
                    if (l.pendiente != null) "Solo quedan ${l.pendiente} ${l.unidad} por recibir"
                    else "No se puede recibir más de lo comprado (${l.cantidad} ${l.unidad})",
                )
        }
    }
    val alguna = compra.lineas.indices.any { (ValidarSalida.normalizarCantidad(recibido[it].orEmpty()).toDoubleOrNull() ?: 0.0) > 0.0 }
    if (!alguna) put(-1, "Escribe al menos una cantidad recibida")
}

data class EstadoEntrada(
    val cargando: Boolean = false,
    val compras: List<CompraParaRecibir> = emptyList(),
    val aviso: String? = null,
    val compra: CompraParaRecibir? = null,
    /** Consultando lo pendiente de la compra elegida. */
    val consultando: Boolean = false,
    val llegoTodo: Boolean = true,
    val recibido: Map<Int, String> = emptyMap(),
    val errores: Map<Int, String> = emptyMap(),
    val guardando: Boolean = false,
    val guardada: Boolean = false,
) {
    /** Recibir una parte exige saber el insumo y el pendiente de cada línea (con señal). */
    val puedeParcial: Boolean get() = compra?.conPendientes == true
}

class EntradaViewModel(
    private val obra: RepositorioObra,
    private val registros: RepositorioRegistros,
    private val programador: ProgramadorSincronizacion,
) : ViewModel() {
    private val _e = MutableStateFlow(EstadoEntrada())
    val estado = _e.asStateFlow()
    val almacen = SelectorAlmacen(obra, viewModelScope)

    fun cargar(obraId: Int) {
        _e.update { it.copy(cargando = true, aviso = null) }
        almacen.cargar(obraId)
        viewModelScope.launch {
            try {
                val c = obra.compras(obraId)
                _e.update { it.copy(cargando = false, compras = c, aviso = if (c.isEmpty()) "No hay compras registradas por recibir en esta obra." else null) }
            } catch (x: ErrorSigro) {
                val msg = if (x.esTransitorio) "Las compras se cargan con señal. Vuelve a intentarlo cuando haya señal." else x.message
                _e.update { it.copy(cargando = false, aviso = msg) }
            }
        }
    }

    /**
     * Al elegir la compra se pide lo pendiente al servidor y se propone «llegó todo» (lo recibido = lo
     * pendiente). Sin señal se puede igual registrar «llegó todo»; recibir una parte espera a tener señal.
     */
    fun elegir(obraId: Int, c: CompraParaRecibir) {
        _e.update { it.copy(compra = c, consultando = true, llegoTodo = true, recibido = proponer(c, true), errores = emptyMap()) }
        viewModelScope.launch {
            val conPendientes = try { obra.conPendientes(obraId, c) } catch (x: ErrorSigro) { null }
            _e.update { s ->
                if (s.compra?.id != c.id) return@update s
                val compra = conPendientes ?: c
                s.copy(compra = compra, consultando = false, recibido = proponer(compra, s.llegoTodo))
            }
        }
    }

    fun cambiarCompra() = _e.update { it.copy(compra = null, consultando = false, recibido = emptyMap(), errores = emptyMap()) }

    fun elegirAlmacen(obraId: Int, a: AlmacenObra) {
        almacen.elegir(obraId, a)
        _e.update { it.copy(errores = it.errores - (-1)) }
    }

    fun alternarModo() = _e.update { s ->
        val c = s.compra ?: return@update s
        if (s.llegoTodo && !s.puedeParcial) return@update s // sin el pendiente del servidor no se puede partir
        val todo = !s.llegoTodo
        s.copy(llegoTodo = todo, recibido = proponer(c, todo), errores = emptyMap())
    }

    fun recibido(i: Int, v: String) = _e.update { it.copy(recibido = it.recibido + (i to v), errores = it.errores - i - (-1)) }

    fun guardar(obraId: Int) {
        val s = _e.value
        val c = s.compra ?: return
        val errores = when {
            c.recibidaCompleta -> mapOf(-1 to "Esta compra ya se recibió completa.")
            almacen.estado.value.falta -> mapOf(-1 to "Elige a qué almacén entra")
            else -> validarRecepcion(c, s.recibido)
        }
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val lineas = c.lineas.mapIndexedNotNull { i, l ->
                val v = s.recibido[i].orEmpty()
                if ((ValidarSalida.normalizarCantidad(v).toDoubleOrNull() ?: 0.0) > 0.0) l to v else null
            }
            registros.registrarAlmacen(obraId, RegistroAlmacen.Entrada(almacen.estado.value.elegido?.id, c, lineas, todo = s.llegoTodo))
            programador.programar() // si hay señal se envía ya; si no, cuando vuelva
            _e.update { it.copy(guardando = false, guardada = true) }
        }
    }

    fun otra() = _e.update { EstadoEntrada(compras = it.compras) }

    private fun proponer(c: CompraParaRecibir, todo: Boolean): Map<Int, String> =
        c.lineas.indices.associateWith { i -> if (todo) c.lineas[i].recibible else "" }
}

@Composable
fun PantallaEntrada(obraId: Int, alVolver: () -> Unit, vm: EntradaViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    val almacen by vm.almacen.estado.collectAsState()
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
            TextButton(onClick = vm::cambiarCompra) { Text("Cambiar de compra", color = Paleta.Naranja, fontWeight = FontWeight.Bold) }
            ElegirAlmacenObra(almacen, { vm.elegirAlmacen(obraId, it) })
        }
        if (compra.recibidaCompleta) AvisoNaranja("El servidor dice que esta compra ya se recibió completa.")
        if (e.consultando) CircularProgressIndicator(color = Paleta.Naranja)
        FormularioAlmacen {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { PasoAlmacen("2", "¿Qué llegó?") }
                TextButton(onClick = vm::alternarModo, enabled = !e.llegoTodo || e.puedeParcial) {
                    Text(if (e.llegoTodo) "Llegó todo · cambiar" else "Llegó una parte · cambiar", color = Paleta.Naranja, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            compra.lineas.forEachIndexed { i, l ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(l.descripcion, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
                    Text(
                        "Pedido ${l.cantidad} ${l.unidad} · " + (l.pendiente?.let { "pendiente $it" } ?: "pendiente: se ve con señal"),
                        fontSize = 13.sp, color = Paleta.Texto3,
                    )
                    if (e.llegoTodo) {
                        Text("Recibido: ${e.recibido[i].orEmpty()} ${l.unidad}", fontSize = 15.sp, color = Paleta.Tinta)
                    } else {
                        CampoVidrio(e.recibido[i].orEmpty(), { vm.recibido(i, it) }, "Recibido", teclado = KeyboardType.Decimal, sufijo = l.unidad, error = e.errores[i])
                    }
                }
            }
            Text(
                if (e.llegoTodo) "«Llegó todo» registra todo lo pendiente de la compra, aunque otra entrega haya llegado antes."
                else "No se puede recibir más de lo pendiente. Lo que falte queda para otra entrega.",
                fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.Texto3,
            )
            if (!e.puedeParcial && !e.consultando) {
                Text("Para recibir solo una parte hace falta señal: el servidor dice cuánto queda pendiente.", fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.Texto3)
            }
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
