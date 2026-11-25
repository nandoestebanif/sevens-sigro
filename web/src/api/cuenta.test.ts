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
    expect(errorCambioClave('a', 'corta', 'corta')).toMatch(/10 caracteres/)
    expect(errorCambioClave('Igual12345', 'Igual12345', 'Igual12345')).toMatch(/distinta/)
    expect(errorCambioClave('Vieja12345', 'Nueva12345', 'Nueva123456')).toMatch(/no coinciden/)
    expect(errorCambioClave('Vieja12345', 'Nueva12345', 'Nueva12345')).toBeNull()
  })
})
