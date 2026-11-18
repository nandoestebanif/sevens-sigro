import { describe, expect, it } from 'vitest'
import { deServidor, errorAltaProveedor } from './sunat'

describe('alta de proveedor · mensajes para el usuario (HU-31)', () => {
  it('pide 11 u 8 dígitos sin texto técnico', () => {
    expect(errorAltaProveedor('Fernando Stahl', 'x')?.texto).toMatch(/11 dígitos/)
    expect(errorAltaProveedor('206064568', 'ABC')?.campo).toBe('ruc')
  })
  it('revisa el dígito verificador y la razón social', () => {
    expect(errorAltaProveedor('20100130202', 'ABC SAC')?.texto).toMatch(/dígito verificador/)
    expect(errorAltaProveedor('20606456892', '')?.campo).toBe('razon')
    expect(errorAltaProveedor('20606456892', 'SEVENS')).toBeNull()
    expect(errorAltaProveedor('12345678', 'Ana Pérez')).toBeNull()
  })
})

describe('consulta de RUC por el servidor', () => {
  it('convierte la respuesta camelCase de pedir (antes razon_social llegaba undefined y la pantalla se caía)', () => {
    const r = deServidor({ ruc: '20606456892', razonSocial: "SEVEN'S INGENIEROS S.A.C.", estadoSunat: 'ACTIVO', condicion: 'HABIDO', direccion: null, aptoParaComprar: true })
    expect(r?.razon_social).toBe("SEVEN'S INGENIEROS S.A.C.")
    expect(r?.estado_sunat).toBe('ACTIVO')
    expect(r?.apto_para_comprar).toBe(true)
  })
  it('sin razón social no autocompleta', () => {
    expect(deServidor({ ruc: '20100130204', condicion: 'HABIDO' })).toBeNull()
  })
})
