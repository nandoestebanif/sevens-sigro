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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.CompraParaRecibir
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.ExcesoRequerido
import pe.sevens.sigro.dominio.LineaCompra
import pe.sevens.sigro.dominio.MotivoExceso
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.ValidarAlmacen
import pe.sevens.sigro.dominio.ValidarSalida
import pe.sevens.sigro.dominio.VerificacionRecepcion
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Marcador
import pe.sevens.sigro.ui.componentes.Opciones
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
 *  - HU-36 (D11, T-298): antes de guardar, con señal, se pregunta al servidor si con esta entrada se pasa lo
 *    requerido en el S10 (POST …/recepciones/verificacion). Si se pasa, se AVISA con sus cifras y se pide el
 *    motivo (regla 7: avisa, no bloquea); el motivo viaja como `motivo_exceso`. Pasar lo COMPRADO sí se bloquea
 *    (HU-35, `validarRecepcion`). Sin señal se guarda igual: si al enviarse el servidor encuentra exceso sin motivo,
 *    el registro queda rechazado con su mensaje en «Mis registros» (HU-38) y se vuelve a registrar con el motivo.
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
    /** HU-36: consultando al servidor si se pasa lo requerido. */
    val verificando: Boolean = false,
    /** HU-36: la respuesta del servidor cuando SÍ se pasa; mientras esté, guardar exige motivo. */
    val exceso: VerificacionRecepcion? = null,
    val motivo: MotivoExceso? = null,
    val motivoDetalle: String = "",
    /** Se guardó sin poder revisar lo requerido (sin señal). */
    val sinVerificar: Boolean = false,
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

    fun cambiarCompra() = _e.update { it.copy(compra = null, consultando = false, recibido = emptyMap(), errores = emptyMap()).sinExceso() }

    fun elegirAlmacen(obraId: Int, a: AlmacenObra) {
        almacen.elegir(obraId, a)
        _e.update { it.copy(errores = it.errores - (-1)) }
    }

    fun alternarModo() = _e.update { s ->
        val c = s.compra ?: return@update s
        if (s.llegoTodo && !s.puedeParcial) return@update s // sin el pendiente del servidor no se puede partir
        val todo = !s.llegoTodo
        s.copy(llegoTodo = todo, recibido = proponer(c, todo), errores = emptyMap()).sinExceso()
    }

    /** Cambiar una cantidad obliga a volver a preguntar al servidor (el aviso anterior ya no vale). */
    fun recibido(i: Int, v: String) = _e.update { it.copy(recibido = it.recibido + (i to v), errores = it.errores - i - (-1)).sinExceso() }

    fun motivo(m: MotivoExceso) = _e.update { it.copy(motivo = m, errores = it.errores - ERROR_MOTIVO) }
    fun motivoDetalle(v: String) = _e.update { it.copy(motivoDetalle = v, errores = it.errores - ERROR_MOTIVO) }

    /**
     * «No, recibir solo hasta lo requerido»: pasa a «llegó una parte» con lo recibido de cada línea que se pasa
     * bajado a lo que falta para el requerido. Se vuelve a verificar al guardar.
     */
    fun hastaLoRequerido() = _e.update { s ->
        val c = s.compra ?: return@update s
        val v = s.exceso ?: return@update s
        if (!c.conPendientes) return@update s.copy(errores = s.errores + (-1 to "Para recibir solo una parte hace falta señal."))
        val base = if (s.llegoTodo) proponer(c, true) else s.recibido
        s.copy(llegoTodo = false, recibido = base + ExcesoRequerido.hastaLoRequerido(c.lineas, base, v), errores = emptyMap()).sinExceso()
    }

    fun guardar(obraId: Int) {
        val s = _e.value
        val c = s.compra ?: return
        if (s.verificando || s.guardando) return
        val errores = when {
            c.recibidaCompleta -> mapOf(-1 to "Esta compra ya se recibió completa.")
            almacen.estado.value.falta -> mapOf(-1 to "Elige a qué almacén entra")
            else -> validarRecepcion(c, s.recibido)
        }
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        val lineas = c.lineas.mapIndexedNotNull { i, l ->
            val v = s.recibido[i].orEmpty()
            if ((ValidarSalida.normalizarCantidad(v).toDoubleOrNull() ?: 0.0) > 0.0) l to v else null
        }
        // Ya se avisó que se pasa lo requerido: ahora solo falta el motivo.
        if (s.exceso != null) {
            val m = ExcesoRequerido.motivo(s.motivo, s.motivoDetalle)
            if (m == null) {
                val msg = if (s.motivo == MotivoExceso.OTRO) "Escribe el motivo" else "Elige por qué se necesita más de lo requerido"
                _e.update { it.copy(errores = it.errores + (ERROR_MOTIVO to msg)) }
                return
            }
            return guardarEnCelular(obraId, c, lineas, s.llegoTodo, m, sinVerificar = false)
        }
        _e.update { it.copy(verificando = true, errores = emptyMap()) }
        viewModelScope.launch {
            val consulta = if (s.llegoTodo) null else lineas.map { (l, v) -> requireNotNull(l.insumoId) to v }
            val v = try {
                Resultado.Listo(obra.verificarRecepcion(obraId, c.id, consulta))
            } catch (x: ErrorSigro) {
                if (x.esTransitorio) Resultado.SinSenal else Resultado.Rechazo(x.message)
            }
            when (v) {
                is Resultado.Listo -> if (v.verificacion?.hayExceso == true) {
                    _e.update { it.copy(verificando = false, exceso = v.verificacion, motivo = null, motivoDetalle = "") }
                } else {
                    _e.update { it.copy(verificando = false) }
                    guardarEnCelular(obraId, c, lineas, s.llegoTodo, null, sinVerificar = false)
                }
                Resultado.SinSenal -> {
                    _e.update { it.copy(verificando = false) }
                    guardarEnCelular(obraId, c, lineas, s.llegoTodo, null, sinVerificar = true)
                }
                // 409 ya recibida, 422 se pasa de lo comprado…: el servidor manda; no se guarda.
                is Resultado.Rechazo -> _e.update { it.copy(verificando = false, errores = mapOf(-1 to v.mensaje)) }
            }
        }
    }

    private fun guardarEnCelular(
        obraId: Int, c: CompraParaRecibir, lineas: List<Pair<LineaCompra, String>>, todo: Boolean, motivo: String?, sinVerificar: Boolean,
    ) {
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            registros.registrarAlmacen(obraId, RegistroAlmacen.Entrada(almacen.estado.value.elegido?.id, c, lineas, todo = todo, motivoExceso = motivo))
            programador.programar() // si hay señal se envía ya; si no, cuando vuelva
            _e.update { it.copy(guardando = false, guardada = true, sinVerificar = sinVerificar) }
        }
    }

    fun otra() = _e.update { EstadoEntrada(compras = it.compras) }

    private fun proponer(c: CompraParaRecibir, todo: Boolean): Map<Int, String> =
        c.lineas.indices.associateWith { i -> if (todo) c.lineas[i].recibible else "" }

    private fun EstadoEntrada.sinExceso() = copy(exceso = null, motivo = null, motivoDetalle = "", errores = errores - ERROR_MOTIVO)

    private sealed interface Resultado {
        data class Listo(val verificacion: VerificacionRecepcion?) : Resultado
        data object SinSenal : Resultado
        data class Rechazo(val mensaje: String) : Resultado
    }

    companion object {
        /** Clave de error del motivo del exceso (las líneas usan su índice; -1 es toda la entrada). */
        const val ERROR_MOTIVO = -2
    }
}

