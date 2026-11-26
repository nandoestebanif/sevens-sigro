/* Rediseño negro y amarillo: lo que tiene que verse en escritorio y en celular (datos de ejemplo). */
import { expect, test } from '@playwright/test'

const esCelular = (nombre: string) => nombre === 'celular'

test.beforeEach(async ({ page }) => {
  await page.goto('/#/tablero')
  await expect(page.getByRole('heading', { level: 1, name: 'Gasto en recursos' })).toBeVisible()
})

test('arranca en el tema oscuro de la marca, con Poppins', async ({ page }) => {
  await expect(page.locator('html')).toHaveAttribute('data-bs-theme', 'dark')
  const fondo = await page.evaluate(() => getComputedStyle(document.body).backgroundColor)
  expect(fondo).toBe('rgb(36, 36, 36)')
  const letra = await page.evaluate(() => getComputedStyle(document.body).fontFamily)
  expect(letra).toContain('Poppins')
})

test('las cifras van en tickets de color con su ilustración', async ({ page }) => {
  const tickets = page.locator('.sigro-ticket')
  await expect(tickets.first()).toBeVisible()
  expect(await tickets.count()).toBeGreaterThanOrEqual(4)
  await expect(tickets.first().locator('svg[aria-hidden="true"]')).toBeVisible()
  const colores = await tickets.evaluateAll((ts) => ts.slice(0, 3).map((t) => getComputedStyle(t).backgroundColor))
  expect(new Set(colores).size).toBe(3) // se turnan: no hay dos seguidos del mismo color
})

test('el menú tiene las áreas reales y marca la pantalla abierta', async ({ page }, info) => {
  if (esCelular(info.project.name)) await page.getByRole('button', { name: 'Abrir menú' }).click()
  const menu = page.getByRole('navigation', { name: 'Secciones' })
  for (const area of ['Preparar la obra', 'Control de la obra', 'Abastecimiento', 'Almacén']) {
    await expect(menu.getByRole('button', { name: area })).toBeVisible()
  }
  await expect(menu.getByRole('link', { name: 'Gasto en recursos' })).toHaveClass(/active/)
})

test('el menú se contrae a íconos y se vuelve a expandir', async ({ page }, info) => {
  test.skip(esCelular(info.project.name), 'en celular el menú es un panel que se abre con ☰')
  const contraer = page.getByRole('button', { name: 'Contraer menú' })
  await contraer.click()
  await expect(page.locator('.sigro-menu')).toHaveClass(/compacto/)
  // el menú se encoge con una transición: se espera a que termine en vez de medirlo a mitad (fallaba a veces con 129 px)
  await expect.poll(() => page.locator('.sigro-menu').evaluate((m) => m.getBoundingClientRect().width)).toBeLessThan(120)
  await page.getByRole('button', { name: 'Expandir menú' }).click()
  await expect(page.locator('.sigro-menu')).not.toHaveClass(/compacto/)
})

test('en celular la página no se desborda a lo ancho', async ({ page }, info) => {
  test.skip(!esCelular(info.project.name), 'solo celular')
  const desborde = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(desborde).toBeLessThanOrEqual(1)
})

test('el tema claro se elige desde Pantalla y se recuerda', async ({ page }) => {
  await page.getByRole('button', { name: 'Pantalla' }).click()
  await page.getByRole('button', { name: 'Claro' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-bs-theme', 'light')
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-bs-theme', 'light')
})
