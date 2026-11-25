import { describe, expect, it } from 'vitest'
import { aNumero, cantidadPositiva, claveAceptable, excesoNumero, filtrarNumero, importeNoNegativo, LIMITE_FACTOR, minimo } from './reglas'

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

describe('topes del servidor en tiempo real', () => {
  it('la cantidad no acepta un quinto decimal ni letras', () => {
    expect(filtrarNumero('1,234', '1,2345')).toBe('1,2345')
    expect(filtrarNumero('1,2345', '1,23456')).toBe('1,2345')
    expect(filtrarNumero('12', '12a')).toBe('12')
    expect(filtrarNumero('1,5', '1,5,')).toBe('1,5')
    expect(filtrarNumero('', '1 000')).toBe('1000')
  })
  it('no pasa de 14 cifras enteras', () => {
    const catorce = '9'.repeat(14)
    expect(filtrarNumero(catorce, catorce + '9')).toBe(catorce)
  })
  it('borrar siempre se puede, aunque el valor previo viniera largo', () => {
    expect(filtrarNumero('12,333333', '12,33333')).toBe('12,33333')
  })
  it('el factor admite 6 decimales', () => {
    expect(filtrarNumero('7,12345', '7,123456', LIMITE_FACTOR)).toBe('7,123456')
    expect(cantidadPositiva('7,123456', 'Escribe el factor.', LIMITE_FACTOR)).toBeNull()
  })
  it('la validación dice cuál es el exceso', () => {
    expect(cantidadPositiva('2,12345')).toBe('Máximo 4 decimales.')
    expect(cantidadPositiva('1'.repeat(15))).toBe('Máximo 14 cifras enteras.')
    expect(cantidadPositiva('2,1234')).toBeNull()
    expect(importeNoNegativo('4,12345')).toBe('Máximo 4 decimales.')
    expect(excesoNumero('3,5')).toBeNull()
  })
})
