/* SIGRO · HT-06 · Playwright contra la API LOCAL (gateway en :8000 por defecto).
   Credenciales SOLO por variables de entorno; nada de claves en el repositorio:
     SIGRO_E2E_USUARIO, SIGRO_E2E_CLAVE   administrador de la obra (el de `python -m app.cli primer-admin`)
     SIGRO_E2E_API                        URL del gateway (por defecto http://localhost:8000)
     SIGRO_E2E_PUERTO                     puerto del servidor de desarrollo de la web (por defecto 5174)
     SIGRO_E2E_WEB                        carpeta de la web a levantar (por defecto ..; sirve para probar la rama de un PR)
     FACTILIZA_TOKEN                      solo indica que Integraciones puede consultar SUNAT; sin él, esa prueba se omite
   La web se levanta sola (vite) con VITE_API_URL apuntando al gateway. El gateway debe permitir ese origen
   (SIGRO_CORS=http://localhost:5174). Guía completa: docs/pruebas/plan-de-pruebas.md */
import { defineConfig, devices } from '@playwright/test'

const puerto = Number(process.env.SIGRO_E2E_PUERTO ?? 5174)
const api = process.env.SIGRO_E2E_API ?? 'http://localhost:8000'

export default defineConfig({
  testDir: './pruebas',
  // en serie y en orden de archivo: la importación del S10 (02) alimenta catálogo (03) y saldo por comprar (05)
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: { timeout: 30_000 },
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${puerto}`,
    locale: 'es-PE',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npm run dev -- --port ${puerto} --strictPort`,
    cwd: process.env.SIGRO_E2E_WEB ?? '..',   // otra copia de la web (p. ej. la rama de un PR) si se quiere probar esa
    url: `http://localhost:${puerto}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    env: { VITE_API_URL: api },
  },
})
