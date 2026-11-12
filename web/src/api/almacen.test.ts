/* HU-35 · HU-36 · HU-43 · HU-45 · HU-23: la web traduce, el servidor calcula. Rutas, cuerpos y conversión de
   código ↔ id con fetch simulado (la API real se probó aparte, de punta a punta, contra PostgreSQL). */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { almacenHttp, avanceHttp, idMovimiento, recepcionHttp } from './http'

type Pedido = { url: string; metodo: string; cuerpo: any }
let pedidos: Pedido[] = []

const INSUMOS = [
  { id: 1, codigo: 'SIGRO-00001', descripcion: 'CEMENTO PORTLAND TIPO I', unidad_s10: 'bls', unidad_compra: 'bls', factor_conversion: '1', tipo: 'material', activo: true, verificado: true },
  { id: 2, codigo: 'SIGRO-00002', descripcion: 'ARENA GRUESA', unidad_s10: 'm3', unidad_compra: 'm3', factor_conversion: '1', tipo: 'material', activo: true, verificado: true },
]

function servidor(rutas: Record<string, unknown>) {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit = {}) => {
    const metodo = init.method ?? 'GET'
    pedidos.push({ url, metodo, cuerpo: init.body ? JSON.parse(String(init.body)) : undefined })
    const clave = Object.keys(rutas).find((k) => `${metodo} ${url.split('?')[0]}`.endsWith(k))
    if (!clave) return new Response(JSON.stringify({ codigo: 'no_existe', mensaje: url }), { status: 404 })
    return new Response(JSON.stringify(rutas[clave]), { status: 200 })
  }))
}

beforeEach(() => {
  pedidos = []
  const s = new Map<string, string>([
    ['sigro-sesion', JSON.stringify({ token: 't', usuario: { id: 2, nombre: 'Almacenero', usuario: 'alma' }, obras: [{ id: 1, nombre: 'Obra', rol: 'almacenero' }] })],
    ['sigro-obra-activa', '1'],
  ])
  vi.stubGlobal('sessionStorage', { getItem: (k: string) => s.get(k) ?? null, setItem: (k: string, v: string) => s.set(k, v), removeItem: (k: string) => s.delete(k) })
  vi.stubGlobal('window', { dispatchEvent: () => true })
})
afterEach(() => vi.unstubAllGlobals())

const OBRAS = { 'GET /api/v1/obras': [{ id: 1, nombre: 'Obra', fecha_inicio: '2026-10-19', fecha_fin: '2027-10-17', estado: 'en_ejecucion', semana: 4, semanas_totales: 52 }] }

