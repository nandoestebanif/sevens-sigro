/* ─────────────────────────────────────────────────────────────────────────────
   Pantalla de arranque: la web no se usa hasta que el servidor esté listo.
   En Render los servicios duermen; sin esto, la primera persona del día
   escribe su clave, espera y recibe un error que no es suyo. Aquí:
   - una capa a pantalla completa con un Spinner y una ProgressBar de Bootstrap;
   - lo de atrás queda inerte (ni clic ni teclado) y con aria-busy;
   - se pregunta a GET /salud/servicios cada 10 s y se deja apenas todo está
     listo; a los 5 s se avisa que el servidor está despertando; a los 90 s,
     «No pudimos conectar» y Reintentar;
   - con la sesión ya abierta (recarga de la página) no se consulta.
   Con el cliente simulado (sin VITE_API_URL) no hay servidor que esperar.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useRef, useState, type ReactNode, type RefObject } from 'react'
import { Button, ProgressBar, Spinner } from 'react-bootstrap'
import { sesionActual, usaApiReal } from '../api/http'
import { AVISO_MS, esperarServidor, type EstadoArranque } from '../api/salud'

/** Deja un bloque inerte: no recibe clic, foco ni teclado, y el lector de pantalla lo anuncia ocupado. */
export function useInerte(ref: RefObject<HTMLElement>, activo: boolean) {
  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (activo) { el.setAttribute('inert', ''); el.setAttribute('aria-busy', 'true') }
    else { el.removeAttribute('inert'); el.removeAttribute('aria-busy') }
  }, [ref, activo])
}

/** Capa de espera a pantalla completa. La usan el arranque y la entrada a la obra. */
export function CapaPlano({ titulo, detalle, progreso, saliendo, alDibujarse, acciones, etiqueta }: {
  titulo: string
  detalle?: string
  /** 0 a 1 */
  progreso?: number
  saliendo?: boolean
  /** se llama al mostrarse (antes esperaba a que terminara una animación) */
  alDibujarse?: () => void
  acciones?: ReactNode
  etiqueta: string
}) {
  const caja = useRef<HTMLDivElement>(null)
  useEffect(() => { caja.current?.focus(); alDibujarse?.() }, []) // eslint-disable-line react-hooks/exhaustive-deps
  const pct = Math.round(Math.max(0, Math.min(1, progreso ?? 0)) * 100)
  if (saliendo) return null
  return (
    <div
      ref={caja} tabIndex={-1} role="dialog" aria-modal="true" aria-label={etiqueta}
      className="position-fixed top-0 start-0 w-100 h-100 bg-body d-flex align-items-center justify-content-center"
      style={{ zIndex: 2000 }}
    >
      <div className="text-center px-3" style={{ maxWidth: 420 }} aria-live="polite">
        <Spinner animation="border" variant="primary" className="mb-3" />
        <h1 className="h5">{titulo}</h1>
        {detalle && <p className="text-body-secondary small">{detalle}</p>}
        <ProgressBar now={pct} label={`${pct} %`} visuallyHidden aria-label="Avance de la conexión" className="mb-3" />
        {acciones}
      </div>
    </div>
  )
}

export function Arranque({ children }: { children: ReactNode }) {
  // con la sesión ya abierta no se espera ni se consulta
  const [debeEsperar] = useState(() => usaApiReal && !sesionActual())
  const [visible, setVisible] = useState(debeEsperar)
  const [estado, setEstado] = useState<EstadoArranque>({ fase: 'preparando', listos: 0, total: 0 })
  const [lento, setLento] = useState(false)
  const [intento, setIntento] = useState(0)
  const contenido = useRef<HTMLDivElement>(null)
  useInerte(contenido, visible)

  useEffect(() => {
    if (!debeEsperar) return
    const corte = new AbortController()
    setLento(false)
    setEstado({ fase: 'preparando', listos: 0, total: 0 })
    const reloj = setTimeout(() => setLento(true), AVISO_MS)
    esperarServidor({ alCambiar: setEstado, senal: corte.signal }).then((listo) => {
      if (listo && !corte.signal.aborted) setVisible(false)
    })
    return () => { corte.abort(); clearTimeout(reloj) }
  }, [intento, debeEsperar])

  // al quitar la capa, el foco va al primer campo (el autoFocus de Entrar no pudo tomarlo mientras era inerte)
  useEffect(() => {
    if (!visible) (contenido.current?.querySelector<HTMLElement>('#usuario, input, button'))?.focus()
  }, [visible])

  const fallo = estado.fase === 'sin_conexion'
  const detalle = fallo
    ? 'El servidor no respondió en 90 segundos. Revisa tu conexión a internet y vuelve a intentar.'
    : lento || estado.fase === 'despertando'
      ? 'El servidor está despertando, puede tardar hasta un minuto'
      : undefined

  return (
    <>
      <div ref={contenido}>{children}</div>
      {visible && (
        <CapaPlano
          key={intento}
          etiqueta="Preparando SIGRO"
          titulo={fallo ? 'No pudimos conectar' : 'Preparando SIGRO…'}
          detalle={detalle}
          progreso={estado.total ? estado.listos / estado.total : 0}
          acciones={fallo && <Button onClick={() => setIntento((i) => i + 1)}>Reintentar</Button>}
        />
      )}
    </>
  )
}
