/* Re-prueba del 19/11 (Nicolás): las horas se mostraban en UTC. */
import { describe, expect, it } from 'vitest'
import { diaHoraLima, fechaHoraLima } from './horaLima'

describe('hora de Lima en la web', () => {
  it('convierte el ISO UTC del servidor (historial del factor)', () => {
    expect(fechaHoraLima('2026-11-19T17:35:10.123Z')).toBe('2026-11-19 12:35')
  })
  it('convierte el texto de PostgreSQL con «+00» (ajustes del requerimiento)', () => {
    expect(fechaHoraLima('2026-11-19 17:39:02.51+00')).toBe('2026-11-19 12:39')
    expect(fechaHoraLima('2026-11-18 00:39:00+00')).toBe('2026-11-17 19:39') // pasada la medianoche UTC sigue siendo el día anterior en Lima
  })
  it('el kárdex muestra día/mes y hora de Lima', () => {
    expect(diaHoraLima('2026-11-19T17:36:00Z')).toBe('19/11 12:36')
  })
  it('si no es una fecha, la deja como vino', () => {
    expect(fechaHoraLima('sin fecha')).toBe('sin fecha')
  })
})
