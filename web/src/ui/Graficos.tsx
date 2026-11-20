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

const soles = (v: number) => `S/ ${v.toLocaleString('es-PE', { maximumFractionDigits: 0 })}`

/** Colores del tema de Bootstrap leídos en vivo: así el gráfico sigue al modo claro u oscuro. */
function useColoresTema() {
  const leer = () => {
    const css = getComputedStyle(document.documentElement)
    return {
      texto: css.getPropertyValue('--bs-body-color').trim() || '#212529',
      secundario: css.getPropertyValue('--bs-secondary-color').trim() || '#6c757d',
      rejilla: css.getPropertyValue('--bs-border-color-translucent').trim() || 'rgba(0,0,0,.1)',
      primario: css.getPropertyValue('--bs-primary').trim() || '#c2410c',
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
      x: { ticks: { color: c.secundario }, grid: { color: c.rejilla } },
      y: { ticks: { color: c.secundario, callback: (v) => soles(Number(v)) }, grid: { color: c.rejilla }, beginAtZero: true },
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
          scales: { ...(base(c).scales as object), x: { ticks: { color: c.secundario, maxTicksLimit: 13 }, grid: { color: c.rejilla }, title: { display: true, text: 'semana', color: c.secundario } } },
        }}
        data={{
          labels: semanas.map(String),
          datasets: [
            { label: 'Esperado (lineal en el plazo)', data: esperado, borderColor: c.secundario, borderDash: [6, 4], pointRadius: 0, fill: false },
            {
              label: `Gastado hoy (semana ${semana})`,
              data: semanas.map((s) => (s === semana ? gastado : null)),
              borderColor: c.primario, backgroundColor: c.primario, pointRadius: 7, pointHoverRadius: 9, showLine: false,
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
  const paleta = [c.primario, '#0369a1', '#4d7c0f', '#7c3aed', '#b45309', '#0f766e']
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
          datasets: [{ data: partes.map((p) => p.valor), backgroundColor: partes.map((_, i) => paleta[i % paleta.length]), borderWidth: 0 }],
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
            { label: 'Recibido', data: familias.map((f) => f.recibido), backgroundColor: resolver('var(--bs-info)') },
            { label: 'Consumido', data: familias.map((f) => f.consumido), backgroundColor: c.primario },
            { label: 'En stock', data: familias.map((f) => f.enStock), backgroundColor: resolver('var(--bs-success)') },
          ],
        }}
      />
    </div>
  )
}
