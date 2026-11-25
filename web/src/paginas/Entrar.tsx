/* ─────────────────────────────────────────────────────────────────────────────
   HU-12 · Entrar al sistema · formulario con Card, Form e InputGroup de React-Bootstrap
   Lo que cumple:
   - el acceso exige usuario y contraseña, y los valida el servidor;
   - usuario inexistente y clave errónea reciben el MISMO mensaje (no se revela
     cuál de los dos falló) y el bloqueo a los 5 intentos lo aplica la API;
   - la sesión dura lo que la pestaña: el token nunca va a localStorage.
   ───────────────────────────────────────────────────────────────────────── */
import { useState } from 'react'
import { ErrorApi } from '../api/error'
import { API_URL, iniciarSesion } from '../api/http'
import { Alert, Button, Card, Form, InputGroup } from 'react-bootstrap'
import { PantallaEntrada, Portada } from '../ui/Portada'
import { useForm } from 'react-hook-form'
import { PAUSA_MS } from '../formularios/enVivo'
import { zodResolver } from '@hookform/resolvers/zod'
import { esquemaEntrar, type DatosEntrar } from '../formularios/esquemas'

const PROMESAS = [
  'El presupuesto del S10 se importa tal cual: 284 insumos, al céntimo.',
  'El almacén registra ingresos y salidas desde la obra, aunque no haya señal.',
  'El gasto en recursos se compara con lo planificado, insumo por insumo.',
]

function servidor(): string {
  try { return new URL(API_URL).host } catch { return API_URL }
}

/** `saliendo`: ya entró; la tarjeta se desvanece antes de la capa «Preparando tu obra» (App.tsx). */
export function Entrar({ saliendo = false }: { saliendo?: boolean }) {
  // React Hook Form guarda los campos y sus errores; Zod (esquemaEntrar) dice qué es válido
  const { register, handleSubmit, formState: { errors, dirtyFields, isSubmitting: enviando } } = useForm<DatosEntrar>({
    // validación en vivo: revisa al escribir y al salir; un error nuevo espera la pausa y lo corregido se quita ya
    mode: 'all', delayError: PAUSA_MS,
    resolver: zodResolver(esquemaEntrar),
    defaultValues: { usuario: '', clave: '' },
  })
  const [verClave, setVerClave] = useState(false)
  const [error, setError] = useState('') // error del servidor (401, sin red), no de validación
  // el servidor está despertando (502, 503 o sin red): se reintenta solo y no se muestra un error falso
  const [conectando, setConectando] = useState(false)

  // solo se llama si el esquema pasó (handleSubmit valida antes)
  const entrar = async ({ usuario, clave }: DatosEntrar) => {
    setConectando(false); setError('')
    try {
      await iniciarSesion(usuario, clave, { alReintentar: () => setConectando(true) })
    } catch (e) {
      // 401: «Usuario o contraseña incorrectos.»; agotados los reintentos: «No pudimos conectar…»
      setError(e instanceof ErrorApi ? e.message : 'No se pudo ingresar. Inténtalo de nuevo.')
    } finally {
      setConectando(false)
    }
  }
  const textoBoton = conectando ? 'Conectando con el servidor…' : enviando || saliendo ? 'Entrando…' : 'Entrar'

  return (
    <PantallaEntrada portada={
      <Portada titulo="Lo que pide el S10, lo que se compra y lo que se gasta." remate="En un solo lugar.">
        <ul className="list-unstyled mb-0" style={{ color: '#d2d3d3' }}>
          {PROMESAS.map((t) => <li key={t} className="mb-2">✓ {t}</li>)}
        </ul>
      </Portada>
    }>
      <Card>
        <Card.Body className="p-4">
          <Form noValidate onSubmit={handleSubmit(entrar)}>
            <h2 className="h4 mb-1">Ingresar</h2>
            <p className="text-body-secondary small">Con tu usuario de SIGRO. Verás solo las obras donde estás asignado.</p>

            <Form.Group className="mb-3" controlId="usuario">
              <Form.Label>Usuario</Form.Label>
              {/* {...register('usuario')} conecta el campo: valor, cambios y validación */}
              <Form.Control {...register('usuario')} placeholder="Tu usuario" autoComplete="username" autoFocus
                isInvalid={!!errors.usuario} isValid={!!dirtyFields.usuario && !errors.usuario} />
              <Form.Control.Feedback type="invalid">{errors.usuario?.message}</Form.Control.Feedback>
            </Form.Group>

            <Form.Group className="mb-3" controlId="clave">
              <Form.Label>Contraseña</Form.Label>
              {/* InputGroup: el campo y el botón «Ver» quedan pegados */}
              <InputGroup hasValidation>
                <Form.Control {...register('clave')} type={verClave ? 'text' : 'password'} placeholder="Tu contraseña"
                  autoComplete="current-password" isInvalid={!!errors.clave} />
                <Button variant="outline-secondary" onClick={() => setVerClave(!verClave)}
                  aria-label={verClave ? 'Ocultar contraseña' : 'Mostrar contraseña'} aria-pressed={verClave}>
                  {verClave ? 'Ocultar' : 'Ver'}
                </Button>
                <Form.Control.Feedback type="invalid">{errors.clave?.message}</Form.Control.Feedback>
              </InputGroup>
            </Form.Group>

            {error && <Alert variant="danger" className="py-2">{error}</Alert>}
            <Button type="submit" size="lg" className="w-100" disabled={enviando || saliendo}>{textoBoton}</Button>
            <p className="text-center small text-body-secondary mt-3 mb-0">
              ¿No tienes usuario? Pídelo al administrador de la obra.
            </p>
            {API_URL && <p className="text-center small text-body-secondary mb-0">Servidor: {servidor()}</p>}
          </Form>
        </Card.Body>
      </Card>
    </PantallaEntrada>
  )
}
