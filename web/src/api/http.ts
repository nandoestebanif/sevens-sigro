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
import { conReintentos, type OpcionesReintento } from './reintentos'
import { faltaFactor } from './unidades'
import type {
  AvanceInsumo, Compra, EstadoCompra, HistorialFactor, InsumoCatalogo, InsumoMaestro, LineaCompra, LineaRecepcion,
  Obra, ObraActiva, ParametroReposicion, Proveedor, ResultadoRequerimiento, Semana, TipoComprobante,
} from './tipos'
// importes propios de almacén y semana: en su línea, para no chocar con la lista de arriba
import type { Movimiento, ReporteSemana, StockInsumo, VerificacionRecepcion } from './tipos'

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
  let datos: any = null
  try {
    datos = texto ? JSON.parse(texto) : null
  } catch {
    // el 502 de Render mientras un servicio despierta es una página HTML, no {codigo, mensaje}
    if (r.ok) throw new ErrorApi('respuesta_invalida', 'El servidor respondió algo que SIGRO no entiende.', r.status)
  }
  if (!r.ok) {
    const d = (datos && (datos.codigo ? datos : datos.detail)) || {}
    // Pydantic antepone «Value error, » a las validaciones propias: al usuario le basta el mensaje
    const mensaje = typeof d.mensaje === 'string' ? d.mensaje.replace(/^([a-z_]+: )?Value error, /, '') : null
    throw new ErrorApi(d.codigo ?? `http_${r.status}`, mensaje ?? 'El servidor rechazó la operación.', r.status)
  }
  return convertir(datos, aCamel) as T
}

/** HU-12. Si el servidor está despertando (502, 503 o sin red) se reintenta solo, hasta 3 veces con espera
    creciente: la persona no ve «no se pudo ingresar» por algo que no hizo. Un 401 es la respuesta de verdad. */
