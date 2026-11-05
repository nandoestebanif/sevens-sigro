/* Pruebas unitarias de la web (`npm test`): solo src/**. Las de punta a punta viven en e2e/ y corren con
   Playwright contra la API (ver e2e/playwright.config.ts); Vitest no las toca. */
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    environment: 'node',
  },
})
