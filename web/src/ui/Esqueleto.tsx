/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · esqueletos de carga con Placeholder de Bootstrap
   Mientras llega la respuesta del servidor se muestra la forma de lo que va a
   aparecer (barras grises animadas) en vez de un spinner: la página no «salta»
   al cargar y se percibe más rápida.
   ───────────────────────────────────────────────────────────────────────── */
import { Card, Placeholder } from 'react-bootstrap'

/** Filas de una tabla que todavía no llegó. */
export function EsqueletoTabla({ filas = 6, columnas = 5 }: { filas?: number; columnas?: number }) {
  return (
    <div aria-busy="true" aria-label="Cargando…" className="d-flex flex-column gap-3 py-2">
      {Array.from({ length: filas }, (_, f) => (
        <Placeholder key={f} as="div" animation="glow" className="d-flex gap-3">
          {Array.from({ length: columnas }, (_, c) => <Placeholder key={c} xs={c === 1 ? 4 : 2} className="rounded-pill" />)}
        </Placeholder>
      ))}
    </div>
  )
}

/** Tarjetas de cifra (KPI) que todavía no llegaron: la silueta del ticket, sin color hasta que llegue la cifra. */
export function EsqueletoKpis({ cuantas = 4 }: { cuantas?: number }) {
  return (
    <div aria-busy="true" aria-label="Cargando cifras…" className="d-flex flex-wrap gap-3">
      {Array.from({ length: cuantas }, (_, i) => (
        <div key={i} className="sigro-ticket sigro-ticket-esqueleto" style={{ flex: '1 1 200px' }}>
          <Placeholder as="div" animation="glow"><Placeholder xs={6} size="sm" className="rounded-pill" /></Placeholder>
          <Placeholder as="div" animation="glow" className="mt-5 mb-2"><Placeholder xs={8} size="lg" className="rounded-pill" /></Placeholder>
          <Placeholder as="div" animation="glow"><Placeholder xs={10} size="xs" className="rounded-pill" /></Placeholder>
        </div>
      ))}
    </div>
  )
}

/** Página completa: título, cifras y una tabla. */
export function EsqueletoPagina() {
  return (
    <div className="d-flex flex-column gap-3" aria-busy="true">
      <Placeholder as="h1" animation="glow" className="h3"><Placeholder xs={4} /></Placeholder>
      <Placeholder as="p" animation="glow"><Placeholder xs={7} size="sm" /></Placeholder>
      <EsqueletoKpis />
      <Card><Card.Body><EsqueletoTabla /></Card.Body></Card>
    </div>
  )
}

/** Espacio de un gráfico mientras se descarga Chart.js. */
export function EsqueletoGrafico({ alto = 260 }: { alto?: number }) {
  return (
    <Placeholder as="div" animation="wave" aria-busy="true" aria-label="Cargando gráfico…">
      <Placeholder xs={12} className="rounded-3" style={{ height: alto }} />
    </Placeholder>
  )
}
