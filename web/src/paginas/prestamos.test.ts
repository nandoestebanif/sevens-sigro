import { describe, expect, it } from 'vitest'
import { prestadoPorInsumo, type Prestamo } from '../api/prestamos'
import { opcionesPrestamo } from './Prestamos'
import type { Existencia } from './existencias-reglas'

const p = (o: Partial<Prestamo>): Prestamo => ({
  id: 1, almacenId: 1, almacen: 'Central', insumoId: 3, insumoCodigo: '0337', descripcion: 'Taladro', unidad: 'und', cantidad: 1,
  trabajadorId: null, responsable: 'Luis', devolverEl: null, nota: null, prestadoEn: '2026-11-25T10:00:00Z', devueltoEn: null,
  estadoDevolucion: null, notaDevolucion: null, atrasado: false, ...o,
})
const taladro = { insumoId: 3, descripcion: 'Taladro', unidad: 'und', cantidad: 5,
  almacenes: [{ almacen: 'Central', cantidad: 3, almacenId: 1 }, { almacen: 'Frente', cantidad: 2, almacenId: 2 }] } as unknown as Existencia

describe('préstamos de herramientas', () => {
  it('suma lo prestado sin devolver por herramienta', () => {
    const m = prestadoPorInsumo([p({ cantidad: 2 }), p({ id: 2, cantidad: 1 }), p({ id: 3, cantidad: 4, devueltoEn: '2026-11-25T12:00:00Z' })])
    expect(m.get(3)).toBe(3)
  })
  it('lo disponible para prestar es el saldo del almacén menos lo prestado allí', () => {
    const o = opcionesPrestamo([taladro], [p({ cantidad: 2, almacenId: 1 })])
    expect(o.map((x) => [x.almacenId, x.disponible])).toEqual([[1, 1], [2, 2]])
    expect(opcionesPrestamo([taladro], [p({ cantidad: 9 })])[0].disponible).toBe(0)
  })
})
