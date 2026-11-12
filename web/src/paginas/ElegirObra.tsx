/* ─────────────────────────────────────────────────────────────────────────────
   Elegir la obra al entrar · solo cuando la persona está asignada a más de una.
   Las obras son las que devolvió el servidor al iniciar sesión (regla 9: solo
   aquellas donde está asignada); después la misma entrada «Preparando tu obra».
   ───────────────────────────────────────────────────────────────────────── */
import { useState } from 'react'
import { Fondo } from '../ui/vidrio'

export interface ObraParaElegir { id: string; nombre: string; rol: string }

export function ElegirObra({ nombre, obras, alElegir }: {
  nombre: string
  obras: ObraParaElegir[]
  alElegir: (o: ObraParaElegir) => Promise<void>
}) {
  const [eligiendo, setEligiendo] = useState<string | null>(null)
  const [error, setError] = useState('')
  const elegir = async (o: ObraParaElegir) => {
    setEligiendo(o.id); setError('')
    try { await alElegir(o) } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo abrir la obra. Inténtalo de nuevo.')
      setEligiendo(null)
    }
  }
  return (
    <>
      <Fondo dibujo="entrar" />
      <div className="pagina" style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '40px 20px' }}>
        <section className="vidrio-fuerte entrar-formulario" aria-labelledby="elegir-obra-titulo" style={{ boxShadow: 'var(--vidrio-sombra-alta)', display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <h1 id="elegir-obra-titulo" style={{ fontSize: 30, fontWeight: 800, letterSpacing: '-0.03em', margin: 0 }}>Elige la obra</h1>
            <p style={{ fontSize: 15, color: 'var(--tinta-2)', lineHeight: 1.45, margin: 0 }}>
              Hola, {nombre}. Tienes {obras.length} obras asignadas: verás solo lo de la que elijas y puedes cambiarla desde el menú.
            </p>
          </div>
          <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 10 }}>
            {obras.map((o) => (
              <li key={o.id}>
                <button
                  type="button" className="vidrio foco-vidrio" onClick={() => elegir(o)} disabled={eligiendo !== null}
                  style={{
                    width: '100%', minHeight: 64, borderRadius: 20, padding: '12px 16px', textAlign: 'left',
                    display: 'flex', flexDirection: 'column', gap: 2, cursor: eligiendo ? 'default' : 'pointer', color: 'var(--tinta)',
                  }}
                >
                  <span style={{ fontSize: 16, fontWeight: 800 }}>{o.nombre}</span>
                  <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>{eligiendo === o.id ? 'Abriendo…' : o.rol}</span>
                </button>
              </li>
            ))}
          </ul>
          {error && <div role="alert" style={{ fontSize: 14, fontWeight: 600, color: 'var(--grave)' }}>{error}</div>}
        </section>
      </div>
    </>
  )
}
