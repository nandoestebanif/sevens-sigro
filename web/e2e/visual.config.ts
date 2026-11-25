/* SIGRO · pruebas visuales del rediseño (24/11) · SIN API: la web corre con sus datos de ejemplo
   (sin VITE_API_URL), así se pueden correr en cualquier máquina y en CI sin credenciales.
   Revisan lo que la persona ve: tema oscuro por defecto, menú, tickets, celular y modo claro.
     npm run visual            (levanta la web sola en :5175) */
import { defineConfig, devices } from '@playwright/test'

const puerto = Number(process.env.SIGRO_VISUAL_PUERTO ?? 5175)

export default defineConfig({
  testDir: './visual',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'informe-visual' }]],
  use: { baseURL: `http://localhost:${puerto}`, locale: 'es-PE', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [
    { name: 'escritorio', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'celular', use: { ...devices['Pixel 7'] } },
  ],
  webServer: {
    command: `npm run dev -- --port ${puerto} --strictPort`,
    cwd: '..',
    url: `http://localhost:${puerto}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    env: { VITE_API_URL: '' },
  },
})
