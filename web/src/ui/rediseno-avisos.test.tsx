/* Rediseño 24/11 · chips, avisos y estados vacíos: se renderizan a HTML (sin navegador) y se revisa
   lo que importa para el usuario y el lector de pantalla, no los colores. */
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { Vidrio } from './piezas'
import { Chip, Vacio, type TonoChip } from './base'

const html = (n: JSX.Element) => renderToStaticMarkup(n)

describe('chips', () => {
  const tonos: TonoChip[] = ['bien', 'aviso', 'grave', 'neutro', 'info', 'marca']
  it.each(tonos)('el tono «%s» va en su clase y el texto siempre se ve', (tono) => {
    const h = html(<Chip tono={tono}>estado</Chip>)
    expect(h).toContain(`sigro-chip tono-${tono}`)
    expect(h).toContain('>estado<')
  })
})

describe('avisos y estados vacíos', () => {
  it('la franja de 4 px a la izquierda pasa a un borde fino del mismo color', () => {
    const h = html(<Vidrio estilo={{ borderLeft: '4px solid var(--grave)', padding: '16px 20px' }} etiqueta="Aviso">x</Vidrio>)
    expect(h).toContain('border:1px solid var(--grave)')
    expect(h).not.toContain('border-left')
  })
  it('un Vidrio sin franja no gana borde', () => {
    expect(html(<Vidrio etiqueta="Normal">x</Vidrio>)).not.toContain('border:1px')
  })
  it('el estado vacío trae ilustración decorativa, título y detalle', () => {
    const h = html(<Vacio titulo="Sin stock a esa fecha" detalle="No hay insumos con saldo." dibujo="grua" />)
    expect(h).toContain('class="dibujo" aria-hidden="true"')
    expect(h).toContain('Sin stock a esa fecha')
    expect(h).toContain('No hay insumos con saldo.')
  })
})
