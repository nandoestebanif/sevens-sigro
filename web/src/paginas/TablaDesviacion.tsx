/* ─────────────────────────────────────────────────────────────────────────────
   «Insumos y su desviación» del panel de gasto en recursos (HU-57, HU-23)

   Antes: una tabla de 10 columnas y 285 filas, con scroll hacia abajo y a la
   derecha. Ahora (fase 1 del rediseño, 25/11):
   - 6 columnas que entran en una laptop sin desplazarse a los lados;
   - lo demás (esperado, requerido, recibido, falta comprar, efecto cantidad)
     se abre con «Ver detalle» en la misma fila;
   - buscador por nombre o SKU, filtros por tipo y críticos, 10 por página.
   Las cifras son las del servidor; aquí solo se presentan.
   ───────────────────────────────────────────────────────────────────────── */
import { Fragment, useMemo, useState } from 'react'
import { Form, InputGroup } from 'react-bootstrap'
import type { AvanceInsumo } from '../api/tipos'
import { Chip, Medidor, Vacio, type TonoChip } from '../ui/base'
import { Paginador } from '../ui/Tabla'
import { TAMANOS, filtrarTexto, paginar } from '../ui/tablas'

const soles = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 0 })
const num = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 2 })
const tonoDesvio = (p: number): TonoChip => (p <= 1 ? 'bien' : p <= 4 ? 'aviso' : 'grave')
const colorDesvio = (p: number) => (p <= 1 ? 'var(--bien)' : p <= 4 ? 'var(--aviso)' : 'var(--grave)')
const pts = (p: number) => `${p > 0 ? '+' : ''}${p.toLocaleString('es-PE', { maximumFractionDigits: 1 })} pt`
const signoSoles = (v: number) => `${v > 0 ? '+' : v < 0 ? '−' : ''}S/ ${Math.abs(v).toLocaleString('es-PE', { maximumFractionDigits: 0 })}`
const TIPOS: Record<string, string> = { material: 'Materiales', herramienta: 'Herramientas', equipo: 'Equipos', mano_de_obra: 'Mano de obra' }

