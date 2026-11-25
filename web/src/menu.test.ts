/* Menú lateral en acordeón y modo compacto por alto útil. */
import { describe, expect, it } from 'vitest'
import { CORTE_BAJO, tipoDeAlto } from './escala'
import { alternarGrupo, grupoDeRuta } from './menu'

const grupos = [
  { rotulo: 'Preparar la obra', items: [{ ruta: '/obra' }, { ruta: '/importar' }] },
  { rotulo: 'Control de la obra', items: [{ ruta: '/tablero' }, { ruta: '/reposicion' }, { ruta: '/parametros' }] },
  { rotulo: 'Administración', items: [{ ruta: '/administracion' }] },
]

describe('acordeón del menú', () => {
  it('al entrar está abierta el área de la pantalla actual', () => {
    expect(grupoDeRuta(grupos, '/parametros')).toBe('Control de la obra')
    expect(grupoDeRuta(grupos, '/administracion/usuarios')).toBe('Administración') // subruta
    expect(grupoDeRuta(grupos, '/reposicion-x')).toBeNull() // no confunde prefijos
    expect(grupoDeRuta(grupos, '/')).toBeNull()
  })
  it('un área a la vez: abrir otra cierra la anterior; tocar la abierta la cierra', () => {
    expect(alternarGrupo('Control de la obra', 'Preparar la obra')).toBe('Preparar la obra')
    expect(alternarGrupo('Preparar la obra', 'Preparar la obra')).toBeNull()
    expect(alternarGrupo(null, 'Administración')).toBe('Administración')
  })
})

describe('alto útil', () => {
  it('1366×768 a 125 % es bajo (el menú se compacta); a 100 % y en 1080 p no', () => {
    expect(tipoDeAlto(768, '125')).toBe('bajo')   // 614 px útiles
    expect(tipoDeAlto(768, '100')).toBe('normal') // 768
    expect(tipoDeAlto(1080, '125')).toBe('normal') // 864
    expect(tipoDeAlto(CORTE_BAJO - 1, '100')).toBe('bajo')
  })
})
