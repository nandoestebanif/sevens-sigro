// @vitest-environment jsdom
import { act, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Campo } from '../ui/base'
import { PAUSA_MS, useEnVivo } from './enVivo'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

function Formulario() {
  const [cantidad, setCantidad] = useState('')
  const n = Number(cantidad.replace(',', '.'))
  const v = useEnVivo({ cantidad: !cantidad.trim() ? 'Escribe la cantidad.' : !(n > 0) ? 'Debe ser mayor que cero.' : null })
  return <Campo id="cant" etiqueta="Cantidad" valor={cantidad} enCambio={(x) => { setCantidad(x); v.cambio('cantidad') }} {...v.campo('cantidad')} />
}

describe('useEnVivo con Campo', () => {
  let caja: HTMLDivElement
  beforeEach(() => { vi.useFakeTimers(); caja = document.createElement('div'); document.body.appendChild(caja) })
  afterEach(() => { vi.useRealTimers(); caja.remove() })

  const escribir = (valor: string) => {
    const input = caja.querySelector('input')!
    const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!
    act(() => { set.call(input, valor); input.dispatchEvent(new Event('input', { bubbles: true })) })
  }

  it('marca en rojo tras la pausa y pone ✓ al corregir, sin esperar', () => {
    act(() => { createRoot(caja).render(<Formulario />) })
    escribir('0')
    expect(caja.querySelector('.is-invalid')).toBeNull()
    act(() => { vi.advanceTimersByTime(PAUSA_MS + 10) })
    expect(caja.querySelector('.is-invalid')).not.toBeNull()
    expect(caja.textContent).toContain('Debe ser mayor que cero.')
    escribir('0,5')
    expect(caja.querySelector('.is-invalid')).toBeNull()
    expect(caja.querySelector('.is-valid')).not.toBeNull()
  })

  it('al salir del campo se ve el error sin pausa', () => {
    act(() => { createRoot(caja).render(<Formulario />) })
    const input = caja.querySelector('input')!
    act(() => { input.focus(); input.blur() })
    expect(caja.textContent).toContain('Escribe la cantidad.')
  })
})

describe('un campo opcional vacío no lleva ✓ (27/11)', () => {
  it('Campo con valido pero sin texto no se pinta como correcto', () => {
    const c = document.createElement('div'); document.body.appendChild(c)
    const r = createRoot(c)
    act(() => r.render(<Campo id="correo" etiqueta="Correo (opcional)" valor="" enCambio={() => {}} valido />))
    expect(c.querySelector('input')!.classList.contains('is-valid')).toBe(false)
    act(() => r.render(<Campo id="correo" etiqueta="Correo (opcional)" valor="a@b.pe" enCambio={() => {}} valido />))
    expect(c.querySelector('input')!.classList.contains('is-valid')).toBe(true)
    act(() => r.unmount()); c.remove()
  })
})
