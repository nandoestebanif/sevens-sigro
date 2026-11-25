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
import { AccionesFila, TablaDatos } from '../ui/TablaDatos'
import { puedo } from '../api/permisos'
import { Chips, Encabezado, Pildora, Vidrio } from '../ui/piezas'
import { useEffect, useMemo, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import { mensajeDeCarga } from '../api/error'
import type { AvanceInsumo, ParametroReposicion } from '../api/tipos'
import { unidadParaMostrar } from '../api/unidades'
import { BuscarOpcion } from '../ui/BuscarOpcion'
import { Boton, Chip, Modal, Vacio } from '../ui/base'
import { type ErroresParametros, leerCantidad, validarParametros } from './parametros-reglas'
import { EsqueletoTabla } from '../ui/Esqueleto'

const n = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 2 })
const fecha = (iso: string) => new Date(iso).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' })

/** Campo de cantidad con su unidad pegada a la derecha y el mensaje (error o ayuda) debajo. */
function CampoCantidad({ id, etiqueta, valor, enCambio, unidad, error, ayuda }: {
  id: string; etiqueta: string; valor: string; enCambio: (v: string) => void; unidad: string; error?: string; ayuda: string
}) {
  return (
    <div className="d-flex flex-column gap-2">
      <label htmlFor={id} className="small fw-semibold text-body-secondary">{etiqueta}</label>
      {/* input-group de Bootstrap: el número y su unidad quedan pegados en una sola caja */}
      <div className={`input-group${error ? ' has-validation' : ''}`}>
        <input
          id={id} value={valor} inputMode="decimal" autoComplete="off" onChange={(e) => enCambio(e.target.value)}
          aria-invalid={error ? true : undefined} aria-describedby={`${id}-msj`}
          className={`form-control${error ? ' is-invalid' : ''}`}
        />
        <span aria-hidden className="input-group-text n small fw-semibold">
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
      <div className="d-flex flex-column gap-3">
        {!inicial && (
          <BuscarOpcion
            id="par-insumo" etiqueta="Insumo del requerimiento" opciones={opciones} valor={codigo}
            enCambio={(v) => { setCodigo(v); setErrores((x) => ({ ...x, insumo: undefined })) }}
            marcador="Escribe el código o parte del nombre" error={errores.insumo}
            ayuda={`${insumos.length} insumos del requerimiento sin parámetros.`}
            sinResultados="Ningún insumo coincide con"
          />
        )}
        {aviso && <div role="status" className="small text-warning-emphasis bg-warning-subtle rounded-3 py-2 px-2" >{aviso}</div>}
        <CampoCantidad id="par-seguridad" etiqueta="Stock de seguridad" valor={seguridad} unidad={unidad}
          enCambio={(v) => { setSeguridad(v); setErrores((x) => ({ ...x, seguridad: undefined, punto: undefined })) }}
          error={errores.seguridad} ayuda="Colchón frente a la demora del proveedor." />
        <CampoCantidad id="par-punto" etiqueta="Punto de reposición" valor={punto} unidad={unidad}
          enCambio={(v) => { setPunto(v); setErrores((x) => ({ ...x, punto: undefined })) }}
          error={errores.punto} ayuda="Saldo que dispara el aviso; igual o mayor que el stock de seguridad." />
        {error && <div role="alert" className="small text-danger">{error}</div>}
        <div className="d-flex justify-content-end gap-2">
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={guardando}>{guardando ? 'Guardando…' : 'Guardar'}</Boton>
        </div>
      </div>
    </Modal>
  )
}

