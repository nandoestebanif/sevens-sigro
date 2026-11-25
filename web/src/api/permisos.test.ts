/* La copia de permisos de la web debe ser IDÉNTICA a la del servidor (api/app/permisos.py). */
import { describe, expect, it } from 'vitest'
import { ACCIONES, DESCRIPCION, GRUPOS_PERMISOS, puede } from './permisos'
// el archivo del servidor, tal cual (Vite lo importa como texto)
import py from '../../../api/app/permisos.py?raw'

const CONSTANTES: Record<string, string> = { ALMACENERO: 'almacenero', SSOMA: 'ssoma', RESIDENTE: 'residente', ADMIN: 'administrador', GERENTE: 'gerente' }
const TODOS = Object.values(CONSTANTES)

function delServidor(): Record<string, string[]> {
  const out: Record<string, string[]> = {}
  for (const m of py.matchAll(/^\s+"([a-z_]+\.[a-z_]+)":\s*(TODOS|frozenset\(\{([^}]*)\}\))/gm)) {
    out[m[1]] = m[2] === 'TODOS' ? [...TODOS] : m[3].split(',').map((x: string) => CONSTANTES[x.trim()]).filter(Boolean)
  }
  return out
}

describe('permisos de la web = permisos del servidor', () => {
  const servidor = delServidor()
  it('lee las acciones del servidor', () => { expect(Object.keys(servidor).length).toBeGreaterThan(40) })
  it('mismas acciones y mismos roles', () => {
    const ordenar = (m: Record<string, string[]>) => Object.fromEntries(Object.entries(m).map(([k, v]) => [k, [...v].sort()]).sort())
    expect(ordenar(ACCIONES)).toEqual(ordenar(servidor))
  })
  it('cada acción tiene descripción y está en un grupo de la matriz', () => {
    const agrupadas = GRUPOS_PERMISOS.flatMap((g) => g.acciones)
    for (const k of Object.keys(ACCIONES)) { expect(DESCRIPCION[k], k).toBeTruthy(); expect(agrupadas, k).toContain(k) }
    expect(new Set(agrupadas).size).toBe(agrupadas.length)
  })
  it('puede() responde con la matriz', () => {
    expect(puede('residente', 'solicitud.emitir')).toBe(true)
    expect(puede('almacenero', 'solicitud.emitir')).toBe(false)
    expect(puede(null, 'proyecto.consultar')).toBe(false)
    expect(puede('administrador', 'accion.inexistente')).toBe(false)
  })
})
