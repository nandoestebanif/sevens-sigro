/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · piezas del estilo vidrio (lienzo «SIGRO Oficina · Web»)
   Las páginas migradas componen con estas piezas; las demás siguen con
   ui/base.tsx hasta su turno. Todo color sale de tokens.css, así el modo
   oscuro y el contraste se resuelven en un solo lugar.
   ───────────────────────────────────────────────────────────────────────── */
import { type CSSProperties, type ReactNode } from 'react'

/* ── fondo de plano: degradado cálido, retícula de 24 y 120 px, tres manchas
   de luz y un dibujo propio de cada página. Fijo detrás de todo: el vidrio
   de las tarjetas lo desenfoca. Se dibuja FUERA de .pagina, porque la
   animación de entrada usa transform y ataría el fondo a la página. ── */
const DIBUJOS = {
  entrar: {
    manchas: [[1150, 640, 380, 'naranja'], [220, 820, 340, 'azul'], [700, 120, 260, 'turquesa']] as const,
    trazo: (
      <>
        <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1" opacity="0.4" strokeDasharray="8 6">
          <path d="M80 120v720M440 120v720M800 120v720M1160 120v720M40 200h1360M40 520h1360M40 820h1360" />
        </g>
        <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.3" opacity="0.55">
          {[80, 440, 800, 1160].map((x) => <circle key={x} cx={x} cy="104" r="14" />)}
        </g>
        <g style={{ fill: 'var(--plano-trazo)' }} opacity="0.6" fontFamily="Inter, sans-serif" fontSize="12" fontWeight="700" textAnchor="middle">
          {['A', 'B', 'C', 'D'].map((l, i) => <text key={l} x={80 + i * 360} y="108">{l}</text>)}
        </g>
        <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="4" opacity="0.4">
          <path d="M80 200h300M500 200h660v320M1160 600v220H860M760 820H80V600M80 520V200M800 200v200M800 480v340M80 520h300M520 520h640" />
        </g>
        <g fill="none" style={{ stroke: 'var(--plano-cota)' }} strokeWidth="1.2" opacity="0.55">
          <path d="M80 860h1080M80 852v16M1160 852v16" />
        </g>
        <text x="590" y="884" fontFamily="Inter, sans-serif" fontSize="12" style={{ fill: 'var(--plano-cota)' }} opacity="0.75">32.40 m</text>
      </>
    ),
  },
  importar: {
    manchas: [[1250, 260, 340, 'azul'], [180, 820, 360, 'naranja'], [900, 1080, 300, 'turquesa']] as const,
    trazo: (
      <>
        <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.3" opacity="0.45">
          <path d="M1180 140h200v260h-200z" />
          <path d="M1180 180h200M1180 220h200M1180 260h200M1180 300h200M1180 340h200M1240 140v260M1320 140v260" />
          <path d="M1150 170h200v260h-200" strokeDasharray="4 4" />
        </g>
        <text x="1180" y="426" fontFamily="Inter, sans-serif" fontSize="12" style={{ fill: 'var(--plano-trazo)' }} opacity="0.7">RELACIÓN DE INSUMOS</text>
      </>
    ),
  },
}
export type DibujoPlano = keyof typeof DIBUJOS

