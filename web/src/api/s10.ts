/* ─────────────────────────────────────────────────────────────────────────────
   Puente con el micro de integraciones · importación del S10 (HU-07).
   La web NUNCA parsea el PDF: lo sube al micro (localhost:8001 en desarrollo)
   y muestra lo que el micro normalizó. Si el micro está apagado, la pantalla
   lo dice con claridad y no pasa nada más.
   ───────────────────────────────────────────────────────────────────────── */

export interface InsumoImportado {
  codigo: string
  descripcion: string
  unidad: string
  tipo: 'mano_obra' | 'material' | 'equipo' | 'subcontrato'
  cantidad: number
  precio: number
  parcial: number
  accion: 'crear' | 'actualizar'
  advertencia: string | null
}

export interface ResultadoImportacion {
  obra: string | null
  subpresupuesto: string | null
  fecha: string | null
  resumen: {
    leidos: number
    a_crear: number
    a_actualizar: number
    fallidos: number
    por_tipo: Record<string, number>
    parcial_total: number
  }
  insumos: InsumoImportado[]
  fallidos: { texto: string; error: string }[]
}

// En desarrollo, el micro corre en :8001; desplegado, se indica con VITE_INTEGRACIONES_URL.
const BASE = (import.meta.env.VITE_INTEGRACIONES_URL ?? 'http://localhost:8001').replace(/\/$/, '')

export class ErrorImportacion extends Error {}

export async function importarRelacionS10(
  archivo: File,
  codigosExistentes: string[] = [],
): Promise<ResultadoImportacion> {
  const datos = new FormData()
  datos.append('archivo', archivo)
  datos.append('codigos_existentes', codigosExistentes.join(','))

  let r: Response
  try {
    const control = new AbortController()
    const t = setTimeout(() => control.abort(), 30000)
    r = await fetch(`${BASE}/s10/relacion-insumos`, { method: 'POST', body: datos, signal: control.signal })
    clearTimeout(t)
  } catch {
    throw new ErrorImportacion(
      'El servicio de integraciones no responde. Arranca el micro (puerto 8001) y vuelve a intentar: el archivo no se pierde.',
    )
  }
  if (!r.ok) {
    const detalle = await r.json().catch(() => null) as { detail?: string } | null
    throw new ErrorImportacion(detalle?.detail ?? `El servicio respondió ${r.status}.`)
  }
  return r.json() as Promise<ResultadoImportacion>
}
