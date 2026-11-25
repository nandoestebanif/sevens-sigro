/* SIGRO · préstamo y devolución de herramientas y equipos (backend Spring, migración 0023).
   Un préstamo no mueve el saldo: disponible = en almacén − prestado sin devolver. */
import { num, obraId, pedir } from './http'

export type EstadoDevolucion = 'bueno' | 'danado' | 'perdido'
export interface Prestamo {
  id: number; almacenId: number; almacen: string; insumoId: number; insumoCodigo: string; descripcion: string; unidad: string
  cantidad: number; trabajadorId: number | null; responsable: string; devolverEl: string | null; nota: string | null
  prestadoEn: string; devueltoEn: string | null; estadoDevolucion: EstadoDevolucion | null; notaDevolucion: string | null; atrasado: boolean
}
interface PrestamoApi extends Omit<Prestamo, 'cantidad'> { cantidad: string }
const deApi = (p: PrestamoApi): Prestamo => ({ ...p, cantidad: num(p.cantidad) })

export const TEXTO_DEVOLUCION: Record<EstadoDevolucion, string> = { bueno: 'En buen estado', danado: 'Dañada', perdido: 'Perdida' }

export const prestamos = {
  async listar(estado: 'sin_devolver' | 'todos' = 'todos') {
    const id = await obraId()
    const r = await pedir<{ sinDevolver: number; atrasados: number; prestamos: PrestamoApi[] }>('GET', `/obras/${id}/almacen/prestamos?estado=${estado}`)
    return { sinDevolver: r.sinDevolver, atrasados: r.atrasados, prestamos: r.prestamos.map(deApi) }
  },
  async prestar(d: { insumoId: number; almacenId?: number; cantidad: string; responsable: string; trabajadorId?: number; devolverEl?: string; nota?: string }) {
    const id = await obraId()
    return deApi(await pedir<PrestamoApi>('POST', `/obras/${id}/almacen/prestamos`, {
      uuidCliente: crypto.randomUUID(), ...d, cantidad: d.cantidad.replace(',', '.'),
      devolverEl: d.devolverEl || undefined, nota: d.nota?.trim() || undefined,
    }))
  },
  async devolver(prestamoId: number, estado: EstadoDevolucion, nota?: string) {
    const id = await obraId()
    return pedir<PrestamoApi & { advertencias: string[] }>('POST', `/obras/${id}/almacen/prestamos/${prestamoId}/devolucion`, { estado, nota: nota?.trim() || undefined })
  },
}

/** Cuánto de cada herramienta está prestado sin devolver (por insumo). */
export function prestadoPorInsumo(ps: readonly Prestamo[]): Map<number, number> {
  const m = new Map<number, number>()
  for (const p of ps) if (!p.devueltoEn) m.set(p.insumoId, (m.get(p.insumoId) ?? 0) + p.cantidad)
  return m
}
