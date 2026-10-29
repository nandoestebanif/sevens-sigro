package pe.sevens.sigro.datos.repositorio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import pe.sevens.sigro.datos.local.AjusteDao
import pe.sevens.sigro.datos.local.AjusteEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.ObraRolDto
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.ObraAsignada
import pe.sevens.sigro.dominio.RepositorioSesion
import pe.sevens.sigro.dominio.Sesion
import pe.sevens.sigro.plataforma.Plataforma

class RepositorioSesionImpl(
    private val api: ApiSigro,
    private val ajustes: AjusteDao,
) : RepositorioSesion {

    private val listaObras = ListSerializer(ObraRolDto.serializer())

    override val sesion: Flow<Sesion?> = ajustes.observar().map { filas ->
        val m = filas.associate { it.clave to it.valor }
        val token = m[Clave.TOKEN] ?: return@map null
        Sesion(
            servidor = m[Clave.SERVIDOR] ?: Plataforma.servidorPorDefecto,
            token = token,
            usuarioId = m[Clave.USUARIO_ID]?.toIntOrNull() ?: 0,
            nombre = m[Clave.NOMBRE].orEmpty(),
            usuario = m[Clave.USUARIO].orEmpty(),
            obras = m[Clave.OBRAS]?.let { decodificarObras(it) }.orEmpty(),
        )
    }

    override val obraElegida: Flow<ObraAsignada?> = ajustes.observar().map { filas ->
        val m = filas.associate { it.clave to it.valor }
        val id = m[Clave.OBRA_ID]?.toIntOrNull() ?: return@map null
        m[Clave.OBRAS]?.let { decodificarObras(it) }?.firstOrNull { it.id == id }
    }

    override suspend fun servidorGuardado(): String =
        ajustes.leer(Clave.SERVIDOR) ?: Plataforma.servidorPorDefecto

    override suspend fun iniciarSesion(servidor: String, usuario: String, clave: String): Sesion {
        val base = servidor.trim().trimEnd('/')
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw ErrorSigro("servidor_invalido", "La dirección del servidor debe empezar con http:// o https://")
        }
        val s = api.iniciarSesion(base, usuario, clave)
        if (s.obras.isEmpty()) {
            // Regla 9: sin obra asignada no hay nada que registrar. Se dice, no se deja una pantalla vacía.
            throw ErrorSigro("sin_obras", "Tu usuario no está asignado a ninguna obra. Pide al administrador que te asigne.")
        }
        guardar(Clave.SERVIDOR, base)
        guardar(Clave.TOKEN, s.token)
        guardar(Clave.USUARIO_ID, s.usuario.id.toString())
        guardar(Clave.NOMBRE, s.usuario.nombre)
        guardar(Clave.USUARIO, s.usuario.usuario)
        guardar(Clave.OBRAS, jsonSigro.encodeToString(listaObras, s.obras))
        // Con una sola obra no se pregunta: se entra directo.
        if (s.obras.size == 1) guardar(Clave.OBRA_ID, s.obras.first().id.toString())
        else ajustes.quitar(listOf(Clave.OBRA_ID))
        return Sesion(base, s.token, s.usuario.id, s.usuario.nombre, s.usuario.usuario, s.obras.map(::aObra))
    }

    override suspend fun elegirObra(obra: ObraAsignada) = guardar(Clave.OBRA_ID, obra.id.toString())

    override suspend fun cambiarDeObra() = ajustes.quitar(listOf(Clave.OBRA_ID))

    /** Cierra la sesión pero NO borra la cola: lo registrado sin señal se envía al volver a entrar. */
    override suspend fun cerrarSesion() = ajustes.quitar(Clave.deSesion)

    private suspend fun guardar(clave: String, valor: String) = ajustes.guardar(AjusteEntity(clave, valor))

    private fun decodificarObras(texto: String): List<ObraAsignada> =
        runCatching { jsonSigro.decodeFromString(listaObras, texto).map(::aObra) }.getOrDefault(emptyList())

    private fun aObra(o: ObraRolDto) = ObraAsignada(o.id, o.nombre, o.rol)
}
