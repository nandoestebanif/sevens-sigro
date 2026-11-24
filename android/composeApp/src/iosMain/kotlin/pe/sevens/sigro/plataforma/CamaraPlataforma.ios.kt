package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import pe.sevens.sigro.dominio.Camara
import pe.sevens.sigro.dominio.CamaraNoDisponible

/** iOS: sin cámara en esta versión (el build de iOS no se exige en el curso). La pantalla lo dice. */
@Composable
actual fun rememberCamara(): Camara = CamaraNoDisponible
