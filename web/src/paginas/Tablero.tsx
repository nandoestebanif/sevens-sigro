/* ─────────────────────────────────────────────────────────────────────────────
   HU-57 · Como gerente, quiero comparar el consumo valorizado de cada insumo
           contra lo que se planificó para la obra, para detectar la desviación
           de costo mientras la obra corre.
   HU-23 · Como residente, quiero consultar qué porcentaje de cada insumo se
           compró y se consumió, para distinguir lo que ingresó de lo que se usó.

   Criterios cubiertos:
   - Compara contra cantidad y costo planificados del reporte de recursos REAL.
   - Desviación visible en puntos y con su chip; medidor contra el avance del plazo.
   - Se muestran requerido, comprado, recibido, consumido y saldo por comprar (HU-23).
   - Los insumos no planificados se informan aparte y no tocan la desviación.
   - El reporte declara su alcance (gasto en recursos ≠ costo total) y advierte
     que lo planificado incluye desperdicio: exceder avisa, no bloquea.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useState } from 'react'
import { api } from '../api/cliente'
import type { ResumenTablero } from '../api/tipos'
import { Chip, Medidor, Tarjeta, Titulo, Vacio, type TonoChip } from '../ui/base'

const soles = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 0 })
const FAM_COLOR: Record<string, string> = {
  materiales: 'var(--fam-materiales)',
  herramientas: 'var(--fam-herramientas)',
  horas_hombre: 'var(--fam-horas)',
}
const tonoDesvio = (p: number): TonoChip => (p <= 1 ? 'bien' : p <= 4 ? 'aviso' : 'grave')
const colorDesvio = (p: number) => (p <= 1 ? 'var(--bien)' : p <= 4 ? 'var(--aviso)' : 'var(--grave)')

function Kpi({ rot, soles: conSoles, valor, pie, color }: {
  rot: string; soles?: boolean; valor: string; pie: string; color?: string
}) {
  return (
    <Tarjeta estilo={{ flex: 1, padding: '15px 16px' }}>
      <div style={{ fontSize: 12, color: 'var(--tinta-3)', fontWeight: 500 }}>{rot}</div>
      <div style={{ marginTop: 7, display: 'flex', alignItems: 'baseline' }}>
        {conSoles && <span style={{ fontSize: 13, color: 'var(--tinta-3)', fontWeight: 600, marginRight: 4 }}>S/</span>}
        <span className="n" style={{ fontSize: 26, fontWeight: 700, letterSpacing: '-1px', color: color ?? 'var(--tinta)' }}>{valor}</span>
      </div>
      <div style={{ fontSize: 11.5, color: 'var(--tinta-3)', marginTop: 5 }}>{pie}</div>
    </Tarjeta>
  )
}

export function Tablero() {
  const [r, setR] = useState<ResumenTablero | null>(null)
  useEffect(() => {
    api.resumenTablero().then(setR)
  }, [])

  if (!r) {
    return <Tarjeta><Vacio titulo="Cargando…" detalle="Consultando el gasto en recursos de la obra." /></Tarjeta>
  }

  const th = (texto: string, der = false) => (
    <th style={{ padding: '11px 14px', fontSize: 11, fontWeight: 600, color: 'var(--tinta-3)', letterSpacing: 0.4, textAlign: der ? 'right' : 'left' }}>{texto}</th>
  )

  return (
    <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
      <Titulo sub={`Presupuesto 2701006 · semana ${r.semana} de ${r.semanasTotales} · lo consumido contra lo planificado en el reporte de recursos del S10`}>
        Gasto en recursos de la obra
      </Titulo>

      <Tarjeta estilo={{ padding: '12px 16px', borderLeft: '4px solid var(--marca)' }}>
        <span style={{ fontSize: 12.5, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
          <strong style={{ color: 'var(--tinta)' }}>Esto es el gasto en recursos, no el costo total de la obra.</strong>{' '}
          Incluye material, herramientas y horas hombre. No incluye gastos generales,
          subcontratos, servicios ni planilla. Y lo planificado ya incluye el desperdicio:
          excederlo avisa, nunca bloquea.
        </span>
      </Tarjeta>

      <div style={{ display: 'flex', gap: 14 }}>
        <Kpi rot="Gasto en recursos" soles valor={soles(r.gastoTotal)} pie="al 22 de octubre" />
        <Kpi rot="Materiales" soles valor={soles(r.familias[0].gastado)} pie={`${r.familias[0].pct} % de lo planificado`} color="var(--aviso)" />
        <Kpi rot="Horas hombre" soles valor={soles(r.familias[2].gastado)} pie={`${r.familias[2].pct} % de lo planificado`} color="var(--bien)" />
        <Kpi rot="Avance del plazo" valor={`${r.avancePlazoPct} %`} pie={`semana ${r.semana} de ${r.semanasTotales}`} />
      </div>

      <Tarjeta relleno={false}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: 'var(--tarjeta-suave)', borderBottom: '1px solid var(--borde)' }}>
              {th('Insumo')}
              {th('Planificado S/', true)}
              {th('Comprado', true)}
              {th('Recibido', true)}
              {th('Consumido', true)}
              {th('Falta comprar', true)}
              {th('Gastado S/', true)}
              {th('Desviación', true)}
            </tr>
          </thead>
          <tbody>
            {r.insumos.map((f, i) => (
              <tr key={f.insumoId} style={{ borderBottom: i === r.insumos.length - 1 ? 'none' : '1px solid var(--linea)' }}>
                <td style={{ padding: '12px 14px' }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--tinta)' }}>{f.descripcion}</div>
                  <div className="n" style={{ fontSize: 11, color: 'var(--tinta-3)', marginTop: 2 }}>SKU {f.codigo}</div>
                </td>
                <td className="n" style={{ padding: '12px 14px', fontSize: 12.5, color: 'var(--tinta-2)', textAlign: 'right' }}>{soles(f.planificadoImporte)}</td>
                <td className="n" style={{ padding: '12px 14px', fontSize: 12.5, color: 'var(--tinta-2)', textAlign: 'right' }}>{f.compradoPct} %</td>
                <td className="n" style={{ padding: '12px 14px', fontSize: 12.5, color: 'var(--tinta-2)', textAlign: 'right' }}>{f.recibidoPct} %</td>
                <td style={{ padding: '12px 14px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 9, justifyContent: 'flex-end' }}>
                    <Medidor pct={f.consumidoPct} marcaPct={r.avancePlazoPct} color={colorDesvio(f.desviacionPuntos)} ancho={96} />
                    <span className="n" style={{ fontSize: 12.5, color: 'var(--tinta-2)', width: 48, textAlign: 'right' }}>{f.consumidoPct} %</span>
                  </div>
                </td>
                <td className="n" style={{ padding: '12px 14px', fontSize: 12.5, color: 'var(--tinta-2)', textAlign: 'right' }}>
                  {f.saldoPorComprar.toLocaleString('es-PE')} {f.unidadCompra}
                </td>
                <td className="n" style={{ padding: '12px 14px', fontSize: 13, fontWeight: 700, textAlign: 'right' }}>{soles(f.gastado)}</td>
                <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                  <Chip tono={tonoDesvio(f.desviacionPuntos)}>
                    {f.desviacionPuntos > 0 ? '+' : ''}{f.desviacionPuntos} pt
                  </Chip>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div style={{ padding: '10px 14px', background: 'var(--tarjeta-suave)', borderTop: '1px solid var(--borde)', display: 'flex', gap: 16, fontSize: 11.5, color: 'var(--tinta-3)' }}>
          <span>▍ La marca del medidor es el avance del plazo: {r.avancePlazoPct} %</span>
          <span>Un consumo por encima de la marca se está adelantando a la obra</span>
        </div>
      </Tarjeta>

      <div style={{ display: 'flex', gap: 14, alignItems: 'stretch' }}>
        <Tarjeta estilo={{ flex: 1.6 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 12 }}>
            <span style={{ fontSize: 13.5, fontWeight: 700 }}>Gasto por familia de recurso</span>
            <span style={{ fontSize: 11.5, color: 'var(--tinta-3)' }}>la marca es el avance del plazo</span>
          </div>
          {r.familias.map((fam) => (
            <div key={fam.familia} style={{ display: 'flex', alignItems: 'center', gap: 13, padding: '7px 0' }}>
              <div style={{ width: 168, display: 'flex', alignItems: 'center', gap: 8, flex: 'none' }}>
                <span style={{ width: 9, height: 9, borderRadius: 99, background: FAM_COLOR[fam.familia] }} />
                <span style={{ fontSize: 13, fontWeight: 600 }}>{fam.etiqueta}</span>
              </div>
              <div style={{ flex: 1 }}>
                <Medidor pct={fam.pct} marcaPct={r.avancePlazoPct} color={FAM_COLOR[fam.familia]} ancho="100%" />
              </div>
              <span className="n" style={{ fontSize: 13, fontWeight: 700, width: 50, textAlign: 'right' }}>{fam.pct} %</span>
              <span className="n" style={{ fontSize: 11.5, color: 'var(--tinta-3)', width: 150, textAlign: 'right' }}>
                S/ {soles(fam.gastado)} de {soles(fam.planificado)}
              </span>
            </div>
          ))}
        </Tarjeta>

        <Tarjeta estilo={{ flex: 1 }}>
          <div style={{ fontSize: 13.5, fontWeight: 700, marginBottom: 10 }}>No planificados · aparte</div>
          {r.noPlanificados.map((np) => (
            <div key={np.descripcion} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, padding: '7px 0', borderTop: '1px solid var(--linea)' }}>
              <div>
                <div style={{ fontSize: 12.5, fontWeight: 600 }}>{np.descripcion}</div>
                <div style={{ fontSize: 11.5, color: 'var(--tinta-3)', marginTop: 2 }}>{np.motivo}</div>
              </div>
              <span className="n" style={{ fontSize: 13, fontWeight: 700, whiteSpace: 'nowrap' }}>S/ {soles(np.gastado)}</span>
            </div>
          ))}
          <p style={{ fontSize: 11.5, color: 'var(--tinta-3)', marginTop: 10 }}>
            Se informan aparte para no distorsionar la desviación de los planificados.
          </p>
        </Tarjeta>
      </div>
    </div>
  )
}
