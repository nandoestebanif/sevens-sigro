/* ─────────────────────────────────────────────────────────────────────────────
   HU-25 · Como residente de obra, quiero definir para cada insumo su stock de
           seguridad y su punto de reposición, para saber cuándo reponer y con
           cuánto colchón frente a la demora del proveedor.
   (Pantalla 17 del lienzo, «Parámetros de reposición».)

   Condiciones finales que cubre:
   - El stock de seguridad y el punto de reposición se registran en unidad de compra.
   - Un cambio durante la obra queda en el historial (el anterior no se pisa).
   - Los conceptos se muestran por separado, nunca como un solo «mínimo».
   - El punto no puede ser menor que el stock de seguridad (lo valida el servidor).
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useMemo, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { AvanceInsumo, ParametroReposicion } from '../api/tipos'
import { Boton, Campo, Modal, Tarjeta, Titulo, Vacio } from '../ui/base'

const n = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 2 })
const fecha = (iso: string) => new Date(iso).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' })

function Formulario({ insumos, inicial, enCerrar, enListo }: {
  insumos: AvanceInsumo[]
  inicial: ParametroReposicion | null
  enCerrar: () => void
  enListo: () => void
}) {
  const [codigo, setCodigo] = useState(inicial?.insumoCodigo ?? '')
  const [seguridad, setSeguridad] = useState(inicial ? String(inicial.stockSeguridad) : '')
  const [punto, setPunto] = useState(inicial ? String(inicial.puntoReposicion) : '')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const unidad = insumos.find((i) => i.codigo === codigo)?.unidadCompra ?? inicial?.unidadCompra ?? ''

  const guardar = async () => {
    const s = Number(seguridad.replace(',', '.')), p = Number(punto.replace(',', '.'))
    if (!codigo) { setError('Elige el insumo.'); return }
    if (!(s >= 0) || !(p >= 0) || seguridad === '' || punto === '') { setError('Escribe las dos cantidades (0 o más).'); return }
    setGuardando(true); setError('')
    try {
      await api.registrarParametro({ insumoCodigo: codigo, stockSeguridad: s, puntoReposicion: p })
      enListo()
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo guardar.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={inicial ? `Cambiar parámetros · ${inicial.descripcion}` : 'Definir parámetros de un insumo'} enCerrar={enCerrar}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {!inicial && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label htmlFor="par-insumo" style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' }}>Insumo del requerimiento</label>
            <select id="par-insumo" value={codigo} onChange={(e) => setCodigo(e.target.value)}
              style={{ height: 42, border: '1px solid var(--borde)', borderRadius: 'var(--radio-chico)', padding: '0 10px', fontSize: 14, fontFamily: 'inherit', background: 'var(--tarjeta)', color: 'var(--tinta)' }}>
              <option value="">Elige un insumo…</option>
              {insumos.map((i) => <option key={i.codigo} value={i.codigo}>{i.descripcion} · {i.codigo}</option>)}
            </select>
          </div>
        )}
        <Campo id="par-seguridad" etiqueta={`Stock de seguridad${unidad ? ` (${unidad})` : ''}`} valor={seguridad} enCambio={setSeguridad}
          marcador="colchón frente a la demora del proveedor" />
        <Campo id="par-punto" etiqueta={`Punto de reposición${unidad ? ` (${unidad})` : ''}`} valor={punto} enCambio={setPunto}
          marcador="saldo que dispara el aviso (incluye el colchón)" />
        {error && <div role="alert" style={{ fontSize: 13, color: 'var(--grave)' }}>{error}</div>}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</Boton>
        </div>
      </div>
    </Modal>
  )
}

function Historial({ p, enCerrar }: { p: ParametroReposicion; enCerrar: () => void }) {
  const [filas, setFilas] = useState<ParametroReposicion[] | null>(null)
  useEffect(() => { api.historialParametro(p.insumoCodigo).then(setFilas) }, [p.insumoCodigo])
  return (
    <Modal titulo={`Historial · ${p.descripcion}`} enCerrar={enCerrar}>
      {filas === null ? <Vacio titulo="Cargando…" detalle="" /> : (
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead><tr style={{ color: 'var(--tinta-3)', fontSize: 11.5 }}>
            <th style={{ textAlign: 'left', padding: 6 }}>Vigente desde</th>
            <th style={{ textAlign: 'right', padding: 6 }}>Stock de seguridad</th>
            <th style={{ textAlign: 'right', padding: 6 }}>Punto de reposición</th>
          </tr></thead>
          <tbody>{filas.map((f, i) => (
            <tr key={i} style={{ borderTop: '1px solid var(--linea)', fontWeight: i === 0 ? 700 : 400 }}>
              <td style={{ padding: 6 }}>{fecha(f.vigenteDesde)}{i === 0 ? ' · vigente' : ''}</td>
              <td className="n" style={{ padding: 6, textAlign: 'right' }}>{n(f.stockSeguridad)} {f.unidadCompra}</td>
              <td className="n" style={{ padding: 6, textAlign: 'right' }}>{n(f.puntoReposicion)} {f.unidadCompra}</td>
            </tr>
          ))}</tbody>
        </table>
      )}
    </Modal>
  )
}

export function Parametros() {
  const [vigentes, setVigentes] = useState<ParametroReposicion[] | null>(null)
  const [insumos, setInsumos] = useState<AvanceInsumo[]>([])
  const [editando, setEditando] = useState<ParametroReposicion | null | 'nuevo'>(null)
  const [viendo, setViendo] = useState<ParametroReposicion | null>(null)

  const cargar = () => api.parametrosReposicion().then(setVigentes)
  useEffect(() => { cargar(); api.saldoPorComprar().then(setInsumos) }, [])
  const sinParametro = useMemo(
    () => insumos.filter((i) => !(vigentes ?? []).some((v) => v.insumoCodigo === i.codigo)), [insumos, vigentes])

  return (
    <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <Titulo sub="Cuándo reponer cada insumo y con cuánto colchón. Cambiarlos no borra lo anterior: queda en su historial.">
          Parámetros de reposición
        </Titulo>
        <Boton enClic={() => setEditando('nuevo')} deshabilitado={!sinParametro.length}>Definir parámetros</Boton>
      </div>
      <Tarjeta relleno={false}>
        {vigentes === null ? <Vacio titulo="Cargando…" detalle="" />
          : vigentes.length === 0 ? (
            <Vacio titulo="Todavía no hay parámetros en esta obra"
              detalle={insumos.length ? 'Define el stock de seguridad y el punto de reposición de los insumos críticos primero.'
                : 'Primero carga el requerimiento de la obra en «Importar del S10».'} />
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead><tr style={{ background: 'var(--tarjeta-suave)', borderBottom: '1px solid var(--borde)' }}>
                {['Insumo', 'Stock de seguridad', 'Punto de reposición', 'Vigente desde', ''].map((h, i) => (
                  <th key={i} style={{ padding: '11px 16px', fontSize: 11, fontWeight: 600, color: 'var(--tinta-3)', textAlign: i === 0 || i === 3 ? 'left' : 'right' }}>{h}</th>
                ))}
              </tr></thead>
              <tbody>{vigentes.map((p) => (
                <tr key={p.insumoCodigo} style={{ borderBottom: '1px solid var(--linea)' }}>
                  <td style={{ padding: '11px 16px' }}>
                    <div style={{ fontSize: 13.5, fontWeight: 600 }}>{p.descripcion}</div>
                    <div className="n" style={{ fontSize: 11.5, color: 'var(--tinta-3)' }}>{p.insumoCodigo}</div>
                  </td>
                  <td className="n" style={{ padding: '11px 16px', textAlign: 'right', fontSize: 13.5 }}>{n(p.stockSeguridad)} {p.unidadCompra}</td>
                  <td className="n" style={{ padding: '11px 16px', textAlign: 'right', fontSize: 13.5, fontWeight: 700 }}>{n(p.puntoReposicion)} {p.unidadCompra}</td>
                  <td style={{ padding: '11px 16px', fontSize: 12.5, color: 'var(--tinta-2)' }}>{fecha(p.vigenteDesde)}</td>
                  <td style={{ padding: '11px 16px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <Boton tipo="fantasma" enClic={() => setViendo(p)}>Historial</Boton>
                    <Boton tipo="secundario" enClic={() => setEditando(p)}>Cambiar</Boton>
                  </td>
                </tr>
              ))}</tbody>
            </table>
          )}
      </Tarjeta>
      {editando && (
        <Formulario insumos={sinParametro} inicial={editando === 'nuevo' ? null : editando}
          enCerrar={() => setEditando(null)} enListo={() => { setEditando(null); cargar() }} />
      )}
      {viendo && <Historial p={viendo} enCerrar={() => setViendo(null)} />}
    </div>
  )
}
