/* Administración · llamadas a la API: rutas, cuerpos (snake_case) y errores {codigo, mensaje}, con fetch simulado. */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { administracion } from './administracion'
import { ErrorApi } from './error'

type Pedido = { url: string; metodo: string; cuerpo: unknown; autorizacion: string | null }
let pedidos: Pedido[] = []

function servidor(responder: (url: string, metodo: string) => { estado: number; cuerpo?: unknown }) {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit = {}) => {
    const metodo = init.method ?? 'GET'
    const h = (init.headers ?? {}) as Record<string, string>
    pedidos.push({ url, metodo, cuerpo: init.body ? JSON.parse(String(init.body)) : undefined, autorizacion: h.Authorization ?? null })
    const r = responder(url, metodo)
    return new Response(r.cuerpo === undefined ? '' : JSON.stringify(r.cuerpo), { status: r.estado })
  }))
}

beforeEach(() => {
  pedidos = []
  // sesión abierta (sessionStorage), como después de entrar
  const almacen = new Map<string, string>([['sigro-sesion', JSON.stringify({ token: 'tkn', usuario: { id: 1, nombre: 'Admin', usuario: 'admin' }, obras: [{ id: 1, nombre: 'Obra', rol: 'administrador' }] })]])
  vi.stubGlobal('sessionStorage', { getItem: (k: string) => almacen.get(k) ?? null, setItem: (k: string, v: string) => almacen.set(k, v), removeItem: (k: string) => almacen.delete(k) })
  vi.stubGlobal('window', { dispatchEvent: () => true })
})
afterEach(() => vi.unstubAllGlobals())

describe('celulares', () => {
  it('lista y autoriza en la obra activa, con el token de la sesión', async () => {
    servidor((url) => ({ estado: 200, cuerpo: url.endsWith('/autorizar') ? { id: 3, usuario_nombre: 'Ana', estado: 'activo' } : [{ id: 3, usuario_id: 7, usuario_nombre: 'Ana', estado: 'pendiente' }] }))
    const xs = await administracion.dispositivos('1')
    expect(xs[0]).toMatchObject({ id: 3, usuarioId: 7, usuarioNombre: 'Ana' })
    await administracion.autorizar('1', 3)
    expect(pedidos.map((p) => `${p.metodo} ${p.url}`)).toEqual(['GET /api/v1/obras/1/dispositivos', 'POST /api/v1/obras/1/dispositivos/3/autorizar'])
    expect(pedidos.every((p) => p.autorizacion === 'Bearer tkn')).toBe(true)
  })

  it('el 409 llega como ErrorApi con el código de la restricción', async () => {
    servidor(() => ({ estado: 409, cuerpo: { codigo: 'uq_dispositivo_activo_por_usuario', mensaje: 'Esa persona ya tiene un dispositivo autorizado en esta obra; revócalo antes de autorizar otro.' } }))
    const e = await administracion.autorizar('1', 3).catch((x) => x)
    expect(e).toBeInstanceOf(ErrorApi)
    expect(e.codigo).toBe('uq_dispositivo_activo_por_usuario')
  })

  it('revocar manda el motivo sin espacios de más', async () => {
    servidor(() => ({ estado: 200, cuerpo: { id: 2, estado: 'revocado' } }))
    await administracion.revocar('1', 2, '  Cambió de celular  ')
    expect(pedidos[0]).toMatchObject({ metodo: 'POST', url: '/api/v1/obras/1/dispositivos/2/revocar', cuerpo: { motivo: 'Cambió de celular' } })
  })
})

describe('usuarios', () => {
  it('alta: nombre y usuario recortados; la clave viaja tal cual se escribió', async () => {
    servidor(() => ({ estado: 201, cuerpo: { id: 9, nombre: 'Juan Pérez', usuario: 'jperez', estado: 'activo', creado_en: '2026-11-05T17:00:00Z' } }))
    const u = await administracion.crearUsuario({ nombre: ' Juan Pérez ', usuario: ' jperez ', clave: 'valor-de-prueba' })
    expect(u.creadoEn).toBe('2026-11-05T17:00:00Z')
    expect(pedidos[0]).toMatchObject({ url: '/api/v1/usuarios', cuerpo: { nombre: 'Juan Pérez', usuario: 'jperez', clave: 'valor-de-prueba' } })
  })

  it('clave débil: el mensaje de la API llega intacto', async () => {
    servidor(() => ({ estado: 422, cuerpo: { detail: { codigo: 'clave_debil', mensaje: 'La contraseña debe mezclar mayúsculas, minúsculas y al menos un número.' } } }))
    const e = await administracion.crearUsuario({ nombre: 'A', usuario: 'abc', clave: 'valor-de-prueba' }).catch((x) => x)
    expect(e.codigo).toBe('clave_debil')
    expect(e.message).toBe('La contraseña debe mezclar mayúsculas, minúsculas y al menos un número.')
  })

  it('la baja es un POST a /baja: nunca un DELETE', async () => {
    servidor(() => ({ estado: 200, cuerpo: { id: 9, estado: 'baja' } }))
    await administracion.darDeBaja(9)
    expect(pedidos[0]).toMatchObject({ metodo: 'POST', url: '/api/v1/usuarios/9/baja' })
  })
})

describe('asignaciones y almacenes', () => {
  it('asignar con usuario_id y rol; retirar sin borrar', async () => {
    servidor(() => ({ estado: 201, cuerpo: { id: 5, usuario_id: 9, usuario_nombre: 'Juan', rol: 'almacenero' } }))
    await administracion.asignar('1', 9, 'almacenero')
    await administracion.retirar('1', 5)
    await administracion.asignaciones('1', false)
    expect(pedidos[0]).toMatchObject({ url: '/api/v1/obras/1/asignaciones', cuerpo: { usuario_id: 9, rol: 'almacenero' } })
    expect(pedidos[1]).toMatchObject({ metodo: 'POST', url: '/api/v1/obras/1/asignaciones/5/retiro' })
    expect(pedidos[2].url).toBe('/api/v1/obras/1/asignaciones?vigentes=false')
  })

  it('alta de almacén central o de frente', async () => {
    servidor(() => ({ estado: 201, cuerpo: { id: 3, nombre: 'Torre B', tipo: 'frente', estado: 'activo' } }))
    await administracion.crearAlmacen('1', { nombre: ' Torre B ', tipo: 'frente' })
    expect(pedidos[0]).toMatchObject({ url: '/api/v1/obras/1/almacenes', cuerpo: { nombre: 'Torre B', tipo: 'frente' } })
  })

  it('sin permiso (403): ErrorApi sin_permiso, que la pantalla traduce', async () => {
    servidor(() => ({ estado: 403, cuerpo: { codigo: 'sin_permiso', mensaje: 'No tienes permiso para esta operación en esta obra.' } }))
    const e = await administracion.almacenes('1').catch((x) => x)
    expect(e.codigo).toBe('sin_permiso')
  })
})
