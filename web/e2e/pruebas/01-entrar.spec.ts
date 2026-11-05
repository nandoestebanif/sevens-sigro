/* HU-12 · Entrar al sistema. */
import { expect, test } from '@playwright/test'
import { MOTIVO_SIN_CREDENCIALES, SIN_CREDENCIALES, USUARIO, entrar } from './ayuda'

test.describe('HU-12 · Entrar', () => {
  test.skip(SIN_CREDENCIALES, MOTIVO_SIN_CREDENCIALES)

  test('usuario inexistente recibe el mismo mensaje que una clave errónea', async ({ page }) => {
    await page.goto('/')
    await page.getByLabel('Usuario', { exact: true }).fill(`qa_no_existe_${Date.now()}`)
    await page.getByLabel('Contraseña', { exact: true }).fill('NoEsLaClave123')
    await page.getByRole('button', { name: 'Entrar', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('Usuario o contraseña incorrectos')
    await expect(page.getByRole('navigation', { name: 'Secciones' })).toHaveCount(0)
  })

  test('con usuario y clave válidos entra; la sesión vive en sessionStorage, nunca en localStorage', async ({ page }) => {
    await entrar(page)
    await expect(page.getByRole('complementary', { name: 'Menú principal' })).toContainText(/semana|aún no inicia/i)
    const enLocal = await page.evaluate(() => JSON.stringify(window.localStorage))
    expect(enLocal).not.toContain('token')
    const enSesion = await page.evaluate(() => window.sessionStorage.getItem('sigro-sesion') ?? '')
    expect(enSesion).toContain(USUARIO)
  })

  test('cerrar sesión vuelve a la pantalla de ingreso', async ({ page }) => {
    await entrar(page)
    await page.getByRole('button', { name: 'Cerrar sesión' }).click()
    await expect(page.getByRole('heading', { name: 'Ingresar' })).toBeVisible()
  })
})
