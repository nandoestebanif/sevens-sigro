import { describe, expect, it } from 'vitest'
import { errorAltaProveedor } from './sunat'

describe('alta de proveedor · mensajes para el usuario (HU-31)', () => {
  it('pide 11 u 8 dígitos sin texto técnico', () => {
    expect(errorAltaProveedor('Fernando Stahl', 'x')?.texto).toMatch(/11 dígitos/)
    expect(errorAltaProveedor('206064568', 'ABC')?.campo).toBe('ruc')
  })
  it('revisa el dígito verificador y la razón social', () => {
    expect(errorAltaProveedor('20100130202', 'ABC SAC')?.texto).toMatch(/dígito verificador/)
    expect(errorAltaProveedor('20606456892', '')?.campo).toBe('razon')
    expect(errorAltaProveedor('20606456892', 'SEVENS')).toBeNull()
    expect(errorAltaProveedor('12345678', 'Ana Pérez')).toBeNull()
  })
})
