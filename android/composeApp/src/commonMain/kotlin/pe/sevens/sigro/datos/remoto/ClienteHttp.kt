package pe.sevens.sigro.datos.remoto

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val jsonSigro = Json {
    ignoreUnknownKeys = true // la API puede sumar campos sin romper la app
    explicitNulls = false
    encodeDefaults = true
}

/** El motor cambia por plataforma (OkHttp en Android, Darwin en iOS); la configuración es una sola. */
fun crearClienteHttp(motor: HttpClientEngine): HttpClient = HttpClient(motor) {
    expectSuccess = false
    install(ContentNegotiation) { json(jsonSigro) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000 // el plan gratuito despierta lento (ADR-06)
    }
}
