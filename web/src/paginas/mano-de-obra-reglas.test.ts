import { describe, expect, it } from 'vitest'
import type { HorasFila, Trabajador } from '../api/manoDeObra'
import { activoEl, horas, resumirHoras } from './mano-de-obra-reglas'

const h = (periodo: number, id: number, nombre: string, cuadrilla: string, cat: string, n: number, x: number, ultima: string): HorasFila =>
  ({ periodo, trabajadorId: id, trabajador: nombre, cuadrilla, categoria: cat, diasPresente: 5, diasAusente: 0, diasPermiso: 0, horasNormales: n, horasExtra: x, ultimaJornada: ultima })

const filas = [
  h(1, 7, 'Ana Ruiz', 'Cuadrilla A', 'oficial', 40, 2, '2026-10-24'),
  h(2, 7, 'Ana Ruiz', 'Cuadrilla A', 'oficial', 32, 0, '2026-10-30'),
  h(1, 8, 'Luis Paz', 'Cuadrilla A', 'peon', 24, 4.5, '2026-10-23'),
  h(2, 9, 'Rosa Gil', 'Cuadrilla B', 'operario', 16, 0, '2026-10-29'),
]

describe('HU-64 · horas acumuladas', () => {
  it('separa normales y extra y dice la última jornada considerada', () => {
    const r = resumirHoras(filas, null)
    expect([r.normales, r.extra, r.ultima]).toEqual([112, 6.5, '2026-10-30'])
    expect(r.periodos).toEqual([1, 2])
  })
  it('agrupa por trabajador (todas sus semanas) y por cuadrilla', () => {
    const r = resumirHoras(filas, null)
    expect(r.porTrabajador.map((f) => [f.nombre, f.normales])).toEqual([['Ana Ruiz', 72], ['Luis Paz', 24], ['Rosa Gil', 16]])
    expect(r.porTrabajador[0].detalle).toBe('Cuadrilla A · Oficial')
    expect(r.porCuadrilla.map((f) => [f.nombre, f.normales, f.detalle])).toEqual([['Cuadrilla A', 96, '2 trabajadores'], ['Cuadrilla B', 16, '1 trabajador']])
  })
  it('filtra por semana sin perder la lista de semanas', () => {
    const r = resumirHoras(filas, 1)
    expect([r.normales, r.ultima]).toEqual([64, '2026-10-24'])
    expect(r.periodos).toEqual([1, 2])
  })
  it('muestra horas sin decimales de más', () => {
    expect(horas(8)).toBe('8 h')
    expect(horas(4.5)).toBe('4.5 h')          // es-PE usa punto decimal
  })
})

describe('HU-62 · activo o de baja', () => {
  const t: Trabajador = { id: 1, nombre: 'Ana', tipoDocumento: 'DNI', documento: '12345678', categoria: 'oficial', cuadrilla: 'A', fechaIngreso: '2026-10-19', fechaSalida: '2026-11-20' }
  it('la baja es una fecha: el último día aún trabaja', () => {
    expect(activoEl(t, '2026-11-20')).toBe(true)
    expect(activoEl(t, '2026-11-21')).toBe(false)
    expect(activoEl({ ...t, fechaSalida: null }, '2027-01-01')).toBe(true)
  })
})
