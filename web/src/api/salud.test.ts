/* La espera del arranque: cada 2 s, aviso a los 5 s, «No pudimos conectar» a los 90 s. Reloj y consulta simulados. */
import { describe, expect, it } from 'vitest'
import { AVISO_MS, CADA_MS, LIMITE_MS, esperarServidor, type EstadoArranque, type Salud } from './salud'

const salud = (listos: number, total = 5): Salud => ({
  listo: listos === total,
  servicios: Array.from({ length: total }, (_, i) => ({ servicio: `s${i}`, listo: i < listos, estado: i < listos ? 'ok' : 'no_responde', ms: 0 })),
})

/** reloj que solo avanza cuando la espera «duerme» o la consulta tarda */
function mundo(respuestas: (Salud | null)[], tardaConsulta = 0) {
  let t = 0
  const consultas: number[] = []
  const estados: EstadoArranque[] = []
  const opciones = {
    ahora: () => t,
    consultar: async () => { consultas.push(t); t += tardaConsulta; return respuestas.length > 1 ? respuestas.shift()! : respuestas[0] },
    dormir: async (ms: number) => { t += ms },
    alCambiar: (e: EstadoArranque) => estados.push(e),
    senal: new AbortController().signal,
  }
  return { opciones, consultas, estados, reloj: () => t }
}

describe('esperarServidor', () => {
  it('pregunta cada 2 s y termina cuando todos los servicios están listos', async () => {
    const m = mundo([null, salud(2), salud(4), salud(5)])
    await expect(esperarServidor(m.opciones)).resolves.toBe(true)
    expect(m.consultas).toEqual([0, CADA_MS, 2 * CADA_MS, 3 * CADA_MS])
    expect(m.estados[m.estados.length - 1]).toEqual({ fase: 'listo', listos: 5, total: 5 })
    expect(m.estados.map((e) => e.listos)).toEqual([0, 2, 4, 5])   // la barra de progreso avanza con lo real
  })

  it('si ya están despiertos, una sola consulta basta', async () => {
    const m = mundo([salud(5)])
    await expect(esperarServidor(m.opciones)).resolves.toBe(true)
    expect(m.consultas).toEqual([0])
  })

  it('desde los 5 s la fase es «despertando»', async () => {
    const m = mundo([salud(1), salud(1), salud(1), salud(1), salud(5)])
    await esperarServidor(m.opciones)
    const fases = m.estados.map((e) => e.fase)
    expect(fases.slice(0, 3)).toEqual(['preparando', 'preparando', 'preparando'])   // 0, 2 y 4 s
    expect(fases[3]).toBe('despertando')                                           // 6 s ≥ 5 s
    expect(AVISO_MS).toBe(5000)
  })

  it('a los 90 s se rinde con «sin_conexion» y no sigue preguntando', async () => {
    const m = mundo([null])
    await expect(esperarServidor(m.opciones)).resolves.toBe(false)
    expect(m.estados[m.estados.length - 1]?.fase).toBe('sin_conexion')
    expect(m.reloj()).toBe(LIMITE_MS)
    expect(m.consultas.length).toBe(LIMITE_MS / CADA_MS + 1)
    expect(Math.max(...m.consultas)).toBe(LIMITE_MS)
  })

  it('las consultas lentas cuentan para el límite', async () => {
    const m = mundo([null], 10000)                                  // cada consulta tarda 10 s
    await esperarServidor(m.opciones)
    expect(m.reloj()).toBeLessThanOrEqual(LIMITE_MS + 10000)
    expect(m.estados[m.estados.length - 1]?.fase).toBe('sin_conexion')
  })

  it('se puede cancelar (al desmontar o al reintentar)', async () => {
    const corte = new AbortController()
    const m = mundo([null])
    let veces = 0
    const r = await esperarServidor({ ...m.opciones, senal: corte.signal, consultar: async () => { if (++veces === 3) corte.abort(); return null } })
    expect(r).toBe(false)
    expect(veces).toBe(3)
  })
})
