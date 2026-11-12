/* Escala del texto y ancho útil: el corte de diseño se decide con el ancho que queda después del zoom. */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CORTE_CHICO, CORTE_MEDIO, anchoUtil, escalaGuardada, factor, tipoDeAncho } from './escala'

afterEach(() => vi.unstubAllGlobals())

describe('escala', () => {
  it('factor de cada escala; lo desconocido vale 1', () => {
    expect([factor('90'), factor('100'), factor('110'), factor('125')]).toEqual([0.9, 1, 1.1, 1.25])
    expect(factor(undefined)).toBe(1)
    expect(factor('300')).toBe(1)
  })

  it('a 125 % una ventana de 1 100 px deja 880 px útiles: el menú pasa arriba', () => {
    expect(anchoUtil(1100, '125')).toBe(880)
    expect(tipoDeAncho(1100, '125')).toBe('medio')
    expect(tipoDeAncho(1100, '100')).toBe('amplio') // a 100 % la misma ventana conserva el menú lateral
  })

  it('los cortes son en px útiles, iguales a los de antes a 100 %', () => {
    expect(tipoDeAncho(CORTE_MEDIO + 1, '100')).toBe('amplio')
    expect(tipoDeAncho(CORTE_MEDIO, '100')).toBe('medio')
    expect(tipoDeAncho(CORTE_CHICO, '100')).toBe('chico')
    expect(tipoDeAncho(CORTE_CHICO * 1.25, '125')).toBe('chico')
    expect(tipoDeAncho(850, '90')).toBe('amplio') // a 90 % caben 944 px útiles
  })

  it('la escala guardada: solo valores conocidos; sin almacenamiento, 100', () => {
    vi.stubGlobal('localStorage', { getItem: () => '125' })
    expect(escalaGuardada()).toBe('125')
    vi.stubGlobal('localStorage', { getItem: () => 'grande' })
    expect(escalaGuardada()).toBe('100')
    vi.stubGlobal('localStorage', { getItem: () => { throw new Error('bloqueado') } })
    expect(escalaGuardada()).toBe('100')
  })
})
