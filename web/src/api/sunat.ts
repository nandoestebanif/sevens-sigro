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

// En desarrollo el micro corre local; en producción esto sale de la configuración.
const URL_INTEGRACIONES = 'http://localhost:8001'

export async function consultarRucEnSunat(ruc: string): Promise<RucSunat | null> {
  if (!/^\d{11}$/.test(ruc)) return null
  try {
    const control = new AbortController()
    const temporizador = setTimeout(() => control.abort(), 6000)
    const r = await fetch(`${URL_INTEGRACIONES}/ruc/${ruc}`, { signal: control.signal })
    clearTimeout(temporizador)
    if (!r.ok) return null
    return (await r.json()) as RucSunat
  } catch {
    return null // micro apagado o sin red: se sigue sin validación
  }
}
