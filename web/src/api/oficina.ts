/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · acciones de oficina sobre la API real (HT-11 y HU-33)

   Como administracion.ts, va SOLO contra el servidor (sin simulación): ampliar un
   plazo, dar de baja un proveedor, ajustar el requerimiento o comparar un precio no tienen sentido con datos inventados.
   Nada se edita ni se borra: cada cambio deja su rastro (quién, cuándo, motivo),
   y el rol y la obra los verifica el servidor (regla 9).
   ───────────────────────────────────────────────────────────────────────── */
import { num, obraId, pedir, sesionActual } from './http'

/* ── HU-02 · T-331: ampliar el plazo de la obra; las semanas nuevas las genera el servidor ── */
export interface CambioPlazo { fechaFinAnterior: string; fechaFinNueva: string; motivo: string; autor: string; fecha: string }
export const plazo = {
  async ampliar(obra: string, nuevaFechaFin: string, motivo: string): Promise<void> {
    await pedir('POST', `/obras/${obra}/ampliar-plazo`, { nuevaFechaFin, motivo: motivo.trim() })
  },
  async historial(obra: string): Promise<CambioPlazo[]> {
    return pedir<CambioPlazo[]>('GET', `/obras/${obra}/plazo-historial`)
  },
}

/* ── HU-29 · T-344: la baja es un estado con fecha; el proveedor deja de salir en compras nuevas ── */
export const proveedores = {
  async darDeBaja(id: string): Promise<void> {
    await pedir('POST', `/proveedores/${id}/baja`)
  },
}

/* ── HU-19 · T-339: ajustar lo planificado deja el antes, el después, el motivo y quién ── */
export interface LineaRequerimiento {
  id: number; codigo: string; descripcion: string; unidad: string; cantidad: number; precio: number
  manual: boolean            // T-340: agregado a mano (no vino del S10)
  motivoAlta: string | null
}
export interface Ajuste { cantidadAnterior: number; cantidadNueva: number; motivo: string; ajustadoPor: number; ajustadoEn: string }
interface RequerimientoApi {
  id: number; insumoCodigo: string; descripcion: string; unidadS10: string; cantidadPlanificada: string; precioPlanificado: string
  origen?: 's10' | 'manual'; motivoAlta?: string | null
}
const linea = (r: RequerimientoApi): LineaRequerimiento => ({
  id: r.id, codigo: r.insumoCodigo, descripcion: r.descripcion, unidad: r.unidadS10,
  cantidad: num(r.cantidadPlanificada), precio: num(r.precioPlanificado),
  manual: r.origen === 'manual', motivoAlta: r.motivoAlta ?? null,
})
interface AjusteApi { cantidadAnterior: string; cantidadNueva: string; motivo: string; ajustadoPor: number; ajustadoEn: string }
export const requerimiento = {
  async listar(): Promise<LineaRequerimiento[]> {
    const xs = await pedir<RequerimientoApi[]>('GET', `/obras/${await obraId()}/requerimiento`)
    return xs.map(linea)
  },
  /** T-340: un insumo que el S10 no trae. Debe estar en el catálogo; si ya está planificado, el servidor responde 409. */
  async agregar(codigo: string, cantidad: number, precio: number, motivo: string): Promise<LineaRequerimiento> {
    return linea(await pedir<RequerimientoApi>('POST', `/obras/${await obraId()}/requerimiento`,
      { insumoCodigo: codigo.trim(), cantidad, precio, motivo: motivo.trim() }))
  },
  async ajustar(id: number, cantidad: number, motivo: string): Promise<void> {
    await pedir('POST', `/obras/${await obraId()}/requerimiento/${id}/ajustar`, { cantidad, motivo: motivo.trim() })
  },
  async ajustes(id: number): Promise<Ajuste[]> {
    const xs = await pedir<AjusteApi[]>('GET', `/obras/${await obraId()}/requerimiento/${id}/ajustes`)
    return xs.map((a) => ({ ...a, cantidadAnterior: num(a.cantidadAnterior), cantidadNueva: num(a.cantidadNueva) }))
  },
}

/* ── HU-33 · T-282/T-283: el promedio ponderado pagado en ESTA obra, sin IGV, para comparar el precio ── */
export interface PrecioReferencia {
  promedio: number | null    // por unidad de compra, sin IGV; null si aún no se recibió nada del insumo
  unidadCompra: string
  limitePct: number          // el servidor marca la línea si se aparta más que esto (hoy 10 %)
}
interface PrecioApi { insumoCodigo: string; unidadCompra: string; promedioPonderado: string | null; limitePct: string }
export const precios = {
  async referencia(codigo: string): Promise<PrecioReferencia> {
    const p = await pedir<PrecioApi>('GET', `/obras/${await obraId()}/compras/precio-referencia?insumo=${encodeURIComponent(codigo)}`)
    return { promedio: p.promedioPonderado === null ? null : num(p.promedioPonderado), unidadCompra: p.unidadCompra, limitePct: num(p.limitePct) }
  },
}

/** Cuánto se aparta un precio CON IGV (como lo escribe la persona) del promedio SIN IGV. null si no hay con qué comparar. */
export function desvioPct(precioConIgv: number, promedioSinIgv: number | null, factorSinIgv = 1 / 1.18): number | null {
  if (promedioSinIgv === null || promedioSinIgv <= 0 || !(precioConIgv > 0)) return null
  return Math.round(((precioConIgv * factorSinIgv) / promedioSinIgv - 1) * 1000) / 10
}

/* ── T-345: nombre de quien registró cada movimiento ──
   Los nombres los tiene obras; el residente y el administrador pueden ver las asignaciones de la obra. El
   almacenero no: para él se muestra su propio nombre y, en lo ajeno, «Usuario N» (no se inventa un nombre). */
let nombres: Map<number, string> | null = null
export async function nombresDeLaObra(): Promise<Map<number, string>> {
  if (nombres) return nombres
  const m = new Map<number, string>()
  const yo = sesionActual()?.usuario
  if (yo) m.set(yo.id, yo.nombre)
  try {
    const xs = await pedir<{ usuarioId: number; usuarioNombre: string }[]>('GET', `/obras/${await obraId()}/asignaciones`)
    for (const a of xs) m.set(a.usuarioId, a.usuarioNombre)
    nombres = m
  } catch { /* sin permiso o sin red: quedan el propio nombre y «Usuario N» */ }
  return m
}
export function olvidarNombres() { nombres = null }
