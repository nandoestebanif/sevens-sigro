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
  // W2 · grúa torre y edificio en obra
  panel: {
    manchas: [[1300, 160, 360, 'naranja'], [200, 900, 380, 'azul'], [760, 1100, 300, 'turquesa']] as const,
    trazo: (
      <>
        <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.6" opacity="0.4">
          <path d="M1240 900V260M1240 260H1010M1240 260h120M1010 260v50M990 310h40v20h-40zM1240 300l-30 30M1240 360l-30 30M1240 420l-30 30M1240 480l-30 30M1210 300v600" />
          <path d="M1300 900V560h110v340M1300 610h110M1300 660h110M1300 710h110M1300 760h110M1300 810h110M1355 560v340" />
        </g>
        <g fill="none" style={{ stroke: 'var(--plano-cota)' }} strokeWidth="1.2" opacity="0.5"><path d="M1440 560v340M1432 560h16M1432 900h16" /></g>
      </>
    ),
  },
  // W4 · estantería de almacén en isométrico
  catalogo: {
    manchas: [[1260, 200, 340, 'naranja'], [200, 760, 360, 'azul'], [900, 960, 300, 'turquesa']] as const,
    trazo: (
      <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.4" opacity="0.42" strokeLinejoin="round">
        <path d="M1150 600l200-100 50 25v320l-200 100-50-25z" /><path d="M1150 600l50 25 200-100M1200 625v320" />
        <path d="M1150 700l50 25 200-100M1150 800l50 25 200-100" /><path d="M1230 660l40-20v28l-40 20zM1290 630l40-20v28l-40 20zM1240 760l50-25v26l-50 25z" />
      </g>
    ),
  },
  // W5 · el «diente de sierra» del inventario con su punto de reposición
  reposicion: {
    manchas: [[1200, 300, 340, 'naranja'], [240, 780, 360, 'turquesa'], [820, 980, 300, 'azul']] as const,
    trazo: (
      <>
        <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.6" opacity="0.5">
          <path d="M960 200 L1060 360 L1060 200 L1160 360 L1160 200 L1260 360 L1260 200 L1360 360" />
          <path d="M960 200v160M1060 200v160M1160 200v160M1260 200v160" strokeDasharray="3 5" strokeWidth="1" />
          <path d="M940 380h420M940 380V170" />
        </g>
        <g fill="none" style={{ stroke: 'var(--plano-cota)' }} strokeWidth="1.4" opacity="0.6" strokeDasharray="8 5"><path d="M940 300h420" /></g>
        <text x="1270" y="292" fontFamily="Inter, sans-serif" fontSize="12" style={{ fill: 'var(--plano-cota)' }} opacity="0.8">punto de reposición</text>
      </>
    ),
  },
  // W6 · comprobante y camión de reparto
  compras: {
    manchas: [[1260, 220, 340, 'azul'], [260, 760, 360, 'naranja'], [860, 1080, 300, 'turquesa']] as const,
    trazo: (
      <>
        <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.4" opacity="0.45">
          <path d="M1200 120h160v240l-20-14-20 14-20-14-20 14-20-14-20 14-20-14-20 14z" /><path d="M1225 170h110M1225 200h110M1225 230h80M1225 290h110" />
        </g>
        <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.4" opacity="0.4">
          <path d="M120 760h260v120H120zM380 800h80l40 50v30H380M170 900a20 20 0 1 0 0 1M430 900a20 20 0 1 0 0 1" />
        </g>
      </>
    ),
  },
  // proveedores (sin lienzo propio): la ficha del RUC y el camión del lienzo de compras
  proveedores: {
    manchas: [[1240, 260, 340, 'turquesa'], [220, 800, 360, 'naranja'], [880, 1060, 300, 'azul']] as const,
    trazo: (
      <>
        <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.4" opacity="0.45" strokeLinejoin="round">
          <path d="M1180 150h200v130h-200zM1200 180h70v70h-70zM1290 190h70M1290 215h70M1290 240h45" />
          <path d="M1180 320h200M1180 345h150" strokeDasharray="4 4" />
        </g>
        <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.4" opacity="0.4">
          <path d="M120 760h260v120H120zM380 800h80l40 50v30H380M170 900a20 20 0 1 0 0 1M430 900a20 20 0 1 0 0 1" />
        </g>
      </>
    ),
  },
  // W7 · el almacén con su flecha de entrada y de salida
  movimientos: {
    manchas: [[1280, 260, 340, 'turquesa'], [200, 820, 360, 'naranja'], [860, 1100, 300, 'azul']] as const,
    trazo: (
      <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.6" opacity="0.45" strokeLinecap="round" strokeLinejoin="round">
        <path d="M1160 360V200l120-60 120 60v160z" /><path d="M1230 360v-80h100v80" />
        <path d="M1060 260h80M1120 240l20 20-20 20" style={{ stroke: 'var(--bien)' }} />
        <path d="M1400 300h70M1450 280l20 20-20 20" style={{ stroke: 'var(--grave)' }} />
      </g>
    ),
  },
  // W8 · casco de obra y llave del celular autorizado
  accesos: {
    manchas: [[1260, 260, 340, 'azul'], [220, 780, 360, 'naranja'], [860, 960, 300, 'turquesa']] as const,
    trazo: (
      <g fill="none" style={{ stroke: 'var(--plano-trazo)' }} strokeWidth="1.5" opacity="0.45" strokeLinecap="round" strokeLinejoin="round">
        <path d="M1220 260a80 80 0 0 1 160 0z" /><path d="M1190 260h220M1300 160v30" />
        <path d="M1330 420a40 40 0 1 0 0 1M1360 420h80l-10 14 10 14" />
      </g>
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

/** Estilo de la píldora: lo usan el botón y los enlaces que parecen botón (navegar no es una acción). */
export function estiloPildora(tipo: 'primario' | 'secundario' = 'primario', grande?: boolean, deshabilitado?: boolean): CSSProperties {
  const alto = grande ? 56 : tipo === 'primario' ? 48 : 44
  return {
    minHeight: alto, padding: tipo === 'primario' ? '0 22px' : '0 18px', borderRadius: alto / 2,
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8, textDecoration: 'none',
    fontFamily: 'inherit', fontWeight: 600, fontSize: grande ? 16 : tipo === 'primario' ? 15 : 14,
    cursor: deshabilitado ? 'not-allowed' : 'pointer', opacity: deshabilitado ? 0.55 : 1,
    ...(tipo === 'primario'
      ? { background: 'var(--pildora)', color: 'var(--pildora-tinta)', border: 0, boxShadow: 'var(--pildora-sombra)' }
      : { background: 'var(--pildora-2)', color: 'var(--pildora-2-tinta)', border: '1px solid var(--pildora-2-borde)' }),
  }
}

/** Botón en píldora: negro el principal, vidrio claro el secundario. Mínimo 44 px de alto (táctil). */
export function Pildora({ children, tipo = 'primario', deshabilitado, enClic, anchoCompleto, grande, enviar, chica, etiqueta }: {
  children: ReactNode
  tipo?: 'primario' | 'secundario'
  deshabilitado?: boolean
  enClic?: () => void
  anchoCompleto?: boolean
  grande?: boolean
  /** type="submit": envía el formulario que lo contiene */
  enviar?: boolean
  /** dentro de una fila de tabla: 40 px de alto */
  chica?: boolean
  /** aria-label, cuando el texto visible no basta fuera de contexto */
  etiqueta?: string
}) {
  return (
    <button
      type={enviar ? 'submit' : 'button'}
      onClick={enClic}
      disabled={deshabilitado}
      aria-label={etiqueta}
      className="foco-vidrio"
      style={{
        ...estiloPildora(tipo, grande, deshabilitado),
        ...(chica ? { minHeight: 40, borderRadius: 20, padding: '0 14px', fontSize: 13.5 } : {}),
        width: anchoCompleto ? '100%' : undefined,
      }}
    >
      {children}
    </button>
  )
}

/** Cifra clave de la página sobre vidrio: rótulo, valor grande y pie que la explica. */
export function Kpi({ rotulo, valor, pie, prefijo, color }: {
  rotulo: string
  valor: ReactNode
  pie?: ReactNode
  prefijo?: string
  color?: string
}) {
  return (
    <div className="vidrio" style={{ borderRadius: 'var(--radio-vidrio)', padding: '18px 20px', flex: '1 1 200px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--tinta-2)' }}>{rotulo}</span>
      <span style={{ display: 'flex', alignItems: 'baseline' }}>
        {prefijo && <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--tinta-2)', marginRight: 4 }}>{prefijo}</span>}
        <span className="n" style={{ fontSize: 'clamp(24px, 3vw, 30px)', fontWeight: 800, letterSpacing: '-0.03em', color: color ?? 'var(--tinta)' }}>{valor}</span>
      </span>
      {pie && <span style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>{pie}</span>}
    </div>
  )
}

/** Control segmentado en píldora (filtros y pestañas): la opción elegida en negro. */
export function Segmentado<T extends string | number | boolean>({ opciones, valor, enCambio, etiqueta, pestanas }: {
  opciones: { valor: T; texto: ReactNode }[]
  valor: T
  enCambio: (v: T) => void
  etiqueta: string
  /** role="tablist" en vez de un grupo de botones conmutables */
  pestanas?: boolean
}) {
  return (
    <div
      role={pestanas ? 'tablist' : 'group'} aria-label={etiqueta}
      style={{ display: 'flex', flexWrap: 'wrap', gap: 4, background: 'var(--vidrio-celda)', border: '1px solid var(--separador)', borderRadius: 24, padding: 3, alignSelf: 'flex-start' }}
    >
      {opciones.map((o) => {
        const activo = o.valor === valor
        return (
          <button
            key={String(o.valor)} type="button" className="foco-vidrio"
            role={pestanas ? 'tab' : undefined}
            aria-selected={pestanas ? activo : undefined}
            aria-pressed={pestanas ? undefined : activo}
            onClick={() => enCambio(o.valor)}
            style={{
              minHeight: 40, padding: '0 16px', border: 0, borderRadius: 20, fontFamily: 'inherit', fontSize: 13.5, fontWeight: 700, cursor: 'pointer',
              background: activo ? 'var(--pildora)' : 'transparent', color: activo ? 'var(--pildora-tinta)' : 'var(--tinta)',
            }}
          >
            {o.texto}
          </button>
        )
      })}
    </div>
  )
}

/** Fila de chips bajo el encabezado: qué dato es real del cliente y cuál es de ejemplo. */
export function Chips({ children }: { children: ReactNode }) {
  return <div className="fila-chips" style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center', padding: '0 4px' }}>{children}</div>
}

/** Campo con rótulo para formularios sobre vidrio: el control va como hijo (input.campo, select.campo…). */
export function CampoVidrio({ etiqueta, id, children, ayuda, error }: {
  etiqueta: string
  id: string
  children: ReactNode
  ayuda?: ReactNode
  error?: string
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
      <label htmlFor={id} style={{ fontSize: 14, fontWeight: 600, color: 'var(--tinta-2)' }}>{etiqueta}</label>
      {children}
      {ayuda && !error && <span style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>{ayuda}</span>}
      {error && <span role="alert" style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--grave)' }}>{error}</span>}
    </div>
  )
}

/** Aviso dentro de la página (error, éxito o advertencia): borde de color, siempre con texto. */
export function Aviso({ tono, children, rol }: { tono: 'bien' | 'aviso' | 'grave' | 'info'; children: ReactNode; rol?: 'alert' | 'status' }) {
  return (
    <div
      role={rol}
      style={{
        background: `var(--${tono}-tinte)`, color: 'var(--tinta)', borderLeft: `4px solid var(--${tono})`,
        borderRadius: 14, padding: '10px 14px', fontSize: 14, lineHeight: 1.5,
      }}
    >
      {children}
    </div>
  )
}

/** Encabezado de página: título grande a la izquierda, acciones a la derecha; en angosto se apilan. */
export function Encabezado({ titulo, sub, acciones }: { titulo: ReactNode; sub?: ReactNode; acciones?: ReactNode }) {
  return (
    <header style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', justifyContent: 'space-between', gap: 14, padding: '8px 4px 0' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
        <h1 style={{ fontSize: 'clamp(26px, 4vw, 34px)', fontWeight: 800, letterSpacing: '-0.03em' }}>{titulo}</h1>
        {sub && <p style={{ fontSize: 15, color: 'var(--tinta-plano)', lineHeight: 1.45 }}>{sub}</p>}
      </div>
      {acciones && <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 10 }}>{acciones}</div>}
    </header>
  )
}

export function TituloSeccion({ children }: { children: ReactNode }) {
  return <h2 style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.01em' }}>{children}</h2>
}
