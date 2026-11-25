/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · TablaDatos: la tabla de TODAS las listas de la web

   Una sola pieza para que cada listado se comporte igual:
   - columnas que se ordenan al tocar su título (▲ ▼), con reglas del español;
   - buscador sin tildes ni mayúsculas (cada palabra, en cualquier orden);
   - paginación de Bootstrap (25 / 50 / 100 por página) solo si hace falta;
   - acciones por fila (dar de baja, anular, retirar…), que la pantalla muestra
     según el rol: el servidor igual lo verifica (regla 9);
   - en el celular, cada fila se vuelve una tarjeta con «etiqueta: valor»;
   - vacío y «sin resultados» con su mensaje.
   Las funciones puras (ordenar, paginar, filtrarTexto) están en tablas.ts.
   ───────────────────────────────────────────────────────────────────────── */
import { useMemo, useState, type ReactNode } from 'react'
import { Dropdown, Form, InputGroup } from 'react-bootstrap'
import { Vacio } from './base'
import { Paginador, ThOrden } from './Tabla'
import { alternarOrden, filtrarTexto, ordenar, paginar, type Orden } from './tablas'

export interface Columna<T> {
  clave: string
  titulo: ReactNode
  /** texto para la tarjeta del celular cuando `titulo` no es texto */
  rotulo?: string
  celda: (fila: T) => ReactNode
  /** si está, la columna se ordena con este valor (número o texto; null al final) */
  valor?: (fila: T) => string | number | null
  numerica?: boolean
  /** no se muestra en la tarjeta del celular (dato secundario) */
  soloEscritorio?: boolean
}

