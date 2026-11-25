import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { ProveedorAvisos } from './ui/Avisos'
// Poppins va en el paquete (sin depender de internet: también funciona en la PWA)
import '@fontsource/poppins/400.css'
import '@fontsource/poppins/500.css'
import '@fontsource/poppins/600.css'
import '@fontsource/poppins/700.css'
// Bootstrap 5 personalizado con los colores de SEVEN'S (ver estilos/tema.scss)
import './estilos/tema.scss'

// Tema y tamaño de texto guardados en este dispositivo, antes del primer pintado.
// Bootstrap trae el modo oscuro: basta con data-bs-theme en <html>. El oscuro es el de la marca y va por defecto.
try {
  document.documentElement.dataset.bsTheme = localStorage.getItem('sigro-tema') === 'claro' ? 'light' : 'dark'
  document.documentElement.style.fontSize = `${localStorage.getItem('sigro-escala') ?? '100'}%`
} catch { document.documentElement.dataset.bsTheme = 'dark' /* sin almacenamiento: tema oscuro y 100 % */ }

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* avisos flotantes (Toast) disponibles en toda la app */}
    <ProveedorAvisos>
      <App />
    </ProveedorAvisos>
  </StrictMode>,
)
