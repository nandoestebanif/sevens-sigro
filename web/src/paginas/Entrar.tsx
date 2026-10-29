/* ─────────────────────────────────────────────────────────────────────────────
   HU-12 · Entrar al sistema · lienzo W1 «Entrar» (estilo vidrio)
   Lo que cumple:
   - el acceso exige usuario y contraseña, y los valida el servidor;
   - usuario inexistente y clave errónea reciben el MISMO mensaje (no se revela
     cuál de los dos falló) y el bloqueo a los 5 intentos lo aplica la API;
   - la sesión dura lo que la pestaña: el token nunca va a localStorage.
   ───────────────────────────────────────────────────────────────────────── */
import { useState } from 'react'
import { ErrorApi } from '../api/error'
import { API_URL, iniciarSesion } from '../api/http'
import logoBlanco from '../assets/sevens-logo-blanco.png'
import { Fondo, Pildora } from '../ui/vidrio'

const PROMESAS = [
  'El presupuesto del S10 se importa tal cual: 284 insumos, al céntimo.',
  'El almacén registra ingresos y salidas desde la obra, aunque no haya señal.',
  'El gasto en recursos se compara con lo planificado, insumo por insumo.',
]

const rotulo = { display: 'flex', flexDirection: 'column', gap: 6, fontSize: 14, fontWeight: 600, color: 'var(--tinta-2)' } as const

function servidor(): string {
  try { return new URL(API_URL).host } catch { return API_URL }
}

