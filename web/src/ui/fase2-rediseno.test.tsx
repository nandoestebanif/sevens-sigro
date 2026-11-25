/* Fase 2 del rediseño (25/11): acciones con ícono, detalle y reglas de trabajadores en la web. */
import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { AccionesFila, iconoDe } from './TablaDatos'
import { SelectorObra } from './SelectorObra'
import { errorBaja, validarTrabajador, type DatosTrabajador } from '../paginas/mano-de-obra-reglas'
import type { Trabajador } from '../api/manoDeObra'

const nada = () => {}

describe('acciones de fila con ícono', () => {
  it('deduce el ícono por el texto y respeta el declarado', () => {
    expect(iconoDe({ texto: 'Ver detalle', enClic: nada })).toBe('ver')
    expect(iconoDe({ texto: 'Corregir datos', enClic: nada })).toBe('editar')
    expect(iconoDe({ texto: 'Dar de baja', enClic: nada })).toBe('baja')
    expect(iconoDe({ texto: 'Anular', peligro: true, enClic: nada })).toBe('anular')
    expect(iconoDe({ texto: 'Comprobantes', enClic: nada })).toBe('archivo')
    expect(iconoDe({ texto: 'Lo que sea', icono: 'factor', enClic: nada })).toBe('factor')
    expect(iconoDe({ texto: 'Otra cosa', enClic: nada })).toBeNull()
  })
  it('muestra hasta 3 botones con nombre accesible y el resto en «⋯»', () => {
    const h = renderToStaticMarkup(<AccionesFila de="la compra F001-1" acciones={[
      { texto: 'Ver detalle', enClic: nada }, { texto: 'Recibir', enClic: nada },
      { texto: 'Anular', peligro: true, enClic: nada }, { texto: 'Comprobantes', enClic: nada }, false,
    ]} />)
    expect(h.match(/class="sigro-accion accion-/g)).toHaveLength(3)
    expect(h).toContain('aria-label="Ver detalle: la compra F001-1"')
    expect(h).toContain('Más acciones de la compra F001-1')
  })
})

describe('obras cerradas en el selector', () => {
  it('se marcan «cerrada · solo consulta»', () => {
    const h = renderToStaticMarkup(<SelectorObra obras={[{ id: '1', nombre: 'Flores' }, { id: '2', nombre: 'Cedros', cerrada: true }]} alElegir={nada} />)
    expect(h.match(/cerrada · solo consulta/g)).toHaveLength(1)
  })
})

describe('trabajadores desde la web', () => {
  const ok: DatosTrabajador = { nombres: 'Rosa', apellidoPaterno: 'Gil', apellidoMaterno: '', tipoDocumento: 'DNI', documento: '12345678', categoria: 'peon', cuadrilla: 'A', fechaIngreso: '2026-11-02' }
  it('valida como el servidor', () => {
    expect(validarTrabajador(ok)).toEqual({})
    expect(validarTrabajador({ ...ok, documento: '1234' }).documento).toMatch(/8 dígitos/)
    expect(validarTrabajador({ ...ok, apellidoPaterno: ' ' }).apellidoPaterno).toBeTruthy()
    expect(validarTrabajador({ ...ok, tipoDocumento: 'CE', documento: 'AB12' }).documento).toMatch(/6 y 20/)
  })
  it('al corregir no revisa documento ni ingreso (no se tocan)', () => {
    expect(validarTrabajador({ ...ok, documento: '', fechaIngreso: '' }, true)).toEqual({})
  })
  it('la baja no puede ser antes del ingreso', () => {
    const t: Trabajador = { id: 1, nombre: 'Gil, Rosa', tipoDocumento: 'DNI', documento: '12345678', categoria: 'peon', cuadrilla: 'A', fechaIngreso: '2026-11-02', fechaSalida: null }
    expect(errorBaja(t, '2026-11-01')).toMatch(/antes del ingreso/)
    expect(errorBaja(t, '')).toMatch(/fecha/)
    expect(errorBaja(t, '2026-11-20')).toBeNull()
  })
})
