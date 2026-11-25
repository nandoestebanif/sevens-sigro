/* Accesibilidad del rediseño: cada par texto/fondo que usa la web pasa el contraste AA de WCAG 2.1. */
import { describe, expect, it } from 'vitest'
import { CHIP, MARCA, TICKETS, contraste } from './paleta'

const AA = 4.5

describe('contraste del rediseño (WCAG AA)', () => {
  it('la fórmula da los valores conocidos', () => {
    expect(contraste('#000000', '#ffffff')).toBeCloseTo(21, 0)
    expect(contraste('#777777', '#ffffff')).toBeCloseTo(4.48, 1)
  })
  it('texto blanco sobre el fondo, la tarjeta y el menú', () => {
    for (const fondo of [MARCA.fondo, MARCA.tarjeta, MARCA.negro]) expect(contraste('#ffffff', fondo)).toBeGreaterThanOrEqual(AA)
  })
  it('texto secundario (#b4b5b5) sobre fondo y tarjeta', () => {
    for (const fondo of [MARCA.fondo, MARCA.tarjeta]) expect(contraste('#b4b5b5', fondo)).toBeGreaterThanOrEqual(AA)
  })
  it('el botón amarillo lleva texto negro', () => {
    expect(contraste(MARCA.negro, MARCA.amarillo)).toBeGreaterThanOrEqual(AA)
  })
  it.each(TICKETS)('texto negro sobre el ticket %s', (fondo) => {
    expect(contraste(MARCA.negro, fondo)).toBeGreaterThanOrEqual(AA)
  })
  it.each(Object.entries(CHIP.oscuro))('chip «%s» en oscuro, sobre la tarjeta', (_, color) => {
    expect(contraste(color, MARCA.tarjeta)).toBeGreaterThanOrEqual(AA)
  })
  it.each(Object.entries(CHIP.claro))('chip «%s» en claro, sobre blanco', (_, color) => {
    expect(contraste(color, '#ffffff')).toBeGreaterThanOrEqual(AA)
  })
  it('los enlaces: amarillo sobre negro y amarillo oscuro sobre blanco', () => {
    expect(contraste(MARCA.amarillo, MARCA.fondo)).toBeGreaterThanOrEqual(AA)
    expect(contraste('#7a5d00', '#ffffff')).toBeGreaterThanOrEqual(AA)
  })
})
