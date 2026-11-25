/* Presupuesto de peso de la web (después de `npm run build`).
   La web abre en obra con datos móviles: si el arranque engorda, la primera carga se nota.
   Se mide comprimido con gzip, que es lo que viaja por la red. Falla si algo se pasa. */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'

const DIST = new URL('../dist/assets/', import.meta.url).pathname
const KB = 1024
// límites con holgura sobre lo medido el 24/11 (JS de arranque 30 KB, CSS 36 KB, todo junto ~1.6 MB sin comprimir)
const LIMITES = { arranqueJs: 45 * KB, css: 50 * KB, total: 2.4 * KB * KB }

const archivos = readdirSync(DIST).map((n) => ({ n, ruta: join(DIST, n) }))
const gz = (ruta) => gzipSync(readFileSync(ruta)).length
const arranqueJs = archivos.filter((a) => /^index-.*\.js$/.test(a.n)).reduce((s, a) => s + gz(a.ruta), 0)
const css = archivos.filter((a) => a.n.endsWith('.css')).reduce((s, a) => s + gz(a.ruta), 0)
const total = archivos.reduce((s, a) => s + statSync(a.ruta).size, 0)

const filas = [
  ['JS de arranque (gzip)', arranqueJs, LIMITES.arranqueJs],
  ['CSS (gzip)', css, LIMITES.css],
  ['Todos los assets (sin comprimir)', total, LIMITES.total],
]
let mal = false
for (const [nombre, valor, limite] of filas) {
  const ok = valor <= limite
  mal ||= !ok
  console.log(`${ok ? '✓' : '✗'} ${nombre}: ${(valor / KB).toFixed(1)} KB de ${(limite / KB).toFixed(0)} KB`)
}
if (mal) { console.error('La web se pasó de su presupuesto de peso.'); process.exit(1) }
