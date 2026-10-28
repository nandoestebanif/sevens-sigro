import SwiftUI
import UIKit
import ComposeApp

/// Muestra la interfaz de Compose Multiplatform (la misma de Android) dentro de SwiftUI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose maneja el teclado
    }
}
