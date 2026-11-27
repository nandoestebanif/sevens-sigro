// @vitest-environment jsdom
/* Listas de la web (27/11): filtrar por la fecha que se elija y «Ver detalle» en cada fila. */
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it } from 'vitest'
import { TablaDatos } from './TablaDatos'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
// el panel lateral (Offcanvas) pregunta el ancho de pantalla; jsdom no trae matchMedia
window.matchMedia ??= ((q: string) => ({ matches: false, media: q, onchange: null, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {}, dispatchEvent: () => false })) as unknown as typeof window.matchMedia

interface Cel { id: number; nombre: string; codigo: string; pedido: string; autorizado: string | null }
const filas: Cel[] = [
  { id: 1, nombre: 'Persona Uno', codigo: 'cel-1', pedido: '2026-11-19T20:11:00Z', autorizado: '2026-11-19T20:11:00Z' },
  { id: 2, nombre: 'Persona Dos', codigo: 'cel-2', pedido: '2026-11-25T19:37:00Z', autorizado: null },
  { id: 3, nombre: 'Persona Tres', codigo: 'cel-3', pedido: '2026-11-27T12:36:00Z', autorizado: '2026-11-27T12:36:00Z' },
]

let raiz: Root | null = null
let caja: HTMLDivElement | null = null
afterEach(() => { act(() => raiz?.unmount()); caja?.remove(); raiz = null; caja = null })

function montar(detalle?: false) {
  caja = document.createElement('div'); document.body.appendChild(caja)
  raiz = createRoot(caja)
  act(() => raiz!.render(
    <TablaDatos etiqueta="Celulares" filas={filas} clave={(f) => f.id} buscar={(f) => f.nombre}
      vacio={{ titulo: 'Sin', detalle: '' }} detalle={detalle}
      fechas={[{ clave: 'pedido', titulo: 'Pedido', valor: (f) => f.pedido }, { clave: 'aut', titulo: 'Autorización', valor: (f) => f.autorizado }]}
      columnas={[
        { clave: 'nombre', titulo: 'Persona', valor: (f) => f.nombre, celda: (f) => f.nombre },
        { clave: 'codigo', titulo: 'Código', celda: (f) => f.codigo, soloEscritorio: true },
      ]} />,
  ))
  return caja
}
const escribir = (el: HTMLInputElement, v: string) => act(() => {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, v)
  el.dispatchEvent(new Event('input', { bubbles: true }))
})
const nombres = (c: HTMLElement) => [...c.querySelectorAll('tbody tr td.principal')].map((td) => td.textContent)

describe('filtro por fechas en las listas', () => {
  it('elige qué fecha y filtra por el rango (días de Lima)', () => {
    const c = montar()
    const desde = c.querySelector<HTMLInputElement>('input[aria-label="Desde (Pedido)"]')!
    escribir(desde, '25/11/2026')
    expect(nombres(c)).toEqual(['Persona Dos', 'Persona Tres'])
    expect(c.textContent).toContain('2 de 3')
    const sel = c.querySelector<HTMLSelectElement>('select[aria-label="Qué fecha"]')!
    act(() => { sel.value = 'aut'; sel.dispatchEvent(new Event('change', { bubbles: true })) })
    expect(nombres(c)).toEqual(['Persona Tres']) // la que nunca se autorizó no entra
    const quitar = [...c.querySelectorAll('button')].find((b) => b.textContent === 'Quitar fechas')!
    act(() => quitar.click())
    expect(nombres(c)).toHaveLength(3)
  })
})

describe('ver detalle en cada fila', () => {
  it('abre el panel con todas las columnas, también las que el celular oculta', () => {
    const c = montar()
    const ver = c.querySelector<HTMLButtonElement>('button[aria-label="Ver detalle: Persona Dos"]')!
    act(() => ver.click())
    const panel = document.querySelector('.sigro-detalle')!
    expect(panel.textContent).toContain('Persona Dos')
    expect(panel.textContent).toContain('Código')
    expect(panel.textContent).toContain('cel-2')
  })
  it('la lista que ya tiene su propio detalle lo apaga', () => {
    const c = montar(false)
    expect(c.querySelector('button[aria-label^="Ver detalle"]')).toBeNull()
  })
})
