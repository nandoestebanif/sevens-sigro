package pe.sevens.sigro.datos.repositorio

import pe.sevens.sigro.dominio.RepositorioPreferencias

/** El tema se guarda en los ajustes del celular (Room), fuera de las claves de la sesión: cerrar sesión no lo borra. */
class RepositorioPreferenciasImpl(private val cred: Credenciales) : RepositorioPreferencias {
    override suspend fun tema(): String? = cred.leer(CLAVE_TEMA)
    override suspend fun guardarTema(valor: String) = cred.guardar(CLAVE_TEMA, valor)

    private companion object { const val CLAVE_TEMA = "tema" }
}
