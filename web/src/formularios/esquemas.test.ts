import { describe, expect, it } from 'vitest'
import { esquemaEntrar, esquemaObra, esquemaProveedor, primerError } from './esquemas'

describe('esquemaProveedor (Zod)', () => {
  it('acepta un RUC válido y un DNI', () => {
    expect(esquemaProveedor.safeParse({ ruc: '20606456892', razonSocial: 'SEVENS INGENIEROS SAC' }).success).toBe(true)
    expect(esquemaProveedor.safeParse({ ruc: '12345678', razonSocial: 'Ana Pérez' }).success).toBe(true)
  })
  it('rechaza letras, largos raros y el dígito verificador malo, cada uno con su mensaje', () => {
    expect(primerError(esquemaProveedor, { ruc: 'abc', razonSocial: 'X SAC' })?.texto).toMatch(/11 dígitos/)
    expect(primerError(esquemaProveedor, { ruc: '206064568', razonSocial: 'X SAC' })?.campo).toBe('ruc')
    expect(primerError(esquemaProveedor, { ruc: '20100130202', razonSocial: 'X SAC' })?.texto).toMatch(/dígito verificador/)
  })
  it('exige razón social', () => {
    expect(primerError(esquemaProveedor, { ruc: '20606456892', razonSocial: ' ' })?.campo).toBe('razonSocial')
  })
})

describe('esquemaObra (Zod)', () => {
  const ok = { nombre: 'Multifamiliar Flores', presupuesto: '2701006-002', inicio: '2026-07-20', fin: '2027-07-18' }
  it('acepta una obra bien escrita', () => { expect(esquemaObra.safeParse(ok).success).toBe(true) })
  it('el fin antes del inicio marca el campo «fin»', () => {
    expect(primerError(esquemaObra, { ...ok, fin: '2026-01-01' })).toEqual({ campo: 'fin', texto: 'El fin debe ser posterior al inicio.' })
  })
  it('presupuesto vacío vale; con otro formato no', () => {
    expect(esquemaObra.safeParse({ ...ok, presupuesto: '' }).success).toBe(true)
    expect(primerError(esquemaObra, { ...ok, presupuesto: '27-01' })?.campo).toBe('presupuesto')
  })
})

describe('esquemaEntrar (Zod)', () => {
  it('pide usuario y contraseña', () => {
    expect(primerError(esquemaEntrar, { usuario: '  ', clave: 'x' })?.campo).toBe('usuario')
    expect(primerError(esquemaEntrar, { usuario: 'ana', clave: '' })?.campo).toBe('clave')
  })
})
