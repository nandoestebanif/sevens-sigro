/* Administración · reglas de pantalla (sin red ni React). */
import { describe, expect, it } from 'vitest'
import type { Asignacion, Dispositivo, Usuario } from '../../api/administracion'
import { ErrorApi } from '../../api/error'
import {
  CODIGO_YA_TIENE_CELULAR, asignables, campoDelError, celularAnterior, esAdministrador, fechaLima, mensajeParaPersona,
  ordenarCelulares, ordenarUsuarios, puedeAutorizar, puedeRevocar, validarAltaUsuario, validarMotivo,
} from './reglas'

const celular = (id: number, usuarioId: number, estado: Dispositivo['estado'], nombre = `Persona ${usuarioId}`): Dispositivo => ({
  id, usuarioId, usuarioNombre: nombre, identificador: `sigro-android-${id}`, estado,
  solicitadoEn: '2026-11-05T15:00:00Z', fechaAutorizacion: null, fechaRevocacion: null, motivoRevocacion: null,
})
const usuario = (id: number, nombre: string, estado = 'activo'): Usuario => ({ id, nombre, usuario: `u${id}`, estado, creadoEn: '2026-11-01T12:00:00Z' })
const asignacion = (id: number, usuarioId: number, fechaRetiro: string | null = null): Asignacion => ({
  id, usuarioId, usuarioNombre: '', usuario: '', rol: 'almacenero', fechaAsignacion: '2026-11-01T12:00:00Z', fechaRetiro,
})

describe('quién ve la sección', () => {
  const sesion = { obras: [{ id: 1, rol: 'administrador' }, { id: 2, rol: 'residente' }] }
  it('solo el administrador de la obra activa', () => {
    expect(esAdministrador(sesion, '1')).toBe(true)
    expect(esAdministrador(sesion, '2')).toBe(false)
    expect(esAdministrador(sesion, '3')).toBe(false)
    expect(esAdministrador(null, '1')).toBe(false)
    expect(esAdministrador(sesion, undefined)).toBe(false)
  })
})

describe('celulares (HU-15)', () => {
  it('autorizar solo un pendiente; revocar solo uno autorizado', () => {
    expect([puedeAutorizar(celular(1, 1, 'pendiente')), puedeRevocar(celular(1, 1, 'pendiente'))]).toEqual([true, false])
    expect([puedeAutorizar(celular(1, 1, 'activo')), puedeRevocar(celular(1, 1, 'activo'))]).toEqual([false, true])
    expect([puedeAutorizar(celular(1, 1, 'revocado')), puedeRevocar(celular(1, 1, 'revocado'))]).toEqual([false, false])
  })

  it('ante el 409 encuentra el celular anterior de la MISMA persona', () => {
    const nuevo = celular(3, 7, 'pendiente')
    const lista = [celular(1, 7, 'revocado'), celular(2, 7, 'activo'), celular(4, 8, 'activo'), nuevo]
    expect(celularAnterior(lista, nuevo)?.id).toBe(2)
    expect(celularAnterior([nuevo, celular(4, 8, 'activo')], nuevo)).toBeNull()
  })

  it('el 409 se explica en lenguaje claro', () => {
    const m = mensajeParaPersona(new ErrorApi(CODIGO_YA_TIENE_CELULAR, 'Esa persona ya tiene un dispositivo autorizado en esta obra; revócalo antes de autorizar otro.'))
    expect(m).toMatch(/ya tiene otro celular autorizado/)
    expect(m).toMatch(/revoca el anterior/)
  })

  it('pendientes primero', () => {
    const xs = ordenarCelulares([celular(1, 1, 'revocado', 'Ana'), celular(2, 2, 'activo', 'Beto'), celular(3, 3, 'pendiente', 'Carla')])
    expect(xs.map((x) => x.estado)).toEqual(['pendiente', 'activo', 'revocado'])
  })

  it('revocar exige motivo', () => {
    expect(validarMotivo('  ')).toMatch(/motivo/)
    expect(validarMotivo('ok')).toMatch(/3 letras/)
    expect(validarMotivo('Cambió de celular')).toBeNull()
    expect(validarMotivo('x'.repeat(301))).toMatch(/300/)
  })
})

