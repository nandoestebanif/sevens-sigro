/* Ordenar y paginar listas: funciones puras (sin React), por eso se prueban solas en tablas.test.ts. */

export type Direccion = 'asc' | 'desc'
export interface Orden<K extends string> { clave: K; dir: Direccion }

const comparador = new Intl.Collator('es', { numeric: true, sensitivity: 'base' })

/** Devuelve una copia ordenada (no toca la original). Texto con reglas del español; null siempre al final. */
export function ordenar<T, K extends string>(lista: readonly T[], orden: Orden<K> | null, valor: (item: T, clave: K) => string | number | null): T[] {
  if (!orden) return [...lista]
  const signo = orden.dir === 'asc' ? 1 : -1
  return [...lista].sort((a, b) => {
    const x = valor(a, orden.clave), y = valor(b, orden.clave)
    if (x === null && y === null) return 0
    if (x === null) return 1
    if (y === null) return -1
    const c = typeof x === 'number' && typeof y === 'number' ? x - y : comparador.compare(String(x), String(y))
    return c * signo
  })
}

/** Al tocar un encabezado: misma columna → invierte; otra columna → ascendente. */
export function alternarOrden<K extends string>(actual: Orden<K> | null, clave: K): Orden<K> {
  return actual?.clave === clave ? { clave, dir: actual.dir === 'asc' ? 'desc' : 'asc' } : { clave, dir: 'asc' }
}

export interface Pagina<T> { items: T[]; pagina: number; paginas: number; desde: number; hasta: number; total: number }

/** Corta la página pedida (1 = la primera). Si la página no existe, se ajusta a la última válida. */
export function paginar<T>(lista: readonly T[], pagina: number, tam: number): Pagina<T> {
  const total = lista.length
  const paginas = Math.max(1, Math.ceil(total / tam))
  const p = Math.min(Math.max(1, Math.floor(pagina)), paginas)
  const ini = (p - 1) * tam
  const items = lista.slice(ini, ini + tam)
  return { items, pagina: p, paginas, desde: total ? ini + 1 : 0, hasta: ini + items.length, total }
}

/** Números de página a mostrar: 1 … 4 5 [6] 7 8 … 20  (null = puntos suspensivos). */
export function numerosDePagina(actual: number, paginas: number, alrededor = 2): (number | null)[] {
  const out: (number | null)[] = []
  for (let i = 1; i <= paginas; i++) {
    if (i === 1 || i === paginas || Math.abs(i - actual) <= alrededor) out.push(i)
    else if (out[out.length - 1] !== null) out.push(null)
  }
  return out
}
