/* ─────────────────────────────────────────────────────────────────────────────
   Importación del S10 (HU-07). La web NUNCA parsea el PDF: lo sube y muestra
   lo que el micro de integraciones normalizó.
   - Con la API real, el PDF va por el gateway con el JWT de la sesión
     (POST /api/v1/catalogo/importaciones-s10). El catálogo lo reenvía a
     Integraciones con su token de servicio y pone él los códigos que ya
     existen: el navegador nunca ve ese token ni habla con Integraciones.
   - Con el cliente simulado (sin VITE_API_URL), en desarrollo se sube directo
     al micro en localhost:8001, que corre sin token.
   ───────────────────────────────────────────────────────────────────────── */
import { API_URL, cerrarSesion, sesionActual, usaApiReal } from './http'

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

const MICRO_LOCAL = 'http://localhost:8001'

export class ErrorImportacion extends Error {}

export async function importarRelacionS10(
  archivo: File,
  codigosExistentes: string[] = [],
): Promise<ResultadoImportacion> {
  const datos = new FormData()
  datos.append('archivo', archivo)
  // con la API real, los códigos que ya existen los pone el servidor
  if (!usaApiReal) datos.append('codigos_existentes', codigosExistentes.join(','))
  const sesion = sesionActual()
  const url = usaApiReal ? `${API_URL}/api/v1/catalogo/importaciones-s10` : `${MICRO_LOCAL}/s10/relacion-insumos`

  let r: Response
  try {
    const control = new AbortController()
    // en el ambiente de pruebas la primera petición despierta la cadena gateway → catálogo → integraciones
    const t = setTimeout(() => control.abort(), usaApiReal ? 90000 : 30000)
    r = await fetch(url, {
      method: 'POST',
      body: datos,
      headers: usaApiReal && sesion ? { Authorization: `Bearer ${sesion.token}` } : {},
      signal: control.signal,
    })
    clearTimeout(t)
  } catch {
    throw new ErrorImportacion(usaApiReal
      ? 'No se pudo conectar con el servidor de SIGRO. Revisa tu conexión y vuelve a intentar: el archivo no se pierde.'
      : 'El servicio de integraciones no responde. Arranca el micro (puerto 8001) y vuelve a intentar: el archivo no se pierde.')
  }
  if (r.status === 401 && usaApiReal && sesion) {
    cerrarSesion() // token vencido o usuario dado de baja: se vuelve a entrar
    throw new ErrorImportacion('Tu sesión venció. Vuelve a ingresar.')
  }
  if (!r.ok) {
    // la API responde {codigo, mensaje}; el micro, {detail}
    const d = await r.json().catch(() => null) as { mensaje?: string; detail?: unknown } | null
    const mensaje = d?.mensaje ?? (typeof d?.detail === 'string' ? d.detail : null)
    throw new ErrorImportacion(mensaje ?? `El servidor respondió ${r.status}.`)
  }
  return r.json() as Promise<ResultadoImportacion>
}
