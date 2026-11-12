// @vitest-environment jsdom
/* HU-12 · el login no muestra errores falsos mientras el servidor despierta.
   Se prueba iniciarSesion() con un fetch falso: nada de red ni de servidor. */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErrorApi } from './error'
import { iniciarSesion, sesionActual, cerrarSesion } from './http'
import { conReintentos, esPasajero, ESPERAS_LOGIN } from './reintentos'

const SESION = { token: 't', usuario: { id: 1, nombre: 'Henry Ticona', usuario: 'henry' }, obras: [{ id: 1, nombre: 'Multifamiliar Flores', rol: 'residente' }] }
const json = (estado: number, cuerpo: unknown) => new Response(JSON.stringify(cuerpo), { status: estado, headers: { 'Content-Type': 'application/json' } })
const paginaRender502 = () => new Response('<html><body>502 Bad Gateway</body></html>', { status: 502, headers: { 'Content-Type': 'text/html' } })
const sinRed = () => { throw new TypeError('Failed to fetch') }

/** fetch que responde en orden lo que se le da; cuenta las llamadas */
function servidor(...respuestas: (() => Response)[]) {
  const f = vi.fn(async () => {
    const r = respuestas.shift()
    if (!r) throw new Error('el login pidió más veces de lo esperado')
    return r()
  })
  vi.stubGlobal('fetch', f)
  return f
}

beforeEach(() => { vi.useFakeTimers(); cerrarSesion() })
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

/** corre el login avanzando el reloj falso por las esperas entre reintentos */
async function entrar(alReintentar?: (n: number) => void) {
  const p = iniciarSesion('henry', 'clave', { alReintentar })
  p.catch(() => {})           // el error se revisa abajo; evita el aviso de promesa sin capturar
  await vi.runAllTimersAsync()
  return p
}

describe('iniciarSesion', () => {
  it('un 503 y luego 200: entra sin mostrar error y avisa que está conectando', async () => {
    const f = servidor(() => json(503, { codigo: 'servicio_no_disponible', mensaje: 'El servicio de obras no responde' }), () => json(200, SESION))
    const avisos: number[] = []
    await expect(entrar((n) => avisos.push(n))).resolves.toMatchObject({ token: 't' })
    expect(f).toHaveBeenCalledTimes(2)
    expect(avisos).toEqual([1])
    expect(sesionActual()?.usuario.usuario).toBe('henry')
  })

  it('el 502 de Render (HTML, no JSON) también se reintenta', async () => {
    const f = servidor(paginaRender502, paginaRender502, () => json(200, SESION))
    await expect(entrar()).resolves.toMatchObject({ token: 't' })
    expect(f).toHaveBeenCalledTimes(3)
  })

  it('sin red se reintenta; agotados los 3 reintentos recién muestra el error', async () => {
    const f = servidor(sinRed, sinRed, sinRed, sinRed)
    const avisos: number[] = []
    const e = await entrar((n) => avisos.push(n)).catch((x) => x)
    expect(f).toHaveBeenCalledTimes(4)          // 1 intento + 3 reintentos, ni uno más
    expect(avisos).toEqual([1, 2, 3])
    expect(e).toBeInstanceOf(ErrorApi)
    expect(e.codigo).toBe('sin_servidor')
    expect(e.message).not.toMatch(/No se pudo ingresar/)
    expect(sesionActual()).toBeNull()
  })

  it('un 401 real no se reintenta y dice «Usuario o contraseña incorrectos.»', async () => {
    const f = servidor(() => json(401, { codigo: 'credenciales_invalidas', mensaje: 'Usuario o contraseña incorrectos.' }))
    const avisos: number[] = []
    const e = await entrar((n) => avisos.push(n)).catch((x) => x)
    expect(f).toHaveBeenCalledTimes(1)
    expect(avisos).toEqual([])
    expect(e.message).toBe('Usuario o contraseña incorrectos.')
  })

  it('la cuenta bloqueada (429) se informa tal cual, sin reintentar', async () => {
    const f = servidor(() => json(429, { codigo: 'cuenta_bloqueada', mensaje: 'Demasiados intentos. Espera unos minutos e inténtalo de nuevo.' }))
    const e = await entrar().catch((x) => x)
    expect(f).toHaveBeenCalledTimes(1)
    expect(e.codigo).toBe('cuenta_bloqueada')
  })
})

describe('conReintentos', () => {
  it('espera 1 s, 2 s y 4 s: creciente', async () => {
    const esperas: number[] = []
    const hacer = vi.fn(async () => { throw new ErrorApi('http_503', 'x', 503) })
    await expect(conReintentos(hacer, { dormir: async (ms) => { esperas.push(ms) } })).rejects.toMatchObject({ codigo: 'sin_servidor' })
    expect(esperas).toEqual([...ESPERAS_LOGIN])
    expect(esperas[0] < esperas[1] && esperas[1] < esperas[2]).toBe(true)
  })

  it('solo 502, 503, 504 y la falta de red son pasajeros', () => {
    expect(esPasajero(new ErrorApi('x', 'x', 502))).toBe(true)
    expect(esPasajero(new ErrorApi('x', 'x', 503))).toBe(true)
    expect(esPasajero(new ErrorApi('sin_conexion', 'x'))).toBe(true)
    for (const estado of [400, 401, 403, 404, 409, 422, 429, 500]) expect(esPasajero(new ErrorApi('x', 'x', estado))).toBe(false)
    expect(esPasajero(new Error('otra cosa'))).toBe(false)
  })
})
