/* Lo común a todas las pruebas: credenciales del entorno, entrar y navegar por el menú.
   Selectores por rol y por etiqueta (lo que ve una persona), no por clases ni estilos: así sobreviven al rediseño. */
import { expect, type Page } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const USUARIO = process.env.SIGRO_E2E_USUARIO ?? ''
export const CLAVE = process.env.SIGRO_E2E_CLAVE ?? ''
export const SIN_CREDENCIALES = !USUARIO || !CLAVE
export const MOTIVO_SIN_CREDENCIALES = 'Faltan SIGRO_E2E_USUARIO y SIGRO_E2E_CLAVE (nunca se escriben en el repositorio).'

/** La Relación de insumos real del S10 (presupuesto 2701006, subpresupuesto 002). Se lee, nunca se edita. */
export const PDF_S10 = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../insumos-cliente/5.3. Relacion de insumos.pdf')

export async function entrar(page: Page, usuario = USUARIO, clave = CLAVE) {
  await page.goto('/')
  await page.getByLabel('Usuario', { exact: true }).fill(usuario)
  await page.getByLabel('Contraseña', { exact: true }).fill(clave)
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('navigation', { name: 'Secciones' })).toBeVisible()
}

export async function irA(page: Page, seccion: string | RegExp) {
  await page.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: seccion, exact: true }).click()
}
