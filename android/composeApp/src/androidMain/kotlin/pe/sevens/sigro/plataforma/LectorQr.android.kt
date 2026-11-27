package pe.sevens.sigro.plataforma

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Android: CameraX (vista previa + análisis de cuadros) y ZXing para leer el QR. ZXing es Java puro: lee sin señal,
 * no necesita Google Play Services ni descarga modelos, y no envía datos de uso a nadie. Solo busca QR.
 * Se analiza un cuadro a la vez (KEEP_ONLY_LATEST): si el celular es lento, se saltan cuadros, no se acumulan.
 */
@Composable
actual fun LectorQr(modifier: Modifier, alLeer: (String) -> Unit) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val alLeerAhora by rememberUpdatedState(alLeer)
    val hilo = remember { Executors.newSingleThreadExecutor() }
    val proveedor = remember(contexto) { ProcessCameraProvider.getInstance(contexto) }
    // Al salir de la pantalla se suelta la cámara (aunque la actividad siga viva).
    DisposableEffect(ciclo) {
        onDispose {
            runCatching { if (proveedor.isDone) proveedor.get().unbindAll() }
            hilo.shutdown()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val principal = ContextCompat.getMainExecutor(ctx)
            val lector = LectorZxing(principal) { alLeerAhora(it) }
            PreviewView(ctx).also { vista ->
                vista.scaleType = PreviewView.ScaleType.FILL_CENTER
                proveedor.addListener({
                    val camaras = proveedor.get()
                    val previa = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
                    val analisis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analisis.setAnalyzer(hilo, lector)
                    runCatching {
                        camaras.unbindAll()
                        camaras.bindToLifecycle(ciclo, CameraSelector.DEFAULT_BACK_CAMERA, previa, analisis)
                    }
                }, principal)
            }
        },
    )
}

/** Lee el plano de luminancia (Y) del cuadro; el resultado se entrega en el hilo principal. */
private class LectorZxing(private val principal: Executor, private val alLeer: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val lector = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8"))
    }

    override fun analyze(cuadro: ImageProxy) {
        cuadro.use {
            val plano = it.planes[0]
            val ancho = it.width
            val alto = it.height
            val datos = ByteArray(ancho * alto)
            val buffer = plano.buffer.apply { rewind() }
            // cada fila puede traer relleno (rowStride > ancho): se copia solo lo útil
            for (fila in 0 until alto) {
                buffer.position(fila * plano.rowStride)
                buffer.get(datos, fila * ancho, ancho)
            }
            val fuente = PlanarYUVLuminanceSource(datos, ancho, alto, 0, 0, ancho, alto, false)
            val texto = try {
                lector.decodeWithState(BinaryBitmap(HybridBinarizer(fuente))).text
            } catch (_: NotFoundException) {
                null
            } catch (_: Exception) {
                null // un cuadro borroso no es un error
            } finally {
                lector.reset()
            }
            if (texto != null) principal.execute { alLeer(texto) }
        }
    }
}

actual val lectorQrDisponible: Boolean = true
