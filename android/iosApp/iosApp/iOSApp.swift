import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // Koin arranca una vez, antes de la primera pantalla (igual que SigroApp en Android).
        Modulos_iosKt.iniciarKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
