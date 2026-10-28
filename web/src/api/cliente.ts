/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · capa de API
   Las pantallas hablan con esta interfaz y con nada más. Hoy la implementación
   es simulada (datos en memoria con la latencia de una red real); cuando la API
   de FastAPI esté desplegada, se escribe una implementación `ClienteHttp` con
   los mismos métodos y las pantallas no se tocan.

   Reglas que la simulación respeta igual que las respetará el servidor:
   - El RUC es único: un alta repetida falla, no sobreescribe.
   - Un proveedor de baja no aparece para compras nuevas.
   - Registrar la compra NO mueve el stock.
   - Los estados y los números derivados vienen calculados, nunca los arma la UI.
   ───────────────────────────────────────────────────────────────────────── */
import { INSUMOS_S10 } from '../datos/insumos-s10'
import type {
  AlertaReposicion, AvanceInsumo, Compra, InsumoCatalogo, InsumoMaestro,
  LineaCompra, LineaRecepcion, Movimiento, Obra, ObraActiva, Proveedor,
  ResumenTablero, Semana, StockInsumo, TipoComprobante,
} from './tipos'

import { ErrorApi } from './error'
import { catalogoHttp, obrasHttp, proveedoresHttp, usaApiReal } from './http'
export { ErrorApi }

export interface ClienteSigro {
  obraActiva(): Promise<ObraActiva>
  listarObras(): Promise<Obra[]>
  /** regla 9: el alcance es la obra — al cambiarla, lo de empresa (catálogo,
      proveedores) permanece y lo de obra (stock, compras, gasto) cambia */
  elegirObra(id: string): Promise<ObraActiva>
  /** HU-07: aplica al catálogo lo que leyó el microservicio del S10. El código es la
      clave: repetir la importación actualiza, no duplica, y no toca saldos. */
  aplicarImportacionS10(filas: { codigo: string; descripcion: string; unidad: string; tipo: string }[]):
    Promise<{ creados: number; actualizados: number; sinCambios: number; advertencias: string[] }>
  /** HU-26: el aviso salta solo al cruzar el punto; HU-27: con su sugerencia */
  alertasReposicion(): Promise<AlertaReposicion[]>
  /** HU-01: nombre y plazo obligatorios; nace «en ejecución».
      HU-03: los periodos semanales se generan solos, del inicio al fin. */
  crearObra(datos: { nombre: string; presupuesto: string; inicio: string; fin: string }): Promise<Obra>
  listarProveedores(busqueda?: string): Promise<Proveedor[]>
  /** solo los que admiten compras nuevas: excluye a los dados de baja (HU-29) */
  proveedoresParaComprar(): Promise<Proveedor[]>
  crearProveedor(datos: { ruc: string; razonSocial: string; validadoEnSunat?: boolean }): Promise<Proveedor>
  listarInsumos(): Promise<InsumoCatalogo[]>
  /** el catálogo maestro completo de la empresa (los 2 243 reales + los creados) */
  listarMaestro(): Promise<InsumoMaestro[]>
  /** HU-08: alta en el catálogo maestro; el código lo genera el servidor */
  crearInsumoCatalogo(datos: { nombre: string; um: string; medida?: string; marca?: string }): Promise<InsumoMaestro>
  /** corrige nombre o marca de cualquier insumo; con verificar=true un
      «por verificar» pasa a «activo». El código no se toca jamás. */
  editarInsumo(codigo: string, datos: { nombre?: string; marca?: string; verificar?: boolean }): Promise<InsumoMaestro>
  registrarCompra(datos: {
    proveedorId: string
    tipoComprobante: TipoComprobante
    numeroComprobante: string
    fecha: string
    lineas: LineaCompra[]
  }): Promise<Compra>
  listarCompras(): Promise<Compra[]>
  /** HU-35: pedido, recibido y pendiente por insumo (lo calcula el servidor) */
  detalleRecepcion(compraId: string): Promise<LineaRecepcion[]>
  /** HU-35: recibe total o parcialmente; cada línea genera su INGRESO en el
      kárdex por la cantidad recibida — es la recepción la que mueve el stock */
  registrarRecepcion(compraId: string, lineas: { insumoId: string; cantidad: number }[]): Promise<void>
  avancePorInsumo(): Promise<AvanceInsumo[]>
  resumenTablero(): Promise<ResumenTablero>
  /** HU-43: saldo por almacén y consolidado, derivado de los movimientos */
  consultarStock(): Promise<StockInsumo[]>
  /** kárdex completo, del más reciente al más antiguo, con anuladoPor derivado */
  listarMovimientos(): Promise<Movimiento[]>
  /** HU-45: corrige SIN tocar el original — crea un reverso que lo referencia */
  revertirMovimiento(id: string, motivo: string): Promise<Movimiento>
}

/* ───────────────────────────── simulación ───────────────────────────── */

const espera = (ms = 180) => new Promise((r) => setTimeout(r, ms))

/* Los proveedores son los 626 REALES del cliente (datos/proveedores-cliente.ts).
   Todos entran «sin validar» —validarlos contra SUNAT es trabajo de HU-31— y
   sobre cuatro se monta la historia de la demo de la semana 14: días de
   entrega, compras registradas y el ejemplo de baja (que es, a propósito, uno
   de los registros de prueba que viven en la base actual del cliente). */
