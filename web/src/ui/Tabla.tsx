/* Piezas de tabla sobre Bootstrap: encabezado que ordena y paginador (Pagination). */
import type { ReactNode } from 'react'
import { Form, Pagination } from 'react-bootstrap'
import { numerosDePagina, type Orden, type Pagina } from './tablas'

/** <th> que ordena al tocarlo. aria-sort le dice al lector de pantalla cómo está ordenada la columna. */
export function ThOrden<K extends string>({ clave, orden, enOrdenar, children, className }: {
  clave: K; orden: Orden<K> | null; enOrdenar: (k: K) => void; children: ReactNode; className?: string
}) {
  const activa = orden?.clave === clave
  return (
    <th className={className} aria-sort={activa ? (orden!.dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button type="button" className="btn btn-link p-0 fw-semibold text-reset text-decoration-none text-nowrap" onClick={() => enOrdenar(clave)}>
        {children} <span aria-hidden className={activa ? 'text-primary' : 'text-body-tertiary'}>{activa ? (orden!.dir === 'asc' ? '▲' : '▼') : '↕'}</span>
      </button>
    </th>
  )
}

/** Paginador de Bootstrap con «Mostrando 26–50 de 312» y cuántas filas por página. */
export function Paginador({ p, enPagina, tam, enTam, tamanos = [25, 50, 100] }: {
  p: Pagina<unknown>; enPagina: (n: number) => void; tam: number; enTam: (n: number) => void; tamanos?: number[]
}) {
  return (
    <nav aria-label="Páginas" className="d-flex flex-wrap justify-content-between align-items-center gap-2 mt-3">
      <span className="small text-body-secondary n">Mostrando {p.desde}–{p.hasta} de {p.total.toLocaleString('es-PE')}</span>
      <div className="d-flex align-items-center gap-2">
        <Form.Select size="sm" aria-label="Filas por página" value={tam} onChange={(e) => enTam(Number(e.target.value))} style={{ width: 'auto' }}>
          {tamanos.map((t) => <option key={t} value={t}>{t} por página</option>)}
        </Form.Select>
        <Pagination size="sm" className="mb-0">
          <Pagination.Prev disabled={p.pagina === 1} onClick={() => enPagina(p.pagina - 1)} aria-label="Anterior" />
          {numerosDePagina(p.pagina, p.paginas).map((n, i) => n === null
            ? <Pagination.Ellipsis key={`e${i}`} disabled />
            : <Pagination.Item key={n} active={n === p.pagina} onClick={() => enPagina(n)}>{n}</Pagination.Item>)}
          <Pagination.Next disabled={p.pagina === p.paginas} onClick={() => enPagina(p.pagina + 1)} aria-label="Siguiente" />
        </Pagination>
      </div>
    </nav>
  )
}