export function TablaDesviacion({ filas, conDesviacionReal, avancePlazoPct }: {
  filas: AvanceInsumo[]
  conDesviacionReal: boolean
  avancePlazoPct: number
}) {
  const [q, setQ] = useState('')
  const [tipo, setTipo] = useState('')
  const [soloCriticos, setSoloCriticos] = useState(false)
  const [pagina, setPagina] = useState(1)
  const [tam, setTam] = useState(TAMANOS[0])
  const [abierto, setAbierto] = useState<string | null>(null)

  const tipos = useMemo(() => [...new Set(filas.map((f) => f.tipo).filter(Boolean) as string[])].sort(), [filas])
  const visibles = useMemo(() => {
    const xs = filas.filter((f) => (!tipo || f.tipo === tipo) && (!soloCriticos || f.critico))
    return filtrarTexto(xs, q, (f) => `${f.descripcion} ${f.codigo}`)
  }, [filas, q, tipo, soloCriticos])
  const p = paginar(visibles, pagina, tam)
  const ir = (n: number) => { setPagina(n); setAbierto(null) }

  if (filas.length === 0) {
    return <Vacio titulo="Sin requerimiento todavía" detalle="Importa la Relación de insumos del S10 para comparar lo gastado contra lo planificado." />
  }
  return (
    <div className="d-flex flex-column gap-3 min-w-0" style={{ minWidth: 0 }}>
      <div className="d-flex flex-wrap gap-2 align-items-center">
        <InputGroup style={{ flex: '1 1 240px', maxWidth: 380 }}>
          <InputGroup.Text aria-hidden>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" /></svg>
          </InputGroup.Text>
          <Form.Control value={q} placeholder="Buscar por nombre o SKU…" aria-label="Buscar insumo" onChange={(e) => { setQ(e.target.value); ir(1) }} />
        </InputGroup>
        {tipos.length > 1 && (
          <Form.Select aria-label="Tipo de insumo" value={tipo} onChange={(e) => { setTipo(e.target.value); ir(1) }} style={{ width: 'auto' }}>
            <option value="">Todos los tipos</option>
            {tipos.map((t) => <option key={t} value={t}>{TIPOS[t] ?? t}</option>)}
          </Form.Select>
        )}
        <Form.Check type="switch" id="desv-criticos" label="Solo críticos" checked={soloCriticos} onChange={(e) => { setSoloCriticos(e.target.checked); ir(1) }} />
        <span className="small text-body-secondary n ms-auto" role="status">{visibles.length.toLocaleString('es-PE')} de {filas.length.toLocaleString('es-PE')} insumos</span>
      </div>

      {visibles.length === 0 ? (
        <Vacio titulo="Sin resultados" detalle="Prueba con otra palabra o quita los filtros." />
      ) : (
        <>
          {/* position-relative: lo «visually-hidden» de adentro no se escapa y ensancha la página en el celular */}
          <div className="overflow-x-auto position-relative">
            <table className="table table-hover align-middle mb-0 tabla-desviacion" aria-label="Insumos y su desviación">
              <thead>
                <tr>
                  <th>Insumo</th>
                  <th className="n">Planificado S/</th>
                  <th className="n">Comprado</th>
                  <th className="n">Consumido</th>
                  <th className="n">Gastado S/</th>
                  <th className="n">Desviación</th>
                  <th className="text-end"><span className="visually-hidden">Detalle</span></th>
                </tr>
              </thead>
              <tbody>
                {p.items.map((f) => {
                  const ver = abierto === f.insumoId
                  return (
                    <Fragment key={f.insumoId}>
                      <tr className={ver ? 'table-active' : undefined}>
                        <td style={{ minWidth: 200 }}>
                          <div className="fw-semibold">{f.descripcion}</div>
                          <div className="n small text-body-secondary mt-1 text-start d-flex align-items-center gap-2">
                            SKU {f.codigo}{f.critico && <Chip tono="marca">crítico</Chip>}
                          </div>
                        </td>
                        <td className="n">{soles(f.planificadoImporte)}</td>
                        <td className="n text-nowrap">
                          {num(f.compradoCantidad)} {f.unidadCompra}
                          <div className="small text-body-secondary">{f.compradoPct} %</div>
                        </td>
                        <td>
                          {f.sinDatoAlmacen ? <div className="n text-body-secondary">—</div> : (
                            <div className="d-flex align-items-center gap-2 justify-content-end">
                              <Medidor pct={f.consumidoPct} marcaPct={avancePlazoPct} color={colorDesvio(f.desviacionPuntos)} ancho={56} />
                              <span className="n text-end" style={{ width: 48 }}>{f.consumidoPct} %</span>
                            </div>
                          )}
                        </td>
                        <td className="n fw-bold">{soles(f.gastado)}</td>
                        <td className="n"><Chip tono={tonoDesvio(f.desviacionPuntos)}>{pts(f.desviacionPuntos)}</Chip></td>
                        <td className="text-end">
                          <button type="button" className={`btn btn-sm btn-outline-secondary sigro-ver${ver ? ' abierto' : ''}`} aria-expanded={ver}
                            aria-controls={`desv-${f.insumoId}`} onClick={() => setAbierto(ver ? null : f.insumoId)}
                            aria-label={`${ver ? 'Ocultar' : 'Ver'} detalle de ${f.descripcion}`} title={ver ? 'Ocultar detalle' : 'Ver detalle'}>
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="M6 9l6 6 6-6" /></svg>
                          </button>
                        </td>
                      </tr>
                      {ver && (
                        <tr id={`desv-${f.insumoId}`} className="fila-detalle">
                          <td colSpan={7}>
                            <dl className="detalle-insumo">
                              {conDesviacionReal && <div><dt>Esperado a la fecha</dt><dd>S/ {soles(f.esperadoImporte ?? 0)}</dd></div>}
                              <div><dt>Requerido (S10)</dt><dd>{f.requeridoCompra !== undefined ? `${num(f.requeridoCompra)} ${f.unidadCompra}` : '—'}</dd></div>
                              <div><dt>Recibido</dt><dd>{f.sinDatoAlmacen ? '—' : `${f.recibidoPct} %`}</dd></div>
                              <div><dt>Consumido</dt><dd>{f.sinDatoAlmacen ? '—' : `${num(f.consumidoCantidad ?? 0)} ${f.unidad ?? f.unidadCompra}`}</dd></div>
                              <div><dt>Falta comprar</dt><dd>{num(f.saldoPorComprar)} {f.unidadCompra}</dd></div>
                              {f.desviacionImporte !== undefined && (
                                <div><dt>Desviación en soles</dt><dd>{signoSoles(f.desviacionImporte)}{f.efectoCantidad !== undefined ? ` · por cantidad ${signoSoles(f.efectoCantidad)}` : ''}</dd></div>
                              )}
                              <div><dt>Avance del plazo</dt><dd>{avancePlazoPct.toLocaleString('es-PE', { maximumFractionDigits: 1 })} %</dd></div>
                            </dl>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  )
                })}
              </tbody>
            </table>
          </div>
          {p.total > TAMANOS[0] && <Paginador p={p} enPagina={ir} tam={tam} tamanos={TAMANOS} enTam={(n) => { setTam(n); ir(1) }} />}
        </>
      )}
    </div>
  )
}
