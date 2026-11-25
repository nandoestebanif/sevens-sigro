package pe.sevens.sigro.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoDevolucion
import pe.sevens.sigro.dominio.HerramientaDisponible
import pe.sevens.sigro.dominio.HoraLima
import pe.sevens.sigro.dominio.Limites
import pe.sevens.sigro.dominio.PanelHerramientas
import pe.sevens.sigro.dominio.PrestamoHerramienta
import pe.sevens.sigro.dominio.RepositorioPrestamos
import pe.sevens.sigro.dominio.Texto
import pe.sevens.sigro.dominio.ValidarPrestamo
import pe.sevens.sigro.plataforma.Plataforma
import pe.sevens.sigro.ui.componentes.AvisoNaranja
import pe.sevens.sigro.ui.componentes.AvisoRojo
import pe.sevens.sigro.ui.componentes.BotonNegro
import pe.sevens.sigro.ui.componentes.CampoVidrio
import pe.sevens.sigro.ui.componentes.Modulo
import pe.sevens.sigro.ui.componentes.Opciones
import pe.sevens.sigro.ui.componentes.PantallaVidrio
import pe.sevens.sigro.ui.componentes.TarjetaVidrio
import pe.sevens.sigro.ui.componentes.TituloSeccion
import pe.sevens.sigro.ui.componentes.planoStock
import pe.sevens.sigro.ui.tema.Paleta

/* Préstamo y devolución de herramientas y equipos. Con señal: el servidor decide si hay disponible. */

enum class ModoHerramientas { LISTA, PRESTAR, DEVOLVER }

/** Fechas rápidas para «devolver el»: en obra no se escribe una fecha, se elige. */
enum class PlazoDevolucion(val legible: String, val dias: Int?) { HOY("Hoy", 0), MANANA("Mañana", 1), SEMANA("En 7 días", 7), SIN("Sin fecha", null) }

data class EstadoHerramientas(
    val cargando: Boolean = false,
    val panel: PanelHerramientas? = null,
    val error: String? = null,
    val aviso: String? = null,
    val modo: ModoHerramientas = ModoHerramientas.LISTA,
    // prestar
    val herramienta: HerramientaDisponible? = null,
    val cantidad: String = "1",
    val responsable: String = "",
    val plazo: PlazoDevolucion = PlazoDevolucion.SIN,
    val nota: String = "",
    // devolver
    val devolviendo: PrestamoHerramienta? = null,
    val estadoDevolucion: EstadoDevolucion = EstadoDevolucion.BUENO,
    val notaDevolucion: String = "",
    val errores: Map<String, String> = emptyMap(),
    val guardando: Boolean = false,
)

class HerramientasViewModel(private val repo: RepositorioPrestamos) : ViewModel() {
    private val _e = MutableStateFlow(EstadoHerramientas())
    val estado = _e.asStateFlow()

