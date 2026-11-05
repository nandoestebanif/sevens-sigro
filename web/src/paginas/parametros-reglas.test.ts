/* HU-25 · reglas del formulario de parámetros, unidades (HU-17) y búsqueda del combobox. */
import { describe, expect, it } from 'vitest'
import { faltaFactor, unidadParaMostrar } from '../api/unidades'
import { filtrarOpciones } from '../ui/BuscarOpcion'
import { leerCantidad, validarParametros } from './parametros-reglas'

describe('validar parámetros', () => {
  const ok = { insumo: '0221000000', unidad: 'bls' }
  it('punto ≥ stock de seguridad, con el mensaje bajo el punto', () => {
    expect(validarParametros({ ...ok, seguridad: '120', punto: '80' }).punto).toMatch(/no puede ser menor que el stock de seguridad \(120 bls\)/)
    expect(validarParametros({ ...ok, seguridad: '80', punto: '80' })).toEqual({})
    expect(validarParametros({ ...ok, seguridad: '80', punto: '120,5' })).toEqual({})
  })
  it('números mayores que 0, con hasta 4 decimales', () => {
    expect(validarParametros({ ...ok, seguridad: '0', punto: '10' }).seguridad).toBe('Debe ser mayor que 0.')
    expect(validarParametros({ ...ok, seguridad: '10', punto: 'abc' }).punto).toMatch(/Solo números/)
    expect(validarParametros({ ...ok, seguridad: '1.23456', punto: '2' }).seguridad).toMatch(/4 decimales/)
    expect(validarParametros({ ...ok, seguridad: '-5', punto: '2' }).seguridad).toMatch(/Solo números/)
    expect(validarParametros({ ...ok, seguridad: '', punto: '' })).toEqual({ seguridad: 'Escribe el stock de seguridad.', punto: 'Escribe el punto de reposición.' })
  })
  it('sin insumo elegido', () => {
    expect(validarParametros({ insumo: '', seguridad: '1', punto: '2' }).insumo).toMatch(/Elige el insumo/)
  })
  it('lee la coma decimal de obra', () => {
    expect(leerCantidad('12,5')).toBe(12.5)
    expect(leerCantidad(' 7 ')).toBe(7)
    expect(leerCantidad('1e3')).toBeNaN()
  })
})

describe('unidad junto al campo', () => {
  it('falta factor: unidades distintas y el saldo igual en las dos (factor 1)', () => {
    expect(faltaFactor('kg', 'varilla 9 m', 36357, 36357)).toBe(true)
    expect(faltaFactor('kg', 'varilla 9 m', 36357, 3600.4)).toBe(false)
    expect(faltaFactor('bls', 'BLS', 10, 10)).toBe(false) // misma unidad: no hace falta factor
    expect(faltaFactor('kg', 'varilla 9 m', 0, 0)).toBeNull() // saldo 0: no se puede saber
  })
  it('se muestra la de compra; si falta el factor, la del S10 con aviso', () => {
    expect(unidadParaMostrar({ unidadS10: 'kg', unidadCompra: 'varilla 9 m', faltaFactor: false })).toEqual({ unidad: 'varilla 9 m', aviso: null })
    const f = unidadParaMostrar({ unidadS10: 'kg', unidadCompra: 'varilla 9 m', faltaFactor: true })
    expect(f.unidad).toBe('kg')
    expect(f.aviso).toMatch(/Falta factor de conversión/)
    expect(unidadParaMostrar({ unidadCompra: 'bls' })).toEqual({ unidad: 'bls', aviso: null }) // al editar: la que guardó el servidor
    expect(unidadParaMostrar(null)).toEqual({ unidad: '', aviso: null })
  })
})

describe('combobox: buscar por código o nombre', () => {
  const ops = Array.from({ length: 284 }, (_, i) => ({ valor: String(1000 + i), texto: i === 7 ? 'CEMENTO PORTLAND TIPO I' : `INSUMO ${i}`, detalle: `02${1000 + i}` }))
  it('por nombre, sin tildes ni mayúsculas, y por código', () => {
    expect(filtrarOpciones(ops, 'cemento').visibles.map((o) => o.texto)).toEqual(['CEMENTO PORTLAND TIPO I'])
    expect(filtrarOpciones(ops, 'portland  tipo').total).toBe(1)
    expect(filtrarOpciones(ops, '021007').visibles[0].valor).toBe('1007')
    expect(filtrarOpciones([{ valor: '1', texto: 'Válvula de compuerta' }], 'valvula').total).toBe(1)
  })
  it('muestra a lo más 50 a la vez y dice cuántos hay', () => {
    const r = filtrarOpciones(ops, 'insumo')
    expect(r.visibles).toHaveLength(50)
    expect(r.total).toBe(283)
  })
})
