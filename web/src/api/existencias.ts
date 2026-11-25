/* SIGRO · existencias del almacén para la oficina: insumos en almacén y herramientas y equipos.
   Una consulta al stock (saldo por almacén y consolidado, con punto de reposición y stock de seguridad ya convertidos
   por el servidor) y otra al catálogo para saber el tipo de cada insumo (material o herramienta/equipo). */
import { num, obraId, pedir } from './http'
import { estadoDe, type Existencia, type TipoInsumo } from '../paginas/existencias-reglas'

interface FilaApi {
  almacenId: number | null; almacen: string | null; insumoId: number; insumoCodigo: string; descripcion: string; unidad: string
  cantidad: string; costoPromedio: string | null; valor: string; ultimoMovimientoEn: string
  puntoReposicion?: string | null; bajoPuntoReposicion?: boolean | null; stockSeguridad?: string | null; bajoStockSeguridad?: boolean | null
  unidadCompra?: string | null; cantidadEnUnidadCompra?: string | null
}
interface StockApi { corte: string | null; porAlmacen: FilaApi[]; consolidado: FilaApi[]; advertencias: string[] }
interface InsumoApi { id: number; tipo: string }

const nul = (x: string | null | undefined) => (x == null ? null : num(x))

export async function existencias(): Promise<{ filas: Existencia[]; almacenes: string[]; corte: string | null; advertencias: string[] }> {
  const id = await obraId()
  const [s, cat] = await Promise.all([
    pedir<StockApi>('GET', `/obras/${id}/almacen/stock`),
    pedir<InsumoApi[]>('GET', '/catalogo/insumos').catch(() => [] as InsumoApi[]),   // sin catálogo, el tipo queda «sin dato»
  ])
  const tipo = new Map(cat.map((c) => [c.id, c.tipo as TipoInsumo]))
  const filas = s.consolidado.map((c): Existencia => {
    const cantidad = num(c.cantidad)
    return {
      insumoId: c.insumoId, codigo: c.insumoCodigo, descripcion: c.descripcion, unidad: c.unidad,
      tipo: tipo.get(c.insumoId) ?? 'sin_dato', cantidad,
      almacenes: s.porAlmacen.filter((p) => p.insumoId === c.insumoId).map((p) => ({ almacen: p.almacen ?? '', cantidad: num(p.cantidad) })),
      unidadCompra: c.unidadCompra ?? null, cantidadCompra: nul(c.cantidadEnUnidadCompra),
      puntoReposicion: nul(c.puntoReposicion), stockSeguridad: nul(c.stockSeguridad),
      costoPromedio: nul(c.costoPromedio), valor: num(c.valor), ultimoMovimiento: c.ultimoMovimientoEn,
      estado: estadoDe(cantidad, c.bajoStockSeguridad ?? null, c.bajoPuntoReposicion ?? null),
    }
  })
  const almacenes = [...new Set(s.porAlmacen.map((p) => p.almacen ?? '').filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es'))
  return { filas, almacenes, corte: s.corte, advertencias: s.advertencias }
}
