package pe.sevens.sigro.datos.repositorio

import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.EstadoDispositivo
import pe.sevens.sigro.dominio.RepositorioDispositivo

/** HU-15 desde el celular: mostrar el código y pedir que el administrador lo autorice. */
class RepositorioDispositivoImpl(
    private val api: ApiSigro,
    private val cred: Credenciales,
) : RepositorioDispositivo {

    override suspend fun identificador(): String = cred.identificadorDispositivo()

    override suspend fun estado(obraId: Int): EstadoDispositivo {
        val id = identificador()
        val servidor = cred.exigir()
        return try {
            val propio = api.dispositivos(servidor, obraId).lastOrNull { it.identificador == id }
            EstadoDispositivo(id, propio?.estado)
        } catch (e: ErrorSigro) {
            // El almacenero quizá no puede LISTAR dispositivos (solo el administrador): no es un error para él.
            if (e.estadoHttp == 403) EstadoDispositivo(id, null) else throw e
        }
    }

    override suspend fun solicitarAutorizacion(obraId: Int): EstadoDispositivo {
        val id = identificador()
        val d = api.solicitarDispositivo(cred.exigir(), obraId, id)
        return EstadoDispositivo(id, d.estado)
    }
}
