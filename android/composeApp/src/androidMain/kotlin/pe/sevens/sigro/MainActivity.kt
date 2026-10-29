package pe.sevens.sigro

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
