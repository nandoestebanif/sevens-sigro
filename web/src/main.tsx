import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { ProveedorAvisos } from './ui/Avisos'
// Bootstrap 5 personalizado con los colores de SEVEN'S (ver estilos/tema.scss)
import './estilos/tema.scss'

// Tema y tamaño de texto guardados en este dispositivo, antes del primer pintado.
// Bootstrap trae el modo oscuro: basta con data-bs-theme en <html>.
try {
  document.documentElement.dataset.bsTheme = localStorage.getItem('sigro-tema') === 'oscuro' ? 'dark' : 'light'
  document.documentElement.style.fontSize = `${localStorage.getItem('sigro-escala') ?? '100'}%`
} catch { /* sin almacenamiento: tema claro y 100 % */ }

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* avisos flotantes (Toast) disponibles en toda la app */}
    <ProveedorAvisos>
      <App />
    </ProveedorAvisos>
  </StrictMode>,
)
