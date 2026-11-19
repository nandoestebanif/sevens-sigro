/* Cierre del Sprint 2 · defectos de la prueba de aceptación (17–19/11):
   HU-17 c4 y D11 (quién, con nombre), HU-41 (origen en palabras), T-355 (403 explicado) y HU-58 (anular compra). */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErrorApi, mensajeDeCarga } from './error'
import { comprasHttp, textoOrigen } from './http'
import { olvidarNombres, requerimiento } from './oficina'

type Pedido = { url: string; metodo: string; cuerpo: unknown }
let pedidos: Pedido[] = []
function servidor(responder: (url: string, metodo: string) => { estado: number; cuerpo?: unknown }) {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit = {}) => {
    const metodo = init.method ?? 'GET'
    pedidos.push({ url, metodo, cuerpo: init.body ? JSON.parse(String(init.body)) : undefined })
    const r = responder(url, metodo)
    return new Response(r.cuerpo === undefined ? '' : JSON.stringify(r.cuerpo), { status: r.estado })
  }))
}
const OBRA = { id: 7, nombre: 'Multifamiliar Flores', rol: 'residente' }
beforeEach(() => {
  pedidos = []
  const almacen = new Map<string, string>([
    ['sigro-sesion', JSON.stringify({ token: 'tkn', usuario: { id: 1, nombre: 'Henry', usuario: 'henry' }, obras: [OBRA] })],
    ['sigro-obra-activa', '7'],
  ])
  vi.stubGlobal('sessionStorage', {
    getItem: (k: string) => almacen.get(k) ?? null,
    setItem: (k: string, v: string) => { almacen.set(k, v) },
    removeItem: (k: string) => { almacen.delete(k) },
  })
})
afterEach(() => { vi.unstubAllGlobals() })

describe('HU-41 · el kárdex nombra el origen del ingreso sin compra', () => {
  it('traduce el código interno y agrega el detalle', () => {
    expect(textoOrigen('saldo_otra_obra', 'Residencial Los Álamos')).toBe('Ingreso sin compra · Saldo de otra obra: Residencial Los Álamos')
    expect(textoOrigen('devolucion_cuadrilla')).toBe('Ingreso sin compra · Devolución de la cuadrilla')
    expect(textoOrigen('codigo_nuevo')).toBe('Ingreso sin compra · codigo nuevo')
    expect(textoOrigen('saldo_otra_obra', 'x')).not.toContain('saldo_otra_obra')
  })
})

describe('T-355 · una pantalla sin permiso no se queda en «Cargando…»', () => {
  it('explica el 403 y no muestra texto técnico', () => {
    expect(mensajeDeCarga(new ErrorApi('sin_permiso', 'Forbidden', 403))).toMatch(/No tienes permiso/)
    expect(mensajeDeCarga(new ErrorApi('sin_conexion', 'No se pudo conectar con el servidor de SIGRO.'))).toMatch(/conectar/)
    expect(mensajeDeCarga(new TypeError('x.map is not a function'))).toBe('No se pudo cargar esta información. Inténtalo de nuevo en unos minutos.')
  })
})

const OBRAS = [{ id: 7, nombre: 'Multifamiliar Flores', semana: 3, semanasTotales: 40 }]

describe('D11 · los ajustes del requerimiento dicen quién los hizo', () => {
  it('pone el nombre de la persona asignada a la obra y, si no la conoce, «Usuario #id»', async () => {
    olvidarNombres()
    servidor((url) => {
      if (url.endsWith('/api/v1/obras')) return { estado: 200, cuerpo: OBRAS }
      if (url.endsWith('/obras/7/asignaciones')) return { estado: 200, cuerpo: [{ usuario_id: 2, usuario_nombre: 'Adiel Luján' }] }
      if (url.endsWith('/requerimiento/5/ajustes')) {
        return { estado: 200, cuerpo: [
          { id: 1, cantidad_anterior: '100', cantidad_nueva: '120', motivo: 'Metrado corregido', ajustado_por: 2, ajustado_en: '2026-11-18 10:00' },
          { id: 2, cantidad_anterior: '120', cantidad_nueva: '110', motivo: 'Ajuste fino', ajustado_por: 4, ajustado_en: '2026-11-19 09:00' },
        ] }
      }
      return { estado: 404 }
    })
    const xs = await requerimiento.ajustes(5)
    expect(xs.map((a) => a.ajustadoPorNombre)).toEqual(['Adiel Luján', 'Usuario #4'])
    expect(xs[0].cantidadNueva).toBe(120)
  })
})

describe('HU-58 · anular una compra registrada por error (R10)', () => {
  it('exige motivo antes de llamar al servidor', async () => {
    servidor(() => ({ estado: 500 }))
    const e = await comprasHttp.anularCompra('9', 'mal').catch((x) => x)
    expect(e).toBeInstanceOf(ErrorApi)
    expect(e.codigo).toBe('motivo')
    expect(pedidos).toHaveLength(0)
  })
  it('envía el motivo recortado a /compras/{id}/anular y devuelve la compra anulada', async () => {
    servidor((url, metodo) => {
      if (url.endsWith('/api/v1/obras')) return { estado: 200, cuerpo: OBRAS }
      if (url.endsWith('/obras/7/compras/9/anular') && metodo === 'POST') {
        return { estado: 200, cuerpo: {
          id: 9, proveedor_id: 1, proveedor_razon_social: 'FERRETERIA PRUEBA SAC', tipo_comprobante: 'factura',
          numero_comprobante: 'F001-2422', fecha: '2026-11-17', estado: 'anulada', motivo_anulacion: 'Compra de prueba irreal',
          totales: { base: '10', igv: '1.80', total: '11.80' }, lineas: [],
        } }
      }
      return { estado: 404 }
    })
    const c = await comprasHttp.anularCompra('9', '  Compra de prueba irreal  ')
    expect(pedidos.find((p) => p.metodo === 'POST')?.cuerpo).toEqual({ motivo: 'Compra de prueba irreal' })
    expect(c.estado).toBe('anulada')
    expect(c.motivoAnulacion).toBe('Compra de prueba irreal')
  })
})
