/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · gasto en recursos (HU-58) e inventario valorizado (HU-56) sobre la API real

   Ambas cifras las calcula el servicio costo a partir del libro de Almacén (al
   costo promedio ponderado congelado en cada salida, NIC 2), de Compras y de
   Tareo. La web no suma ni recalcula nada: muestra lo que devolvió el servidor
   con su definición y su fecha de corte, y dice cuando las cifras no cuadran.
   Como oficina.ts, va solo contra el servidor: un gasto inventado no sirve.
   ───────────────────────────────────────────────────────────────────────── */
import { num, obraId, pedir } from './http'

const n0 = (v: string | number | null | undefined): number | null => (v === null || v === undefined ? null : num(String(v)))
const consulta = (p: Record<string, string | undefined>) => {
  const q = Object.entries(p).filter(([, v]) => v).map(([k, v]) => `${k}=${encodeURIComponent(v!)}`).join('&')
  return q ? `?${q}` : ''
}

/* ── HU-58 · gasto en recursos ── */
export type ClaveCifra = 'comprado' | 'recibido' | 'consumido' | 'en_stock' | 'horas_hombre'
export interface Cifra { clave: ClaveCifra; valor: number | null; definicion: string; corte: string | null; nota: string | null }
export interface GastoFamilia { familia: string; recibido: number; consumido: number; enStock: number; hasta: string | null }
export interface Gasto {
  declaracion: string
  criterio: string
  moneda: string
  corte: string                 // un solo instante para todas las cifras
  cifras: Cifra[]
  otrosMovimientos: number | null
  porConciliar: boolean
  conciliacion: string[]
  porFamilia: GastoFamilia[]
  horas: { normales: number | null; extra: number | null; corte: string | null }
}
interface GastoApi {
  declaracion: string; criterio: string; moneda: string; corte: string; otrosMovimientos: string | null
  porConciliar: boolean; conciliacion: string[]
  cifras: { clave: ClaveCifra; valor: string | null; definicion: string; corte: string | null; nota?: string | null }[]
  porFamilia: { familia: string; recibido: string; consumido: string; enStock: string; hasta: string | null }[]
  horasHombre: { horasNormales: string | null; horasExtra: string | null; corte: string | null }
}

export async function gasto(corte?: string): Promise<Gasto> {
  const g = await pedir<GastoApi>('GET', `/obras/${await obraId()}/costo/gasto${consulta({ corte })}`)
  return {
    declaracion: g.declaracion, criterio: g.criterio, moneda: g.moneda, corte: g.corte,
    cifras: g.cifras.map((c) => ({ clave: c.clave, valor: n0(c.valor), definicion: c.definicion, corte: c.corte, nota: c.nota ?? null })),
    otrosMovimientos: n0(g.otrosMovimientos),
    porConciliar: g.porConciliar, conciliacion: g.conciliacion,
    porFamilia: g.porFamilia.map((f) => ({ familia: f.familia, recibido: num(f.recibido), consumido: num(f.consumido), enStock: num(f.enStock), hasta: f.hasta })),
    horas: { normales: n0(g.horasHombre.horasNormales), extra: n0(g.horasHombre.horasExtra), corte: g.horasHombre.corte },
  }
}

/* ── HU-56 · inventario valorizado ── */
export interface InsumoValorizado {
  codigo: string; descripcion: string; unidad: string; tipo: string
  cantidad: number; costoPromedio: number | null; valor: number; porcentaje: number; ultimoMovimiento: string
}
export interface AlmacenValorizado { id: number; nombre: string; insumos: number; valor: number; porcentaje: number }
export interface Inventario {
  declaracion: string; criterio: string; moneda: string
  almacen: string               // «todos» o el id consultado
  corte: string
  corteAjustado: boolean        // pidieron una fecha futura y el servidor usó «ahora»
  valorTotal: number
  porAlmacen: AlmacenValorizado[]
  porInsumo: InsumoValorizado[]
}
interface InventarioApi {
  declaracion: string; criterio: string; moneda: string; almacen: string; corte: string; corteAjustado?: boolean; valorTotal: string
  porAlmacen: { almacenId: number; almacen: string; insumos: number; valor: string; porcentaje: string }[]
  porInsumo: { insumoCodigo: string; descripcion: string; unidad: string; tipo: string; cantidad: string; costoPromedio: string | null
    valor: string; porcentaje: string; ultimoMovimientoEn: string }[]
}

export async function inventario(almacen: string = 'todos', corte?: string): Promise<Inventario> {
  const v = await pedir<InventarioApi>('GET', `/obras/${await obraId()}/costo/inventario${consulta({ almacen, corte })}`)
  return {
    declaracion: v.declaracion, criterio: v.criterio, moneda: v.moneda, almacen: v.almacen, corte: v.corte,
    corteAjustado: !!v.corteAjustado, valorTotal: num(v.valorTotal),
    porAlmacen: v.porAlmacen.map((a) => ({ id: a.almacenId, nombre: a.almacen, insumos: a.insumos, valor: num(a.valor), porcentaje: num(a.porcentaje) })),
    porInsumo: v.porInsumo.map((i) => ({
      codigo: i.insumoCodigo, descripcion: i.descripcion, unidad: i.unidad, tipo: i.tipo, cantidad: num(i.cantidad),
      costoPromedio: n0(i.costoPromedio), valor: num(i.valor), porcentaje: num(i.porcentaje), ultimoMovimiento: i.ultimoMovimientoEn,
    })),
  }
}

export async function almacenesDeLaObra(): Promise<{ id: number; nombre: string }[]> {
  return pedir<{ id: number; nombre: string }[]>('GET', `/obras/${await obraId()}/almacenes`)
}

/** Fecha y hora de corte en hora de Lima, como la lee una persona: 15/11/2026 18:40. */
export function fechaCorte(iso: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('es-PE', { timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
}
