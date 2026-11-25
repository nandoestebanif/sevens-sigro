/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · mano de obra para la oficina (HU-62 y HU-64), solo contra el servidor

   La asistencia se toma en el celular (sin señal, con su cola). Desde el 25/11 la
   web también registra, corrige y da de baja trabajadores (el administrador, desde
   la oficina; el servidor verifica el rol). Las horas las calcula el servidor con
   la versión vigente de cada jornada.
   ───────────────────────────────────────────────────────────────────────── */
import { num, obraId, pedir } from './http'

export interface Trabajador {
  id: number; nombre: string; tipoDocumento: string; documento: string
  categoria: string; cuadrilla: string; fechaIngreso: string; fechaSalida: string | null
  /** desde la migración 0020; nulos en los registrados antes */
  nombres?: string | null; apellidoPaterno?: string | null; apellidoMaterno?: string | null
}

export type Categoria = 'operario' | 'oficial' | 'peon'
export interface NuevoTrabajador {
  nombres: string; apellidoPaterno: string; apellidoMaterno: string; tipoDocumento: 'DNI' | 'CE' | 'PASAPORTE'
  documento: string; categoria: Categoria; cuadrilla: string; fechaIngreso: string
}
export interface DniReniec { dni: string; nombres: string; apellidoPaterno: string; apellidoMaterno: string }

const cuerpoNombre = (d: { nombres: string; apellidoPaterno: string; apellidoMaterno: string }) => ({
  nombres: d.nombres.trim(), apellido_paterno: d.apellidoPaterno.trim(), apellido_materno: d.apellidoMaterno.trim() || null,
})

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
  async registrar(d: NuevoTrabajador): Promise<Trabajador> {
    return pedir<Trabajador>('POST', `/obras/${await obraId()}/trabajadores`, {
      ...cuerpoNombre(d), tipo_documento: d.tipoDocumento, documento: d.documento.trim(), categoria: d.categoria,
      cuadrilla: d.cuadrilla.trim(), fecha_ingreso: d.fechaIngreso,
    })
  },
  /** Documento y fecha de ingreso no se corrigen (los protege la base). */
  async corregir(id: number, d: { nombres: string; apellidoPaterno: string; apellidoMaterno: string; categoria: Categoria; cuadrilla: string }): Promise<Trabajador> {
    return pedir<Trabajador>('POST', `/obras/${await obraId()}/trabajadores/${id}/datos`, {
      ...cuerpoNombre(d), categoria: d.categoria, cuadrilla: d.cuadrilla.trim(),
    })
  },
  async darDeBaja(id: number, fechaSalida: string): Promise<Trabajador> {
    return pedir<Trabajador>('POST', `/obras/${await obraId()}/trabajadores/${id}/baja`, { fecha_salida: fechaSalida })
  },
  /** RENIEC vía integraciones; 404 «reniec_sin_respuesta» si no respondió (se escribe a mano). */
  async consultarDni(dni: string): Promise<DniReniec> {
    return pedir<DniReniec>('GET', `/obras/${await obraId()}/trabajadores/consulta-dni/${encodeURIComponent(dni.trim())}`)
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
