/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · avisos flotantes (Toast de Bootstrap)

   Para confirmar algo que YA pasó («Compra registrada», «Reverso registrado»):
   aparece arriba a la derecha y se va solo a los 6 s. Los errores y las
   advertencias siguen dentro de la página (Alert), porque piden una acción.

   Se usa con un Context de React: <ProveedorAvisos> envuelve la app una vez y
   cualquier pantalla llama a  const avisar = useAvisos()  →  avisar('Listo').
   ───────────────────────────────────────────────────────────────────────── */
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { Toast, ToastContainer } from 'react-bootstrap'

type Tono = 'bien' | 'info' | 'aviso'
interface AvisoFlotante { id: number; texto: string; tono: Tono }
type Avisar = (texto: string, tono?: Tono) => void

const Contexto = createContext<Avisar>(() => {})

/** Devuelve la función para mostrar un aviso flotante. */
export const useAvisos = () => useContext(Contexto)

const ESTILO: Record<Tono, { bg: string; titulo: string }> = {
  bien: { bg: 'success', titulo: 'Listo' },
  info: { bg: 'info', titulo: 'Aviso' },
  aviso: { bg: 'warning', titulo: 'Atención' },
}

export function ProveedorAvisos({ children }: { children: ReactNode }) {
  const [avisos, setAvisos] = useState<AvisoFlotante[]>([])
  const avisar = useCallback<Avisar>((texto, tono = 'bien') => {
    setAvisos((a) => [...a.slice(-2), { id: Date.now() + Math.random(), texto, tono }]) // como mucho 3 a la vez
  }, [])
  const quitar = (id: number) => setAvisos((a) => a.filter((x) => x.id !== id))

  return (
    <Contexto.Provider value={avisar}>
      {children}
      {/* aria-live: el lector de pantalla lee el aviso sin mover el foco */}
      <ToastContainer position="top-end" className="p-3 position-fixed" style={{ zIndex: 1090 }} aria-live="polite">
        {avisos.map((a) => (
          <Toast key={a.id} onClose={() => quitar(a.id)} delay={6000} autohide bg={`${ESTILO[a.tono].bg}-subtle`}>
            <Toast.Header closeLabel="Cerrar aviso">
              <span className={`rounded-circle bg-${ESTILO[a.tono].bg} me-2`} style={{ width: 10, height: 10 }} aria-hidden />
              <strong className="me-auto">{ESTILO[a.tono].titulo}</strong>
            </Toast.Header>
            <Toast.Body className={`text-${ESTILO[a.tono].bg}-emphasis`}>{a.texto}</Toast.Body>
          </Toast>
        ))}
      </ToastContainer>
    </Contexto.Provider>
  )
}
