/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · portada negra de las pantallas de entrada (Ingresar, Elegir obra)
   Logo blanco de SEVEN'S, un titular con su remate en amarillo, un texto o
   lista debajo y, al pie, tarjetas «ticket» con ilustraciones de obra.
   En celulares la portada se oculta y queda solo el formulario.
   ───────────────────────────────────────────────────────────────────────── */
import type { ReactNode } from 'react'
import { Col, Container, Row } from 'react-bootstrap'
import logo from '../assets/sevens-logo-blanco.webp'
import { DibujoObra, type Ilustracion } from './Ilustraciones'

export interface TarjetaPortada { nombre: string; dibujo: Ilustracion }

/** las tres partes de SIGRO, como tarjetas «ticket» de la portada */
export const PARTES_SIGRO: TarjetaPortada[] = [
  { nombre: 'Compras', dibujo: 'factura' }, { nombre: 'Almacén', dibujo: 'camion' }, { nombre: 'Gasto en recursos', dibujo: 'grua' },
]

export function Portada({ titulo, remate, children, tarjetas = PARTES_SIGRO }: {
  titulo: ReactNode
  /** final del titular, en amarillo */
  remate?: ReactNode
  children?: ReactNode
  tarjetas?: TarjetaPortada[]
}) {
  return (
    <div className="sigro-portada w-100 p-5 d-flex flex-column justify-content-between gap-4">
      <img src={logo} alt="SEVEN'S Ingenieros SAC" height={86} className="align-self-start" />
      <div>
        <h1 className="display-6 fw-semibold mb-4" style={{ maxWidth: 560 }}>
          {titulo}{remate && <> <span className="text-primary">{remate}</span></>}
        </h1>
        {children}
      </div>
      <div className="d-flex gap-3">
        {tarjetas.map((p) => (
          <div key={p.nombre} className="sigro-ticket" style={{ flex: '1 1 0', minHeight: 140 }}>
            <span className="franja" aria-hidden="true" />
            <span className="ilustracion"><DibujoObra cual={p.dibujo} ancho={66} /></span>
            <div className="fw-semibold small position-relative" style={{ marginTop: 78 }}>{p.nombre}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

/** Pantalla de entrada: portada a la izquierda (desde lg) y el contenido a la derecha. */
export function PantallaEntrada({ portada, children }: { portada: ReactNode; children: ReactNode }) {
  return (
    <Container fluid className="min-vh-100 d-flex align-items-stretch p-3">
      <Row className="w-100 g-4 align-items-center mx-0">
        <Col lg={6} className="d-none d-lg-flex align-self-stretch">{portada}</Col>
        <Col sm={10} md={7} lg={5} xl={4} className="mx-auto">{children}</Col>
      </Row>
    </Container>
  )
}
