/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · gráficos con Chart.js (vía react-chartjs-2)

   Chart.js dibuja en un <canvas>; react-chartjs-2 lo envuelve como componente de
   React: se le pasan `data` y `options` y él crea, actualiza y destruye el gráfico.
   Este archivo se carga aparte (React.lazy en ui/GraficosPerezosos.tsx): las
   pantallas sin gráficos no descargan Chart.js.

   Regla de la casa: el gráfico NUNCA inventa datos. Solo dibuja cifras que la
   pantalla ya tiene del servidor; la «curva esperada» es la misma regla de HU-57
   (lo planificado repartido de forma lineal en el plazo), no una historia simulada.
   ───────────────────────────────────────────────────────────────────────── */
import {
  ArcElement, BarElement, CategoryScale, Chart as ChartJS, Filler, Legend, LinearScale,
  LineElement, PointElement, Tooltip, type ChartOptions,
} from 'chart.js'
import { useEffect, useState } from 'react'
import { Bar, Doughnut, Line } from 'react-chartjs-2'

// Chart.js es modular: se registra solo lo que se usa (así el paquete pesa menos).
ChartJS.register(ArcElement, BarElement, CategoryScale, LinearScale, LineElement, PointElement, Filler, Legend, Tooltip)

// Rediseño 24/11: la misma letra de la web, barras redondeadas y un globo de ayuda oscuro en los dos modos.
ChartJS.defaults.font.family = 'Poppins, system-ui, sans-serif'
ChartJS.defaults.font.size = 12
ChartJS.defaults.elements.bar.borderRadius = 8
ChartJS.defaults.datasets.bar.maxBarThickness = 44
Object.assign(ChartJS.defaults.plugins.tooltip, {
  backgroundColor: '#161717', titleColor: '#ffffff', bodyColor: '#d2d3d3', padding: 12, cornerRadius: 12, boxPadding: 6,
  borderColor: '#3d3e3e', borderWidth: 1,
})

/** Paleta de datos del rediseño: violeta, menta y amarillo primero (los tres flujos), luego rosa, lila y naranja. */
export const PALETA = { violeta: '#7c6cf6', menta: '#2ee6c5', amarillo: '#fad85d', rosa: '#ff8fa3', lila: '#f2a6ff', naranja: '#ffb86b', rojo: '#ff6b6b' }

const soles = (v: number) => `${v < 0 ? "−" : ""}S/ ${Math.abs(v).toLocaleString("es-PE", { maximumFractionDigits: 0 })}`

/** Colores del tema de Bootstrap leídos en vivo: así el gráfico sigue al modo claro u oscuro. */
function useColoresTema() {
  const leer = () => {
    const css = getComputedStyle(document.documentElement)
    return {
      texto: css.getPropertyValue('--bs-body-color').trim() || '#212529',
      secundario: css.getPropertyValue('--bs-secondary-color').trim() || '#6c757d',
      rejilla: css.getPropertyValue('--bs-border-color-translucent').trim() || 'rgba(0,0,0,.1)',
      primario: css.getPropertyValue('--bs-primary').trim() || '#fad85d',
    }
  }
  const [c, setC] = useState(leer)
  useEffect(() => {
    // al cambiar data-bs-theme en <html>, se vuelven a leer los colores
    const o = new MutationObserver(() => setC(leer()))
    o.observe(document.documentElement, { attributes: true, attributeFilter: ['data-bs-theme'] })
    return () => o.disconnect()
  }, [])
  return c
}

/** El canvas no entiende «var(--x)»: se traduce al color real del tema. */
const resolver = (color: string) => {
  const m = /^var\((--[\w-]+)\)$/.exec(color.trim())
  return m ? getComputedStyle(document.documentElement).getPropertyValue(m[1]).trim() || '#888' : color
}

function base(c: ReturnType<typeof useColoresTema>): ChartOptions<'bar' | 'line'> {
  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { labels: { color: c.texto, usePointStyle: true } },
      tooltip: { callbacks: { label: (x) => `${x.dataset.label}: ${soles(Number(x.parsed.y))}` } },
    },
    scales: {
      x: { ticks: { color: c.secundario }, grid: { display: false }, border: { color: c.rejilla } },
      y: { ticks: { color: c.secundario, callback: (v) => soles(Number(v)) }, grid: { color: c.rejilla }, border: { display: false }, beginAtZero: true },
    },
  }
}

export interface FamiliaGrafico { etiqueta: string; color: string; planificado: number; gastado: number }

/** Barras agrupadas: planificado contra gastado por familia de recurso (HU-58). */
export function GraficoFamilias({ familias }: { familias: FamiliaGrafico[] }) {
  const c = useColoresTema()
  return (
    <div style={{ height: 260 }} role="img" aria-label={`Planificado contra gastado: ${familias.map((f) => `${f.etiqueta} ${soles(f.gastado)} de ${soles(f.planificado)}`).join('; ')}`}>
      <Bar
        options={base(c) as ChartOptions<'bar'>}
        data={{
          labels: familias.map((f) => f.etiqueta),
          datasets: [
            { label: 'Planificado (S10)', data: familias.map((f) => f.planificado), backgroundColor: c.rejilla, borderColor: c.secundario, borderWidth: 1 },
            { label: 'Gastado a la fecha', data: familias.map((f) => f.gastado), backgroundColor: familias.map((f) => resolver(f.color)) },
          ],
        }}
      />
    </div>
  )
}

/**
 * Curva esperada (lineal, la regla de HU-57) y el punto real de hoy. No hay historia semanal guardada,
 * así que no se dibuja una curva «real» inventada: solo dónde está el gasto en la semana actual.
 */
