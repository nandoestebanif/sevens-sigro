/* ─────────────────────────────────────────────────────────────────────────────
   Elegir la obra al entrar · solo cuando la persona está asignada a más de una.
   Las obras son las que devolvió el servidor al iniciar sesión (regla 9: solo
   aquellas donde está asignada); después la misma entrada «Preparando tu obra».
   ───────────────────────────────────────────────────────────────────────── */
import { useState } from 'react'
import { Alert, Card, Container, ListGroup } from 'react-bootstrap'

export interface ObraParaElegir { id: string; nombre: string; rol: string }

export function ElegirObra({ nombre, obras, alElegir }: {
  nombre: string
  obras: ObraParaElegir[]
  alElegir: (o: ObraParaElegir) => Promise<void>
}) {
  const [eligiendo, setEligiendo] = useState<string | null>(null)
  const [error, setError] = useState('')
  const elegir = async (o: ObraParaElegir) => {
    setEligiendo(o.id); setError('')
    try { await alElegir(o) } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo abrir la obra. Inténtalo de nuevo.')
      setEligiendo(null)
    }
  }
  return (
    <Container className="min-vh-100 d-flex align-items-center justify-content-center py-5">
      <Card className="shadow w-100"  style={{ maxWidth: 480 }}>
        <Card.Body className="p-4">
          <h1 id="elegir-obra-titulo" className="h4">Elige la obra</h1>
          <p className="text-body-secondary small">
            Hola, {nombre}. Tienes {obras.length} obras asignadas: verás solo lo de la que elijas y puedes cambiarla desde el menú.
          </p>
          {/* ListGroup con «action»: cada fila es un botón */}
          <ListGroup aria-labelledby="elegir-obra-titulo">
            {obras.map((o) => (
              <ListGroup.Item key={o.id} action onClick={() => elegir(o)} disabled={eligiendo !== null}>
                <div className="fw-semibold">{o.nombre}</div>
                <small className="text-body-secondary">{eligiendo === o.id ? 'Abriendo…' : o.rol}</small>
              </ListGroup.Item>
            ))}
          </ListGroup>
          {error && <Alert variant="danger" className="mt-3 mb-0 py-2">{error}</Alert>}
        </Card.Body>
      </Card>
    </Container>
  )
}
