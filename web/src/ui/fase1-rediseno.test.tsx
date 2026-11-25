/* Fase 1 del rediseño (25/11): ninguna lista crece sin fin. */
import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { SelectorObra } from './SelectorObra'
import { SelectorSemana } from './SelectorSemana'
import { TAMANOS } from './tablas'

const obras = Array.from({ length: 18 }, (_, i) => ({ id: String(i + 1), nombre: `Obra ${i + 1}`, rol: 'Residente de obra' }))

describe('elegir obra con muchas obras', () => {
  const h = renderToStaticMarkup(<SelectorObra obras={obras} actual="7" alElegir={() => {}} />)
  it('muestra 5 a la vez, con buscador y «1–5 de 18»', () => {
    expect(h.match(/role="listitem"/g)).toHaveLength(5)
    expect(h).toContain('Buscar entre tus 18 obras')
    expect(h.replace(/<!-- -->/g, '')).toContain('1–5 de 18')
  })
  it('la obra actual va primero y marcada', () => {
    expect(h.indexOf('Obra 7')).toBeLessThan(h.indexOf('Obra 1<'))
    expect(h).toContain('aria-current="true"')
  })
  it('con pocas obras no hay buscador ni páginas', () => {
    const p = renderToStaticMarkup(<SelectorObra obras={obras.slice(0, 3)} alElegir={() => {}} />)
    expect(p).not.toContain('Buscar entre')
    expect(p).not.toContain('Siguientes')
  })
})

describe('semana y tablas', () => {
  it('el selector de semana no lista 144 opciones: muestra la elegida y ‹ ›', () => {
    const h = renderToStaticMarkup(<SelectorSemana numero={6} total={144} actual={6} alCambiar={() => {}} />)
    expect(h).not.toContain('<option')
    expect(h).toContain('aria-label="Semana anterior"')
    expect(h.replace(/<!-- -->/g, '')).toContain('Semana 6')
  })
  it('las tablas entran de 10 en 10', () => {
    expect(TAMANOS[0]).toBe(10)
  })
})
