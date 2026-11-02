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
 * Va por la cola sin señal a POST /obras/{id}/almacen/ingresos. Si la obra de origen está en SIGRO,
 * el servidor responde 422 `usar_traslado` y queda rechazado con ese motivo en «Mis registros».
 */

/** HU-41: exige insumo, cantidad y costo. Devuelve campo → mensaje. */
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
}

data class EstadoIngreso(
    val origen: OrigenIngreso = OrigenIngreso.DEVOLUCION,
    val insumo: Insumo? = null,
    val cantidad: String = "",
    val costo: String = "",
    val detalle: String = "",
    val almacenId: Int? = null,
    val errores: Map<String, String> = emptyMap(),
    val guardando: Boolean = false,
    val guardado: Boolean = false,
)

class IngresoSinCompraViewModel(
    catalogo: RepositorioCatalogo,
    private val obra: RepositorioObra,
    private val registros: RepositorioRegistros,
    private val programador: ProgramadorSincronizacion,
) : ViewModel() {
    val buscador = BuscadorInsumos(catalogo, viewModelScope)
    private val _e = MutableStateFlow(EstadoIngreso())
    val estado = _e.asStateFlow()

    fun cargar(obraId: Int) {
        viewModelScope.launch {
            val a = runCatching { obra.almacenes(obraId).firstOrNull()?.id }.getOrNull()
            _e.update { it.copy(almacenId = a) }
        }
    }

    fun origen(o: OrigenIngreso) = _e.update { it.copy(origen = o) }
    fun elegir(i: Insumo) { buscador.limpiar(); _e.update { it.copy(insumo = i, errores = it.errores - "insumo") } }
    fun quitar() = _e.update { it.copy(insumo = null) }
    fun cantidad(v: String) = _e.update { it.copy(cantidad = v, errores = it.errores - "cantidad") }
    fun costo(v: String) = _e.update { it.copy(costo = v, errores = it.errores - "costo") }
    fun detalle(v: String) = _e.update { it.copy(detalle = v, errores = it.errores - "detalle") }

    fun guardar(obraId: Int) {
        val s = _e.value
        val errores = validarIngreso(s.insumo, s.cantidad, s.costo, s.origen, s.detalle)
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            registros.registrarAlmacen(
                obraId,
                RegistroAlmacen.IngresoSinCompra(s.almacenId, s.origen, LineaAlmacen(s.insumo!!, s.cantidad), s.costo, s.detalle.trim().ifEmpty { null }),
            )
            programador.programar() // con señal se envía ya; si no, cuando vuelva
            _e.update { it.copy(guardando = false, guardado = true) }
        }
    }

    fun otro() = _e.update { EstadoIngreso(origen = it.origen, almacenId = it.almacenId) }
}

@Composable
fun PantallaIngresoSinCompra(obraId: Int, alVolver: () -> Unit, vm: IngresoSinCompraViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    val b by vm.buscador.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }

    PantallaVidrio("Ingreso sin compra", plano = { planoIngreso(it) }, alVolver = alVolver, subtitulo = "Material que llega sin una compra de por medio") {
        if (e.guardado) {
            GuardadoEnCelular(
                "Ingreso guardado", "El ingreso de ${e.cantidad} ${e.insumo?.unidad.orEmpty()} de ${e.insumo?.descripcion.orEmpty()} quedó en el celular con su código único.",
                "Registrar otro ingreso", vm::otro, alVolver, conServicio = true,
            )
            return@PantallaVidrio
        }
        FormularioAlmacen {
            PasoAlmacen("1", "¿De dónde viene?")
            Opciones(OrigenIngreso.entries, e.origen, { it.legible }, vm::origen)
            if (e.origen == OrigenIngreso.OTRA_OBRA) {
                AvisoNaranja("Solo si esa obra no está en SIGRO. Si está registrada, usa «Traslado entre obras» para que el costo viaje con el material.")
            }
        }
        FormularioAlmacen {
            PasoAlmacen("2", "¿Qué y cuánto?")
            ElegirInsumo(b, e.insumo, vm.buscador::buscar, vm::elegir, vm::quitar, error = e.errores["insumo"])
            CampoVidrio(e.cantidad, vm::cantidad, "Cantidad", teclado = KeyboardType.Decimal, sufijo = e.insumo?.unidad ?: "—", error = e.errores["cantidad"])
            CampoVidrio(
                e.costo, vm::costo, "Costo unitario", teclado = KeyboardType.Decimal, sufijo = "S/", error = e.errores["costo"],
                ayuda = "Obligatorio: sin él no se puede valorizar el material.",
            )
            CampoVidrio(
                e.detalle, vm::detalle,
                when (e.origen) { OrigenIngreso.OTRA_OBRA -> "Obra de origen *"; OrigenIngreso.OTRO -> "Detalle *"; else -> "Detalle (opcional)" },
                marcador = if (e.origen == OrigenIngreso.OTRA_OBRA) "Nombre de la obra" else "Ej.: devuelve la cuadrilla de acabados",
                error = e.errores["detalle"],
            )
        }
        BotonNegro("Guardar ingreso", { vm.guardar(obraId) }, icono = Icons.Filled.Save, colorIcono = Paleta.NaranjaVivo, cargando = e.guardando)
    }
}
