/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · elegir la semana de la obra (HU-03)

   Una obra de 3 años tiene 144 semanas: un <select> de 144 opciones obliga a
   bajar y bajar. Aquí (fase 1 del rediseño, 25/11):
   - ‹ y › pasan a la semana anterior o siguiente;
   - «Semana N» abre una cuadrícula de 24 semanas por página, con la actual en
     amarillo, las pasadas en gris y las que vienen con borde;
   - «Ir a la actual» vuelve a la semana en curso.
   ───────────────────────────────────────────────────────────────────────── */
import { useState } from 'react'
import { Dropdown } from 'react-bootstrap'

const POR_PAGINA = 24

export function SelectorSemana({ numero, total, actual, alCambiar }: {
  numero: number
  total: number
  /** semana en curso de la obra (0 si aún no inicia) */
  actual: number
  alCambiar: (n: number) => void
}) {
  const [abierto, setAbierto] = useState(false)
  const [desde, setDesde] = useState(() => Math.floor((numero - 1) / POR_PAGINA) * POR_PAGINA + 1)
  const hasta = Math.min(total, desde + POR_PAGINA - 1)
  const elegir = (n: number) => { alCambiar(n); setAbierto(false) }
  const abrir = (v: boolean) => { if (v) setDesde(Math.floor((numero - 1) / POR_PAGINA) * POR_PAGINA + 1); setAbierto(v) }

  return (
    <div className="d-inline-flex align-items-center gap-1 sigro-semana" role="group" aria-label="Semana a revisar">
      <button type="button" className="btn btn-sm btn-outline-secondary" disabled={numero <= 1} onClick={() => alCambiar(numero - 1)} aria-label="Semana anterior">‹</button>
      <Dropdown show={abierto} onToggle={abrir} align="end">
        <Dropdown.Toggle variant="outline-secondary" size="sm" className="fw-semibold" aria-label={`Semana ${numero} de ${total}; elegir otra`}>
          Semana {numero} <span className="text-body-secondary fw-normal">de {total}</span>
        </Dropdown.Toggle>
        <Dropdown.Menu className="p-3" style={{ width: 300 }} popperConfig={{ strategy: 'fixed' }}>
          <div className="d-flex justify-content-between align-items-center mb-2">
            <button type="button" className="btn btn-sm btn-link px-1" disabled={desde <= 1} onClick={() => setDesde(desde - POR_PAGINA)} aria-label="Semanas anteriores">‹</button>
            <span className="small fw-semibold n">Semanas {desde}–{hasta}</span>
            <button type="button" className="btn btn-sm btn-link px-1" disabled={hasta >= total} onClick={() => setDesde(desde + POR_PAGINA)} aria-label="Semanas siguientes">›</button>
          </div>
          <div className="sigro-semanas" role="listbox" aria-label={`Semanas ${desde} a ${hasta}`}>
            {Array.from({ length: hasta - desde + 1 }, (_, i) => desde + i).map((k) => (
              <button key={k} type="button" role="option" aria-selected={k === numero}
                className={`${k === numero ? 'elegida' : ''} ${k === actual ? 'actual' : k < actual ? 'pasada' : 'futura'}`}
                onClick={() => elegir(k)} title={k === actual ? 'Semana en curso' : k < actual ? 'Semana pasada' : 'Semana por venir'}>
                {k}
              </button>
            ))}
          </div>
          <div className="d-flex justify-content-between align-items-center mt-2 small text-body-secondary">
            <span><span className="punto actual" /> en curso <span className="punto pasada ms-2" /> pasada</span>
            {actual > 0 && actual !== numero && (
              <button type="button" className="btn btn-sm btn-link px-0" onClick={() => elegir(actual)}>Ir a la actual</button>
            )}
          </div>
        </Dropdown.Menu>
      </Dropdown>
      <button type="button" className="btn btn-sm btn-outline-secondary" disabled={numero >= total} onClick={() => alCambiar(numero + 1)} aria-label="Semana siguiente">›</button>
    </div>
  )
}
