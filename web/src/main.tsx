import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { vigilarEscala } from './escala'
import './fuentes.css'
import './tokens.css'

// la escala guardada y el ancho útil, antes del primer pintado (sin saltos de diseño)
vigilarEscala()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
