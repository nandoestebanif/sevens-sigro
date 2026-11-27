/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · TablaDatos: la tabla de TODAS las listas de la web

   Una sola pieza para que cada listado se comporte igual:
   - columnas que se ordenan al tocar su título (▲ ▼), con reglas del español;
   - buscador sin tildes ni mayúsculas (cada palabra, en cualquier orden);
   - paginación de Bootstrap (10 / 25 / 50 / 100 por página) solo si hace falta: ninguna lista obliga a
     bajar y bajar para ver lo de abajo (fase 1 del rediseño, 25/11);
   - acciones por fila (dar de baja, anular, retirar…), que la pantalla muestra
     según el rol: el servidor igual lo verifica (regla 9);
   - en el celular, cada fila se vuelve una tarjeta con «etiqueta: valor»;
   - vacío y «sin resultados» con su mensaje;
   - filtro por fechas (27/11): desde/hasta con el calendario de la web, y si la fila tiene varias fechas
     (pedido, autorización, revocación…) se elige cuál; los días son los de Lima;
   - «Ver detalle» en cada fila (27/11): un panel lateral con todo lo de la fila; la lista puede dar el suyo, más
     completo, o apagarlo si ya tiene su propio detalle.
   Las funciones puras (ordenar, paginar, filtrarTexto) están en tablas.ts.
   ───────────────────────────────────────────────────────────────────────── */
import { useMemo, useState, type ReactNode } from 'react'
import { Dropdown, Form, InputGroup } from 'react-bootstrap'
import { Vacio } from './base'
import { Fecha } from './Fecha'
import { PanelDetalle, type SeccionDetalle } from './PanelDetalle'
import { Paginador, ThOrden } from './Tabla'
import { TAMANOS, alternarOrden, filtrarFechas, filtrarTexto, ordenar, paginar, type Orden } from './tablas'

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

/** Una fecha de la fila por la que se puede filtrar (p. ej. «Pedido», «Autorización»). */
export interface CampoFecha<T> {
  clave: string
  titulo: string
  valor: (fila: T) => string | null | undefined
}

/** Lo que muestra el panel «Ver detalle» de una fila. */
export interface DetalleFila {
  titulo: ReactNode
  subtitulo?: ReactNode
  chips?: ReactNode
  secciones: SeccionDetalle[]
  extra?: ReactNode
}

