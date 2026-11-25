package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import pe.sevens.sigro.dominio.Camara
import pe.sevens.sigro.dominio.CamaraNoDisponible

/** Escritorio (solo para ver la app en la PC y generar capturas): sin cámara. */
@Composable
actual fun rememberCamara(): Camara = CamaraNoDisponible
