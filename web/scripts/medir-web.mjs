// Medición de la web con Chrome real: carga (bytes, FCP, LCP) y catálogo (DOM, tiempo, FPS de desplazamiento).
// Uso: npm run build && npx vite preview --port 5175, y en otra terminal:
//   npm i --no-save playwright-core && node scripts/medir-web.mjs <etiqueta>   (CPU=1 SOLO_CATALOGO=1 opcionales)
// Resultados y método: docs/rendimiento-web-api.md
// Medición de la web de oficina con Chrome (playwright-core + CDP).
// Uso: node medir-web.mjs <etiqueta> [url-base]   (vite preview en :5175, cliente simulado)
import { chromium } from 'playwright-core'

const etiqueta = process.argv[2] ?? 'sin-etiqueta'
const base = process.argv[3] ?? 'http://localhost:5175/'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const REPETICIONES = 5

const mediana = (xs) => { const s = [...xs].sort((a, b) => a - b); return s[Math.floor(s.length / 2)] }

async function nuevaPagina(navegador, { red4g = false, cpu = 1 } = {}) {
  const ctx = await navegador.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const cdp = await ctx.newCDPSession(page)
  await cdp.send('Network.enable')
  await cdp.send('Network.setCacheDisabled', { cacheDisabled: true })
  if (red4g) {
    // «Slow 4G» de DevTools: 150 ms de latencia, 1.6 Mbps de bajada, 750 kbps de subida
    await cdp.send('Network.emulateNetworkConditions', {
      offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8,
    })
  }
  if (cpu > 1) await cdp.send('Emulation.setCPUThrottlingRate', { rate: cpu })
  const bytes = { total: 0, script: 0, font: 0, image: 0, stylesheet: 0, document: 0, otro: 0, peticiones: 0, origenes: new Set() }
  const tipos = new Map()
  cdp.on('Network.responseReceived', (e) => { tipos.set(e.requestId, e.type.toLowerCase()); bytes.origenes.add(new URL(e.response.url).origin) })
  cdp.on('Network.loadingFinished', (e) => {
    const t = tipos.get(e.requestId) ?? 'otro'
    bytes.total += e.encodedDataLength; bytes.peticiones++
    bytes[t in bytes ? t : 'otro'] += e.encodedDataLength
  })
  await page.addInitScript(() => {
    window.__lcp = 0
    new PerformanceObserver((l) => { for (const e of l.getEntries()) window.__lcp = e.startTime }).observe({ type: 'largest-contentful-paint', buffered: true })
  })
  return { ctx, page, cdp, bytes }
}

async function carga(navegador, opciones) {
  const { ctx, page, bytes } = await nuevaPagina(navegador, opciones)
  await page.goto(base + '#/tablero', { waitUntil: 'networkidle' })
  await page.waitForTimeout(300)
  const m = await page.evaluate(() => {
    const nav = performance.getEntriesByType('navigation')[0]
    const fcp = performance.getEntriesByName('first-contentful-paint')[0]?.startTime ?? 0
    return { fcp, lcp: window.__lcp, load: nav.loadEventEnd, fuentesListas: document.fonts.status }
  })
  await ctx.close()
  return { ...m, kib: bytes.total / 1024, jsKib: bytes.script / 1024, fuenteKib: bytes.font / 1024, imgKib: bytes.image / 1024, cssKib: bytes.stylesheet / 1024, peticiones: bytes.peticiones, origenes: bytes.origenes.size }
}

async function catalogo(navegador, { todo }) {
  const { ctx, page, cdp } = await nuevaPagina(navegador, { cpu: Number(process.env.CPU ?? 4) })
  await page.goto(base + '#/tablero', { waitUntil: 'networkidle' })
  // tiempo desde el clic en el menú hasta que la tabla está pintada con filas
  const t0 = await page.evaluate(() => performance.now())
  await page.click('a[href="#/catalogo"]')
  await page.waitForSelector('table tbody tr td')
  await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))))
  const t1 = await page.evaluate(() => performance.now())
  let tMostrarTodo = 0
  if (todo) {
    // versión anterior: «Mostrar 60 más» hasta ver los 2 243
    const a = await page.evaluate(() => performance.now())
    for (;;) {
      const b = page.locator('button', { hasText: 'Mostrar 60 más' })
      if (!(await b.count())) break
      await b.click()
    }
    await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))))
    tMostrarTodo = (await page.evaluate(() => performance.now())) - a
  }
  const dom = await page.evaluate(() => ({ nodos: document.getElementsByTagName('*').length, filas: document.querySelectorAll('table tbody tr').length, alto: document.documentElement.scrollHeight }))
  await cdp.send('Performance.enable')
  const { JSHeapUsedSize } = Object.fromEntries((await cdp.send('Performance.getMetrics').catch(() => ({ metrics: [] }))).metrics?.map((x) => [x.name, x.value]) ?? [])
  // FPS de desplazamiento: 4 s bajando 120 px por cuadro, CPU ×4 más lenta
  const scroll = await page.evaluate(() => new Promise((r) => {
    window.scrollTo(0, 0)
    const cuadros = []; let ultimo = performance.now(); const inicio = ultimo
    const paso = (t) => {
      cuadros.push(t - ultimo); ultimo = t
      window.scrollBy(0, 120)
      if (t - inicio < 4000 && window.scrollY + innerHeight < document.documentElement.scrollHeight - 5) requestAnimationFrame(paso)
      else {
        const dur = t - inicio
        const largos = cuadros.filter((c) => c > 50).length
        r({ fps: (cuadros.length / dur) * 1000, cuadros: cuadros.length, peorCuadro: Math.max(...cuadros), cuadrosLargos: largos })
      }
    }
    requestAnimationFrame(paso)
  }))
  const filasTrasScroll = await page.evaluate(() => document.querySelectorAll('table tbody tr').length)
  await ctx.close()
  return { msHastaTabla: t1 - t0, msMostrarTodo: tMostrarTodo, ...dom, filasTrasScroll, heapMiB: JSHeapUsedSize ? JSHeapUsedSize / 1048576 : null, ...scroll }
}

const navegador = await chromium.launch({ executablePath: CHROME, headless: true })
const res = { etiqueta, fecha: new Date().toISOString() }
for (const [nombre, op] of (process.env.SOLO_CATALOGO ? [] : [['carga_sin_limite', {}], ['carga_slow4g', { red4g: true }]])) {
  const xs = []
  for (let i = 0; i < REPETICIONES; i++) xs.push(await carga(navegador, op))
  res[nombre] = Object.fromEntries(Object.keys(xs[0]).map((k) => [k, typeof xs[0][k] === 'number' ? +mediana(xs.map((x) => x[k])).toFixed(1) : xs[0][k]]))
}
for (const [nombre, op] of [['catalogo_inicial', { todo: false }], ['catalogo_completo', { todo: true }]]) {
  const xs = []
  for (let i = 0; i < 3; i++) xs.push(await catalogo(navegador, op))
  res[nombre] = Object.fromEntries(Object.keys(xs[0]).map((k) => [k, typeof xs[0][k] === 'number' ? +mediana(xs.map((x) => x[k])).toFixed(1) : xs[0][k]]))
}
await navegador.close()
console.log(JSON.stringify(res, null, 2))
