/* SIGRO · bitácora de la obra: quién hizo qué y cuándo (cada cambio y cada acceso rechazado).
   El servidor no guarda cuerpos, tokens, claves, IP ni datos personales: aquí solo hay ruta, usuario y resultado.
   Filtros (fecha, quién, qué, tipo) en el servidor; un clic en la fila abre el detalle. */
import { useMemo, useRef, useState } from 'react'
import { Button, Form } from 'react-bootstrap'
import { ErrorApi } from '../../api/error'
import { bitacora } from '../../api/pinApi'
import type { FilaBitacora, FiltroBitacora } from '../../api/pin'
import { Chip } from '../../ui/base'
import { PanelDetalle } from '../../ui/PanelDetalle'
import { EnVivo, useActualizacion } from '../../ui/Actualizacion'
import { Paginador } from '../../ui/Tabla'
import { paginar } from '../../ui/tablas'

/** «/api/v1/obras/7/almacen/salidas» → «Almacen · salidas». Solo para leerla mejor; la ruta exacta va debajo. */
export function queHizo(ruta: string): string {
  const r = ruta.replace(/^\/api\/v1/, '').replace(/^\/obras\/\d+/, '').replace(/\/\d+/g, '').split('/').filter(Boolean)
  if (!r.length) return 'Obra'
  return r.map((p, i) => (i === 0 ? p[0].toUpperCase() + p.slice(1) : p)).join(' · ').replace(/-/g, ' ')
}

export function tonoEstado(e: number): 'bien' | 'aviso' | 'grave' {
  return e < 400 ? 'bien' : e === 401 || e === 403 ? 'grave' : 'aviso'
}

/** Qué significa el resultado, en palabras de la obra. */
export function significado(e: number): string {
  if (e === 200) return 'Hecho.'
  if (e === 201) return 'Hecho: se creó un registro nuevo.'
  if (e < 300) return 'Hecho.'
  if (e === 401) return 'Rechazado: no había sesión válida (no inició sesión o venció).'
  if (e === 403) return 'Rechazado: su rol no tiene permiso para esto en esta obra, o faltó el PIN del administrador.'
  if (e === 404) return 'No se encontró lo que pidió.'
  if (e === 409) return 'No se aplicó: choca con lo que ya existe (duplicado, periodo cerrado, ya anulado…).'
  if (e === 422) return 'No se aplicó: algún dato no cumple una regla (cantidad, fecha, formato…).'
  if (e === 429) return 'Bloqueado por demasiados intentos; se libera solo después de unos minutos.'
  if (e >= 500) return 'Falló en el servidor; no se guardó nada.'
  return 'No se aplicó.'
}

/** Ruta → ¿de la obra o general? */
export function ambito(ruta: string): string {
  return /^\/api\/v1\/obras\/\d+/.test(ruta) ? 'De esta obra' : 'General (usuarios, catálogo, proveedores, sesión)'
}

const TIPOS: [NonNullable<FiltroBitacora['solo']>, string][] = [['', 'Todo'], ['cambios', 'Solo cambios hechos'], ['rechazos', 'Solo accesos rechazados'], ['errores', 'Todo lo que no se aplicó']]
const fecha = (m: string) => new Date(m).toLocaleString('es-PE', { timeZone: 'America/Lima' })