export function TablaDatos<T>({
  filas, columnas, clave, etiqueta, buscar, marcadorBusqueda = 'Buscar…', acciones, tituloAcciones = 'Acciones',
  vacio, sinResultados, ordenInicial = null, tamInicial = 25, filtros, claseFila,
}: {
  filas: readonly T[]
  columnas: Columna<T>[]
  clave: (fila: T) => string | number
  etiqueta: string
  /** texto en el que se busca; sin esto no hay buscador */
  buscar?: (fila: T) => string
  marcadorBusqueda?: string
  acciones?: (fila: T) => ReactNode
  tituloAcciones?: string
  vacio: { titulo: string; detalle: string }
  sinResultados?: string
  ordenInicial?: Orden<string> | null
  tamInicial?: number
  /** controles junto al buscador (segmentados, selects) */
  filtros?: ReactNode
  claseFila?: (fila: T) => string | undefined
}) {
  const [q, setQ] = useState('')
  const [orden, setOrden] = useState<Orden<string> | null>(ordenInicial)
  const [pagina, setPagina] = useState(1)
  const [tam, setTam] = useState(tamInicial)

  const valorDe = useMemo(() => Object.fromEntries(columnas.filter((c) => c.valor).map((c) => [c.clave, c.valor!])), [columnas])
  const visibles = useMemo(() => {
    const f = buscar ? filtrarTexto(filas, q, buscar) : [...filas]
    return ordenar(f, orden, (x, k) => valorDe[k]?.(x) ?? null)
  }, [filas, q, buscar, orden, valorDe])
  const p = paginar(visibles, pagina, tam)
  const enOrdenar = (k: string) => { setOrden((o) => alternarOrden(o, k)); setPagina(1) }
  const rotulo = (c: Columna<T>) => c.rotulo ?? (typeof c.titulo === 'string' ? c.titulo : '')

  return (
    <div className="d-flex flex-column gap-3">
      {(buscar || filtros) && filas.length > 0 && (
        <div className="d-flex flex-wrap gap-2 align-items-center">
          {buscar && (
            <InputGroup style={{ flex: '1 1 260px', maxWidth: 420 }}>
              <InputGroup.Text aria-hidden>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" /></svg>
              </InputGroup.Text>
              <Form.Control value={q} placeholder={marcadorBusqueda} aria-label={`Buscar en ${etiqueta}`}
                onChange={(e) => { setQ(e.target.value); setPagina(1) }} />
            </InputGroup>
          )}
          {filtros}
          {q && <span className="small text-body-secondary n" role="status">{visibles.length} de {filas.length}</span>}
        </div>
      )}

      {filas.length === 0 ? (
        <Vacio titulo={vacio.titulo} detalle={vacio.detalle} />
      ) : visibles.length === 0 ? (
        <Vacio titulo="Sin resultados" detalle={sinResultados ?? 'Prueba con otra palabra o quita los filtros.'} />
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="table table-hover align-middle mb-0 tabla-datos" aria-label={etiqueta}>
              <thead>
                <tr>
                  {columnas.map((c) => c.valor
                    ? <ThOrden key={c.clave} clave={c.clave} orden={orden} enOrdenar={enOrdenar} className={c.numerica ? 'n' : undefined}>{c.titulo}</ThOrden>
                    : <th key={c.clave} className={c.numerica ? 'n' : undefined}>{c.titulo}</th>)}
                  {acciones && <th className="text-end">{tituloAcciones}</th>}
                </tr>
              </thead>
              <tbody>
                {p.items.map((f) => (
                  <tr key={clave(f)} className={claseFila?.(f)}>
                    {columnas.map((c) => (
                      <td key={c.clave} data-label={rotulo(c)} className={[c.numerica ? 'n' : '', c.soloEscritorio ? 'solo-escritorio' : '', c === columnas[0] ? 'principal' : ''].join(' ').trim() || undefined}>
                        {c.celda(f)}
                      </td>
                    ))}
                    {acciones && <td className="text-end acciones" data-label="">{acciones(f)}</td>}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {p.total > 25 && <Paginador p={p} enPagina={setPagina} tam={tam} enTam={(n) => { setTam(n); setPagina(1) }} />}
        </>
      )}
    </div>
  )
}

export interface Accion {
  texto: string
  enClic: () => void
  /** las que no se deshacen solas (baja, anular, retirar): van en rojo y al final */
  peligro?: boolean
  deshabilitado?: boolean
}

/**
 * Acciones de una fila. Una sola → un botón. Varias → menú «⋯» (Dropdown de Bootstrap) para que la tabla no se
 * ensanche; las de peligro van al final, separadas y en rojo. Las que el rol no puede ni siquiera llegan aquí.
 */
export function AccionesFila({ acciones, de }: { acciones: (Accion | false | null | undefined)[]; de: string }) {
  const xs = acciones.filter(Boolean) as Accion[]
  if (!xs.length) return null
  if (xs.length === 1) {
    const a = xs[0]
    return (
      <button type="button" disabled={a.deshabilitado} onClick={a.enClic} aria-label={`${a.texto}: ${de}`}
        className={`btn btn-sm text-nowrap ${a.peligro ? 'btn-outline-danger' : 'btn-outline-secondary'}`}>{a.texto}</button>
    )
  }
  const normales = xs.filter((a) => !a.peligro), peligro = xs.filter((a) => a.peligro)
  return (
    <Dropdown align="end">
      <Dropdown.Toggle variant="outline-secondary" size="sm" className="sin-flecha" aria-label={`Acciones de ${de}`}>⋯</Dropdown.Toggle>
      <Dropdown.Menu popperConfig={{ strategy: 'fixed' }} renderOnMount>
        {normales.map((a) => <Dropdown.Item key={a.texto} as="button" disabled={a.deshabilitado} onClick={a.enClic}>{a.texto}</Dropdown.Item>)}
        {normales.length > 0 && peligro.length > 0 && <Dropdown.Divider />}
        {peligro.map((a) => <Dropdown.Item key={a.texto} as="button" className="text-danger" disabled={a.deshabilitado} onClick={a.enClic}>{a.texto}</Dropdown.Item>)}
      </Dropdown.Menu>
    </Dropdown>
  )
}
