/* ─────────────────────────────────────────────────────────────────────────────
   Elegir la obra al entrar · solo cuando la persona está asignada a más de una.
   Las obras son las que devolvió el servidor al iniciar sesión (regla 9: solo
   aquellas donde está asignada); después la misma entrada «Preparando tu obra».
   ───────────────────────────────────────────────────────────────────────── */
import { useState } from 'react'
import { Alert, Card } from 'react-bootstrap'
import { DibujoObra } from '../ui/Ilustraciones'
import { PantallaEntrada, Portada } from '../ui/Portada'

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
    <PantallaEntrada portada={
      <Portada titulo={`Hola, ${nombre}.`} remate="¿En qué obra trabajas hoy?">
        <p className="mb-0" style={{ color: '#d2d3d3', maxWidth: 520 }}>
          Solo ves las obras donde estás asignado, y dentro de cada una, solo lo que tu rol permite.
        </p>
      </Portada>
    }>
      <Card>
        <Card.Body className="p-4">
          <h1 id="elegir-obra-titulo" className="h4">Elige la obra</h1>
          <p className="text-body-secondary small">
            Hola, {nombre}. Tienes {obras.length} obras asignadas: verás solo lo de la que elijas y puedes cambiarla desde el menú.
          </p>
          {/* cada obra es un botón: tarjeta con su color, la grúa, el nombre y tu rol en ella */}
          <div role="list" aria-labelledby="elegir-obra-titulo" className="d-flex flex-column gap-2">
            {obras.map((o) => (
              <div role="listitem" key={o.id} className="sigro-obra-opcion">
                <button type="button" onClick={() => elegir(o)} disabled={eligiendo !== null} aria-busy={eligiendo === o.id}>
                  <span className="icono" aria-hidden="true"><DibujoObra cual="grua" ancho={44} /></span>
                  <span className="min-w-0 flex-grow-1 text-start">
                    <span className="d-block fw-semibold text-truncate">{o.nombre}</span>
                    <small className="text-body-secondary">{eligiendo === o.id ? 'Abriendo…' : o.rol}</small>
                  </span>
                  <span className="flecha" aria-hidden="true">→</span>
                </button>
              </div>
            ))}
          </div>
          {error && <Alert variant="danger" className="mt-3 mb-0 py-2">{error}</Alert>}
        </Card.Body>
      </Card>
    </PantallaEntrada>
  )
}
