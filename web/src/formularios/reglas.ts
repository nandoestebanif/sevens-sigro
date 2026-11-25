/* SIGRO · reglas de campos sueltos que se repiten en varios formularios (validación en vivo).
   Devuelven el mensaje si está mal o `null` si está bien. En obra se escribe con coma: se acepta «12,5». */

const NUMERO = /^\d{1,14}([.,]\d{1,6})?$/

/** Topes del servidor (Contratos del backend): NUMERIC(18,4) para cantidades y precios; NUMERIC(18,6) para factores. */
export interface Limite { enteros: number; decimales: number }
export const LIMITE_CANTIDAD: Limite = { enteros: 14, decimales: 4 }
export const LIMITE_FACTOR: Limite = { enteros: 12, decimales: 6 }
/** Largos de texto del servidor: motivos y notas 300. */
export const MAX_MOTIVO = 300

/**
 * Filtro en tiempo real: lo que se escribe entra solo si es un número parcial válido que no pasa del tope
 * (una coma o punto, sin más enteros ni decimales de los que acepta el servidor). Si no, queda el valor anterior.
 * Los espacios se quitan: «1 000» pegado queda «1000».
 */
export function filtrarNumero(anterior: string, nuevo: string, lim: Limite = LIMITE_CANTIDAD): string {
  const t = nuevo.replace(/[\s\u00A0]/g, '')
  if (t.length < anterior.length) return t // borrar siempre se puede (aunque el valor previo viniera largo)
  return new RegExp(`^\\d{0,${lim.enteros}}([.,]\\d{0,${lim.decimales}})?$`).test(t) ? t : anterior
}

/** Mensaje si el número se pasa de cifras enteras o decimales; null si cabe. */
export function excesoNumero(texto: string, lim: Limite = LIMITE_CANTIDAD): string | null {
  const [ent = '', dec = ''] = texto.trim().replace(',', '.').split('.')
  if (ent.length > lim.enteros) return `Máximo ${lim.enteros} cifras enteras.`
  if (dec.length > lim.decimales) return `Máximo ${lim.decimales} decimales.`
  return null
}

/** «12,5» → 12.5; NaN si no es un número. */
export const aNumero = (texto: string) => (NUMERO.test(texto.trim()) ? Number(texto.trim().replace(',', '.')) : NaN)

/** Cantidad obligatoria y mayor que cero. */
export function cantidadPositiva(texto: string, vacio = 'Escribe la cantidad.', lim: Limite = LIMITE_CANTIDAD): string | null {
  const t = texto.trim()
  if (!t) return vacio
  if (/^\d+([.,]\d+)?$/.test(t) && excesoNumero(t, lim)) return excesoNumero(t, lim)
  if (!NUMERO.test(t)) return 'Solo números, p. ej. 12,5.'
  return aNumero(t) > 0 ? null : 'Debe ser mayor que cero.'
}

/** Importe opcional que no puede ser negativo (vacío = 0). */
export function importeNoNegativo(texto: string): string | null {
  const t = texto.trim()
  if (!t) return null
  if (t.startsWith('-')) return 'No puede ser negativo.'
  if (/^\d+([.,]\d+)?$/.test(t) && excesoNumero(t)) return excesoNumero(t)
  return NUMERO.test(t) ? null : 'Solo números, p. ej. 4,50.'
}

/**
 * La misma regla que el servidor (api/app/seguridad.py · clave_aceptable): 10 caracteres o más y mezcla de
 * mayúsculas, minúsculas y al menos un número. Así el aviso en vivo dice lo mismo que dirá el servidor.
 */
export function claveAceptable(clave: string): string | null {
  if (clave.length < 10) return 'La contraseña debe tener al menos 10 caracteres.'
  if (clave.toLowerCase() === clave || clave.toUpperCase() === clave || !/\d/.test(clave)) {
    return 'La contraseña debe mezclar mayúsculas, minúsculas y al menos un número.'
  }
  return null
}

/** Texto con un mínimo de letras (motivos, nombres). */
export function minimo(texto: string, letras: number, mensaje: string): string | null {
  return texto.trim().length >= letras ? null : mensaje
}
