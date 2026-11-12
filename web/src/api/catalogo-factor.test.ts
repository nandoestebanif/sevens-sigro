/* T-077 (HU-17) · factor de conversión en el catálogo contra la API real (fetch simulado). */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErrorApi } from './error'
import { catalogoHttp } from './http'

type Pedido = { url: string; metodo: string; cuerpo: unknown }
let pedidos: Pedido[] = []

function servidor(responder: (url: string, metodo: string) => { estado: number; cuerpo?: unknown }) {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit = {}) => {
    const metodo = init.method ?? 'GET'
    pedidos.push({
      url,
      metodo,
      cuerpo: init.body ? JSON.parse(String(init.body)) : undefined,
    })
    const r = responder(url, metodo)
    return new Response(r.cuerpo === undefined ? '' : JSON.stringify(r.cuerpo), { status: r.estado })
  }))
}

const ACERO = {
  id: 12,
  codigo: '0203020004',
  descripcion: 'ACERO CORRUGADO FY= 4200 Kg/cm2',
  unidad_s10: 'kg',
  unidad_compra: 'varilla',
  factor_conversion: '1.000000',
  tipo: 'material',
  activo: true,
  verificado: true,
}

beforeEach(() => {
  pedidos = []
  const almacen = new Map<string, string>([
    ['sigro-sesion', JSON.stringify({ token: 'tkn', usuario: { id: 1, nombre: 'Henry', usuario: 'henry' }, obras: [{ id: 1, nombre: 'Obra', rol: 'residente' }] })],
  ])
  vi.stubGlobal('sessionStorage', {
    getItem: (k: string) => almacen.get(k) ?? null,
    setItem: (k: string, v: string) => almacen.set(k, v),
    removeItem: (k: string) => almacen.delete(k),
  })
  vi.stubGlobal('window', { dispatchEvent: () => true })
})

afterEach(() => vi.unstubAllGlobals())

describe('catalogoHttp · factor de conversión (T-077 · HU-17)', () => {
  it('maestro incluye unidadS10 y factorConversion', async () => {
    servidor(() => ({ estado: 200, cuerpo: [{ ...ACERO, factor_conversion: '7.120000' }] }))
    const xs = await catalogoHttp.maestro()
    expect(xs[0]).toMatchObject({
      codigo: '0203020004',
      um: 'varilla',
      unidadS10: 'kg',
      factorConversion: 7.12,
    })
  })

  it('cambiarFactor envía POST con factor y motivo recortado, y consulta historial', async () => {
    servidor((url, metodo) => {
      if (url.endsWith('/catalogo/insumos')) return { estado: 200, cuerpo: [ACERO] }
      if (url.endsWith('/catalogo/insumos/12/factor-conversion') && metodo === 'POST') {
        return { estado: 200, cuerpo: { ...ACERO, factor_conversion: '7.120000' } }
      }
      if (url.endsWith('/catalogo/insumos/12/factor-conversion/historial')) {
        return {
          estado: 200,
          cuerpo: [{
            id: 1,
            factor_anterior: '1.000000',
            factor_nuevo: '7.120000',
            motivo: 'Varilla 1/2 x 9m',
            cambiado_por: 3,
            cambiado_en: '2026-11-12T15:00:00Z',
          }],
        }
      }
      return { estado: 404 }
    })

    const actualizado = await catalogoHttp.cambiarFactor('0203020004', 7.12, '  Varilla 1/2 x 9m  ')
    expect(actualizado.factorConversion).toBe(7.12)
    expect(pedidos.find((p) => p.metodo === 'POST')).toMatchObject({
      url: '/api/v1/catalogo/insumos/12/factor-conversion',
      cuerpo: { factor: 7.12, motivo: 'Varilla 1/2 x 9m' },
    })

    const hist = await catalogoHttp.historialFactor('0203020004')
    expect(hist).toEqual([{
      id: '1',
      factorAnterior: 1,
      factorNuevo: 7.12,
      motivo: 'Varilla 1/2 x 9m',
      cambiadoPor: 'Usuario #3',
      cambiadoEn: '2026-11-12T15:00:00Z',
    }])
  })

  it('rechaza factor <= 0 o motivo corto antes de llamar a la API', async () => {
    const e1 = await catalogoHttp.cambiarFactor('0203020004', 0, 'motivo válido').catch((x) => x)
    const e2 = await catalogoHttp.cambiarFactor('0203020004', 7.12, 'ok').catch((x) => x)
    expect(e1).toBeInstanceOf(ErrorApi)
    expect(e1.codigo).toBe('factor')
    expect(e2).toBeInstanceOf(ErrorApi)
    expect(e2.codigo).toBe('motivo')
    expect(pedidos).toHaveLength(0)
  })
})