const DEMO: Record<string, Partial<Proveedor>> = {
  // distribuidor de cemento (la obra le compra seguido)
  '20493437365': { estado: 'validado', diasEntrega: 2, comprasRegistradas: 14, ultimaCompra: '2026-10-19' },
  // acero
  '20446460481': { estado: 'validado', diasEntrega: 5, comprasRegistradas: 9, ultimaCompra: '2026-10-11' },
  // ferretería de cercanía
  '20609474212': { estado: 'sin_validar', diasEntrega: 1, comprasRegistradas: 3, ultimaCompra: '2026-10-21' },
  // registro de prueba del origen («RUC» de 6 dígitos): dado de baja
  '123456': { estado: 'de_baja', comprasRegistradas: 0 },
}

const AVANCE: AvanceInsumo[] = [
  // planificadoCantidad y planificadoImporte salen de la Relación de insumos REAL;
  // comprado/consumido son el ejemplo de la semana 14 mientras no existe la API.
  { insumoId: '0203020004', codigo: '0203020004', descripcion: 'ACERO CORRUGADO FY= 4200 Kg/cm2', unidadS10: 'kg', unidadCompra: 'varilla 9 m', fase: 'Estructuras', critico: true, planificadoCantidad: 36357.42, planificadoImporte: 165063, compradoCantidad: 11198, compradoPct: 30.8, recibidoPct: 28.2, consumidoPct: 27.1, gastado: 44732, saldoPorComprar: 2811, desviacionPuntos: 0.2 },
  { insumoId: '0221000000', codigo: '0221000000', descripcion: 'CEMENTO PORTLAND TIPO I (42.50 KG.)', unidadS10: 'bls', unidadCompra: 'bls', fase: 'Estructuras', critico: true, planificadoCantidad: 3492.36, planificadoImporte: 125725, compradoCantidad: 1271, compradoPct: 36.4, recibidoPct: 33.1, consumidoPct: 29.4, gastado: 36963, saldoPorComprar: 2221, desviacionPuntos: 2.5 },
  { insumoId: '0217080008', codigo: '0217080008', descripcion: 'LADRILLO K.K. 9x13x24 (18 Huecos)', unidadS10: 'und', unidadCompra: 'und', fase: 'Albañilería', critico: false, planificadoCantidad: 21684.93, planificadoImporte: 25588, compradoCantidad: 7633, compradoPct: 35.2, recibidoPct: 35.2, consumidoPct: 32.0, gastado: 8188, saldoPorComprar: 14052, desviacionPuntos: 5.1 },
  { insumoId: '0202000008', codigo: '0202000008', descripcion: 'ALAMBRE NEGRO RECOCIDO # 8', unidadS10: 'kg', unidadCompra: 'kg', fase: 'Estructuras', critico: false, planificadoCantidad: 457.06, planificadoImporte: 2742, compradoCantidad: 156, compradoPct: 34.1, recibidoPct: 34.1, consumidoPct: 31.2, gastado: 856, saldoPorComprar: 301, desviacionPuntos: 4.3 },
  { insumoId: '0205010033', codigo: '0205010033', descripcion: 'ARENA GRUESA', unidadS10: 'm3', unidadCompra: 'm3', fase: 'Estructuras', critico: false, planificadoCantidad: 186.18, planificadoImporte: 14894, compradoCantidad: 53, compradoPct: 28.6, recibidoPct: 25.4, consumidoPct: 22.9, gastado: 3411, saldoPorComprar: 133, desviacionPuntos: -4.0 },
]

/* Kárdex de la semana 14 (demo). Insumos y precios reales del catálogo; el
   libro ya trae una corrección hecha (M-0006 anulada por M-0007) para que la
   consulta muestre el anulado y el que lo anuló, criterio de HU-45. */
const mov = (
  id: string, fechaServidor: string, almacen: string, insumoId: string,
  descripcion: string, unidad: string, tipo: Movimiento['tipo'], cantidad: number,
  costoUnitario: number, motivo: string, autor: string, reversaA: string | null = null,
): Movimiento => ({
  id, obraId: 'o1', uuidCliente: `d1-${id.toLowerCase()}`, fechaServidor, almacen,
  insumoId, descripcion, unidad, tipo, cantidad, costoUnitario, motivo, autor,
  reversaA, anuladoPor: null,
})

const CEM = ['0221000000', 'CEMENTO PORTLAND TIPO I (42.50 KG.)', 'bls'] as const
const ACE = ['0203020004', 'ACERO CORRUGADO FY= 4200 Kg/cm2', 'varilla 9 m'] as const
const ARE = ['0205010033', 'ARENA GRUESA', 'm3'] as const

