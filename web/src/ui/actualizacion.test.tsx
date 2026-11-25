// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CADA_MS, EnVivo, haceCuanto, nuevos, useActualizacion } from './Actualizacion'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

describe('haceCuanto y nuevos', () => {
  it('dice el tiempo en palabras', () => {
    expect(haceCuanto(2_000)).toBe('recién')
    expect(haceCuanto(12_000)).toBe('hace 12 s')
    expect(haceCuanto(125_000)).toBe('hace 2 min')
    expect(haceCuanto(2 * 3_600_000)).toBe('hace 2 h')
  })
  it('cuenta los movimientos que no estaban', () => {
    expect(nuevos(null, [{ id: 1 }])).toBe(0) // la primera carga no es «nuevo»
    expect(nuevos([{ id: 1 }], [{ id: 2 }, { id: 1 }, { id: 3 }])).toBe(2)
  })
})

describe('useActualizacion', () => {
  let caja: HTMLDivElement
  beforeEach(() => { vi.useFakeTimers(); caja = document.createElement('div'); document.body.appendChild(caja) })
  afterEach(() => { vi.useRealTimers(); caja.remove() })

  function Pantalla({ cargar }: { cargar: () => Promise<unknown> }) {
    const vivo = useActualizacion(cargar)
    return <EnVivo {...vivo} />
  }

  it('carga al abrir, cada 30 s y no apila pedidos si el servidor tarda', async () => {
    let resolver: () => void = () => {}
    const cargar = vi.fn(() => new Promise<void>((r) => { resolver = r }))
    act(() => { createRoot(caja).render(<Pantalla cargar={cargar} />) })
    expect(cargar).toHaveBeenCalledTimes(1)
    expect(caja.textContent).toContain('actualizando…')
    act(() => { vi.advanceTimersByTime(CADA_MS) })
    expect(cargar).toHaveBeenCalledTimes(1) // el primero sigue en curso: no se lanza otro
    await act(async () => { resolver() })
    expect(caja.textContent).toContain('En vivo · actualizado recién')
    act(() => { vi.advanceTimersByTime(CADA_MS) })
    expect(cargar).toHaveBeenCalledTimes(2)
  })

  it('el botón Actualizar consulta en el acto', async () => {
    const cargar = vi.fn(() => Promise.resolve())
    act(() => { createRoot(caja).render(<Pantalla cargar={cargar} />) })
    await act(async () => {})
    act(() => { caja.querySelector('button')!.click() })
    expect(cargar).toHaveBeenCalledTimes(2)
  })
})
