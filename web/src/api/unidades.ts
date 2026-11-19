/* SIGRO · en qué unidad se escribe una cantidad de un insumo (HU-17, HU-25).

   Los parámetros de reposición van en la unidad de COMPRA. El factor de conversión
   (unidades del S10 por unidad de compra) vale 1 por defecto en el catálogo: si la
   unidad de compra y la del S10 son distintas y el factor sigue en 1, falta
   declararlo, y la cantidad en realidad está en la unidad del S10.

   La API no manda el factor en /saldo-por-comprar (pedido a Adiel), pero sí el saldo
   en las dos unidades: si son iguales con unidades distintas, el factor es 1. */

/** true: falta el factor · false: hay factor (o no hace falta) · null: no se puede saber (saldo 0). */
export function faltaFactor(unidadS10: string, unidadCompra: string, saldoS10: number, saldoEnUnidadCompra: number): boolean | null {
  if (unidadS10.trim().toLowerCase() === unidadCompra.trim().toLowerCase()) return false
  if (!saldoS10) return null
  return Math.abs(saldoS10 - saldoEnUnidadCompra) < 1e-9
}

/** La unidad que se muestra junto al campo y, si falta el factor, el aviso. */
export function unidadParaMostrar(i: { unidadS10?: string; unidadCompra: string; faltaFactor?: boolean | null } | null | undefined):
  { unidad: string; aviso: string | null } {
  if (!i) return { unidad: '', aviso: null }
  if (i.faltaFactor === true && i.unidadS10) {
    return { unidad: i.unidadS10, aviso: `Falta factor de conversión: se usa la unidad del S10 (${i.unidadS10}), no la de compra (${i.unidadCompra}).` }
  }
  return { unidad: i.unidadCompra, aviso: null }
}

/** D08 · unidades de compra normalizadas para el alta de insumo: la abreviatura es la que usa el S10
    (así el factor de conversión compara igual con igual) y el texto la explica para quien no la conoce. */
export const UNIDADES_COMPRA: { valor: string; texto: string }[] = [
  { valor: 'und', texto: 'und · unidad' },
  { valor: 'kg', texto: 'kg · kilogramo' },
  { valor: 'bls', texto: 'bls · bolsa' },
  { valor: 'm3', texto: 'm³ · metro cúbico' },
  { valor: 'm2', texto: 'm² · metro cuadrado' },
  { valor: 'm', texto: 'm · metro lineal' },
  { valor: 'varilla', texto: 'varilla (9 m)' },
  { valor: 'gal', texto: 'gal · galón' },
  { valor: 'l', texto: 'l · litro' },
  { valor: 'pza', texto: 'pza · pieza' },
  { valor: 'pln', texto: 'pln · plancha' },
  { valor: 'rll', texto: 'rll · rollo' },
  { valor: 'cja', texto: 'cja · caja' },
  { valor: 'pqt', texto: 'pqt · paquete' },
  { valor: 'jgo', texto: 'jgo · juego' },
  { valor: 'par', texto: 'par' },
  { valor: 'mll', texto: 'mll · millar' },
  { valor: 'p2', texto: 'p² · pie cuadrado' },
]
