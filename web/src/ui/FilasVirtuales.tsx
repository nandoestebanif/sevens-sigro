/* Cuerpo de tabla virtualizado: con miles de filas (el catálogo tiene 2 243) solo existen en el DOM las
   que se ven más un margen; el resto se reemplaza por dos filas espaciadoras que conservan el alto real,
   así la barra de desplazamiento, el encabezado y la tabla se ven igual que antes.

   Se desplaza la VENTANA (no una caja con su propia barra): la página sigue comportándose como las demás.
   Cada fila se mide al pintarse (data-index + ref), así una fila que salta a dos líneas no descuadra. */
import { memo, useLayoutEffect, useRef, useState, type ReactElement, type Ref } from 'react'
import { useWindowVirtualizer } from '@tanstack/react-virtual'

export type MedirFila = { ref: Ref<HTMLTableRowElement>; 'data-index': number }

type PropsFila = {
  item: unknown
  indice: number
  fila: (item: never, indice: number, medir: MedirFila) => ReactElement
  medirRef: Ref<HTMLTableRowElement>
}
/* Al desplazar solo cambia QUÉ filas se ven: las que ya estaban no se vuelven a pintar (memo). */
const Fila = memo(({ item, indice, fila, medirRef }: PropsFila) => fila(item as never, indice, { ref: medirRef, 'data-index': indice }))

export function FilasVirtuales<T>({ items, alto = 49, columnas, fila, margen = 12 }: {
  items: T[]
  /** alto estimado de una fila en px (se corrige al medir) */
  alto?: number
  /** número de columnas, para que la fila espaciadora ocupe toda la tabla */
  columnas: number
  /** pinta UNA fila: debe devolver un <tr> que reciba `medir` ({...medir}) */
  fila: (item: T, indice: number, medir: MedirFila) => ReactElement
  /** filas de más arriba y abajo de lo visible */
  margen?: number
}) {
  const cuerpo = useRef<HTMLTableSectionElement>(null)
  const [desfase, setDesfase] = useState(0)
  // dónde empieza la tabla dentro de la página: lo que hay encima no es parte de la lista
  useLayoutEffect(() => {
    const medir = () => {
      if (cuerpo.current) setDesfase(cuerpo.current.getBoundingClientRect().top + window.scrollY)
    }
    medir()
    window.addEventListener('resize', medir)
    return () => window.removeEventListener('resize', medir)
  }, [])

  const v = useWindowVirtualizer({
    count: items.length,
    estimateSize: () => alto,
    overscan: margen,
    scrollMargin: desfase,
    // sin flushSync: React agrupa los cambios del desplazamiento en un solo pintado por cuadro
    useFlushSync: false,
  })
  const visibles = v.getVirtualItems()
  const arriba = visibles.length ? visibles[0].start - v.options.scrollMargin : 0
  const abajo = visibles.length ? v.getTotalSize() - (visibles[visibles.length - 1].end - v.options.scrollMargin) : 0

  return (
    <tbody ref={cuerpo}>
      {arriba > 0 && <tr aria-hidden="true"><td colSpan={columnas} style={{ height: arriba, padding: 0, border: 0 }} /></tr>}
      {visibles.map((x) => (
        <Fila key={x.key} item={items[x.index]} indice={x.index} fila={fila as PropsFila['fila']} medirRef={v.measureElement} />
      ))}
      {abajo > 0 && <tr aria-hidden="true"><td colSpan={columnas} style={{ height: abajo, padding: 0, border: 0 }} /></tr>}
    </tbody>
  )
}