const MOVIMIENTOS: Movimiento[] = [
  mov('M-0001', '2026-10-18T08:40:11', 'Almacén central', ...ARE, 'ingreso', 15, 80.00, 'Ingreso sin compra · saldo traído de la obra San Isidro', 'L. Condori (almacenero)'),
  mov('M-0002', '2026-10-19T08:12:34', 'Almacén central', ...CEM, 'ingreso', 320, 24.77, 'Recepción de la compra F001-4512 · Dinoselva', 'L. Condori (almacenero)'),
  mov('M-0003', '2026-10-19T10:05:02', 'Almacén central', ...CEM, 'salida', 48, 24.77, 'Vaciado de losa · piso 3 · retira J. Quispe', 'L. Condori (almacenero)'),
  mov('M-0004', '2026-10-20T07:40:50', 'Almacén central', ...CEM, 'salida', 36, 24.77, 'Asentado de muros · bloque B · retira R. Mamani', 'L. Condori (almacenero)'),
  mov('M-0005', '2026-10-20T09:02:15', 'Almacén central', ...ACE, 'ingreso', 180, 34.97, 'Recepción de la compra F003-221 · Acero Comercial', 'L. Condori (almacenero)'),
  mov('M-0006', '2026-10-21T08:55:48', 'Almacén central', ...ARE, 'salida', 12, 80.00, 'Tarrajeo exterior · retira cuadrilla de albañiles', 'L. Condori (almacenero)'),
  mov('M-0007', '2026-10-21T09:10:21', 'Almacén central', ...ARE, 'reverso', 12, 80.00, 'Se digitó 12; el conteo real fue 2', 'L. Condori (almacenero)', 'M-0006'),
  mov('M-0008', '2026-10-21T09:11:03', 'Almacén central', ...ARE, 'salida', 2, 80.00, 'Tarrajeo exterior · retira cuadrilla de albañiles (corregido)', 'L. Condori (almacenero)'),
  mov('M-0009', '2026-10-21T11:30:44', 'Almacén central', ...CEM, 'salida', 20, 24.77, 'Traslado al almacén del frente B', 'L. Condori (almacenero)'),
  mov('M-0010', '2026-10-21T11:31:10', 'Almacén frente B', ...CEM, 'ingreso', 20, 24.77, 'Traslado desde el almacén central', 'L. Condori (almacenero)'),
  mov('M-0011', '2026-10-21T14:20:37', 'Almacén central', ...ACE, 'salida', 120, 34.97, 'Habilitado de columnas · piso 4 · retira cuadrilla de fierreros', 'L. Condori (almacenero)'),
  mov('M-0012', '2026-10-22T07:30:29', 'Almacén central', ...CEM, 'salida', 40, 24.77, 'Vaciado de losa · piso 4 · retira J. Quispe', 'L. Condori (almacenero)'),
]

/** efecto de un movimiento sobre el saldo: el reverso deshace el signo del original */
const efecto = (m: Movimiento, todos: Movimiento[]): number => {
  if (m.tipo === 'ingreso') return m.cantidad
  if (m.tipo === 'salida') return -m.cantidad
  const original = todos.find((x) => x.id === m.reversaA)
  return original?.tipo === 'salida' ? m.cantidad : -m.cantidad
}

const PUNTO_REPOSICION: Record<string, number> = {
  '0221000000': 250, // bls: cemento es crítico y la demo lo muestra bajo el punto
  '0203020004': 40, // varillas
}

/* HU-03: las semanas se generan solas, desde el inicio hasta el fin; la última
   puede ser más corta. El estado de cada una se deriva del calendario: una
   semana cerrada no admite movimientos nuevos (lo valida el servidor al
   registrar; la pantalla solo lo muestra). */
function generarSemanas(inicio: string, fin: string, hoy = new Date().toISOString().slice(0, 10)): Semana[] {
  const dia = (iso: string, mas = 0) => {
    const d = new Date(`${iso}T00:00:00`)
    d.setDate(d.getDate() + mas)
    return d.toISOString().slice(0, 10)
  }
  const semanas: Semana[] = []
  let desde = inicio
  let n = 1
  while (desde <= fin) {
    const hasta7 = dia(desde, 6)
    const hasta = hasta7 < fin ? hasta7 : fin
    const dias = Math.round((Date.parse(hasta) - Date.parse(desde)) / 86400000) + 1
    semanas.push({
      numero: n,
      inicio: desde,
      fin: hasta,
      dias,
      estado: hasta < hoy ? 'cerrada' : desde > hoy ? 'por_venir' : 'en_curso',
    })
    desde = dia(hasta, 1)
    n += 1
  }
  return semanas
}

export class ClienteSimulado implements ClienteSigro {
  private proveedores: Proveedor[] | null = null

  /* Compras de la demo, coherentes con el kárdex: F001-4512 explica el ingreso
     M-0002 (320 de 400 bls: recepción parcial) y F003-221 el M-0005 (completa). */
  private compras: Compra[] = [
    {
      id: 'co-1', obraId: 'o1', proveedorId: 'demo-dinoselva', proveedorRazonSocial: 'DINOSELVA IQUITOS S.A.C.',
      tipoComprobante: 'factura', numeroComprobante: 'F001-4512', fecha: '2026-10-18',
      estado: 'recibida_parcial',
      lineas: [{ insumoId: '0221000000', descripcion: 'CEMENTO PORTLAND TIPO I (42.50 KG.)', unidadCompra: 'bls', cantidad: 400, precioUnitario: 29.23 }],
      totales: { base: 9908.47, igv: 1783.53, total: 11692.0 },
      adjunto: true,
    },
    {
      id: 'co-2', obraId: 'o1', proveedorId: 'demo-acero', proveedorRazonSocial: 'ACERO COMERCIAL S.R.L.',
      tipoComprobante: 'factura', numeroComprobante: 'F003-221', fecha: '2026-10-19',
      estado: 'recibida',
      lineas: [{ insumoId: '0203020004', descripcion: 'ACERO CORRUGADO FY= 4200 Kg/cm2', unidadCompra: 'varilla 9 m', cantidad: 180, precioUnitario: 41.27 }],
      totales: { base: 6295.42, igv: 1133.18, total: 7428.6 },
      adjunto: true,
    },
  ]

  /** lo recibido por compra e insumo; el estado de la compra SE DERIVA de esto */
  private recibido: Record<string, Record<string, number>> = {
    'co-1': { '0221000000': 320 }, // M-0002 del kárdex
    'co-2': { '0203020004': 180 }, // M-0005 del kárdex
  }

