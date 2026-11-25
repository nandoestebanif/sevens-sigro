/* Los gráficos se cargan «perezosamente» (React.lazy + Suspense): Chart.js se descarga
   solo cuando una pantalla muestra un gráfico, y mientras tanto se ve su esqueleto. */
import { lazy, Suspense, type ComponentProps } from 'react'
import { EsqueletoGrafico } from './Esqueleto'

const Familias = lazy(() => import('./Graficos').then((m) => ({ default: m.GraficoFamilias })))
const Curva = lazy(() => import('./Graficos').then((m) => ({ default: m.GraficoCurva })))
const Flujo = lazy(() => import('./Graficos').then((m) => ({ default: m.GraficoFlujo })))
const Desvios = lazy(() => import('./Graficos').then((m) => ({ default: m.GraficoDesvios })))
const Reparto = lazy(() => import('./Graficos').then((m) => ({ default: m.GraficoReparto })))

type G = typeof import('./Graficos')
export const GraficoFamilias = (p: ComponentProps<G['GraficoFamilias']>) => <Suspense fallback={<EsqueletoGrafico />}><Familias {...p} /></Suspense>
export const GraficoCurva = (p: ComponentProps<G['GraficoCurva']>) => <Suspense fallback={<EsqueletoGrafico />}><Curva {...p} /></Suspense>
export const GraficoReparto = (p: ComponentProps<G['GraficoReparto']>) => <Suspense fallback={<EsqueletoGrafico alto={240} />}><Reparto {...p} /></Suspense>
export const GraficoFlujo = (p: ComponentProps<G['GraficoFlujo']>) => <Suspense fallback={<EsqueletoGrafico />}><Flujo {...p} /></Suspense>
export const GraficoDesvios = (p: ComponentProps<G['GraficoDesvios']>) => <Suspense fallback={<EsqueletoGrafico />}><Desvios {...p} /></Suspense>
