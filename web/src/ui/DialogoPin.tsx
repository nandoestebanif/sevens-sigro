/* SIGRO · ventana del PIN para las acciones delicadas (26/11).
   Se monta una vez en la app y se registra en api/pin: pedir() la abre antes de cerrar una semana o la obra,
   cambiar el plazo, anular una compra, ajustar el requerimiento o cambiar un factor. Cancelar = no se hace nada. */
import { useEffect, useRef, useState } from 'react'
import { registrarSolicitantePin } from '../api/pin'
import { FormularioPin } from '../paginas/administracion/PuertaPin'
import { Modal } from './base'

export function DialogoPin() {
  const [motivo, setMotivo] = useState<string | null>(null)
  const resolver = useRef<((ok: boolean) => void) | null>(null)

  useEffect(() => {
    registrarSolicitantePin((m) => new Promise<boolean>((res) => {
      resolver.current?.(false)          // una sola ventana a la vez
      resolver.current = res
      setMotivo(m)
    }))
    return () => registrarSolicitantePin(null)
  }, [])

  const terminar = (ok: boolean) => { resolver.current?.(ok); resolver.current = null; setMotivo(null) }
  if (motivo === null) return null
  return (
    <Modal titulo="Confirma con tu PIN" enCerrar={() => terminar(false)}>
      <FormularioPin titulo="" idBase="pin-accion" textoBoton="Confirmar"
        ayuda={`${motivo} Vale 15 minutos en esta pestaña; 5 intentos fallidos lo bloquean 10 minutos.`}
        alListo={() => terminar(true)} />
    </Modal>
  )
}
