/* SIGRO · reglas puras de la lista de trabajadores (se prueban con Vitest). */
import type { HorasFila, Trabajador } from '../api/manoDeObra'
import { activoEl } from './mano-de-obra-reglas'

export type EstadoTrabajador = 'activos' | 'baja' | 'todos'

/** En la obra = activo hoy o con ingreso programado; si no, de baja (nadie se borra, regla 10). */
export const enObra = (t: Trabajador, hoy: string) => activoEl(t, hoy) || t.fechaIngreso > hoy

export interface FiltroTrabajadores { estado: EstadoTrabajador; categoria: string; cuadrilla: string }
export function filtrarTrabajadores(xs: readonly Trabajador[], f: FiltroTrabajadores, hoy: string): Trabajador[] {
  return xs.filter((t) => (f.estado === 'todos' || (f.estado === 'activos' ? enObra(t, hoy) : !enObra(t, hoy)))
    && (f.categoria === '' || t.categoria === f.categoria)
    && (f.cuadrilla === '' || t.cuadrilla === f.cuadrilla))
}

export function resumirPersonal(xs: readonly Trabajador[], hoy: string) {
  const activos = xs.filter((t) => enObra(t, hoy))
  const porCategoria: Record<string, number> = { operario: 0, oficial: 0, peon: 0 }
  for (const t of activos) porCategoria[t.categoria] = (porCategoria[t.categoria] ?? 0) + 1
  const cuadrillas = [...new Set(xs.map((t) => t.cuadrilla))].sort((a, b) => a.localeCompare(b, 'es'))
  return { activos: activos.length, deBaja: xs.length - activos.length, porCategoria, cuadrillas,
    cuadrillasActivas: new Set(activos.map((t) => t.cuadrilla)).size }
}

/** Horas de cada trabajador en toda la obra (normales + extra), de lo que calculó el servidor. */
export function horasPorTrabajador(filas: readonly HorasFila[]): Map<number, { normales: number; extra: number; ultima: string | null }> {
  const m = new Map<number, { normales: number; extra: number; ultima: string | null }>()
  for (const f of filas) {
    const x = m.get(f.trabajadorId) ?? { normales: 0, extra: 0, ultima: null }
    x.normales += f.horasNormales
    x.extra += f.horasExtra
    if (f.ultimaJornada && (!x.ultima || f.ultimaJornada > x.ultima)) x.ultima = f.ultimaJornada
    m.set(f.trabajadorId, x)
  }
  return m
}
