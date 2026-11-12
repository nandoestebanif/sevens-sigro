/* ─────────────────────────────────────────────────────────────────────────────
   Reintentos ante fallas pasajeras del servidor (no de la persona).
   En Render un servicio dormido responde 502/503 o corta la conexión mientras
   despierta: eso no es «no se pudo ingresar», es «todavía no». Se reintenta
   solo, con espera creciente; cualquier otra respuesta (401, 422, 429…) es la
   respuesta de verdad y se entrega en el acto.
   ───────────────────────────────────────────────────────────────────────── */
import { ErrorApi } from './error'

/** 502 y 503 los da Render o el gateway mientras un servicio despierta; 504 es el mismo caso por tiempo. */
const PASAJEROS = new Set([502, 503, 504])

export function esPasajero(e: unknown): boolean {
  return e instanceof ErrorApi && (e.codigo === 'sin_conexion' || (e.estado !== undefined && PASAJEROS.has(e.estado)))
}

export const ESPERAS_LOGIN = [1000, 2000, 4000] as const // 3 reintentos: 1 s, 2 s y 4 s

export interface OpcionesReintento {
  esperas?: readonly number[]
  /** se llama ANTES de cada reintento, con su número (1, 2, 3) */
  alReintentar?: (intento: number) => void
  dormir?: (ms: number) => Promise<void>
}

const dormirDe = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))

export async function conReintentos<T>(hacer: () => Promise<T>, o: OpcionesReintento = {}): Promise<T> {
  const esperas = o.esperas ?? ESPERAS_LOGIN
  const dormir = o.dormir ?? dormirDe
  for (let intento = 0; ; intento++) {
    try {
      return await hacer()
    } catch (e) {
      if (!esPasajero(e)) throw e
      if (intento >= esperas.length) {
        throw new ErrorApi('sin_servidor', 'No pudimos conectar con el servidor de SIGRO. Inténtalo de nuevo en un momento.')
      }
      o.alReintentar?.(intento + 1)
      await dormir(esperas[intento])
    }
  }
}
