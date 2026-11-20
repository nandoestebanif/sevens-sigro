/* ─────────────────────────────────────────────────────────────────────────────
   HU-57 · Como gerente, quiero comparar el consumo valorizado de cada insumo
           contra lo que se planificó para la obra, para detectar la desviación
           de costo mientras la obra corre.
   HU-23 · Como residente, quiero consultar qué porcentaje de cada insumo se
           compró y se consumió, para distinguir lo que ingresó de lo que se usó.
   Lienzo W2 «Gasto en recursos» (estilo vidrio).

   Criterios cubiertos:
   - Compara contra cantidad y costo planificados del reporte de recursos REAL.
   - Desviación visible en puntos y con su chip; medidor contra el avance del plazo.
   - Se muestran requerido, comprado, recibido, consumido y saldo por comprar (HU-23).
   - Los insumos no planificados se informan aparte y no tocan la desviación.
   - El reporte declara su alcance (gasto en recursos ≠ costo total) y advierte
     que lo planificado incluye desperdicio: exceder avisa, no bloquea.
   HU-58 (T-325, T-326): con el servidor configurado, la parte de arriba son las
   cinco cifras reales del gasto (GastoRecursos.tsx) y reemplaza al prototipo.
   La comparación por insumo (HU-57, HU-23) sigue con datos de ejemplo hasta
   conectarla con /costo/desviacion y /avance (T-214, T-093) y lo dice.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import { fechaCorte, type OrdenDesviacion } from '../api/costo'
import { usaApiReal } from '../api/http'
import { GastoRecursos } from './GastoRecursos'
import type { AvanceInsumo, ResumenTablero } from '../api/tipos'
import { Chip, Medidor, Vacio, type TonoChip } from '../ui/base'
import { Aviso, Chips, Encabezado, Kpi, Segmentado, TituloSeccion, Vidrio } from '../ui/piezas'
import { GraficoCurva, GraficoFamilias } from '../ui/GraficosPerezosos'
import { EsqueletoPagina } from '../ui/Esqueleto'

const soles = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 0 })
const FAM_COLOR: Record<string, string> = {
  materiales: 'var(--fam-materiales)',
  herramientas: 'var(--fam-herramientas)',
  horas_hombre: 'var(--fam-horas)',
}
const tonoDesvio = (p: number): TonoChip => (p <= 1 ? 'bien' : p <= 4 ? 'aviso' : 'grave')
const colorDesvio = (p: number) => (p <= 1 ? 'var(--bien)' : p <= 4 ? 'var(--aviso)' : 'var(--grave)')
const pts = (p: number) => `${p > 0 ? '+' : ''}${p.toLocaleString('es-PE', { maximumFractionDigits: 1 })} pt`
const signoSoles = (v: number) => `${v > 0 ? '+' : v < 0 ? '−' : ''}S/ ${Math.abs(v).toLocaleString('es-PE', { maximumFractionDigits: 0 })}`

export function Tablero() {
  const [r, setR] = useState<ResumenTablero | null>(null)
  const [soloDesviados, setSoloDesviados] = useState(false)
  const [orden, setOrden] = useState<OrdenDesviacion>('importe')
  useEffect(() => {
    // «sigro-panel-listo»: la entrada a la obra (App.tsx) espera a que el panel tenga sus cifras para mostrarse
    api.resumenTablero(orden).then(setR).finally(() => window.dispatchEvent(new Event('sigro-panel-listo')))
  }, [orden])

  if (!r) {
    return (
      <>
        <EsqueletoPagina />
      </>
    )
  }

  // solo presentación: totales y desviación global a partir de lo que devolvió el servidor
  const planificado = r.familias.reduce((s, f) => s + f.planificado, 0)
  const gastoPct = planificado > 0 ? (r.gastoTotal / planificado) * 100 : 0
  const desvioGlobal = Math.round((gastoPct - r.avancePlazoPct) * 10) / 10
  const conDesviacionReal = Boolean(r.corte)
  const filas: AvanceInsumo[] = soloDesviados
    ? r.insumos.filter((f) => f.lectura === 'sobre_lo_esperado' || f.desviacionPuntos > 1)
    : r.insumos
  const pct1 = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 1 })

  return (
    <>
      <div className="d-flex flex-column gap-3 pb-4">
        <Encabezado
          titulo="Gasto en recursos"
          sub={usaApiReal
            ? 'Cuánto se compró, se recibió, se consumió y queda en stock en la obra, al mismo corte y con su definición.'
            : `Presupuesto 2701006 del S10 · semana ${r.semana} de ${r.semanasTotales} · lo consumido contra lo planificado en el reporte de recursos`}
          acciones={<Link to="/compras" className="btn btn-primary">+ Registrar compra</Link>}
        />

        {usaApiReal ? (
          <>
            <GastoRecursos />
            <Chips>
              {conDesviacionReal ? (
                <>
                  <Chip tono="bien">Desviación real · corte {fechaCorte(r.corte ?? null)}</Chip>
                  <Chip tono="neutro">Avance del plazo: {pct1(r.avancePlazoPct)} %</Chip>
                  {r.desviacionTotalImporte !== undefined && (
                    <Chip tono={r.desviacionTotalImporte > 0 ? 'aviso' : 'bien'}>
                      Desviación acumulada: {signoSoles(r.desviacionTotalImporte)}
                      {r.desviacionTotalPct !== null && r.desviacionTotalPct !== undefined ? ` (${r.desviacionTotalPct > 0 ? '+' : ''}${pct1(r.desviacionTotalPct)} %)` : ''}
                    </Chip>
                  )}
                </>
              ) : (
                <Chip tono="neutro">Comparación de desviación por insumo no disponible para este perfil</Chip>
              )}
            </Chips>
            {r.avisoPlanificado && (
              <Aviso tono="info">
                <strong>{r.avisoPlanificado}</strong>{r.supuesto ? ` ${r.supuesto}` : ''}
              </Aviso>
            )}
          </>
        ) : (
          <>
          <Chips>
            <Chip tono="info">Consumo y compras: datos de ejemplo · lo planificado es el S10 real</Chip>
            <Chip tono="neutro">Avance del plazo: {pct1(r.avancePlazoPct)} %</Chip>
          </Chips>
  
          <Vidrio estilo={{ borderLeft: '4px solid var(--accion)', padding: '14px 20px' }} etiqueta="Alcance del reporte">
            <p className="text-body-secondary">
              <strong>Esto es el gasto en recursos, no el costo total de la obra.</strong>{' '}
              Incluye material, herramientas y horas hombre. No incluye gastos generales,
              subcontratos, servicios ni planilla. Y lo planificado ya incluye el desperdicio:
              excederlo avisa, nunca bloquea.
            </p>
          </Vidrio>
  
          <div className="d-flex flex-wrap gap-3">
            <Kpi ayuda="planificado" rotulo="Planificado en recursos" prefijo="S/" valor={soles(planificado)} pie="Materiales, herramientas y horas hombre del S10" />
            <Kpi rotulo="Gastado a la fecha" prefijo="S/" valor={soles(r.gastoTotal)} pie={`${pct1(gastoPct)} % de lo planificado`} />
            <Kpi
              ayuda="desviación"
              rotulo="Desviación del gasto" valor={pts(desvioGlobal)} color={colorDesvio(desvioGlobal)}
              pie={`Gasto ${pct1(gastoPct)} % frente a ${pct1(r.avancePlazoPct)} % del plazo`}
            />
            <Kpi ayuda="avance del plazo" rotulo="Avance del plazo" valor={`${pct1(r.avancePlazoPct)} %`} pie={`semana ${r.semana} de ${r.semanasTotales}`} />
          </div>
          </>
        )}

        <div className="d-flex flex-wrap gap-3 align-items-start">
          {!usaApiReal && <div className="d-flex flex-column gap-3 min-w-0"  style={{ flex: '999 1 520px' }}>
            <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 18 }} etiqueta="Por familia de recurso">
              <div className="d-flex flex-wrap justify-content-between gap-2 align-items-baseline">
                <TituloSeccion>Por familia de recurso</TituloSeccion>
                <span className="small text-body-secondary">La línea negra marca el avance del plazo</span>
              </div>
              {r.familias.map((fam) => (
                <div key={fam.familia} className="d-flex flex-column gap-2">
                  <div className="d-flex flex-wrap justify-content-between gap-2">
                    <span className="d-flex align-items-center gap-2 fw-bold">
                      <span aria-hidden className="rounded-2" style={{ width: 10, height: 10, background: FAM_COLOR[fam.familia] }} />
                      {fam.etiqueta}
                    </span>
                    <span className="n text-body-secondary">
                      <b>S/ {soles(fam.gastado)}</b> de S/ {soles(fam.planificado)} · {pct1(fam.pct)} %
                    </span>
                  </div>
                  <BarraPlazo pct={fam.pct} plazo={r.avancePlazoPct} color={FAM_COLOR[fam.familia]} />
                </div>
              ))}
              <p className="small text-body-secondary">
                Subcontratos, gastos generales, servicios y planilla quedan fuera: SIGRO informa el gasto en recursos, no el costo total de la obra.
              </p>
            </Vidrio>

            {/* Gráficos (Chart.js): las mismas cifras de arriba, para leerlas de un vistazo */}
            <div className="row g-3">
              <div className="col-12">
                <Vidrio etiqueta="Planificado contra gastado">
                  <TituloSeccion>Planificado contra gastado</TituloSeccion>
                  <GraficoFamilias familias={r.familias.map((f) => ({ etiqueta: f.etiqueta, color: FAM_COLOR[f.familia], planificado: f.planificado, gastado: f.gastado }))} />
                </Vidrio>
              </div>
              <div className="col-12">
                <Vidrio etiqueta="Gasto esperado y real">
                  <TituloSeccion>Gasto esperado en el plazo y dónde estamos</TituloSeccion>
                  <GraficoCurva planificado={planificado} gastado={r.gastoTotal} semana={r.semana} semanasTotales={r.semanasTotales} />
                </Vidrio>
              </div>
            </div>

          </div>}

          <aside className="d-flex flex-column gap-3 min-w-0"  style={{ flex: '1 1 280px' }}>
            {!usaApiReal && <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 10 }} etiqueta="Plazo de la obra">
              <TituloSeccion>Plazo de la obra</TituloSeccion>
              <div
                role="img" aria-label={`Avance del plazo: ${r.avancePlazoPct} por ciento`}
                className="overflow-hidden rounded-2"  style={{ height: 12, background: 'var(--separador)' }}
              >
                <span className="medidor-barra" style={{ display: 'block', width: `${Math.min(r.avancePlazoPct, 100)}%`, height: 12, background: 'var(--pildora)', borderRadius: 6 }} />
              </div>
              <span className="n text-body-secondary">
                Semana {r.semana} de {r.semanasTotales} · {pct1(r.avancePlazoPct)} % del plazo
              </span>
            </Vidrio>}

            <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 10 }} etiqueta="No planificados">
              <TituloSeccion>No planificados · aparte</TituloSeccion>
              {r.noPlanificados.length === 0 && <span className="small text-body-secondary">Ninguno por ahora.</span>}
              {r.noPlanificados.map((np) => (
                <div key={np.descripcion} className="d-flex flex-wrap justify-content-between bg-body-tertiary rounded-4 py-2 px-3"  style={{ gap: '4px 10px' }}>
                  <span className="d-flex flex-column gap-1 min-w-0"  style={{ flex: '1 1 160px' }}>
                    <span className="fw-bold">{np.descripcion}</span>
                    <span className="small text-body-secondary">{np.motivo}</span>
                  </span>
                  <span className="n fw-bold text-nowrap">S/ {soles(np.gastado)}</span>
                </div>
              ))}
              <p className="small text-body-secondary">
                Se informan aparte para no distorsionar la desviación de los planificados.
              </p>
            </Vidrio>
          </aside>
        </div>

        <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Insumos y su desviación">
          <div className="d-flex flex-wrap justify-content-between gap-2 align-items-center">
            <TituloSeccion>Insumos y su desviación</TituloSeccion>
            <div className="d-flex flex-wrap gap-2 align-items-center">
              {conDesviacionReal && (
                <label className="d-flex align-items-center gap-2 small fw-semibold text-body-secondary">
                  Ordenar
                  <select
                    aria-label="Ordenar desviación por"
                    className="form-select small"
                    value={orden}
                    onChange={(e) => setOrden(e.target.value as OrdenDesviacion)}
                     style={{ height: 36 }}
                  >
                    <option value="importe">Mayor desviación S/</option>
                    <option value="porcentaje">Mayor desviación %</option>
                    <option value="cantidad">Mayor desviación cantidad</option>
                    <option value="codigo">Código SKU</option>
                  </select>
                </label>
              )}
              <Segmentado
                etiqueta="Filtro de insumos" valor={soloDesviados} enCambio={setSoloDesviados}
                opciones={[{ valor: false, texto: 'Todos' }, { valor: true, texto: 'Solo desviados' }]}
              />
            </div>
          </div>
          {r.insumos.length === 0 ? (
            <Vacio titulo="Sin requerimiento todavía" detalle="Importa la Relación de insumos del S10 para comparar lo gastado contra lo planificado." />
          ) : (
            <div className="overflow-x-auto">
              <table className="table table-hover align-middle mb-0" style={{ minWidth: 1080 }}>
                <thead>
                  <tr>
                    <th>Insumo</th>
                    <th className="n">Planificado S/</th>
                    {conDesviacionReal && <th className="n">Esperado S/</th>}
                    <th className="n">Requerido</th>
                    <th className="n">Comprado</th>
                    <th className="n">Recibido</th>
                    <th className="n">Consumido</th>
                    <th className="n">Falta comprar</th>
                    <th className="n">Gastado S/</th>
                    <th className="n">Desviación</th>
                  </tr>
                </thead>
                <tbody>
                  {filas.map((f) => (
                    <tr key={f.insumoId}>
                      <td style={{ minWidth: 220 }}>
                        <div className="fw-semibold">{f.descripcion}</div>
                        <div className="n small text-body-secondary mt-1 text-start">
                          SKU {f.codigo}{conDesviacionReal && f.unidad ? ` · ${f.consumidoCantidad?.toLocaleString('es-PE', { maximumFractionDigits: 2 }) ?? 0} ${f.unidad} consumidos` : ''}
                        </div>
                      </td>
                      <td className="n">{soles(f.planificadoImporte)}</td>
                      {conDesviacionReal && <td className="n">{soles(f.esperadoImporte ?? 0)}</td>}
                      <td className="n text-nowrap">
                        {f.requeridoCompra !== undefined ? `${f.requeridoCompra.toLocaleString('es-PE', { maximumFractionDigits: 2 })} ${f.unidadCompra}` : '—'}
                      </td>
                      <td className="n text-nowrap">
                        {f.compradoCantidad.toLocaleString('es-PE', { maximumFractionDigits: 2 })} {f.unidadCompra}
                        <div className="small text-body-secondary">{f.compradoPct} %</div>
                      </td>
                      <td className="n">{f.sinDatoAlmacen ? '—' : `${f.recibidoPct} %`}</td>
                      <td>
                        {f.sinDatoAlmacen ? (
                          <div className="n text-body-secondary">—</div>
                        ) : (
                          <div className="d-flex align-items-center gap-2 justify-content-end">
                            <Medidor pct={f.consumidoPct} marcaPct={r.avancePlazoPct} color={colorDesvio(f.desviacionPuntos)} ancho={88} />
                            <span className="n text-end" style={{ width: 52 }}>{f.consumidoPct} %</span>
                          </div>
                        )}
                      </td>
                      <td className="n text-nowrap">{f.saldoPorComprar.toLocaleString('es-PE')} {f.unidadCompra}</td>
                      <td className="n fw-bold">{soles(f.gastado)}</td>
                      <td className="n">
                        <div className="d-flex flex-column align-items-end gap-1">
                          <Chip tono={tonoDesvio(f.desviacionPuntos)}>{pts(f.desviacionPuntos)}</Chip>
                          {f.desviacionImporte !== undefined && (
                            <span className="small text-body-secondary text-nowrap">
                              {signoSoles(f.desviacionImporte)}
                              {f.efectoCantidad !== undefined ? ` (cant ${signoSoles(f.efectoCantidad)})` : ''}
                            </span>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="small text-body-secondary">
            Comprado, recibido y consumido se expresan en porcentaje de lo requerido en el S10, en la unidad de compra (HU-23):
            se pueden cuadrar a mano con la columna «Requerido». La marca del medidor es el avance del plazo ({pct1(r.avancePlazoPct)} %): un consumo por encima se está adelantando a la obra.
            Lo planificado incluye desperdicio: pasarse avisa, no bloquea.
          </p>
        </Vidrio>
      </div>
    </>
  )
}

/** Barra de la familia: lo gastado contra lo planificado, con la marca del avance del plazo. */
function BarraPlazo({ pct, plazo, color }: { pct: number; plazo: number; color: string }) {
  return (
    <div
      role="img" aria-label={`${pct} por ciento de lo planificado; plazo al ${plazo} por ciento`}
      className="rounded-3" style={{ height: 14, background: 'var(--separador)', position: 'relative' }}
    >
      <span className="medidor-barra" style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${Math.min(pct, 100)}%`, background: color, borderRadius: 7 }} />
      <span title="Avance del plazo" style={{ position: 'absolute', left: `${Math.min(plazo, 100)}%`, top: -3, bottom: -3, width: 2, background: 'var(--tinta)' }} />
    </div>
  )
}
