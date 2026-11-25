/* ─────────────────────────────────────────────────────────────────────────────
   Consulta de RUC contra el microservicio de integraciones (HU-31).
   La web NUNCA llama a Factiliza directo: el token vive solo en el micro.
   Si el micro no está corriendo o SUNAT no responde, devolvemos null y el
   proveedor se registra «sin validar» — una compra no se bloquea porque un
   servicio externo esté caído (regla acordada con el cliente).
   ───────────────────────────────────────────────────────────────────────── */

export interface RucSunat {
  ruc: string
  razon_social: string
  estado_sunat: string
  condicion: string
  direccion: string | null
  apto_para_comprar: boolean
}

import { pedir } from './http'
import { esquemaProveedor, primerError } from '../formularios/esquemas'

/** Valida el dígito verificador oficial SUNAT (módulo 11) de un RUC de 11 dígitos. */
export function rucValido(ruc: string): boolean {
  if (!/^(10|15|16|17|20)\d{9}$/.test(ruc)) return false
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2]
  let suma = 0
  for (let i = 0; i < 10; i++) suma += Number(ruc[i]) * pesos[i]
  const resto = 11 - (suma % 11)
  const digito = resto === 10 ? 0 : resto === 11 ? 1 : resto
  return digito === Number(ruc[10])
}

/**
 * La razón social se pide al servidor (GET /proveedores/consulta-ruc/{ruc}, por el gateway), que la consulta en
 * el microservicio de integraciones. Antes se llamaba a localhost:8001 desde el navegador y en el ambiente de
 * pruebas nunca respondía (hallazgo de la prueba de aceptación del 18/11).
 */
export async function consultarRucEnSunat(ruc: string): Promise<RucSunat | null> {
  if (!rucValido(ruc)) return null
  try {
    // `pedir` entrega las claves en camelCase (razonSocial…): aquí se vuelven a la forma que usa la pantalla.
    const r = await pedir<{ ruc: string; razonSocial: string; estadoSunat: string; condicion: string; direccion: string | null; aptoParaComprar: boolean }>(
      'GET', `/proveedores/consulta-ruc/${ruc}`)
    return deServidor(r)
  } catch {
    return null // SUNAT o el micro sin respuesta: se escribe a mano y queda «por validar»
  }
}

/** Mensaje en palabras del usuario antes de ir al servidor (antes salía el texto técnico de la API). */
export function errorAltaProveedor(ruc: string, razon: string): { campo: 'ruc' | 'razon'; texto: string } | null {
  // La regla vive en el esquema de Zod (formularios/esquemas.ts); aquí solo se traduce el nombre del campo.
  const e = primerError(esquemaProveedor, { ruc, razonSocial: razon })
  return e ? { campo: e.campo === 'razonSocial' ? 'razon' : 'ruc', texto: e.texto } : null
}

/** Respuesta del servidor (camelCase, tras `pedir`) → RucSunat. Sin razón social no se autocompleta nada. */
export function deServidor(r: { ruc: string; razonSocial?: string; estadoSunat?: string; condicion?: string; direccion?: string | null; aptoParaComprar?: boolean } | null): RucSunat | null {
  if (!r || !r.razonSocial) return null
  return {
    ruc: r.ruc, razon_social: r.razonSocial, estado_sunat: r.estadoSunat ?? '', condicion: r.condicion ?? '',
    direccion: r.direccion ?? null, apto_para_comprar: !!r.aptoParaComprar,
  }
}