export async function iniciarSesion(usuario: string, clave: string, o: OpcionesReintento = {}): Promise<Sesion> {
  let s: Sesion
  try {
    s = await conReintentos(() => pedir<Sesion>('POST', '/auth/login', { usuario, clave }), o)
  } catch (e) {
    if (e instanceof ErrorApi && e.estado === 401) {
      // usuario inexistente y clave errónea, el mismo mensaje: no se revela cuál de los dos falló
      throw new ErrorApi('credenciales_invalidas', 'Usuario o contraseña incorrectos.', 401)
    }
    throw e
  }
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
  if (obraActivaId && /^\d+$/.test(obraActivaId)) return obraActivaId
  try {
    const guardado = sessionStorage.getItem(CLAVE_OBRA)
    obraActivaId = guardado && /^\d+$/.test(guardado) ? guardado : null
  } catch { obraActivaId = null }
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
    if (!datos.nombre.trim()) throw new ErrorApi('nombre', 'Escribe el nombre de la obra.')
    if (!datos.inicio || !datos.fin) throw new ErrorApi('fechas', 'Elige las fechas de inicio y de fin.')
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
let codigoPorId = new Map<number, string>()
let descPorCodigo = new Map<string, string>()
let descPorId = new Map<number, string>()
const aMaestro = (i: InsumoApi): InsumoMaestro => ({
  codigo: i.codigo, nombre: i.descripcion, um: i.unidadCompra,
  unidadS10: i.unidadS10,
  factorConversion: Number(i.factorConversion || 1),
  medida: '', marca: '', // la API guarda la descripción completa; marca y precio no son del catálogo
  precioPromedio: 0,     // el precio sale de las compras de cada obra, no del maestro
  estado: i.verificado ? 'activo' : 'por_verificar', creadoEl: '',
})
async function todos(): Promise<InsumoApi[]> {
  const xs = await pedir<InsumoApi[]>('GET', '/catalogo/insumos')
  idPorCodigo = new Map(xs.map((x) => [x.codigo, x.id]))
  codigoPorId = new Map(xs.map((x) => [x.id, x.codigo]))
  descPorCodigo = new Map(xs.map((x) => [x.codigo, x.descripcion]))
  descPorId = new Map(xs.map((x) => [x.id, x.descripcion]))
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
  /** HU-17: declara o actualiza el factor de conversión (unidades S10 por unidad de compra) */
  async cambiarFactor(codigo: string, factor: number, motivo: string): Promise<InsumoMaestro> {
    if (!Number.isFinite(factor) || factor <= 0) throw new ErrorApi('factor', 'El factor debe ser mayor a cero.')
    if (motivo.trim().length < 3) throw new ErrorApi('motivo', 'Escribe el motivo del cambio (mínimo 3 caracteres).')
    const id = await idDe(codigo)
    return aMaestro(await pedir<InsumoApi>('POST', `/catalogo/insumos/${id}/factor-conversion`, {
      factor,
      motivo: motivo.trim(),
    }))
  },
  async historialFactor(codigo: string): Promise<HistorialFactor[]> {
    const id = await idDe(codigo)
    const xs = await pedir<{
      id: number; factorAnterior: string; factorNuevo: string; motivo: string; cambiadoPor: number; cambiadoEn: string
    }[]>('GET', `/catalogo/insumos/${id}/factor-conversion/historial`)
    return xs.map((h) => ({
      id: String(h.id),
      factorAnterior: Number(h.factorAnterior),
      factorNuevo: Number(h.factorNuevo),
      motivo: h.motivo,
      cambiadoPor: `Usuario #${h.cambiadoPor}`,
      cambiadoEn: h.cambiadoEn,
    }))
  },
}

/* ── requerimiento (HU-19), saldo por comprar (HU-24) y parámetros de reposición (HU-25) ──
   todo es de la obra activa (regla 9): la ruta lleva su id y el servidor verifica la asignación */
const obraId = async () => (await obrasHttp.activa()).id
const num = (x: unknown) => Number(x ?? 0) // la API manda los decimales como texto exacto

interface SaldoApi {
  insumoId: number; insumoCodigo: string; descripcion: string; unidadS10: string; unidadCompra: string
  requerido: string; comprado: string; saldo: string; saldoEnUnidadCompra: string; exceso: string
  estado: 'pendiente' | 'completo' | 'excedido'
}
interface ParametroApi {
  insumoCodigo: string; descripcion: string; unidadCompra: string
  stockSeguridad: string; puntoReposicion: string; vigenteDesde: string; insumoId: number
}
const insumoIdPorCodigo = new Map<string, number>()
const aParametro = (p: ParametroApi): ParametroReposicion => {
  insumoIdPorCodigo.set(p.insumoCodigo, p.insumoId)
  return {
    insumoCodigo: p.insumoCodigo, descripcion: p.descripcion, unidadCompra: p.unidadCompra,
    stockSeguridad: num(p.stockSeguridad), puntoReposicion: num(p.puntoReposicion), vigenteDesde: p.vigenteDesde,
  }
}

export const reposicionHttp = {
  async saldo(): Promise<AvanceInsumo[]> {
    const xs = await pedir<SaldoApi[]>('GET', `/obras/${await obraId()}/saldo-por-comprar`)
    return xs.map((s) => {
      const req = num(s.requerido), comp = num(s.comprado)
      return {
        insumoId: String(s.insumoId), codigo: s.insumoCodigo, descripcion: s.descripcion,
        unidadS10: s.unidadS10, unidadCompra: s.unidadCompra,
        fase: 'Toda la obra', // las fases llegan con el requerimiento por fase (HU-20, Sprint 2)
        critico: false,       // marcar críticos es HU-28 (Sprint 2)
        planificadoCantidad: req, planificadoImporte: 0,
        compradoCantidad: comp, compradoPct: req ? Math.round((comp / req) * 1000) / 10 : 0,
        recibidoPct: 0, consumidoPct: 0, gastado: 0, // llegan con almacén y costo
        saldoPorComprar: num(s.saldoEnUnidadCompra), desviacionPuntos: 0,
        faltaFactor: faltaFactor(s.unidadS10, s.unidadCompra, num(s.saldo), num(s.saldoEnUnidadCompra)),
      }
    })
  },
  async parametros(): Promise<ParametroReposicion[]> {
    return (await pedir<ParametroApi[]>('GET', `/obras/${await obraId()}/parametros-reposicion`)).map(aParametro)
  },
  async registrar(d: { insumoCodigo: string; stockSeguridad: number; puntoReposicion: number }): Promise<ParametroReposicion> {
    return aParametro(await pedir<ParametroApi>('POST', `/obras/${await obraId()}/parametros-reposicion`, d))
  },
  async historial(insumoCodigo: string): Promise<ParametroReposicion[]> {
    let id = insumoIdPorCodigo.get(insumoCodigo)
    if (id === undefined) { await reposicionHttp.parametros(); id = insumoIdPorCodigo.get(insumoCodigo) }
    if (id === undefined) return []
    return (await pedir<ParametroApi[]>('GET', `/obras/${await obraId()}/parametros-reposicion/${id}/historial`)).map(aParametro)
  },
  async cargarRequerimiento(filas: { insumoCodigo: string; cantidad: number; precio: number }[]): Promise<ResultadoRequerimiento> {
    return pedir<ResultadoRequerimiento>('POST', `/obras/${await obraId()}/requerimiento/importar`, { filas })
  },
}
/* ── compras (HU-32) y recepción (HU-35) ── */
interface CompraApi {
  id: number; proveedorId: number; proveedorRazonSocial: string
  comprobantesVigentes?: number
  tipoComprobante: TipoComprobante; numeroComprobante: string; fecha: string
  estado: 'registrada' | 'anulada'; totales: { base: string; igv: string; total: string }
  lineas: { insumoCodigo: string; descripcion: string; cantidad: string; unidadCompra: string; precioUnitario: string }[]
}
interface RecepcionLineaApi {
  insumoId: number; unidadCompra: string; pedido: string; recibido: string; pendiente: string
}
function estadoRecepcionDeCompra(lineas: RecepcionLineaApi[]): EstadoCompra {
  if (!lineas.length) return 'registrada'
  const recibido = lineas.reduce((s, l) => s + num(l.recibido), 0)
  const pendiente = lineas.reduce((s, l) => s + num(l.pendiente), 0)
  if (recibido <= 0) return 'registrada'
  return pendiente <= 0 ? 'recibida' : 'recibida_parcial'
}
const aCompra = (c: CompraApi, oId: string, estado?: EstadoCompra): Compra => ({
  id: String(c.id), obraId: oId, proveedorId: String(c.proveedorId),
  proveedorRazonSocial: c.proveedorRazonSocial, tipoComprobante: c.tipoComprobante,
  numeroComprobante: c.numeroComprobante, fecha: c.fecha,
  estado: estado ?? (c.estado === 'anulada' ? 'anulada' : 'registrada'),
  lineas: c.lineas.map((l) => ({
    insumoId: l.insumoCodigo, descripcion: l.descripcion, unidadCompra: l.unidadCompra,
    cantidad: num(l.cantidad), precioUnitario: num(l.precioUnitario),
  })),
  totales: { base: num(c.totales.base), igv: num(c.totales.igv), total: num(c.totales.total) },
  adjunto: (c.comprobantesVigentes ?? 0) > 0,
})
const nuevoUuid = () =>
  typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })

