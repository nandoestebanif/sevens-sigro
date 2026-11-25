/* SIGRO · menú lateral en acordeón: un área abierta a la vez, así el menú entra sin scroll
   (también a 125 % y en 1366×768). Reglas puras, se prueban con Vitest. */

export interface GrupoMenu { rotulo: string; items: { ruta: string }[] }

/** El área que contiene la pantalla abierta (por la ruta), o null si ninguna. */
export function grupoDeRuta(grupos: GrupoMenu[], ruta: string): string | null {
  const g = grupos.find((x) => x.items.some((i) => ruta === i.ruta || ruta.startsWith(`${i.ruta}/`)))
  return g?.rotulo ?? null
}

/** Clic en un área: si estaba abierta se cierra; si no, se abre y se cierra la que estaba. */
export function alternarGrupo(abierto: string | null, rotulo: string): string | null {
  return abierto === rotulo ? null : rotulo
}

/** Iniciales del avatar del menú: las dos primeras palabras del nombre («Adiel Luján» → «AL»). */
export function iniciales(nombre: string): string {
  return nombre.trim().split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toLocaleUpperCase('es-PE') ?? '').join('')
}

/** Menú por función (25/11): cada rol de campo ve solo su módulo. El almacenero, el almacén (y las compras que
    recibe); el SSOMA, el personal y las herramientas. Residente, administrador y gerente ven lo que sus permisos
    admiten. Esconder es comodidad: el servidor igual verifica el rol en cada petición (regla 9). */
export const MENU_POR_ROL: Readonly<Record<string, readonly string[]>> = {
  almacenero: ['/movimientos', '/stock', '/herramientas', '/semana', '/compras', '/catalogo'],
  ssoma: ['/trabajadores', '/mano-de-obra', '/herramientas'],
}

/** ¿Esta ruta va en el menú de ese rol? Sin lista propia, decide el permiso de cada pantalla. */
export function rutaVisible(rol: string | null | undefined, ruta: string): boolean {
  const lista = rol ? MENU_POR_ROL[rol] : undefined
  return !lista || lista.some((r) => ruta === r || ruta.startsWith(`${r}/`))
}
