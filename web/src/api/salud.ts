/* ─────────────────────────────────────────────────────────────────────────────
   Esperar a que el servidor esté listo antes de mostrar la web.
   En Render cada servicio se duerme y tarda 20-30 s en despertar. Una sola
   llamada al gateway, GET /salud/servicios, los despierta a todos a la vez y
   dice cuáles ya responden. Se pregunta cada 2 s hasta que todos estén
   listos; a los 90 s se deja de esperar y la pantalla ofrece «Reintentar».
   ───────────────────────────────────────────────────────────────────────── */
import { API_URL } from './http'

export interface SaludServicio { servicio: string; listo: boolean; estado: string; ms: number }
export interface Salud { listo: boolean; servicios: SaludServicio[] }

export type FaseArranque = 'preparando' | 'despertando' | 'listo' | 'sin_conexion'
export interface EstadoArranque { fase: FaseArranque; listos: number; total: number }

export const CADA_MS = 2000      // entre una consulta y la siguiente
export const AVISO_MS = 5000     // desde aquí se dice «el servidor está despertando»
export const LIMITE_MS = 90000   // desde aquí, «No pudimos conectar» y Reintentar
const ESPERA_CONSULTA_MS = 12000 // una consulta colgada no detiene la espera

/** Una consulta. Nunca lanza: si el gateway mismo duerme (502 de Render, red caída) responde «nada listo». */
export async function consultarSalud(senal?: AbortSignal): Promise<Salud | null> {
  const corte = new AbortController()
  const reloj = setTimeout(() => corte.abort(), ESPERA_CONSULTA_MS)
  const cancelar = () => corte.abort()
  senal?.addEventListener('abort', cancelar)
  try {
    const r = await fetch(`${API_URL}/salud/servicios`, { cache: 'no-store', signal: corte.signal })
    const datos = (await r.json().catch(() => null)) as Salud | null   // 503 también trae el detalle
    return datos && Array.isArray(datos.servicios) ? datos : null
  } catch {
    return null
  } finally {
    clearTimeout(reloj)
    senal?.removeEventListener('abort', cancelar)
  }
}

export interface OpcionesEspera {
  consultar?: (senal: AbortSignal) => Promise<Salud | null>
  dormir?: (ms: number, senal: AbortSignal) => Promise<void>
  ahora?: () => number
  alCambiar: (e: EstadoArranque) => void
  senal: AbortSignal
}

const dormirDe = (ms: number, senal: AbortSignal) => new Promise<void>((listo) => {
  const t = setTimeout(listo, ms)
  senal.addEventListener('abort', () => { clearTimeout(t); listo() }, { once: true })
})

/** Pregunta hasta que todos estén listos (devuelve true) o se cumpla el límite (false). Cancelable. */
export async function esperarServidor(o: OpcionesEspera): Promise<boolean> {
  const consultar = o.consultar ?? consultarSalud
  const dormir = o.dormir ?? dormirDe
  const ahora = o.ahora ?? (() => Date.now())
  const inicio = ahora()
  let listos = 0
  let total = 0
  for (;;) {
    if (o.senal.aborted) return false
    const s = await consultar(o.senal)
    if (o.senal.aborted) return false
    if (s) { listos = s.servicios.filter((x) => x.listo).length; total = s.servicios.length }
    if (s?.listo) { o.alCambiar({ fase: 'listo', listos, total }); return true }
    const pasado = ahora() - inicio
    if (pasado >= LIMITE_MS) { o.alCambiar({ fase: 'sin_conexion', listos, total }); return false }
    o.alCambiar({ fase: pasado >= AVISO_MS ? 'despertando' : 'preparando', listos, total })
    // (la pantalla lleva además su propio reloj de 5 s, por si una consulta en curso tarda)
    await dormir(Math.min(CADA_MS, Math.max(0, LIMITE_MS - pasado)), o.senal)
  }
}
