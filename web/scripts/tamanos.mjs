// Tamaño crudo, gzip (nivel 9) y brotli de cada archivo de dist/: la tabla de docs/rendimiento-web-api.md.
// Uso: npm run build && node scripts/tamanos.mjs
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync, brotliCompressSync } from 'node:zlib'

const raiz = fileURLToPath(new URL('../dist/', import.meta.url))
const filas = []
const recorrer = (dir) => {
  for (const n of readdirSync(dir)) {
    const p = join(dir, n)
    if (statSync(p).isDirectory()) recorrer(p)
    else if (n !== 'estadisticas.html') {
      const b = readFileSync(p)
      filas.push([relative(raiz, p).split(sep).join('/'), b.length, gzipSync(b, { level: 9 }).length, brotliCompressSync(b).length])
    }
  }
}
recorrer(raiz)
filas.sort((a, b) => b[1] - a[1])
const kb = (n) => (n / 1024).toFixed(2)
console.log('| Archivo | Crudo (KiB) | gzip (KiB) | brotli (KiB) |\n|---|---:|---:|---:|')
for (const [f, c, g, br] of filas) console.log(`| ${f} | ${kb(c)} | ${kb(g)} | ${kb(br)} |`)
