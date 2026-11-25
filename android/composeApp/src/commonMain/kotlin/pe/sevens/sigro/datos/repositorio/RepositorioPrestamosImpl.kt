package pe.sevens.sigro.datos.repositorio

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.DevolucionIn
import pe.sevens.sigro.datos.remoto.HerramientaDisponibleDto
import pe.sevens.sigro.datos.remoto.PrestamoDto
import pe.sevens.sigro.datos.remoto.PrestamoIn
import pe.sevens.sigro.dominio.EstadoDevolucion
import pe.sevens.sigro.dominio.HerramientaDisponible
import pe.sevens.sigro.dominio.PanelHerramientas
import pe.sevens.sigro.dominio.PrestamoHerramienta
import pe.sevens.sigro.dominio.RepositorioPrestamos
import pe.sevens.sigro.dominio.ValidarSalida

/** Préstamos de herramientas contra el servidor (con señal: el disponible lo decide el servidor en el acto). */
class RepositorioPrestamosImpl(private val api: ApiSigro, private val cred: Credenciales) : RepositorioPrestamos {

    override suspend fun panel(obraId: Int): PanelHerramientas {
        val d = api.prestamos(cred.exigir(), obraId)
        return PanelHerramientas(d.prestamos.map(::aDominio), d.herramientas.map(::aDominio))
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun prestar(
        obraId: Int, h: HerramientaDisponible, cantidad: String, responsable: String, devolverEl: String?, nota: String?,
    ): PrestamoHerramienta = aDominio(
        api.prestar(
            cred.exigir(), obraId,
            PrestamoIn(
                uuidCliente = Uuid.random().toString(), almacenId = h.almacenId, insumoId = h.insumoId,
                cantidad = ValidarSalida.normalizarCantidad(cantidad), responsable = responsable.trim(),
                devolverEl = devolverEl?.takeIf { it.isNotBlank() }, nota = nota?.trim()?.ifEmpty { null },
            ),
        ),
    )

    override suspend fun devolver(obraId: Int, prestamoId: Int, estado: EstadoDevolucion, nota: String?): List<String> =
        api.devolver(cred.exigir(), obraId, prestamoId, DevolucionIn(estado.api, nota?.trim()?.ifEmpty { null })).advertencias

    private fun aDominio(p: PrestamoDto) = PrestamoHerramienta(
        p.id, p.almacen, p.insumoCodigo, p.descripcion, p.unidad, p.cantidad, p.responsable, p.devolverEl, p.prestadoEn,
        p.devueltoEn, EstadoDevolucion.deApi(p.estadoDevolucion), p.notaDevolucion, p.atrasado,
    )

    private fun aDominio(h: HerramientaDisponibleDto) = HerramientaDisponible(
        h.almacenId, h.almacen, h.insumoId, h.insumoCodigo, h.descripcion, h.unidad, h.enAlmacen, h.prestado, h.disponible,
    )
}
