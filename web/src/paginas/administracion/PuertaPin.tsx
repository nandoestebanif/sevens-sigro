/* SIGRO · el panel de administración se abre con un PIN de 6 dígitos (además de la sesión).
   La primera vez se crea (pide la contraseña); después se escribe y vale 15 minutos en esta pestaña.
   Quien encuentra la PC con la sesión abierta no puede crear usuarios ni autorizar celulares sin el PIN.
   26/11: el mismo formulario lo usa DialogoPin para las acciones delicadas (cerrar semana u obra, plazo, anular…). */
import { useEffect, useState, type ReactNode } from 'react'
import { ErrorApi } from '../../api/error'
import { olvidarPin, pinAceptable, tokenPin } from '../../api/pin'
import { pin } from '../../api/pinApi'
import { Boton, Tarjeta } from '../../ui/base'

/** Crear (primera vez, con la contraseña) o escribir el PIN. Llama a alListo cuando queda un PIN vigente. */
export function FormularioPin({ titulo, ayuda, textoBoton, alListo, idBase = 'pin' }: {
  titulo?: string; ayuda?: string; textoBoton?: string; alListo?: () => void; idBase?: string
}) {
  const [configurado, setConfigurado] = useState<boolean | null>(null)
  const [valor, setValor] = useState('')
  const [repetir, setRepetir] = useState('')
  const [clave, setClave] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  useEffect(() => { pin.estado().then((e) => setConfigurado(e.configurado)).catch(() => setConfigurado(false)) }, [])

  const enviar = async () => {
    setError('')
    const e = pinAceptable(valor) ?? (configurado === false && valor !== repetir ? 'Los dos PIN no coinciden.' : null)
      ?? (configurado === false && !clave ? 'Escribe tu contraseña para crear el PIN.' : null)
    if (e) { setError(e); return }
    setEnviando(true)
    try {
      if (configurado) await pin.verificar(valor)
      else await pin.crear(clave, valor)
      setValor(''); setRepetir(''); setClave('')
      alListo?.()
    } catch (x) {
      setError(x instanceof ErrorApi ? x.message : 'No se pudo comprobar el PIN.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form className="d-flex flex-column gap-3 mx-auto w-100" style={{ maxWidth: 380 }} onSubmit={(ev) => { ev.preventDefault(); void enviar() }}>
      {titulo !== '' && <h2 className="h5 mb-0">{configurado === false ? 'Crea tu PIN' : (titulo ?? 'Escribe tu PIN')}</h2>}
      <p className="small text-body-secondary mb-0">
        {configurado === false
          ? 'Protege las acciones delicadas: aunque alguien use tu sesión abierta, sin el PIN no puede hacerlas. Son 6 dígitos que no sean repetidos ni seguidos.'
          : (ayuda ?? 'Vale 15 minutos en esta pestaña. 5 intentos fallidos lo bloquean 10 minutos.')}
      </p>
      <label className="small fw-semibold" htmlFor={`${idBase}-valor`}>PIN</label>
      <input id={`${idBase}-valor`} className="form-control form-control-lg text-center n" inputMode="numeric" autoComplete="off" type="password"
        maxLength={6} value={valor} onChange={(e) => setValor(e.target.value.replace(/\D/g, ''))} autoFocus />
      {configurado === false && (
        <>
          <label className="small fw-semibold" htmlFor={`${idBase}-repetir`}>Repite el PIN</label>
          <input id={`${idBase}-repetir`} className="form-control text-center n" inputMode="numeric" autoComplete="off" type="password"
            maxLength={6} value={repetir} onChange={(e) => setRepetir(e.target.value.replace(/\D/g, ''))} />
          <label className="small fw-semibold" htmlFor={`${idBase}-clave`}>Tu contraseña</label>
          <input id={`${idBase}-clave`} className="form-control" type="password" autoComplete="current-password" value={clave} onChange={(e) => setClave(e.target.value)} />
        </>
      )}
      {error && <div role="alert" className="small text-danger">{error}</div>}
      <Boton enClic={() => void enviar()} deshabilitado={enviando || configurado === null}>
        {enviando ? 'Comprobando…' : configurado === false ? 'Crear PIN y continuar' : (textoBoton ?? 'Continuar')}
      </Boton>
    </form>
  )
}

export function PuertaPin({ children }: { children: ReactNode }) {
  const [abierto, setAbierto] = useState(() => !!tokenPin())

  useEffect(() => {
    const alCambiar = () => setAbierto(!!tokenPin())
    window.addEventListener('sigro:pin', alCambiar)
    const t = setInterval(alCambiar, 15_000)          // a los 15 min se vuelve a cerrar
    return () => { window.removeEventListener('sigro:pin', alCambiar); clearInterval(t) }
  }, [])

  if (abierto) {
    return (
      <>
        <div className="d-flex justify-content-end">
          <button type="button" className="btn btn-link btn-sm p-0" onClick={olvidarPin}>Cerrar el panel (pedir el PIN otra vez)</button>
        </div>
        {children}
      </>
    )
  }
  return (
    <Tarjeta>
      <FormularioPin titulo="Escribe tu PIN de administrador" textoBoton="Entrar al panel" />
    </Tarjeta>
  )
}
