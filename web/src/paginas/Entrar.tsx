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
import { Alert, Button, Card, Col, Container, Form, InputGroup, Row } from 'react-bootstrap'
import logo from '../assets/sevens-logo.png'

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
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [verClave, setVerClave] = useState(false)
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  // el servidor está despertando (502, 503 o sin red): se reintenta solo y no se muestra un error falso
  const [conectando, setConectando] = useState(false)

  const entrar = async () => {
    if (!usuario.trim() || !clave) { setError('Escribe tu usuario y tu contraseña.'); return }
    setEnviando(true); setConectando(false); setError('')
    try {
      await iniciarSesion(usuario.trim(), clave, { alReintentar: () => setConectando(true) })
    } catch (e) {
      // 401: «Usuario o contraseña incorrectos.»; agotados los reintentos: «No pudimos conectar…»
      setError(e instanceof ErrorApi ? e.message : 'No se pudo ingresar. Inténtalo de nuevo.')
    } finally {
      setEnviando(false); setConectando(false)
    }
  }
  const textoBoton = conectando ? 'Conectando con el servidor…' : enviando || saliendo ? 'Entrando…' : 'Entrar'

  return (
    <Container className="min-vh-100 d-flex align-items-center justify-content-center py-5">
      <Row className="w-100 justify-content-center g-4 align-items-center">
        {/* columna de presentación: logo y qué hace SIGRO (se oculta en celulares) */}
        <Col lg={5} className="d-none d-lg-block">
          <img src={logo} alt="SEVEN'S Ingenieros SAC" height={90} className="mb-4" />
          <h1 className="h3 mb-3">Lo que pide el S10, lo que se compra y lo que se gasta. En un solo lugar.</h1>
          <ul className="list-unstyled text-body-secondary">
            {PROMESAS.map((t) => <li key={t} className="mb-2">✓ {t}</li>)}
          </ul>
        </Col>

        <Col sm={10} md={7} lg={5} xl={4}>
          <Card className="shadow">
            <Card.Body className="p-4">
              <Form onSubmit={(e) => { e.preventDefault(); entrar() }}>
                <h2 className="h4 mb-1">Ingresar</h2>
                <p className="text-body-secondary small">Con tu usuario de SIGRO. Verás solo las obras donde estás asignado.</p>

                <Form.Group className="mb-3" controlId="usuario">
                  <Form.Label>Usuario</Form.Label>
                  <Form.Control value={usuario} placeholder="Tu usuario" autoComplete="username" autoFocus
                    onChange={(e) => setUsuario(e.target.value)} />
                </Form.Group>

                <Form.Group className="mb-3" controlId="clave">
                  <Form.Label>Contraseña</Form.Label>
                  {/* InputGroup: el campo y el botón «Ver» quedan pegados */}
                  <InputGroup>
                    <Form.Control type={verClave ? 'text' : 'password'} value={clave} placeholder="Tu contraseña"
                      autoComplete="current-password" onChange={(e) => setClave(e.target.value)} />
                    <Button variant="outline-secondary" onClick={() => setVerClave(!verClave)}
                      aria-label={verClave ? 'Ocultar contraseña' : 'Mostrar contraseña'} aria-pressed={verClave}>
                      {verClave ? 'Ocultar' : 'Ver'}
                    </Button>
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
        </Col>
      </Row>
    </Container>
  )
}
