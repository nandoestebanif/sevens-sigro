/* HU-29 · buscar proveedores · HU-30 · registrar (RUC o DNI, D-C5) · HU-31 · validar en SUNAT. */
import { expect, test } from '@playwright/test'
import { MOTIVO_SIN_CREDENCIALES, SIN_CREDENCIALES, entrar, irA } from './ayuda'

const dniAlAzar = () => String(10_000_000 + Math.floor(Math.random() * 89_999_999))

test.describe('HU-29 · HU-30 · HU-31 · Proveedores', () => {
  test.skip(SIN_CREDENCIALES, MOTIVO_SIN_CREDENCIALES)

  test('registrar un proveedor con DNI: queda «sin validar» y se encuentra por documento', async ({ page }) => {
    await entrar(page)
    await irA(page, 'Proveedores')
    const dni = dniAlAzar()
    const razon = `[QA] Proveedor e2e ${dni}`
    await page.getByRole('button', { name: 'Registrar proveedor' }).click()
    const dialogo = page.getByRole('dialog', { name: 'Registrar proveedor' })
    await dialogo.getByLabel('RUC o DNI').fill(dni)
    await dialogo.getByLabel('Razón social').fill(razon)
    await dialogo.getByRole('button', { name: 'Guardar proveedor' }).click()
    await expect(dialogo).toBeHidden()

    await page.getByLabel('Buscar proveedor por razón social o RUC').fill(dni)
    const fila = page.getByRole('row').filter({ hasText: razon })
    await expect(fila).toHaveCount(1)
    await expect(fila).toContainText('Sin validar')
  })

  test('un documento repetido se rechaza (RUC único en todo el sistema)', async ({ page }) => {
    await entrar(page)
    await irA(page, 'Proveedores')
    const dni = dniAlAzar()
    const dialogo = page.getByRole('dialog', { name: 'Registrar proveedor' })
    for (const intento of [1, 2]) {
      await page.getByRole('button', { name: 'Registrar proveedor' }).click()
      await dialogo.getByLabel('RUC o DNI').fill(dni)
      await dialogo.getByLabel('Razón social').fill(`[QA] Repetido e2e ${dni} ${intento}`)
      await dialogo.getByRole('button', { name: 'Guardar proveedor' }).click()
      if (intento === 1) await expect(dialogo).toBeHidden()
      else await expect(dialogo).toContainText(/ya existe/i)
    }
  })

  test('con 11 dígitos consulta SUNAT y trae la razón social (HU-31)', async ({ page }) => {
    test.skip(!process.env.FACTILIZA_TOKEN,
      'Sin FACTILIZA_TOKEN: Integraciones no puede consultar SUNAT. El token nunca se escribe en el repositorio; ' +
      'para correrla, arranca Integraciones en :8001 con el token en su .env y define FACTILIZA_TOKEN en esta terminal.')
    await entrar(page)
    await irA(page, 'Proveedores')
    await page.getByRole('button', { name: 'Registrar proveedor' }).click()
    const dialogo = page.getByRole('dialog', { name: 'Registrar proveedor' })
    await dialogo.getByLabel('RUC o DNI').fill('20606456892')        // el RUC del propio cliente
    await expect(dialogo.getByText(/^SUNAT: /)).toBeVisible()
    await expect(dialogo.getByLabel('Razón social')).toHaveValue(/SEVEN/i)
  })
})
