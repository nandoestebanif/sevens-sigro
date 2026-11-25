/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · piezas de página sobre React-Bootstrap
   Encabezado, tarjetas de cifra (KPI), filtros, avisos y campos con etiqueta.
   Igual que en ui/base.tsx: las pantallas usan estos nombres y aquí se decide
   qué componente de Bootstrap va debajo.
   ───────────────────────────────────────────────────────────────────────── */
import type { CSSProperties, ReactNode } from 'react'
import { Alert, Button, ButtonGroup, Card, Form, InputGroup, Nav, Stack } from 'react-bootstrap'
import { Ayuda, type Clave } from './Glosario'
import { DibujoObra, type Ilustracion } from './Ilustraciones'

/** Separa un estilo en lo que va a la Card (tamaño en la fila) y lo que va a su contenido (cómo se acomodan los hijos). */
export function partirEstilo(estilo?: CSSProperties): [CSSProperties, CSSProperties] {
  const interior = ['display', 'flexDirection', 'flexWrap', 'gap', 'alignItems', 'justifyContent', 'padding']
  const fuera: CSSProperties = {}, dentro: CSSProperties = {}
  for (const [k, v] of Object.entries(estilo ?? {})) (interior.includes(k) ? dentro : fuera)[k as 'gap'] = v
  return [fuera, dentro]
}

/** Sección de contenido: una Card de Bootstrap. «fuerte» = con un poco más de sombra. */
export function Vidrio({ children, fuerte, estilo, etiqueta }: {
  children: ReactNode
  fuerte?: boolean
  estilo?: CSSProperties
  etiqueta?: string
}) {
  const [fuera, dentro] = partirEstilo(estilo)
  return (
    <Card as="section" aria-label={etiqueta} className={fuerte ? 'shadow' : 'shadow-sm'} style={{ minWidth: 0, ...fuera }}>
      <Card.Body style={dentro}>{children}</Card.Body>
    </Card>
  )
}

/** Botón de Bootstrap. Primario = naranja de la marca; secundario = con borde. */
export function Pildora({ children, tipo = 'primario', deshabilitado, enClic, anchoCompleto, grande, enviar, chica, etiqueta }: {
  children: ReactNode
  tipo?: 'primario' | 'secundario'
  deshabilitado?: boolean
  enClic?: () => void
  anchoCompleto?: boolean
  grande?: boolean
  /** type="submit": envía el formulario que lo contiene */
  enviar?: boolean
  /** dentro de una fila de tabla: botón chico */
  chica?: boolean
  /** aria-label, cuando el texto visible no basta fuera de contexto */
  etiqueta?: string
}) {
  return (
    <Button
      type={enviar ? 'submit' : 'button'}
      variant={tipo === 'primario' ? 'primary' : 'outline-secondary'}
      size={grande ? 'lg' : chica ? 'sm' : undefined}
      onClick={enClic}
      disabled={deshabilitado}
      aria-label={etiqueta}
      className={anchoCompleto ? 'w-100' : undefined}
    >
      {children}
    </Button>
  )
}

/** Tarjeta «ticket» del rediseño: fondo de color (se turnan solos por orden), franja de luz,
    muescas a los lados e ilustración de obra. El contenido lo pone quien la usa. */
export function Ticket({ children, ilustracion, etiqueta, estilo }: {
  children: ReactNode
  ilustracion?: Ilustracion
  etiqueta?: string
  estilo?: CSSProperties
}) {
  return (
    <section className="sigro-ticket" aria-label={etiqueta} style={{ flex: '1 1 200px', ...estilo }}>
      {[26, 50, 74].map((y) => (
        <span key={y} aria-hidden="true">
          <span className="muesca" style={{ left: '-0.625rem', top: `${y}%` }} />
          <span className="muesca" style={{ right: '-0.625rem', top: `${y}%` }} />
        </span>
      ))}
      <span className="franja" aria-hidden="true" />
      {ilustracion && <span className="ilustracion"><DibujoObra cual={ilustracion} /></span>}
      {children}
    </section>
  )
}

/** Cifra clave de la página: un ticket con rótulo, valor grande y pie que la explica. */
export function Kpi({ rotulo, valor, pie, prefijo, ayuda, ilustracion }: {
  rotulo: string
  /** término del glosario: agrega el «ⓘ» con su definición (Popover) */
  ayuda?: Clave
  valor: ReactNode
  pie?: ReactNode
  prefijo?: string
  /** sobre un ticket de color el valor va siempre en negro; el signo y el pie dicen si es bueno o malo */
  color?: string
  ilustracion?: Ilustracion
}) {
  return (
    <Ticket ilustracion={ilustracion} etiqueta={rotulo}>
      <div className="rotulo">{rotulo}{ayuda && <Ayuda clave={ayuda} titulo={rotulo} />}</div>
      <div className="valor n">{prefijo && <span className="fs-6 fw-semibold me-1">{prefijo}</span>}{valor}</div>
      {pie && <div className="pie">{pie}</div>}
    </Ticket>
  )
}

