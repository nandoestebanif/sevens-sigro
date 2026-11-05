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