    fun cargar(obraId: Int) {
        _e.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val p = repo.panel(obraId)
                _e.update { it.copy(cargando = false, panel = p) }
            } catch (x: ErrorSigro) {
                _e.update { it.copy(cargando = false, error = if (x.esTransitorio) "Los préstamos se ven y se registran con señal." else x.message) }
            }
        }
    }

    fun abrirPrestar() = _e.update {
        it.copy(modo = ModoHerramientas.PRESTAR, herramienta = it.panel?.herramientas?.firstOrNull { h -> (h.disponible.toDoubleOrNull() ?: 0.0) > 0 },
            cantidad = "1", responsable = "", plazo = PlazoDevolucion.SIN, nota = "", errores = emptyMap(), aviso = null)
    }
    fun abrirDevolver(p: PrestamoHerramienta) = _e.update {
        it.copy(modo = ModoHerramientas.DEVOLVER, devolviendo = p, estadoDevolucion = EstadoDevolucion.BUENO, notaDevolucion = "", errores = emptyMap(), aviso = null)
    }
    fun volver() = _e.update { it.copy(modo = ModoHerramientas.LISTA, errores = emptyMap()) }
    fun herramienta(h: HerramientaDisponible) = _e.update { it.copy(herramienta = h, errores = it.errores - "herramienta") }
    fun cantidad(v: String) = _e.update { it.copy(cantidad = Limites.cantidad(it.cantidad, v), errores = it.errores - "cantidad") }
    fun responsable(v: String) = _e.update { it.copy(responsable = Limites.texto(v, Limites.QUIEN_RETIRA), errores = it.errores - "responsable") }
    fun plazo(p: PlazoDevolucion) = _e.update { it.copy(plazo = p) }
    fun nota(v: String) = _e.update { it.copy(nota = Limites.texto(v, Limites.MOTIVO)) }
    fun estadoDevolucion(s: EstadoDevolucion) = _e.update { it.copy(estadoDevolucion = s, errores = emptyMap()) }
    fun notaDevolucion(v: String) = _e.update { it.copy(notaDevolucion = Limites.texto(v, Limites.MOTIVO), errores = emptyMap()) }

    fun prestar(obraId: Int) {
        val s = _e.value
        val err = ValidarPrestamo.errores(s.herramienta, s.cantidad, s.responsable)
        if (err.isNotEmpty()) { _e.update { it.copy(errores = err) }; return }
        val h = s.herramienta ?: return
        val fecha = s.plazo.dias?.let { Plataforma.fechaIso(Plataforma.ahoraMs() + it * 86_400_000L) }
        _e.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            try {
                val p = repo.prestar(obraId, h, s.cantidad, s.responsable, fecha, s.nota)
                _e.update { it.copy(guardando = false, modo = ModoHerramientas.LISTA, aviso = "Prestado: ${Texto.cantidadConUnidad(p.cantidad, p.unidad)} de ${Texto.nombre(p.descripcion)} a ${p.responsable}.") }
                cargar(obraId)
            } catch (x: ErrorSigro) {
                _e.update { it.copy(guardando = false, error = if (x.esTransitorio) "Sin señal: el préstamo se registra cuando vuelva la señal (el servidor revisa lo disponible)." else x.message) }
            }
        }
    }

    fun devolver(obraId: Int) {
        val s = _e.value
        val p = s.devolviendo ?: return
        ValidarPrestamo.errorDevolucion(s.estadoDevolucion, s.notaDevolucion)?.let { m -> _e.update { it.copy(errores = mapOf("nota" to m)) }; return }
        _e.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            try {
                val adv = repo.devolver(obraId, p.id, s.estadoDevolucion, s.notaDevolucion)
                _e.update { it.copy(guardando = false, modo = ModoHerramientas.LISTA, aviso = (listOf("Devuelta: ${Texto.nombre(p.descripcion)}.") + adv).joinToString(" ")) }
                cargar(obraId)
            } catch (x: ErrorSigro) {
                _e.update { it.copy(guardando = false, error = if (x.esTransitorio) "Sin señal: intenta de nuevo cuando vuelva." else x.message) }
            }
        }
    }
}

@Composable
fun PantallaHerramientas(obraId: Int, alVolver: () -> Unit, vm: HerramientasViewModel = koinViewModel()) {
    val e by vm.estado.collectAsState()
    LaunchedEffect(obraId) { vm.cargar(obraId) }
    val volver = if (e.modo == ModoHerramientas.LISTA) alVolver else vm::volver
    val subtitulo = when (e.modo) {
        ModoHerramientas.LISTA -> e.panel?.let { p -> "${p.sinDevolver.size} prestadas" + if (p.atrasados > 0) " · ${p.atrasados} atrasadas" else "" } ?: "Quién tiene cada herramienta"
        ModoHerramientas.PRESTAR -> "Prestar una herramienta"
        ModoHerramientas.DEVOLVER -> "Registrar la devolución"
    }
    PantallaVidrio(
        "Herramientas", plano = { planoStock(it) }, alVolver = volver, subtitulo = subtitulo, modulo = Modulo.STOCK,
        actualizando = e.cargando, alActualizar = if (e.modo == ModoHerramientas.LISTA) ({ vm.cargar(obraId) }) else null,
    ) {
        e.aviso?.let { Text(it, fontSize = 15.sp, color = Paleta.Tinta, fontWeight = FontWeight.SemiBold) }
        e.error?.let { AvisoRojo(it) }
        when (e.modo) {
            ModoHerramientas.LISTA -> Lista(e, vm)
            ModoHerramientas.PRESTAR -> FormPrestar(e, vm, obraId)
            ModoHerramientas.DEVOLVER -> FormDevolver(e, vm, obraId)
        }
    }
}

