/** Error de negocio con el contrato de la API: {codigo, mensaje}. Lo usan el
    cliente simulado y el HTTP por igual, así las pantallas no distinguen.
    `estado` es el código HTTP cuando vino del servidor (lo usa el reintento del login). */
export class ErrorApi extends Error {
  constructor(public codigo: string, mensaje: string, public estado?: number) {
    super(mensaje)
  }
}
