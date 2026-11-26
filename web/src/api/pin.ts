/* SIGRO · PIN del panel de administración (backend Spring, migración 0024).
   El token del PIN dura 15 minutos y vive solo en esta pestaña (sessionStorage): al cerrarla hay que volver a
   escribirlo. Nunca se guarda el PIN. El servidor lo exige (cabecera X-SIGRO-PIN) en usuarios, asignaciones,
   celulares y bitácora; aquí solo se recuerda el token para no pedirlo en cada clic. */
const CLAVE = 'sigro.pin'
let enMemoria: { token: string; vence: number } | null = null

function leer(): { token: string; vence: number } | null {
  if (enMemoria) return enMemoria
  try { const t = sessionStorage.getItem(CLAVE); enMemoria = t ? JSON.parse(t) : null } catch { enMemoria = null }
  return enMemoria
}

/** Token de PIN vigente (o null). Lo usa pedir() para la cabecera X-SIGRO-PIN. */
export function tokenPin(ahora = Date.now()): string | null {
  const t = leer()
  return t && t.vence > ahora ? t.token : null
}

export function guardarPin(token: string, segundos = 900, ahora = Date.now()) {
  enMemoria = { token, vence: ahora + segundos * 1000 - 5_000 }
  try { sessionStorage.setItem(CLAVE, JSON.stringify(enMemoria)) } catch { /* sin almacenamiento: queda en memoria */ }
  window.dispatchEvent(new Event('sigro:pin'))
}

export function olvidarPin() {
  enMemoria = null
  try { sessionStorage.removeItem(CLAVE) } catch { /* nada */ }
  window.dispatchEvent(new Event('sigro:pin'))
}

/** PIN de 6 dígitos que no sea repetido ni seguido (igual que el servidor). */
export function pinAceptable(pin: string): string | null {
  if (!/^\d{6}$/.test(pin)) return 'El PIN son 6 dígitos.'
  if (new Set(pin).size === 1 || '0123456789'.includes(pin) || '9876543210'.includes(pin)) return 'Ese PIN es muy fácil de adivinar (repetido o seguido).'
  return null
}

export interface FilaBitacora { id: number; momento: string; usuarioId: number | null; usuario: string | null; metodo: string; ruta: string; estado: number; duracionMs: number }

/** Filtros de la bitácora; el servidor los combina (fechas de Lima, inclusive). */
export interface FiltroBitacora { desde?: string; hasta?: string; usuarioId?: number; q?: string; solo?: '' | 'cambios' | 'rechazos' | 'errores' }

export function consultaBitacora(f: FiltroBitacora = {}): string {
  const p = new URLSearchParams({ limite: '500' })
  if (f.desde) p.set('desde', f.desde)
  if (f.hasta) p.set('hasta', f.hasta)
  if (f.usuarioId) p.set('usuario_id', String(f.usuarioId))
  if (f.q?.trim()) p.set('q', f.q.trim().slice(0, 60))
  if (f.solo) p.set('solo', f.solo)
  return p.toString()
}