  private async cargarProveedores(): Promise<Proveedor[]> {
    if (!this.proveedores) {
      const { PROVEEDORES_CLIENTE } = await import('../datos/proveedores-cliente')
      this.proveedores = PROVEEDORES_CLIENTE.map(([numero, razon], i) => ({
        id: `p${i + 1}`,
        ruc: numero,
        razonSocial: razon,
        estado: 'sin_validar' as const, // validarlos contra SUNAT es HU-31
        diasEntrega: null,
        comprasRegistradas: 0,
        ultimaCompra: null,
        ...DEMO[numero],
      }))
    }
    return this.proveedores
  }

  /* La obra real de la demo; sus 52 semanas salen del MISMO generador que
     usará cualquier obra nueva (de ahí el «semana 14 de 52» de toda la web). */
  private obras: Obra[] = [{
    id: 'o1', nombre: 'Multifamiliar Flores', presupuesto: '2701006-002',
    inicio: '2026-07-20', fin: '2027-07-18', estado: 'en_ejecucion',
    semanas: generarSemanas('2026-07-20', '2027-07-18'),
  }]

  /** regla 9: el alcance es la obra; todo lo que es «de obra» se filtra por esta */
  private obraActivaId = 'o1'

  async obraActiva(): Promise<ObraActiva> {
    await espera(60)
    const o = this.obras.find((x) => x.id === this.obraActivaId) ?? this.obras[0]
    const enCurso = o.semanas.find((s) => s.estado === 'en_curso')
    const hoy = new Date().toISOString().slice(0, 10)
    return {
      id: o.id, nombre: o.nombre, presupuesto: o.presupuesto,
      // 0 = la obra aún no inicia; la barra lateral lo dice con palabras
      semana: enCurso?.numero ?? (hoy < o.inicio ? 0 : o.semanas.length),
      semanasTotales: o.semanas.length,
    }
  }

  async elegirObra(id: string): Promise<ObraActiva> {
    await espera(120)
    if (!this.obras.some((o) => o.id === id)) throw new ErrorApi('no_existe', 'Esa obra no existe.')
    this.obraActivaId = id
    return this.obraActiva()
  }

  async listarObras(): Promise<Obra[]> {
    await espera(100)
    return this.obras.map((o) => ({ ...o, semanas: [...o.semanas] }))
  }

  async crearObra(datos: { nombre: string; presupuesto: string; inicio: string; fin: string }): Promise<Obra> {
    await espera(300)
    const nombre = datos.nombre.trim()
    // HU-01: nombre, fecha de inicio y fecha de fin son obligatorios
    if (nombre.length < 3) throw new ErrorApi('nombre', 'El nombre de la obra es obligatorio.')
    if (!datos.inicio || !datos.fin) throw new ErrorApi('fechas', 'Las fechas de inicio y fin son obligatorias.')
    // HU-01: la fecha de fin no puede ser anterior a la de inicio
    if (datos.fin < datos.inicio) throw new ErrorApi('fin', 'La fecha de fin no puede ser anterior a la de inicio.')
    if (this.obras.some((o) => o.nombre.toLowerCase() === nombre.toLowerCase())) {
      throw new ErrorApi('nombre', 'Ya existe una obra con ese nombre.')
    }
    const obra: Obra = {
      id: `o${this.obras.length + 1}-${Date.now()}`,
      nombre,
      presupuesto: datos.presupuesto.trim(),
      inicio: datos.inicio,
      fin: datos.fin,
      estado: 'en_ejecucion', // HU-01: el proyecto nace en ejecución
      semanas: generarSemanas(datos.inicio, datos.fin), // HU-03: solas
    }
    this.obras = [...this.obras, obra]
    return obra
  }

  async listarProveedores(busqueda = ''): Promise<Proveedor[]> {
    const proveedores = await this.cargarProveedores()
    await espera()
    const q = busqueda.trim().toLowerCase()
    if (!q) return [...proveedores]
    // HU-29: se busca por razón social o por RUC
    return proveedores.filter(
      (p) => p.razonSocial.toLowerCase().includes(q) || p.ruc.includes(q),
    )
  }

  async proveedoresParaComprar(): Promise<Proveedor[]> {
    const proveedores = await this.cargarProveedores()
    await espera()
    // HU-29: un proveedor dado de baja no aparece al registrar compras nuevas
    return proveedores.filter((p) => p.estado !== 'de_baja')
  }

  async crearProveedor(datos: { ruc: string; razonSocial: string; validadoEnSunat?: boolean }): Promise<Proveedor> {
    const proveedores = await this.cargarProveedores()
    await espera(260)
    const ruc = datos.ruc.trim()
    const razonSocial = datos.razonSocial.trim()
    // HU-30: exige RUC y razón social como mínimo
    // D-C5 (ratificada por el PO): RUC de 11 dígitos o DNI de 8. El DNI queda «sin
    // validar» y nunca se consulta en SUNAT (persona natural sin RUC).
    const esDni = /^\d{8}$/.test(ruc)
    if (!esDni && !/^\d{11}$/.test(ruc)) {
      throw new ErrorApi('ruc_invalido', 'Ingresa un RUC de 11 dígitos o un DNI de 8.')
    }
    if (razonSocial.length < 3) throw new ErrorApi('razon_social', 'La razón social es obligatoria.')
    // HU-30: no se admiten dos proveedores con el mismo RUC
    if (proveedores.some((p) => p.ruc === ruc)) {
      throw new ErrorApi('ruc_duplicado', 'Ya existe un proveedor con ese RUC.')
    }
    const nuevo: Proveedor = {
      id: `p${proveedores.length + 1}-${Date.now()}`,
      ruc,
      razonSocial,
      // el veredicto viene de la consulta al micro de integraciones (HU-31);
      // si el micro o SUNAT no respondieron, queda «sin validar» y no bloquea
      estado: datos.validadoEnSunat && !esDni ? 'validado' : 'sin_validar',
      diasEntrega: null,
      comprasRegistradas: 0,
      ultimaCompra: null,
    }
    proveedores.unshift(nuevo)
    return nuevo
  }

