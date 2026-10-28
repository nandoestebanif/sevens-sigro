/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · HT-04 · cliente HTTP de la API real (T-240)

   Una sola pieza habla con el servidor de Adiel (`/api/v1`). Aquí, y en ningún
   otro lugar, se resuelven tres cosas:
   1. la sesión: token Bearer guardado solo mientras dure la pestaña;
   2. los nombres: la API usa snake_case y la web camelCase;
   3. los errores: la API responde {codigo, mensaje} y la web los muestra como
      ErrorApi, igual que los simulados — las pantallas no notan la diferencia.

   Se activa con VITE_API_URL (ej. http://localhost:8000). Sin esa variable la
   web sigue con el cliente simulado, para que nadie quede bloqueado.
   ───────────────────────────────────────────────────────────────────────── */
import { ErrorApi } from './error'
import type { InsumoCatalogo, InsumoMaestro, Obra, ObraActiva, Proveedor, Semana } from './tipos'

export const API_URL: string = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '')
export const usaApiReal = API_URL !== ''

/* ── sesión ── sessionStorage: se borra al cerrar la pestaña; nunca localStorage
   para un token. Envuelto en try/catch porque algunos navegadores lo bloquean. */
const CLAVE = 'sigro-sesion'
export interface Sesion {
  token: string
  usuario: { id: number; nombre: string; usuario: string }
  obras: { id: number; nombre: string; rol: string }[]
}
let sesionMem: Sesion | null = null
export function sesionActual(): Sesion | null {
  if (sesionMem) return sesionMem
  try {
    const s = sessionStorage.getItem(CLAVE)
    sesionMem = s ? (JSON.parse(s) as Sesion) : null
  } catch { sesionMem = null }
  return sesionMem
}
function guardarSesion(s: Sesion | null) {
  sesionMem = s
  try {
    if (s) sessionStorage.setItem(CLAVE, JSON.stringify(s))
    else sessionStorage.removeItem(CLAVE)
  } catch { /* sin almacenamiento: la sesión vive en memoria */ }
  window.dispatchEvent(new Event('sigro-sesion'))
}
export function cerrarSesion() { guardarSesion(null) }

