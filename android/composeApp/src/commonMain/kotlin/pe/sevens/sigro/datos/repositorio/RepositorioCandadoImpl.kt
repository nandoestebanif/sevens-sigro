package pe.sevens.sigro.datos.repositorio

import pe.sevens.sigro.dominio.AjusteHuella
import pe.sevens.sigro.dominio.CuentaConHuella
import pe.sevens.sigro.dominio.ReglaCandado
import pe.sevens.sigro.dominio.RepositorioCandado

/**
 * El ajuste de la huella se guarda por usuario en los ajustes del celular (Room), fuera de las claves de la sesión:
 * si en el mismo celular entra otra persona, no hereda la huella de la anterior.
 */
class RepositorioCandadoImpl(private val cred: Credenciales) : RepositorioCandado {
    override suspend fun ajuste(usuarioId: Int): AjusteHuella = ReglaCandado.decodificar(cred.leer("huella:$usuarioId"))
    override suspend fun guardar(usuarioId: Int, ajuste: AjusteHuella) = cred.guardar("huella:$usuarioId", ReglaCandado.codificar(ajuste))
    override suspend fun ultimoUso(): Long? = cred.leer(CLAVE_USO)?.toLongOrNull()
    override suspend fun guardarUltimoUso(ms: Long) = cred.guardar(CLAVE_USO, ms.toString())

    // fuera de las claves de la sesión: cerrar sesión no la borra (para volver a entrar con la huella)
    override suspend fun cuenta(): CuentaConHuella? = ReglaCandado.decodificarCuenta(cred.leer(CLAVE_CUENTA))
    override suspend fun guardarCuenta(c: CuentaConHuella) = cred.guardar(CLAVE_CUENTA, ReglaCandado.codificarCuenta(c))
    override suspend fun olvidarCuenta() = cred.guardar(CLAVE_CUENTA, "")

    private companion object {
        const val CLAVE_USO = "ultimo_uso"
        const val CLAVE_CUENTA = "huella_cuenta"
    }
}