export const comprasHttp = {
  async registrar(d: { proveedorId: string; tipoComprobante: TipoComprobante; numeroComprobante: string; fecha: string; lineas: LineaCompra[] }): Promise<Compra> {
    if (!d.proveedorId) throw new ErrorApi('proveedor', 'Elige el proveedor de la compra.')
    const oId = await obraId()
    const c = await pedir<CompraApi>('POST', `/obras/${oId}/compras`, {
      proveedorId: Number(d.proveedorId),
      tipoComprobante: d.tipoComprobante,
      numeroComprobante: d.numeroComprobante.trim(),
      fecha: d.fecha,
      lineas: d.lineas.map((l) => ({
        insumoCodigo: l.insumoId,
        cantidad: l.cantidad,
        unidadCompra: l.unidadCompra,
        precioUnitario: l.precioUnitario,
      })),
    })
    return aCompra(c, oId)
  },
  async listar(): Promise<Compra[]> {
    const oId = await obraId()
    const cs = await pedir<CompraApi[]>('GET', `/obras/${oId}/compras`)
    const estados = await Promise.allSettled(
      cs.map((c) => c.estado === 'anulada'
        ? Promise.resolve<EstadoCompra>('anulada')
        : pedir<RecepcionLineaApi[]>('GET', `/obras/${oId}/almacen/compras/${c.id}/recepcion`).then(estadoRecepcionDeCompra)),
    )
    return cs.map((c, i) => {
      const r = estados[i]
      return aCompra(c, oId, r.status === 'fulfilled' ? r.value : (c.estado === 'anulada' ? 'anulada' : 'registrada'))
    })
  },
}

