package pe.sevens.sigro.ui.pantallas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

class RaizViewModel(private val sesiones: RepositorioSesion) : ViewModel() {
    val estado: StateFlow<EstadoRaiz> = combine(sesiones.sesion, sesiones.obraElegida) { s, o ->
        when {
            s == null -> EstadoRaiz.SinSesion
            o == null -> EstadoRaiz.ElegirObra(s)
            else -> EstadoRaiz.Dentro(s, o)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoRaiz.Cargando)

    fun elegir(obra: ObraAsignada) = viewModelScope.launch { sesiones.elegirObra(obra) }
    fun cambiarDeObra() = viewModelScope.launch { sesiones.cambiarDeObra() }
    fun salir() = viewModelScope.launch { sesiones.cerrarSesion() }
}
