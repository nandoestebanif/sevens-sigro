import { describe, expect, it } from 'vitest'
import { diaLima, filtrarFechas } from './tablas'

describe('filtro por fechas de las listas', () => {
  it('lleva cada fecha al día de Lima', () => {
    expect(diaLima('2026-11-27')).toBe('2026-11-27')
    expect(diaLima('2026-11-27T04:30:00Z')).toBe('2026-11-26') // 23:30 del 26 en Lima
    expect(diaLima('2026-11-27T04:30:00')).toBe('2026-11-26') // sin zona: UTC, como manda la API
    expect(diaLima('2026-11-27T10:00:00-05:00')).toBe('2026-11-27')
    expect(diaLima(null)).toBeNull()
    expect(diaLima('ayer')).toBeNull()
  })

  const filas = [
    { id: 1, pedido: '2026-11-19T20:11:00Z', autorizado: '2026-11-19T20:11:00Z' },
    { id: 2, pedido: '2026-11-25T19:37:00Z', autorizado: null },
    { id: 3, pedido: '2026-11-27T12:36:00Z', autorizado: '2026-11-27T12:36:00Z' },
  ]
  const ids = (xs: typeof filas) => xs.map((x) => x.id)

  it('incluye los dos extremos y sin rango no filtra', () => {
    expect(ids(filtrarFechas(filas, (f) => f.pedido))).toEqual([1, 2, 3])
    expect(ids(filtrarFechas(filas, (f) => f.pedido, '2026-11-19', '2026-11-25'))).toEqual([1, 2])
    expect(ids(filtrarFechas(filas, (f) => f.pedido, '2026-11-25'))).toEqual([2, 3])
    expect(ids(filtrarFechas(filas, (f) => f.pedido, undefined, '2026-11-19'))).toEqual([1])
  })

  it('sin esa fecha no entra; un rango al revés se entiende igual', () => {
    expect(ids(filtrarFechas(filas, (f) => f.autorizado, '2026-11-01', '2026-11-30'))).toEqual([1, 3])
    expect(ids(filtrarFechas(filas, (f) => f.pedido, '2026-11-25', '2026-11-19'))).toEqual([1, 2])
  })
})
