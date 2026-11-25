/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · quién puede qué (copia EXACTA de api/app/permisos.py)

   La web la usa para mostrar u ocultar botones; quien decide es el servidor,
   que verifica cada petición contra la asignación vigente (regla 9).
   permisos.test.ts lee el archivo del servidor y falla si esta copia se desfasa.
   ───────────────────────────────────────────────────────────────────────── */
import { sesionActual } from './http'

export type Rol = 'almacenero' | 'ssoma' | 'residente' | 'administrador' | 'gerente'
export const ROLES: Rol[] = ['almacenero', 'ssoma', 'residente', 'administrador', 'gerente']

export const ACCIONES: Record<string, Rol[]> = {
  'proyecto.crear': ['residente', 'administrador'],
  'proyecto.ampliar_plazo': ['residente', 'administrador'],
  'proyecto.cerrar': ['administrador'],
  'proyecto.consultar': ['almacenero', 'ssoma', 'residente', 'administrador', 'gerente'],
  'periodo.consultar': ['almacenero', 'ssoma', 'residente', 'administrador', 'gerente'],
  'periodo.cerrar': ['residente', 'administrador'],
  'almacen.registrar': ['almacenero', 'residente', 'administrador'],
  'almacen.consultar': ['almacenero', 'residente', 'administrador', 'gerente'],
  'usuario.crear': ['administrador'],
  'usuario.consultar': ['administrador'],
  'asignacion.escribir': ['administrador'],
  'asignacion.consultar': ['residente', 'administrador'],
  'dispositivo.solicitar': ['almacenero', 'ssoma'],
  'dispositivo.autorizar': ['administrador'],
  'dispositivo.consultar': ['residente', 'administrador'],
  'trabajador.registrar': ['almacenero', 'ssoma', 'administrador'],
  'trabajador.consultar': ['almacenero', 'ssoma', 'residente', 'administrador'],
  'jornada.registrar': ['almacenero', 'ssoma'],
  'horas.consultar': ['almacenero', 'ssoma', 'residente', 'administrador', 'gerente'],
  'proveedor.registrar': ['almacenero', 'residente', 'administrador'],
  'proveedor.consultar': ['almacenero', 'residente', 'administrador', 'gerente'],
  'compra.registrar': ['almacenero', 'residente', 'administrador'],
  'compra.consultar': ['almacenero', 'residente', 'administrador', 'gerente'],
  'solicitud.emitir': ['residente'],
  'solicitud.consultar': ['residente', 'administrador', 'gerente'],
  'requerimiento.importar': ['residente', 'administrador'],
  'requerimiento.ajustar': ['residente', 'administrador'],
  'requerimiento.agregar': ['residente', 'administrador'],
  'requerimiento.consultar': ['residente', 'administrador', 'gerente'],
  'partida.importar': ['residente', 'administrador'],
  'partida.consultar': ['residente', 'administrador', 'gerente'],
  'reposicion.registrar': ['residente', 'administrador'],
  'reposicion.consultar': ['residente', 'administrador', 'gerente'],
  'critico.marcar': ['residente', 'administrador'],
  'avance.consultar': ['residente', 'administrador', 'gerente'],
  'salida.registrar': ['almacenero'],
  'ingreso.registrar': ['almacenero'],
  'recepcion.registrar': ['almacenero'],
  'traslado.registrar': ['almacenero'],
  'conteo.registrar': ['almacenero'],
  'movimiento.revertir': ['almacenero', 'residente'],
  'stock.consultar': ['almacenero', 'ssoma', 'residente', 'administrador', 'gerente'],
  'costo.consultar': ['residente', 'administrador', 'gerente'],
  'insumo.consultar': ['almacenero', 'ssoma', 'residente', 'administrador', 'gerente'],
  'insumo.registrar': ['almacenero', 'residente', 'administrador'],
  'insumo.verificar': ['residente', 'administrador'],
  'insumo.factor': ['residente', 'administrador'],
  'insumo.importar': ['residente', 'administrador'],
}

