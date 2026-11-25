import { describe, expect, it } from 'vitest'
import { estadoDe, filtrar, ordenarPorUrgencia, resumir, dondeEsta, type Existencia } from './existencias-reglas'
import { filtrarTrabajadores, horasPorTrabajador, resumirPersonal } from './trabajadores-reglas'
import type { Trabajador } from '../api/manoDeObra'

const ex = (codigo: string, cantidad: number, o: Partial<Existencia> = {}): Existencia => ({
  insumoId: Number(codigo), codigo, descripcion: `Insumo ${codigo}`, unidad: 'und', tipo: 'material', cantidad,
  almacenes: [{ almacen: 'Central', cantidad }], unidadCompra: null, cantidadCompra: null, puntoReposicion: null, stockSeguridad: null,
  costoPromedio: null, valor: cantidad * 10, ultimoMovimiento: '2026-11-20T10:00:00Z', estado: estadoDe(cantidad, null, null), ...o,
})

describe('existencias del almacén', () => {
  it('el estado sale de lo que dijo el servidor; sin saldo es «sin stock»', () => {
    expect(estadoDe(0, true, true)).toBe('sin_stock')
    expect(estadoDe(5, true, true)).toBe('bajo_seguridad')
    expect(estadoDe(5, false, true)).toBe('bajo_punto')
    expect(estadoDe(5, null, null)).toBe('normal')
  })
  it('ordena lo más urgente primero y filtra por estado, almacén y herramientas', () => {
    const xs = [ex('3', 9), ex('1', 0), ex('2', 4, { estado: 'bajo_punto' }), ex('4', 2, { tipo: 'equipo', almacenes: [{ almacen: 'Frente', cantidad: 2 }] })]
    expect(ordenarPorUrgencia(xs).map((x) => x.codigo)).toEqual(['1', '2', '3', '4'])
    expect(filtrar(xs, { estado: 'reponer', almacen: '', soloEquipos: false }).map((x) => x.codigo)).toEqual(['2'])
    expect(filtrar(xs, { estado: 'todos', almacen: 'Frente', soloEquipos: false }).map((x) => x.codigo)).toEqual(['4'])
    expect(filtrar(xs, { estado: 'todos', almacen: '', soloEquipos: true }).map((x) => x.codigo)).toEqual(['4'])
    expect(resumir(xs)).toMatchObject({ conStock: 3, porReponer: 1, sinStock: 1, valor: 150 })
    expect(dondeEsta(ex('9', 3, { almacenes: [{ almacen: 'Central', cantidad: 2 }, { almacen: 'Frente', cantidad: 1 }, { almacen: 'B', cantidad: 0 }] }), String)).toBe('Central 2 · Frente 1')
  })
})

const t = (id: number, o: Partial<Trabajador> = {}): Trabajador => ({
  id, nombre: `T${id}`, tipoDocumento: 'DNI', documento: String(10000000 + id), categoria: 'peon', cuadrilla: 'A',
  fechaIngreso: '2026-11-01', fechaSalida: null, ...o,
})

describe('trabajadores', () => {
  const xs = [t(1, { categoria: 'operario' }), t(2), t(3, { fechaSalida: '2026-11-10', cuadrilla: 'B' }), t(4, { fechaIngreso: '2026-12-01', categoria: 'oficial' })]
  it('cuenta a los que están en la obra (también el de ingreso programado) por categoría', () => {
    expect(resumirPersonal(xs, '2026-11-25')).toEqual({ activos: 3, deBaja: 1, porCategoria: { operario: 1, oficial: 1, peon: 1 }, cuadrillas: ['A', 'B'], cuadrillasActivas: 1 })
  })
  it('filtra por estado, categoría y cuadrilla', () => {
    expect(filtrarTrabajadores(xs, { estado: 'baja', categoria: '', cuadrilla: '' }, '2026-11-25').map((x) => x.id)).toEqual([3])
    expect(filtrarTrabajadores(xs, { estado: 'todos', categoria: 'peon', cuadrilla: 'A' }, '2026-11-25').map((x) => x.id)).toEqual([2])
  })
  it('suma las horas de cada trabajador en todas las semanas', () => {
    const m = horasPorTrabajador([
      { periodo: 1, trabajadorId: 1, trabajador: 'T1', cuadrilla: 'A', categoria: 'peon', diasPresente: 5, diasAusente: 0, diasPermiso: 0, horasNormales: 40, horasExtra: 2, ultimaJornada: '2026-11-07' },
      { periodo: 2, trabajadorId: 1, trabajador: 'T1', cuadrilla: 'A', categoria: 'peon', diasPresente: 5, diasAusente: 0, diasPermiso: 0, horasNormales: 40, horasExtra: 0, ultimaJornada: '2026-11-14' },
    ])
    expect(m.get(1)).toEqual({ normales: 80, extra: 2, ultima: '2026-11-14' })
  })
})