/** HU-36: el aviso del lienzo («Pasa lo requerido»), con las cifras del servidor y el motivo. */
@Composable
private fun AvisoExceso(e: EstadoEntrada, compra: CompraParaRecibir, vm: EntradaViewModel) {
    val v = e.exceso ?: return
    val nombres = compra.lineas.filter { it.insumoId != null }.associate { it.insumoId!! to Texto.nombre(it.descripcion) }
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFEF3C7), RoundedCornerShape(18.dp)).padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val excedidas = v.excedidas
        Text(
            if (excedidas.size == 1) "${nombres[excedidas[0].insumoId] ?: excedidas[0].insumoCodigo} pasa lo requerido en el S10 en " +
                "${ExcesoRequerido.fmt(excedidas[0].exceso)} ${excedidas[0].unidad}"
            else "${excedidas.size} insumos pasan lo requerido en el S10",
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E),
        )
        excedidas.forEach { l ->
            Text(ExcesoRequerido.detalle(l, nombres[l.insumoId] ?: l.insumoCodigo), fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF78350F))
        }
        Text(
            "Puedes seguir: entra al stock al costo de la compra y queda marcado con tu motivo para el residente.",
            fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF78350F),
        )
        Text("¿Por qué se necesita más de lo requerido?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF78350F))
        Opciones(MotivoExceso.entries, e.motivo, { it.legible }, vm::motivo)
        if (e.motivo == MotivoExceso.OTRO) {
            CampoVidrio(e.motivoDetalle, vm::motivoDetalle, "Motivo", error = e.errores[EntradaViewModel.ERROR_MOTIVO])
        } else {
            e.errores[EntradaViewModel.ERROR_MOTIVO]?.let { Text(it, fontSize = 13.sp, color = Paleta.RechazadoTexto, fontWeight = FontWeight.SemiBold) }
        }
        TextButton(onClick = vm::hastaLoRequerido, enabled = compra.conPendientes) {
            Text("No, recibir solo hasta lo requerido", color = Paleta.Naranja, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun PantallaEntrada(obraId: Int, alVolver: () -> Unit, vm: EntradaViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    val almacen by vm.almacen.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }

    PantallaVidrio("Entrada", plano = { planoEntrada(it) }, alVolver = alVolver, subtitulo = "Lo que llegó de una compra") {
        if (e.guardada) {
            GuardadoEnCelular(
                "Entrada guardada",
                "Lo recibido de ${e.compra?.proveedor ?: "la compra"} quedó en el celular con su código único." +
                    (if (e.sinVerificar) " Sin señal no se pudo revisar lo requerido en el S10: si se pasa, «Mis registros» te pedirá registrarla de nuevo con el motivo." else ""),
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
                        Text("${c.comprobante} · ${Texto.fecha(c.fecha)} · ${c.lineas.size} ${if (c.lineas.size == 1) "insumo" else "insumos"}", fontSize = 13.sp, color = Paleta.Texto3)
                    }
                }
                if (!e.cargando && e.compras.isEmpty()) TextButton(onClick = { vm.cargar(obraId) }) { Text("Volver a intentar", color = Paleta.Naranja, fontWeight = FontWeight.Bold) }
            }
            return@PantallaVidrio
        }
        TarjetaVidrio {
            Text("Compra", fontSize = 13.sp, color = Paleta.Texto3)
            Text("${compra.proveedor} · ${Texto.fecha(compra.fecha)}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
            Text("${compra.comprobante} · ${compra.lineas.size} insumos", fontSize = 14.sp, color = Paleta.Texto2)
            TextButton(onClick = vm::cambiarCompra) { Text("Cambiar de compra", color = Paleta.Naranja, fontWeight = FontWeight.Bold) }
            ElegirAlmacenObra(almacen, { vm.elegirAlmacen(obraId, it) })
        }
        if (compra.recibidaCompleta) AvisoNaranja("Esta compra ya se recibió completa.")
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
                    Text(Texto.nombre(l.descripcion), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
                    Text(
                        "Pedido ${Texto.cantidadConUnidad(l.cantidad, l.unidad)} · " + (l.pendiente?.let { "faltan ${Texto.cantidad(it)}" } ?: "lo que falta se ve con señal"),
                        fontSize = 13.sp, color = Paleta.Texto3,
                    )
                    if (e.llegoTodo) {
                        Text("Recibido: ${Texto.cantidadConUnidad(e.recibido[i].orEmpty(), l.unidad)}", fontSize = 15.sp, color = Paleta.Tinta)
                    } else {
                        CampoVidrio(e.recibido[i].orEmpty(), { vm.recibido(i, it) }, "Recibido", teclado = KeyboardType.Decimal, sufijo = Texto.unidad(l.unidad), error = e.errores[i])
                    }
                }
            }
            Text(
                (if (e.llegoTodo) "«Llegó todo» registra todo lo pendiente de la compra, aunque otra entrega haya llegado antes."
                else "No se puede recibir más de lo pendiente. Lo que falte queda para otra entrega.") +
                    " Si con lo recibido se pasa lo requerido en el S10, se avisa antes de guardar y se pide el motivo.",
                fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.Texto3,
            )
            AvisoExceso(e, compra, vm)
            if (!e.puedeParcial && !e.consultando) {
                Text("Para recibir solo una parte hace falta señal (para saber cuánto falta).", fontSize = 13.sp, lineHeight = 18.sp, color = Paleta.Texto3)
            }
        }
        e.errores[-1]?.let { AvisoRojo(it) }
        BotonNegro(
            if (e.exceso != null) "Guardar con el motivo" else "Guardar entrada", { vm.guardar(obraId) },
            icono = Icons.Filled.Save, colorIcono = Paleta.NaranjaVivo, cargando = e.guardando || e.verificando,
        )
    }
}
