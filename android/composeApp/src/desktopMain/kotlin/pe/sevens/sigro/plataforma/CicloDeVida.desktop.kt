package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable

/** Sin candado de huella en este equipo: no hace falta seguir el ciclo de vida. */
@Composable
actual fun CicloDeVida(alSalir: () -> Unit, alVolver: () -> Unit) {}
