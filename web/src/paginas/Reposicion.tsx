/* ─────────────────────────────────────────────────────────────────────────────
   HU-24 · Como residente de obra, quiero consultar cuánto falta comprar de cada
           insumo, para formular el siguiente requerimiento sobre una base cierta.

   Criterios de la tarjeta:
   - Falta por comprar = requerido − comprado → columna central, y lo calcula la
     capa de API (la UI no hace aritmética de negocio).
   - Se puede filtrar por fase y por insumo crítico → chips y selector.
   - La consulta se exporta para armar el pedido → botón que baja un CSV con lo
     filtrado, listo para convertirse en la solicitud de materiales (HU-66).
   Lienzo W5 «Reposición» (estilo vidrio).
   ───────────────────────────────────────────────────────────────────────── */
import { reposicionHttp, usaApiReal } from '../api/http'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import { ErrorApi, mensajeDeCarga } from '../api/error'
import type { AlertaReposicion, AvanceInsumo } from '../api/tipos'
import { Chip, Vacio } from '../ui/base'
import { Chips, Encabezado, Fondo, Pildora, Segmentado, TituloSeccion, Vidrio, estiloPildora } from '../ui/vidrio'

const n2 = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 0 })

/* ── HU-26 · HU-27: el aviso salta solo y nunca va solo — trae saldo, punto,
   falta por comprar y la sugerencia editable con su criterio ── */
function Alerta({ a }: { a: AlertaReposicion }) {
  // HU-27: referencia EDITABLE, nunca pedido automático
  const [cantidad, setCantidad] = useState(String(a.sugerencia))
  const activa = a.estado === 'activa'
  return (
    <Vidrio estilo={{ borderLeft: `4px solid ${activa ? 'var(--grave)' : 'var(--aviso)'}`, padding: '16px 20px' }} etiqueta={`Aviso de reposición: ${a.descripcion}`}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'baseline', flexWrap: 'wrap' }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <strong style={{ fontSize: 15 }}>{a.descripcion}</strong>
          {activa
            ? <Chip tono="grave">bajo el punto de reposición</Chip>
            : <Chip tono="aviso">atendida · compra {a.atendidaPor}</Chip>}
        </span>
        <span className="n" style={{ fontSize: 13.5, color: 'var(--tinta-2)' }}>
          saldo <strong>{n2(a.saldo)} {a.unidad}</strong> · punto {n2(a.punto)} · falta por comprar {n2(a.faltaPorComprar)}
        </span>
      </div>
      <div style={{ fontSize: 13, color: 'var(--tinta-2)', marginTop: 4 }}>
        punto configurado como {a.criterioPunto}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 10, flexWrap: 'wrap' }}>
        <label htmlFor={`sug-${a.insumoId}`} style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--tinta-2)' }}>
          Sugerencia de compra:
        </label>
        <input
          id={`sug-${a.insumoId}`}
          className="n campo"
          value={cantidad}
          onChange={(e) => setCantidad(e.target.value)}
          style={{ width: 110, height: 44, padding: '0 12px', fontWeight: 700, textAlign: 'right' }}
        />
        <span style={{ fontSize: 13.5, color: 'var(--tinta-2)' }}>{a.unidad}</span>
        <span style={{ fontSize: 13, color: 'var(--tinta-2)', flex: '1 1 260px' }}>
          referencia editable, nunca pedido automático · criterio: {a.criterioSugerencia}
        </span>
      </div>
    </Vidrio>
  )
}

