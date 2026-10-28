package pe.sevens.sigro

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import pe.sevens.sigro.plataforma.ManejarAtras
import pe.sevens.sigro.ui.pantallas.Destino
import pe.sevens.sigro.ui.pantallas.EstadoRaiz
import pe.sevens.sigro.ui.pantallas.PantallaDispositivo
import pe.sevens.sigro.ui.pantallas.PantallaEntrar
import pe.sevens.sigro.ui.pantallas.PantallaInicio
import pe.sevens.sigro.ui.pantallas.PantallaObras
import pe.sevens.sigro.ui.pantallas.PantallaPendientes
import pe.sevens.sigro.ui.pantallas.PantallaSalida
import pe.sevens.sigro.ui.pantallas.RaizViewModel
import pe.sevens.sigro.ui.tema.TemaSigro

/** Interfaz común de Android e iOS. La misma función la muestran MainActivity y MainViewController. */
@Composable
fun App() {
    KoinContext {
        TemaSigro {
            val raiz: RaizViewModel = koinViewModel()
            val estado by raiz.estado.collectAsState()
            when (val e = estado) {
                EstadoRaiz.Cargando -> Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                EstadoRaiz.SinSesion -> PantallaEntrar()
                is EstadoRaiz.ElegirObra -> PantallaObras(e.sesion, alElegir = raiz::elegir, alSalir = raiz::salir)
                is EstadoRaiz.Dentro -> Navegacion(e)
            }
        }
    }
}

/**
 * Navegación dentro de la obra: una pila simple. Son cuatro pantallas; una librería de
 * navegación entra cuando haya más (Sprint 2: recepción, stock, tareo).
 */
@Composable
private fun Navegacion(e: EstadoRaiz.Dentro) {
    val pila = remember(e.obra.id) { mutableStateListOf<Destino>(Destino.Inicio) }
    val actual = pila.last()
    val volver: () -> Unit = { if (pila.size > 1) pila.removeAt(pila.lastIndex) }
    ManejarAtras(activo = pila.size > 1, alVolver = volver)

    AnimatedContent(actual, transitionSpec = { fadeIn() togetherWith fadeOut() }) { d ->
        when (d) {
            Destino.Inicio -> PantallaInicio(e.sesion, e.obra, irA = { pila.add(it) })
            Destino.Salida -> PantallaSalida(e.obra.id, alVolver = volver)
            Destino.Pendientes -> PantallaPendientes(e.obra.id, alVolver = volver)
            Destino.Dispositivo -> PantallaDispositivo(e.obra.id, alVolver = volver)
        }
    }
}
