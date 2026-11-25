/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · detalle de un elemento de cualquier lista (fase 2 del rediseño, 25/11)

   Un panel que entra por la derecha (Offcanvas de Bootstrap) con todo lo de una
   fila: la tabla muestra lo justo para comparar y el detalle, lo demás. Igual
   para obras, insumos, compras, trabajadores y usuarios:
   - encabezado con título, subtítulo y chips de estado;
   - secciones de «etiqueta: valor» en dos columnas;
   - contenido libre debajo (p. ej. el kárdex de un insumo);
   - acciones al pie (editar, dar de baja…), que el servidor igual verifica.
   ───────────────────────────────────────────────────────────────────────── */
import type { ReactNode } from 'react'
import { Offcanvas } from 'react-bootstrap'

export interface SeccionDetalle { titulo?: string; datos: [string, ReactNode][] }

export function PanelDetalle({ titulo, subtitulo, chips, secciones = [], children, pie, enCerrar, ancho = 520 }: {
  titulo: ReactNode
  subtitulo?: ReactNode
  chips?: ReactNode
  secciones?: SeccionDetalle[]
  children?: ReactNode
  /** botones de acción al pie */
  pie?: ReactNode
  enCerrar: () => void
  ancho?: number
}) {
  return (
    <Offcanvas show onHide={enCerrar} placement="end" className="sigro-detalle" style={{ width: `min(${ancho}px, 100vw)` }} aria-labelledby="detalle-titulo">
      <Offcanvas.Header closeButton closeLabel="Cerrar detalle">
        <div className="min-w-0">
          <Offcanvas.Title as="h2" id="detalle-titulo" className="h5 mb-1 text-break">{titulo}</Offcanvas.Title>
          {subtitulo && <div className="small text-body-secondary">{subtitulo}</div>}
          {chips && <div className="d-flex flex-wrap gap-1 mt-2">{chips}</div>}
        </div>
      </Offcanvas.Header>
      <Offcanvas.Body className="d-flex flex-column gap-4">
        {secciones.filter((s) => s.datos.length).map((s, i) => (
          <section key={s.titulo ?? i}>
            {s.titulo && <h3 className="sigro-detalle-seccion">{s.titulo}</h3>}
            <dl className="detalle-insumo">
              {s.datos.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v ?? '—'}</dd></div>)}
            </dl>
          </section>
        ))}
        {children}
      </Offcanvas.Body>
      {pie && <div className="sigro-detalle-pie">{pie}</div>}
    </Offcanvas>
  )
}
