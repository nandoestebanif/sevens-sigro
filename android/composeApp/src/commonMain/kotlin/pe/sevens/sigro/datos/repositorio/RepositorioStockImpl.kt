package pe.sevens.sigro.datos.repositorio

import pe.sevens.sigro.datos.local.StockDao
import pe.sevens.sigro.datos.local.StockGuardadoEntity
import pe.sevens.sigro.datos.remoto.ApiSigro
import pe.sevens.sigro.datos.remoto.FilaStockDto
import pe.sevens.sigro.datos.remoto.StockDto
import pe.sevens.sigro.datos.remoto.jsonSigro
import pe.sevens.sigro.dominio.ErrorSigro
import pe.sevens.sigro.dominio.FilaStock
import pe.sevens.sigro.dominio.RepositorioStock
import pe.sevens.sigro.dominio.StockObra
import pe.sevens.sigro.plataforma.Plataforma

/**
 * A2 · HU-43. El stock lo calcula el servidor (regla 2). Con señal se pide y se guarda la respuesta tal cual;
 * sin señal (o con el servidor dormido) se muestra esa copia con la hora en que llegó. El celular nunca suma ni
 * resta lo que registró después: la copia dice «actualizado a las HH:MM» y nada más.
 */
class RepositorioStockImpl(
    private val api: ApiSigro,
    private val guardado: StockDao,
    private val cred: Credenciales,
    private val reloj: () -> Long = Plataforma::ahoraMs,
) : RepositorioStock {

    override suspend fun stock(obraId: Int): StockObra {
        val dto = try {
            api.stock(cred.exigir(), obraId)
        } catch (e: ErrorSigro) {
            if (!e.esTransitorio) throw e
            val copia = guardado.leer(obraId) ?: throw ErrorSigro(
                e.codigo, "${e.message} Todavía no hay un stock guardado en el celular para esta obra.", e.estadoHttp,
            )
            return aDominio(jsonSigro.decodeFromString(StockDto.serializer(), copia.json), copia.recibidoMs, desdeCelular = true)
        }
        val ahora = reloj()
        guardado.guardar(StockGuardadoEntity(obraId, jsonSigro.encodeToString(StockDto.serializer(), dto), ahora))
        return aDominio(dto, ahora, desdeCelular = false)
    }

    private fun aDominio(d: StockDto, recibidoMs: Long, desdeCelular: Boolean) = StockObra(
        corte = d.corte,
        porAlmacen = d.porAlmacen.map(::aFila),
        consolidado = d.consolidado.map(::aFila),
        advertencias = d.advertencias,
        recibidoMs = recibidoMs,
        desdeCelular = desdeCelular,
    )

    private fun aFila(f: FilaStockDto) = FilaStock(
        almacenId = f.almacenId, almacen = f.almacen, insumoId = f.insumoId, codigo = f.insumoCodigo,
        descripcion = f.descripcion, unidad = f.unidad, cantidad = f.cantidad, bajoPuntoReposicion = f.bajoPuntoReposicion,
    )
}
