package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.ProgramadorSincronizacion
import pe.sevens.sigro.dominio.LineaAlmacen
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.RegistroAlmacen
import pe.sevens.sigro.dominio.RepositorioCatalogo
import pe.sevens.sigro.dominio.RepositorioObra
import pe.sevens.sigro.dominio.RepositorioRegistros
import pe.sevens.sigro.dominio.ValidarAlmacen
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.Separador
import pe.sevens.sigro.ui.componentes.planoTraslado
import pe.sevens.sigro.ui.tema.Paleta

/*
 * 2.9 · Traslado entre obras (lienzo Traslado.dc.html, HU-42): una salida aquí y un ingreso en la obra
 * destino con el mismo código de traslado; el costo viaja con el material (lo pone el servidor). El
 * disponible lo verifica el servidor. Va por la cola sin señal a POST /almacen/traslados; quien registra
 * debe estar asignado también en la obra destino (regla 9: si no, el servidor lo rechaza con su motivo).
 */

/** HU-42: destino, al menos un insumo y cada cantidad positiva. Clave "destino", "lineas" o el índice. */
fun validarTraslado(
    destino: ObraAsignada?, lineas: List<LineaAlmacen>,
    almacenDestino: AlmacenObra? = AlmacenObra(0, ""), motivo: String = "traslado",
): Map<String, String> = buildMap {
    if (destino == null) put("destino", "Elige la obra destino")
    else if (almacenDestino == null) put("almacenDestino", "Elige el almacén de destino (se cargan con señal)")
    if (motivo.trim().length < 3) put("motivo", "Di por qué se traslada (mínimo 3 letras)")
    if (lineas.isEmpty()) put("lineas", "Agrega al menos un insumo")
    lineas.forEachIndexed { i, l -> ValidarAlmacen.cantidad(l.cantidad)?.let { put("$i", it) } }
}

data class EstadoTraslado(
    val destinos: List<ObraAsignada> = emptyList(),
    val destino: ObraAsignada? = null,
    val almacenesDestino: List<AlmacenObra> = emptyList(),
    val almacenDestino: AlmacenObra? = null,
    val motivo: String = "",
    val lineas: List<LineaAlmacen> = emptyList(),
    val almacenId: Int? = null,
    val aviso: String? = null,
    val errores: Map<String, String> = emptyMap(),
    val guardando: Boolean = false,
    val guardado: Boolean = false,
)

class TrasladoViewModel(
    catalogo: RepositorioCatalogo,
    private val obra: RepositorioObra,
    private val registros: RepositorioRegistros,
    private val programador: ProgramadorSincronizacion,
) : ViewModel() {
    val buscador = BuscadorInsumos(catalogo, viewModelScope)
    private val _e = MutableStateFlow(EstadoTraslado())
    val estado = _e.asStateFlow()

    /** Destinos: GET /obras. Sin señal, las otras obras asignadas a la persona. */
    fun cargar(obraId: Int, asignadas: List<ObraAsignada>) {
        viewModelScope.launch {
            val r = runCatching { obra.obrasDestino(obraId) }
            val a = runCatching { obra.almacenes(obraId).firstOrNull()?.id }.getOrNull()
            val destinos = r.getOrNull() ?: asignadas.filter { it.id != obraId }
            _e.update {
                it.copy(
                    destinos = destinos, almacenId = a,
                    aviso = if (r.isFailure) "Sin señal: solo ves tus otras obras asignadas." else if (destinos.isEmpty()) "No hay otra obra registrada en SIGRO." else null,
                )
            }
        }
    }

    /** Al elegir la obra se cargan sus almacenes (GET /obras/{destino}/almacenes); con uno solo, se elige solo. */
    fun destino(o: ObraAsignada) {
        _e.update { it.copy(destino = o, almacenesDestino = emptyList(), almacenDestino = null, errores = it.errores - "destino" - "almacenDestino") }
        viewModelScope.launch {
            val r = runCatching { obra.almacenes(o.id) }
            _e.update { s ->
                if (s.destino?.id != o.id) s
                else r.fold(
                    { a -> s.copy(almacenesDestino = a, almacenDestino = a.singleOrNull()) },
                    { s.copy(errores = s.errores + ("almacenDestino" to "No se pudieron cargar los almacenes de esa obra. Con señal, vuelve a elegirla.")) },
                )
            }
        }
    }
    fun almacenDestino(a: AlmacenObra) = _e.update { it.copy(almacenDestino = a, errores = it.errores - "almacenDestino") }
    fun motivo(v: String) = _e.update { it.copy(motivo = v, errores = it.errores - "motivo") }
    fun agregar(i: Insumo) {
        buscador.limpiar()
        _e.update { s -> if (s.lineas.any { it.insumo.id == i.id }) s else s.copy(lineas = s.lineas + LineaAlmacen(i, ""), errores = s.errores - "lineas") }
    }
    fun cantidad(idx: Int, v: String) = _e.update { s -> s.copy(lineas = s.lineas.mapIndexed { i, l -> if (i == idx) l.copy(cantidad = v) else l }, errores = s.errores - "$idx") }
    fun quitar(idx: Int) = _e.update { s -> s.copy(lineas = s.lineas.filterIndexed { i, _ -> i != idx }, errores = emptyMap()) }

    fun guardar(obraId: Int) {
        val s = _e.value
        val errores = validarTraslado(s.destino, s.lineas, s.almacenDestino, s.motivo)
        if (errores.isNotEmpty()) { _e.update { it.copy(errores = errores) }; return }
        _e.update { it.copy(guardando = true) }
        viewModelScope.launch {
            registros.registrarAlmacen(
                obraId,
                RegistroAlmacen.Traslado(s.almacenId, s.destino!!.id, s.destino.nombre, s.almacenDestino!!.id, s.motivo.trim(), s.lineas),
            )
            programador.programar()
            _e.update { it.copy(guardando = false, guardado = true) }
        }
    }

    fun otro() = _e.update { EstadoTraslado(destinos = it.destinos, almacenId = it.almacenId) }
}

