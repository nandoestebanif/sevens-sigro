/* SIGRO · campana de avisos (barra superior) y cambio de contraseña (junto al perfil). */
import { useCallback, useEffect, useState } from 'react'
import { Badge, Button, Dropdown } from 'react-bootstrap'
import { useNavigate } from 'react-router-dom'
import { cuenta, destinoDeAviso, erroresCambioClave, type Aviso } from '../api/cuenta'
import { useEnVivo } from '../formularios/enVivo'
import { ErrorApi } from '../api/error'
import { Modal } from './base'

const hace = (iso: string) => {
  const min = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (min < 1) return 'ahora'
  if (min < 60) return `hace ${min} min`
  const h = Math.round(min / 60)
  if (h < 24) return `hace ${h} h`
  return new Date(iso).toLocaleDateString('es-PE', { timeZone: 'America/Lima', day: '2-digit', month: 'short' })
}

/** Avisos de la persona (p. ej. «Nueva solicitud de materiales»). Se revisan cada minuto; si el servicio no está, no se muestra. */
export function Campana() {
  const [datos, setDatos] = useState<{ noLeidas: number; avisos: Aviso[] } | null>(null)
  const ir = useNavigate()
  const cargar = useCallback(() => { cuenta.avisos().then(setDatos).catch(() => undefined) }, [])
  useEffect(() => {
    cargar()
    const t = setInterval(cargar, 60_000)
    return () => clearInterval(t)
  }, [cargar])
  if (!datos) return null

  const abrir = async (a: Aviso) => {
    if (!a.leida) {
      await cuenta.marcarLeido(a.id).catch(() => undefined)
      setDatos((d) => d && { noLeidas: Math.max(0, d.noLeidas - 1), avisos: d.avisos.map((x) => x.id === a.id ? { ...x, leida: true } : x) })
    }
    const destino = destinoDeAviso(a)
    if (destino) ir(destino)
  }

  return (
    <Dropdown align="end" className="me-2">
      <Dropdown.Toggle as={Button} variant="link" className="sin-flecha sigro-campana position-relative" aria-label={`Avisos${datos.noLeidas ? `: ${datos.noLeidas} sin leer` : ''}`}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {datos.noLeidas > 0 && <Badge bg="danger" pill className="position-absolute top-0 start-100 translate-middle">{datos.noLeidas > 9 ? '9+' : datos.noLeidas}</Badge>}
      </Dropdown.Toggle>
      <Dropdown.Menu style={{ width: 340, maxHeight: 420, overflowY: 'auto' }} popperConfig={{ strategy: 'fixed' }}>
        <Dropdown.Header>Avisos</Dropdown.Header>
        {datos.avisos.length === 0 && <div className="px-3 py-2 small text-body-secondary">No tienes avisos.</div>}
        {datos.avisos.map((a) => (
          <Dropdown.Item key={a.id} as="button" onClick={() => void abrir(a)} className="text-wrap py-2">
            <div className="d-flex gap-2">
              <span aria-hidden className={a.leida ? 'invisible' : 'text-primary'}>●</span>
              <div className="min-w-0">
                <div className={a.leida ? '' : 'fw-semibold'}>{a.titulo}</div>
                <div className="small text-body-secondary">{a.mensaje}</div>
                <div className="small text-body-tertiary">{hace(a.creadaEn)}</div>
              </div>
            </div>
          </Dropdown.Item>
        ))}
      </Dropdown.Menu>
    </Dropdown>
  )
}

/** Cambiar la propia contraseña: pide la actual (así nadie la cambia con una sesión ajena abierta). */
export function CambiarClave({ enCerrar }: { enCerrar: () => void }) {
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [repetida, setRepetida] = useState('')
  const [error, setError] = useState('')
  const [listo, setListo] = useState(false)
  const [ocupado, setOcupado] = useState(false)

  const guardar = async () => {
    if (v.alGuardar()) return
    setOcupado(true); setError('')
    try { await cuenta.cambiarClave(actual, nueva); setListo(true); setActual(''); setNueva(''); setRepetida('') } catch (x) {
      setError(x instanceof ErrorApi ? x.message : 'No se pudo cambiar la contraseña.')
    } finally { setOcupado(false) }
  }

  // validación en vivo: cada clave con su aviso mientras se escribe (la fuerza, con la regla del servidor)
  const v = useEnVivo(erroresCambioClave(actual, nueva, repetida))
  const campo = (id: string, clave: 'actual' | 'nueva' | 'repetida', etiqueta: string, valor: string, cambiar: (v: string) => void, auto: string) => {
    const c = v.campo(clave)
    return (
      <label htmlFor={id} className="d-flex flex-column gap-1 small fw-semibold">
        {etiqueta}
        <input id={id} type="password" autoComplete={auto} value={valor}
          className={`form-control${c.error ? ' is-invalid' : c.valido ? ' is-valid' : ''}`}
          aria-invalid={!!c.error} aria-describedby={c.error ? `${id}-error` : undefined}
          onChange={(e) => { cambiar(e.target.value); setError(''); v.cambio(clave) }} onBlur={c.enSalir} />
        {c.error && <span id={`${id}-error`} className="invalid-feedback d-block fw-normal" aria-live="polite">{c.error}</span>}
      </label>
    )
  }

  return (
    <Modal titulo="Cambiar mi contraseña" enCerrar={enCerrar}>
      {listo ? (
        <div className="d-flex flex-column gap-3">
          <div role="status" className="text-success">Listo: tu contraseña cambió. Úsala la próxima vez que entres.</div>
          <div className="d-flex justify-content-end"><Button onClick={enCerrar}>Cerrar</Button></div>
        </div>
      ) : (
        <form className="d-flex flex-column gap-3" onSubmit={(e) => { e.preventDefault(); void guardar() }}>
          {campo('clave-actual', 'actual', 'Contraseña actual', actual, setActual, 'current-password')}
          {campo('clave-nueva', 'nueva', 'Nueva contraseña', nueva, setNueva, 'new-password')}
          {campo('clave-repetida', 'repetida', 'Repite la nueva contraseña', repetida, setRepetida, 'new-password')}
          <span className="small text-body-secondary">Al menos 10 caracteres, con mayúsculas, minúsculas y un número (la misma regla que revisa el servidor).</span>
          {error && <span role="alert" className="small text-danger">{error}</span>}
          <div className="d-flex gap-2 justify-content-end">
            <Button variant="outline-secondary" onClick={enCerrar}>Cancelar</Button>
            <Button type="submit" disabled={ocupado}>{ocupado ? 'Guardando…' : 'Cambiar contraseña'}</Button>
          </div>
        </form>
      )}
    </Modal>
  )
}
