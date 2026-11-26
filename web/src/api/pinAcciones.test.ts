/* 26/11: qué acciones piden el PIN antes de salir (el servidor también lo exige). */
import { describe, expect, it } from 'vitest'
import { rutaPidePin } from './pin'

describe('acciones delicadas con PIN', () => {
  it.each([
    '/obras/3/periodos/7/cerrar', '/obras/3/cerrar', '/obras/3/ampliar-plazo', '/obras/3/recortar-plazo',
    '/obras/3/compras/15/anular', '/obras/3/requerimiento/9/ajustar', '/catalogo/insumos/21/factor-conversion',
  ])('POST %s pide PIN', (r) => expect(rutaPidePin('POST', r)).toBe(true))

  it.each([
    ['GET', '/obras/3/periodos'], ['POST', '/obras/3/compras'], ['POST', '/obras/3/solicitudes/4/anular'],
    ['GET', '/catalogo/insumos/21/factor-conversion/historial'], ['POST', '/auth/pin/verificar'],
  ])('%s %s no pide PIN', (m, r) => expect(rutaPidePin(m, r)).toBe(false))
})
