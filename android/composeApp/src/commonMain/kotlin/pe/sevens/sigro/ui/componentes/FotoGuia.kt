package pe.sevens.sigro.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.sevens.sigro.dominio.PermisoCamara
import pe.sevens.sigro.dominio.ResultadoFoto
import pe.sevens.sigro.plataforma.rememberCamara

/**
 * HU-35 · Foto de la guía de remisión al recibir una compra (opcional: la entrada se guarda igual).
 * Resuelve en la interfaz cada estado del permiso de cámara, con un mensaje y una salida distintos:
 *  - sin preguntar / concedido → «Tomar foto de la guía» (el primer toque abre el diálogo del sistema);
 *  - denegado → se explica para qué sirve y se puede volver a pedir;
 *  - denegado para siempre → el diálogo ya no aparece: botón a Ajustes y «Ya lo activé» para releer;
 *  - no disponible (iOS, escritorio o equipo sin cámara) → se dice y no se ofrece el botón.
 */
@Composable
fun SeccionFotoGuia(nombreArchivo: String, foto: String?, alCambiarFoto: (String?) -> Unit) {
    val camara = rememberCamara()
    var permiso by remember { mutableStateOf(camara.permiso()) }
    val alcance = rememberCoroutineScope()
    val tomar: () -> Unit = {
        alcance.launch {
            when (val r = camara.tomarFoto(nombreArchivo)) {
                is ResultadoFoto.Tomada -> alCambiarFoto(r.ruta)
                is ResultadoFoto.SinPermiso -> permiso = r.permiso
                ResultadoFoto.Cancelada -> Unit
            }
            permiso = camara.permiso()
        }
    }
    val apoyo = MaterialTheme.colorScheme.onSurfaceVariant

    TarjetaVidrio {
        TituloSeccion("Foto de la guía de remisión (opcional)")
        when {
            foto != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Foto guardada en el celular, como constancia de lo que llegó.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { alCambiarFoto(null) }) { Text("Quitar") }
            }
            permiso == PermisoCamara.NO_DISPONIBLE ->
                Text("La cámara no está disponible en este equipo. La entrada se guarda igual.", style = MaterialTheme.typography.bodyMedium, color = apoyo)
            permiso == PermisoCamara.DENEGADO_PARA_SIEMPRE -> {
                AvisoRojo("El permiso de cámara está bloqueado. Actívalo en Ajustes → Permisos → Cámara. La entrada se guarda igual sin foto.")
                BotonNegro("Abrir ajustes", { camara.abrirAjustes() }, icono = Icons.Filled.Settings, contorno = true)
                TextButton(onClick = { permiso = camara.permiso() }) { Text("Ya lo activé") }
            }
            permiso == PermisoCamara.DENEGADO -> {
                Text(
                    "Sin permiso no se puede fotografiar la guía. La foto sirve de constancia si lo que llegó no coincide con la compra.",
                    style = MaterialTheme.typography.bodyMedium, color = apoyo,
                )
                BotonNegro("Dar permiso y tomar la foto", tomar, icono = Icons.Filled.PhotoCamera, contorno = true)
            }
            else -> BotonNegro("Tomar foto de la guía", tomar, icono = Icons.Filled.PhotoCamera, contorno = true)
        }
    }
}