function Historial({ p, enCerrar }: { p: ParametroReposicion; enCerrar: () => void }) {
  const [filas, setFilas] = useState<ParametroReposicion[] | null>(null)
  const [fallo, setFallo] = useState('')
  useEffect(() => {
    api.historialParametro(p.insumoCodigo).then(setFilas).catch((x) => { setFilas([]); setFallo(mensajeDeCarga(x)) })
  }, [p.insumoCodigo])
  return (
    <Modal titulo={`Historial · ${p.descripcion}`} enCerrar={enCerrar}>
      {filas === null ? <EsqueletoTabla /> : fallo ? <Vacio titulo="No se pudo abrir el historial" detalle={fallo} /> : (
        <table className="w-100 small" >
          <thead><tr className="text-body-secondary small">
            <th className="text-start p-1" >Vigente desde</th>
            <th className="text-end p-1" >Stock de seguridad</th>
            <th className="text-end p-1" >Punto de reposición</th>
          </tr></thead>
          <tbody>{filas.map((f, i) => (
            <tr key={i} style={{ borderTop: '1px solid var(--linea)', fontWeight: i === 0 ? 700 : 400 }}>
              <td className="p-1">{fecha(f.vigenteDesde)}{i === 0 ? ' · vigente' : ''}</td>
              <td className="n text-end p-1" >{n(f.stockSeguridad)} {f.unidadCompra}</td>
              <td className="n text-end p-1" >{n(f.puntoReposicion)} {f.unidadCompra}</td>
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

  const [fallo, setFallo] = useState('')
  const cargar = () => api.parametrosReposicion().then((v) => { setVigentes(v); setFallo('') })
    .catch((x) => { setVigentes([]); setFallo(mensajeDeCarga(x)) })
  useEffect(() => { cargar(); api.saldoPorComprar().then(setInsumos).catch(() => setInsumos([])) }, [])
  const sinParametro = useMemo(
    () => insumos.filter((i) => !(vigentes ?? []).some((v) => v.insumoCodigo === i.codigo)), [insumos, vigentes])

  const puedeRegistrar = puedo('reposicion.registrar')

  return (
    <div className="d-flex flex-column gap-3 pb-4">
      <Encabezado titulo="Parámetros de reposición" sub="Cuándo reponer cada insumo y con cuánto colchón."
        acciones={puedeRegistrar && <Pildora enClic={() => setEditando('nuevo')} deshabilitado={!!fallo || !sinParametro.length}>Definir parámetros</Pildora>} />
      <Chips>
        <Chip tono="neutro">Cambiarlos no borra lo anterior: queda en su historial con fecha</Chip>
        <Chip tono="neutro">El punto de reposición no puede ser menor que el stock de seguridad</Chip>
      </Chips>
      <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Parámetros vigentes">
        {vigentes === null ? <EsqueletoTabla />
          : fallo ? <Vacio titulo="No se pudieron cargar los parámetros" detalle={fallo} />
          : (
            <TablaDatos
              etiqueta="Parámetros vigentes"
              filas={vigentes}
              clave={(p) => p.insumoCodigo}
              buscar={(p) => `${p.descripcion} ${p.insumoCodigo}`}
              marcadorBusqueda="Buscar insumo…"
              vacio={{ titulo: 'Todavía no hay parámetros en esta obra', detalle: insumos.length
                ? 'Define el stock de seguridad y el punto de reposición de los insumos críticos primero.'
                : 'Primero carga el requerimiento de la obra en «Importar del S10».' }}
              columnas={[
                { clave: 'insumo', titulo: 'Insumo', valor: (p) => p.descripcion, celda: (p) => <><strong>{p.descripcion}</strong><div className="n small text-body-secondary">SKU {p.insumoCodigo}</div></> },
                { clave: 'seguridad', titulo: 'Stock de seguridad', numerica: true, valor: (p) => p.stockSeguridad, celda: (p) => <span className="text-nowrap">{n(p.stockSeguridad)} {p.unidadCompra}</span> },
                { clave: 'punto', titulo: 'Punto de reposición', numerica: true, valor: (p) => p.puntoReposicion, celda: (p) => <strong className="text-nowrap">{n(p.puntoReposicion)} {p.unidadCompra}</strong> },
                { clave: 'desde', titulo: 'Vigente desde', numerica: true, valor: (p) => p.vigenteDesde, celda: (p) => <span className="text-nowrap">{fecha(p.vigenteDesde)}</span> },
              ]}
              acciones={(p) => (
                <AccionesFila de={p.descripcion} acciones={[
                  { texto: 'Historial', enClic: () => setViendo(p) },
                  puedeRegistrar && { texto: 'Cambiar', enClic: () => setEditando(p) },
                ]} />
              )}
            />
          )}
      </Vidrio>
      {editando && (
        <Formulario insumos={sinParametro} todos={insumos} inicial={editando === 'nuevo' ? null : editando}
          enCerrar={() => setEditando(null)} enListo={() => { setEditando(null); cargar() }} />
      )}
      {viendo && <Historial p={viendo} enCerrar={() => setViendo(null)} />}
    </div>
  )
}
