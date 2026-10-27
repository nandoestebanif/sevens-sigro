/* ─────────────────────────────────────────────────────────────────────────────
   HU-12 · Entrar al sistema (versión mínima para conectar la API real, HT-04)
   Henry la completa en T-023 con la pantalla 20 del lienzo. Lo que ya cumple:
   - el acceso exige usuario y contraseña, y los valida el servidor;
   - usuario inexistente y clave errónea reciben el MISMO mensaje (no se revela
     cuál de los dos falló) y el bloqueo a los 5 intentos lo aplica la API;
   - la sesión dura lo que la pestaña: el token nunca va a localStorage.
   ───────────────────────────────────────────────────────────────────────── */
import { useState } from 'react'
import { ErrorApi } from '../api/error'
import { iniciarSesion } from '../api/http'
import { Boton, Campo, Tarjeta } from '../ui/base'

export function Entrar() {
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
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
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}>
      <div style={{ width: '100%', maxWidth: 380 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 18 }}>
          <div aria-hidden style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--marca)', color: '#fff', fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>S</div>
          <div>
            <div style={{ fontSize: 18, fontWeight: 700 }}>SIGRO</div>
            <div style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>SEVENS INGENIEROS · oficina</div>
          </div>
        </div>
        <Tarjeta>
          <form
            onSubmit={(e) => { e.preventDefault(); entrar() }}
            style={{ display: 'flex', flexDirection: 'column', gap: 14 }}
          >
            <Campo id="usuario" etiqueta="Usuario" valor={usuario} enCambio={setUsuario} autoFoco />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <label htmlFor="clave" style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' }}>Contraseña</label>
              <input
                id="clave" type="password" value={clave} autoComplete="current-password"
                onChange={(e) => setClave(e.target.value)}
                style={{ height: 42, border: '1px solid var(--borde)', borderRadius: 'var(--radio-chico)', padding: '0 13px', fontSize: 14, fontFamily: 'inherit', color: 'var(--tinta)', background: 'var(--tarjeta)' }}
              />
            </div>
            {error && <div role="alert" style={{ fontSize: 13, color: 'var(--grave)' }}>{error}</div>}
            <button type="submit" style={{ display: 'none' }} aria-hidden />
            <Boton enClic={entrar} deshabilitado={enviando} anchoCompleto>{enviando ? 'Entrando…' : 'Entrar'}</Boton>
          </form>
        </Tarjeta>
      </div>
    </div>
  )
}