@Composable
fun PantallaTraslado(obraId: Int, obraNombre: String, asignadas: List<ObraAsignada>, alVolver: () -> Unit, vm: TrasladoViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    val b by vm.buscador.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId, asignadas) }

    PantallaVidrio("Traslado", plano = { planoTraslado(it) }, alVolver = alVolver, subtitulo = "Desde $obraNombre") {
        if (e.guardado) {
            GuardadoEnCelular(
                "Traslado guardado", "El traslado a ${e.destino?.nombre.orEmpty()} quedó en el celular con su código único.",
                "Registrar otro traslado", vm::otro, alVolver, conServicio = true,
            )
            return@PantallaVidrio
        }
        FormularioAlmacen {
            PasoAlmacen("1", "¿Hacia qué obra?")
            e.aviso?.let { Text(it, fontSize = 14.sp, color = Paleta.Texto2) }
            Opciones(e.destinos, e.destino, { it.nombre }, vm::destino)
            e.errores["destino"]?.let { Text(it, fontSize = 13.sp, color = Paleta.RechazadoTexto) }
            if (e.almacenesDestino.size > 1) {
                Text("Almacén de destino", fontSize = 14.sp, color = Paleta.Texto3)
                Opciones(e.almacenesDestino, e.almacenDestino, { it.nombre }, vm::almacenDestino)
            } else if (e.almacenDestino != null) {
                Text("Llega a: ${e.almacenDestino!!.nombre}", fontSize = 14.sp, color = Paleta.Texto2)
            }
            e.errores["almacenDestino"]?.let { Text(it, fontSize = 13.sp, color = Paleta.RechazadoTexto) }
            CampoVidrio(e.motivo, vm::motivo, "Motivo", marcador = "Ej.: la otra obra necesita cemento para vaciar", error = e.errores["motivo"])
        }
        FormularioAlmacen {
            PasoAlmacen("2", "Material que sale")
            e.lineas.forEachIndexed { i, l ->
                if (i > 0) Separador()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.insumo.descripcion, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Paleta.Tinta)
                            Text("Disponible [lo verifica el servidor]", fontSize = 13.sp, color = Paleta.Texto3)
                        }
                        IconButton(onClick = { vm.quitar(i) }) { Icon(Icons.Filled.Close, contentDescription = "Quitar ${l.insumo.descripcion}", tint = Paleta.Texto3) }
                    }
                    CampoVidrio(l.cantidad, { vm.cantidad(i, it) }, "Cantidad", teclado = KeyboardType.Decimal, sufijo = l.insumo.unidad, error = e.errores["$i"])
                }
            }
            ElegirInsumo(b, null, vm.buscador::buscar, vm::agregar, {}, error = e.errores["lineas"], etiqueta = if (e.lineas.isEmpty()) "Agregar insumo" else "Agregar otro insumo")
        }
        AvisoNaranja("Se registra una salida aquí y un ingreso en la obra destino con el mismo código de traslado. El costo unitario viaja con el material.")
        e.errores["lineas"]?.let { AvisoRojo(it) }
        BotonNegro("Registrar traslado", { vm.guardar(obraId) }, icono = Icons.Filled.SwapHoriz, colorIcono = Paleta.NaranjaVivo, cargando = e.guardando)
    }
}