/* ── nombres ── */
const aCamel = (k: string) => k.replace(/_([a-z0-9])/g, (_, c: string) => c.toUpperCase())
const aSnake = (k: string) => k.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`)
function convertir(v: unknown, f: (k: string) => string): unknown {
  if (Array.isArray(v)) return v.map((x) => convertir(x, f))
  if (v && typeof v === 'object') {
    return Object.fromEntries(Object.entries(v as Record<string, unknown>).map(([k, x]) => [f(k), convertir(x, f)]))
  }
  return v
}

/* ── una petición ── */
export async function pedir<T>(metodo: 'GET' | 'POST', ruta: string, cuerpo?: object): Promise<T> {
  const s = sesionActual()
  let r: Response
  try {
    r = await fetch(`${API_URL}/api/v1${ruta}`, {
      method: metodo,
      headers: {
        ...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
        ...(s ? { Authorization: `Bearer ${s.token}` } : {}),
      },
      body: cuerpo ? JSON.stringify(convertir(cuerpo, aSnake)) : undefined,
    })
  } catch {
    // un servidor caído no deja la pantalla en blanco: se dice qué pasó
    throw new ErrorApi('sin_conexion', 'No se pudo conectar con el servidor de SIGRO. Revisa tu conexión e inténtalo de nuevo.')
  }
  if (r.status === 401 && s) {
    guardarSesion(null) // token vencido o usuario dado de baja: se vuelve a entrar
    throw new ErrorApi('sesion_vencida', 'Tu sesión venció. Vuelve a ingresar.')
  }
  const texto = await r.text()
  const datos = texto ? JSON.parse(texto) : null
  if (!r.ok) {
    const d = (datos && (datos.codigo ? datos : datos.detail)) || {}
    throw new ErrorApi(d.codigo ?? `http_${r.status}`, d.mensaje ?? 'El servidor rechazó la operación.')
  }
  return convertir(datos, aCamel) as T
}

export async function iniciarSesion(usuario: string, clave: string): Promise<Sesion> {
  const s = await pedir<Sesion>('POST', '/auth/login', { usuario, clave })
  guardarSesion({ token: s.token, usuario: s.usuario, obras: s.obras })
  return s
}

/* ── obra (HU-01, HU-03) ── */
interface ObraApi {
  id: number; nombre: string; fechaInicio: string; fechaFin: string
  estado: string; semana: number; semanasTotales: number
}
interface PeriodoApi { id: number; numero: number; fechaInicio: string; fechaFin: string; estado: string }

const dias = (a: string, b: string) => Math.round((Date.parse(b) - Date.parse(a)) / 86400000) + 1
/** el servidor dice si el periodo está cerrado; «en curso / por venir» sale del calendario */
function aSemana(p: PeriodoApi, hoy: string): Semana {
  const estado: Semana['estado'] = p.estado === 'cerrado' || p.fechaFin < hoy ? 'cerrada'
    : p.fechaInicio <= hoy ? 'en_curso' : 'por_venir'
  return { numero: p.numero, inicio: p.fechaInicio, fin: p.fechaFin, dias: dias(p.fechaInicio, p.fechaFin), estado }
}
async function aObra(o: ObraApi): Promise<Obra> {
  const periodos = await pedir<PeriodoApi[]>('GET', `/obras/${o.id}/periodos`)
  const hoy = new Date().toISOString().slice(0, 10)
  return {
    id: String(o.id), nombre: o.nombre,
    presupuesto: '', // la API aún no guarda el código del presupuesto del S10 (pedido a Adiel)
    inicio: o.fechaInicio, fin: o.fechaFin,
    estado: o.estado === 'cerrada' ? 'cerrada' : 'en_ejecucion',
    semanas: periodos.map((p) => aSemana(p, hoy)),
  }
}

const CLAVE_OBRA = 'sigro-obra-activa'
let obraActivaId: string | null = null
function leerObraActiva(): string | null {
  if (obraActivaId) return obraActivaId
  try { obraActivaId = sessionStorage.getItem(CLAVE_OBRA) } catch { /* nada */ }
  return obraActivaId
}

export const obrasHttp = {
  async listar(): Promise<Obra[]> {
    const os = await pedir<ObraApi[]>('GET', '/obras')
    return Promise.all(os.map(aObra))
  },
  async activa(): Promise<ObraActiva> {
    const os = await pedir<ObraApi[]>('GET', '/obras')
    if (!os.length) throw new ErrorApi('sin_obras', 'No tienes ninguna obra asignada. Pide al administrador que te asigne una.')
    const id = leerObraActiva()
    const o = os.find((x) => String(x.id) === id) ?? os[0]
    return { id: String(o.id), nombre: o.nombre, presupuesto: '', semana: o.semana, semanasTotales: o.semanasTotales }
  },
  async elegir(id: string): Promise<ObraActiva> {
    obraActivaId = id
    try { sessionStorage.setItem(CLAVE_OBRA, id) } catch { /* nada */ }
    return obrasHttp.activa()
  },
  async crear(datos: { nombre: string; inicio: string; fin: string }): Promise<Obra> {
    // las validaciones de HU-01 las hace el servidor (CHECK fin ≥ inicio, nombre obligatorio)
    const o = await pedir<ObraApi>('POST', '/obras', {
      nombre: datos.nombre.trim(), fechaInicio: datos.inicio, fechaFin: datos.fin,
    })
    return aObra(o)
  },
}

/* ── proveedores (HU-29, HU-30) ── */
interface ProveedorApi {
  id: number; ruc: string; razonSocial: string; estado: Proveedor['estado']
  comprasRegistradas: number; ultimaCompra: string | null
}
const aProveedor = (p: ProveedorApi): Proveedor => ({
  id: String(p.id), ruc: p.ruc, razonSocial: p.razonSocial, estado: p.estado,
  diasEntrega: null, // la API aún no registra días de entrega
  comprasRegistradas: p.comprasRegistradas, ultimaCompra: p.ultimaCompra,
})
export const proveedoresHttp = {
  async listar(q = ''): Promise<Proveedor[]> {
    return (await pedir<ProveedorApi[]>('GET', `/proveedores?q=${encodeURIComponent(q.trim())}`)).map(aProveedor)
  },
  async paraComprar(): Promise<Proveedor[]> {
    return (await pedir<ProveedorApi[]>('GET', '/proveedores?solo_para_comprar=true')).map(aProveedor)
  },
  async crear(ruc: string, razonSocial: string): Promise<Proveedor> {
    return aProveedor(await pedir<ProveedorApi>('POST', '/proveedores', { ruc: ruc.trim(), razonSocial: razonSocial.trim() }))
  },
}

/* ── catálogo de insumos (HU-07, HU-08, HU-17) ── el catálogo es de la empresa:
   el mismo código en todas las obras (regla 8). El código lo genera el servidor. */
interface InsumoApi {
  id: number; codigo: string; descripcion: string; unidadS10: string; unidadCompra: string
  factorConversion: string; tipo: string; activo: boolean; verificado: boolean
}
let idPorCodigo = new Map<string, number>()
let descPorCodigo = new Map<string, string>()
const aMaestro = (i: InsumoApi): InsumoMaestro => ({
  codigo: i.codigo, nombre: i.descripcion, um: i.unidadCompra,
  medida: '', marca: '', // la API guarda la descripción completa; marca y precio no son del catálogo
  precioPromedio: 0,     // el precio sale de las compras de cada obra, no del maestro
  estado: i.verificado ? 'activo' : 'por_verificar', creadoEl: '',
})
async function todos(): Promise<InsumoApi[]> {
  const xs = await pedir<InsumoApi[]>('GET', '/catalogo/insumos')
  idPorCodigo = new Map(xs.map((x) => [x.codigo, x.id]))
  descPorCodigo = new Map(xs.map((x) => [x.codigo, x.descripcion]))
  return xs
}
async function idDe(codigo: string): Promise<number> {
  if (!idPorCodigo.has(codigo)) await todos()
  const id = idPorCodigo.get(codigo)
  if (id === undefined) throw new ErrorApi('no_existe', 'Ese código no existe en el catálogo.')
  return id
}
export const catalogoHttp = {
  async maestro(): Promise<InsumoMaestro[]> { return (await todos()).map(aMaestro) },
  async insumos(): Promise<InsumoCatalogo[]> {
    return (await todos()).filter((i) => i.activo).map((i) => ({
      id: i.codigo, codigo: i.codigo, descripcion: i.descripcion, unidadCompra: i.unidadCompra,
    }))
  },
  async crear(d: { nombre: string; um: string; medida?: string; marca?: string }): Promise<InsumoMaestro> {
    // la descripción estandarizada junta material, medida y marca; el duplicado lo rechaza el servidor
    const descripcion = [d.nombre, d.medida, d.marca].map((x) => (x ?? '').trim()).filter(Boolean).join(' ').toUpperCase()
    const i = await pedir<InsumoApi>('POST', '/catalogo/insumos', { descripcion, unidad: d.um.trim(), tipo: 'material' })
    idPorCodigo.set(i.codigo, i.id)
    return aMaestro(i)
  },
  async editar(codigo: string, d: { nombre?: string; marca?: string; verificar?: boolean }): Promise<InsumoMaestro> {
    const id = await idDe(codigo)
    const cambiaNombre = d.nombre !== undefined && d.nombre.trim().toUpperCase() !== (descPorCodigo.get(codigo) ?? '').toUpperCase()
    const cambiaMarca = (d.marca ?? '').trim() !== ''
    if (cambiaNombre || cambiaMarca) {
      // la API todavía no permite corregir la descripción (pedido a Adiel); verificar sí
      throw new ErrorApi('no_disponible', 'Corregir el nombre o la marca todavía no está disponible en el servidor. Por ahora solo se puede verificar el insumo.')
    }
    if (!d.verificar) return aMaestro((await todos()).find((x) => x.id === id)!)
    return aMaestro(await pedir<InsumoApi>('POST', `/catalogo/insumos/${id}/verificar`))
  },
  /** HU-07: aplica lo que leyó Integraciones; el código es la clave, repetir no duplica, no toca saldos */
  async aplicarS10(filas: { codigo: string; descripcion: string; unidad: string; tipo: string }[]) {
    return pedir<{ creados: number; actualizados: number; sinCambios: number; advertencias: string[] }>(
      'POST', '/catalogo/insumos/importar-s10', { insumos: filas })
  },
}
