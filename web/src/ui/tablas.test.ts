import { describe, expect, it } from 'vitest'
import { alternarOrden, filtrarTexto, normalizar, numerosDePagina, ordenar, paginar } from './tablas'

const filas = [
  { codigo: '10', nombre: 'Árido', valor: 30 },
  { codigo: '2', nombre: 'cemento', valor: null },
  { codigo: '1', nombre: 'Acero', valor: 10 },
]
const v = (f: (typeof filas)[number], k: 'codigo' | 'nombre' | 'valor') => f[k]

describe('ordenar', () => {
  it('ordena texto en español sin importar tildes ni mayúsculas', () => {
    expect(ordenar(filas, { clave: 'nombre', dir: 'asc' }, v).map((f) => f.nombre)).toEqual(['Acero', 'Árido', 'cemento'])
  })
  it('ordena códigos como números («2» antes que «10»)', () => {
    expect(ordenar(filas, { clave: 'codigo', dir: 'asc' }, v).map((f) => f.codigo)).toEqual(['1', '2', '10'])
  })
  it('deja los vacíos al final también en descendente', () => {
    expect(ordenar(filas, { clave: 'valor', dir: 'desc' }, v).map((f) => f.valor)).toEqual([30, 10, null])
  })
  it('no modifica la lista original', () => {
    const copia = [...filas]
    ordenar(filas, { clave: 'nombre', dir: 'desc' }, v)
    expect(filas).toEqual(copia)
  })
  it('alterna asc → desc en la misma columna y vuelve a asc en otra', () => {
    const a = alternarOrden(null, 'nombre')
    expect(a).toEqual({ clave: 'nombre', dir: 'asc' })
    expect(alternarOrden(a, 'nombre')).toEqual({ clave: 'nombre', dir: 'desc' })
    expect(alternarOrden(a, 'valor')).toEqual({ clave: 'valor', dir: 'asc' })
  })
})

describe('paginar', () => {
  const lista = Array.from({ length: 53 }, (_, i) => i + 1)
  it('corta 25 por página y dice el rango', () => {
    const p = paginar(lista, 3, 25)
    expect(p.items).toEqual([51, 52, 53])
    expect([p.desde, p.hasta, p.paginas, p.total]).toEqual([51, 53, 3, 53])
  })
  it('ajusta una página que no existe', () => {
    expect(paginar(lista, 99, 25).pagina).toBe(3)
    expect(paginar(lista, 0, 25).pagina).toBe(1)
  })
  it('lista vacía: una página sin filas', () => {
    expect(paginar([], 1, 25)).toMatchObject({ items: [], paginas: 1, desde: 0, hasta: 0 })
  })
  it('muestra puntos suspensivos entre los extremos y las vecinas', () => {
    expect(numerosDePagina(6, 20)).toEqual([1, null, 4, 5, 6, 7, 8, null, 20])
    expect(numerosDePagina(1, 3)).toEqual([1, 2, 3])
  })
})


describe('buscar en una tabla', () => {
  const xs = ['Juan Pérez · Peón', 'Rosa Gil · Oficial', 'CEMENTO PORTLAND TIPO I']
  it('ignora tildes y mayúsculas', () => {
    expect(normalizar('  Peón ')).toBe('peon')
    expect(filtrarTexto(xs, 'perez', (x) => x)).toEqual(['Juan Pérez · Peón'])
  })
  it('cada palabra debe estar, en cualquier orden', () => {
    expect(filtrarTexto(xs, 'tipo cemento', (x) => x)).toEqual(['CEMENTO PORTLAND TIPO I'])
    expect(filtrarTexto(xs, 'rosa peon', (x) => x)).toEqual([])
  })
  it('sin búsqueda devuelve todo', () => { expect(filtrarTexto(xs, '   ', (x) => x)).toHaveLength(3) })
})
