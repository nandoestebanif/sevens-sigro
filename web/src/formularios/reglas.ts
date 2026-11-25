/* SIGRO · reglas de campos sueltos que se repiten en varios formularios (validación en vivo).
   Devuelven el mensaje si está mal o `null` si está bien. En obra se escribe con coma: se acepta «12,5». */

const NUMERO = /^\d{1,12}([.,]\d{1,6})?$/

/** «12,5» → 12.5; NaN si no es un número. */
export const aNumero = (texto: string) => (NUMERO.test(texto.trim()) ? Number(texto.trim().replace(',', '.')) : NaN)

/** Cantidad obligatoria y mayor que cero. */
export function cantidadPositiva(texto: string, vacio = 'Escribe la cantidad.'): string | null {
  const t = texto.trim()
  if (!t) return vacio
  if (!NUMERO.test(t)) return 'Solo números, p. ej. 12,5.'
  return aNumero(t) > 0 ? null : 'Debe ser mayor que cero.'
}

/** Importe opcional que no puede ser negativo (vacío = 0). */
export function importeNoNegativo(texto: string): string | null {
  const t = texto.trim()
  if (!t) return null
  if (t.startsWith('-')) return 'No puede ser negativo.'
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
