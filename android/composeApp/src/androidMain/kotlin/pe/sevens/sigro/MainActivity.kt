package pe.sevens.sigro

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import pe.sevens.sigro.di.modulosSigro

/** Koin arranca una sola vez por proceso, antes que cualquier pantalla o trabajo de WorkManager. */
class SigroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SigroApp)
            modules(modulosSigro())
        }
    }
}

/** FragmentActivity (y no ComponentActivity) porque BiometricPrompt, la huella, se muestra sobre un Fragment. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
