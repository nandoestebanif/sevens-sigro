package pe.sevens.sigro

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.startKoin
import pe.sevens.sigro.di.modulosSigro

/** ./gradlew :composeApp:run — la app en una ventana del tamaño de un celular, contra la API local. */
fun main() {
    startKoin { modules(modulosSigro()) }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "SIGRO Almacén (vista de escritorio)",
            state = rememberWindowState(width = 412.dp, height = 860.dp),
        ) { App() }
    }
}