export const recepcionHttp = {
  async detalle(compraId: string): Promise<LineaRecepcion[]> {
    const cId = Number(compraId)
    if (!Number.isInteger(cId) || cId < 1) throw new ErrorApi('no_existe', 'Esa compra no existe.')
    const oId = await obraId()
    const [lineasRec, compra] = await Promise.all([
      pedir<RecepcionLineaApi[]>('GET', `/obras/${oId}/almacen/compras/${cId}/recepcion`),
      pedir<CompraApi>('GET', `/obras/${oId}/compras/${cId}`),
    ])
    if (lineasRec.some((l) => !codigoPorId.has(l.insumoId))) {
      await todos()
    }
    const porCodigo = new Map(compra.lineas.map((l) => [l.insumoCodigo, l]))
    return lineasRec.map((l) => {
      const cod = codigoPorId.get(l.insumoId)
      const lc = cod ? porCodigo.get(cod) : undefined
      return {
        insumoId: String(l.insumoId),
        descripcion: lc?.descripcion ?? descPorId.get(l.insumoId) ?? `Insumo #${l.insumoId}`,
        unidadCompra: l.unidadCompra,
        precioUnitario: lc ? num(lc.precioUnitario) : 0,
        pedido: num(l.pedido),
        recibido: num(l.recibido),
        pendiente: num(l.pendiente),
      }
    })
  },
  /** HU-36 · T-297: lo mismo que se va a recibir, para avisar ANTES de guardar si pasa lo requerido (avisa, no bloquea) */
  async verificar(compraId: string, lineas: { insumoId: string; cantidad: number }[]): Promise<VerificacionRecepcion> {
    const v = await pedir<VerificacionApi>('POST', `/obras/${await obraId()}/almacen/recepciones/verificacion`, {
      compraId: Number(compraId),
      lineas: lineas.filter((l) => l.cantidad > 0).map((l) => ({ insumoId: Number(l.insumoId), cantidad: l.cantidad })),
    })
    if (v.lineas.some((l) => !descPorId.has(l.insumoId))) await todos()
    return {
      hayExceso: v.hayExceso,
      lineas: v.lineas.map((l) => ({
        insumoId: String(l.insumoId), descripcion: descPorId.get(l.insumoId) ?? l.insumoCodigo, unidad: l.unidad,
        requerido: l.requerido === null ? null : num(l.requerido), conEsta: num(l.conEsta),
        exceso: l.exceso === null ? null : num(l.exceso), excede: l.excede,
      })),
    }
  },
  async registrar(compraId: string, lineas: { insumoId: string; cantidad: number }[], motivoExceso?: string): Promise<void> {
    const cId = Number(compraId)
    if (!Number.isInteger(cId) || cId < 1) throw new ErrorApi('no_existe', 'Esa compra no existe.')
    const conCantidad = lineas.filter((l) => l.cantidad > 0)
    if (!conCantidad.length) throw new ErrorApi('vacia', 'La recepción necesita al menos una cantidad mayor a cero.')
    const oId = await obraId()
    await pedir('POST', `/obras/${oId}/almacen/recepciones`, {
      uuidCliente: nuevoUuid(),
      compraId: cId,
      lineas: conCantidad.map((l) => ({
        insumoId: Number(l.insumoId),
        cantidad: l.cantidad,
      })),
      // HU-36: si la verificación avisó exceso, el servidor exige el motivo; queda en el kárdex
      ...(motivoExceso && motivoExceso.trim() ? { motivoExceso: motivoExceso.trim() } : {}),
    })
  },
}

/* ── almacén (HU-35, HU-36, HU-43, HU-45) y avance (HU-23) ──────────────────
   El saldo, el kárdex y el estado de una compra los calcula el servidor (regla 2): aquí solo se traducen nombres y
   unidades. La web identifica los insumos por su código (regla 8); la API, por su id: la traducción vive aquí. */
const almacen = async () => `/obras/${await obraId()}/almacen`

async function codigoDe(id: number): Promise<string> {
  if (!codigoPorId.has(id)) await todos()
  return codigoPorId.get(id) ?? String(id)
}
async function descripcionDe(codigo: string): Promise<string> {
  if (!descPorCodigo.has(codigo)) await todos()
  return descPorCodigo.get(codigo) ?? codigo
}

