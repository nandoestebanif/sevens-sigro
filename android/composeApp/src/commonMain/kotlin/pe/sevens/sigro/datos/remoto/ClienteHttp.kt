package pe.sevens.sigro.datos.remoto

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val jsonSigro = Json {
    ignoreUnknownKeys = true // la API puede sumar campos sin romper la app
    explicitNulls = false
    encodeDefaults = true
}

/** Ruta que no lleva token: con ella se consigue uno. */
internal const val RUTA_LOGIN = "/auth/login"

/**
 * El motor cambia por plataforma (OkHttp en Android, Darwin en iOS); la configuración es una sola.
 *
 * El token lo pone el plugin Auth (bearer) en cada petición, leyéndolo de `token` (el almacén cifrado).
 * No hay refresco: la API no emite refresh token, así que un 401 significa «sesión vencida» y lo maneja
 * [ApiSigro] (cierra la sesión sin tocar las colas).
 */
fun crearClienteHttp(motor: HttpClientEngine, token: suspend () -> String? = { null }): HttpClient = HttpClient(motor) {
    expectSuccess = false
    install(ContentNegotiation) { json(jsonSigro) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000 // el plan gratuito despierta lento (ADR-06)
    }
    install(Auth) {
        bearer {
            loadTokens { token()?.let { BearerTokens(accessToken = it, refreshToken = null) } }
            refreshTokens { null }
            // Sin esto Ktor espera un 401 antes de mandar el token: la API no reta, responde 401 y ya.
            sendWithoutRequest { peticion -> !peticion.url.encodedPath.endsWith(RUTA_LOGIN) }
        }
    }
}

/** Ktor guarda el token que leyó; tras entrar o salir hay que olvidarlo para que lea el nuevo. */
fun HttpClient.olvidarToken() {
    authProvider<BearerAuthProvider>()?.clearToken()
}