export function Fondo({ dibujo }: { dibujo: DibujoPlano }) {
  const d = DIBUJOS[dibujo]
  const p = `plano-${dibujo}`
  const mancha = (color: string) => (
    <radialGradient id={`${p}-m${color}`} cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" style={{ stopColor: `var(--plano-mancha-${color})` }} stopOpacity={color === 'naranja' ? 0.45 : color === 'azul' ? 0.38 : 0.3} />
      <stop offset="1" style={{ stopColor: `var(--plano-mancha-${color})` }} stopOpacity="0" />
    </radialGradient>
  )
  return (
    <svg
      aria-hidden="true" width="100%" height="100%" viewBox="0 0 1440 900" preserveAspectRatio="xMidYMid slice"
      style={{ position: 'fixed', inset: 0, zIndex: -1, pointerEvents: 'none' }}
    >
      <defs>
        <linearGradient id={`${p}-f`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" style={{ stopColor: 'var(--plano-0)' }} />
          <stop offset="0.55" style={{ stopColor: 'var(--plano-1)' }} />
          <stop offset="1" style={{ stopColor: 'var(--plano-2)' }} />
        </linearGradient>
        <pattern id={`${p}-c`} width="24" height="24" patternUnits="userSpaceOnUse">
          <path d="M24 0H0V24" fill="none" style={{ stroke: 'var(--plano-reticula)' }} strokeWidth="0.5" opacity="0.4" />
        </pattern>
        <pattern id={`${p}-C`} width="120" height="120" patternUnits="userSpaceOnUse">
          <path d="M120 0H0V120" fill="none" style={{ stroke: 'var(--plano-reticula-mayor)' }} strokeWidth="0.8" opacity="0.3" />
        </pattern>
        {mancha('naranja')}{mancha('azul')}{mancha('turquesa')}
      </defs>
      <rect width="1440" height="900" fill={`url(#${p}-f)`} />
      <rect width="1440" height="900" fill={`url(#${p}-c)`} />
      <rect width="1440" height="900" fill={`url(#${p}-C)`} />
      {d.manchas.map(([x, y, r, c]) => <circle key={`${x}-${y}`} cx={x} cy={y} r={r} fill={`url(#${p}-m${c})`} />)}
      {d.trazo}
    </svg>
  )
}

/** Superficie de vidrio. «fuerte» para lo que debe leerse por encima de todo (menú, formulario de entrada). */
export function Vidrio({ children, fuerte, estilo, etiqueta }: {
  children: ReactNode
  fuerte?: boolean
  estilo?: CSSProperties
  etiqueta?: string
}) {
  return (
    <section
      aria-label={etiqueta}
      className={fuerte ? 'vidrio-fuerte' : 'vidrio'}
      style={{ borderRadius: 'var(--radio-vidrio)', padding: '20px 22px', minWidth: 0, ...estilo }}
    >
      {children}
    </section>
  )
}

/** Botón en píldora: negro el principal, vidrio claro el secundario. Mínimo 44 px de alto (táctil). */
export function Pildora({ children, tipo = 'primario', deshabilitado, enClic, anchoCompleto, grande, enviar }: {
  children: ReactNode
  tipo?: 'primario' | 'secundario'
  deshabilitado?: boolean
  enClic?: () => void
  anchoCompleto?: boolean
  grande?: boolean
  /** type="submit": envía el formulario que lo contiene */
  enviar?: boolean
}) {
  const alto = grande ? 56 : tipo === 'primario' ? 48 : 44
  return (
    <button
      type={enviar ? 'submit' : 'button'}
      onClick={enClic}
      disabled={deshabilitado}
      className="foco-vidrio"
      style={{
        minHeight: alto, padding: tipo === 'primario' ? '0 22px' : '0 18px', borderRadius: alto / 2,
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
        fontFamily: 'inherit', fontWeight: 600, fontSize: grande ? 16 : tipo === 'primario' ? 15 : 14,
        cursor: deshabilitado ? 'not-allowed' : 'pointer', opacity: deshabilitado ? 0.55 : 1,
        width: anchoCompleto ? '100%' : undefined,
        ...(tipo === 'primario'
          ? { background: 'var(--pildora)', color: 'var(--pildora-tinta)', border: 0, boxShadow: 'var(--pildora-sombra)' }
          : { background: 'var(--pildora-2)', color: 'var(--pildora-2-tinta)', border: '1px solid var(--pildora-2-borde)' }),
      }}
    >
      {children}
    </button>
  )
}

/** Encabezado de página: título grande a la izquierda, acciones a la derecha; en angosto se apilan. */
export function Encabezado({ titulo, sub, acciones }: { titulo: ReactNode; sub?: ReactNode; acciones?: ReactNode }) {
  return (
    <header style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', justifyContent: 'space-between', gap: 14, padding: '8px 4px 0' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
        <h1 style={{ fontSize: 'clamp(26px, 4vw, 34px)', fontWeight: 800, letterSpacing: '-0.03em' }}>{titulo}</h1>
        {sub && <p style={{ fontSize: 15, color: 'var(--tinta-2)', lineHeight: 1.45 }}>{sub}</p>}
      </div>
      {acciones && <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 10 }}>{acciones}</div>}
    </header>
  )
}

export function TituloSeccion({ children }: { children: ReactNode }) {
  return <h2 style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.01em' }}>{children}</h2>
}
