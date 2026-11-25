package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable

/**
 * Avisa cuando la app pasa a segundo plano ([alSalir]) y cuando vuelve ([alVolver]). Lo usa el candado de la huella
 * para contar los minutos sin usarla aunque Android cierre la app. En Android sale del ciclo de vida de la actividad.
 */
@Composable
expect fun CicloDeVida(alSalir: () -> Unit, alVolver: () -> Unit)