describe('usuarios (HU-13)', () => {
  it('revisa nombre y usuario antes de enviar; la clave la juzga el servidor', () => {
    expect(validarAltaUsuario({ nombre: '', usuario: 'j perez', clave: '' })).toEqual({
      nombre: expect.any(String), usuario: expect.any(String), clave: 'Escribe una clave temporal.',
    })
    // cualquier clave no vacía pasa aquí: si es débil, lo dice la API (clave_debil)
    expect(validarAltaUsuario({ nombre: 'Juan Pérez', usuario: 'jperez', clave: 'x' })).toEqual({})
  })

  it('el mensaje de clave débil de la API va bajo el campo de la clave, tal cual', () => {
    const e = new ErrorApi('clave_debil', 'La contraseña debe tener al menos 10 caracteres.')
    expect(campoDelError(e.codigo)).toBe('clave')
    expect(mensajeParaPersona(e)).toBe('La contraseña debe tener al menos 10 caracteres.')
    expect(campoDelError('uq_usuario_usuario')).toBe('usuario')
    expect(campoDelError('sin_permiso')).toBeNull()
  })

  it('los de baja no se esconden: van al final', () => {
    const xs = ordenarUsuarios([usuario(1, 'Zoe'), usuario(2, 'Ana', 'baja'), usuario(3, 'Beto')])
    expect(xs.map((u) => u.nombre)).toEqual(['Beto', 'Zoe', 'Ana'])
  })
})

describe('asignaciones (HU-14)', () => {
  it('se ofrece a los activos sin asignación vigente en la obra', () => {
    const us = [usuario(1, 'Ana'), usuario(2, 'Beto'), usuario(3, 'Carla', 'baja'), usuario(4, 'Dante')]
    const vig = [asignacion(10, 1), asignacion(11, 4, '2026-11-03T12:00:00Z')] // Dante fue retirado: se puede volver a asignar
    expect(asignables(us, vig).map((u) => u.nombre)).toEqual(['Beto', 'Dante'])
  })
})

describe('mensajes para la persona, no técnicos', () => {
  it('traduce lo técnico y respeta lo que la API ya escribió para una persona', () => {
    expect(mensajeParaPersona(new ErrorApi('sin_permiso', 'No tienes permiso para esta operación en esta obra.'))).toMatch(/administrador/)
    expect(mensajeParaPersona(new ErrorApi('datos_invalidos', "usuario: String should match pattern '^[A-Za-z0-9._-]{3,40}$'"))).toMatch(/3 a 40/)
    expect(mensajeParaPersona(new ErrorApi('datos_invalidos', 'algo: raro'))).toBe('Revisa los datos del formulario.')
    expect(mensajeParaPersona(new ErrorApi('http_500', 'El servidor rechazó la operación.'))).toMatch(/Inténtalo de nuevo/)
    expect(mensajeParaPersona(new ErrorApi('error_interno', 'No se pudo completar la operación.'))).toMatch(/servidor no pudo/)
    expect(mensajeParaPersona(new ErrorApi('ultimo_administrador', 'La obra no puede quedarse sin administrador.'))).toBe('La obra no puede quedarse sin administrador.')
    expect(mensajeParaPersona(new TypeError('x is undefined'))).toMatch(/No se pudo completar/)
  })
})

describe('fechas', () => {
  it('en hora de Lima', () => {
    expect(fechaLima('2026-11-05T17:20:31.123456Z')).toBe('05/11/2026 12:20')
    expect(fechaLima('2026-11-05T17:20:31+00:00')).toBe('05/11/2026 12:20')
    expect(fechaLima(null)).toBe('—')
    expect(fechaLima('no es fecha')).toBe('—')
  })
})
