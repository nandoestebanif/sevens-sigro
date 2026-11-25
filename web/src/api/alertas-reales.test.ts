/* Con el servidor, la alerta de reposición NO puede traer datos de la demo (fases, varillas inventadas). */
import { describe, expect, it, vi } from 'vitest'
import { ClienteHttp } from './cliente'
import { esHoraHombre } from './http'
import type { AvanceInsumo, StockInsumo } from './tipos'

const stock = (insumoId: string, saldoTotal: number, punto: number | null, bajoPunto: boolean): StockInsumo =>
  ({ insumoId, descripcion: `Insumo ${insumoId}`, unidad: 'kg', porAlmacen: [], saldoTotal, puntoReposicion: punto, bajoPunto, ultimoMovimiento: '2026-11-24T10:00:00Z' })
const saldo = (codigo: string, falta: number): AvanceInsumo => ({
  insumoId: '9', codigo, descripcion: '', unidadS10: 'kg', unidadCompra: 'varilla 9 m', fase: 'Toda la obra', critico: false,
  planificadoCantidad: 0, planificadoImporte: 0, compradoCantidad: 0, compradoPct: 0, recibidoPct: 0, consumidoPct: 0, gastado: 0,
  saldoPorComprar: falta, desviacionPuntos: 0,
})

describe('alertas de reposición con el servidor (antes mezclaban la demo)', () => {
  it('solo insumos bajo su punto, con lo que falta por comprar del servidor y sin fases inventadas', async () => {
    const c = new ClienteHttp()
    vi.spyOn(c, 'consultarStock').mockResolvedValue([stock('0203020004', 12, 29, true), stock('0221000000', 500, 60, false), stock('X', 3, null, false)])
    vi.spyOn(c, 'saldoPorComprar').mockResolvedValue([saldo('0203020004', 2811)])
    const xs = await c.alertasReposicion()
    expect(xs).toHaveLength(1)
    expect(xs[0]).toMatchObject({ insumoId: '0203020004', faltaPorComprar: 2811, sugerencia: 2811, unidad: 'varilla 9 m', punto: 29 })
    expect(JSON.stringify(xs)).not.toMatch(/pisos|4 040|600|noviembre/)
  })
})

describe('lo que no se compra', () => {
  it('reconoce las horas hombre del S10', () => {
    expect(['hh', 'HH', 'h.h.', ' hh '].every(esHoraHombre)).toBe(true)
    expect(['kg', 'bls', 'HE', 'und', 'hm'].some(esHoraHombre)).toBe(false)
  })
})
