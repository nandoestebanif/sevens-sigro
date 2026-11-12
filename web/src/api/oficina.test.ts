/* HT-11 y HU-17, HU-33: rutas y cuerpos de las acciones de oficina, con fetch simulado. */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { desvioPct, nombresDeLaObra, olvidarNombres, plazo, precios, proveedores, requerimiento } from './oficina'

let pedidos: { url: string; metodo: string; cuerpo: any }[] = []
function servidor(rutas: Record<string, unknown>) {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit = {}) => {
    const metodo = init.method ?? 'GET'
    pedidos.push({ url, metodo, cuerpo: init.body ? JSON.parse(String(init.body)) : undefined })
    const k = Object.keys(rutas).find((x) => `${metodo} ${url}`.endsWith(x))
    if (!k) return new Response(JSON.stringify({ codigo: 'sin_permiso', mensaje: 'No' }), { status: 403 })
    return new Response(JSON.stringify(rutas[k]), { status: 200 })
  }))
}
const OBRAS = { 'GET /api/v1/obras': [{ id: 1, nombre: 'Obra', fecha_inicio: '2026-10-19', fecha_fin: '2027-10-17', estado: 'en_ejecucion', semana: 4, semanas_totales: 52 }] }
beforeEach(() => {
  pedidos = []
  const s = new Map([['sigro-sesion', JSON.stringify({ token: 't', usuario: { id: 3, nombre: 'Residente', usuario: 'resi' }, obras: [] })], ['sigro-obra-activa', '1']])
  vi.stubGlobal('sessionStorage', { getItem: (k: string) => s.get(k) ?? null, setItem: (k: string, v: string) => s.set(k, v), removeItem: (k: string) => s.delete(k) })
  vi.stubGlobal('window', { dispatchEvent: () => true })
  olvidarNombres()
})
afterEach(() => vi.unstubAllGlobals())

describe('HU-33 · precio contra lo habitual', () => {
  it('compara el precio con IGV contra el promedio sin IGV', () => {
    expect(desvioPct(118, 100)).toBe(0)
    expect(desvioPct(129.8, 100)).toBe(10)
    expect(desvioPct(100, 100)).toBe(-15.3)
    expect(desvioPct(50, null)).toBeNull()
    expect(desvioPct(0, 100)).toBeNull()
  })
  it('pide la referencia del insumo en la obra activa', async () => {
    servidor({ ...OBRAS, 'GET /api/v1/obras/1/compras/precio-referencia?insumo=0221000000': { insumo_codigo: '0221000000', unidad_compra: 'bls', promedio_ponderado: '24.7712', limite_pct: '10' } })
    expect(await precios.referencia('0221000000')).toEqual({ promedio: 24.7712, unidadCompra: 'bls', limitePct: 10 })
  })
})

describe('HT-11 · acciones con rastro', () => {
  it('amplía el plazo, da de baja y ajusta el requerimiento con sus cuerpos', async () => {
    servidor({
      ...OBRAS,
      'POST /api/v1/obras/1/ampliar-plazo': {}, 'POST /api/v1/proveedores/7/baja': {},
      'POST /api/v1/obras/1/requerimiento/5/ajustar': {},
    })
    await plazo.ampliar('1', '2027-12-31', '  adenda aprobada ')
    await proveedores.darDeBaja('7')
    await requerimiento.ajustar(5, 120, 'metrado corregido')
    const p = pedidos.filter((x) => x.metodo === 'POST')
    expect(p.map((x) => x.url)).toEqual(['/api/v1/obras/1/ampliar-plazo', '/api/v1/proveedores/7/baja', '/api/v1/obras/1/requerimiento/5/ajustar'])
    expect(p[0].cuerpo).toEqual({ nueva_fecha_fin: '2027-12-31', motivo: 'adenda aprobada' })
    expect(p[2].cuerpo).toEqual({ cantidad: 120, motivo: 'metrado corregido' })
  })
})

describe('T-345 · nombres de quien registró', () => {
  it('usa las asignaciones si la obra las deja ver; si no, al menos el propio nombre', async () => {
    servidor({ ...OBRAS, 'GET /api/v1/obras/1/asignaciones': [{ usuario_id: 2, usuario_nombre: 'Almacenero local' }] })
    const m = await nombresDeLaObra()
    expect(m.get(2)).toBe('Almacenero local')
    expect(m.get(3)).toBe('Residente')
    olvidarNombres()
    servidor({ ...OBRAS })   // 403 en asignaciones (p. ej. almacenero)
    const m2 = await nombresDeLaObra()
    expect([...m2.entries()]).toEqual([[3, 'Residente']])
  })
})