export function Bitacora() {
  const [filas, setFilas] = useState<FilaBitacora[] | null>(null)
  const [f, setF] = useState<FiltroBitacora>({ solo: '' })
  const [error, setError] = useState('')
  const [abierta, setAbierta] = useState<FilaBitacora | null>(null)
  // paginación (27/11): hasta 500 operaciones de golpe eran una lista interminable
  const [pagina, setPagina] = useState(1)
  const [tam, setTam] = useState(25)
  const personas = useRef(new Map<number, string>())
  const filtro = useRef(f)
  filtro.current = f
  const vivo = useActualizacion(() => bitacora(filtro.current).then((r) => {
    r.forEach((x) => { if (x.usuarioId && x.usuario) personas.current.set(x.usuarioId, x.usuario) })
    setFilas(r); setError('')
  }).catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo consultar la bitácora.')))
  const cambiar = (n: Partial<FiltroBitacora>) => { setF((v) => { const x = { ...v, ...n }; filtro.current = x; return x }); setPagina(1); setTimeout(vivo.actualizar) }
  const hayFiltro = Boolean(f.desde || f.hasta || f.usuarioId || f.q || f.solo)
  const resumen = useMemo(() => {
    const l = filas ?? []
    return { total: l.length, rechazos: l.filter((x) => x.estado === 401 || x.estado === 403).length, personas: new Set(l.map((x) => x.usuarioId)).size }
  }, [filas])
  const p = paginar(filas ?? [], pagina, tam)
  const quienes = [...personas.current.entries()].sort((a, b) => a[1].localeCompare(b[1]))

  return (
    <div className="d-flex flex-column gap-3">
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
        <p className="small text-body-secondary mb-0">Cada cambio (crear, registrar, anular…) y cada acceso rechazado en esta obra y en lo general (usuarios, proveedores, catálogo, inicios de sesión), lo más reciente primero. No se edita ni se borra; no guarda contraseñas, PIN, tokens ni lo que se escribió en los formularios.</p>
        <EnVivo {...vivo} />
      </div>
      <Form className="d-flex flex-wrap gap-2 align-items-end" onSubmit={(e) => e.preventDefault()} aria-label="Filtros de la bitácora">
        <Form.Group controlId="bit-desde"><Form.Label className="small mb-1">Desde</Form.Label>
          <Form.Control size="sm" type="date" value={f.desde ?? ''} max={f.hasta || undefined} onChange={(e) => cambiar({ desde: e.target.value || undefined })} /></Form.Group>
        <Form.Group controlId="bit-hasta"><Form.Label className="small mb-1">Hasta</Form.Label>
          <Form.Control size="sm" type="date" value={f.hasta ?? ''} min={f.desde || undefined} onChange={(e) => cambiar({ hasta: e.target.value || undefined })} /></Form.Group>
        <Form.Group controlId="bit-quien"><Form.Label className="small mb-1">Quién</Form.Label>
          <Form.Select size="sm" value={f.usuarioId ?? ''} onChange={(e) => cambiar({ usuarioId: e.target.value ? Number(e.target.value) : undefined })}>
            <option value="">Todos</option>
            {quienes.map(([id, n]) => <option key={id} value={id}>{n}</option>)}
          </Form.Select></Form.Group>
        <Form.Group controlId="bit-que" style={{ minWidth: 180 }}><Form.Label className="small mb-1">Qué</Form.Label>
          <Form.Control size="sm" type="search" placeholder="salidas, usuarios, pin…" maxLength={60} value={f.q ?? ''}
            onChange={(e) => setF((v) => ({ ...v, q: e.target.value }))} onKeyDown={(e) => { if (e.key === 'Enter') cambiar({}) }} onBlur={() => cambiar({})} /></Form.Group>
        <Form.Group controlId="bit-tipo"><Form.Label className="small mb-1">Tipo</Form.Label>
          <Form.Select size="sm" value={f.solo ?? ''} onChange={(e) => cambiar({ solo: e.target.value as FiltroBitacora['solo'] })}>
            {TIPOS.map(([v, n]) => <option key={v} value={v}>{n}</option>)}
          </Form.Select></Form.Group>
        {hayFiltro && <Button size="sm" variant="link" onClick={() => cambiar({ desde: undefined, hasta: undefined, usuarioId: undefined, q: '', solo: '' })}>Quitar filtros</Button>}
      </Form>
      {filas && <div className="small text-body-secondary" aria-live="polite">{resumen.total}{resumen.total === 500 ? '+' : ''} operaciones · {resumen.rechazos} rechazadas · {resumen.personas} {resumen.personas === 1 ? 'persona' : 'personas'}{resumen.total === 500 ? ' · se muestran las 500 más recientes: acota con los filtros' : ''}</div>}
      {error && <div role="alert" className="small text-danger">{error}</div>}
      <div className="table-responsive">
        <table className="table table-sm table-hover align-middle mb-0">
          <thead><tr><th>Cuándo</th><th>Quién</th><th>Qué</th><th>Resultado</th><th className="text-end">ms</th></tr></thead>
          <tbody>
            {p.items.map((x) => (
              <tr key={x.id} role="button" tabIndex={0} onClick={() => setAbierta(x)} onKeyDown={(e) => { if (e.key === 'Enter') setAbierta(x) }} aria-label={`Ver detalle de ${queHizo(x.ruta)}`}>
                <td className="small n text-nowrap">{fecha(x.momento)}</td>
                <td className="small">{x.usuario ?? <span className="text-body-secondary">sin sesión</span>}</td>
                <td className="small"><strong>{queHizo(x.ruta)}</strong><div className="text-body-secondary n" style={{ fontSize: 11 }}>{x.metodo} {x.ruta}</div></td>
                <td><Chip tono={tonoEstado(x.estado)}>{x.estado}</Chip></td>
                <td className="small n text-end">{x.duracionMs}</td>
              </tr>
            ))}
            {filas?.length === 0 && <tr><td colSpan={5} className="small text-body-secondary">{hayFiltro ? 'Nada con estos filtros.' : 'Todavía no hay operaciones en esta obra.'}</td></tr>}
          </tbody>
        </table>
      </div>
      {p.total > 0 && <Paginador p={p} enPagina={setPagina} tam={tam} enTam={(n) => { setTam(n); setPagina(1) }} />}
      {abierta && (
        <PanelDetalle titulo={queHizo(abierta.ruta)} subtitulo={fecha(abierta.momento)} enCerrar={() => setAbierta(null)}
          chips={<><Chip tono={tonoEstado(abierta.estado)}>{abierta.estado}</Chip><Chip tono="neutro">{abierta.metodo === 'POST' ? 'cambio' : 'consulta'}</Chip></>}
          secciones={[
            { titulo: 'Qué pasó', datos: [['Resultado', significado(abierta.estado)], ['Operación', `${abierta.metodo} ${abierta.ruta}`], ['Ámbito', ambito(abierta.ruta)], ['Duración', `${abierta.duracionMs} ms`]] },
            { titulo: 'Quién', datos: [['Persona', abierta.usuario ?? 'Sin sesión (p. ej. un inicio de sesión)'], ['Id de usuario', abierta.usuarioId ?? '—'], ['Registro', `#${abierta.id}`]] },
          ]}
          pie={abierta.usuarioId ? <Button size="sm" variant="outline-secondary" onClick={() => { cambiar({ usuarioId: abierta.usuarioId ?? undefined }); setAbierta(null) }}>Ver todo lo de {abierta.usuario}</Button> : undefined}>
          <p className="small text-body-secondary mb-0">La bitácora guarda solo esto a propósito: sin contraseñas, PIN, tokens, lo escrito en formularios ni IP. El detalle del registro (la salida, la compra…) está en su módulo.</p>
        </PanelDetalle>
      )}
    </div>
  )
}
