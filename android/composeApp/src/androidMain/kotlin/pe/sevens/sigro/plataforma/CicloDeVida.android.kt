package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
actual fun CicloDeVida(alSalir: () -> Unit, alVolver: () -> Unit) {
    val dueno = LocalLifecycleOwner.current
    val salir by rememberUpdatedState(alSalir)
    val volver by rememberUpdatedState(alVolver)
    DisposableEffect(dueno) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_STOP -> salir()
                Lifecycle.Event.ON_START -> volver()
                else -> Unit
            }
        }
        dueno.lifecycle.addObserver(observador)
        onDispose { dueno.lifecycle.removeObserver(observador) }
    }
}
