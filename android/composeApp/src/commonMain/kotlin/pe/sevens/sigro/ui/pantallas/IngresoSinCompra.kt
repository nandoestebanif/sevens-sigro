package pe.sevens.sigro.ui.pantallas

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.dominio.AlmacenObra
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.LineaAlmacen
import pe.sevens.sigro.dominio.OrigenIngreso
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.ValidarAlmacen
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.planoIngreso
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 2.4 · Ingreso sin compra (lienzo IngresoSinCompra.dc.html, HU-41): material que llega sin compra
 * (devolución, préstamo, sobrante…). El costo unitario es obligatorio: sin él no se valoriza.
 * Se guarda en el celular y se envía cuando exista el servicio almacén.
 */

/**
 * HU-41: exige insumo, cantidad y costo; «otro» y «saldo de otra obra» exigen además su detalle (para la otra
 * obra, su nombre), igual que `IngresoIn` en el servidor. Devuelve campo → mensaje.
 */
fun validarIngreso(
    insumo: Insumo?, cantidad: String, costo: String,
    origen: OrigenIngreso = OrigenIngreso.DEVOLUCION, detalle: String = "",
): Map<String, String> = buildMap {
    if (insumo == null) put("insumo", "Elige el insumo que ingresa")
    ValidarAlmacen.cantidad(cantidad)?.let { put("cantidad", it) }
    ValidarAlmacen.costo(costo)?.let { put("costo", it) }
    if (origen.exigeDetalle && detalle.isBlank()) {
        put("detalle", if (origen == OrigenIngreso.OTRA_OBRA) "Escribe el nombre de la obra de donde viene" else "Di de dónde viene")
    }
    if (detalle.trim().length > 300) put("detalle", "Máximo 300 letras")
}

data class EstadoIngreso(
    val origen: OrigenIngreso = OrigenIngreso.DEVOLUCION,
    val insumo: Insumo? = null,
    val cantidad: String = "",
    val costo: String = "",
    val detalle: String = "",
    val errores: Map<String, String> = emptyMap(),
    val guardando: Boolean = false,
    val guardado: Boolean = false,
)

class IngresoSinCompraViewModel(
    catalogo: RepositorioCatalogo,
    obra: RepositorioObra,
    private val registros: RepositorioRegistros,
    private val programador: ProgramadorSincronizacion,
) : ViewModel() {
    val buscador = BuscadorInsumos(catalogo, viewModelScope)
    private val _e = MutableStateFlow(EstadoIngreso())
    val estado = _e.asStateFlow()
    val almacen = SelectorAlmacen(obra, viewModelScope)

    fun cargar(obraId: Int) = almacen.cargar(obraId)
    fun elegirAlmacen(obraId: Int, a: AlmacenObra) {
        almacen.elegir(obraId, a)
        _e.update { it.copy(errores = it.errores - "almacen") }
    }

    fun origen(o: OrigenIngreso) = _e.update { it.copy(origen = o, errores = it.errores - "detalle") }
    fun elegir(i: Insumo) { buscador.limpiar(); _e.update { it.copy(insumo = i, errores = it.errores - "insumo") } }
    fun quitar() = _e.update { it.copy(insumo = null) }
    fun cantidad(v: String) = _e.update { it.copy(cantidad = v, errores = it.errores - "cantidad") }
    fun costo(v: String) = _e.update { it.copy(costo = v, errores = it.errores - "costo") }
    fun detalle(v: String) = _e.update { it.copy(detalle = v, errores = it.errores - "detalle") }

    fun guardar(obraId: Int) {
        val s = _e.value
        val alm = almacen.estado.value
        val errores = validarIngreso(s.insumo, s.cantidad, s.costo, s.origen, s.detalle) +
            (if (alm.falta) mapOf("almacen" to "Elige a qué almacén entra") else emptyMap())
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            registros.registrarAlmacen(
                obraId,
                RegistroAlmacen.IngresoSinCompra(alm.elegido?.id, s.origen, LineaAlmacen(s.insumo!!, s.cantidad), s.costo, s.detalle.trim().ifEmpty { null }),
            )
            programador.programar() // A5: viaja por la misma cola: si hay señal se envía ya; si no, cuando vuelva
            _e.update { it.copy(guardando = false, guardado = true) }
        }
    }

    fun otro() = _e.update { EstadoIngreso(origen = it.origen) }
}

@Composable
fun PantallaIngresoSinCompra(obraId: Int, alVolver: () -> Unit, vm: IngresoSinCompraViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    val b by vm.buscador.estado.collectAsState()
    val almacen by vm.almacen.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }

    PantallaVidrio("Ingreso sin compra", plano = { planoIngreso(it) }, alVolver = alVolver, subtitulo = "Devoluciones, préstamos y sobrantes") {
        if (e.guardado) {
            GuardadoEnCelular(
                "Ingreso guardado", "${e.insumo?.let { Texto.cantidadConUnidad(e.cantidad, it.unidad) + " de " + Texto.nombre(it.descripcion) } ?: "El ingreso"}. Se envía solo en cuanto haya señal.",
                "Registrar otro ingreso", vm::otro, alVolver, conServicio = true,
            )
            return@PantallaVidrio
        }
        FormularioAlmacen {
            PasoAlmacen("1", "¿De dónde viene?")
            ElegirAlmacenObra(almacen, { vm.elegirAlmacen(obraId, it) }, e.errores["almacen"])
            Opciones(OrigenIngreso.entries, e.origen, { it.legible }, vm::origen)
            if (e.origen == OrigenIngreso.OTRA_OBRA) {
                AvisoNaranja("Solo si esa obra no está en SIGRO.")
            }
            if (e.origen.exigeDetalle) {
                CampoVidrio(
                    e.detalle, vm::detalle, if (e.origen == OrigenIngreso.OTRA_OBRA) "Nombre de la obra" else "¿De dónde viene?",
                    error = e.errores["detalle"],
                )
            }
        }
        FormularioAlmacen {
            PasoAlmacen("2", "¿Qué y cuánto?")
            ElegirInsumo(b, e.insumo, vm.buscador::buscar, vm::elegir, vm::quitar, error = e.errores["insumo"])
            CampoVidrio(e.cantidad, vm::cantidad, "Cantidad", teclado = KeyboardType.Decimal, sufijo = e.insumo?.let { Texto.unidad(it.unidad) } ?: "", error = e.errores["cantidad"])
            CampoVidrio(
                e.costo, vm::costo, "Costo unitario sin IGV", teclado = KeyboardType.Decimal, sufijo = "S/", error = e.errores["costo"],
                ayuda = "Obligatorio. Por unidad del S10 (para el acero, por kg).",
            )
            if (!e.origen.exigeDetalle) {
                CampoVidrio(e.detalle, vm::detalle, "Detalle (opcional)", marcador = "Ej.: devuelve la cuadrilla de acabados", error = e.errores["detalle"])
            }
        }
        BotonNegro("Guardar ingreso", { vm.guardar(obraId) }, icono = Icons.Filled.Save, colorIcono = Paleta.NaranjaVivo, cargando = e.guardando)
    }
}
