/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · escala del texto (panel «Aa») y ancho útil de la pantalla

   La escala es un `zoom` del body (tokens.css). Con zoom, el navegador sigue
   midiendo los @media con el ancho REAL de la ventana: a 125 % en una laptop de
   1 100 px el diseño cabe en 880 px, pero el @media de 900 px no se activa y el
   menú lateral aprieta el contenido. Por eso el corte se decide aquí, con el ancho
   útil (ancho de la ventana ÷ escala), y se publica en <html data-ancho>:
     amplio  > 900 px útiles   menú lateral a la izquierda
     medio  ≤ 900 px            menú arriba, en una fila
     chico  ≤ 760 px            además, «Entrar» pone el formulario primero
   No se usan container queries: crean contención y rompen los position: fixed
   (modal, fondo, botón «Aa»).
   ───────────────────────────────────────────────────────────────────────── */

export const ESCALAS = ['90', '100', '110', '125'] as const
export type Escala = (typeof ESCALAS)[number]

export const CORTE_MEDIO = 900
export const CORTE_CHICO = 760

/** «125» → 1.25. Lo que no sea una escala conocida vale 1. */
export function factor(escala: string | null | undefined): number {
  return ESCALAS.includes(escala as Escala) ? Number(escala) / 100 : 1
}

/** Ancho útil en px de CSS una vez aplicado el zoom. */
export function anchoUtil(anchoVentana: number, escala: string | null | undefined): number {
  return anchoVentana / factor(escala)
}

export function tipoDeAncho(anchoVentana: number, escala: string | null | undefined): 'amplio' | 'medio' | 'chico' {
  const w = anchoUtil(anchoVentana, escala)
  return w <= CORTE_CHICO ? 'chico' : w <= CORTE_MEDIO ? 'medio' : 'amplio'
}

/** La escala guardada en el dispositivo (o 100 si no hay o el navegador bloquea el almacenamiento). */
export function escalaGuardada(): Escala {
  try {
    const v = localStorage.getItem('sigro-escala')
    return ESCALAS.includes(v as Escala) ? (v as Escala) : '100'
  } catch {
    return '100'
  }
}

/**
 * Aplica la escala guardada ANTES del primer pintado (sin parpadeo a 100 %) y mantiene `data-ancho` al día
 * cuando cambia el tamaño de la ventana o la escala desde el panel «Aa».
 */
export function vigilarEscala(raiz: HTMLElement = document.documentElement) {
  if (!raiz.dataset.escala) raiz.dataset.escala = escalaGuardada()
  const aplicar = () => { raiz.dataset.ancho = tipoDeAncho(window.innerWidth, raiz.dataset.escala) }
  aplicar()
  window.addEventListener('resize', aplicar)
  new MutationObserver(aplicar).observe(raiz, { attributes: true, attributeFilter: ['data-escala'] })
}