@Composable
private fun Lista(e: EstadoHerramientas, vm: HerramientasViewModel) {
    val p = e.panel ?: return
    BotonNegro("Prestar herramienta", vm::abrirPrestar, icono = Icons.Filled.Build, habilitado = p.herramientas.isNotEmpty())
    Text("Prestar no mueve el saldo: la herramienta sigue en la obra. Desliza hacia abajo para actualizar.", fontSize = 13.sp, color = Paleta.Texto2)
    TituloSeccion("Prestadas ahora")
    if (p.sinDevolver.isEmpty()) Text("Todas las herramientas están en el almacén.", fontSize = 15.sp, color = Paleta.Texto2)
    p.sinDevolver.forEach { x ->
        TarjetaVidrio(Modifier.semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Texto.nombre(x.descripcion), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
                    Text("${Texto.cantidadConUnidad(x.cantidad, x.unidad)} · ${x.responsable}", fontSize = 14.sp, color = Paleta.Tinta)
                    Text("Desde ${HoraLima.deIso(x.prestadoEn) ?: x.prestadoEn}" + (x.devolverEl?.let { " · devolver el ${Texto.fecha(it)}" } ?: ""),
                        fontSize = 13.sp, color = if (x.atrasado) Paleta.Rojo else Paleta.Texto2, fontWeight = if (x.atrasado) FontWeight.Bold else FontWeight.Normal)
                    if (x.atrasado) Text("Atrasada", fontSize = 13.sp, color = Paleta.Rojo, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { vm.abrirDevolver(x) }) { Text("Devolver", color = Paleta.Naranja, fontWeight = FontWeight.Bold) }
            }
        }
    }
    val devueltas = p.prestamos.filter { it.devueltoEn != null }.take(10)
    if (devueltas.isNotEmpty()) {
        TituloSeccion("Devueltas hace poco")
        devueltas.forEach { x ->
            Text("${Texto.nombre(x.descripcion)} · ${x.responsable} · ${x.estadoDevolucion?.legible ?: ""}" + (x.notaDevolucion?.let { " («$it»)" } ?: ""),
                fontSize = 14.sp, color = if (x.estadoDevolucion == EstadoDevolucion.BUENO) Paleta.Texto2 else Paleta.Rojo)
        }
    }
    if (p.herramientas.isNotEmpty()) {
        TituloSeccion("Disponibles en el almacén")
        p.herramientas.forEach { h ->
            Text("${Texto.nombre(h.descripcion)} · ${h.almacen}: ${Texto.cantidad(h.disponible)} de ${Texto.cantidadConUnidad(h.enAlmacen, h.unidad)}",
                fontSize = 14.sp, color = Paleta.Tinta)
        }
    }
}

@Composable
private fun FormPrestar(e: EstadoHerramientas, vm: HerramientasViewModel, obraId: Int) {
    val hs = e.panel?.herramientas.orEmpty().filter { (it.disponible.toDoubleOrNull() ?: 0.0) > 0 }
    if (hs.isEmpty()) { AvisoNaranja("No hay herramientas disponibles para prestar en los almacenes de la obra."); return }
    TituloSeccion("Herramienta")
    Opciones(hs, e.herramienta, { "${Texto.nombre(it.descripcion)} · ${it.almacen} · ${Texto.cantidad(it.disponible)} disp." }, vm::herramienta)
    e.errores["herramienta"]?.let { AvisoRojo(it) }
    CampoVidrio(e.cantidad, vm::cantidad, "Cantidad", teclado = androidx.compose.ui.text.input.KeyboardType.Decimal,
        sufijo = e.herramienta?.let { Texto.unidad(it.unidad) } ?: "", error = e.errores["cantidad"])
    CampoVidrio(e.responsable, vm::responsable, "Quién se la lleva", marcador = "Nombre del trabajador", error = e.errores["responsable"])
    TituloSeccion("Devolver el")
    Opciones(PlazoDevolucion.entries, e.plazo, { it.legible }, vm::plazo)
    CampoVidrio(e.nota, vm::nota, "Nota (opcional)", marcador = "p. ej. para el encofrado del 3.er piso")
    BotonNegro("Prestar", { vm.prestar(obraId) }, cargando = e.guardando, habilitado = !e.guardando)
}

@Composable
private fun FormDevolver(e: EstadoHerramientas, vm: HerramientasViewModel, obraId: Int) {
    val p = e.devolviendo ?: return
    TarjetaVidrio {
        Text(Texto.nombre(p.descripcion), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Paleta.Tinta)
        Text("${Texto.cantidadConUnidad(p.cantidad, p.unidad)} prestadas a ${p.responsable}", fontSize = 14.sp, color = Paleta.Texto2)
    }
    TituloSeccion("¿Cómo vuelve?")
    Opciones(EstadoDevolucion.entries, e.estadoDevolucion, { it.legible }, vm::estadoDevolucion)
    CampoVidrio(e.notaDevolucion, vm::notaDevolucion, if (e.estadoDevolucion == EstadoDevolucion.BUENO) "Nota (opcional)" else "Qué pasó (obligatorio)",
        marcador = if (e.estadoDevolucion == EstadoDevolucion.PERDIDO) "p. ej. se perdió en el frente B" else "p. ej. mango rajado", error = e.errores["nota"])
    Text("La devolución se registra una sola vez.", fontSize = 13.sp, color = Paleta.Texto2)
    BotonNegro("Registrar devolución", { vm.devolver(obraId) }, cargando = e.guardando, habilitado = !e.guardando)
}