  async aplicarImportacionS10(filas: { codigo: string; descripcion: string; unidad: string; tipo: string }[]) {
    await espera(400)
    const existentes = new Set(INSUMOS_S10.map((i) => i.codigo))
    const creados = filas.filter((f) => !existentes.has(f.codigo)).length
    return { creados, actualizados: 0, sinCambios: filas.length - creados, advertencias: [] as string[] }
  }

  async listarInsumos(): Promise<InsumoCatalogo[]> {
    await espera(80)
    // Los 237 materiales REALES de la Relación de insumos del S10. El acero es
    // la única excepción: se planifica en kg y se compra en varillas (factor).
    // Cuando llegue la lista de nombres normalizados del cliente, se reemplaza
    // la descripción aquí sin tocar ninguna pantalla.
    return INSUMOS_S10.filter((i) => i.tipo === 'material').map((i) => ({
      id: i.codigo,
      codigo: i.codigo,
      descripcion: i.descripcion,
      unidadCompra: i.codigo === '0203020004' ? 'varilla 9 m' : i.unidad,
    }))
  }

  /* El maestro vive en la capa de API, no en la pantalla: cuando exista la
     base, estos métodos son los endpoints del módulo catálogo y la pantalla
     no cambia. Se carga perezoso (2 243 filas van en su propio chunk). */
  private maestro: InsumoMaestro[] | null = null

  private async cargarMaestro(): Promise<InsumoMaestro[]> {
    if (!this.maestro) {
      const { CATALOGO } = await import('../datos/catalogo-cliente')
      this.maestro = CATALOGO.map((i) => ({
        codigo: i.codigo,
        nombre: i.nombre,
        um: i.um,
        medida: '',
        marca: i.marcas.toUpperCase() === 'SIN MARCA' ? '' : i.marcas,
        precioPromedio: i.precioPromedio,
        estado: 'activo' as const,
        creadoEl: '',
      }))
    }
    return this.maestro
  }

  async listarMaestro(): Promise<InsumoMaestro[]> {
    const maestro = await this.cargarMaestro()
    await espera(80)
    return maestro.map((i) => ({ ...i }))
  }

  async crearInsumoCatalogo(datos: { nombre: string; um: string; medida?: string; marca?: string }): Promise<InsumoMaestro> {
    const maestro = await this.cargarMaestro()
    await espera(220)
    const nombre = datos.nombre.trim().toUpperCase()
    const medida = (datos.medida ?? '').trim().toUpperCase()
    const marca = (datos.marca ?? '').trim().toUpperCase()
    // HU-08: material y unidad de medida son obligatorios
    if (nombre.length < 3) throw new ErrorApi('nombre', 'El material es obligatorio.')
    if (!datos.um.trim()) throw new ErrorApi('um', 'La unidad de medida es obligatoria.')
    // HU-08: no se admite un insumo con el mismo material, medida y marca que otro
    const nombreCompleto = medida ? `${nombre} ${medida}` : nombre
    const choque = maestro.some((i) =>
      (`${i.nombre}|${i.medida}|${i.marca}` === `${nombre}|${medida}|${marca}`)
      || i.nombre.toUpperCase() === nombreCompleto)
    if (choque) {
      throw new ErrorApi('duplicado', 'Ya existe un insumo con ese material, medida y marca.')
    }
    // HU-08: el sistema genera un código único e irrepetible (correlativo del maestro)
    const mayor = Math.max(...maestro.map((i) => Number(i.codigo) || 0))
    const nuevo: InsumoMaestro = {
      codigo: String(mayor + 1),
      nombre, medida, marca,
      um: datos.um,
      precioPromedio: 0, // lo irán fijando las compras
      // creado a mano queda «por verificar» hasta que el residente lo revisa
      estado: 'por_verificar',
      creadoEl: new Date().toISOString().slice(0, 10),
    }
    maestro.unshift(nuevo)
    return { ...nuevo }
  }

  async editarInsumo(codigo: string, datos: { nombre?: string; marca?: string; verificar?: boolean }): Promise<InsumoMaestro> {
    const maestro = await this.cargarMaestro()
    await espera(200)
    const ins = maestro.find((i) => i.codigo === codigo)
    if (!ins) throw new ErrorApi('no_existe', 'Ese código no existe en el catálogo.')
    if (datos.nombre !== undefined) {
      const nombre = datos.nombre.trim().toUpperCase()
      if (nombre.length < 3) throw new ErrorApi('nombre', 'El material es obligatorio.')
      ins.nombre = nombre
    }
    if (datos.marca !== undefined) ins.marca = datos.marca.trim().toUpperCase()
    if (datos.verificar) ins.estado = 'activo'
    return { ...ins }
  }

