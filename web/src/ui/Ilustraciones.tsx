/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · ilustraciones de obra para las tarjetas «ticket» (rediseño 24/11)
   Dibujos propios en SVG (sin imágenes externas): pesan poco, se ven nítidos a
   cualquier tamaño y funcionan sin internet. Son decorativos: aria-hidden.
   ───────────────────────────────────────────────────────────────────────── */
import { useId, type ReactNode } from 'react'

export type Ilustracion = 'factura' | 'camion' | 'cemento' | 'grua' | 'casco' | 'calendario'

function Lienzo({ ancho, children }: { ancho: number; children: ReactNode }) {
  return (
    <svg viewBox="0 0 140 124" width={ancho} height={Math.round((ancho * 124) / 140)} aria-hidden="true" focusable="false" style={{ display: 'block' }}>
      {children}
    </svg>
  )
}

const Sombra = ({ rx = 54 }: { rx?: number }) => <ellipse cx="70" cy="112" rx={rx} ry="8" fill="#000" opacity=".2" />

function Factura() {
  return (
    <>
      <Sombra rx={52} />
      <path d="M96 12 L106 18 V100 L96 106 Z" fill="#b9e8de" />
      <path d="M34 12 H96 V106 l-7.75-6 -7.75 6 -7.75-6 -7.75 6 -7.75-6 -7.75 6 -7.75-6 -7.75 6 Z" fill="#fff" />
      <rect x="34" y="12" width="62" height="18" fill="#161717" />
      <text x="65" y="25" textAnchor="middle" fontFamily="Poppins, sans-serif" fontWeight="700" fontSize="9" fill="#fad85d">FACTURA</text>
      <g fill="#d9dede"><rect x="42" y="38" width="46" height="5" rx="2.5" /><rect x="42" y="50" width="34" height="5" rx="2.5" /><rect x="42" y="62" width="40" height="5" rx="2.5" /></g>
      <rect x="42" y="76" width="46" height="12" rx="4" fill="#fad85d" />
      <ellipse cx="114" cy="104" rx="14" ry="5" fill="#c99500" /><rect x="100" y="92" width="28" height="12" fill="#e6b422" />
      <ellipse cx="114" cy="92" rx="14" ry="5" fill="#ffe680" /><rect x="100" y="80" width="28" height="12" fill="#e6b422" />
      <ellipse cx="114" cy="80" rx="14" ry="5" fill="#fff0a8" />
    </>
  )
}

function Camion() {
  return (
    <>
      <Sombra rx={60} />
      <path d="M10 40 H84 V92 H10 Z" fill="#fff" /><path d="M84 40 L92 34 V86 L84 92 Z" fill="#d9d9d9" /><path d="M10 40 L18 34 H92 L84 40 Z" fill="#f4f4f4" />
      <g fill="#fad85d" stroke="#161717" strokeWidth="1.2">
        <rect x="18" y="56" width="20" height="10" rx="1.5" /><rect x="40" y="56" width="20" height="10" rx="1.5" /><rect x="29" y="46" width="20" height="10" rx="1.5" />
        <rect x="18" y="66" width="20" height="10" rx="1.5" /><rect x="40" y="66" width="20" height="10" rx="1.5" />
      </g>
      <path d="M92 52 H114 L128 70 V94 H92 Z" fill="#161717" /><path d="M97 57 H112 L122 70 H97 Z" fill="#9fd8ff" />
      <rect x="6" y="90" width="124" height="8" rx="3" fill="#353636" />
      <circle cx="32" cy="100" r="11" fill="#161717" /><circle cx="32" cy="100" r="4.5" fill="#d2d3d3" />
      <circle cx="108" cy="100" r="11" fill="#161717" /><circle cx="108" cy="100" r="4.5" fill="#d2d3d3" />
    </>
  )
}

function Cemento() {
  const id = useId()
  return (
    <>
      <defs>
        <linearGradient id={`${id}f`} x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#fbf7ee" /><stop offset="1" stopColor="#c4b99f" /></linearGradient>
        <linearGradient id={`${id}l`} x1="0" x2="1"><stop offset="0" stopColor="#cfc4aa" /><stop offset="1" stopColor="#93876d" /></linearGradient>
      </defs>
      <Sombra />
      <path d="M98 30 L116 40 L116 102 L98 109 Z" fill={`url(#${id}l)`} />
      <path d="M28 28 Q63 19 98 30 L98 109 Q63 115 28 106 Z" fill={`url(#${id}f)`} />
      <path d="M28 28 Q63 19 98 30 L116 40 Q80 31 46 35 Z" fill="#fffdf7" />
      <path d="M30 32 Q63 24 96 33" fill="none" stroke="#b5aa90" strokeWidth="1.4" strokeDasharray="3 3" />
      <path d="M28 58 Q63 51 98 60 L98 82 Q63 75 28 81 Z" fill="#161717" />
      <text x="63" y="72" textAnchor="middle" fontFamily="Poppins, sans-serif" fontWeight="700" fontSize="11" fill="#fad85d">CEMENTO</text>
      <text x="63" y="96" textAnchor="middle" fontFamily="Poppins, sans-serif" fontWeight="600" fontSize="8" fill="#7d725b">TIPO I · 42.5 kg</text>
    </>
  )
}

