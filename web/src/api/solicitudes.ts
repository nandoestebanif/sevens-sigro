/* SIGRO · solicitudes de materiales (CR-04): el residente pide, el administrador compra.
   Solo contra el servidor: el número correlativo lo asigna la base, sin huecos. Nada se borra: se anula con motivo. */
import { num, obraId, pedir } from './http'

export type EstadoSolicitud = 'solicitada' | 'comprada_en_parte' | 'comprada' | 'anulada'
export interface LineaSolicitud { codigo: string | null; descripcion: string; unidad: string; solicitada: number; comprada: number; pendiente: number }
export interface Solicitud { id: number; numero: number; fecha: string; estado: EstadoSolicitud; motivoAnulacion: string | null; lineas: LineaSolicitud[] }
export interface LineaNueva { insumoCodigo?: string; descripcionLibre?: string; cantidad: number; unidadCompra: string }

interface SolicitudApi {
  id: number; numero: number; fecha: string; estado: EstadoSolicitud; motivoAnulacion: string | null
  lineas: { insumoCodigo: string | null; descripcion: string; unidadCompra: string; cantidadSolicitada: string; cantidadComprada: string; cantidadPendiente: string }[]
}
const aSolicitud = (s: SolicitudApi): Solicitud => ({
  id: s.id, numero: s.numero, fecha: s.fecha, estado: s.estado, motivoAnulacion: s.motivoAnulacion,
  lineas: s.lineas.map((l) => ({ codigo: l.insumoCodigo, descripcion: l.descripcion, unidad: l.unidadCompra,
    solicitada: num(l.cantidadSolicitada), comprada: num(l.cantidadComprada), pendiente: num(l.cantidadPendiente) })),
})

export const solicitudes = {
  async listar(): Promise<Solicitud[]> {
    return (await pedir<SolicitudApi[]>('GET', `/obras/${await obraId()}/solicitudes`)).map(aSolicitud)
  },
  async emitir(lineas: LineaNueva[]): Promise<Solicitud> {
    return aSolicitud(await pedir<SolicitudApi>('POST', `/obras/${await obraId()}/solicitudes`, { lineas }))
  },
  async anular(id: number, motivo: string): Promise<Solicitud> {
    return aSolicitud(await pedir<SolicitudApi>('POST', `/obras/${await obraId()}/solicitudes/${id}/anular`, { motivo: motivo.trim() }))
  },
}

export const ESTADO_SOLICITUD: Record<EstadoSolicitud, { texto: string; tono: 'aviso' | 'info' | 'bien' | 'neutro' }> = {
  solicitada: { texto: 'Por comprar', tono: 'aviso' },
  comprada_en_parte: { texto: 'Comprada en parte', tono: 'info' },
  comprada: { texto: 'Comprada', tono: 'bien' },
  anulada: { texto: 'Anulada', tono: 'neutro' },
}

/** Reglas puras del formulario (se prueban sin navegador). */
export function erroresSolicitud(lineas: LineaNueva[]): string | null {
  if (!lineas.length) return 'Agrega al menos un insumo.'
  if (lineas.some((l) => !(l.cantidad > 0))) return 'Cada línea necesita una cantidad mayor que cero.'
  const cods = lineas.map((l) => l.insumoCodigo).filter(Boolean)
  if (new Set(cods).size !== cods.length) return 'Un insumo aparece dos veces: suma las cantidades en una sola línea.'
  return null
}