  async registrarCompra(datos: {
    proveedorId: string
    tipoComprobante: TipoComprobante
    numeroComprobante: string
    fecha: string
    lineas: LineaCompra[]
  }): Promise<Compra> {
    await espera(300)
    const prov = (await this.cargarProveedores()).find((p) => p.id === datos.proveedorId)
    // HU-32: exige proveedor, fecha, tipo y número de comprobante, y al menos
    // un insumo con cantidad y precio
    if (!prov) throw new ErrorApi('proveedor', 'Elige el proveedor de la compra.')
    if (prov.estado === 'de_baja') throw new ErrorApi('proveedor', 'Ese proveedor está de baja.')
    if (!datos.fecha) throw new ErrorApi('fecha', 'La fecha es obligatoria.')
    const numero = datos.numeroComprobante.trim().toUpperCase()
    if (!numero) throw new ErrorApi('comprobante', 'El número de comprobante es obligatorio.')
    if (!datos.lineas.length) throw new ErrorApi('sin_lineas', 'La compra necesita al menos un insumo.')
    if (datos.lineas.some((l) => l.cantidad <= 0 || l.precioUnitario <= 0)) {
      throw new ErrorApi('linea_invalida', 'Cada línea necesita cantidad y precio mayores a cero.')
    }
    // HU-32: no se admiten dos comprobantes con el mismo tipo y número del mismo proveedor
    const repetido = this.compras.some(
      (c) => c.proveedorId === datos.proveedorId
        && c.tipoComprobante === datos.tipoComprobante
        && c.numeroComprobante === numero,
    )
    if (repetido) {
      throw new ErrorApi('comprobante_duplicado',
        'Ese comprobante ya está registrado para este proveedor.')
    }
    // HU-32: registra base imponible, impuesto y total (precios con IGV, 18 %)
    const total = datos.lineas.reduce((s, l) => s + l.cantidad * l.precioUnitario, 0)
    const base = total / 1.18
    const compra: Compra = {
      id: `c${this.compras.length + 1}-${Date.now()}`,
      obraId: this.obraActivaId,
      proveedorId: datos.proveedorId,
      proveedorRazonSocial: prov.razonSocial,
      tipoComprobante: datos.tipoComprobante,
      numeroComprobante: numero,
      fecha: datos.fecha,
      estado: 'registrada', // el stock se mueve recién con la recepción
      lineas: datos.lineas,
      totales: {
        base: Math.round(base * 100) / 100,
        igv: Math.round((total - base) * 100) / 100,
        total: Math.round(total * 100) / 100,
      },
      adjunto: false,
    }
    this.compras = [compra, ...this.compras]
    prov.comprasRegistradas += 1
    prov.ultimaCompra = datos.fecha
    // HU-26: el aviso se marca como atendido al registrarse una compra del insumo
    for (const l of datos.lineas) {
      this.alertasAtendidas.set(l.insumoId, numero)
    }
    return compra
  }

  async listarCompras(): Promise<Compra[]> {
    await espera()
    // regla 9: solo las compras de la obra activa
    return this.compras.filter((c) => c.obraId === this.obraActivaId)
  }

  /** HU-35: el estado de la compra nunca se setea a mano: se deriva de lo recibido */
  private estadoDeCompra(c: Compra): Compra['estado'] {
    const rec = this.recibido[c.id] ?? {}
    const tot = c.lineas.reduce((s, l) => s + l.cantidad, 0)
    const ya = c.lineas.reduce((s, l) => s + (rec[l.insumoId] ?? 0), 0)
    return ya <= 0 ? 'registrada' : ya < tot ? 'recibida_parcial' : 'recibida'
  }

  async detalleRecepcion(compraId: string): Promise<LineaRecepcion[]> {
    await espera(120)
    const c = this.compras.find((x) => x.id === compraId)
    if (!c) throw new ErrorApi('no_existe', 'Esa compra no existe.')
    const rec = this.recibido[c.id] ?? {}
    return c.lineas.map((l) => ({
      insumoId: l.insumoId,
      descripcion: l.descripcion,
      unidadCompra: l.unidadCompra,
      precioUnitario: l.precioUnitario,
      pedido: l.cantidad,
      recibido: rec[l.insumoId] ?? 0,
      pendiente: Math.max(0, l.cantidad - (rec[l.insumoId] ?? 0)),
    }))
  }

  async registrarRecepcion(compraId: string, lineas: { insumoId: string; cantidad: number }[]): Promise<void> {
    await espera(280)
    const c = this.compras.find((x) => x.id === compraId)
    if (!c) throw new ErrorApi('no_existe', 'Esa compra no existe.')
    const conCantidad = lineas.filter((l) => l.cantidad > 0)
    if (!conCantidad.length) throw new ErrorApi('vacia', 'La recepción necesita al menos una cantidad mayor a cero.')
    const rec = (this.recibido[c.id] ??= {})
    // HU-35: no se puede recibir más de lo comprado de cada insumo
    for (const l of conCantidad) {
      const linea = c.lineas.find((x) => x.insumoId === l.insumoId)
      if (!linea) throw new ErrorApi('linea', 'Ese insumo no está en la compra.')
      const pendiente = linea.cantidad - (rec[l.insumoId] ?? 0)
      if (l.cantidad > pendiente) {
        throw new ErrorApi('excede',
          `De ${linea.descripcion} quedan ${pendiente.toLocaleString('es-PE')} ${linea.unidadCompra} por recibir; no se puede recibir más de lo comprado.`)
      }
    }
    // HU-35: cada recepción genera el INGRESO al almacén por la cantidad recibida
    for (const l of conCantidad) {
      const linea = c.lineas.find((x) => x.insumoId === l.insumoId)!
      rec[l.insumoId] = (rec[l.insumoId] ?? 0) + l.cantidad
      const numero = this.movimientos.length + 1
      this.movimientos = [...this.movimientos, {
        id: `M-${String(numero).padStart(4, '0')}`,
        obraId: c.obraId,
        uuidCliente: `web-rec-${compraId}-${Date.now()}-${l.insumoId}`,
        fechaServidor: new Date().toISOString().slice(0, 19),
        almacen: 'Almacén central',
        insumoId: l.insumoId,
        descripcion: linea.descripcion,
        unidad: linea.unidadCompra,
        tipo: 'ingreso',
        cantidad: l.cantidad,
        // NIC 2.11: el IGV de una factura es crédito fiscal y no forma parte del
        // costo del inventario; en boleta no se recupera, así que sí es costo
        costoUnitario: c.tipoComprobante === 'factura'
          ? Math.round((linea.precioUnitario / 1.18) * 100) / 100
          : linea.precioUnitario,
        motivo: `Recepción de la compra ${c.numeroComprobante} · ${c.proveedorRazonSocial}`,
        autor: 'almacenero (registrado desde la web)',
        reversaA: null,
        anuladoPor: null,
      }]
    }
    c.estado = this.estadoDeCompra(c)
  }

