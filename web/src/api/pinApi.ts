/* SIGRO · llamadas del PIN y de la bitácora (el token vive en ./pin, sin depender de http). */
import { obraId, pedir } from './http'
import { guardarPin, type FilaBitacora } from './pin'

export const pin = {
  estado: () => pedir<{ configurado: boolean }>('GET', '/auth/pin'),
  async crear(clave: string, nuevo: string) {
    const r = await pedir<{ configurado: boolean; pinToken: string }>('POST', '/auth/pin', { clave, pin: nuevo })
    guardarPin(r.pinToken)
  },
  async verificar(valor: string) {
    const r = await pedir<{ pinToken: string; venceEnSegundos: number }>('POST', '/auth/pin/verificar', { pin: valor })
    guardarPin(r.pinToken, r.venceEnSegundos)
  },
}


export async function bitacora(soloRechazos = false): Promise<FilaBitacora[]> {
  return pedir<FilaBitacora[]>('GET', `/obras/${await obraId()}/bitacora?limite=300${soloRechazos ? '&solo=rechazos' : ''}`)
}
