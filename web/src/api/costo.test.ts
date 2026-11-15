/* HU-56 y HU-58: la web pide al servicio costo y muestra lo que llega, sin sumar. Fetch simulado. */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fechaCorte, gasto, inventario } from './costo'

let urls: string[] = []
function servidor(rutas: Record<string, unknown>) {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    urls.push(url)
    const k = Object.keys(rutas).find((x) => url.split('?')[0].endsWith(x))
    if (!k) return new Response(JSON.stringify({ codigo: 'sin_permiso', mensaje: 'No' }), { status: 403 })
    return new Response(JSON.stringify(rutas[k]), { status: 200 })
  }))
}
const OBRAS = { '/api/v1/obras': [{ id: 1, nombre: 'Obra', fecha_inicio: '2026-10-19', fecha_fin: '2027-10-17', estado: 'en_ejecucion', semana: 4, semanas_totales: 52 }] }
beforeEach(() => {
  urls = []
  const s = new Map([['sigro-sesion', JSON.stringify({ token: 't', usuario: { id: 3, nombre: 'Residente', usuario: 'resi' }, obras: [] })], ['sigro-obra-activa', '1']])
  vi.stubGlobal('sessionStorage', { getItem: (k: string) => s.get(k) ?? null, setItem: (k: string, v: string) => s.set(k, v), removeItem: (k: string) => s.delete(k) })
  vi.stubGlobal('window', { dispatchEvent: () => true })
})
afterEach(() => vi.unstubAllGlobals())

describe('HU-58 · gasto en recursos', () => {
  it('trae las cinco cifras con su definición y un solo corte; las horas sin tarifa no se inventan', async () => {
    servidor({ ...OBRAS, '/api/v1/obras/1/costo/gasto': {
      declaracion: 'no es el costo total', criterio: 'NIC 2', moneda: 'PEN', corte: '2026-11-15T23:00:00Z', otros_movimientos: '0',
      por_conciliar: false, conciliacion: [],
      cifras: [{ clave: 'comprado', valor: '1180.00', definicion: 'compras', corte: '2026-11-15T23:00:00Z' },
        { clave: 'horas_hombre', valor: null, definicion: 'jornadas', corte: '2026-11-15T23:00:00Z', nota: 'Falta la tarifa' }],
      por_familia: [{ familia: 'material', recibido: '1000', consumido: '260', en_stock: '740', hasta: '2026-11-12' }],
      horas_hombre: { horas_normales: '96', horas_extra: '6', corte: '2026-11-15T23:00:00Z' },
    } })
    const g = await gasto('2026-11-15')
    expect(urls[urls.length - 1]).toBe('/api/v1/obras/1/costo/gasto?corte=2026-11-15')
    expect(g.cifras[0]).toMatchObject({ clave: 'comprado', valor: 1180 })
    expect(g.cifras[1]).toMatchObject({ valor: null, nota: 'Falta la tarifa' })
    expect(g.horas).toMatchObject({ normales: 96, extra: 6 })
    expect(g.porFamilia[0]).toMatchObject({ familia: 'material', enStock: 740 })
  })
})

describe('HU-56 · inventario valorizado', () => {
  it('pide por almacén y fecha, y convierte los decimales del servidor', async () => {
    servidor({ ...OBRAS, '/api/v1/obras/1/costo/inventario': {
      declaracion: 'd', criterio: 'c', moneda: 'PEN', almacen: '2', corte: '2026-11-15T04:59:59Z', corte_ajustado: false, valor_total: '2080.00',
      por_almacen: [{ almacen_id: 2, almacen: 'Central', insumos: 1, valor: '2080.00', porcentaje: '100.00' }],
      por_insumo: [{ insumo_codigo: 'SIGRO-00001', descripcion: 'CEMENTO', unidad: 'bls', tipo: 'material', cantidad: '80.0000',
        costo_promedio: '26.000000', valor: '2080.00', porcentaje: '100.00', ultimo_movimiento_en: '2026-11-12T20:56:00Z' }],
    } })
    const v = await inventario('2', '2026-11-14')
    expect(urls[urls.length - 1]).toBe('/api/v1/obras/1/costo/inventario?almacen=2&corte=2026-11-14')
    expect(v).toMatchObject({ valorTotal: 2080, porAlmacen: [{ id: 2, nombre: 'Central', valor: 2080 }],
      porInsumo: [{ codigo: 'SIGRO-00001', cantidad: 80, costoPromedio: 26, valor: 2080, porcentaje: 100 }] })
  })
  it('la fecha de corte se lee en hora de Lima', () => {
    expect(fechaCorte('2026-11-15T04:59:59Z')).toContain('14/11/2026')
    expect(fechaCorte(null)).toBe('—')
  })
})
