package pe.sevens.sigro.datos.repositorio

import pe.sevens.sigro.datos.local.AjusteDao
import pe.sevens.sigro.datos.local.InsumoDao
import pe.sevens.sigro.datos.local.InsumoLocalEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.Insumo
import pe.sevens.sigro.dominio.RepositorioCatalogo

class RepositorioCatalogoImpl(
    private val api: ApiSigro,
    private val insumos: InsumoDao,
    ajustes: AjusteDao,
) : RepositorioCatalogo {
    private val cred = Credenciales(ajustes)

    override suspend fun buscar(texto: String): List<Insumo> =
        insumos.buscar(texto.trim()).map { Insumo(it.id, it.codigo, it.descripcion, it.unidad) }

    override suspend fun actualizar(): Int {
        val servidor = cred.servidor() ?: throw sinSesion()
        val token = cred.token() ?: throw sinSesion()
        // En almacén solo salen materiales activos; la mano de obra va por tareo (HU-63).
        val lista = api.insumos(servidor, token)
            .filter { it.activo && it.tipo == "material" }
            .map { InsumoLocalEntity(it.id, it.codigo, it.descripcion, it.unidadCompra) }
        insumos.guardarTodos(lista)
        return insumos.contar()
    }

    override suspend fun cuantosHay(): Int = insumos.contar()

    private fun sinSesion() = ErrorSigro("sin_sesion", "Vuelve a ingresar para actualizar el catálogo.")
}
