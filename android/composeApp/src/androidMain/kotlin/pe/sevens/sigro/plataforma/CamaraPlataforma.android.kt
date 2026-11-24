package pe.sevens.sigro.plataforma

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import pe.sevens.sigro.dominio.Camara
import pe.sevens.sigro.dominio.PermisoCamara
import pe.sevens.sigro.dominio.ResultadoFoto
import pe.sevens.sigro.dominio.estadoPermiso
import java.io.File

/**
 * Android: permiso CAMERA en tiempo de ejecución + la cámara del sistema (intent TakePicture).
 * La foto se guarda en el almacenamiento privado de la app (files/fotos) y se comparte con la cámara
 * mediante un FileProvider (nunca se expone una ruta de archivo a otra app).
 */
@Composable
actual fun rememberCamara(): Camara {
    val contexto = LocalContext.current
    val puente = remember { Puente() }
    // Los «launchers» deben registrarse en la composición; su resultado completa la espera del método suspend.
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { puente.permiso?.complete(it) }
    val foto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { puente.foto?.complete(it) }
    return remember(contexto) {
        CamaraAndroid(contexto, pedir = { puente.permiso = CompletableDeferred(); permiso.launch(Manifest.permission.CAMERA); puente.permiso!! },
            fotografiar = { uri -> puente.foto = CompletableDeferred(); foto.launch(uri); puente.foto!! })
    }
}

private class Puente {
    var permiso: CompletableDeferred<Boolean>? = null
    var foto: CompletableDeferred<Boolean>? = null
}

private class CamaraAndroid(
    private val contexto: Context,
    private val pedir: () -> CompletableDeferred<Boolean>,
    private val fotografiar: (Uri) -> CompletableDeferred<Boolean>,
) : Camara {
    private val prefs = contexto.getSharedPreferences("sigro_permisos", Context.MODE_PRIVATE)

    override fun permiso(): PermisoCamara {
        if (!contexto.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return PermisoCamara.NO_DISPONIBLE
        val actividad = contexto.actividad()
        return estadoPermiso(
            concedido = ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            yaSePregunto = prefs.getBoolean(YA_PREGUNTADO, false),
            sistemaPermiteExplicar = actividad != null && ActivityCompat.shouldShowRequestPermissionRationale(actividad, Manifest.permission.CAMERA),
        )
    }

    override suspend fun pedirPermiso(): PermisoCamara {
        val antes = permiso()
        // Tras «no volver a preguntar» el sistema ya no muestra el diálogo: no se insiste.
        if (antes == PermisoCamara.CONCEDIDO || antes == PermisoCamara.DENEGADO_PARA_SIEMPRE || antes == PermisoCamara.NO_DISPONIBLE) return antes
        pedir().await()
        prefs.edit().putBoolean(YA_PREGUNTADO, true).apply()
        return permiso()
    }

    override suspend fun tomarFoto(nombre: String): ResultadoFoto {
        val p = pedirPermiso()
        if (p != PermisoCamara.CONCEDIDO) return ResultadoFoto.SinPermiso(p)
        val archivo = File(File(contexto.filesDir, "fotos").apply { mkdirs() }, "$nombre.jpg")
        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos", archivo)
        return if (fotografiar(uri).await() && archivo.length() > 0) ResultadoFoto.Tomada(archivo.absolutePath) else {
            archivo.delete()
            ResultadoFoto.Cancelada
        }
    }

    override fun abrirAjustes() {
        contexto.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object { const val YA_PREGUNTADO = "camara_ya_preguntado" }
}

private tailrec fun Context.actividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}
