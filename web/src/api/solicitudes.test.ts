import { describe, expect, it } from 'vitest'
import { erroresSolicitud } from './solicitudes'

describe('solicitud de materiales · reglas del formulario', () => {
  it('pide al menos una línea con cantidad', () => {
    expect(erroresSolicitud([])).toBe('Agrega al menos un insumo.')
    expect(erroresSolicitud([{ insumoCodigo: 'A', cantidad: 0, unidadCompra: 'bls' }])).toMatch(/mayor que cero/)
  })
  it('no admite el mismo insumo dos veces (el servidor también lo rechaza)', () => {
    expect(erroresSolicitud([{ insumoCodigo: 'A', cantidad: 1, unidadCompra: 'u' }, { insumoCodigo: 'A', cantidad: 2, unidadCompra: 'u' }])).toMatch(/dos veces/)
  })
  it('acepta líneas del catálogo y descripciones libres', () => {
    expect(erroresSolicitud([{ insumoCodigo: 'A', cantidad: 1, unidadCompra: 'u' }, { descripcionLibre: 'Disco de corte 7"', cantidad: 3, unidadCompra: 'u' }])).toBeNull()
  })
})