export function Entrar() {
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [verClave, setVerClave] = useState(false)
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  const entrar = async () => {
    if (!usuario.trim() || !clave) { setError('Escribe tu usuario y tu contraseña.'); return }
    setEnviando(true); setError('')
    try {
      await iniciarSesion(usuario.trim(), clave)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo ingresar. Inténtalo de nuevo.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <>
      <Fondo dibujo="entrar" />
      <div
        className="pagina"
        style={{
          maxWidth: 1240, minHeight: '100vh', margin: '0 auto', padding: '40px 20px',
          display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'center', gap: 28,
        }}
      >
        <section className="entrar-marca" aria-label="SIGRO · Oficina de obra">
          <svg aria-hidden="true" width="100%" height="100%" viewBox="0 0 700 640" preserveAspectRatio="xMidYMid slice" style={{ position: 'absolute', left: 0, top: 0 }}>
            <defs>
              <radialGradient id="entrar-luz" cx="0.5" cy="0.5" r="0.5">
                <stop offset="0" stopColor="#FB923C" stopOpacity="0.45" />
                <stop offset="1" stopColor="#FB923C" stopOpacity="0" />
              </radialGradient>
              <pattern id="entrar-reticula" width="28" height="28" patternUnits="userSpaceOnUse">
                <path d="M28 0H0V28" fill="none" stroke="#334155" strokeWidth="0.6" />
              </pattern>
            </defs>
            <rect width="700" height="640" fill="url(#entrar-reticula)" opacity="0.6" />
            <circle cx="600" cy="560" r="300" fill="url(#entrar-luz)" />
            <g fill="none" stroke="#64748B" strokeWidth="2" opacity="0.7">
              <path d="M360 640V300h160v340M360 340h160M360 380h160M360 420h160M360 460h160M360 500h160M360 540h160M360 580h160M413 300v340M466 300v340" />
              <path d="M540 640V220h120v420M540 260h120M540 300h120M540 340h120M540 380h120M540 420h120M540 460h120M540 500h120M540 540h120M540 580h120M600 220v420" />
              <path d="M630 220V90M630 90H470M630 90h50M470 90v30M455 120h30v14h-30zM630 110l-20 20M630 140l-20 20M630 170l-20 20" />
            </g>
          </svg>
          <img src={logoBlanco} alt="SEVEN'S Ingenieros SAC" style={{ position: 'relative', height: 'clamp(72px, 12vw, 120px)', width: 'auto', alignSelf: 'flex-start' }} />
          <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: 18, maxWidth: 420 }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span aria-hidden style={{ width: 28, height: 1.5, background: '#FDBA74' }} />
              <span style={{ fontSize: 16, fontWeight: 600, letterSpacing: '0.26em', textTransform: 'uppercase' }}>Oficina de obra</span>
            </span>
            <h1>Lo que pide el S10, lo que se compra y lo que se gasta. En un solo lugar.</h1>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 12, fontSize: 16, color: '#E2E8F0', lineHeight: 1.4 }}>
              {PROMESAS.map((t) => (
                <li key={t} style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
                  <span aria-hidden style={{ width: 24, height: 24, borderRadius: 12, background: 'rgba(251,146,60,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#FDBA74" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
                  </span>
                  {t}
                </li>
              ))}
            </ul>
          </div>
          <span style={{ position: 'relative', fontSize: 13, color: '#94A3B8' }}>SEVENS INGENIEROS SAC · SIGRO</span>
        </section>

        <section className="vidrio-fuerte entrar-formulario" style={{ boxShadow: 'var(--vidrio-sombra-alta)' }}>
          <form
            onSubmit={(e) => { e.preventDefault(); entrar() }}
            style={{ display: 'flex', flexDirection: 'column', gap: 16 }}
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <h2 style={{ fontSize: 30, fontWeight: 800, letterSpacing: '-0.03em' }}>Ingresar</h2>
              <p style={{ fontSize: 15, color: 'var(--tinta-2)', lineHeight: 1.45 }}>Con tu usuario de SIGRO. Verás solo las obras donde estás asignado.</p>
            </div>
            <label htmlFor="usuario" style={rotulo}>
              Usuario
              <span style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                <input
                  id="usuario" className="campo" value={usuario} placeholder="Tu usuario" autoComplete="username" autoFocus
                  onChange={(e) => setUsuario(e.target.value)} style={{ paddingRight: 46 }}
                />
                <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" strokeLinecap="round" strokeLinejoin="round" style={{ position: 'absolute', right: 14 }}>
                  <path d="M6.5 8.6a5.5 5.5 0 0 1 11 0z" fill="#F59E0B" stroke="#B45309" strokeWidth="1.6" />
                  <path d="M4.6 8.6h14.8M12 3.2v2.4" stroke="#B45309" strokeWidth="1.8" />
                  <path d="M8.2 10.6a3.8 3.8 0 0 0 7.6 0M4.5 21c1-3.6 3.8-5.6 7.5-5.6s6.5 2 7.5 5.6" stroke="#64748B" strokeWidth="2" />
                </svg>
              </span>
            </label>
            <label htmlFor="clave" style={rotulo}>
              Contraseña
              <span style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                <input
                  id="clave" className="campo" type={verClave ? 'text' : 'password'} value={clave} placeholder="Tu contraseña"
                  autoComplete="current-password" onChange={(e) => setClave(e.target.value)} style={{ paddingRight: 52 }}
                />
                <button
                  type="button" className="foco-vidrio" onClick={() => setVerClave(!verClave)}
                  aria-label={verClave ? 'Ocultar contraseña' : 'Mostrar contraseña'} aria-pressed={verClave}
                  style={{ position: 'absolute', right: 2, width: 44, height: 44, border: 0, background: 'transparent', borderRadius: 22, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}
                >
                  <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--tinta-2)" strokeWidth="2" strokeLinecap="round">
                    <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" /><circle cx="12" cy="12" r="3" />
                    {verClave && <path d="M4 4l16 16" />}
                  </svg>
                </button>
              </span>
            </label>
            {error && <div role="alert" style={{ fontSize: 14, fontWeight: 600, color: 'var(--grave)' }}>{error}</div>}
            <Pildora enviar grande anchoCompleto deshabilitado={enviando}>{enviando ? 'Entrando…' : 'Entrar'}</Pildora>
            <p style={{ textAlign: 'center', fontSize: 14, color: 'var(--tinta-2)' }}>
              ¿No tienes usuario? <span style={{ color: 'var(--accion)', fontWeight: 700 }}>Pídelo al administrador de la obra</span>
            </p>
            {API_URL && (
              <span style={{ alignSelf: 'center', fontSize: 13, color: 'var(--tinta-2)', display: 'flex', alignItems: 'center', gap: 6 }}>
                <svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="4" width="18" height="6" rx="1.5" /><rect x="3" y="14" width="18" height="6" rx="1.5" /></svg>
                Servidor: {servidor()}
              </span>
            )}
          </form>
        </section>
      </div>
    </>
  )
}