/** Filtro de opciones excluyentes (ButtonGroup). Con «pestanas», son pestañas de Bootstrap (Nav). */
export function Segmentado<T extends string | number | boolean>({ opciones, valor, enCambio, etiqueta, pestanas }: {
  opciones: { valor: T; texto: ReactNode }[]
  valor: T
  enCambio: (v: T) => void
  etiqueta: string
  pestanas?: boolean
}) {
  if (pestanas) {
    return (
      <Nav variant="tabs" role="tablist" aria-label={etiqueta}>
        {opciones.map((o) => (
          <Nav.Item key={String(o.valor)}>
            <Nav.Link as="button" role="tab" aria-selected={o.valor === valor} active={o.valor === valor} onClick={() => enCambio(o.valor)}>
              {o.texto}
            </Nav.Link>
          </Nav.Item>
        ))}
      </Nav>
    )
  }
  return (
    <ButtonGroup aria-label={etiqueta} className="flex-wrap align-self-start">
      {opciones.map((o) => (
        <Button
          key={String(o.valor)}
          variant={o.valor === valor ? 'primary' : 'outline-primary'}
          aria-pressed={o.valor === valor}
          onClick={() => enCambio(o.valor)}
        >
          {o.texto}
        </Button>
      ))}
    </ButtonGroup>
  )
}

/** Fila de etiquetas bajo el encabezado (qué dato es real y cuál es de ejemplo). */
export function Chips({ children }: { children: ReactNode }) {
  return <Stack direction="horizontal" gap={2} className="flex-wrap">{children}</Stack>
}

/** Campo con etiqueta: el control (Form.Control, Form.Select…) va como hijo. */
export function CampoVidrio({ etiqueta, id, children, ayuda, error }: {
  etiqueta: string
  id: string
  children: ReactNode
  ayuda?: ReactNode
  error?: string
}) {
  return (
    <Form.Group controlId={id} className="min-w-0">
      <Form.Label className="small fw-semibold mb-1">{etiqueta}</Form.Label>
      {children}
      {ayuda && !error && <Form.Text muted>{ayuda}</Form.Text>}
      {error && <div role="alert" className="small fw-semibold text-danger mt-1">{error}</div>}
    </Form.Group>
  )
}

/** Aviso dentro de la página (Alert de Bootstrap), siempre con texto. */
export function Aviso({ tono, children, rol }: { tono: 'bien' | 'aviso' | 'grave' | 'info'; children: ReactNode; rol?: 'alert' | 'status' }) {
  const variante = { bien: 'success', aviso: 'warning', grave: 'danger', info: 'info' }[tono]
  return <Alert variant={variante} role={rol} className="mb-0 py-2">{children}</Alert>
}

/** Encabezado de página: título a la izquierda, acciones a la derecha; en pantallas chicas se apilan. */
export function Encabezado({ titulo, sub, acciones }: { titulo: ReactNode; sub?: ReactNode; acciones?: ReactNode }) {
  return (
    <header className="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-1">
      <div className="min-w-0">
        <h1 className="h3 mb-1">{titulo}</h1>
        {sub && <p className="text-body-secondary mb-0">{sub}</p>}
      </div>
      {acciones && <div className="d-flex flex-wrap align-items-center gap-2">{acciones}</div>}
    </header>
  )
}

export function TituloSeccion({ children }: { children: ReactNode }) {
  return <h2 className="h5 mb-0">{children}</h2>
}

/** Buscador (InputGroup de Bootstrap): ícono de lupa pegado al campo de texto. */
export function Buscador({ valor, enCambio, marcador, etiqueta, estilo }: {
  valor: string
  enCambio: (v: string) => void
  marcador: string
  etiqueta: string
  estilo?: CSSProperties
}) {
  return (
    <InputGroup style={estilo}>
      <InputGroup.Text aria-hidden="true">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
          <circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" />
        </svg>
      </InputGroup.Text>
      <Form.Control type="search" value={valor} onChange={(e) => enCambio(e.target.value)} placeholder={marcador} aria-label={etiqueta} />
    </InputGroup>
  )
}
