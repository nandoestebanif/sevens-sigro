package pe.sevens.sigro.ui.pantallas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.Sesion

/** Qué parte de la app se ve según la sesión guardada en el celular. */
sealed interface EstadoRaiz {
    data object Cargando : EstadoRaiz
    data object SinSesion : EstadoRaiz
    data class ElegirObra(val sesion: Sesion) : EstadoRaiz
    data class Dentro(val sesion: Sesion, val obra: ObraAsignada) : EstadoRaiz
}

/** Las obras de la sesión en cualquier estado (vacío sin sesión): para el color y el dibujo de «Entrando a …». */
fun obrasDe(estado: EstadoRaiz): List<ObraAsignada> = when (estado) {
    is EstadoRaiz.Dentro -> estado.sesion.obras
    is EstadoRaiz.ElegirObra -> estado.sesion.obras
    EstadoRaiz.Cargando, EstadoRaiz.SinSesion -> emptyList()
}

/** La sesión abierta, en cualquier estado (null sin sesión): para el candado de la huella. */
fun sesionDe(estado: EstadoRaiz): Sesion? = when (estado) {
    is EstadoRaiz.Dentro -> estado.sesion
    is EstadoRaiz.ElegirObra -> estado.sesion
    EstadoRaiz.Cargando, EstadoRaiz.SinSesion -> null
}

/** Lugar de [obra] en la lista (0 si no está): decide su color y su dibujo. */
fun indiceDeObra(obras: List<ObraAsignada>, obra: ObraAsignada): Int = obras.indexOfFirst { it.id == obra.id }.coerceAtLeast(0)

class RaizViewModel(private val sesiones: RepositorioSesion) : ViewModel() {
    val estado: StateFlow<EstadoRaiz> = combine(sesiones.sesion, sesiones.obraElegida) { s, o ->
        when {
            s == null -> EstadoRaiz.SinSesion
            o == null -> EstadoRaiz.ElegirObra(s)
            else -> EstadoRaiz.Dentro(s, o)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoRaiz.Cargando)

    private val _entrando = MutableStateFlow<ObraAsignada?>(null)

    /** Obra a la que se está entrando (pantalla «Entrando a …»); `null` el resto del tiempo. */
    val entrando: StateFlow<ObraAsignada?> = _entrando.asStateFlow()

    /**
     * Entra a [obra] desde «Elegir obra» o desde la hoja «Cambiar de obra». Primero se cubre la pantalla con
     * «Entrando a …», debajo se guarda la obra (la navegación vuelve al inicio de la nueva) y se descubre tras
     * [ENTRANDO_MS]. La cola no se toca: cada registro va a la obra donde se hizo.
     */
    fun elegir(obra: ObraAsignada) = viewModelScope.launch {
        _entrando.value = obra
        try {
            sesiones.elegirObra(obra)
            delay(ENTRANDO_MS)
        } finally {
            _entrando.value = null
        }
    }
    fun cambiarDeObra() = viewModelScope.launch { sesiones.cambiarDeObra() }
    fun salir() = viewModelScope.launch { sesiones.cerrarSesion() }
}
