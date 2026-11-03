import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { visualizer } from 'rollup-plugin-visualizer'

// `npm run analizar` (= vite build --mode analisis) deja el mapa del bundle en
// dist/estadisticas.html con tamaño crudo, gzip y brotli por módulo.
export default defineConfig(({ mode }) => ({
  plugins: [
    react(),
    mode === 'analisis' && visualizer({
      filename: 'dist/estadisticas.html',
      template: 'treemap',
      gzipSize: true,
      brotliSize: true,
    }),
  ],
  build: {
    rollupOptions: {
      output: {
        // React y el router cambian casi nunca: en su propio archivo, el navegador los conserva en caché
        // entre despliegues aunque cambie el código de SIGRO
        manualChunks: { react: ['react', 'react-dom', 'react-router-dom'] },
      },
    },
  },
}))
