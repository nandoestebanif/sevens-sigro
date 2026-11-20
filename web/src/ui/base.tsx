/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · componentes base sobre React-Bootstrap
   Cada pieza envuelve un componente de React-Bootstrap con los nombres que ya
   usan las pantallas (Tarjeta, Boton, Chip…). Así las pantallas no repiten
   props de Bootstrap y, si mañana cambia el diseño, se cambia aquí y nada más.
   ───────────────────────────────────────────────────────────────────────── */
import type { CSSProperties, ReactNode } from 'react'
import { Badge, Button, Card, Form, Modal as ModalBs, ProgressBar } from 'react-bootstrap'
import { partirEstilo } from './piezas'

/** Tarjeta de Bootstrap (Card). Sin relleno, para tablas que van de borde a borde. */
export function Tarjeta({ children, relleno = true, estilo }: {
  children: ReactNode
  relleno?: boolean
  estilo?: CSSProperties
}) {
  const [fuera, dentro] = partirEstilo(estilo)
  return (
    <Card className="shadow-sm" style={fuera}>
      {relleno ? <Card.Body style={dentro}>{children}</Card.Body> : children}
    </Card>
  )
}

/** Botón de Bootstrap: primario (naranja de la marca), secundario (con borde) o fantasma (link). */
export function Boton({ children, tipo = 'primario', deshabilitado, enClic, anchoCompleto }: {
  children: ReactNode
  tipo?: 'primario' | 'secundario' | 'fantasma'
  deshabilitado?: boolean
  enClic?: () => void
  anchoCompleto?: boolean
}) {
  const variante = { primario: 'primary', secundario: 'outline-secondary', fantasma: 'link' }[tipo]
  return (
    <Button variant={variante} onClick={enClic} disabled={deshabilitado} className={anchoCompleto ? 'w-100' : undefined}>
      {children}
    </Button>
  )
}

export type TonoChip = 'bien' | 'aviso' | 'grave' | 'neutro' | 'info' | 'marca'

/** Estado con etiqueta (Badge): el color nunca va solo, siempre con su texto. */
export function Chip({ tono, children }: { tono: TonoChip; children: ReactNode }) {
  const color: Record<TonoChip, string> = {
    bien: 'success', aviso: 'warning', grave: 'danger', neutro: 'secondary', info: 'info', marca: 'primary',
  }
  // «-subtle» y «-emphasis»: fondo claro con texto oscuro del mismo color (pasa contraste)
  return (
    <Badge pill bg="" className={`n bg-${color[tono]}-subtle text-${color[tono]}-emphasis border border-${color[tono]}-subtle`}>
      {children}
    </Badge>
  )
}

/** Campo de texto con su etiqueta y su mensaje de error (Form.Group + Form.Control). */
export function Campo({ etiqueta, valor, enCambio, marcador, error, autoFoco, id, deshabilitado }: {
  etiqueta: string
  valor: string
  enCambio: (v: string) => void
  marcador?: string
  error?: string
  autoFoco?: boolean
  id: string
  deshabilitado?: boolean
}) {
  return (
    <Form.Group controlId={id}>
      <Form.Label className="small fw-semibold">{etiqueta}</Form.Label>
      <Form.Control
        value={valor}
        placeholder={marcador}
        autoFocus={autoFoco}
        disabled={deshabilitado}
        isInvalid={!!error}
        onChange={(e) => enCambio(e.target.value)}
      />
      <Form.Control.Feedback type="invalid">{error}</Form.Control.Feedback>
    </Form.Group>
  )
}

/** Barra de avance (ProgressBar). La marca opcional es la referencia: «¿voy bien?». */
export function Medidor({ pct, marcaPct, color, ancho = 124 }: {
  pct: number
  marcaPct?: number
  color: string
  ancho?: number | string
}) {
  return (
    <div
      role="img"
      aria-label={`${pct} por ciento${marcaPct != null ? `, referencia ${marcaPct} por ciento` : ''}`}
      className="position-relative"
      style={{ width: ancho }}
    >
      {/* el color de la barra es una variable de Bootstrap: --bs-progress-bar-bg */}
      <ProgressBar now={Math.min(pct, 100)} style={{ height: 6, '--bs-progress-bar-bg': color } as CSSProperties} />
      {marcaPct != null && (
        <div className="position-absolute bg-body-emphasis" style={{ left: `${marcaPct}%`, top: -3, width: 2, height: 12 }} />
      )}
    </div>
  )
}

export function Rotulo({ children }: { children: ReactNode }) {
  return <div className="small text-body-secondary">{children}</div>
}

export function Titulo({ children, sub }: { children: ReactNode; sub?: ReactNode }) {
  return (
    <div>
      <h1 className="h3 mb-1">{children}</h1>
      {sub && <p className="text-body-secondary small mb-0">{sub}</p>}
    </div>
  )
}

export function Vacio({ titulo, detalle }: { titulo: string; detalle: string }) {
  return (
    <div className="text-center text-body-secondary py-5 px-3">
      <div className="fw-semibold">{titulo}</div>
      <div className="small mt-1">{detalle}</div>
    </div>
  )
}

/**
 * Modal de Bootstrap. React-Bootstrap ya resuelve lo que antes se hacía a mano:
 * se cierra con Esc, con la X o tocando fuera, atrapa el foco y lo devuelve al cerrar.
 */
export function Modal({ titulo, children, enCerrar, ancho = 440 }: {
  titulo: string
  children: ReactNode
  enCerrar: () => void
  ancho?: number
}) {
  const tam = ancho >= 900 ? 'xl' : ancho >= 620 ? 'lg' : undefined
  return (
    <ModalBs show onHide={enCerrar} centered scrollable size={tam}>
      <ModalBs.Header closeButton>
        <ModalBs.Title as="h2" className="h5">{titulo}</ModalBs.Title>
      </ModalBs.Header>
      <ModalBs.Body>{children}</ModalBs.Body>
    </ModalBs>
  )
}
