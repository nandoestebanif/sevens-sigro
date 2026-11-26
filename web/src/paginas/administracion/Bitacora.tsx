/* SIGRO · bitácora de la obra: quién hizo qué y cuándo (cada cambio y cada acceso rechazado).
   El servidor no guarda cuerpos, tokens, claves, IP ni datos personales: aquí solo hay ruta, usuario y resultado. */
import { useMemo, useState } from 'react'
import { ErrorApi } from '../../api/error'
import { bitacora } from '../../api/pinApi'
import type { FilaBitacora } from '../../api/pin'
import { Chip } from '../../ui/base'
import { EnVivo, useActualizacion } from '../../ui/Actualizacion'

/** «/api/v1/obras/7/almacen/salidas» → «Almacén · salidas». Solo para leerla mejor; la ruta exacta va debajo. */
export function queHizo(ruta: string): string {
  const r = ruta.replace(/^\/api\/v1/, '').replace(/^\/obras\/\d+/, '').replace(/\/\d+/g, '').split('/').filter(Boolean)
  if (!r.length) return 'Obra'
  return r.map((p, i) => (i === 0 ? p[0].toUpperCase() + p.slice(1) : p)).join(' · ').replace(/-/g, ' ')
}

export function tonoEstado(e: number): 'bien' | 'aviso' | 'grave' {
  return e < 400 ? 'bien' : e === 401 || e === 403 ? 'grave' : 'aviso'
}

export function Bitacora() {
  const [filas, setFilas] = useState<FilaBitacora[] | null>(null)
  const [soloRechazos, setSoloRechazos] = useState(false)
  const [error, setError] = useState('')
  const vivo = useActualizacion(() => bitacora(soloRechazos).then((f) => { setFilas(f); setError('') })
    .catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo consultar la bitácora.')))
  const rechazos = useMemo(() => (filas ?? []).filter((f) => f.estado === 401 || f.estado === 403).length, [filas])

  return (
    <div className="d-flex flex-column gap-3">
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
        <p className="small text-body-secondary mb-0">Cada cambio (crear, registrar, anular…) y cada acceso rechazado en esta obra y en lo general (usuarios, proveedores, catálogo, inicios de sesión), lo más reciente primero. No se edita ni se borra; no guarda contraseñas, PIN, tokens ni lo que se escribió en los formularios.</p>
        <div className="d-flex align-items-center gap-3">
          <div className="form-check form-switch mb-0">
            <input className="form-check-input" type="checkbox" id="bit-rech" checked={soloRechazos}
              onChange={(e) => { setSoloRechazos(e.target.checked); setTimeout(vivo.actualizar) }} />
            <label className="form-check-label small" htmlFor="bit-rech">Solo accesos rechazados{rechazos ? ` (${rechazos})` : ''}</label>
          </div>
          <EnVivo {...vivo} />
        </div>
      </div>
      {error && <div role="alert" className="small text-danger">{error}</div>}
      <div className="table-responsive">
        <table className="table table-sm align-middle mb-0">
          <thead><tr><th>Cuándo</th><th>Quién</th><th>Qué</th><th>Resultado</th><th className="text-end">ms</th></tr></thead>
          <tbody>
            {(filas ?? []).map((f) => (
              <tr key={f.id}>
                <td className="small n text-nowrap">{new Date(f.momento).toLocaleString('es-PE', { timeZone: 'America/Lima' })}</td>
                <td className="small">{f.usuario ?? <span className="text-body-secondary">sin sesión</span>}</td>
                <td className="small"><strong>{queHizo(f.ruta)}</strong><div className="text-body-secondary n" style={{ fontSize: 11 }}>{f.metodo} {f.ruta}</div></td>
                <td><Chip tono={tonoEstado(f.estado)}>{f.estado}</Chip></td>
                <td className="small n text-end">{f.duracionMs}</td>
              </tr>
            ))}
            {filas?.length === 0 && <tr><td colSpan={5} className="small text-body-secondary">Todavía no hay operaciones en esta obra.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}
