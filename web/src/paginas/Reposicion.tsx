/* ─────────────────────────────────────────────────────────────────────────────
   HU-24 · Como residente de obra, quiero consultar cuánto falta comprar de cada
           insumo, para formular el siguiente requerimiento sobre una base cierta.

   Criterios de la tarjeta:
   - Falta por comprar = requerido − comprado → columna central, y lo calcula la
     capa de API (la UI no hace aritmética de negocio).
   - Se puede filtrar por fase y por insumo crítico → chips y selector.
   - La consulta se exporta para armar el pedido → botón que baja un CSV con lo
     filtrado, listo para convertirse en la solicitud de materiales (HU-66).
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/cliente'
import type { AvanceInsumo } from '../api/tipos'
import { Boton, Chip, Tarjeta, Titulo, Vacio } from '../ui/base'

const n2 = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 0 })

export function Reposicion() {
  const [filas, setFilas] = useState<AvanceInsumo[] | null>(null)
  const [soloCriticos, setSoloCriticos] = useState(false)
  const [fase, setFase] = useState('todas')

  useEffect(() => {
    api.avancePorInsumo().then(setFilas)
  }, [])

  const fases = useMemo(
    () => Array.from(new Set((filas ?? []).map((f) => f.fase))),
    [filas],
  )
  const visibles = useMemo(() => (filas ?? [])
    .filter((f) => (!soloCriticos || f.critico) && (fase === 'todas' || f.fase === fase))
    .filter((f) => f.saldoPorComprar > 0), [filas, soloCriticos, fase])

  // seguridad: un texto que empieza con = + - @ se neutraliza con apóstrofo,
  // para que Excel no lo ejecute como fórmula al abrir el CSV
  const celdaSegura = (v: string) => `"${(/^[=+\-@]/.test(v) ? `'${v}` : v).replace(/"/g, '""')}"`

  const exportar = () => {
    const cab = 'codigo,insumo,fase,critico,unidad_compra,requerido,comprado,falta_por_comprar'
    const cuerpo = visibles.map((f) =>
      [f.codigo, celdaSegura(f.descripcion), f.fase, f.critico ? 'si' : 'no', f.unidadCompra,
        f.planificadoCantidad, f.compradoCantidad, f.saldoPorComprar].join(','))
    const blob = new Blob([[cab, ...cuerpo].join('\n')], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `saldo-por-comprar-${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
  }

  const chipFiltro = (activo: boolean, texto: string, alClic: () => void) => (
    <button
      type="button"
      onClick={alClic}
      style={{
        border: `1px solid ${activo ? 'var(--accion)' : 'var(--borde)'}`,
        background: activo ? 'var(--marca-tinte)' : 'var(--tarjeta)',
        color: activo ? 'var(--marca-tinte-tinta)' : 'var(--tinta-2)',
        borderRadius: 'var(--radio-pildora)', padding: '8px 14px',
        fontSize: 13, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit',
      }}
    >
      {texto}
    </button>
  )

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
        <Titulo sub="Falta por comprar = requerido − comprado. La base cierta para formular la siguiente solicitud de materiales.">
          Saldo por comprar
        </Titulo>
        <Boton enClic={exportar} deshabilitado={!visibles.length}>Exportar para el pedido</Boton>
      </div>

      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        {chipFiltro(!soloCriticos, 'Todos', () => setSoloCriticos(false))}
        {chipFiltro(soloCriticos, 'Solo críticos', () => setSoloCriticos(true))}
        <select
          aria-label="Filtrar por fase"
          value={fase}
          onChange={(e) => setFase(e.target.value)}
          style={{
            height: 37, border: '1px solid var(--borde)', borderRadius: 'var(--radio-pildora)',
            padding: '0 14px', fontSize: 13, fontWeight: 600, fontFamily: 'inherit',
            background: 'var(--tarjeta)', color: 'var(--tinta-2)',
          }}
        >
          <option value="todas">Todas las fases</option>
          {fases.map((f) => <option key={f} value={f}>{f}</option>)}
        </select>
      </div>

      <Tarjeta relleno={false}>
        {filas === null ? (
          <Vacio titulo="Cargando…" detalle="Calculando el saldo por comprar de cada insumo." />
        ) : visibles.length === 0 ? (
          <Vacio titulo="Nada pendiente con este filtro" detalle="Todos los insumos filtrados ya tienen su compra completa." />
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: 'var(--tarjeta-suave)', borderBottom: '1px solid var(--borde)' }}>
                {['Insumo', 'Fase', 'Requerido', 'Comprado', 'Falta por comprar'].map((h, i) => (
                  <th key={h} style={{ padding: '11px 18px', fontSize: 11, fontWeight: 600, color: 'var(--tinta-3)', letterSpacing: 0.4, textAlign: i >= 2 ? 'right' : 'left' }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {visibles.map((f, i) => (
                <tr key={f.insumoId} style={{ borderBottom: i === visibles.length - 1 ? 'none' : '1px solid var(--linea)' }}>
                  <td style={{ padding: '12px 18px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--tinta)' }}>{f.descripcion}</span>
                      {f.critico && <Chip tono="aviso">crítico</Chip>}
                    </div>
                    <div className="n" style={{ fontSize: 11.5, color: 'var(--tinta-3)', marginTop: 2 }}>SKU {f.codigo}</div>
                  </td>
                  <td style={{ padding: '12px 18px', fontSize: 13, color: 'var(--tinta-2)' }}>{f.fase}</td>
                  <td className="n" style={{ padding: '12px 18px', fontSize: 13, color: 'var(--tinta-2)', textAlign: 'right' }}>
                    {n2(f.planificadoCantidad)} {f.unidadS10}
                  </td>
                  <td className="n" style={{ padding: '12px 18px', fontSize: 13, color: 'var(--tinta-2)', textAlign: 'right' }}>
                    {n2(f.compradoCantidad)} {f.unidadS10} · {f.compradoPct} %
                  </td>
                  <td className="n" style={{ padding: '12px 18px', fontSize: 14, fontWeight: 700, color: 'var(--tinta)', textAlign: 'right' }}>
                    {n2(f.saldoPorComprar)} {f.unidadCompra}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Tarjeta>

      <p style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>
        El faltante se muestra en la <strong>unidad de compra</strong>: el acero se pide en
        varillas aunque el S10 lo planifique en kilos (factor de conversión del catálogo).
        La cantidad sugerida con stock de seguridad vive en la alerta de reposición (HU-27).
      </p>
    </div>
  )
}
