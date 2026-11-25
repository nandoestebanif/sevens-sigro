/* Fase 3 del rediseño (25/11): fecha dd/mm/aaaa, arrastrar y soltar, avance del plazo. */
import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { aPeru, cuadricula, deTexto, enmascarar, Fecha } from './Fecha'
import { REGLA_COMPROBANTE, REGLA_S10, SoltarArchivo, nombresTipos, revisarArchivo } from './SoltarArchivo'
import { avancePlazo } from '../paginas/Obra'

describe('selector de fecha', () => {
  it('se escribe como en Perú y el valor sigue siendo ISO', () => {
    expect(enmascarar('25112026')).toBe('25/11/2026')
    expect(enmascarar('2511')).toBe('25/11')
    expect(deTexto('25/11/2026')).toBe('2026-11-25')
    expect(deTexto('5/1/2027')).toBe('2027-01-05')
    expect(aPeru('2026-11-25')).toBe('25/11/2026')
  })
  it('rechaza fechas que no existen', () => {
    expect(deTexto('31/02/2026')).toBeNull()
    expect(deTexto('29/02/2028')).toBe('2028-02-29')
    expect(deTexto('2026-11-25')).toBeNull()
  })
  it('el calendario empieza en lunes (noviembre 2026 empieza en domingo)', () => {
    const c = cuadricula(2026, 11)
    expect(c.slice(0, 7)).toEqual([null, null, null, null, null, null, 1])
    expect(c.length % 7).toBe(0)
    expect(c.filter(Boolean)).toHaveLength(30)
  })
  it('muestra dd/mm/aaaa y un botón para el calendario', () => {
    const h = renderToStaticMarkup(<Fecha id="f" valor="2026-11-25" alCambiar={() => {}} etiqueta="Fecha de corte" />)
    expect(h).toContain('value="25/11/2026"')
    expect(h).toContain('placeholder="dd/mm/aaaa"')
    expect(h).toContain('Abrir calendario')
  })
})

describe('arrastrar y soltar', () => {
  const a = (name: string, type: string, size = 2048) => ({ name, type, size })
  it('revisa tipo y tamaño antes de subir', () => {
    expect(revisarArchivo(a('relacion.pdf', 'application/pdf'), REGLA_S10)).toBeNull()
    expect(revisarArchivo(a('relacion.PDF', ''), REGLA_S10)).toBeNull()
    expect(revisarArchivo(a('foto.jpg', 'image/jpeg'), REGLA_S10)).toMatch(/no es PDF/)
    expect(revisarArchivo(a('factura.png', 'image/png', 11 * 1024 * 1024), REGLA_COMPROBANTE)).toMatch(/máximo es 10 MB/)
    expect(revisarArchivo(a('vacio.pdf', 'application/pdf', 0), REGLA_COMPROBANTE)).toMatch(/vacío/)
    expect(nombresTipos(REGLA_COMPROBANTE)).toBe('PDF, JPG o PNG')
  })
  it('se usa con teclado y el input conserva la etiqueta del campo', () => {
    const h = renderToStaticMarkup(<SoltarArchivo regla={REGLA_S10} archivo={null} alElegir={() => {}} titulo="Suelta aquí el PDF" etiqueta="PDF del S10" />)
    expect(h).toContain('role="button"')
    expect(h).toContain('tabindex="0"')
    expect(h).toContain('aria-label="PDF del S10"')
  })
})

describe('avance del plazo de la obra', () => {
  it('cuenta días transcurridos sobre el total y no se pasa de 0–100', () => {
    expect(avancePlazo('2026-01-01', '2026-01-10', '2026-01-05')).toMatchObject({ pct: 50, transcurridos: 5, total: 10, faltan: 5 })
    expect(avancePlazo('2026-01-01', '2026-01-10', '2025-12-01').pct).toBe(0)
    expect(avancePlazo('2026-01-01', '2026-01-10', '2027-01-01').pct).toBe(100)
  })
})
