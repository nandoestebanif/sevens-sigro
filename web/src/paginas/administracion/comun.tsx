/* SIGRO · Administración · piezas comunes de las cuatro pestañas (avisos, confirmación, tabla, selector). */
import { type CSSProperties, type ReactNode, useCallback, useEffect, useState } from 'react'
import { Boton, Modal, Vacio } from '../../ui/base'
import { mensajeParaPersona } from './reglas'

/** Carga con sus tres estados: cargando (null), error (mensaje para la persona) o datos. */
export function useCarga<T>(cargar: () => Promise<T>, deps: unknown[]) {
  const [datos, setDatos] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  // `deps` las da quien llama (obra, filtros): al cambiar, se vuelve a consultar
  const recargar = useCallback(async () => {
    setError(null)
    try { setDatos(await cargar()) } catch (e) { setError(mensajeParaPersona(e)); setDatos(null) }
  }, deps)
  useEffect(() => { setDatos(null); recargar() }, [recargar])
  return { datos, error, recargar, setDatos }
}

export function AvisoError({ texto, children }: { texto: string; children?: ReactNode }) {
  return (
    <div role="alert" style={{
      background: 'var(--grave-tinte)', color: 'var(--grave)', borderRadius: 'var(--radio-chico)',
      padding: '11px 14px', fontSize: 13.5, lineHeight: 1.5, display: 'flex', flexDirection: 'column', gap: 10,
    }}>
      <span>{texto}</span>
      {children}
    </div>
  )
}

export function AvisoBien({ texto }: { texto: string }) {
  return (
    <div role="status" style={{
      background: 'var(--bien-tinte)', color: 'var(--bien)', borderRadius: 'var(--radio-chico)',
      padding: '10px 14px', fontSize: 13.5,
    }}>
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
      <div style={{ padding: 16 }}>
        <AvisoError texto={error}>
          <div><Boton tipo="secundario" enClic={recargar}>Volver a intentar</Boton></div>
        </AvisoError>
      </div>
    )
  }
  if (datos === null) return <div role="status"><Vacio titulo="Cargando…" detalle="Consultando al servidor." /></div>
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
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ fontSize: 13.5, lineHeight: 1.55, color: 'var(--tinta-2)' }}>{texto}</div>
        {error && <AvisoError texto={error} />}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
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
    <div style={{ overflowX: 'auto' }}>
      <table aria-label={etiqueta} style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ background: 'var(--tarjeta-suave)', borderBottom: '1px solid var(--borde)' }}>
            {titulos.map((h) => (
              <th key={h} scope="col" style={{ padding: '11px 16px', fontSize: 11, fontWeight: 600, color: 'var(--tinta-3)', letterSpacing: 0.4, textAlign: 'left', whiteSpace: 'nowrap' }}>
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
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <label htmlFor={id} style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' }}>{etiqueta}</label>
      <select
        id={id}
        value={String(valor)}
        onChange={(e) => {
          const o = opciones.find((x) => String(x.valor) === e.target.value)
          if (o) enCambio(o.valor)
        }}
        style={{
          height: 42, border: `1px solid ${error ? 'var(--grave)' : 'var(--borde)'}`, borderRadius: 'var(--radio-chico)',
          padding: '0 10px', fontSize: 14, fontFamily: 'inherit', color: 'var(--tinta)', background: 'var(--tarjeta)',
        }}
      >
        <option value="" disabled>Elige…</option>
        {opciones.map((o) => <option key={String(o.valor)} value={String(o.valor)}>{o.texto}</option>)}
      </select>
      {error && <span style={{ fontSize: 12, color: 'var(--grave)' }}>{error}</span>}
    </div>
  )
}
