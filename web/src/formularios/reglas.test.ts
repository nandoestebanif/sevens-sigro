import { describe, expect, it } from 'vitest'
import { aNumero, cantidadPositiva, claveAceptable, importeNoNegativo, minimo } from './reglas'

describe('reglas de campos', () => {
  it('la cantidad se escribe con coma o punto y va mayor que cero', () => {
    expect(cantidadPositiva('')).toBe('Escribe la cantidad.')
    expect(cantidadPositiva('12,5')).toBeNull()
    expect(cantidadPositiva('12.5')).toBeNull()
    expect(cantidadPositiva('0')).toBe('Debe ser mayor que cero.')
    expect(cantidadPositiva('1.2.3')).toMatch(/Solo números/)
    expect(cantidadPositiva('abc')).toMatch(/Solo números/)
    expect(aNumero('12,5')).toBe(12.5)
    expect(aNumero('x')).toBeNaN()
  })
  it('el importe puede quedar vacío pero no negativo', () => {
    expect(importeNoNegativo('')).toBeNull()
    expect(importeNoNegativo('-3')).toBe('No puede ser negativo.')
    expect(importeNoNegativo('4,50')).toBeNull()
  })
  it('la clave sigue la regla del servidor', () => {
    expect(claveAceptable('Corta123')).toMatch(/10 caracteres/)
    expect(claveAceptable('todominuscula1')).toMatch(/mezclar/)
    expect(claveAceptable('SinNumerosAqui')).toMatch(/mezclar/)
    expect(claveAceptable('Segura12345')).toBeNull()
  })
  it('el motivo pide un mínimo', () => {
    expect(minimo('ok', 3, 'corto')).toBe('corto')
    expect(minimo(' hola ', 3, 'corto')).toBeNull()
  })
})
