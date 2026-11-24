package pe.sevens.sigro.plataforma

import androidx.compose.runtime.Composable
import pe.sevens.sigro.dominio.Camara

/**
 * La cámara de la plataforma, recordada en la composición: en Android necesita registrar los
 * «launchers» de permiso y de foto en la pantalla (ActivityResult), por eso es @Composable.
 */
@Composable
expect fun rememberCamara(): Camara
