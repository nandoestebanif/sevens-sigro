/* SIGRO · Administración · piezas comunes de las cuatro pestañas (avisos, confirmación, tabla, selector). */
import { type CSSProperties, type ReactNode, useCallback, useEffect, useState } from 'react'
import { Boton, Modal, Vacio } from '../../ui/base'
import { mensajeParaPersona } from './reglas'
import { EsqueletoTabla } from '../../ui/Esqueleto'

/** Carga con sus tres estados: cargando (null), error (mensaje para la persona) o datos. */
export function useCarga<T>(cargar: () => Promise<T>, deps: unknown[]) {
  const [datos, setDatos] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  // `deps` las da quien llama (obra, filtros): al cambiar, se vuelve a consultar
  const recargar = useCallback(async () => {
    setError(null)
    try { setDatos(await cargar()) } catch (e) { setError(mensajeParaPersona(e)); setDatos(null) }
    // a propósito: las dependencias son las de quien llama (obra, filtros), no la función `cargar`, que se
    // crea en cada render del que llama y haría consultar al servidor sin parar
  }, deps) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { setDatos(null); recargar() }, [recargar])
  return { datos, error, recargar, setDatos }
}

export function AvisoError({ texto, children }: { texto: string; children?: ReactNode }) {
  return (
    <div role="alert" className="text-danger small d-flex flex-column gap-2 bg-danger-subtle rounded-3 py-2 px-3" >
      <span>{texto}</span>
      {children}
    </div>
  )
}

export function AvisoBien({ texto }: { texto: string }) {
  return (
    <div role="status" className="text-success small bg-success-subtle rounded-3 py-2 px-3" >
      {texto}
    </div>
  )
}

/** Cargando / error con reintento / vacío / contenido: las cuatro caras de una lista. */
export function Estado<T>({ datos, error, recargar, vacio, children }: {
  datos: T[] | null
  error: string | null
  recargar: () => void
  vacio: { titulo: string; detalle: string }
  children: (lista: T[]) => ReactNode
}) {
  if (error) {
    return (
      <div className="p-3">
        <AvisoError texto={error}>
          <div><Boton tipo="secundario" enClic={recargar}>Volver a intentar</Boton></div>
        </AvisoError>
      </div>
    )
  }
  if (datos === null) return <div role="status"><EsqueletoTabla /></div>
  if (datos.length === 0) return <Vacio titulo={vacio.titulo} detalle={vacio.detalle} />
  return <>{children(datos)}</>
}

/** Confirmación antes de una acción que no se deshace sola (baja, retiro). Nunca un confirm() del navegador. */
export function Confirmar({ titulo, texto, accion, enConfirmar, enCerrar }: {
  titulo: string
  texto: ReactNode
  accion: string
  enConfirmar: () => Promise<void>
  enCerrar: () => void
}) {
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const confirmar = async () => {
    setOcupado(true); setError(null)
    try { await enConfirmar() } catch (e) { setError(mensajeParaPersona(e)); setOcupado(false) }
  }
  return (
    <Modal titulo={titulo} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <div className="small text-body-secondary">{texto}</div>
        {error && <AvisoError texto={error} />}
        <div className="d-flex gap-2 justify-content-end">
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={confirmar} deshabilitado={ocupado}>{ocupado ? 'Guardando…' : accion}</Boton>
        </div>
      </div>
    </Modal>
  )
}

const celda: CSSProperties = { padding: '12px 16px', fontSize: 13.5, verticalAlign: 'top' }
export const estiloCelda = celda
export const estiloCeldaSuave: CSSProperties = { ...celda, fontSize: 13, color: 'var(--tinta-2)' }

export function Tabla({ titulos, children, etiqueta }: { titulos: string[]; children: ReactNode; etiqueta: string }) {
  return (
    <div className="overflow-x-auto">
      <table aria-label={etiqueta} className="w-100" >
        <thead>
          <tr className="bg-body-tertiary border-bottom">
            {titulos.map((h) => (
              <th key={h} scope="col" className="small fw-semibold text-body-secondary text-start text-nowrap py-2 px-3" >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}

export const filaTabla = (ultima: boolean): CSSProperties => ({ borderBottom: ultima ? 'none' : '1px solid var(--linea)' })

export function Selector<T extends string | number>({ id, etiqueta, valor, opciones, enCambio, error }: {
  id: string
  etiqueta: string
  valor: T | ''
  opciones: { valor: T; texto: string }[]
  enCambio: (v: T) => void
  error?: string
}) {
  return (
    <div className="d-flex flex-column gap-2">
      <label htmlFor={id} className="small fw-semibold text-body-secondary">{etiqueta}</label>
      <select className="form-select"
        id={id}
        value={String(valor)}
        onChange={(e) => {
          const o = opciones.find((x) => String(x.valor) === e.target.value)
          if (o) enCambio(o.valor)
        }}

      >
        <option value="" disabled>Elige…</option>
        {opciones.map((o) => <option key={String(o.valor)} value={String(o.valor)}>{o.texto}</option>)}
      </select>
      {error && <span className="small text-danger">{error}</span>}
    </div>
  )
}
