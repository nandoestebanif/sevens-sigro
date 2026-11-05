/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · Administración (HU-13, HU-14, HU-15 y almacenes de la obra)

   Va SOLO contra la API real, por el mismo `pedir` de http.ts (sesión, nombres y
   errores {codigo, mensaje}). No hay versión simulada: usuarios, permisos y
   celulares inventados no le sirven a nadie. Sin VITE_API_URL la pantalla lo dice.

   La API es la que verifica que quien llama sea administrador de la obra (regla 9):
   que el menú esconda la sección es comodidad, no control de acceso.
   ───────────────────────────────────────────────────────────────────────── */
import { pedir } from './http'

export type Rol = 'almacenero' | 'ssoma' | 'residente' | 'administrador' | 'gerente'
export type EstadoDispositivo = 'pendiente' | 'activo' | 'revocado'
export type TipoAlmacen = 'central' | 'frente'

export interface Usuario {
  id: number
  nombre: string
  usuario: string
  estado: 'activo' | 'baja' | string
  creadoEn: string
  /** La base la guarda al dar de baja; la API todavía no la devuelve (pedido a Adiel). */
  fechaBaja?: string | null
}

export interface Asignacion {
  id: number
  usuarioId: number
  usuarioNombre: string
  usuario: string
  rol: Rol
  fechaAsignacion: string
  fechaRetiro: string | null
}

export interface Dispositivo {
  id: number
  usuarioId: number
  usuarioNombre: string
  identificador: string
  estado: EstadoDispositivo
  solicitadoEn: string
  fechaAutorizacion: string | null
  fechaRevocacion: string | null
  motivoRevocacion: string | null
}

export interface Almacen {
  id: number
  nombre: string
  tipo: TipoAlmacen | string
  estado: string
}

const obra = (id: string | number) => `/obras/${encodeURIComponent(String(id))}`

export const administracion = {
  /* ── usuarios (HU-13): son de la empresa, no de una obra ── */
  usuarios: () => pedir<Usuario[]>('GET', '/usuarios'),
  /** La clave temporal la valida el servidor (clave_debil) y nunca se guarda en la web. */
  crearUsuario: (d: { nombre: string; usuario: string; clave: string }) =>
    pedir<Usuario>('POST', '/usuarios', { nombre: d.nombre.trim(), usuario: d.usuario.trim(), clave: d.clave }),
  /** Nada se borra (regla 10): queda «de baja» con fecha y pierde sus asignaciones vigentes. */
  darDeBaja: (usuarioId: number) => pedir<Usuario>('POST', `/usuarios/${usuarioId}/baja`),

  /* ── asignaciones (HU-14): el rol vive junto a la obra ── */
  asignaciones: (obraId: string, vigentes = true) =>
    pedir<Asignacion[]>('GET', `${obra(obraId)}/asignaciones?vigentes=${vigentes}`),
  asignar: (obraId: string, usuarioId: number, rol: Rol) =>
    pedir<Asignacion>('POST', `${obra(obraId)}/asignaciones`, { usuarioId, rol }),
  retirar: (obraId: string, asignacionId: number) =>
    pedir<Asignacion>('POST', `${obra(obraId)}/asignaciones/${asignacionId}/retiro`),

  /* ── celulares (HU-15, regla 5) ── */
  dispositivos: (obraId: string) => pedir<Dispositivo[]>('GET', `${obra(obraId)}/dispositivos`),
  autorizar: (obraId: string, dispositivoId: number) =>
    pedir<Dispositivo>('POST', `${obra(obraId)}/dispositivos/${dispositivoId}/autorizar`),
  revocar: (obraId: string, dispositivoId: number, motivo: string) =>
    pedir<Dispositivo>('POST', `${obra(obraId)}/dispositivos/${dispositivoId}/revocar`, { motivo: motivo.trim() }),

  /* ── almacenes de la obra ── */
  almacenes: (obraId: string) => pedir<Almacen[]>('GET', `${obra(obraId)}/almacenes`),
  crearAlmacen: (obraId: string, d: { nombre: string; tipo: TipoAlmacen }) =>
    pedir<Almacen>('POST', `${obra(obraId)}/almacenes`, { nombre: d.nombre.trim(), tipo: d.tipo }),
}
