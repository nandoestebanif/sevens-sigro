/* ─────────────────────────────────────────────────────────────────────────────
   «El plano se dibuja» · la misma animación aprobada de la app
   (android/…/ui/componentes/Planos.kt y PlanoQueSeDibuja.kt, lienzo Main.dc.html):
   8 grupos de trazos en cascada — terreno, columnas, vigas, techo, grúa,
   puertas, ventanas y cota —, 900 ms cada uno, ~2,3 s en total. Después
   aparece el logo. Es decorativo: no son medidas de la obra.
   Con «reducir movimiento» no se anima: la capa muestra una barra de progreso.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useState, type CSSProperties } from 'react'
import logo from '../assets/sevens-logo.png'

const GRUPOS = [
  'M20 640H370',
  'M60 640V340M150 640V340M240 640V340M330 640V340',
  'M60 565H330M60 490H330M60 415H330M60 340H330',
  'M60 340L195 300L330 340',
  'M290 640V150M290 150H140M290 150H350M140 150V190M120 190h40v18h-40z',
  'M84 610h40v30M174 610h40v30M264 610h40v30',
  'M80 455h50v25h-50zM170 455h50v25h-50zM260 455h50v25h-50zM80 380h50v25h-50zM170 380h50v25h-50zM260 380h50v25h-50z',
  'M60 680H330M60 672v16M330 672v16',
]
const RETRASOS = [0, 150, 420, 700, 820, 1050, 1150, 1350]
const DURACION_TRAZO = 900
/** cuando termina el último trazo y aparece el logo (como APARECE_LOGO_MS en la app) */
export const PLANO_DIBUJADO_MS = 2300

/** cada «M» se dibuja de 0 a su largo, igual que subtrazos() en la app */
const subtrazos = (d: string) => d.match(/M[^M]*/g) ?? []

const consultaMovimiento = () =>
  typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null

/** true si la persona pidió menos movimiento en su sistema */
export const pideMenosMovimiento = () => consultaMovimiento()?.matches ?? false

export function useMenosMovimiento(): boolean {
  const [menos, setMenos] = useState(pideMenosMovimiento)
  useEffect(() => {
    const consulta = consultaMovimiento()
    if (!consulta) return
    const cambio = () => setMenos(consulta.matches)
    consulta.addEventListener('change', cambio)
    return () => consulta.removeEventListener('change', cambio)
  }, [])
  return menos
}

/** Llama a `alDibujarse` cuando el plano quedó completo (en el acto si no hay animación). */
export function PlanoQueSeDibuja({ alDibujarse, ancho = 260 }: { alDibujarse?: () => void; ancho?: number }) {
  const [conLogo, setConLogo] = useState(false)
  useEffect(() => {
    const t = setTimeout(() => { setConLogo(true); alDibujarse?.() }, PLANO_DIBUJADO_MS)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return (
    <div className="plano-dibuja" style={{ width: ancho }}>
      {/* el logo tiene su lugar reservado arriba del plano: aparece sin mover nada */}
      <img src={logo} alt="" className={conLogo ? 'plano-logo visible' : 'plano-logo'} />
      <svg aria-hidden="true" viewBox="0 120 390 600" width={ancho} height={(ancho * 600) / 390}>
        <g fill="none" stroke="var(--tinta-2)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          {GRUPOS.map((d, i) => subtrazos(d).map((s, j) => (
            <path
              key={`${i}-${j}`} d={s} pathLength={1} className="plano-trazo"
              style={{ animationDelay: `${RETRASOS[i]}ms`, animationDuration: `${DURACION_TRAZO}ms` } as CSSProperties}
            />
          )))}
        </g>
        <text x="195" y="706" textAnchor="middle" className={conLogo ? 'plano-cota visible' : 'plano-cota'}
          fontFamily="Inter, sans-serif" fontSize="12" fontWeight="700" fill="var(--accion)">27.00 m</text>
      </svg>
    </div>
  )
}
