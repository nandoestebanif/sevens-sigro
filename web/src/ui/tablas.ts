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

/** Para buscar sin que importen tildes ni mayúsculas: «Peón» encuentra «peon». */
export const normalizar = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()

/** Filtra por texto: cada palabra buscada debe aparecer en el texto de la fila (en cualquier orden). */
export function filtrarTexto<T>(lista: readonly T[], busqueda: string, texto: (item: T) => string): T[] {
  const palabras = normalizar(busqueda).split(/\s+/).filter(Boolean)
  if (!palabras.length) return [...lista]
  return lista.filter((x) => { const t = normalizar(texto(x)); return palabras.every((p) => t.includes(p)) })
}

/** Filas por página que se ofrecen en las listas; la primera es la de entrada (pantallas sin scroll largo). */
export const TAMANOS = [10, 25, 50, 100]

/**
 * Día en Lima (AAAA-MM-DD) de una fecha de la API. «2026-11-27» queda igual; un instante con hora se pasa a la hora
 * de Lima (las 23:30 del 26 en Lima son el 27 en UTC: cuenta como 26). Sin zona, la API manda UTC. null si no es fecha.
 */
export function diaLima(v: string | null | undefined): string | null {
  if (!v) return null
  const t = v.trim()
  if (/^\d{4}-\d{2}-\d{2}$/.test(t)) return t
  const ms = Date.parse(/[zZ]|[+-]\d\d:?\d\d$/.test(t) ? t : `${t}Z`)
  if (Number.isNaN(ms)) return null
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(ms))
}

/**
 * Filtra por un rango de días (los dos extremos incluidos, AAAA-MM-DD, vacíos = sin límite). Con un rango puesto, la fila
 * que no tiene esa fecha (p. ej. un celular que nunca se autorizó, al filtrar por autorización) no entra.
 */
export function filtrarFechas<T>(lista: readonly T[], valor: (item: T) => string | null | undefined, desde?: string, hasta?: string): T[] {
  if (!desde && !hasta) return [...lista]
  const [a, b] = desde && hasta && desde > hasta ? [hasta, desde] : [desde, hasta] // al revés: se entiende igual
  return lista.filter((x) => {
    const d = diaLima(valor(x))
    return d !== null && (!a || d >= a) && (!b || d <= b)
  })
}
