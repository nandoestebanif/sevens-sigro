/* ─────────────────────────────────────────────────────────────────────────────
   Pantalla de arranque: la web no se usa hasta que el servidor esté listo.
   En Render los servicios duermen; sin esto, la primera persona del día
   escribe su clave, espera y recibe un error que no es suyo. Aquí:
   - una capa de vidrio a pantalla completa con «el plano se dibuja» y el logo;
   - lo de atrás queda inerte (ni clic ni teclado) y con aria-busy;
   - se pregunta a GET /salud/servicios cada 10 s y se deja apenas todo está
     listo; a los 5 s se avisa que el servidor está despertando; a los 90 s,
     «No pudimos conectar» y Reintentar;
   - con la sesión ya abierta (recarga de la página) no se consulta: si el
     servidor respondió para entrar, no hace falta despertarlo otra vez;
   - con «reducir movimiento», sin animación: solo una barra de progreso.
   Con el cliente simulado (sin VITE_API_URL) no hay servidor que esperar.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useRef, useState, type ReactNode, type RefObject } from 'react'
import { sesionActual, usaApiReal } from '../api/http'
import { AVISO_MS, esperarServidor, type EstadoArranque } from '../api/salud'
import { PlanoQueSeDibuja, useMenosMovimiento } from './PlanoQueSeDibuja'
import { Pildora } from './vidrio'

const SALIDA_MS = 350

/** Deja un bloque inerte: no recibe clic, foco ni teclado, y el lector de pantalla lo anuncia ocupado. */
export function useInerte(ref: RefObject<HTMLElement>, activo: boolean) {
  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (activo) { el.setAttribute('inert', ''); el.setAttribute('aria-busy', 'true') }
    else { el.removeAttribute('inert'); el.removeAttribute('aria-busy') }
  }, [ref, activo])
}

/** La capa de vidrio con el plano. La usan el arranque y la entrada a la obra. */
export function CapaPlano({ titulo, detalle, progreso, saliendo, alDibujarse, acciones, etiqueta }: {
  titulo: string
  detalle?: string
  /** 0 a 1; solo se ve con «reducir movimiento», en lugar del plano */
  progreso?: number
  saliendo?: boolean
  alDibujarse?: () => void
  acciones?: ReactNode
  etiqueta: string
}) {
  const menos = useMenosMovimiento()
  const caja = useRef<HTMLDivElement>(null)
  useEffect(() => { caja.current?.focus() }, [])
  useEffect(() => { if (menos) alDibujarse?.() }, [menos]) // eslint-disable-line react-hooks/exhaustive-deps
  const pct = Math.round(Math.max(0, Math.min(1, progreso ?? 0)) * 100)
  return (
    <div
      ref={caja} tabIndex={-1} role="dialog" aria-modal="true" aria-label={etiqueta}
      className={`capa-plano${saliendo ? ' saliendo' : ''}`}
    >
      <div className="capa-plano-centro">
        {menos ? (
          <div className="capa-plano-barra" role="progressbar" aria-label="Avance de la conexión" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct}>
            <span style={{ width: `${pct}%` }} />
          </div>
        ) : (
          <PlanoQueSeDibuja alDibujarse={alDibujarse} />
        )}
        <div aria-live="polite" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, textAlign: 'center' }}>
          <p className="capa-plano-titulo">{titulo}</p>
          {detalle && <p className="capa-plano-detalle">{detalle}</p>}
        </div>
        {acciones}
      </div>
    </div>
  )
}

export function Arranque({ children }: { children: ReactNode }) {
  // con la sesión ya abierta no se espera ni se consulta (hotfix-sondeo)
  const [debeEsperar] = useState(() => usaApiReal && !sesionActual())
  const [visible, setVisible] = useState(debeEsperar)
  const [saliendo, setSaliendo] = useState(false)
  const [estado, setEstado] = useState<EstadoArranque>({ fase: 'preparando', listos: 0, total: 0 })
  const [lento, setLento] = useState(false)
  const [intento, setIntento] = useState(0)
  const contenido = useRef<HTMLDivElement>(null)
  useInerte(contenido, visible)

  useEffect(() => {
    if (!debeEsperar) return
    const corte = new AbortController()
    let salida: ReturnType<typeof setTimeout> | undefined
    setLento(false)
    setEstado({ fase: 'preparando', listos: 0, total: 0 })
    const reloj = setTimeout(() => setLento(true), AVISO_MS)   // a tiempo aunque una consulta tarde
    esperarServidor({ alCambiar: setEstado, senal: corte.signal }).then((listo) => {
      if (!listo || corte.signal.aborted) return
      setSaliendo(true)
      salida = setTimeout(() => setVisible(false), SALIDA_MS)
    })
    return () => { corte.abort(); clearTimeout(reloj); clearTimeout(salida) }
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
  const progreso = estado.total ? estado.listos / estado.total : 0

  return (
    <>
      <div ref={contenido} className="raiz-contenido">{children}</div>
      {visible && (
        <CapaPlano
          key={intento}
          etiqueta="Preparando SIGRO"
          titulo={fallo ? 'No pudimos conectar' : 'Preparando SIGRO…'}
          detalle={detalle}
          progreso={progreso}
          saliendo={saliendo}
          acciones={fallo && (
            <Pildora enClic={() => { setSaliendo(false); setIntento((i) => i + 1) }}>Reintentar</Pildora>
          )}
        />
      )}
    </>
  )
}
