/* Rediseño 24/11 · pantallas de entrada: portada y elegir obra (regla 9: solo las obras asignadas). */
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ElegirObra } from './ElegirObra'
import { PARTES_SIGRO, Portada } from '../ui/Portada'

const obras = [
  { id: '1', nombre: 'Multifamiliar Flores', rol: 'Administrador' },
  { id: '2', nombre: 'Residencial Los Cedros', rol: 'Residente de obra' },
]

describe('portada de entrada', () => {
  it('el titular lleva su remate resaltado y el logo con texto alternativo', () => {
    const h = renderToStaticMarkup(<Portada titulo="Hola, Adiel." remate="¿En qué obra trabajas hoy?" />)
    expect(h).toContain('<h1')
    // el lector de pantalla lee el titular entero; la vista lo anima palabra por palabra con el remate en amarillo
    expect(h).toContain('<span class="visually-hidden">Hola, Adiel. ¿En qué obra trabajas hoy?</span>')
    expect(h).toContain('class="text-primary sigro-remate"')
    expect(h.match(/class="sigro-palabra"/g)).toHaveLength(7)
    expect(h).toContain('alt="SEVEN&#x27;S Ingenieros SAC"')
  })
  it('por defecto muestra las tres partes de SIGRO', () => {
    const h = renderToStaticMarkup(<Portada titulo="x" />)
    expect(PARTES_SIGRO.map((p) => p.nombre)).toEqual(['Compras', 'Almacén', 'Gasto en recursos'])
    expect(h.match(/class="sigro-ticket"/g)).toHaveLength(3)
  })
})

describe('elegir obra', () => {
  const h = renderToStaticMarkup(<ElegirObra nombre="Adiel" obras={obras} alElegir={async () => {}} />)
  it('cada obra asignada es un botón con su nombre y el rol que tienes en ella', () => {
    expect(h.match(/<button type="button"/g)).toHaveLength(obras.length)
    for (const o of obras) { expect(h).toContain(o.nombre); expect(h).toContain(o.rol) }
  })
  it('la lista se nombra con el título y dice cuántas obras tienes', () => {
    expect(h).toContain('id="elegir-obra-titulo"')
    expect(h).toContain('aria-labelledby="elegir-obra-titulo"')
    expect(h.replace(/<!-- -->/g, '')).toContain('Tienes 2 obras asignadas')
  })
})
