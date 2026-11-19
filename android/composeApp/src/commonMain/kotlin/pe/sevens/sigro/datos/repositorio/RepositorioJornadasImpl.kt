package pe.sevens.sigro.datos.repositorio

import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.CorreccionJornadaIn
import pe.sevens.sigro.dominio.CorreccionJornada
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.JornadaCorregida
import pe.sevens.sigro.dominio.MarcaAsistencia
import pe.sevens.sigro.dominio.RepositorioJornadas

/**
 * HU-63 c3 · corrección de una jornada ya enviada. Pide al servidor la jornada vigente de ese trabajador y día
 * y le inserta la corrección con su motivo. Si todavía no llegó (sigue en la cola del celular), lo dice: no se
 * corrige algo que el servidor aún no tiene.
 */
class RepositorioJornadasImpl(
    private val api: ApiSigro,
    private val cred: Credenciales,
) : RepositorioJornadas {
    override suspend fun corregir(obraId: Int, fecha: String, marca: MarcaAsistencia, motivo: String): JornadaCorregida {
        CorreccionJornada.errorMotivo(motivo)?.let { throw ErrorSigro("motivo", it) }
        val servidor = cred.exigir()
        val vigente = api.jornadasVigentes(servidor, obraId, fecha, marca.trabajador.id).firstOrNull()
            ?: throw ErrorSigro(
                "jornada_por_enviar",
                "Esa jornada todavía no llegó al servidor (está en «Mis registros» por enviar). Corrígela cuando se haya enviado.",
            )
        val nueva = api.corregirJornada(
            servidor, obraId, vigente.id,
            CorreccionJornadaIn(marca.estado.api, marca.normalesEnvio, marca.extraEnvio, motivo.trim()),
        )
        return JornadaCorregida(nueva.id, vigente.id)
    }
}
