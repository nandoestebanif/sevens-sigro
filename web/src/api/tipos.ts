/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · tipos del dominio
   Espejo de docs/modelo-datos.md. Si un campo no está en el modelo, no está
   aquí; si se necesita uno nuevo, primero se cambia el modelo y se avisa en el
   Pull Request. El backend es la fuente de verdad: el cliente nunca calcula
   saldos ni estados, solo muestra lo que el servidor devolvió.
   ───────────────────────────────────────────────────────────────────────── */

export type Rol = 'almacenero' | 'ssoma' | 'residente' | 'administrador' | 'gerente'

export type EstadoProveedor = 'validado' | 'sin_validar' | 'de_baja'

export interface Proveedor {
  id: string
  ruc: string // único en todo el sistema; lo garantiza el servidor
  razonSocial: string
  estado: EstadoProveedor
  /** días que tarda en entregar; alimenta el punto de reposición */
  diasEntrega: number | null
  comprasRegistradas: number
  ultimaCompra: string | null // ISO; null si nunca se le compró
}

export interface Insumo {
  id: string
  codigo: string // único global: el mismo en todas las obras
  descripcion: string
  unidadS10: string
  unidadCompra: string
  factorConversion: number | null
  tipo: 'material' | 'mano_obra' | 'equipo' | 'subcontrato'
  verificado: boolean // false cuando lo creó el almacenero en obra
}

export interface LineaCompra {
  insumoId: string
  descripcion: string // denormalizada para mostrar sin otra consulta
  cantidad: number
  unidadCompra: string
  precioUnitario: number
}

export type EstadoCompra = 'registrada' | 'recibida_parcial' | 'recibida'

export type TipoComprobante = 'factura' | 'boleta'

export interface TotalesCompra {
  base: number // base imponible
  igv: number
  total: number
}

export interface Compra {
  id: string
  proveedorId: string
  proveedorRazonSocial: string // denormalizada para listar sin otra consulta
  tipoComprobante: TipoComprobante
  numeroComprobante: string // serie-número, p. ej. F001-4512
  fecha: string // ISO (yyyy-mm-dd)
  estado: EstadoCompra // lo deriva el servidor de las recepciones
  lineas: LineaCompra[]
  totales: TotalesCompra // registrados con la compra (criterio de HU-32)
  adjunto: boolean // la foto de la factura, una por compra
}

/** HU-35: pedido, recibido y pendiente por insumo de una compra.
    Los tres números vienen calculados del servidor. */
export interface LineaRecepcion {
  insumoId: string
  descripcion: string
  unidadCompra: string
  precioUnitario: number
  pedido: number
  recibido: number
  pendiente: number
}

/** Insumo tal como lo necesita el formulario de compra. */
export interface InsumoCatalogo {
  id: string
  codigo: string
  descripcion: string
  unidadCompra: string
}

/** HU-08: insumo del catálogo maestro de la empresa, tal como lo mantiene la
    administradora. El código lo genera el servidor y no se edita nunca.
    «por_verificar» marca a los creados en obra hasta que el residente los
    revisa (respuesta 3 del cliente). Del catálogo nada se borra: la baja es
    un estado con fecha, porque los movimientos históricos lo referencian. */
export interface InsumoMaestro {
  codigo: string
  nombre: string
  um: string
  medida: string // vacía cuando el tipo no la lleva
  marca: string // opcional; se exige solo en los tipos que la requieren
  precioPromedio: number
  estado: 'activo' | 'por_verificar'
  creadoEl: string // ISO
}

/** Fila del avance de compra y consumo (HU-23) y del saldo por comprar (HU-24).
    Todos los números vienen calculados del servidor. */
export interface AvanceInsumo {
  insumoId: string
  codigo: string
  descripcion: string
  unidadS10: string
  /** HU-23: todo se expresa en la unidad de compra */
  unidadCompra: string
  fase: string
  critico: boolean
  planificadoCantidad: number
  planificadoImporte: number
  compradoCantidad: number
  compradoPct: number
  recibidoPct: number
  consumidoPct: number
  gastado: number
  /** HU-24: falta por comprar = requerido − comprado, nunca negativa */
  saldoPorComprar: number
  desviacionPuntos: number // consumido − avance del plazo
}

/** HU-57: los insumos no planificados se informan aparte,
    para no distorsionar la desviación de los planificados. */
export interface InsumoNoPlanificado {
  descripcion: string
  gastado: number
  motivo: string
}

export interface GastoFamilia {
  familia: 'materiales' | 'herramientas' | 'horas_hombre'
  etiqueta: string
  gastado: number
  planificado: number
  pct: number
}

export interface ResumenTablero {
  gastoTotal: number
  avancePlazoPct: number
  semana: number
  semanasTotales: number
  familias: GastoFamilia[]
  insumos: AvanceInsumo[]
  noPlanificados: InsumoNoPlanificado[]
}

/* ── almacén · kárdex (HU-43 · HU-45) ──
   El movimiento es un libro que no se borra (regla 1): no existe editar ni
   eliminar; la corrección es un movimiento de reverso que apunta al original.
   El saldo NO es un campo (regla 2): se deriva de los movimientos y lo
   calcula el servidor (aquí, la capa de API). */
export type TipoMovimiento = 'ingreso' | 'salida' | 'reverso'

export interface Movimiento {
  id: string // correlativo del servidor, p. ej. M-0007
  uuidCliente: string // idempotencia: un reenvío con el mismo uuid no duplica
  fechaServidor: string // ISO con hora; la hora que vale para auditar
  almacen: string
  insumoId: string
  descripcion: string
  unidad: string
  tipo: TipoMovimiento
  cantidad: number // siempre positiva; el efecto en el saldo lo da el tipo
  costoUnitario: number // congelado al instante del movimiento (NIC 2)
  motivo: string
  autor: string
  /** solo en un reverso: el movimiento que anula */
  reversaA: string | null
  /** derivado al consultar (el original jamás se modifica):
      id del reverso que lo anuló, si existe */
  anuladoPor: string | null
}

export interface SaldoAlmacen {
  almacen: string
  saldo: number
}

export interface StockInsumo {
  insumoId: string
  descripcion: string
  unidad: string
  porAlmacen: SaldoAlmacen[]
  saldoTotal: number
  puntoReposicion: number | null
  bajoPunto: boolean
  ultimoMovimiento: string // ISO del último movimiento considerado
}

export interface ObraActiva {
  id: string
  nombre: string
  presupuesto: string
  semana: number
  semanasTotales: number
}
