/* Rediseño 24/11 · piezas de la piel nueva: se renderizan a HTML (sin navegador) y se revisa
   lo que importa para el usuario y el lector de pantalla, no los colores. */
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { DibujoObra, type Ilustracion } from './Ilustraciones'
import { Kpi, Ticket } from './piezas'

const html = (n: JSX.Element) => renderToStaticMarkup(n)

describe('ilustraciones de obra', () => {
  const todas: Ilustracion[] = ['factura', 'camion', 'cemento', 'grua', 'casco', 'calendario']
  it.each(todas)('«%s» es un SVG decorativo: el lector de pantalla no lo lee', (cual) => {
    const h = html(<DibujoObra cual={cual} ancho={80} />)
    expect(h).toMatch(/^<svg/)
    expect(h).toContain('aria-hidden="true"')
    expect(h).toContain('focusable="false"')
    expect(h).toContain('width="80"')
  })
  it('dos dibujos con degradado en la misma página no comparten ids', () => {
    const h = html(<div><DibujoObra cual="cemento" /><DibujoObra cual="cemento" /></div>)
    const ids = [...h.matchAll(/id="([^"]+)"/g)].map((m) => m[1])
    expect(ids.length).toBeGreaterThan(0)
    expect(new Set(ids).size).toBe(ids.length)
  })
})

describe('tarjeta ticket y Kpi', () => {
  it('el ticket es una sección con nombre, tres muescas por lado y su ilustración', () => {
    const h = html(<Ticket etiqueta="Comprado" ilustracion="factura"><span>S/ 10</span></Ticket>)
    expect(h).toMatch(/^<section class="sigro-ticket" aria-label="Comprado"/)
    expect(h.match(/class="muesca"/g)).toHaveLength(6)
    expect(h).toContain('class="ilustracion"')
    expect(h).toContain('S/ 10')
  })
  it('el Kpi muestra rótulo, prefijo, valor y pie', () => {
    const h = html(<Kpi rotulo="Capital detenido" prefijo="S/" valor="66,992" pie="Todos los almacenes" />)
    expect(h).toContain('aria-label="Capital detenido"')
    expect(h).toContain('>S/<')
    expect(h).toContain('66,992')
    expect(h).toContain('Todos los almacenes')
  })
  it('sin ilustración no deja un hueco vacío', () => {
    expect(html(<Kpi rotulo="Avance" valor="26.9 %" />)).not.toContain('class="ilustracion"')
  })
})