interface VerificacionApi {
  compraId: number; hayExceso: boolean
  lineas: { insumoId: number; insumoCodigo: string; unidad: string; requerido: string | null; yaRecibido: string;
    conEsta: string; exceso: string | null; excede: boolean }[]
}
interface MovimientoApi {
  id: number; uuidCliente: string; tipo: string; sentido: number; proyectoId: number; almacenId: number; insumoId: number
  insumoCodigo: string | null; unidad: string | null; cantidad: string; costoUnitario: string; motivo: string | null
  quienRetira: string | null; origen: string | null; recepcionId: number | null; revierteAId: number | null
  registradoPor: number; fechaServidor: string; revertidoPorId?: number | null; recepcionMotivoExceso?: string | null
}
interface StockFilaApi {
  almacenId: number | null; almacen: string | null; insumoId: number; insumoCodigo: string; descripcion: string
  unidad: string; cantidad: string; ultimoMovimientoEn: string; puntoReposicion: string | null; bajoPuntoReposicion: boolean | null
}
interface StockApi { corte: string | null; porAlmacen: StockFilaApi[]; consolidado: StockFilaApi[]; advertencias: string[] }
interface AlmacenApi { id: number; nombre: string }

/** el id del servidor se muestra como en el lienzo (M-0007) y se lee de vuelta al corregir */
export const idMovimiento = (id: number) => `M-${String(id).padStart(4, '0')}`
const idServidor = (id: string) => Number(id.replace(/^M-0*/, '')) || Number(id)

/** el texto del motivo según el tipo: una salida dice quién retiró; una recepción, de qué compra viene */
function motivoDe(m: MovimientoApi): string {
  if (m.motivo) return m.quienRetira ? `${m.motivo} · retira ${m.quienRetira}` : m.motivo
  if (m.tipo === 'ingreso_compra') {
    return m.recepcionMotivoExceso ? `Recepción de compra · motivo del exceso de esa recepción: ${m.recepcionMotivoExceso}` : 'Recepción de compra'
  }
  if (m.origen) return `Ingreso sin compra (${m.origen})`
  return m.tipo.replace(/_/g, ' ')
}

async function aMovimiento(m: MovimientoApi, nombres: Map<number, string>, oId: string): Promise<Movimiento> {
  const codigo = m.insumoCodigo ?? (await codigoDe(m.insumoId))
  const yo = sesionActual()?.usuario
  return {
    id: idMovimiento(m.id), obraId: oId, uuidCliente: m.uuidCliente, fechaServidor: m.fechaServidor,
    almacen: nombres.get(m.almacenId) ?? `Almacén ${m.almacenId}`,
    insumoId: codigo, descripcion: await descripcionDe(codigo), unidad: m.unidad ?? '',
    tipo: m.tipo === 'reverso' ? 'reverso' : m.sentido > 0 ? 'ingreso' : 'salida',
    cantidad: num(m.cantidad), costoUnitario: num(m.costoUnitario), motivo: motivoDe(m),
    autor: yo && yo.id === m.registradoPor ? yo.nombre : `Usuario ${m.registradoPor}`,
    reversaA: m.revierteAId ? idMovimiento(m.revierteAId) : null,
    anuladoPor: m.revertidoPorId ? idMovimiento(m.revertidoPorId) : null,
  }
}

async function nombresDeAlmacen(): Promise<Map<number, string>> {
  try {
    return new Map((await pedir<AlmacenApi[]>('GET', `/obras/${await obraId()}/almacenes`)).map((a) => [a.id, a.nombre]))
  } catch { return new Map() }   // sin la lista se muestra «Almacén 3»: el dato no se inventa
}

export const almacenHttp = {
  /** HU-43: por almacén y de toda la obra, con la hora del último movimiento; nada se suma en la web */
  async stock(): Promise<StockInsumo[]> {
    const s = await pedir<StockApi>('GET', `${await almacen()}/stock`)
    return s.consolidado.map((c) => ({
      insumoId: c.insumoCodigo, descripcion: c.descripcion, unidad: c.unidad,
      porAlmacen: s.porAlmacen.filter((p) => p.insumoId === c.insumoId).map((p) => ({ almacen: p.almacen ?? '', saldo: num(p.cantidad) })),
      saldoTotal: num(c.cantidad),
      puntoReposicion: c.puntoReposicion === null ? null : num(c.puntoReposicion),
      bajoPunto: c.bajoPuntoReposicion === true,
      ultimoMovimiento: c.ultimoMovimientoEn,
    })).sort((a, b) => Number(b.bajoPunto) - Number(a.bajoPunto))
  },
  /** kárdex completo, del más reciente al más antiguo; el anulado y el que lo anuló vienen del servidor */
  async kardex(): Promise<Movimiento[]> {
    const [xs, nombres, oId] = await Promise.all([pedir<MovimientoApi[]>('GET', `${await almacen()}/kardex`), nombresDeAlmacen(), obraId()])
    const movs = await Promise.all(xs.map((m) => aMovimiento(m, nombres, oId)))
    return movs.sort((a, b) => b.fechaServidor.localeCompare(a.fechaServidor))
  },
  /** HU-45: corregir = un movimiento inverso que apunta al original; el original no se toca */
  async revertir(id: string, motivo: string): Promise<Movimiento> {
    if (motivo.trim().length < 5) throw new ErrorApi('motivo', 'El motivo es obligatorio: sin motivo no hay rastro que valga.')
    const [m, nombres, oId] = await Promise.all([
      pedir<MovimientoApi>('POST', `${await almacen()}/movimientos/${idServidor(id)}/reverso`, { uuidCliente: nuevoUuid(), motivo: motivo.trim() }),
      nombresDeAlmacen(), obraId(),
    ])
    return aMovimiento(m, nombres, oId)
  },
}

