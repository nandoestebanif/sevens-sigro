/* SIGRO · Administración · reglas de pantalla sin React ni red (se prueban con Vitest).
   Aquí NO se decide quién puede qué: eso lo hace la API en cada petición (regla 9).
   Solo se decide qué mostrar y cómo decirlo. */
import type { Asignacion, Dispositivo, EstadoDispositivo, Rol, TipoAlmacen, Usuario } from '../../api/administracion'

export const ROLES: { valor: Rol; texto: string }[] = [
  { valor: 'almacenero', texto: 'Almacenero' },
  { valor: 'ssoma', texto: 'Ingeniero SSOMA' },
  { valor: 'residente', texto: 'Residente de obra' },
  { valor: 'administrador', texto: 'Administrador' },
  { valor: 'gerente', texto: 'Gerente' },
]
export const textoRol = (r: string) => ROLES.find((x) => x.valor === r)?.texto ?? r

export const TIPOS_ALMACEN: { valor: TipoAlmacen; texto: string }[] = [
  { valor: 'central', texto: 'Central' },
  { valor: 'frente', texto: 'Frente de obra' },
]
export const textoTipoAlmacen = (t: string) => TIPOS_ALMACEN.find((x) => x.valor === t)?.texto ?? t

/** La sección se muestra solo a quien es administrador EN la obra activa. La API igual lo verifica. */
export function esAdministrador(
  sesion: { obras: { id: number | string; rol: string }[] } | null,
  obraActivaId: string | null | undefined,
): boolean {
  if (!sesion || obraActivaId == null) return false
  return sesion.obras.some((o) => String(o.id) === String(obraActivaId) && o.rol === 'administrador')
}

/* ── celulares ── */
export const ESTADO_CELULAR: Record<EstadoDispositivo, { texto: string; tono: 'bien' | 'aviso' | 'neutro' }> = {
  pendiente: { texto: 'Por autorizar', tono: 'aviso' },
  activo: { texto: 'Autorizado', tono: 'bien' },
  revocado: { texto: 'Revocado', tono: 'neutro' },
}

/** Botones de cada fila: autorizar solo un pendiente; revocar solo uno autorizado (activo). */
export const puedeAutorizar = (d: Dispositivo) => d.estado === 'pendiente'
export const puedeRevocar = (d: Dispositivo) => d.estado === 'activo'

/** El 409 que pone la base cuando la persona ya tiene otro celular autorizado en la obra (regla 5). */
export const CODIGO_YA_TIENE_CELULAR = 'uq_dispositivo_activo_por_usuario'

/** El celular autorizado que impide autorizar `d`: el otro activo de la misma persona en la obra. */
export function celularAnterior(lista: Dispositivo[], d: Dispositivo): Dispositivo | null {
  return lista.find((x) => x.usuarioId === d.usuarioId && x.id !== d.id && x.estado === 'activo') ?? null
}

/** Pendientes primero (son los que esperan una acción), después autorizados y al final revocados. */
export function ordenarCelulares(lista: Dispositivo[]): Dispositivo[] {
  const peso: Record<string, number> = { pendiente: 0, activo: 1, revocado: 2 }
  return [...lista].sort((a, b) => (peso[a.estado] ?? 3) - (peso[b.estado] ?? 3) || a.usuarioNombre.localeCompare(b.usuarioNombre, 'es'))
}

export function validarMotivo(motivo: string): string | null {
  const m = motivo.trim()
  if (m.length < 3) return 'Escribe el motivo de la revocación (al menos 3 letras).'
  if (m.length > 300) return 'El motivo admite como máximo 300 caracteres.'
  return null
}

/* ── usuarios ── */
const PATRON_USUARIO = /^[A-Za-z0-9._-]{3,40}$/

/** Lo que se puede revisar antes de enviar. La clave NO: su regla la aplica y explica el servidor. */
export function validarAltaUsuario(d: { nombre: string; usuario: string; clave: string }): Record<string, string> {
  const e: Record<string, string> = {}
  if (!d.nombre.trim()) e.nombre = 'Escribe nombres y apellidos.'
  else if (d.nombre.trim().length > 120) e.nombre = 'El nombre admite como máximo 120 caracteres.'
  if (!PATRON_USUARIO.test(d.usuario.trim())) e.usuario = 'El usuario lleva de 3 a 40 letras, números, punto, guion o guion bajo, sin espacios.'
  if (!d.clave) e.clave = 'Escribe una clave temporal.'
  return e
}

