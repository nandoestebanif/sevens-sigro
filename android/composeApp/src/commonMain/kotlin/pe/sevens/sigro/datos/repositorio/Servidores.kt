package pe.sevens.sigro.datos.repositorio

/**
 * A4 · El ambiente de pruebas pasó de varios servicios en Render (entrada por `sigro-gateway`) a uno solo,
 * `sigro-api` (ADR-10, docs/despliegue-pruebas.md). Un celular que guardó la dirección vieja la cambia
 * sola, sin pedirle nada al almacenero.
 */
object Servidores {
    const val PRUEBAS = "https://sigro-api.onrender.com"
    private val retirados = setOf("sigro-gateway.onrender.com")

    /** La dirección vigente para `url`: la misma, o la de pruebas si apuntaba a un servidor retirado. */
    fun migrar(url: String): String {
        val host = url.trim().substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
        return if (host in retirados) PRUEBAS else url
    }
}
