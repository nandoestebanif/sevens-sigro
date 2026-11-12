/* HU-24 · Saldo por comprar = requerido − comprado (necesita el requerimiento que carga 02-importar-s10). */
import { expect, test } from '@playwright/test'
import { MOTIVO_SIN_CREDENCIALES, SIN_CREDENCIALES, entrar, irA } from './ayuda'

test.describe('HU-24 · Saldo por comprar', () => {
  test.skip(SIN_CREDENCIALES, MOTIVO_SIN_CREDENCIALES)

  test('muestra lo requerido del S10 por insumo: cemento en bolsas y acero en kilos', async ({ page }) => {
    await entrar(page)
    await irA(page, 'Reposición')
    await expect(page.getByRole('heading', { name: 'Saldo por comprar' })).toBeVisible()
    await page.getByRole('button', { name: 'Todos', exact: true }).click()
    const cemento = page.getByRole('row').filter({ hasText: 'CEMENTO PORTLAND TIPO I' })
    await expect(cemento).toHaveCount(1)
    // 3 492.36 bls en el S10; la pantalla redondea a enteros. Sin compras en la obra: comprado 0 y falta todo
    await expect(cemento).toContainText(/3[\s.,]?492(?:[.,]\d+)? bls/)
    await expect(cemento).toContainText(/0 bls · 0 %/)
    const acero = page.getByRole('row').filter({ hasText: 'ACERO CORRUGADO' })
    await expect(acero).toContainText(/36[\s.,]?357(?:[.,]\d+)? kg/)      // 36 357.42 kg
  })
})