export function GraficoCurva({ planificado, gastado, semana, semanasTotales }: {
  planificado: number; gastado: number; semana: number; semanasTotales: number
}) {
  const c = useColoresTema()
  const semanas = Array.from({ length: semanasTotales + 1 }, (_, i) => i)
  const esperado = semanas.map((s) => (planificado * s) / Math.max(semanasTotales, 1))
  return (
    <div style={{ height: 260 }} role="img" aria-label={`En la semana ${semana} se esperaba ${soles(esperado[semana] ?? 0)} y se gastó ${soles(gastado)}`}>
      <Line
        options={{
          ...(base(c) as ChartOptions<'line'>),
          scales: { ...(base(c).scales as object), x: { ticks: { color: c.secundario, maxTicksLimit: 13 }, grid: { display: false }, title: { display: true, text: 'semana', color: c.secundario } } },
        }}
        data={{
          labels: semanas.map(String),
          datasets: [
            { label: 'Esperado (lineal en el plazo)', data: esperado, borderColor: PALETA.menta, borderWidth: 2, borderDash: [6, 5], pointRadius: 0, fill: false },
            {
              label: `Gastado hoy (semana ${semana})`,
              data: semanas.map((s) => (s === semana ? gastado : null)),
              borderColor: '#ffffff', backgroundColor: PALETA.violeta, borderWidth: 3, pointRadius: 9, pointHoverRadius: 11, showLine: false,
            },
          ],
        }}
      />
    </div>
  )
}

/** Dona: reparto de un total (p. ej. el inventario valorizado por almacén, HU-56). */
export function GraficoReparto({ partes }: { partes: { etiqueta: string; valor: number }[] }) {
  const c = useColoresTema()
  const paleta = [PALETA.violeta, PALETA.menta, PALETA.amarillo, PALETA.rosa, PALETA.lila, PALETA.naranja]
  return (
    <div style={{ height: 240 }} role="img" aria-label={partes.map((p) => `${p.etiqueta}: ${soles(p.valor)}`).join('; ')}>
      <Doughnut
        options={{
          responsive: true, maintainAspectRatio: false, cutout: '60%',
          plugins: {
            legend: { position: 'right', labels: { color: c.texto, usePointStyle: true } },
            tooltip: { callbacks: { label: (x) => `${x.label}: ${soles(Number(x.parsed))}` } },
          },
        }}
        data={{
          labels: partes.map((p) => p.etiqueta),
          datasets: [{ data: partes.map((p) => p.valor), backgroundColor: partes.map((_, i) => paleta[i % paleta.length]), borderWidth: 0, borderRadius: 6, spacing: 3 }],
        }}
      />
    </div>
  )
}

export interface FlujoFamilia { etiqueta: string; recibido: number; consumido: number; enStock: number }

/** Barras por familia: recibido, consumido y en stock, las tres del libro de almacén (HU-58). */
export function GraficoFlujo({ familias }: { familias: FlujoFamilia[] }) {
  const c = useColoresTema()
  return (
    <div style={{ height: 260 }} role="img" aria-label={familias.map((f) => `${f.etiqueta}: recibido ${soles(f.recibido)}, consumido ${soles(f.consumido)}, en stock ${soles(f.enStock)}`).join('; ')}>
      <Bar
        options={base(c) as ChartOptions<'bar'>}
        data={{
          labels: familias.map((f) => f.etiqueta),
          datasets: [
            { label: 'Recibido', data: familias.map((f) => f.recibido), backgroundColor: PALETA.violeta },
            { label: 'Consumido', data: familias.map((f) => f.consumido), backgroundColor: PALETA.menta },
            { label: 'En stock', data: familias.map((f) => f.enStock), backgroundColor: PALETA.amarillo },
          ],
        }}
      />
    </div>
  )
}

export interface DesvioInsumo { etiqueta: string; importe: number }

/**
 * Insumos con mayor desviación en soles (HU-57), barras horizontales: rojo = se gastó más de lo esperado
 * a la fecha, verde = menos. Son las cifras del servidor; aquí solo se eligen las más grandes.
 */
export function GraficoDesvios({ insumos, cuantos = 8 }: { insumos: DesvioInsumo[]; cuantos?: number }) {
  const c = useColoresTema()
  const top = [...insumos].filter((i) => i.importe !== 0).sort((a, b) => Math.abs(b.importe) - Math.abs(a.importe)).slice(0, cuantos)
  const corto = (t: string) => (t.length > 34 ? `${t.slice(0, 33)}…` : t)
  return (
    <div style={{ height: Math.max(180, top.length * 36 + 60) }} role="img"
      aria-label={top.map((i) => `${i.etiqueta}: ${i.importe > 0 ? 'más' : 'menos'} de lo esperado por ${soles(Math.abs(i.importe))}`).join('; ')}>
      <Bar
        options={{
          ...(base(c) as ChartOptions<'bar'>),
          indexAxis: 'y',
          plugins: {
            legend: { display: false },
            tooltip: { callbacks: { label: (x) => `${Number(x.parsed.x) > 0 ? 'Sobre' : 'Bajo'} lo esperado: ${soles(Math.abs(Number(x.parsed.x)))}` } },
          },
          scales: {
            x: { ticks: { color: c.secundario, callback: (v) => soles(Number(v)) }, grid: { color: c.rejilla } },
            y: { ticks: { color: c.texto }, grid: { display: false } },
          },
        }}
        data={{
          labels: top.map((i) => corto(i.etiqueta)),
          datasets: [{
            label: 'Desviación (S/)',
            data: top.map((i) => i.importe),
            backgroundColor: top.map((i) => (i.importe > 0 ? PALETA.rojo : PALETA.menta)),
          }],
        }}
      />
    </div>
  )
}
