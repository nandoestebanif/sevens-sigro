/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · reglas de los formularios con Zod

   Un «esquema» de Zod describe cómo debe ser un dato (tipo, largo, formato) y el
   mensaje que se muestra si no cumple. React Hook Form lo usa con zodResolver:
   valida al enviar y luego en cada cambio, y deja los errores por campo.

   Ventaja: la regla vive en UN lugar, se prueba sin pantalla (esquemas.test.ts)
   y TypeScript deduce el tipo del formulario del propio esquema (z.infer).
   ───────────────────────────────────────────────────────────────────────── */
import { z } from 'zod'
import { rucValido } from '../api/sunat'

/** HU-12 · Entrar */
export const esquemaEntrar = z.object({
  usuario: z.string().trim().min(1, 'Escribe tu usuario.'),
  clave: z.string().min(1, 'Escribe tu contraseña.'),
})
export type DatosEntrar = z.infer<typeof esquemaEntrar>

/** HU-31 · Proveedor: RUC de 11 dígitos con dígito verificador SUNAT, o DNI de 8. */
export const esquemaProveedor = z.object({
  ruc: z.string().trim()
    .regex(/^(\d{11}|\d{8})$/, 'Escribe el RUC (11 dígitos) o el DNI (8 dígitos), solo números.')
    .refine((r) => r.length !== 11 || rucValido(r), 'El dígito verificador de este RUC no coincide con la fórmula SUNAT.'),
  razonSocial: z.string().trim().min(3, 'Escribe la razón social (o el nombre, si es un DNI). Si SUNAT no responde, va a mano.'),
})
export type DatosProveedor = z.infer<typeof esquemaProveedor>

const fecha = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Elige la fecha.')

/** HU-01 · Crear la obra: el fin va después del inicio (si no, la obra no tendría semanas). */
export const esquemaObra = z.object({
  nombre: z.string().trim().min(3, 'Escribe el nombre de la obra (al menos 3 letras).').max(120, 'Máximo 120 caracteres.'),
  presupuesto: z.string().trim().regex(/^(\d{7}(-\d{3})?)?$/, 'Formato del S10: 7 dígitos y, si aplica, el subpresupuesto (2701006-002).'),
  inicio: fecha,
  fin: fecha,
}).refine((o) => o.fin > o.inicio, { path: ['fin'], message: 'El fin debe ser posterior al inicio.' })
export type DatosObra = z.infer<typeof esquemaObra>

/** Primer error de un esquema, con el nombre del campo: para quien necesite un solo mensaje. */
export function primerError<T>(esquema: z.ZodType<T>, datos: unknown): { campo: string; texto: string } | null {
  const r = esquema.safeParse(datos)
  if (r.success) return null
  const e = r.error.issues[0]
  return { campo: String(e.path[0] ?? ''), texto: e.message }
}
