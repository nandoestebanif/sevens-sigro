package pe.sevens.sigro

import androidx.compose.ui.window.ComposeUIViewController

/** Punto de entrada de iOS: Swift lo muestra dentro de un UIViewController (ver iosApp/). */
fun MainViewController() = ComposeUIViewController { App() }
