package pe.sevens.sigro.plataforma

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Android: CameraX (vista previa + análisis de cuadros) y ML Kit Barcode Scanning con el modelo incluido en la app,
 * así lee el QR en el sótano sin señal. Solo busca QR (más rápido y no confunde códigos de barras de los productos).
 * Se analiza un cuadro a la vez (KEEP_ONLY_LATEST): si el celular es lento, se saltan cuadros, no se acumulan.
 */
@Composable
actual fun LectorQr(modifier: Modifier, alLeer: (String) -> Unit) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val alLeerAhora by rememberUpdatedState(alLeer)
    val hilo = remember { Executors.newSingleThreadExecutor() }
    val lector = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val proveedor = remember(contexto) { ProcessCameraProvider.getInstance(contexto) }
    // Al salir de la pantalla se suelta la cámara (aunque la actividad siga viva) y se cierra el lector.
    DisposableEffect(ciclo) {
        onDispose {
            runCatching { if (proveedor.isDone) proveedor.get().unbindAll() }
            lector.close()
            hilo.shutdown()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { vista ->
                vista.scaleType = PreviewView.ScaleType.FILL_CENTER
                proveedor.addListener({
                    val camaras = proveedor.get()
                    val previa = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
                    val analisis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analisis.setAnalyzer(hilo) { cuadro -> analizar(lector, cuadro) { alLeerAhora(it) } }
                    runCatching {
                        camaras.unbindAll()
                        camaras.bindToLifecycle(ciclo, CameraSelector.DEFAULT_BACK_CAMERA, previa, analisis)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
    )
}

@OptIn(ExperimentalGetImage::class)
private fun analizar(lector: com.google.mlkit.vision.barcode.BarcodeScanner, cuadro: ImageProxy, alLeer: (String) -> Unit) {
    val imagen = cuadro.image ?: return cuadro.close()
    lector.process(InputImage.fromMediaImage(imagen, cuadro.imageInfo.rotationDegrees))
        // los oyentes de ML Kit corren en el hilo principal: se puede tocar el estado de la pantalla
        .addOnSuccessListener { codigos -> codigos.firstNotNullOfOrNull { it.rawValue }?.let(alLeer) }
        .addOnCompleteListener { cuadro.close() }
}

actual val lectorQrDisponible: Boolean = true