function Grua() {
  return (
    <>
      <Sombra rx={56} />
      <path d="M14 56 L52 56 L52 108 L14 108 Z" fill="#fff" /><path d="M52 56 L64 50 L64 102 L52 108 Z" fill="#d9d9d9" /><path d="M14 56 L26 50 L64 50 L52 56 Z" fill="#f4f4f4" />
      <g fill="#9a9b9b"><rect x="20" y="64" width="8" height="8" rx="1.5" /><rect x="38" y="64" width="8" height="8" rx="1.5" /><rect x="20" y="80" width="8" height="8" rx="1.5" /><rect x="38" y="80" width="8" height="8" rx="1.5" /><rect x="29" y="96" width="9" height="12" rx="1.5" /></g>
      <g stroke="#161717" strokeWidth="2.6" fill="none" strokeLinejoin="round">
        <path d="M92 108 L92 20 M104 108 L104 20" /><path d="M92 100 L104 88 L92 76 L104 64 L92 52 L104 40 L92 28" />
        <path d="M34 18 L132 18 M34 26 L126 26 M34 18 L34 26" /><path d="M98 6 L34 18 M98 6 L132 18 M98 6 L98 20" />
      </g>
      <rect x="116" y="27" width="16" height="12" rx="2" fill="#161717" /><path d="M50 26 L50 46" stroke="#161717" strokeWidth="1.6" />
      <rect x="30" y="46" width="40" height="7" rx="2" fill="#fad85d" stroke="#161717" strokeWidth="1.4" /><rect x="84" y="106" width="28" height="6" rx="2" fill="#161717" />
    </>
  )
}

function Casco() {
  const id = useId()
  return (
    <>
      <defs>
        <radialGradient id={id} cx=".35" cy=".3" r=".8"><stop offset="0" stopColor="#fff3b0" /><stop offset=".55" stopColor="#fad85d" /><stop offset="1" stopColor="#d9a514" /></radialGradient>
      </defs>
      <Sombra rx={56} />
      <path d="M24 82 Q24 26 70 24 Q116 26 116 82 Z" fill={`url(#${id})`} />
      <path d="M61 25 Q70 19 79 25 L82 80 L58 80 Z" fill="#ffe680" />
      <path d="M36 80 Q38 50 54 34" fill="none" stroke="#fff" strokeOpacity=".6" strokeWidth="5" strokeLinecap="round" />
      <path d="M8 84 Q70 70 132 84 Q134 98 70 98 Q6 98 8 84 Z" fill="#c99500" />
      <path d="M8 84 Q70 70 132 84" fill="none" stroke="#ffe680" strokeWidth="2" />
    </>
  )
}

function Calendario() {
  return (
    <>
      <Sombra rx={50} />
      <rect x="26" y="20" width="84" height="84" rx="14" fill="#fff" /><path d="M110 30 L118 36 V104 L110 104 Z" fill="#e3c6f0" />
      <rect x="26" y="20" width="84" height="24" rx="12" fill="#161717" /><rect x="26" y="32" width="84" height="12" fill="#161717" />
      <rect x="44" y="12" width="8" height="18" rx="4" fill="#fad85d" /><rect x="84" y="12" width="8" height="18" rx="4" fill="#fad85d" />
      <g fill="#e6e6e6"><circle cx="44" cy="58" r="5" /><circle cx="62" cy="58" r="5" /><circle cx="80" cy="58" r="5" /><circle cx="98" cy="58" r="5" /><circle cx="44" cy="76" r="5" /><circle cx="62" cy="76" r="5" /><circle cx="98" cy="76" r="5" /><circle cx="44" cy="92" r="5" /><circle cx="62" cy="92" r="5" /></g>
      <circle cx="80" cy="76" r="9" fill="#fad85d" stroke="#161717" strokeWidth="2" />
    </>
  )
}

const DIBUJOS: Record<Ilustracion, () => JSX.Element> = {
  factura: Factura, camion: Camion, cemento: Cemento, grua: Grua, casco: Casco, calendario: Calendario,
}

/** Ilustración decorativa de obra (no la lee el lector de pantalla). */
export function DibujoObra({ cual, ancho = 76 }: { cual: Ilustracion; ancho?: number }) {
  const Dibujo = DIBUJOS[cual]
  return <Lienzo ancho={ancho}><Dibujo /></Lienzo>
}
