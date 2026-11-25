import { describe, expect, it } from 'vitest'
import { destinoDeAviso, errorCambioClave } from './cuenta'

describe('avisos', () => {
  it('cada aviso lleva a su pantalla', () => {
    expect(destinoDeAviso({ tipo: 'solicitud.emitida', datos: { solicitud_id: 3 } })).toBe('/solicitudes')
    expect(destinoDeAviso({ tipo: 'compra.registrada', datos: {} })).toBe('/compras')
    expect(destinoDeAviso({ tipo: 'otro', datos: {} })).toBeNull()
  })
})

describe('cambiar contraseña', () => {
  it('valida lo evidente antes de ir al servidor', () => {
    expect(errorCambioClave('', 'Nueva123', 'Nueva123')).toMatch(/actual/)
    expect(errorCambioClave('a', 'corta', 'corta')).toMatch(/8 caracteres/)
    expect(errorCambioClave('Igual1234', 'Igual1234', 'Igual1234')).toMatch(/distinta/)
    expect(errorCambioClave('Vieja1234', 'Nueva1234', 'Nueva12345')).toMatch(/no coinciden/)
    expect(errorCambioClave('Vieja1234', 'Nueva1234', 'Nueva1234')).toBeNull()
  })
})
