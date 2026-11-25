/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · paleta del rediseño (24/11) en TypeScript
   Los mismos colores de estilos/tema.scss, para lo que se dibuja fuera del CSS
   (gráficos de Chart.js) y para PROBAR el contraste de cada par texto/fondo
   (estilos/contraste.test.ts). Si se cambia un color aquí, cambiarlo también en tema.scss.
   ───────────────────────────────────────────────────────────────────────── */

export const MARCA = { negro: '#161717', fondo: '#242424', tarjeta: '#2b2c2c', amarillo: '#fad85d' } as const

/** Datos: violeta, menta y amarillo primero (recibido, consumido, en stock), luego rosa, lila y naranja. */
export const PALETA = { violeta: '#7c6cf6', menta: '#2ee6c5', amarillo: '#fad85d', rosa: '#ff8fa3', lila: '#f2a6ff', naranja: '#ffb86b', rojo: '#ff6b6b' } as const

/** Fondos de las tarjetas «ticket», en el orden en que se turnan. */
export const TICKETS = ['#2ee6c5', '#fad85d', '#f2a6ff', '#ffb3c1', '#ffc58f'] as const

/** Texto de cada tono de Chip en oscuro (sobre la tarjeta) y en claro (sobre blanco). */
export const CHIP = {
  oscuro: { bien: '#2ee6c5', aviso: '#f5c451', grave: '#ff9a9a', info: '#c9b8ff', marca: '#fad85d', neutro: '#d2d3d3' },
  claro: { bien: '#0b7a64', aviso: '#8a5a00', grave: '#b42318', info: '#5a4bd1', marca: '#7a5d00', neutro: '#5c5d5d' },
} as const

/** Luminancia relativa (WCAG 2.1). */
function luminancia(hex: string): number {
  const n = hex.replace('#', '')
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(n.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** Contraste WCAG entre dos colores (de 1 a 21). AA pide 4.5 para texto normal y 3 para texto grande. */
export function contraste(a: string, b: string): number {
  const [x, y] = [luminancia(a), luminancia(b)].sort((p, q) => q - p)
  return (x + 0.05) / (y + 0.05)
}
