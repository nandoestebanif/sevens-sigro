/* T-149 (HU-32) y T-158 (HU-35) · compras y recepción contra la API real (fetch simulado). */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErrorApi } from './error'
import { comprasHttp, recepcionHttp } from './http'

type Pedido = { url: string; metodo: string; cuerpo: unknown; autorizacion: string | null }
let pedidos: Pedido[] = []

function servidor(responder: (url: string, metodo: string) => { estado: number; cuerpo?: unknown }) {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit = {}) => {
    const metodo = init.method ?? 'GET'
    const h = (init.headers ?? {}) as Record<string, string>
    pedidos.push({
      url,
      metodo,
      cuerpo: typeof init.body === 'string' ? JSON.parse(init.body) : init.body,
      autorizacion: h.Authorization ?? null,
    })
    const r = responder(url, metodo)
    return new Response(r.cuerpo === undefined ? '' : JSON.stringify(r.cuerpo), { status: r.estado })
  }))
}

const OBRA = { id: 1, nombre: 'Multifamiliar Flores', fecha_inicio: '2026-07-20', fecha_fin: '2027-07-18', estado: 'en_ejecucion', semana: 14, semanas_totales: 52 }

const compraApi = (id: number, overrides: Record<string, unknown> = {}) => ({
  id,
  proveedor_id: 10,
  proveedor_razon_social: 'DINOSELVA IQUITOS S.A.C.',
  comprobantes_vigentes: 0,
  tipo_comprobante: 'factura',
  numero_comprobante: `F001-${id}`,
  fecha: '2026-11-12',
  estado: 'registrada',
  totales: { base: '100.00', igv: '18.00', total: '118.00' },
  lineas: [{ insumo_codigo: '0221000000', descripcion: 'CEMENTO PORTLAND TIPO I', cantidad: '10', unidad_compra: 'bls', precio_unitario: '11.80' }],
  ...overrides,
})

beforeEach(() => {
  pedidos = []
  const almacen = new Map<string, string>([
    ['sigro-sesion', JSON.stringify({ token: 'tkn', usuario: { id: 1, nombre: 'Henry', usuario: 'henry' }, obras: [{ id: 1, nombre: 'Multifamiliar Flores', rol: 'residente' }] })],
  ])
  vi.stubGlobal('sessionStorage', {
    getItem: (k: string) => almacen.get(k) ?? null,
    setItem: (k: string, v: string) => almacen.set(k, v),
    removeItem: (k: string) => almacen.delete(k),
  })
  vi.stubGlobal('window', { dispatchEvent: () => true })
})

afterEach(() => vi.unstubAllGlobals())

describe('comprasHttp (T-149 · HU-32)', () => {
  it('lista compras leyendo comprobantes_vigentes, anuladas y el estado derivado de recepción', async () => {
    servidor((url) => {
      if (url.endsWith('/api/v1/obras')) return { estado: 200, cuerpo: [OBRA] }
      if (url.endsWith('/api/v1/obras/1/compras')) {
        return {
          estado: 200,
          cuerpo: [
            compraApi(1, { comprobantes_vigentes: 2 }),
            compraApi(2),
            compraApi(3),
            compraApi(4, { estado: 'anulada' }),
          ],
        }
      }
      if (url.endsWith('/almacen/compras/1/recepcion')) {
        return { estado: 200, cuerpo: [{ insumo_id: 5, unidad_compra: 'bls', pedido: '10', recibido: '10', pendiente: '0' }] }
      }
      if (url.endsWith('/almacen/compras/2/recepcion')) {
        return { estado: 200, cuerpo: [{ insumo_id: 5, unidad_compra: 'bls', pedido: '10', recibido: '4', pendiente: '6' }] }
      }
      if (url.endsWith('/almacen/compras/3/recepcion')) {
        return { estado: 503, cuerpo: { codigo: 'servicio_no_disponible', mensaje: 'Almacén apagado' } }
      }
      return { estado: 404 }
    })

    const xs = await comprasHttp.listar()
    expect(xs.map((x) => [x.id, x.estado, x.adjunto])).toEqual([
      ['1', 'recibida', true],
      ['2', 'recibida_parcial', false],
      ['3', 'registrada', false],
      ['4', 'anulada', false],
    ])
  })

  it('registra compra enviando snake_case y sin enviar totales calculados', async () => {
    servidor((url, metodo) => {
      if (url.endsWith('/api/v1/obras')) return { estado: 200, cuerpo: [OBRA] }
      if (url.endsWith('/api/v1/obras/1/compras') && metodo === 'POST') return { estado: 201, cuerpo: compraApi(7) }
      return { estado: 404 }
    })

    const c = await comprasHttp.registrar({
      proveedorId: '10',
      tipoComprobante: 'factura',
      numeroComprobante: ' F001-7 ',
      fecha: '2026-11-12',
      lineas: [{ insumoId: '0221000000', descripcion: 'CEMENTO', cantidad: 10, unidadCompra: 'bls', precioUnitario: 11.8 }],
    })
    expect(c.id).toBe('7')
    const post = pedidos.find((p) => p.metodo === 'POST')!
    expect(post.cuerpo).toEqual({
      proveedor_id: 10,
      tipo_comprobante: 'factura',
      numero_comprobante: 'F001-7',
      fecha: '2026-11-12',
      lineas: [{ insumo_codigo: '0221000000', cantidad: 10, unidad_compra: 'bls', precio_unitario: 11.8 }],
    })
  })
})

