/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · validación mientras se escribe (la misma regla que la app, 25/11)

   «Premiar pronto, avisar con calma»:
   - lo que la persona CORRIGE deja de marcarse al instante;
   - un error NUEVO aparece tras una pausa corta al escribir (PAUSA_MS), para no
     regañar a mitad de un número («0» antes de «0,5»), y al momento si sale del
     campo (blur) o elige de una lista;
   - un campo que no tocó no se marca, salvo al intentar guardar (se revisa todo).

   Solo decide QUÉ se muestra y CUÁNDO: las reglas son las de cada formulario y el
   servidor vuelve a verificar todo. `visibles` es pura y se prueba sin pantalla.
   ───────────────────────────────────────────────────────────────────────── */
import { useCallback, useEffect, useRef, useState } from 'react'

export const PAUSA_MS = 600

export type Errores<K extends string> = Partial<Record<K, string | null | undefined>>

/** Qué errores se ven: solo los de campos tocados (o todos, tras guardar); uno nuevo espera a que deje de escribir. */
export function visibles<K extends string>(
  todos: Errores<K>, tocados: ReadonlySet<K>, todoTocado: boolean, escribiendo: ReadonlySet<K>, yaVisibles: ReadonlySet<K>,
): Partial<Record<K, string>> {
  const r: Partial<Record<K, string>> = {}
  for (const k of Object.keys(todos) as K[]) {
    const msg = todos[k]
    if (!msg) continue
    if (!todoTocado && !tocados.has(k)) continue
    if (escribiendo.has(k) && !yaVisibles.has(k)) continue // nuevo: espera la pausa
    r[k] = msg
  }
  return r
}

/** Campos tocados que están bien: llevan ✓. */
export function correctos<K extends string>(todos: Errores<K>, tocados: ReadonlySet<K>): Set<K> {
  return new Set([...tocados].filter((k) => !todos[k]))
}

/**
 * Hook para un formulario con estado propio. `todos` son los errores de TODOS los campos con los valores de ahora
 * (se calculan en cada render). Uso:
 *
 *   const v = useEnVivo(reglas(valores))
 *   <Campo … enCambio={(x) => { setCantidad(x); v.cambio('cantidad') }} {...v.campo('cantidad')} />
 *   const guardar = () => { if (v.alGuardar()) return; … }
 */
export function useEnVivo<K extends string>(todos: Errores<K>, pausaMs = PAUSA_MS) {
  const [tocados, setTocados] = useState<ReadonlySet<K>>(() => new Set())
  const [todoTocado, setTodoTocado] = useState(false)
  const [escribiendo, setEscribiendo] = useState<ReadonlySet<K>>(() => new Set())
  const mostrados = useRef<ReadonlySet<K>>(new Set())
  const relojes = useRef(new Map<K, ReturnType<typeof setTimeout>>())
  useEffect(() => () => relojes.current.forEach(clearTimeout), [])

  const errores = visibles(todos, tocados, todoTocado, escribiendo, mostrados.current)
  // lo que se ve ahora: un error que ya estaba a la vista no se esconde mientras sigue mal
  mostrados.current = new Set(Object.keys(errores) as K[])

  const quitar = useCallback((k: K) => setEscribiendo((s) => { if (!s.has(k)) return s; const n = new Set(s); n.delete(k); return n }), [])
  const tocar = useCallback((k: K) => setTocados((s) => (s.has(k) ? s : new Set(s).add(k))), [])

  const cambio = useCallback((k: K) => {
    tocar(k)
    setEscribiendo((s) => (s.has(k) ? s : new Set(s).add(k)))
    clearTimeout(relojes.current.get(k))
    relojes.current.set(k, setTimeout(() => quitar(k), pausaMs))
  }, [pausaMs, quitar, tocar])

  const salir = useCallback((k: K) => {
    tocar(k)
    clearTimeout(relojes.current.get(k))
    quitar(k)
  }, [quitar, tocar])

  /** Revisa todo: devuelve `true` si hay algún error (no se debe guardar). */
  const alGuardar = useCallback(() => {
    relojes.current.forEach(clearTimeout)
    setTodoTocado(true)
    setEscribiendo(new Set())
    return Object.values(todos).some(Boolean)
  }, [todos])

  const reiniciar = useCallback(() => {
    relojes.current.forEach(clearTimeout)
    setTocados(new Set()); setTodoTocado(false); setEscribiendo(new Set())
  }, [])

  const bien = correctos(todos, tocados)
  /** Props para `Campo`: su error, si va con ✓ y qué hacer al salir. */
  const campo = (k: K) => ({ error: errores[k], valido: bien.has(k) && !errores[k], enSalir: () => salir(k) })

  return { errores, correctos: bien, cambio, salir, alGuardar, reiniciar, campo }
}
