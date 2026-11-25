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
  /** Desde la 0021 la API la devuelve. */
  fechaBaja?: string | null
  dni?: string | null
  correo?: string | null
  celular?: string | null
}

export interface DniReniec { dni: string; nombres: string; apellidoPaterno: string; apellidoMaterno: string; nombreCompleto: string }

/** Vacío = «no lo tiene» (la API lo guarda como nulo). */
const limpio = (v?: string) => (v ?? '').replace(/\s/g, '')

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
  crearUsuario: (d: { nombre: string; usuario: string; clave: string; dni?: string; correo?: string; celular?: string }) =>
    pedir<Usuario>('POST', '/usuarios', {
      nombre: d.nombre.trim(), usuario: d.usuario.trim(), clave: d.clave,
      dni: limpio(d.dni), correo: (d.correo ?? '').trim(), celular: limpio(d.celular),
    }),
  /** Nombre de la persona en RENIEC (404 si no respondió: se escribe a mano). */
  consultarDni: (dni: string) => pedir<DniReniec>('GET', `/usuarios/consulta-dni/${encodeURIComponent(limpio(dni))}`),
  /** Completa o corrige el contacto; un campo vacío lo quita. */
  actualizarContacto: (usuarioId: number, d: { dni: string; correo: string; celular: string }) =>
    pedir<Usuario>('POST', `/usuarios/${usuarioId}/contacto`, { dni: limpio(d.dni), correo: d.correo.trim(), celular: limpio(d.celular) }),
  /** Para quien olvidó su clave: la temporal se entrega en persona y la persona la cambia al entrar. */
  claveTemporal: (usuarioId: number, clave: string) => pedir<Usuario>('POST', `/usuarios/${usuarioId}/clave-temporal`, { clave }),
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