/** Lo que dice cada acción, para la matriz de Administración. */
export const DESCRIPCION: Record<string, string> = {
  'proyecto.crear': 'Crear una obra',
  'proyecto.ampliar_plazo': 'Ampliar el plazo',
  'proyecto.cerrar': 'Cerrar la obra (no se reabre)',
  'proyecto.consultar': 'Ver la obra',
  'periodo.consultar': 'Ver las semanas',
  'periodo.cerrar': 'Cerrar una semana',
  'almacen.registrar': 'Crear un almacén',
  'almacen.consultar': 'Ver los almacenes',
  'usuario.crear': 'Crear usuarios y darlos de baja',
  'usuario.consultar': 'Ver usuarios',
  'asignacion.escribir': 'Asignar y retirar personas',
  'asignacion.consultar': 'Ver quién está en la obra',
  'dispositivo.solicitar': 'Pedir autorización del celular',
  'dispositivo.autorizar': 'Autorizar o revocar celulares',
  'dispositivo.consultar': 'Ver celulares',
  'trabajador.registrar': 'Registrar, editar y dar de baja trabajadores',
  'trabajador.consultar': 'Ver trabajadores',
  'jornada.registrar': 'Tomar asistencia',
  'horas.consultar': 'Ver horas acumuladas',
  'proveedor.registrar': 'Registrar proveedores',
  'proveedor.consultar': 'Ver proveedores',
  'compra.registrar': 'Registrar y anular compras',
  'compra.consultar': 'Ver compras',
  'solicitud.emitir': 'Emitir y anular solicitudes de compra',
  'solicitud.consultar': 'Ver solicitudes de compra',
  'requerimiento.importar': 'Importar el requerimiento del S10',
  'requerimiento.ajustar': 'Ajustar lo planificado',
  'requerimiento.agregar': 'Agregar un insumo al requerimiento',
  'requerimiento.consultar': 'Ver el requerimiento',
  'partida.importar': 'Importar partidas (APU)',
  'partida.consultar': 'Ver partidas',
  'reposicion.registrar': 'Fijar parámetros de reposición',
  'reposicion.consultar': 'Ver reposición',
  'critico.marcar': 'Marcar insumos críticos',
  'avance.consultar': 'Ver el avance de compras',
  'salida.registrar': 'Registrar salidas',
  'ingreso.registrar': 'Registrar ingresos sin compra',
  'recepcion.registrar': 'Recibir una compra',
  'traslado.registrar': 'Trasladar entre almacenes',
  'conteo.registrar': 'Contar el almacén',
  'movimiento.revertir': 'Revertir un movimiento',
  'stock.consultar': 'Ver stock y kárdex',
  'costo.consultar': 'Ver gasto e inventario valorizado',
  'insumo.consultar': 'Ver el catálogo',
  'insumo.registrar': 'Agregar un insumo al catálogo',
  'insumo.verificar': 'Verificar un insumo',
  'insumo.factor': 'Cambiar el factor de conversión',
  'insumo.importar': 'Importar el catálogo del S10',
}

export const GRUPOS_PERMISOS: { grupo: string; acciones: string[] }[] = [
  { grupo: 'Obra', acciones: ['proyecto.crear', 'proyecto.ampliar_plazo', 'proyecto.cerrar', 'proyecto.consultar', 'periodo.consultar', 'periodo.cerrar', 'almacen.registrar', 'almacen.consultar'] },
  { grupo: 'Accesos', acciones: ['usuario.crear', 'usuario.consultar', 'asignacion.escribir', 'asignacion.consultar', 'dispositivo.solicitar', 'dispositivo.autorizar', 'dispositivo.consultar'] },
  { grupo: 'Mano de obra', acciones: ['trabajador.registrar', 'trabajador.consultar', 'jornada.registrar', 'horas.consultar'] },
  { grupo: 'Compras', acciones: ['proveedor.registrar', 'proveedor.consultar', 'compra.registrar', 'compra.consultar', 'solicitud.emitir', 'solicitud.consultar', 'requerimiento.importar', 'requerimiento.ajustar', 'requerimiento.agregar', 'requerimiento.consultar', 'partida.importar', 'partida.consultar', 'reposicion.registrar', 'reposicion.consultar', 'critico.marcar', 'avance.consultar'] },
  { grupo: 'Almacén', acciones: ['salida.registrar', 'ingreso.registrar', 'recepcion.registrar', 'traslado.registrar', 'conteo.registrar', 'movimiento.revertir', 'stock.consultar', 'costo.consultar'] },
  { grupo: 'Catálogo', acciones: ['insumo.consultar', 'insumo.registrar', 'insumo.verificar', 'insumo.factor', 'insumo.importar'] },
]

/** ¿Puede este rol hacer esta acción? Una acción desconocida es «no» (el servidor también la negaría). */
export const puede = (rol: string | null | undefined, accion: string) => !!rol && (ACCIONES[accion] ?? []).includes(rol as Rol)

/** El rol de la sesión en la obra activa (o en la primera, mientras carga). */
export function rolActual(obraId?: string | null): Rol | null {
  const s = sesionActual()
  const o = s?.obras.find((x) => String(x.id) === String(obraId)) ?? s?.obras[0]
  return (o?.rol as Rol) ?? null
}

/** Atajo para las pantallas: puedo('compra.registrar'). Sin sesión real (modo demostración) se muestra todo. */
export function puedo(accion: string, obraId?: string | null): boolean {
  const s = sesionActual()
  if (!s) return true
  return puede(rolActual(obraId), accion)
}
