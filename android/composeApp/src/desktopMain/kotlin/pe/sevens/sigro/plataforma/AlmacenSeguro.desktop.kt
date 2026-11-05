package pe.sevens.sigro.plataforma

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

/**
 * Escritorio: archivo en ~/.sigro, SIN cifrar. La versión de PC no es producto (solo sirve para ver la
 * interfaz y generar capturas); el almacenero usa Android o iOS, donde el token sí va cifrado.
 */
class AlmacenSeguroEscritorio(private val archivo: File) : AlmacenSeguro {
    private fun cargar() = Properties().apply { if (archivo.exists()) archivo.inputStream().use { load(it) } }
    private fun escribir(p: Properties) = archivo.outputStream().use { p.store(it, "SIGRO escritorio (no es producto)") }

    override suspend fun leer(clave: String): String? = withContext(Dispatchers.IO) { cargar().getProperty(clave) }
    override suspend fun guardar(clave: String, valor: String) = withContext(Dispatchers.IO) {
        escribir(cargar().apply { setProperty(clave, valor) })
    }
    override suspend fun borrar(clave: String) = withContext(Dispatchers.IO) { escribir(cargar().apply { remove(clave) }) }
}