/** En qué campo del alta va un error del servidor. */
export function campoDelError(codigo: string): 'nombre' | 'usuario' | 'clave' | null {
  if (codigo === 'clave_debil') return 'clave'
  if (codigo === 'uq_usuario_usuario' || codigo === 'ck_usuario_usuario') return 'usuario'
  return null
}

export const estaDeBaja = (u: Usuario) => u.estado === 'baja'

/** Activos primero, después los de baja; dentro, por nombre. Los de baja no se esconden: son historia. */
export function ordenarUsuarios(lista: Usuario[]): Usuario[] {
  return [...lista].sort((a, b) => Number(estaDeBaja(a)) - Number(estaDeBaja(b)) || a.nombre.localeCompare(b.nombre, 'es'))
}

/* ── asignaciones ── */
/** A quién se puede asignar: activos y sin asignación vigente en esta obra (una persona, un rol por obra). */
export function asignables(usuarios: Usuario[], vigentes: Asignacion[]): Usuario[] {
  const yaEstan = new Set(vigentes.filter((a) => a.fechaRetiro == null).map((a) => a.usuarioId))
  return ordenarUsuarios(usuarios).filter((u) => !estaDeBaja(u) && !yaEstan.has(u.id))
}

/* ── mensajes ── */
/**
 * Lo que ve la persona cuando algo falla. Los mensajes de negocio de la API ya están escritos para una persona
 * y salen tal cual; lo técnico (validación de Pydantic, errores internos) se traduce.
 */
export function mensajeParaPersona(e: unknown): string {
  const codigo = (e as { codigo?: string } | null)?.codigo
  const texto = e instanceof Error ? e.message : ''
  switch (codigo) {
    case 'sin_conexion':
      return 'No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.'
    case 'sin_permiso':
      return 'Solo el administrador de la obra puede hacer esto.'
    case 'sesion_vencida':
      return 'Tu sesión venció. Vuelve a ingresar.'
    case 'error_interno':
      return 'El servidor no pudo completar la operación. Inténtalo de nuevo en un momento.'
    case 'datos_invalidos':
      return validacionLegible(texto)
    case CODIGO_YA_TIENE_CELULAR:
      return 'Esta persona ya tiene otro celular autorizado en esta obra. Para autorizar este, primero revoca el anterior.'
  }
  if (!codigo || /^http_\d+$/.test(codigo) || !texto) return 'No se pudo completar la operación. Inténtalo de nuevo.'
  return texto
}

/** «usuario: String should match pattern …» → algo que se entienda, según el campo. */
function validacionLegible(texto: string): string {
  const campo = texto.split(':')[0]?.trim()
  switch (campo) {
    case 'usuario': return 'El usuario lleva de 3 a 40 letras, números, punto, guion o guion bajo, sin espacios.'
    case 'nombre': return 'Revisa el nombre: es obligatorio y admite hasta 120 caracteres (80 en un almacén).'
    case 'motivo': return 'Escribe el motivo de la revocación (de 3 a 300 caracteres).'
    case 'rol': return 'Elige un rol de la lista.'
    case 'tipo': return 'Elige si el almacén es central o de frente.'
    case 'clave': return 'La clave temporal es demasiado larga.'
    default: return 'Revisa los datos del formulario.'
  }
}

/* ── fechas ── */
const FORMATO = new Intl.DateTimeFormat('es-PE', {
  timeZone: 'America/Lima', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false,
})

/** Fecha del servidor en hora de Lima: «05/11/2026 12:20». Sin fecha, «—». */
export function fechaLima(iso: string | null | undefined): string {
  if (!iso) return '—'
  const t = Date.parse(/[zZ]|[+-]\d\d:?\d\d$/.test(iso) ? iso : `${iso}Z`)
  return Number.isNaN(t) ? '—' : FORMATO.format(new Date(t)).replace(',', '')
}
