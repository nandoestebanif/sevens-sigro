/* HU-25 · reglas del formulario «Definir parámetros», sin React (se prueban con Vitest).
   El servidor también valida (punto ≥ stock de seguridad, hasta 4 decimales); aquí se avisa antes,
   bajo cada campo, con un mensaje que se entienda. */

const NUMERO = /^\d{1,14}([.,]\d{1,4})?$/

/** «12,5» → 12.5 · texto inválido → NaN. */
export function leerCantidad(texto: string): number {
  const t = texto.trim()
  return NUMERO.test(t) ? Number(t.replace(',', '.')) : NaN
}

export interface ErroresParametros { insumo?: string; seguridad?: string; punto?: string }

export function validarParametros(d: { insumo: string; seguridad: string; punto: string; unidad?: string }): ErroresParametros {
  const e: ErroresParametros = {}
  const u = d.unidad ? ` ${d.unidad}` : ''
  if (!d.insumo) e.insumo = 'Elige el insumo: escribe parte de su código o de su nombre.'
  const campo = (texto: string, nombre: string): [number, string | undefined] => {
    if (!texto.trim()) return [NaN, `Escribe el ${nombre}.`]
    const n = leerCantidad(texto)
    if (Number.isNaN(n)) return [NaN, 'Solo números, con hasta 4 decimales (por ejemplo 12,5).']
    if (n <= 0) return [n, 'Debe ser mayor que 0.']
    return [n, undefined]
  }
  const [s, es] = campo(d.seguridad, 'stock de seguridad')
  const [p, ep] = campo(d.punto, 'punto de reposición')
  if (es) e.seguridad = es
  if (ep) e.punto = ep
  if (!es && !ep && p < s) {
    e.punto = `El punto de reposición (${p}${u}) no puede ser menor que el stock de seguridad (${s}${u}): el aviso llegaría tarde.`
  }
  return e
}
