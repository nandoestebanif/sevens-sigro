/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · mano de obra para la oficina (HU-62 y HU-64), solo contra el servidor

   La web solo consulta: registrar, dar de baja y tomar asistencia los hacen el
   almacenero y el SSOMA desde el celular (sin señal, con su cola). Las horas las
   calcula el servidor con la versión vigente de cada jornada.
   ───────────────────────────────────────────────────────────────────────── */
import { num, obraId, pedir } from './http'

export interface Trabajador {
  id: number; nombre: string; tipoDocumento: string; documento: string
  categoria: string; cuadrilla: string; fechaIngreso: string; fechaSalida: string | null
}

export interface HorasFila {
  periodo: number; trabajadorId: number; trabajador: string; cuadrilla: string; categoria: string
  diasPresente: number; diasAusente: number; diasPermiso: number
  horasNormales: number; horasExtra: number; ultimaJornada: string | null
}

interface HorasApi {
  periodoNumero: number; trabajadorId: number; trabajador: string; cuadrilla: string; categoria: string
  diasPresente: number; diasAusente: number; diasPermiso: number
  horasNormales: string; horasExtra: string; ultimaJornada?: string | null
}

export const manoDeObra = {
  async trabajadores(): Promise<Trabajador[]> {
    return pedir<Trabajador[]>('GET', `/obras/${await obraId()}/trabajadores`)
  },
  async horas(): Promise<HorasFila[]> {
    const xs = await pedir<HorasApi[]>('GET', `/obras/${await obraId()}/horas-acumuladas`)
    return xs.map((h) => ({
      periodo: h.periodoNumero, trabajadorId: h.trabajadorId, trabajador: h.trabajador, cuadrilla: h.cuadrilla,
      categoria: h.categoria, diasPresente: h.diasPresente, diasAusente: h.diasAusente, diasPermiso: h.diasPermiso,
      horasNormales: num(h.horasNormales), horasExtra: num(h.horasExtra), ultimaJornada: h.ultimaJornada ?? null,
    }))
  },
}
