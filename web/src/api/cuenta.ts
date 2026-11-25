/* SIGRO · lo de la propia cuenta: avisos (notificaciones) y cambiar la contraseña. */
import { claveAceptable } from '../formularios/reglas'
import { ErrorApi } from './error'
import { pedir } from './http'

export interface Aviso { id: number; tipo: string; titulo: string; mensaje: string; datos: Record<string, unknown>; creadaEn: string; leida: boolean }

export const cuenta = {
  /** null = el servicio de avisos no está disponible (503): la campana no se muestra, no es un error para la persona. */
  async avisos(): Promise<{ noLeidas: number; avisos: Aviso[] } | null> {
    try {
      const r = await pedir<{ noLeidas: number; notificaciones: Aviso[] }>('GET', '/notificaciones?limite=30')
      return { noLeidas: r.noLeidas, avisos: r.notificaciones }
    } catch (e) {
      if (e instanceof ErrorApi && (e.estado === 503 || e.estado === 404)) return null
      throw e
    }
  },
  async marcarLeido(id: number): Promise<void> { await pedir('POST', `/notificaciones/${id}/leida`) },
  async cambiarClave(claveActual: string, claveNueva: string): Promise<void> {
    await pedir('POST', '/auth/cambiar-clave', { claveActual, claveNueva })
  },
}

/** Adónde lleva un aviso al tocarlo (según los datos que trae el evento). */
export function destinoDeAviso(a: Pick<Aviso, 'tipo' | 'datos'>): string | null {
  if (a.tipo.startsWith('solicitud.') || 'solicitud_id' in a.datos || 'solicitudId' in a.datos) return '/solicitudes'
  if (a.tipo.startsWith('compra.')) return '/compras'
  if (a.tipo.startsWith('reposicion.') || a.tipo.includes('stock')) return '/reposicion'
  if (a.tipo.startsWith('salida.') || a.tipo.startsWith('movimiento.')) return '/movimientos'
  return null
}

/** Reglas del cambio de clave, campo por campo (validación en vivo). La fuerza sigue la regla del servidor. */
export function erroresCambioClave(actual: string, nueva: string, repetida: string) {
  return {
    actual: actual ? null : 'Escribe tu contraseña actual.',
    nueva: claveAceptable(nueva) ?? (nueva === actual ? 'La nueva contraseña debe ser distinta de la actual.' : null),
    repetida: !repetida ? 'Repite la nueva contraseña.' : repetida !== nueva ? 'Las dos contraseñas nuevas no coinciden.' : null,
  }
}

/** El primer error del cambio de clave (o null): para quien necesita un solo mensaje. */
export function errorCambioClave(actual: string, nueva: string, repetida: string): string | null {
  return Object.values(erroresCambioClave(actual, nueva, repetida)).find(Boolean) ?? null
}
