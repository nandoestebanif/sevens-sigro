import { describe, expect, it } from 'vitest'
import { MENU_POR_ROL, rutaVisible } from './menu'

describe('menú por función', () => {
  it('el almacenero ve su almacén y las compras que recibe, no el personal ni la administración', () => {
    for (const r of ['/stock', '/herramientas', '/movimientos', '/semana', '/compras', '/catalogo']) expect(rutaVisible('almacenero', r)).toBe(true)
    for (const r of ['/trabajadores', '/mano-de-obra', '/administracion', '/tablero', '/inventario']) expect(rutaVisible('almacenero', r)).toBe(false)
  })
  it('el SSOMA ve el personal y las herramientas, no el almacén ni las compras', () => {
    for (const r of ['/trabajadores', '/mano-de-obra', '/herramientas']) expect(rutaVisible('ssoma', r)).toBe(true)
    for (const r of ['/stock', '/movimientos', '/compras', '/catalogo', '/administracion']) expect(rutaVisible('ssoma', r)).toBe(false)
  })
  it('administrador, residente y gerente no tienen lista propia: decide el permiso de cada pantalla', () => {
    for (const rol of ['administrador', 'residente', 'gerente', null, undefined]) expect(rutaVisible(rol, '/administracion')).toBe(true)
    expect(Object.keys(MENU_POR_ROL).sort()).toEqual(['almacenero', 'ssoma'])
  })
  it('las subrutas siguen a su módulo', () => {
    expect(rutaVisible('almacenero', '/compras/12')).toBe(true)
    expect(rutaVisible('ssoma', '/administracion/usuarios')).toBe(false)
  })
})
