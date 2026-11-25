/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · arrastrar y soltar un archivo (fase 3 del rediseño, 25/11)

   Para el PDF del S10 y los comprobantes de compra. Antes había que buscar el
   botón «Elegir archivo»; ahora se suelta el archivo encima (o se hace clic o
   Enter/Espacio: con teclado funciona igual).
   - revisa tipo y tamaño ANTES de subir y lo dice con palabras;
   - mientras se arrastra, la zona se ilumina;
   - el servidor igual vuelve a revisar (esto solo ahorra la espera).
   ───────────────────────────────────────────────────────────────────────── */
import { useId, useRef, useState, type DragEvent, type KeyboardEvent } from 'react'

export interface ReglaArchivo {
  /** tipos MIME aceptados, p. ej. ['application/pdf'] */
  tipos: string[]
  /** extensiones equivalentes (algunos navegadores no informan el tipo) */
  extensiones: string[]
  maxMB: number
}

export const REGLA_S10: ReglaArchivo = { tipos: ['application/pdf'], extensiones: ['pdf'], maxMB: 20 }
export const REGLA_COMPROBANTE: ReglaArchivo = { tipos: ['application/pdf', 'image/jpeg', 'image/png'], extensiones: ['pdf', 'jpg', 'jpeg', 'png'], maxMB: 10 }

const ext = (nombre: string) => nombre.split('.').pop()?.toLowerCase() ?? ''
export const tamano = (b: number) => (b < 1024 * 1024 ? `${Math.max(1, Math.round(b / 1024))} KB` : `${(b / 1024 / 1024).toLocaleString('es-PE', { maximumFractionDigits: 1 })} MB`)

/** «PDF, JPG o PNG» (jpeg y jpg cuentan como uno) */
export function nombresTipos(r: ReglaArchivo, y = 'o'): string {
  const xs = [...new Set(r.extensiones.map((x) => (x === 'jpeg' ? 'jpg' : x).toUpperCase()))]
  return xs.length > 1 ? `${xs.slice(0, -1).join(', ')} ${y} ${xs[xs.length - 1]}` : xs[0]
}

/** null si el archivo sirve; si no, por qué (en palabras de la persona). */
export function revisarArchivo(a: { name: string; type: string; size: number }, r: ReglaArchivo): string | null {
  const okTipo = r.tipos.includes(a.type) || r.extensiones.includes(ext(a.name))
  if (!okTipo) return `«${a.name}» no es ${nombresTipos(r, 'ni')}.`
  if (a.size === 0) return `«${a.name}» está vacío.`
  if (a.size > r.maxMB * 1024 * 1024) return `«${a.name}» pesa ${tamano(a.size)}; el máximo es ${r.maxMB} MB.`
  return null
}

export function SoltarArchivo({ regla, archivo, alElegir, titulo, detalle, deshabilitado, etiqueta, compacto }: {
  regla: ReglaArchivo
  archivo: File | null
  alElegir: (a: File) => void
  /** lo que se pide, p. ej. «Suelta aquí la Relación de insumos» */
  titulo: string
  detalle?: string
  deshabilitado?: boolean
  /** nombre accesible del input de archivo */
  etiqueta: string
  compacto?: boolean
}) {
  const entrada = useRef<HTMLInputElement>(null)
  const id = useId()
  const [encima, setEncima] = useState(false)
  const [error, setError] = useState('')

  const tomar = (a: File | undefined) => {
    if (!a) return
    const e = revisarArchivo(a, regla)
    setError(e ?? '')
    if (!e) alElegir(a)
  }
  const soltar = (e: DragEvent) => {
    e.preventDefault(); setEncima(false)
    if (!deshabilitado) tomar(e.dataTransfer.files?.[0])
  }
  const tecla = (e: KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); entrada.current?.click() }
  }
  const icono = archivo ? (ext(archivo.name) === 'pdf' ? 'PDF' : 'IMG') : '↑'

  return (
    <div className="d-flex flex-column gap-1">
      <div
        role="button" tabIndex={deshabilitado ? -1 : 0} aria-disabled={deshabilitado || undefined}
        aria-label={archivo ? `${titulo}: elegido ${archivo.name}. Pulsa para cambiarlo` : `${titulo}: arrastra el archivo o pulsa para elegirlo`}
        aria-describedby={error ? `${id}-error` : undefined}
        className={`sigro-soltar${encima ? ' encima' : ''}${archivo ? ' con-archivo' : ''}${error ? ' con-error' : ''}${compacto ? ' compacto' : ''}`}
        onClick={() => !deshabilitado && entrada.current?.click()} onKeyDown={tecla}
        onDragOver={(e) => { e.preventDefault(); if (!deshabilitado) setEncima(true) }}
        onDragEnter={(e) => { e.preventDefault(); if (!deshabilitado) setEncima(true) }}
        onDragLeave={() => setEncima(false)} onDrop={soltar}
      >
        <span className="icono" aria-hidden>{icono}</span>
        <span className="min-w-0 flex-grow-1">
          <span className="d-block fw-bold text-break">{archivo ? archivo.name : encima ? 'Suéltalo aquí' : titulo}</span>
          <span className="d-block small text-body-secondary">
            {archivo ? `${tamano(archivo.size)} · clic o arrastra otro para cambiarlo` : detalle ?? `${nombresTipos(regla)} · hasta ${regla.maxMB} MB · o haz clic para buscarlo`}
          </span>
        </span>
        <span className="accion d-none d-sm-inline" aria-hidden>{archivo ? 'Cambiar' : 'Buscar'}</span>
        {/* el input lleva el nombre del campo (las pruebas e2e le cargan el archivo por su etiqueta) */}
        <input ref={entrada} type="file" className="d-none" tabIndex={-1} aria-label={etiqueta} disabled={deshabilitado}
          accept={[...regla.tipos, ...regla.extensiones.map((x) => `.${x}`)].join(',')}
          onChange={(e) => { tomar(e.target.files?.[0]); e.target.value = '' }} />
      </div>
      {error && <div id={`${id}-error`} className="small text-danger" role="alert">{error}</div>}
    </div>
  )
}