export function Reposicion() {
  const [filas, setFilas] = useState<AvanceInsumo[] | null>(null)
  const [alertas, setAlertas] = useState<AlertaReposicion[]>([])
  const [soloCriticos, setSoloCriticos] = useState(false)
  const [fase, setFase] = useState('todas')

  const [fallo, setFallo] = useState('')
  const [avisoCritico, setAvisoCritico] = useState<{ codigo: string; texto: string } | null>(null)
  // HU-28: solo se ofrece marcar si el servidor ya tiene /criticos (null = aún no desplegado); sin eso el botón no podría guardar
  const [conHu28, setConHu28] = useState(false)
  useEffect(() => { if (usaApiReal) reposicionHttp.criticos().then((x) => setConHu28(x !== null)).catch(() => setConHu28(false)) }, [])
  // HU-28: marcar o quitar «crítico» (residente y administrador; el servidor decide el permiso)
  const alternarCritico = async (f: AvanceInsumo) => {
    setAvisoCritico({ codigo: f.codigo, texto: 'Guardando…' })
    try {
      await reposicionHttp.marcarCritico(f.codigo, !f.critico)
      setFilas((xs) => (xs ?? []).map((x) => (x.codigo === f.codigo ? { ...x, critico: !f.critico } : x)))
      setAvisoCritico({ codigo: f.codigo, texto: f.critico ? 'Ya no es crítico.' : 'Marcado como crítico: aparece en «Solo críticos».' })
    } catch (e) {
      setAvisoCritico({ codigo: f.codigo, texto: e instanceof ErrorApi && e.estado === 403 ? 'Solo el residente o el administrador marcan los insumos críticos.'
        : e instanceof ErrorApi && e.estado === 404 ? 'Marcar críticos estará disponible cuando el servidor tenga HU-28.'
          : e instanceof ErrorApi ? e.message : 'No se pudo cambiar la marca. Inténtalo de nuevo.' })
    }
  }
  useEffect(() => {
    api.saldoPorComprar().then(setFilas).catch((x) => { setFilas([]); setFallo(mensajeDeCarga(x)) })
    api.alertasReposicion().then(setAlertas).catch(() => setAlertas([]))
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

  return (
    <>
      <Fondo dibujo="reposicion" />
      <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Encabezado
          titulo="Reposición"
          sub="Qué falta comprar de lo planificado y qué está por acabarse en la obra"
          acciones={
            <>
              <Link to="/parametros" className="foco-vidrio" style={estiloPildora('secundario')}>Parámetros de reposición</Link>
              <Pildora enClic={exportar} deshabilitado={!visibles.length}>Exportar para el pedido</Pildora>
            </>
          }
        />

        <Chips>
          {usaApiReal
            ? <Chip tono="bien">Requerido, comprado y stock: del servidor</Chip>
            : <Chip tono="info">Comprado y stock: datos de ejemplo · lo requerido es el S10 real</Chip>}
          <Chip tono="neutro">El requerimiento avisa, no bloquea</Chip>
        </Chips>

        {alertas.map((a) => <Alerta key={a.insumoId} a={a} />)}

        <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 14 }} etiqueta="Saldo por comprar">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <TituloSeccion>Saldo por comprar</TituloSeccion>
            <p style={{ fontSize: 13.5, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
              Falta por comprar = requerido − comprado. La base cierta para formular la siguiente solicitud de materiales.
            </p>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>
            <Segmentado
              etiqueta="Filtro por criticidad" valor={soloCriticos} enCambio={setSoloCriticos}
              opciones={[{ valor: false, texto: 'Todos' }, { valor: true, texto: 'Solo críticos' }]}
            />
            {/* D14: las fases llegan con el requerimiento por fase (HU-20, Product Backlog); con una sola no hay nada que elegir */}
            {fases.length > 1 && <select
              aria-label="Filtrar por fase"
              value={fase}
              onChange={(e) => setFase(e.target.value)}
              className="campo"
              style={{ width: 'auto', minWidth: 200, height: 46, borderRadius: 23, fontWeight: 600, fontSize: 14 }}
            >
              <option value="todas">Todas las fases</option>
              {fases.map((f) => <option key={f} value={f}>{f}</option>)}
            </select>}
          </div>
          {soloCriticos && (
            <span style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>
              {conHu28
                ? <>Los críticos los marca el residente con «Marcar crítico» (HU-28).</>
                : <>Crítico = insumo con punto de reposición definido en <Link to="/parametros">Parámetros de reposición</Link>.</>}
            </span>
          )}

          {filas === null ? (
            <Vacio titulo="Cargando…" detalle="Calculando el saldo por comprar de cada insumo." />
          ) : fallo ? (
            <Vacio titulo="No se pudo cargar el saldo por comprar" detalle={fallo} />
          ) : visibles.length === 0 ? (
            soloCriticos && !(filas ?? []).some((f) => f.critico)
              ? <Vacio titulo="Todavía no hay insumos críticos" detalle={conHu28 ? 'Vuelve a «Todos» y usa «Marcar crítico» en el acero, el cemento u otro insumo.' : 'Define el stock de seguridad y el punto de reposición del insumo en «Parámetros de reposición» y aparecerá aquí.'} />
              : <Vacio titulo="Nada pendiente con este filtro" detalle="Todos los insumos filtrados ya tienen su compra completa." />
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="tabla-vidrio">
                <thead>
                  <tr>
                    <th>Insumo</th><th>Fase</th><th className="n">Requerido (S10)</th><th className="n">Comprado</th><th className="n">Falta por comprar</th>
                  </tr>
                </thead>
                <tbody>
                  {visibles.map((f) => (
                    <tr key={f.insumoId}>
                      <td style={{ minWidth: 240 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                          <span style={{ fontWeight: 600 }}>{f.descripcion}</span>
                          {f.critico && <Chip tono="aviso">crítico</Chip>}
                          {conHu28 && (
                            <Pildora tipo="secundario" chica enClic={() => void alternarCritico(f)}
                              etiqueta={f.critico ? `Quitar crítico a ${f.descripcion}` : `Marcar ${f.descripcion} como crítico`}>
                              {f.critico ? 'Quitar crítico' : '★ Marcar crítico'}
                            </Pildora>
                          )}
                        </div>
                        <div className="n" style={{ fontSize: 12, color: 'var(--tinta-2)', marginTop: 2, textAlign: 'left' }}>SKU {f.codigo}</div>
                        {avisoCritico?.codigo === f.codigo && (
                          <div role="status" style={{ fontSize: 12.5, color: 'var(--tinta-2)', marginTop: 4 }}>{avisoCritico.texto}</div>
                        )}
                      </td>
                      <td style={{ color: 'var(--tinta-2)' }}>{f.fase}</td>
                      <td className="n" style={{ whiteSpace: 'nowrap' }}>{n2(f.planificadoCantidad)} {f.unidadS10}</td>
                      <td className="n" style={{ whiteSpace: 'nowrap' }}>{n2(f.compradoCantidad)} {f.unidadS10} · {f.compradoPct} %</td>
                      <td className="n" style={{ fontWeight: 800, whiteSpace: 'nowrap' }}>{n2(f.saldoPorComprar)} {f.unidadCompra}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Vidrio>

        <p style={{ fontSize: 13, color: 'var(--tinta-plano)', padding: '0 4px', lineHeight: 1.5 }}>
          El faltante se muestra en la <strong>unidad de compra</strong>: el acero se pide en
          varillas aunque el S10 lo planifique en kilos (factor de conversión del catálogo).
          La sugerencia de compra (HU-27) aparece en la alerta de arriba: el menor entre lo que falta y lo que exige la fase en curso.
        </p>
      </div>
    </>
  )
}
