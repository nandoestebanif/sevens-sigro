/* HU-08 · Catálogo de insumos: código único (regla 8), búsqueda y alta «por verificar». */
import { expect, test } from '@playwright/test'
import { MOTIVO_SIN_CREDENCIALES, SIN_CREDENCIALES, entrar, irA } from './ayuda'

test.describe('HU-08 · Catálogo de insumos', () => {
  test.skip(SIN_CREDENCIALES, MOTIVO_SIN_CREDENCIALES)

  test('encuentra por código los insumos importados del S10 (cemento y acero fy=4200 en kg)', async ({ page }) => {
    await entrar(page)
    await irA(page, 'Catálogo de insumos')
    const buscar = page.getByLabel('Buscar insumo por nombre o código')
    await buscar.fill('0221000000')
    await expect(page.getByRole('row').filter({ hasText: 'CEMENTO PORTLAND TIPO I' })).toHaveCount(1)
    await buscar.fill('0203020004')
    const acero = page.getByRole('row').filter({ hasText: 'ACERO CORRUGADO' })
    await expect(acero).toHaveCount(1)
    await expect(acero).toContainText(/kg/i)
  })

  test('agregar un insumo: el código lo pone el servidor y un nombre repetido se rechaza', async ({ page }) => {
    await entrar(page)
    await irA(page, 'Catálogo de insumos')
    const nombre = `[QA] Insumo e2e ${Date.now()}`
    const dialogo = page.getByRole('dialog', { name: 'Agregar insumo al catálogo' })
    const guardar = dialogo.getByRole('button', { name: /^(Agregar al catálogo|Guardar y verificar)$/ })

    await page.getByRole('button', { name: 'Agregar insumo' }).click()
    await dialogo.getByLabel('Material').fill(nombre)
    await guardar.click()
    await expect(dialogo).toBeHidden()
    await page.getByLabel('Buscar insumo por nombre o código').fill(nombre)
    await expect(page.getByRole('row').filter({ hasText: nombre })).toContainText(/SIGRO-\d+/)

    // mismo nombre y misma unidad: el servidor lo rechaza señalando el que ya existe
    await page.getByRole('button', { name: 'Agregar insumo' }).click()
    await dialogo.getByLabel('Material').fill(nombre)
    await guardar.click()
    await expect(dialogo).toBeVisible()
    await expect(dialogo).toContainText(/ya existe|SIGRO-\d+/i)
  })
})
