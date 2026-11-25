/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · elegir una obra de la lista (al entrar y con «Cambiar de obra»)

   Una persona puede estar en muchas obras: la lista NO crece hacia abajo sin fin.
   - con más de POR_PAGINA obras aparece el buscador (sin tildes ni mayúsculas);
   - se muestran POR_PAGINA a la vez, con «‹ 1–5 de 18 ›»;
   - la obra activa va marcada y primero; cada una conserva el color de su ticket.
   ───────────────────────────────────────────────────────────────────────── */
import { useMemo, useState } from 'react'
import { Form, InputGroup } from 'react-bootstrap'
import { DibujoObra } from './Ilustraciones'
import { filtrarTexto, paginar } from './tablas'

export interface OpcionObra { id: string; nombre: string; rol?: string }

const POR_PAGINA = 5
const COLORES = ['#2ee6c5', '#fad85d', '#f2a6ff', '#ffb3c1', '#ffc58f']

export function SelectorObra({ obras, actual, ocupada, alElegir, autoFoco, etiquetaId }: {
  obras: OpcionObra[]
  /** la obra en la que ya está (va primero y marcada) */
  actual?: string | null
  /** la que se está abriendo («Abriendo…») */
  ocupada?: string | null
  alElegir: (o: OpcionObra) => void
  autoFoco?: boolean
  /** id del título que nombra la lista (aria-labelledby) */
  etiquetaId?: string
}) {
  const [q, setQ] = useState('')
  const [pagina, setPagina] = useState(1)
  // el color sigue a la obra (por su posición en la lista completa), no a la página
  const color = useMemo(() => new Map(obras.map((o, i) => [o.id, COLORES[i % COLORES.length]])), [obras])
  const ordenadas = useMemo(() => {
    const xs = filtrarTexto(obras, q, (o) => `${o.nombre} ${o.rol ?? ''}`)
    return actual ? [...xs.filter((o) => o.id === actual), ...xs.filter((o) => o.id !== actual)] : xs
  }, [obras, q, actual])
  const p = paginar(ordenadas, pagina, POR_PAGINA)

  return (
    <div className="d-flex flex-column gap-2 sigro-selector-obra">
      {obras.length > POR_PAGINA && (
        <InputGroup>
          <InputGroup.Text aria-hidden>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" /></svg>
          </InputGroup.Text>
          <Form.Control value={q} autoFocus={autoFoco} placeholder={`Buscar entre tus ${obras.length} obras…`} aria-label="Buscar obra"
            onChange={(e) => { setQ(e.target.value); setPagina(1) }} />
        </InputGroup>
      )}
      {p.total === 0 ? (
        <p className="small text-body-secondary mb-0 py-3 text-center">Ninguna obra coincide con «{q}».</p>
      ) : (
        <div role="list" aria-label={etiquetaId ? undefined : 'Obras'} aria-labelledby={etiquetaId} className="d-flex flex-column gap-2">
          {p.items.map((o) => (
            <div role="listitem" key={o.id} className="sigro-obra-opcion" style={{ ['--sigro-ticket' as string]: color.get(o.id) }}>
              <button type="button" onClick={() => alElegir(o)} disabled={!!ocupada} aria-busy={ocupada === o.id}
                aria-current={o.id === actual ? 'true' : undefined}>
                <span className="icono" aria-hidden="true"><DibujoObra cual="grua" ancho={40} /></span>
                <span className="min-w-0 flex-grow-1 text-start">
                  <span className="d-block fw-semibold text-truncate" title={o.nombre}>{o.nombre}</span>
                  <small className="text-body-secondary">
                    {ocupada === o.id ? 'Abriendo…' : o.id === actual ? `${o.rol ? `${o.rol} · ` : ''}obra actual` : o.rol}
                  </small>
                </span>
                <span className="flecha" aria-hidden="true">{o.id === actual ? '✓' : '→'}</span>
              </button>
            </div>
          ))}
        </div>
      )}
      {p.paginas > 1 && (
        <nav aria-label="Páginas de obras" className="d-flex justify-content-between align-items-center pt-1">
          <button type="button" className="btn btn-sm btn-outline-secondary" disabled={p.pagina === 1} onClick={() => setPagina(p.pagina - 1)} aria-label="Obras anteriores">‹ Anteriores</button>
          <span className="small text-body-secondary n">{p.desde}–{p.hasta} de {p.total}</span>
          <button type="button" className="btn btn-sm btn-outline-secondary" disabled={p.pagina === p.paginas} onClick={() => setPagina(p.pagina + 1)} aria-label="Obras siguientes">Siguientes ›</button>
        </nav>
      )}
    </div>
  )
}
