/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · datos en vivo: la pantalla vuelve a pedir lo suyo al servidor sola.
   Cada CADA_MS mientras la pestaña está visible y, al volver a ella, enseguida.
   Así, lo que el almacenero registra en el celular aparece en la oficina sin recargar.
   Sondeo simple (no WebSocket): funciona igual en el plan gratuito de Render.
   ───────────────────────────────────────────────────────────────────────── */
import { useCallback, useEffect, useRef, useState } from 'react'

export const CADA_MS = 30_000
/** Al volver a la pestaña se actualiza si lo último tiene más de esto. */
export const VIEJO_MS = 10_000

/** «hace 5 s», «hace 2 min», «hace 1 h». */
export function haceCuanto(ms: number): string {
  const s = Math.max(0, Math.round(ms / 1000))
  if (s < 5) return 'recién'
  if (s < 60) return `hace ${s} s`
  const m = Math.floor(s / 60)
  if (m < 60) return `hace ${m} min`
  return `hace ${Math.floor(m / 60)} h`
}

export interface Actualizacion {
  actualizadoEn: number | null
  actualizando: boolean
  actualizar: () => void
}

/**
 * Llama a [cargar] al montar, cada [cadaMs] con la pestaña visible y al volver a ella.
 * [cargar] devuelve una promesa: mientras corre no se lanza otra (no se apilan pedidos si el servidor tarda).
 */
export function useActualizacion(cargar: () => Promise<unknown>, { cadaMs = CADA_MS, activo = true } = {}): Actualizacion {
  const [actualizadoEn, setActualizadoEn] = useState<number | null>(null)
  const [actualizando, setActualizando] = useState(false)
  const enCurso = useRef(false)
  const ultimo = useRef(0)
  const fn = useRef(cargar)
  fn.current = cargar

  const actualizar = useCallback(() => {
    if (enCurso.current) return
    enCurso.current = true
    setActualizando(true)
    fn.current()
      .catch(() => undefined) // cada pantalla muestra su propio error; aquí solo se marca la hora
      .finally(() => {
        enCurso.current = false
        ultimo.current = Date.now()
        setActualizadoEn(ultimo.current)
        setActualizando(false)
      })
  }, [])

  useEffect(() => {
    if (!activo) return
    actualizar()
    const t = setInterval(() => { if (document.visibilityState === 'visible') actualizar() }, cadaMs)
    const alVolver = () => { if (document.visibilityState === 'visible' && Date.now() - ultimo.current > VIEJO_MS) actualizar() }
    document.addEventListener('visibilitychange', alVolver)
    window.addEventListener('focus', alVolver)
    return () => { clearInterval(t); document.removeEventListener('visibilitychange', alVolver); window.removeEventListener('focus', alVolver) }
  }, [activo, cadaMs, actualizar])

  return { actualizadoEn, actualizando, actualizar }
}

/** Píldora «● En vivo · actualizado hace 12 s · Actualizar» para el encabezado de la pantalla. */
export function EnVivo({ actualizadoEn, actualizando, actualizar }: Actualizacion) {
  const [, tic] = useState(0)
  useEffect(() => { const t = setInterval(() => tic((n) => n + 1), 5_000); return () => clearInterval(t) }, [])
  return (
    <span className="d-inline-flex align-items-center gap-2 small text-body-secondary" role="status" aria-live="polite">
      <span aria-hidden className={`rounded-circle d-inline-block${actualizando ? ' spinner-grow spinner-grow-sm' : ''}`}
        style={{ width: 8, height: 8, background: 'var(--bs-success)' }} />
      <span>
        <strong className="text-body">En vivo</strong>
        {' · '}
        {actualizando ? 'actualizando…' : actualizadoEn ? `actualizado ${haceCuanto(Date.now() - actualizadoEn)}` : 'cargando…'}
      </span>
      <button type="button" className="btn btn-link btn-sm p-0 fw-semibold" onClick={actualizar} disabled={actualizando}>
        Actualizar
      </button>
    </span>
  )
}

/** Cuántos ids nuevos trae [ahora] respecto de [antes] (para avisar «2 movimientos nuevos»). */
export function nuevos<T extends { id: number | string }>(antes: T[] | null, ahora: T[]): number {
  if (!antes) return 0
  const vistos = new Set(antes.map((x) => x.id))
  return ahora.filter((x) => !vistos.has(x.id)).length
}