export function TablaDatos<T>({
  filas, columnas, clave, etiqueta, buscar, marcadorBusqueda = 'Buscar…', acciones, tituloAcciones = 'Acciones',
  vacio, sinResultados, ordenInicial = null, tamInicial = 10, filtros, claseFila, fechas, detalle,
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
  /** fechas por las que se puede filtrar; la primera es la que se ofrece al abrir */
  fechas?: CampoFecha<T>[]
  /**
   * «Ver detalle»: sin darlo, el panel muestra todas las columnas de la fila (también las que el celular oculta);
   * una función da un detalle propio; `false` lo apaga (la lista ya tiene su propio «Ver detalle»).
   */
  detalle?: ((fila: T) => DetalleFila) | false
}) {
  const [q, setQ] = useState('')
  const [orden, setOrden] = useState<Orden<string> | null>(ordenInicial)
  const [pagina, setPagina] = useState(1)
  const [tam, setTam] = useState(tamInicial)
  const [campoFecha, setCampoFecha] = useState(fechas?.[0]?.clave ?? '')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [viendo, setViendo] = useState<T | null>(null)
  const fecha = fechas?.find((f) => f.clave === campoFecha) ?? fechas?.[0]
  const conFechas = Boolean(fecha && (desde || hasta))

  const valorDe = useMemo(() => Object.fromEntries(columnas.filter((c) => c.valor).map((c) => [c.clave, c.valor!])), [columnas])
  const visibles = useMemo(() => {
    const t = buscar ? filtrarTexto(filas, q, buscar) : [...filas]
    const f = fecha ? filtrarFechas(t, fecha.valor, desde, hasta) : t
    return ordenar(f, orden, (x, k) => valorDe[k]?.(x) ?? null)
  }, [filas, q, buscar, orden, valorDe, fecha, desde, hasta])
  const p = paginar(visibles, pagina, tam)
  const enOrdenar = (k: string) => { setOrden((o) => alternarOrden(o, k)); setPagina(1) }
  const rotulo = (c: Columna<T>) => c.rotulo ?? (typeof c.titulo === 'string' ? c.titulo : '')
  const conDetalle = detalle !== false
  const detalleDe = (f: T): DetalleFila => {
    if (typeof detalle === 'function') return detalle(f)
    const [primera, ...resto] = columnas
    const nombre = primera?.valor?.(f)
    return {
      titulo: nombre != null && nombre !== '' ? String(nombre) : primera ? primera.celda(f) : etiqueta,
      subtitulo: etiqueta,
      secciones: [{ datos: (nombre != null ? resto : columnas).filter((c) => rotulo(c)).map((c) => [rotulo(c), c.celda(f)] as [string, ReactNode]) }],
    }
  }
  const nombreFila = (f: T) => { const v = columnas[0]?.valor?.(f); return v != null ? String(v) : etiqueta }
  const d = viendo ? detalleDe(viendo) : null

  return (
    <div className="d-flex flex-column gap-3">
      {(buscar || filtros || fechas?.length) && filas.length > 0 && (
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
          {fechas && fechas.length > 0 && fecha && (
            <FiltroFechas id={etiqueta} campos={fechas} campo={fecha.clave} desde={desde} hasta={hasta}
              enCambio={(c) => { if (c.campo !== undefined) setCampoFecha(c.campo); if (c.desde !== undefined) setDesde(c.desde); if (c.hasta !== undefined) setHasta(c.hasta); setPagina(1) }} />
          )}
          {(q || conFechas) && <span className="small text-body-secondary n" role="status">{visibles.length} de {filas.length}</span>}
        </div>
      )}

      {filas.length === 0 ? (
        <Vacio titulo={vacio.titulo} detalle={vacio.detalle} />
      ) : visibles.length === 0 ? (
        <Vacio titulo="Sin resultados" detalle={conFechas
          ? `Nada con fecha de ${fecha!.titulo.toLowerCase()} en ese rango. Amplía las fechas o quítalas.`
          : sinResultados ?? 'Prueba con otra palabra o quita los filtros.'} />
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="table table-hover align-middle mb-0 tabla-datos" aria-label={etiqueta}>
              <thead>
                <tr>
                  {columnas.map((c) => c.valor
                    ? <ThOrden key={c.clave} clave={c.clave} orden={orden} enOrdenar={enOrdenar} className={c.numerica ? 'n' : undefined}>{c.titulo}</ThOrden>
                    : <th key={c.clave} className={c.numerica ? 'n' : undefined}>{c.titulo}</th>)}
                  {(acciones || conDetalle) && <th className="text-end">{tituloAcciones}</th>}
                </tr>
              </thead>
              <tbody>
                {p.items.map((f) => (
                  <tr key={clave(f)} className={claseFila?.(f)}>
                    {columnas.map((c) => (
                      <td key={c.clave} data-label={rotulo(c)} className={[c.numerica ? 'n' : '', c.soloEscritorio ? 'solo-escritorio' : '', c === columnas[0] && !c.numerica ? 'principal' : ''].join(' ').trim() || undefined}>
                        {c.celda(f)}
                      </td>
                    ))}
                    {(acciones || conDetalle) && (
                      <td className="text-end acciones" data-label="">
                        <div className="d-inline-flex align-items-center gap-1">
                          {conDetalle && (
                            <button type="button" className="sigro-accion accion-ver" onClick={() => setViendo(f)}
                              aria-label={`Ver detalle: ${nombreFila(f)}`} title="Ver detalle">
                              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                                {TRAZOS.ver.split('|').map((x, i) => <path key={i} d={x} />)}
                              </svg>
                            </button>
                          )}
                          {acciones?.(f)}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {p.total > TAMANOS[0] && <Paginador p={p} enPagina={setPagina} tam={tam} tamanos={TAMANOS} enTam={(n) => { setTam(n); setPagina(1) }} />}
        </>
      )}
      {viendo && d && (
        <PanelDetalle titulo={d.titulo} subtitulo={d.subtitulo} chips={d.chips} secciones={d.secciones} enCerrar={() => setViendo(null)}>
          {d.extra}
        </PanelDetalle>
      )}
    </div>
  )
}

/**
 * Desde / hasta con el calendario de la web (dd/mm/aaaa, días de Lima). Si hay varias fechas, se elige cuál
 * («Fecha de pedido», «Fecha de autorización»…). Lo usan TablaDatos y las tablas propias (kárdex, bitácora).
 */
export function FiltroFechas({ id, campos, campo, desde, hasta, enCambio }: {
  id: string
  campos: { clave: string; titulo: string }[]
  campo?: string
  desde: string
  hasta: string
  enCambio: (c: { campo?: string; desde?: string; hasta?: string }) => void
}) {
  const actual = campos.find((c) => c.clave === campo) ?? campos[0]
  const base = id.replace(/\W+/g, '-')
  return (
    <div className="d-flex flex-wrap align-items-center gap-2 sigro-filtro-fechas" role="group" aria-label="Filtrar por fecha">
      {campos.length > 1 ? (
        <Form.Select size="sm" aria-label="Qué fecha" value={actual?.clave} style={{ width: 'auto' }} onChange={(e) => enCambio({ campo: e.target.value })}>
          {campos.map((f) => <option key={f.clave} value={f.clave}>Fecha de {f.titulo.toLowerCase()}</option>)}
        </Form.Select>
      ) : actual && <span className="small text-body-secondary">{actual.titulo}:</span>}
      <div style={{ width: 188 }}><Fecha id={`${base}-desde`} etiqueta={`Desde (${actual?.titulo ?? 'fecha'})`} valor={desde} max={hasta || undefined} alCambiar={(v) => enCambio({ desde: v })} /></div>
      <span className="small text-body-secondary" aria-hidden>a</span>
      <div style={{ width: 188 }}><Fecha id={`${base}-hasta`} etiqueta={`Hasta (${actual?.titulo ?? 'fecha'})`} valor={hasta} min={desde || undefined} alCambiar={(v) => enCambio({ hasta: v })} /></div>
      {(desde || hasta) && <button type="button" className="btn btn-sm btn-link" onClick={() => enCambio({ desde: '', hasta: '' })}>Quitar fechas</button>}
    </div>
  )
}

export type IconoAccion = 'ver' | 'editar' | 'baja' | 'anular' | 'recibir' | 'historial' | 'clave' | 'autorizar' | 'estrella' | 'factor' | 'archivo'

export interface Accion {
  texto: string
  enClic: () => void
  /** las que no se deshacen solas (baja, anular, retirar): van en rojo y al final */
  peligro?: boolean
  deshabilitado?: boolean
  /** ícono del botón; si no se da, se deduce del texto («Ver…», «Editar…», «Dar de baja»…) */
  icono?: IconoAccion
}

const TRAZOS: Record<IconoAccion, string> = {
  ver: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z|M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
  editar: 'M12 20h9|M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z',
  baja: 'M3 6h18|M8 6V4h8v2|M19 6l-1 14H6L5 6|M10 11v6|M14 11v6',
  anular: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z|M4.9 4.9l14.2 14.2',
  recibir: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4|M7 10l5 5 5-5|M12 15V3',
  historial: 'M3 12a9 9 0 1 0 3-6.7L3 8|M3 3v5h5|M12 7v5l3 3',
  clave: 'M15.5 7.5a3.5 3.5 0 1 1-4.95 4.95 3.5 3.5 0 0 1 4.95-4.95|M10.5 12.5L3 20|M6 17l2 2|M8 15l2 2',
  autorizar: 'M20 6 9 17l-5-5',
  archivo: 'M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9Z|M14 3v6h6|M8 13h8|M8 17h5',
  factor: 'M7 3v18|M3 7l4-4 4 4|M17 21V3|M13 17l4 4 4-4',
  estrella: 'M12 2l3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21l1.2-6.8-5-4.9 6.9-1Z',
}

/** El ícono que corresponde a una acción por su texto, para no tener que declararlo en cada lista. */
export function iconoDe(a: Accion): IconoAccion | null {
  if (a.icono) return a.icono
  const t = a.texto.toLowerCase()
  if (/^ver\b|detalle|kardex/.test(t)) return 'ver'
  if (/editar|corregir|ajustar|contacto/.test(t)) return 'editar'
  if (/anular|revocar/.test(t)) return 'anular'
  if (/baja|retirar|quitar|cerrar/.test(t) || a.peligro) return 'baja'
  if (/recibir|recepci/.test(t)) return 'recibir'
  if (/historial/.test(t)) return 'historial'
  if (/comprobante|archivo|documento/.test(t)) return 'archivo'
  if (/clave|contraseña/.test(t)) return 'clave'
  if (/autorizar/.test(t)) return 'autorizar'
  if (/crítico/.test(t)) return 'estrella'
  return null
}

const MAX_A_LA_VISTA = 3

/**
 * Acciones de una fila, juntas y a la vista: botones redondos con ícono y color (ver en azul, editar en amarillo,
 * dar de baja o anular en rojo), con su nombre como tooltip y para el lector de pantalla. Si son más de tres, o
 * alguna no tiene ícono, lo que sobra va en «⋯». Las que el rol no puede ni siquiera llegan aquí.
 */
export function AccionesFila({ acciones, de }: { acciones: (Accion | false | null | undefined)[]; de: string }) {
  const xs = (acciones.filter(Boolean) as Accion[]).sort((a, b) => Number(!!a.peligro) - Number(!!b.peligro))
  if (!xs.length) return null
  const conIcono = xs.filter((a) => iconoDe(a))
  const vista = conIcono.slice(0, MAX_A_LA_VISTA)
  const resto = xs.filter((a) => !vista.includes(a))
  return (
    <div className="sigro-acciones" role="group" aria-label={`Acciones de ${de}`}>
      {vista.map((a) => {
        const ic = iconoDe(a)!
        return (
          <button key={a.texto} type="button" disabled={a.deshabilitado} onClick={a.enClic}
            className={`sigro-accion accion-${ic}${a.peligro ? ' peligro' : ''}`} aria-label={`${a.texto}: ${de}`} title={a.texto}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              {TRAZOS[ic].split('|').map((d, i) => <path key={i} d={d} />)}
            </svg>
          </button>
        )
      })}
      {resto.length > 0 && (
        <Dropdown align="end">
          <Dropdown.Toggle variant="outline-secondary" size="sm" className="sin-flecha sigro-accion" aria-label={`Más acciones de ${de}`}>⋯</Dropdown.Toggle>
          <Dropdown.Menu popperConfig={{ strategy: 'fixed' }} renderOnMount>
            {resto.map((a) => (
              <Dropdown.Item key={a.texto} as="button" className={a.peligro ? 'text-danger' : undefined} disabled={a.deshabilitado} onClick={a.enClic}>{a.texto}</Dropdown.Item>
            ))}
          </Dropdown.Menu>
        </Dropdown>
      )}
    </div>
  )
}
