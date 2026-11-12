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
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { AvanceInsumo, ResumenTablero } from '../api/tipos'
import { Chip, Medidor, Vacio, type TonoChip } from '../ui/base'
import { Chips, Encabezado, Fondo, Kpi, Segmentado, TituloSeccion, Vidrio, estiloPildora } from '../ui/vidrio'

const soles = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 0 })
const FAM_COLOR: Record<string, string> = {
  materiales: 'var(--fam-materiales)',
  herramientas: 'var(--fam-herramientas)',
  horas_hombre: 'var(--fam-horas)',
}
const tonoDesvio = (p: number): TonoChip => (p <= 1 ? 'bien' : p <= 4 ? 'aviso' : 'grave')
const colorDesvio = (p: number) => (p <= 1 ? 'var(--bien)' : p <= 4 ? 'var(--aviso)' : 'var(--grave)')
const pts = (p: number) => `${p > 0 ? '+' : ''}${p.toLocaleString('es-PE', { maximumFractionDigits: 1 })} pt`

export function Tablero() {
  const [r, setR] = useState<ResumenTablero | null>(null)
  const [soloDesviados, setSoloDesviados] = useState(false)
  useEffect(() => {
    // «sigro-panel-listo»: la entrada a la obra (App.tsx) espera a que el panel tenga sus cifras para mostrarse
    api.resumenTablero().then(setR).finally(() => window.dispatchEvent(new Event('sigro-panel-listo')))
  }, [])

  if (!r) {
    return (
      <>
        <Fondo dibujo="panel" />
        <Vidrio><Vacio titulo="Cargando…" detalle="Consultando el gasto en recursos de la obra." /></Vidrio>
      </>
    )
  }

  // solo presentación: totales y desviación global a partir de lo que devolvió el servidor
  const planificado = r.familias.reduce((s, f) => s + f.planificado, 0)
  const gastoPct = planificado > 0 ? (r.gastoTotal / planificado) * 100 : 0
  const desvioGlobal = Math.round((gastoPct - r.avancePlazoPct) * 10) / 10
  const filas: AvanceInsumo[] = soloDesviados ? r.insumos.filter((f) => f.desviacionPuntos > 1) : r.insumos
  const pct1 = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 1 })

  return (
    <>
      <Fondo dibujo="panel" />
      <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Encabezado
          titulo="Gasto en recursos"
          sub={`Presupuesto 2701006 del S10 · semana ${r.semana} de ${r.semanasTotales} · lo consumido contra lo planificado en el reporte de recursos`}
          acciones={<Link to="/compras" className="foco-vidrio" style={estiloPildora()}>+ Registrar compra</Link>}
        />

        <Chips>
          <Chip tono="info">Consumo y compras: datos de ejemplo · lo planificado es el S10 real</Chip>
          <Chip tono="neutro">Avance del plazo: {pct1(r.avancePlazoPct)} %</Chip>
        </Chips>

        <Vidrio estilo={{ borderLeft: '4px solid var(--accion)', padding: '14px 20px' }} etiqueta="Alcance del reporte">
          <p style={{ fontSize: 14, color: 'var(--tinta-2)', lineHeight: 1.55 }}>
            <strong style={{ color: 'var(--tinta)' }}>Esto es el gasto en recursos, no el costo total de la obra.</strong>{' '}
            Incluye material, herramientas y horas hombre. No incluye gastos generales,
            subcontratos, servicios ni planilla. Y lo planificado ya incluye el desperdicio:
            excederlo avisa, nunca bloquea.
          </p>
        </Vidrio>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14 }}>
          <Kpi rotulo="Planificado en recursos" prefijo="S/" valor={soles(planificado)} pie="Materiales, herramientas y horas hombre del S10" />
          <Kpi rotulo="Gastado a la fecha" prefijo="S/" valor={soles(r.gastoTotal)} pie={`${pct1(gastoPct)} % de lo planificado`} />
          <Kpi
            rotulo="Desviación del gasto" valor={pts(desvioGlobal)} color={colorDesvio(desvioGlobal)}
            pie={`Gasto ${pct1(gastoPct)} % frente a ${pct1(r.avancePlazoPct)} % del plazo`}
          />
          <Kpi rotulo="Avance del plazo" valor={`${pct1(r.avancePlazoPct)} %`} pie={`semana ${r.semana} de ${r.semanasTotales}`} />
        </div>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, alignItems: 'flex-start' }}>
          <div style={{ flex: '999 1 520px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 18 }}>
            <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 18 }} etiqueta="Por familia de recurso">
              <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', gap: 8, alignItems: 'baseline' }}>
                <TituloSeccion>Por familia de recurso</TituloSeccion>
                <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>La línea negra marca el avance del plazo</span>
              </div>
              {r.familias.map((fam) => (
                <div key={fam.familia} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', gap: 8, fontSize: 14 }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700 }}>
                      <span aria-hidden style={{ width: 10, height: 10, borderRadius: 5, background: FAM_COLOR[fam.familia] }} />
                      {fam.etiqueta}
                    </span>
                    <span className="n" style={{ color: 'var(--tinta-2)' }}>
                      <b style={{ color: 'var(--tinta)' }}>S/ {soles(fam.gastado)}</b> de S/ {soles(fam.planificado)} · {pct1(fam.pct)} %
                    </span>
                  </div>
                  <BarraPlazo pct={fam.pct} plazo={r.avancePlazoPct} color={FAM_COLOR[fam.familia]} />
                </div>
              ))}
              <p style={{ fontSize: 13, color: 'var(--tinta-2)' }}>
                Subcontratos, gastos generales, servicios y planilla quedan fuera: SIGRO informa el gasto en recursos, no el costo total de la obra.
              </p>
            </Vidrio>

          </div>

          <aside style={{ flex: '1 1 280px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 18 }}>
            <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 10 }} etiqueta="Plazo de la obra">
              <TituloSeccion>Plazo de la obra</TituloSeccion>
              <div
                role="img" aria-label={`Avance del plazo: ${r.avancePlazoPct} por ciento`}
                style={{ height: 12, borderRadius: 6, background: 'var(--separador)', overflow: 'hidden' }}
              >
                <span className="medidor-barra" style={{ display: 'block', width: `${Math.min(r.avancePlazoPct, 100)}%`, height: 12, background: 'var(--pildora)', borderRadius: 6 }} />
              </div>
              <span className="n" style={{ fontSize: 14, color: 'var(--tinta-2)' }}>
                Semana {r.semana} de {r.semanasTotales} · {pct1(r.avancePlazoPct)} % del plazo
              </span>
            </Vidrio>

            <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 10 }} etiqueta="No planificados">
              <TituloSeccion>No planificados · aparte</TituloSeccion>
              {r.noPlanificados.length === 0 && <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>Ninguno por ahora.</span>}
              {r.noPlanificados.map((np) => (
                <div key={np.descripcion} style={{ background: 'var(--vidrio-celda)', borderRadius: 16, padding: '12px 14px', display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', gap: '4px 10px' }}>
                  <span style={{ flex: '1 1 160px', display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
                    <span style={{ fontSize: 14, fontWeight: 700 }}>{np.descripcion}</span>
                    <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>{np.motivo}</span>
                  </span>
                  <span className="n" style={{ fontSize: 14, fontWeight: 800, whiteSpace: 'nowrap' }}>S/ {soles(np.gastado)}</span>
                </div>
              ))}
              <p style={{ fontSize: 13, color: 'var(--tinta-2)' }}>
                Se informan aparte para no distorsionar la desviación de los planificados.
              </p>
            </Vidrio>
          </aside>
        </div>

        <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Insumos y su desviación">
          <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', gap: 10, alignItems: 'center' }}>
            <TituloSeccion>Insumos y su desviación</TituloSeccion>
            <Segmentado
              etiqueta="Filtro de insumos" valor={soloDesviados} enCambio={setSoloDesviados}
              opciones={[{ valor: false, texto: 'Todos' }, { valor: true, texto: 'Solo desviados' }]}
            />
          </div>
          {r.insumos.length === 0 ? (
            <Vacio titulo="Sin requerimiento todavía" detalle="Importa la Relación de insumos del S10 para comparar lo gastado contra lo planificado." />
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="tabla-vidrio" style={{ minWidth: 900 }}>
                <thead>
                  <tr>
                    <th>Insumo</th><th className="n">Planificado S/</th><th className="n">Comprado</th><th className="n">Recibido</th>
                    <th className="n">Consumido</th><th className="n">Falta comprar</th><th className="n">Gastado S/</th><th className="n">Desviación</th>
                  </tr>
                </thead>
                <tbody>
                  {filas.map((f) => (
                    <tr key={f.insumoId}>
                      <td style={{ minWidth: 220 }}>
                        <div style={{ fontWeight: 600 }}>{f.descripcion}</div>
                        <div className="n" style={{ fontSize: 12, color: 'var(--tinta-2)', marginTop: 2, textAlign: 'left' }}>SKU {f.codigo}</div>
                      </td>
                      <td className="n">{soles(f.planificadoImporte)}</td>
                      <td className="n">{f.compradoPct} %</td>
                      <td className="n">{f.recibidoPct} %</td>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 9, justifyContent: 'flex-end' }}>
                          <Medidor pct={f.consumidoPct} marcaPct={r.avancePlazoPct} color={colorDesvio(f.desviacionPuntos)} ancho={88} />
                          <span className="n" style={{ width: 52, textAlign: 'right' }}>{f.consumidoPct} %</span>
                        </div>
                      </td>
                      <td className="n" style={{ whiteSpace: 'nowrap' }}>{f.saldoPorComprar.toLocaleString('es-PE')} {f.unidadCompra}</td>
                      <td className="n" style={{ fontWeight: 700 }}>{soles(f.gastado)}</td>
                      <td className="n"><Chip tono={tonoDesvio(f.desviacionPuntos)}>{pts(f.desviacionPuntos)}</Chip></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p style={{ fontSize: 13, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
            La marca del medidor es el avance del plazo ({pct1(r.avancePlazoPct)} %): un consumo por encima se está adelantando a la obra.
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
      style={{ height: 14, borderRadius: 7, background: 'var(--separador)', position: 'relative' }}
    >
      <span className="medidor-barra" style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${Math.min(pct, 100)}%`, background: color, borderRadius: 7 }} />
      <span title="Avance del plazo" style={{ position: 'absolute', left: `${Math.min(plazo, 100)}%`, top: -3, bottom: -3, width: 2, background: 'var(--tinta)' }} />
    </div>
  )
}