/* ── HU-44: entradas y salidas de la semana ── */
interface SemanaApi {
  numero: number; fechaInicio: string; fechaFin: string; estado: string; criterio: string
  filas: { almacen: string | null; insumoCodigo: string; descripcion: string; unidad: string; saldoInicial: string
    entradas: string; salidas: string; saldoFinal: string }[]
}
export const semanaHttp = {
  async reporte(numero: number): Promise<ReporteSemana> {
    const s = await pedir<SemanaApi>('GET', `${await almacen()}/semana?numero=${numero}`)
    return {
      numero: s.numero, inicio: s.fechaInicio, fin: s.fechaFin, cerrada: s.estado === 'cerrado', criterio: s.criterio,
      filas: s.filas.map((f) => ({
        almacen: f.almacen ?? '', insumoId: f.insumoCodigo, descripcion: f.descripcion, unidad: f.unidad,
        saldoInicial: num(f.saldoInicial), entradas: num(f.entradas), salidas: num(f.salidas), saldoFinal: num(f.saldoFinal),
      })),
    }
  },
  /** el CSV lo arma el servidor (mismas cifras que la pantalla); aquí solo se descarga con la sesión */
  async csv(numero: number): Promise<Blob> {
    const s = sesionActual()
    let r: Response
    try {
      r = await fetch(`${API_URL}/api/v1${await almacen()}/semana?numero=${numero}&formato=csv`, {
        headers: s ? { Authorization: `Bearer ${s.token}` } : {},
      })
    } catch {
      throw new ErrorApi('sin_conexion', 'No se pudo conectar con el servidor de SIGRO. Revisa tu conexión e inténtalo de nuevo.')
    }
    if (!r.ok) throw new ErrorApi(`http_${r.status}`, 'El servidor no pudo armar el archivo de esa semana.', r.status)
    return r.blob()
  },
}

/* ── HU-23: requerido, comprado, recibido y consumido por insumo, en unidad de compra (lo calcula compras) ── */
interface AvanceApi {
  pendiente: string[]
  filas: { insumoId: number; insumoCodigo: string; descripcion: string; unidadCompra: string; requerido: string; comprado: string
    recibido: string | null; consumido: string | null; saldoPorComprar: string; pctComprado: string; pctRecibido: string | null
    pctConsumido: string | null }[]
}
export const avanceHttp = {
  async porInsumo(): Promise<AvanceInsumo[]> {
    const a = await pedir<AvanceApi>('GET', `/obras/${await obraId()}/avance`)
    return a.filas.map((f) => ({
      insumoId: f.insumoCodigo, codigo: f.insumoCodigo, descripcion: f.descripcion, unidadS10: f.unidadCompra, unidadCompra: f.unidadCompra,
      fase: 'Toda la obra', critico: false,
      planificadoCantidad: num(f.requerido), planificadoImporte: 0, compradoCantidad: num(f.comprado),
      compradoPct: num(f.pctComprado), recibidoPct: num(f.pctRecibido), consumidoPct: num(f.pctConsumido),
      gastado: 0, saldoPorComprar: num(f.saldoPorComprar), desviacionPuntos: 0,
      // null: el servidor declaró que aún no puede calcularlo (a.pendiente); la pantalla lo muestra como «—»
      sinDatoAlmacen: f.recibido === null,
    }))
  },
}
