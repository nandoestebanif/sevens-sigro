/** Error de negocio con el contrato de la API: {codigo, mensaje}. Lo usan el
    cliente simulado y el HTTP por igual, así las pantallas no distinguen. */
export class ErrorApi extends Error {
  constructor(public codigo: string, mensaje: string) {
    super(mensaje)
  }
}
