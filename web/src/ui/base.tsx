/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · componentes base de la dirección G «Pulido»
   Todo estilo sale de los tokens. Las pantallas componen con estas piezas y no
   escriben colores ni radios a mano.
   ───────────────────────────────────────────────────────────────────────── */
import { type CSSProperties, type ReactNode } from 'react'

export function Tarjeta({ children, relleno = true, estilo }: {
  children: ReactNode
  relleno?: boolean
  estilo?: CSSProperties
}) {
  return (
    <section
      style={{
        background: 'var(--tarjeta)',
        border: '1px solid var(--borde)',
        borderRadius: 'var(--radio)',
        boxShadow: 'var(--sombra)',
        padding: relleno ? '16px 18px' : 0,
        overflow: relleno ? undefined : 'hidden',
        ...estilo,
      }}
    >
      {children}
    </section>
  )
}

export function Boton({ children, tipo = 'primario', deshabilitado, enClic, anchoCompleto }: {
  children: ReactNode
  tipo?: 'primario' | 'secundario' | 'fantasma'
  deshabilitado?: boolean
  enClic?: () => void
  anchoCompleto?: boolean
}) {
  const estilos: Record<string, CSSProperties> = {
    primario: {
      background: 'var(--accion)', color: 'var(--accion-tinta)',
      border: '1px solid var(--accion)', boxShadow: 'var(--sombra-accion)',
    },
    secundario: {
      background: 'var(--tarjeta)', color: 'var(--tinta)',
      border: '1px solid var(--borde)',
    },
    fantasma: {
      background: 'transparent', color: 'var(--tinta-2)', border: '1px solid transparent',
    },
  }
  return (
    <button
      type="button"
      onClick={enClic}
      disabled={deshabilitado}
      style={{
        height: 40, padding: '0 16px', borderRadius: 'var(--radio-chico)',
        fontSize: 13.5, fontWeight: 600, cursor: deshabilitado ? 'not-allowed' : 'pointer',
        opacity: deshabilitado ? 0.55 : 1, width: anchoCompleto ? '100%' : undefined,
        ...estilos[tipo],
      }}
    >
      {children}
    </button>
  )
}

export type TonoChip = 'bien' | 'aviso' | 'grave' | 'neutro'

/** Estado con etiqueta: el color nunca va solo (criterio de accesibilidad). */
export function Chip({ tono, children }: { tono: TonoChip; children: ReactNode }) {
  const mapa: Record<TonoChip, [string, string]> = {
    bien: ['var(--bien)', 'var(--bien-tinte)'],
    aviso: ['var(--aviso)', 'var(--aviso-tinte)'],
    grave: ['var(--grave)', 'var(--grave-tinte)'],
    neutro: ['var(--tinta-3)', 'var(--linea)'],
  }
  const [color, fondo] = mapa[tono]
  return (
    <span
      className="n"
      style={{
        fontSize: 11.5, fontWeight: 600, color, background: fondo,
        borderRadius: 'var(--radio-pildora)', padding: '5px 10px', whiteSpace: 'nowrap',
      }}
    >
      {children}
    </span>
  )
}

export function Campo({ etiqueta, valor, enCambio, marcador, error, autoFoco, id, deshabilitado }: {
  etiqueta: string
  valor: string
  enCambio: (v: string) => void
  marcador?: string
  error?: string
  autoFoco?: boolean
  id: string
  deshabilitado?: boolean
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <label htmlFor={id} style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' }}>
        {etiqueta}
      </label>
      <input
        id={id}
        value={valor}
        placeholder={marcador}
        autoFocus={autoFoco}
        disabled={deshabilitado}
        onChange={(e) => enCambio(e.target.value)}
        style={{
          height: 42, border: `1px solid ${error ? 'var(--grave)' : 'var(--borde)'}`,
          borderRadius: 'var(--radio-chico)', padding: '0 13px', fontSize: 14,
          fontFamily: 'inherit', color: 'var(--tinta)', background: 'var(--tarjeta)',
          outlineColor: 'var(--accion)',
        }}
      />
      {error && <span style={{ fontSize: 12, color: 'var(--grave)' }}>{error}</span>}
    </div>
  )
}

/** Medidor con marca de referencia: responde «¿voy bien?», no solo «¿cuánto?» */
export function Medidor({ pct, marcaPct, color, ancho = 124 }: {
  pct: number
  marcaPct?: number
  color: string
  ancho?: number | string
}) {
  return (
    <div
      role="img"
      aria-label={`${pct} por ciento${marcaPct != null ? `, referencia ${marcaPct} por ciento` : ''}`}
      style={{ position: 'relative', height: 6, background: 'var(--pista)', borderRadius: 3, width: ancho }}
    >
      <div className="medidor-barra" style={{ width: `${Math.min(pct, 100)}%`, height: '100%', background: color, borderRadius: 3 }} />
      {marcaPct != null && (
        <div style={{
          position: 'absolute', left: `${marcaPct}%`, top: -3, width: 2, height: 12,
          background: 'var(--tinta)', borderRadius: 1,
        }} />
      )}
    </div>
  )
}

export function Rotulo({ children }: { children: ReactNode }) {
  return <div style={{ fontSize: 12, color: 'var(--tinta-3)', fontWeight: 500 }}>{children}</div>
}

export function Titulo({ children, sub }: { children: ReactNode; sub?: ReactNode }) {
  return (
    <div>
      <h1 style={{ fontSize: 25, fontWeight: 700, letterSpacing: '-0.6px' }}>{children}</h1>
      {sub && <p style={{ marginTop: 4, fontSize: 13, color: 'var(--tinta-3)' }}>{sub}</p>}
    </div>
  )
}

export function Vacio({ titulo, detalle }: { titulo: string; detalle: string }) {
  return (
    <div style={{ textAlign: 'center', padding: '42px 20px', color: 'var(--tinta-3)' }}>
      <div style={{ fontSize: 14.5, fontWeight: 600, color: 'var(--tinta-2)' }}>{titulo}</div>
      <div style={{ fontSize: 13, marginTop: 6 }}>{detalle}</div>
    </div>
  )
}

export function Modal({ titulo, children, enCerrar }: {
  titulo: string
  children: ReactNode
  enCerrar: () => void
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={titulo}
      className="velo"
      style={{
        position: 'fixed', inset: 0, background: 'var(--velo)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50,
      }}
      onClick={enCerrar}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="modal-caja"
        style={{
          width: 420, maxWidth: 'calc(100vw - 32px)', background: 'var(--tarjeta)',
          border: '1px solid var(--borde)',
          borderRadius: 'var(--radio)', boxShadow: '0 24px 60px rgba(20,20,20,0.25)',
          padding: '20px 22px',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h2 style={{ fontSize: 17, fontWeight: 700 }}>{titulo}</h2>
          <button
            type="button"
            onClick={enCerrar}
            aria-label="Cerrar"
            style={{ border: 0, background: 'transparent', fontSize: 18, cursor: 'pointer', color: 'var(--tinta-3)' }}
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