describe('aviso de exceso en la recepción (HU-36, T-297)', () => {
  it('verifica antes de guardar y registra con el motivo del exceso', async () => {
    servidor({
      ...OBRAS,
      'GET /api/v1/catalogo/insumos': INSUMOS,
      'POST /api/v1/obras/1/almacen/recepciones/verificacion': {
        compra_id: 5, hay_exceso: true,
        lineas: [{ insumo_id: 2, insumo_codigo: 'SIGRO-00002', unidad: 'm3', requerido: '50', ya_recibido: '0', con_esta: '60', exceso: '10', excede: true }],
      },
      'POST /api/v1/obras/1/almacen/recepciones': { id: 1, uuid_cliente: 'x', movimientos: [], compra_id: 5, lineas: [] },
    })
    const v = await recepcionHttp.verificar('5', [{ insumoId: '2', cantidad: 60 }, { insumoId: '1', cantidad: 0 }])
    expect(v).toMatchObject({ hayExceso: true, lineas: [{ insumoId: '2', descripcion: 'ARENA GRUESA', requerido: 50, exceso: 10, excede: true }] })
    await recepcionHttp.registrar('5', [{ insumoId: '2', cantidad: 60 }], '  merma en el vaciado  ')
    const ver = pedidos.find((p) => p.url.endsWith('/verificacion'))!
    expect(ver.cuerpo).toEqual({ compra_id: 5, lineas: [{ insumo_id: 2, cantidad: 60 }] })   // la línea en 0 no viaja
    const rec = pedidos.find((p) => p.url.endsWith('/recepciones'))!
    expect(rec.cuerpo).toMatchObject({ compra_id: 5, lineas: [{ insumo_id: 2, cantidad: 60 }], motivo_exceso: 'merma en el vaciado' })
    expect(rec.cuerpo.uuid_cliente).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('sin ninguna cantidad no se envía nada', async () => {
    servidor({})
    await expect(recepcionHttp.registrar('5', [{ insumoId: '1', cantidad: 0 }])).rejects.toMatchObject({ codigo: 'vacia' })
    expect(pedidos).toHaveLength(0)
  })
})

describe('stock y kárdex (HU-43, HU-45)', () => {
  it('el stock es el del servidor: por almacén y consolidado, sin sumar en la web', async () => {
    const fila = { insumo_id: 1, insumo_codigo: 'SIGRO-00001', descripcion: 'CEMENTO PORTLAND TIPO I', unidad: 'bls', cantidad: '80.0000', costo_promedio: '26', valor: '2080', ultimo_movimiento_en: '2026-11-12T20:56:00Z' }
    servidor({
      ...OBRAS,
      'GET /api/v1/obras/1/almacen/stock': {
        criterio: 'x', corte: '2026-11-12T20:56:00Z', advertencias: [],
        por_almacen: [{ ...fila, almacen_id: 1, almacen: 'Almacén central' }],
        consolidado: [{ ...fila, almacen_id: null, almacen: null, punto_reposicion: '100', bajo_punto_reposicion: true }],
      },
    })
    const [s] = await almacenHttp.stock()
    expect(s).toMatchObject({ insumoId: 'SIGRO-00001', saldoTotal: 80, puntoReposicion: 100, bajoPunto: true, porAlmacen: [{ almacen: 'Almacén central', saldo: 80 }] })
  })

  it('el kárdex muestra M-0007, quién anuló a quién, y el reverso usa el id del servidor', async () => {
    const mov = {
      id: 7, uuid_cliente: 'u', tipo: 'ingreso_compra', sentido: 1, proyecto_id: 1, almacen_id: 1, insumo_id: 1, insumo_codigo: 'SIGRO-00001',
      unidad: 'bls', cantidad: '80', costo_unitario: '26', motivo: null, quien_retira: null, origen: null, recepcion_id: 1,
      revierte_a_id: null, registrado_por: 2, fecha_servidor: '2026-11-12T20:56:00Z', revertido_por_id: 9, recepcion_motivo_exceso: null,
    }
    servidor({
      ...OBRAS,
      'GET /api/v1/catalogo/insumos': INSUMOS,
      'GET /api/v1/obras/1/almacenes': [{ id: 1, nombre: 'Almacén central', tipo: 'central', estado: 'activo' }],
      'GET /api/v1/obras/1/almacen/kardex': [mov],
      'POST /api/v1/obras/1/almacen/movimientos/7/reverso': { ...mov, id: 9, tipo: 'reverso', sentido: -1, revierte_a_id: 7, motivo: 'doble registro', revertido_por_id: undefined },
    })
    const [m] = await almacenHttp.kardex()
    expect(m).toMatchObject({ id: 'M-0007', tipo: 'ingreso', almacen: 'Almacén central', descripcion: 'CEMENTO PORTLAND TIPO I', anuladoPor: 'M-0009', autor: 'Almacenero', motivo: 'Recepción de compra' })
    const r = await almacenHttp.revertir(m.id, 'doble registro')
    expect(r).toMatchObject({ id: 'M-0009', tipo: 'reverso', reversaA: 'M-0007' })
    expect(pedidos.find((p) => p.url.endsWith('/reverso'))!.cuerpo).toMatchObject({ motivo: 'doble registro' })
    await expect(almacenHttp.revertir(m.id, 'no')).rejects.toMatchObject({ codigo: 'motivo' })
  })

  it('idMovimiento rellena a cuatro cifras', () => {
    expect(idMovimiento(7)).toBe('M-0007')
    expect(idMovimiento(12345)).toBe('M-12345')
  })
})

describe('avance (HU-23)', () => {
  it('lo que el servidor aún no sabe se marca, no se pone en 0 como si fuera dato', async () => {
    servidor({
      ...OBRAS,
      'GET /api/v1/obras/1/avance': {
        unidad: 'unidad_compra', base_porcentaje: 'requerimiento_total', pendiente: ['recibido y consumido: …'],
        filas: [{ insumo_id: 1, insumo_codigo: 'SIGRO-00001', descripcion: 'CEMENTO', unidad_compra: 'bls', requerido: '100', comprado: '80',
          recibido: null, consumido: null, saldo_por_comprar: '20', pct_comprado: '80.00', pct_recibido: null, pct_consumido: null }],
      },
    })
    const [a] = await avanceHttp.porInsumo()
    expect(a).toMatchObject({ codigo: 'SIGRO-00001', planificadoCantidad: 100, compradoCantidad: 80, compradoPct: 80, saldoPorComprar: 20, sinDatoAlmacen: true })
  })
})
