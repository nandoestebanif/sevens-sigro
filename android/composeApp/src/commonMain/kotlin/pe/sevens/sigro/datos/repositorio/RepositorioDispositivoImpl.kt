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

    /**
     * Primero `GET …/dispositivos/mios` (los celulares de esta persona en la obra). Si el servidor todavía no lo tiene
     * (404 sin código), se intenta la lista de todos, que solo puede ver el administrador o el residente.
     */
    override suspend fun estado(obraId: Int): EstadoDispositivo {
        val id = identificador()
        val servidor = cred.exigir()
        val mios = try {
            api.misDispositivos(servidor, obraId)
        } catch (e: ErrorSigro) {
            when {
                e.estadoHttp == 403 -> return EstadoDispositivo(id, null, visible = false) // rol sin celular de registro
                e.estadoHttp == 404 && e.codigo == "http_404" -> null                       // servidor sin la ruta todavía
                else -> throw e
            }
        }
        if (mios != null) {
            val propio = mios.lastOrNull { it.identificador == id }
            val otroActivo = mios.any { it.identificador != id && it.estado == ACTIVO }
            return EstadoDispositivo(id, estadoApp(propio?.estado), otroCelularActivo = otroActivo)
        }
        return try {
            val propio = api.dispositivos(servidor, obraId).lastOrNull { it.identificador == id }
            EstadoDispositivo(id, estadoApp(propio?.estado))
        } catch (e: ErrorSigro) {
            // Servidor viejo y usuario sin permiso de listar: no se sabe, y no se dice «Sin solicitar».
            if (e.estadoHttp == 403) EstadoDispositivo(id, null, visible = false) else throw e
        }
    }

    override suspend fun solicitarAutorizacion(obraId: Int): EstadoDispositivo {
        val id = identificador()
        return try {
            EstadoDispositivo(id, estadoApp(api.solicitarDispositivo(cred.exigir(), obraId, id).estado))
        } catch (e: ErrorSigro) {
            // 409: el celular ya está registrado en la obra (pendiente o autorizado). No es un error para el almacenero.
            if (e.estadoHttp == 409) EstadoDispositivo(id, EstadoDispositivo.YA_SOLICITADO) else throw e
        }
    }

    internal companion object {
        /** El servidor guarda `activo` (ck_dispositivo_estado: pendiente · activo · revocado); la app dice «autorizado». */
        const val ACTIVO = "activo"
        fun estadoApp(servidor: String?): String? = if (servidor == ACTIVO) EstadoDispositivo.AUTORIZADO else servidor
    }
}
