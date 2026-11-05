/* HU-07 · Importar la Relación de insumos del S10 (PDF real, presupuesto 2701006 / subpresupuesto 002)
   y HU-19 · cargar el requerimiento de la obra. Repetir la importación no duplica. */
import { expect, test } from '@playwright/test'
import fs from 'node:fs'
import { MOTIVO_SIN_CREDENCIALES, PDF_S10, SIN_CREDENCIALES, entrar, irA } from './ayuda'

test.describe('HU-07 · HU-19 · Importar del S10', () => {
  test.skip(SIN_CREDENCIALES, MOTIVO_SIN_CREDENCIALES)
  test.skip(!fs.existsSync(PDF_S10), `No está el PDF real del cliente en ${PDF_S10}`)

  test('lee el PDF real (284 insumos, 0 fallidos, S/ 1 479 338.33), aplica al catálogo y carga el requerimiento', async ({ page }) => {
    await entrar(page)
    await irA(page, 'Importar del S10')
    await page.getByLabel('PDF de la Relación de insumos del S10').setInputFiles(PDF_S10)
    await page.getByRole('button', { name: 'Leer el archivo' }).click()

    await expect(page.getByText('284 filas leídas, 0 fallidas')).toBeVisible({ timeout: 90_000 })
    await expect(page.getByText(/1[\s.,]?479[\s.,]?338[.,]33/).first()).toBeVisible()
    // por tipo, como en el reporte del S10: 237 materiales, 6 mano de obra, 18 equipos, 23 subcontratos
    const pestanas = page.getByRole('tablist', { name: 'Tipo de insumo' })
    for (const n of ['237', '6', '18', '23']) await expect(pestanas.getByRole('tab', { name: new RegExp(`· ${n}$`) })).toBeVisible()

    await page.getByRole('button', { name: 'Confirmar e importar 284 insumos' }).click()
    const aplicado = page.getByRole('status').filter({ hasText: 'Catálogo actualizado.' })
    await expect(aplicado).toBeVisible()
    // primera vez: 284 creados; las siguientes: 0 creados (el código es la clave, no se duplica)
    const [, creados, actualizados, sinCambios] = ((await aplicado.textContent()) ?? '').match(/(\d+) creados · (\d+) actualizados · (\d+) sin cambios/) ?? []
    expect(Number(creados) + Number(actualizados) + Number(sinCambios)).toBe(284)

    await page.getByRole('button', { name: 'Cargar requerimiento de la obra' }).click()
    const req = page.getByRole('status').filter({ hasText: 'Requerimiento de la obra cargado.' })
    await expect(req).toBeVisible()
    const [, nuevos, yaEstaban] = ((await req.textContent()) ?? '').match(/(\d+) insumos nuevos · (\d+) ya estaban/) ?? []
    expect(Number(nuevos) + Number(yaEstaban)).toBe(284)
  })

  test('repetir la importación no duplica: 0 a crear y 0 creados', async ({ page }) => {
    await entrar(page)
    await irA(page, 'Importar del S10')
    await page.getByLabel('PDF de la Relación de insumos del S10').setInputFiles(PDF_S10)
    await page.getByRole('button', { name: 'Leer el archivo' }).click()
    await expect(page.getByText('0 a crear · 284 a actualizar')).toBeVisible({ timeout: 90_000 })
    await page.getByRole('button', { name: 'Confirmar e importar 284 insumos' }).click()
    await expect(page.getByRole('status').filter({ hasText: 'Catálogo actualizado.' })).toContainText('0 creados')
  })
})
