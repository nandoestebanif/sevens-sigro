import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { visualizer } from 'rollup-plugin-visualizer'
import { VitePWA } from 'vite-plugin-pwa'

// `npm run analizar` (= vite build --mode analisis) deja el mapa del bundle en
// dist/estadisticas.html con tamaño crudo, gzip y brotli por módulo.
export default defineConfig(({ mode }) => ({
  plugins: [
    react(),
    // PWA: la web se puede «instalar» (ícono en el escritorio o el celular, ventana propia) y abre
    // sin red con lo último que se vio. Solo se guarda la interfaz; los datos siempre vienen del servidor.
    VitePWA({
      registerType: 'autoUpdate', // al desplegar una versión nueva, se actualiza sola
      injectRegister: 'script-defer', // Lighthouse (27/11): el registro del service worker no bloquea el primer pintado
      includeAssets: ['favicon.ico', 'apple-touch-icon.png'],
      manifest: {
        name: 'SIGRO · Gestión de recursos de obra',
        short_name: 'SIGRO',
        description: 'Recursos de obra de SEVENS INGENIEROS SAC: compras, almacén y gasto contra el S10.',
        lang: 'es-PE',
        start_url: '/',
        display: 'standalone',
        background_color: '#161717',
        theme_color: '#161717',
        icons: [
          { src: 'icono-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icono-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icono-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,png,ico,webp,svg,woff2}'],
        // las librerías del PDF (~750 KB) se descargan solo si alguien exporta: no se guardan de antemano
        globIgnores: ['**/jspdf*', '**/html2canvas*', '**/purify*', '**/index.es-*', '**/estadisticas.html'],
        // la API nunca se guarda en caché: el saldo lo calcula el servidor (regla 2) y debe ser el de ahora
        navigateFallbackDenylist: [/^\/api\//],
        maximumFileSizeToCacheInBytes: 3 * 1024 * 1024,
      },
    }),
    mode === 'analisis' && visualizer({
      filename: 'dist/estadisticas.html',
      template: 'treemap',
      gzipSize: true,
      brotliSize: true,
    }),
  ],
  css: {
    preprocessorOptions: {
      // Bootstrap 5.3 todavía usa @import de Sass: se silencian sus avisos de deprecación
      scss: { quietDeps: true, silenceDeprecations: ['import', 'global-builtin', 'color-functions', 'mixed-decls'] },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // React y el router cambian casi nunca: en su propio archivo, el navegador los conserva en caché
        // entre despliegues aunque cambie el código de SIGRO
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          bootstrap: ['react-bootstrap'],
          formularios: ['react-hook-form', 'zod', '@hookform/resolvers'],
          graficos: ['chart.js', 'react-chartjs-2'],
        },
      },
    },
  },
}))
