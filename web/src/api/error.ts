/** Error de negocio con el contrato de la API: {codigo, mensaje}. Lo usan el
    cliente simulado y el HTTP por igual, así las pantallas no distinguen.
    `estado` es el código HTTP cuando vino del servidor (lo usa el reintento del login). */
export class ErrorApi extends Error {
  constructor(public codigo: string, mensaje: string, public estado?: number) {
    super(mensaje)
  }
}

/** T-355 · una pantalla que no carga lo dice en palabras del usuario, nunca se queda en «Cargando…».
    El 403 lo decide el servidor (regla 9): la pantalla solo explica qué pasó y qué hacer. */
export function mensajeDeCarga(x: unknown): string {
  if (x instanceof ErrorApi) {
    if (x.estado === 403) return 'No tienes permiso para ver esta información en esta obra. Si la necesitas, pídele al administrador que revise tu rol.'
    if (x.codigo === 'sin_conexion' || x.codigo === 'sesion_vencida') return x.message
  }
  return 'No se pudo cargar esta información. Inténtalo de nuevo en unos minutos.'
}