describe('recepcionHttp (T-158 · HU-35)', () => {
  it('detalle combina almacén, compra y catálogo para mostrar descripción y precio', async () => {
    servidor((url) => {
      if (url.endsWith('/api/v1/obras')) return { estado: 200, cuerpo: [OBRA] }
      if (url.endsWith('/almacen/compras/7/recepcion')) {
        return { estado: 200, cuerpo: [{ insumo_id: 55, unidad_compra: 'bls', pedido: '10', recibido: '3', pendiente: '7' }] }
      }
      if (url.endsWith('/obras/1/compras/7')) return { estado: 200, cuerpo: compraApi(7) }
      if (url.endsWith('/catalogo/insumos')) {
        return {
          estado: 200,
          cuerpo: [{ id: 55, codigo: '0221000000', descripcion: 'CEMENTO PORTLAND TIPO I', unidad_s10: 'bls', unidad_compra: 'bls', factor_conversion: '1', tipo: 'material', activo: true, verificado: true }],
        }
      }
      return { estado: 404 }
    })

    const det = await recepcionHttp.detalle('7')
    expect(det).toEqual([{
      insumoId: '55',
      descripcion: 'CEMENTO PORTLAND TIPO I',
      unidadCompra: 'bls',
      precioUnitario: 11.8,
      pedido: 10,
      recibido: 3,
      pendiente: 7,
    }])
  })

  it('registrar filtra líneas en cero, genera uuid_cliente y manda ids numéricos', async () => {
    servidor((url, metodo) => {
      if (url.endsWith('/api/v1/obras')) return { estado: 200, cuerpo: [OBRA] }
      if (url.endsWith('/almacen/recepciones') && metodo === 'POST') return { estado: 201, cuerpo: { id: 1, uuid_cliente: 'u', compra_id: 7, movimientos: [], lineas: [] } }
      return { estado: 404 }
    })

    await recepcionHttp.registrar('7', [
      { insumoId: '55', cantidad: 7 },
      { insumoId: '56', cantidad: 0 },
    ])
    const post = pedidos.find((p) => p.metodo === 'POST')!
    expect(post.url).toContain('/api/v1/obras/1/almacen/recepciones')
    expect(post.cuerpo).toMatchObject({
      compra_id: 7,
      lineas: [{ insumo_id: 55, cantidad: 7 }],
    })
    expect((post.cuerpo as { uuid_cliente: string }).uuid_cliente).toMatch(/^[0-9a-f-]{36}$/i)
  })

  it('registrar sin cantidades mayores a cero falla antes de llamar a la API', async () => {
    const e = await recepcionHttp.registrar('7', [{ insumoId: '55', cantidad: 0 }]).catch((x) => x)
    expect(e).toBeInstanceOf(ErrorApi)
    expect(e.codigo).toBe('vacia')
    expect(pedidos).toHaveLength(0)
  })

  it('HU-34: adjunta, lista, descarga y anula comprobantes con motivo', async () => {
    const comp = {
      id: 99, compra_id: 7, nombre: 'factura-f001.pdf', tipo_mime: 'application/pdf',
      tamano_bytes: 2048, sha256: 'abc123def456', estado: 'vigente',
      subido_por: 3, subido_en: '2026-11-17T10:00:00Z', anulado_por: null, anulado_en: null, motivo_anulacion: null,
    }
    servidor((url, metodo) => {
      if (url.endsWith('/api/v1/obras')) return { estado: 200, cuerpo: [OBRA] }
      if (url.endsWith('/compras/7/comprobantes') && metodo === 'POST') return { estado: 201, cuerpo: comp }
      if (url.endsWith('/compras/7/comprobantes') && metodo === 'GET') return { estado: 200, cuerpo: [comp] }
      if (url.endsWith('/compras/7/comprobantes/99') && metodo === 'GET') return { estado: 200, cuerpo: 'PDF' }
      if (url.endsWith('/compras/7/comprobantes/99/anulacion') && metodo === 'POST') {
        return { estado: 200, cuerpo: { ...comp, estado: 'anulado', motivo_anulacion: 'archivo equivocado' } }
      }
      return { estado: 404 }
    })

    const file = new File(['%PDF-1.4'], 'factura-f001.pdf', { type: 'application/pdf' })
    const subido = await comprasHttp.subirComprobante('7', file)
    expect(subido).toMatchObject({ id: '99', compraId: '7', nombre: 'factura-f001.pdf', estado: 'vigente' })
    expect(pedidos.find((p) => p.url.endsWith('/compras/7/comprobantes') && p.metodo === 'POST')?.cuerpo).toBeInstanceOf(FormData)

    const lista = await comprasHttp.listarComprobantes('7')
    expect(lista).toHaveLength(1)

    const blob = await comprasHttp.descargarComprobante('7', '99')
    expect(blob).toBeInstanceOf(Blob)

    const anulado = await comprasHttp.anularComprobante('7', '99', 'archivo equivocado')
    expect(anulado).toMatchObject({ estado: 'anulado', motivoAnulacion: 'archivo equivocado' })
    await expect(comprasHttp.anularComprobante('7', '99', 'abc')).rejects.toMatchObject({ codigo: 'motivo' })
  })
})
