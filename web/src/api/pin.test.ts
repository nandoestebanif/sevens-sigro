// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { guardarPin, olvidarPin, pinAceptable, tokenPin } from './pin'
import { queHizo, tonoEstado } from '../paginas/administracion/Bitacora'

describe('PIN del panel de administración', () => {
  it('acepta 6 dígitos que no sean repetidos ni seguidos', () => {
    expect(pinAceptable('481937')).toBeNull()
    expect(pinAceptable('111111')).toMatch(/fácil/)
    expect(pinAceptable('123456')).toMatch(/fácil/)
    expect(pinAceptable('654321')).toMatch(/fácil/)
    expect(pinAceptable('12345')).toMatch(/6 dígitos/)
  })
  it('el token vale 15 minutos y se puede olvidar', () => {
    guardarPin('t-1', 900, 1_000)
    expect(tokenPin(1_000 + 60_000)).toBe('t-1')
    expect(tokenPin(1_000 + 900_000)).toBeNull()
    olvidarPin()
    expect(tokenPin(1_001)).toBeNull()
  })
})

describe('bitácora', () => {
  it('se lee en palabras y marca los rechazos', () => {
    expect(queHizo('/api/v1/obras/7/almacen/salidas')).toBe('Almacen · salidas')
    expect(queHizo('/api/v1/usuarios/5/clave-temporal')).toBe('Usuarios · clave temporal')
    expect(tonoEstado(201)).toBe('bien')
    expect(tonoEstado(403)).toBe('grave')
    expect(tonoEstado(422)).toBe('aviso')
  })
})

describe('consultaBitacora', () => {
  it('solo manda los filtros puestos y recorta el texto', async () => {
    const { consultaBitacora } = await import('./pin')
    expect(consultaBitacora()).toBe('limite=500')
    expect(consultaBitacora({ desde: '2026-11-01', usuarioId: 7, q: '  salidas ', solo: 'rechazos' }))
      .toBe('limite=500&desde=2026-11-01&usuario_id=7&q=salidas&solo=rechazos')
    expect(consultaBitacora({ q: 'x'.repeat(80) })).toContain('q=' + 'x'.repeat(60) + '')
    expect(consultaBitacora({ q: 'x'.repeat(80) })).not.toContain('x'.repeat(61))
  })
})

describe('detalle de la bitácora', () => {
  it('explica el resultado y el ámbito', async () => {
    const { significado, ambito } = await import('../paginas/administracion/Bitacora')
    expect(significado(403)).toMatch(/permiso/)
    expect(significado(201)).toMatch(/creó/)
    expect(significado(503)).toMatch(/servidor/)
    expect(ambito('/api/v1/obras/3/almacen/salidas')).toBe('De esta obra')
    expect(ambito('/api/v1/usuarios')).toMatch(/General/)
  })
})
