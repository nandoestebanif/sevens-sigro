// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { FilaBitacora } from '../../api/pin'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

const filas: FilaBitacora[] = Array.from({ length: 60 }, (_, i) => ({
  id: 1000 - i, momento: new Date(Date.UTC(2026, 10, 27, 6, 0, 59 - i)).toISOString(), usuarioId: 7, usuario: 'Persona de prueba',
  metodo: 'POST', ruta: `/api/v1/obras/1/almacen/salidas/${i}`, estado: 200, duracionMs: 10,
}))
vi.mock('../../api/pinApi', () => ({ bitacora: vi.fn(async () => filas) }))
const { Bitacora } = await import('./Bitacora')

let raiz: Root | null = null
let caja: HTMLDivElement
afterEach(() => { act(() => raiz?.unmount()); caja?.remove() })

describe('Bitácora con paginación (27/11)', () => {
  it('muestra 25 por página y se puede ir a la última', async () => {
    caja = document.createElement('div'); document.body.appendChild(caja)
    await act(async () => { raiz = createRoot(caja); raiz.render(<Bitacora />) })
    await act(async () => { await new Promise((r) => setTimeout(r, 0)) })
    expect(caja.textContent).toContain('Mostrando 1–25 de 60')
    expect(caja.querySelectorAll('tbody tr[role="button"]')).toHaveLength(25)
    const tres = [...caja.querySelectorAll('.pagination .page-link')].find((b) => b.textContent === '3') as HTMLElement
    await act(async () => { tres.click() })
    expect(caja.textContent).toContain('Mostrando 51–60 de 60')
    expect(caja.querySelectorAll('tbody tr[role="button"]')).toHaveLength(10)
  })
})
