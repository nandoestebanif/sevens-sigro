/* Reglas puras de la pantalla Mano de obra (HU-62 y HU-64): se prueban sin navegador.
   No calculan horas: solo agrupan lo que devolvió el servidor. */
import type { HorasFila, Trabajador } from '../api/manoDeObra'

export const CATEGORIAS: Record<string, string> = { operario: 'Operario', oficial: 'Oficial', peon: 'Peón' }
export const categoria = (c: string) => CATEGORIAS[c] ?? c

/** Activo en una fecha (AAAA-MM-DD): ya ingresó y no salió antes. Como en la app (regla 10: la baja es una fecha). */
export const activoEl = (t: Trabajador, hoy: string) => t.fechaIngreso <= hoy && (t.fechaSalida === null || t.fechaSalida >= hoy)

export interface FilaHoras { clave: string; nombre: string; detalle: string; dias: number; normales: number; extra: number; ultima: string | null }
export interface ResumenHoras { porTrabajador: FilaHoras[]; porCuadrilla: FilaHoras[]; normales: number; extra: number; ultima: string | null; periodos: number[] }

const max = (xs: (string | null)[]) => xs.reduce<string | null>((m, x) => (x && (!m || x > m) ? x : m), null)

/** HU-64 c2: por trabajador y por cuadrilla, de toda la obra (`periodo` nulo) o de una semana; de más a menos horas. */
export function resumirHoras(filas: HorasFila[], periodo: number | null): ResumenHoras {
  const elegidas = periodo === null ? filas : filas.filter((f) => f.periodo === periodo)
  const agrupar = (clave: (f: HorasFila) => string, nombre: (l: HorasFila[]) => string, detalle: (l: HorasFila[]) => string) => {
    const grupos = new Map<string, HorasFila[]>()
    for (const f of elegidas) grupos.set(clave(f), [...(grupos.get(clave(f)) ?? []), f])
    return [...grupos.entries()].map(([k, l]) => ({
      clave: k, nombre: nombre(l), detalle: detalle(l),
      dias: l.reduce((s, f) => s + f.diasPresente, 0),
      normales: l.reduce((s, f) => s + f.horasNormales, 0),
      extra: l.reduce((s, f) => s + f.horasExtra, 0),
      ultima: max(l.map((f) => f.ultimaJornada)),
    })).sort((a, b) => (b.normales + b.extra) - (a.normales + a.extra) || a.nombre.localeCompare(b.nombre, 'es'))
  }
  return {
    porTrabajador: agrupar((f) => String(f.trabajadorId), (l) => l[0].trabajador,
      (l) => `${l[0].cuadrilla} · ${[...new Set(l.map((f) => categoria(f.categoria)))].join(', ')}`),
    porCuadrilla: agrupar((f) => f.cuadrilla, (l) => l[0].cuadrilla, (l) => {
      const n = new Set(l.map((f) => f.trabajadorId)).size
      return `${n} ${n === 1 ? 'trabajador' : 'trabajadores'}`
    }),
    normales: elegidas.reduce((s, f) => s + f.horasNormales, 0),
    extra: elegidas.reduce((s, f) => s + f.horasExtra, 0),
    ultima: max(elegidas.map((f) => f.ultimaJornada)),
    periodos: [...new Set(filas.map((f) => f.periodo))].sort((a, b) => a - b),
  }
}

export const horas = (h: number) => `${h.toLocaleString('es-PE', { maximumFractionDigits: 1 })} h`

/* ── registrar, corregir y dar de baja desde la web (25/11) · las mismas reglas que el servidor ── */
export interface DatosTrabajador {
  nombres: string; apellidoPaterno: string; apellidoMaterno: string
  tipoDocumento: 'DNI' | 'CE' | 'PASAPORTE'; documento: string; categoria: string; cuadrilla: string; fechaIngreso: string
}

/** Errores por campo; `soloNombre` es para corregir (documento e ingreso no se tocan). */
export function validarTrabajador(d: DatosTrabajador, soloNombre = false): Partial<Record<keyof DatosTrabajador, string>> {
  const e: Partial<Record<keyof DatosTrabajador, string>> = {}
  if (d.nombres.trim().length < 2) e.nombres = 'Escribe los nombres.'
  if (!d.apellidoPaterno.trim()) e.apellidoPaterno = 'Escribe al menos el apellido paterno.'
  if (!CATEGORIAS[d.categoria]) e.categoria = 'Elige la categoría.'
  if (!d.cuadrilla.trim()) e.cuadrilla = 'Escribe la cuadrilla.'
  if (soloNombre) return e
  const doc = d.documento.trim()
  if (d.tipoDocumento === 'DNI' ? !/^\d{8}$/.test(doc) : !/^[A-Za-z0-9]{6,20}$/.test(doc)) {
    e.documento = d.tipoDocumento === 'DNI' ? 'El DNI tiene 8 dígitos.' : 'Entre 6 y 20 letras o números.'
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(d.fechaIngreso)) e.fechaIngreso = 'Elige la fecha de ingreso.'
  return e
}

/** La baja: una fecha de salida no anterior al ingreso (lo demás —jornadas, plazo— lo verifica el servidor). */
export function errorBaja(t: Trabajador, fecha: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha)) return 'Elige la fecha de salida.'
  if (fecha < t.fechaIngreso) return 'La salida no puede ser antes del ingreso.'
  return null
}
