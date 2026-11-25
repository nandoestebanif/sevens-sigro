import { describe, expect, it } from 'vitest'
import { correctos, visibles } from './enVivo'

const todos = { cantidad: 'Debe ser mayor que cero', motivo: 'Escribe el motivo', numero: null }
const vacio = new Set<keyof typeof todos>()

describe('validación en vivo', () => {
  it('no marca lo que no se tocó', () => {
    expect(visibles(todos, vacio, false, vacio, vacio)).toEqual({})
  })
  it('un error nuevo espera a que deje de escribir', () => {
    const t = new Set(['cantidad'] as const)
    expect(visibles(todos, t, false, t, vacio)).toEqual({})
    expect(visibles(todos, t, false, vacio, vacio)).toEqual({ cantidad: 'Debe ser mayor que cero' })
  })
  it('un error ya visible no parpadea mientras sigue escribiendo', () => {
    const t = new Set(['cantidad'] as const)
    expect(visibles(todos, t, false, t, t)).toEqual({ cantidad: 'Debe ser mayor que cero' })
  })
  it('al guardar se revisa todo', () => {
    expect(visibles(todos, vacio, true, vacio, vacio)).toEqual({ cantidad: 'Debe ser mayor que cero', motivo: 'Escribe el motivo' })
  })
  it('lo corregido se va al instante y queda con ✓', () => {
    const ok = { ...todos, cantidad: null }
    const t = new Set(['cantidad'] as const)
    expect(visibles(ok, t, false, t, t)).toEqual({})
    expect([...correctos(ok, t)]).toEqual(['cantidad'])
  })
})
