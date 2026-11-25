/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · selector de fecha propio (fase 3 del rediseño, 25/11)

   El <input type="date"> del navegador muestra «mm/dd/yyyy» en inglés según la
   máquina y su calendario cambia en cada navegador. Este:
   - se escribe como en Perú, dd/mm/aaaa (las barras se ponen solas);
   - el botón abre un calendario en español, lunes primero, con «Hoy»;
   - respeta mín./máx. (p. ej. nada después de hoy en una compra) y lo explica;
   - el valor sigue siendo ISO (AAAA-MM-DD), igual que antes: nada cambia en la API;
   - «hoy» es el de Lima, no el del navegador.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useMemo, useRef, useState } from 'react'
import { Dropdown, Form, InputGroup } from 'react-bootstrap'

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']
const DIAS = ['L', 'M', 'M', 'J', 'V', 'S', 'D']
const DIAS_LARGOS = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo']

export const hoyEnLima = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date())
const dos = (n: number) => String(n).padStart(2, '0')
export const aIso = (a: number, m: number, d: number) => `${a}-${dos(m)}-${dos(d)}`
/** ISO → dd/mm/aaaa */
export const aPeru = (iso: string) => (/^\d{4}-\d{2}-\d{2}$/.test(iso) ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}` : '')

/** dd/mm/aaaa (o d/m/aaaa) → ISO, o null si no es una fecha que exista. */
export function deTexto(t: string): string | null {
  const m = /^\s*(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\s*$/.exec(t)
  if (!m) return null
  const [d, mes, a] = [+m[1], +m[2], +m[3]]
  const f = new Date(Date.UTC(a, mes - 1, d))
  return f.getUTCFullYear() === a && f.getUTCMonth() === mes - 1 && f.getUTCDate() === d ? aIso(a, mes, d) : null
}

/** Mientras se escribe: solo dígitos y las barras van solas («25112026» → «25/11/2026»). */
export function enmascarar(t: string): string {
  const n = t.replace(/\D/g, '').slice(0, 8)
  return [n.slice(0, 2), n.slice(2, 4), n.slice(4)].filter(Boolean).join('/')
}

/** Las 6 semanas (lunes primero) que muestran el mes; null = día de otro mes. */
export function cuadricula(a: number, m: number): (number | null)[] {
  const primero = (new Date(Date.UTC(a, m - 1, 1)).getUTCDay() + 6) % 7
  const dias = new Date(Date.UTC(a, m, 0)).getUTCDate()
  const xs: (number | null)[] = Array(primero).fill(null)
  for (let d = 1; d <= dias; d++) xs.push(d)
  while (xs.length % 7) xs.push(null)
  return xs
}

export function Fecha({ id, valor, alCambiar, min, max, etiqueta, invalido, valido, enSalir, describedBy, deshabilitado }: {
  id: string
  /** AAAA-MM-DD o '' */
  valor: string
  alCambiar: (iso: string) => void
  min?: string
  max?: string
  /** nombre accesible si no hay <label htmlFor={id}> */
  etiqueta?: string
  invalido?: boolean
  valido?: boolean
  enSalir?: () => void
  describedBy?: string
  deshabilitado?: boolean
}) {
  const [texto, setTexto] = useState(aPeru(valor))
  const [abierto, setAbierto] = useState(false)
  const hoy = hoyEnLima()
  const base = valor || (max && max < hoy ? max : min && min > hoy ? min : hoy)
  const [vista, setVista] = useState({ a: +base.slice(0, 4), m: +base.slice(5, 7) })
  const [aviso, setAviso] = useState('')

  // si el valor cambia desde FUERA (p. ej. se limpia el formulario), el texto lo sigue;
  // lo que este mismo campo emite al escribir no pisa lo que la persona va tecleando
  const emitido = useRef(valor)
  useEffect(() => { if (valor !== emitido.current) { emitido.current = valor; setTexto(aPeru(valor)) } }, [valor])
  const emitir = (iso: string) => { emitido.current = iso; alCambiar(iso) }

  const fuera = (iso: string) => (min && iso < min ? `No antes del ${aPeru(min)}.` : max && iso > max ? `No después del ${aPeru(max)}.` : '')
  const elegir = (iso: string) => {
    setAviso(''); setTexto(aPeru(iso)); emitir(iso); setAbierto(false)
  }
  const escribir = (t: string) => {
    const m = enmascarar(t)
    setTexto(m)
    if (m.length === 10) {
      const iso = deTexto(m)
      if (!iso) { setAviso('Esa fecha no existe.'); emitir(''); return }
      const f = fuera(iso)
      setAviso(f)
      emitir(f ? '' : iso)
      if (!f) setVista({ a: +iso.slice(0, 4), m: +iso.slice(5, 7) })
    } else {
      setAviso('')
      if (valor) emitir('')
    }
  }
  const mover = (delta: number) => setVista(({ a, m }) => {
    const t = a * 12 + (m - 1) + delta
    return { a: Math.floor(t / 12), m: (t % 12) + 1 }
  })
  const dias = useMemo(() => cuadricula(vista.a, vista.m), [vista])
  const abrir = (v: boolean) => {
    if (v) { const b = valor || base; setVista({ a: +b.slice(0, 4), m: +b.slice(5, 7) }) }
    setAbierto(v)
  }

  return (
    <div className="sigro-fecha">
      <InputGroup hasValidation>
        <Form.Control id={id} value={texto} placeholder="dd/mm/aaaa" inputMode="numeric" autoComplete="off" maxLength={10}
          aria-label={etiqueta} aria-describedby={[describedBy, aviso ? `${id}-aviso` : ''].filter(Boolean).join(' ') || undefined}
          isInvalid={invalido || !!aviso} isValid={valido && !aviso} disabled={deshabilitado}
          onChange={(e) => escribir(e.target.value)} onBlur={() => enSalir?.()} />
        <Dropdown show={abierto} onToggle={abrir} align="end" autoClose="outside">
          <Dropdown.Toggle variant="outline-secondary" className="sin-flecha sigro-fecha-boton" disabled={deshabilitado}
            aria-label={`Abrir calendario${etiqueta ? ` · ${etiqueta}` : ''}`}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 10h18" />
            </svg>
          </Dropdown.Toggle>
          <Dropdown.Menu className="p-3 sigro-calendario" popperConfig={{ strategy: 'fixed' }}>
            <div className="d-flex justify-content-between align-items-center mb-2">
              <button type="button" className="btn btn-sm btn-link px-2" onClick={() => mover(-1)} aria-label="Mes anterior">‹</button>
              <span className="fw-semibold text-capitalize" aria-live="polite">{MESES[vista.m - 1]} {vista.a}</span>
              <button type="button" className="btn btn-sm btn-link px-2" onClick={() => mover(1)} aria-label="Mes siguiente">›</button>
            </div>
            <div className="sigro-dias" role="grid" aria-label={`${MESES[vista.m - 1]} de ${vista.a}`}>
              {DIAS.map((d, i) => <span key={i} className="cab" role="columnheader" aria-label={DIAS_LARGOS[i]}>{d}</span>)}
              {dias.map((d, i) => {
                if (d === null) return <span key={i} aria-hidden />
                const iso = aIso(vista.a, vista.m, d)
                const no = fuera(iso)
                return (
                  <button key={i} type="button" role="gridcell" disabled={!!no} title={no || undefined}
                    aria-selected={iso === valor} aria-current={iso === hoy ? 'date' : undefined}
                    className={`${iso === valor ? 'elegido' : ''} ${iso === hoy ? 'hoy' : ''}`}
                    aria-label={`${DIAS_LARGOS[(i % 7)]} ${d} de ${MESES[vista.m - 1]} de ${vista.a}`}
                    onClick={() => elegir(iso)}>{d}</button>
                )
              })}
            </div>
            <div className="d-flex justify-content-between mt-2">
              <button type="button" className="btn btn-sm btn-link px-0" disabled={!!fuera(hoy)} onClick={() => elegir(hoy)}>Hoy</button>
              {(min || max) && <span className="small text-body-secondary align-self-center">{min ? `desde ${aPeru(min)}` : ''}{min && max ? ' · ' : ''}{max ? `hasta ${aPeru(max)}` : ''}</span>}
            </div>
          </Dropdown.Menu>
        </Dropdown>
        {aviso && <Form.Control.Feedback type="invalid" id={`${id}-aviso`}>{aviso}</Form.Control.Feedback>}
      </InputGroup>
    </div>
  )
}