  /* ── kárdex (HU-43 · HU-45): el saldo se deriva, el libro no se toca ── */
  private movimientos: Movimiento[] = [...MOVIMIENTOS]

  /** los de la obra activa (regla 9) */
  private movsDeObra(): Movimiento[] {
    return this.movimientos.filter((m) => m.obraId === this.obraActivaId)
  }

  /** anuladoPor se calcula al leer: el original nunca se modifica en el libro */
  private conAnulaciones(): Movimiento[] {
    return this.movsDeObra().map((m) => ({
      ...m,
      anuladoPor: this.movimientos.find((r) => r.reversaA === m.id)?.id ?? null,
    }))
  }

  async listarMovimientos(): Promise<Movimiento[]> {
    await espera(140)
    return this.conAnulaciones().sort((a, b) => b.fechaServidor.localeCompare(a.fechaServidor))
  }

  async consultarStock(): Promise<StockInsumo[]> {
    await espera(180)
    const porInsumo = new Map<string, Movimiento[]>()
    for (const m of this.movsDeObra()) {
      porInsumo.set(m.insumoId, [...(porInsumo.get(m.insumoId) ?? []), m])
    }
    return Array.from(porInsumo.entries()).map(([insumoId, movs]) => {
      const almacenes = new Map<string, number>()
      for (const m of movs) {
        almacenes.set(m.almacen, (almacenes.get(m.almacen) ?? 0) + efecto(m, this.movimientos))
      }
      const porAlmacen = Array.from(almacenes.entries()).map(([almacen, saldo]) => ({ almacen, saldo }))
      const saldoTotal = porAlmacen.reduce((s, a) => s + a.saldo, 0)
      const punto = PUNTO_REPOSICION[insumoId] ?? null
      return {
        insumoId,
        descripcion: movs[0].descripcion,
        unidad: movs[0].unidad,
        porAlmacen,
        saldoTotal,
        puntoReposicion: punto,
        bajoPunto: punto !== null && saldoTotal < punto,
        ultimoMovimiento: movs.map((m) => m.fechaServidor).sort()[movs.length - 1],
      }
    }).sort((a, b) => Number(b.bajoPunto) - Number(a.bajoPunto))
  }

  async revertirMovimiento(id: string, motivo: string): Promise<Movimiento> {
    await espera(260)
    const original = this.movimientos.find((m) => m.id === id)
    if (!original) throw new ErrorApi('no_existe', 'Ese movimiento no existe.')
    if (original.tipo === 'reverso') {
      throw new ErrorApi('es_reverso', 'Un reverso no se revierte: si la corrección estuvo mal, se registra el movimiento correcto.')
    }
    if (this.movimientos.some((r) => r.reversaA === id)) {
      throw new ErrorApi('ya_anulado', 'Ese movimiento ya fue corregido con un reverso.')
    }
    if (motivo.trim().length < 5) {
      throw new ErrorApi('motivo', 'El motivo es obligatorio: sin motivo no hay rastro que valga.')
    }
    // el reverso hereda cantidad y costo congelado del original; nada se edita
    const numero = this.movimientos.length + 1
    const reverso: Movimiento = {
      id: `M-${String(numero).padStart(4, '0')}`,
      obraId: original.obraId,
      uuidCliente: `web-rev-${Date.now()}`,
      fechaServidor: new Date().toISOString().slice(0, 19),
      almacen: original.almacen,
      insumoId: original.insumoId,
      descripcion: original.descripcion,
      unidad: original.unidad,
      tipo: 'reverso',
      cantidad: original.cantidad,
      costoUnitario: original.costoUnitario,
      motivo: motivo.trim(),
      autor: 'tú (web de oficina)',
      reversaA: original.id,
      anuladoPor: null,
    }
    this.movimientos = [...this.movimientos, reverso]
    return reverso
  }

  async avancePorInsumo(): Promise<AvanceInsumo[]> {
    await espera()
    // el avance es de la obra: una obra recién creada aún no tiene requerimiento
    return this.obraActivaId === 'o1' ? [...AVANCE] : []
  }

  /* ── HU-26 · HU-27: el aviso lo dispara el sistema, no una persona ── */

  /** cómo se configuró el punto de cada insumo (HU-26: mínimo absoluto o
      porcentaje del requerimiento) y qué exige la fase en curso (HU-27) */
  private static CONFIG_REPOSICION: Record<string, { criterioPunto: string; exigeFase: number; fase: string }> = {
    '0221000000': { criterioPunto: 'mínimo absoluto configurado: 250 bls', exigeFase: 480, fase: 'vaciados de noviembre (Estructuras)' },
    '0203020004': { criterioPunto: '1 % del requerimiento total (4 040 varillas)', exigeFase: 600, fase: 'habilitado de los pisos 5 y 6' },
  }

