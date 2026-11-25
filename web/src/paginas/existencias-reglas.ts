/* SIGRO · reglas puras de «Materiales en almacén» y «Herramientas y equipos» (se prueban con Vitest).
   El saldo, el punto de reposición y el stock de seguridad los calcula el servidor (regla 2); aquí solo se
   clasifica lo que él devolvió y se resume para los indicadores. */

export type EstadoExistencia = 'sin_stock' | 'bajo_seguridad' | 'bajo_punto' | 'normal'
export type TipoInsumo = 'material' | 'equipo' | 'mano_obra' | 'subcontrato' | 'sin_dato'

export interface Existencia {
  insumoId: number
  codigo: string
  descripcion: string
  unidad: string
  tipo: TipoInsumo
  cantidad: number
  almacenes: { almacen: string; cantidad: number }[]
  unidadCompra: string | null
  cantidadCompra: number | null
  puntoReposicion: number | null
  stockSeguridad: number | null
  costoPromedio: number | null
  valor: number
  ultimoMovimiento: string
  estado: EstadoExistencia
}

/** El servidor dice si está bajo el punto o bajo el stock de seguridad; sin saldo no hay nada que comparar. */
export function estadoDe(cantidad: number, bajoSeguridad: boolean | null, bajoPunto: boolean | null): EstadoExistencia {
  if (cantidad <= 0) return 'sin_stock'
  if (bajoSeguridad === true) return 'bajo_seguridad'
  if (bajoPunto === true) return 'bajo_punto'
  return 'normal'
}

export const TEXTO_ESTADO: Record<EstadoExistencia, string> = {
  sin_stock: 'Sin stock',
  bajo_seguridad: 'Bajo el stock de seguridad',
  bajo_punto: 'Por reponer',
  normal: 'Con stock',
}

/** Lo más urgente primero: sin stock, bajo seguridad, por reponer y luego el resto; dentro, por código. */
const PESO: Record<EstadoExistencia, number> = { sin_stock: 0, bajo_seguridad: 1, bajo_punto: 2, normal: 3 }
export function ordenarPorUrgencia(xs: readonly Existencia[]): Existencia[] {
  return [...xs].sort((a, b) => PESO[a.estado] - PESO[b.estado] || a.codigo.localeCompare(b.codigo, 'es'))
}

export type FiltroEstado = 'todos' | 'reponer' | 'sin_stock'
export function filtrar(xs: readonly Existencia[], f: { estado: FiltroEstado; almacen: string; soloEquipos: boolean }): Existencia[] {
  // Materiales y herramientas/equipos son vistas separadas: un taladro no se mezcla con el cemento
  return xs.filter((x) => (f.soloEquipos ? x.tipo === 'equipo' : x.tipo !== 'equipo')
    && (f.almacen === '' || x.almacenes.some((a) => a.almacen === f.almacen))
    && (f.estado === 'todos'
      || (f.estado === 'sin_stock' ? x.estado === 'sin_stock' : x.estado === 'bajo_punto' || x.estado === 'bajo_seguridad')))
}

export function resumir(xs: readonly Existencia[]) {
  return {
    conStock: xs.filter((x) => x.cantidad > 0).length,
    porReponer: xs.filter((x) => x.estado === 'bajo_punto' || x.estado === 'bajo_seguridad').length,
    sinStock: xs.filter((x) => x.estado === 'sin_stock').length,
    valor: Math.round(xs.reduce((s, x) => s + x.valor, 0) * 100) / 100,
    ultimo: xs.reduce<string | null>((m, x) => (!m || x.ultimoMovimiento > m ? x.ultimoMovimiento : m), null),
  }
}

/** «Central 12 · Frente 2»: dónde está, en una línea. */
export function dondeEsta(x: Existencia, fmt: (n: number) => string): string {
  return x.almacenes.filter((a) => a.cantidad !== 0).map((a) => `${a.almacen} ${fmt(a.cantidad)}`).join(' · ') || '—'
}
