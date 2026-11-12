/* ─────────────────────────────────────────────────────────────────────────────
   HU-25 · Como residente de obra, quiero definir para cada insumo su stock de
           seguridad y su punto de reposición, para saber cuándo reponer y con
           cuánto colchón frente a la demora del proveedor.
   (Pantalla 17 del lienzo, «Parámetros de reposición».)

   Condiciones finales que cubre:
   - El stock de seguridad y el punto de reposición se registran en unidad de compra.
   - Un cambio durante la obra queda en el historial (el anterior no se pisa).
   - Los conceptos se muestran por separado, nunca como un solo «mínimo».
   - El punto no puede ser menor que el stock de seguridad (se avisa bajo el campo y lo valida el servidor).
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useMemo, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { AvanceInsumo, ParametroReposicion } from '../api/tipos'
import { unidadParaMostrar } from '../api/unidades'
import { BuscarOpcion } from '../ui/BuscarOpcion'
import { Boton, Modal, Tarjeta, Titulo, Vacio } from '../ui/base'
import { type ErroresParametros, leerCantidad, validarParametros } from './parametros-reglas'

const n = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 2 })
const fecha = (iso: string) => new Date(iso).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' })

/** Campo de cantidad con su unidad pegada a la derecha y el mensaje (error o ayuda) debajo. */
function CampoCantidad({ id, etiqueta, valor, enCambio, unidad, error, ayuda }: {
  id: string; etiqueta: string; valor: string; enCambio: (v: string) => void; unidad: string; error?: string; ayuda: string
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <label htmlFor={id} style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' }}>{etiqueta}</label>
      <div style={{ display: 'flex', alignItems: 'stretch', border: `1px solid ${error ? 'var(--grave)' : 'var(--borde)'}`, borderRadius: 'var(--radio-chico)', background: 'var(--tarjeta)', overflow: 'hidden' }}>
        <input
          id={id} value={valor} inputMode="decimal" autoComplete="off" onChange={(e) => enCambio(e.target.value)}
          aria-invalid={error ? true : undefined} aria-describedby={`${id}-msj`}
          style={{ flex: 1, minWidth: 0, height: 40, border: 0, padding: '0 13px', fontSize: 14, fontFamily: 'inherit', color: 'var(--tinta)', background: 'transparent', outlineColor: 'var(--accion)' }}
        />
        <span aria-hidden className="n" style={{ flex: 'none', display: 'flex', alignItems: 'center', padding: '0 12px', fontSize: 13, fontWeight: 600, color: 'var(--tinta-2)', background: 'var(--tarjeta-suave)', borderLeft: '1px solid var(--borde)' }}>
          {unidad || '—'}
        </span>
      </div>
      <span id={`${id}-msj`} role={error ? 'alert' : undefined} style={{ fontSize: 12, color: error ? 'var(--grave)' : 'var(--tinta-3)' }}>
        {error ?? (unidad ? `${ayuda} En ${unidad}.` : ayuda)}
      </span>
    </div>
  )
}

/*
 * HU-25 · «Definir parámetros». El insumo se busca por código o nombre (no un <select> de 284).
 * La unidad va junto a cada campo: la de compra del insumo, o la del S10 con aviso si falta el factor.
 * «Nivel máximo» no está: la API (ParametroIn) no lo acepta todavía (pedido a Adiel en el PR).
 */
function Formulario({ insumos, todos, inicial, enCerrar, enListo }: {
  insumos: AvanceInsumo[]
  todos: AvanceInsumo[]
  inicial: ParametroReposicion | null
  enCerrar: () => void
  enListo: () => void
}) {
  const [codigo, setCodigo] = useState(inicial?.insumoCodigo ?? '')
  const [seguridad, setSeguridad] = useState(inicial ? String(inicial.stockSeguridad) : '')
  const [punto, setPunto] = useState(inicial ? String(inicial.puntoReposicion) : '')
  const [errores, setErrores] = useState<ErroresParametros>({})
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)

  const insumo = todos.find((i) => i.codigo === codigo) ?? null
  const { unidad, aviso } = unidadParaMostrar(insumo ?? (inicial ? { unidadCompra: inicial.unidadCompra } : null))
  const opciones = useMemo(() => insumos.map((i) => ({ valor: i.codigo, texto: i.descripcion, detalle: `${i.codigo} · ${i.unidadCompra}` })), [insumos])

  const guardar = async () => {
    const e = validarParametros({ insumo: codigo, seguridad, punto, unidad })
    setErrores(e); setError('')
    if (Object.keys(e).length) return
    setGuardando(true)
    try {
      await api.registrarParametro({ insumoCodigo: codigo, stockSeguridad: leerCantidad(seguridad), puntoReposicion: leerCantidad(punto) })
      enListo()
    } catch (x) {
      setError(x instanceof ErrorApi ? x.message : 'No se pudo guardar. Inténtalo de nuevo.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={inicial ? `Cambiar parámetros · ${inicial.descripcion}` : 'Definir parámetros de un insumo'} enCerrar={enCerrar} ancho={480}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {!inicial && (
          <BuscarOpcion
            id="par-insumo" etiqueta="Insumo del requerimiento" opciones={opciones} valor={codigo}
            enCambio={(v) => { setCodigo(v); setErrores((x) => ({ ...x, insumo: undefined })) }}
            marcador="Escribe el código o parte del nombre" error={errores.insumo}
            ayuda={`${insumos.length} insumos del requerimiento sin parámetros.`}
            sinResultados="Ningún insumo coincide con"
          />
        )}
        {aviso && <div role="status" style={{ fontSize: 12.5, lineHeight: 1.5, color: 'var(--aviso)', background: 'var(--aviso-tinte)', borderRadius: 'var(--radio-chico)', padding: '8px 12px' }}>{aviso}</div>}
        <CampoCantidad id="par-seguridad" etiqueta="Stock de seguridad" valor={seguridad} unidad={unidad}
          enCambio={(v) => { setSeguridad(v); setErrores((x) => ({ ...x, seguridad: undefined, punto: undefined })) }}
          error={errores.seguridad} ayuda="Colchón frente a la demora del proveedor." />
        <CampoCantidad id="par-punto" etiqueta="Punto de reposición" valor={punto} unidad={unidad}
          enCambio={(v) => { setPunto(v); setErrores((x) => ({ ...x, punto: undefined })) }}
          error={errores.punto} ayuda="Saldo que dispara el aviso; igual o mayor que el stock de seguridad." />
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
        <Formulario insumos={sinParametro} todos={insumos} inicial={editando === 'nuevo' ? null : editando}
          enCerrar={() => setEditando(null)} enListo={() => { setEditando(null); cargar() }} />
      )}
      {viendo && <Historial p={viendo} enCerrar={() => setViendo(null)} />}
    </div>
  )
}
