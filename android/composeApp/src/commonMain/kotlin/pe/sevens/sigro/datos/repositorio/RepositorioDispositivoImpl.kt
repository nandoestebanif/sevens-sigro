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
            // El almacenero no puede LISTAR dispositivos (solo el administrador): no se sabe, y no se dice «Sin solicitar».
            if (e.estadoHttp == 403) EstadoDispositivo(id, null, visible = false) else throw e
        }
    }

    override suspend fun solicitarAutorizacion(obraId: Int): EstadoDispositivo {
        val id = identificador()
        return try {
            EstadoDispositivo(id, api.solicitarDispositivo(cred.exigir(), obraId, id).estado)
        } catch (e: ErrorSigro) {
            // 409: el celular ya está registrado en la obra (pendiente o autorizado). No es un error para el almacenero.
            if (e.estadoHttp == 409) EstadoDispositivo(id, EstadoDispositivo.YA_SOLICITADO) else throw e
        }
    }
}