  /** aviso → compra que lo atendió (HU-26: se marca atendido al comprar) */
  private alertasAtendidas = new Map<string, string>()

  async alertasReposicion(): Promise<AlertaReposicion[]> {
    const stock = await this.consultarStock()
    // HU-26: un mismo insumo no genera avisos repetidos mientras siga bajo el
    // punto: hay a lo sumo UNA alerta por insumo, activa o atendida
    return stock
      .filter((s) => s.puntoReposicion !== null && s.saldoTotal < s.puntoReposicion)
      .map((s) => {
        const conf = ClienteSimulado.CONFIG_REPOSICION[s.insumoId]
        const avance = AVANCE.find((a) => a.insumoId === s.insumoId)
        const falta = avance?.saldoPorComprar ?? 0
        // HU-27: el menor entre lo que falta por comprar y lo que exige la fase
        const sugerencia = conf ? Math.min(falta, conf.exigeFase) : falta
        const atendidaPor = this.alertasAtendidas.get(s.insumoId) ?? null
        return {
          insumoId: s.insumoId,
          descripcion: s.descripcion,
          unidad: s.unidad,
          saldo: s.saldoTotal,
          punto: s.puntoReposicion!,
          criterioPunto: conf?.criterioPunto ?? 'mínimo absoluto',
          faltaPorComprar: falta,
          sugerencia,
          criterioSugerencia: conf
            ? `el menor entre lo que falta por comprar (${falta.toLocaleString('es-PE')} ${s.unidad}) y lo que exige la fase en curso: ${conf.fase} (${conf.exigeFase.toLocaleString('es-PE')} ${s.unidad})`
            : 'lo que falta por comprar',
          estado: atendidaPor ? 'atendida' as const : 'activa' as const,
          atendidaPor,
        }
      })
  }

  async resumenTablero(): Promise<ResumenTablero> {
    await espera(220)
    const activa = await this.obraActiva()
    if (this.obraActivaId !== 'o1') {
      // una obra recién creada todavía no tiene requerimiento ni gasto
      return {
        gastoTotal: 0,
        avancePlazoPct: 0,
        semana: activa.semana,
        semanasTotales: activa.semanasTotales,
        familias: [
          { familia: 'materiales', etiqueta: 'Materiales', gastado: 0, planificado: 0, pct: 0 },
          { familia: 'herramientas', etiqueta: 'Herramientas y equipos', gastado: 0, planificado: 0, pct: 0 },
          { familia: 'horas_hombre', etiqueta: 'Horas hombre', gastado: 0, planificado: 0, pct: 0 },
        ],
        insumos: [],
        noPlanificados: [],
      }
    }
    return {
      gastoTotal: 373060,
      avancePlazoPct: 26.9,
      semana: 14,
      semanasTotales: 52,
      familias: [
        { familia: 'materiales', etiqueta: 'Materiales', gastado: 214680, planificado: 768310, pct: 27.9 },
        { familia: 'herramientas', etiqueta: 'Herramientas y equipos', gastado: 9460, planificado: 33310, pct: 28.4 },
        { familia: 'horas_hombre', etiqueta: 'Horas hombre', gastado: 148920, planificado: 542998, pct: 27.4 },
      ],
      insumos: [...AVANCE],
      // HU-57: lo no planificado se informa aparte y no distorsiona la desviación
      noPlanificados: [
        { descripcion: 'IMPERMEABILIZANTE PARA CISTERNA', gastado: 1240, motivo: 'No estaba en el requerimiento del S10; lo agregó el residente el 18 oct' },
      ],
    }
  }
}

/** instancia única que usan las pantallas; cambiarla por ClienteHttp es una línea */
/* ───────────────────────────── API real ─────────────────────────────
   HT-04: lo que la API de Adiel ya expone va al servidor; lo que todavía no
   (catálogo, compras con insumos, almacén, costo, reposición) sigue simulado
   hasta que exista su endpoint. Cada método que pasa al servidor se mueve aquí
   sin tocar ninguna pantalla. */
class ClienteHttp extends ClienteSimulado {
  override obraActiva() { return obrasHttp.activa() }
  override elegirObra(id: string) { return obrasHttp.elegir(id) }
  override listarObras() { return obrasHttp.listar() }
  override crearObra(datos: { nombre: string; presupuesto: string; inicio: string; fin: string }) {
    return obrasHttp.crear(datos)
  }
  override listarProveedores(busqueda = '') { return proveedoresHttp.listar(busqueda) }
  override proveedoresParaComprar() { return proveedoresHttp.paraComprar() }
  override crearProveedor(datos: { ruc: string; razonSocial: string; validadoEnSunat?: boolean }) {
    // el veredicto de SUNAT lo decide el servidor (HU-31), no la pantalla
    return proveedoresHttp.crear(datos.ruc, datos.razonSocial)
  }
  override listarMaestro() { return catalogoHttp.maestro() }
  override listarInsumos() { return catalogoHttp.insumos() }
  override crearInsumoCatalogo(datos: { nombre: string; um: string; medida?: string; marca?: string }) {
    return catalogoHttp.crear(datos)
  }
  override editarInsumo(codigo: string, datos: { nombre?: string; marca?: string; verificar?: boolean }) {
    return catalogoHttp.editar(codigo, datos)
  }
  override aplicarImportacionS10(filas: { codigo: string; descripcion: string; unidad: string; tipo: string }[]) {
    return catalogoHttp.aplicarS10(filas)
  }
}

export const api: ClienteSigro = usaApiReal ? new ClienteHttp() : new ClienteSimulado()
