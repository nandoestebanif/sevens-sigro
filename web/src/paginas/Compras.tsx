/* ─────────────────────────────────────────────────────────────────────────────
   HU-32 · Como residente de obra, quiero registrar la compra con proveedor,
           comprobante y productos, para dejar constancia de lo adquirido.

   Criterios de la tarjeta y dónde se cumplen:
   - Exige proveedor, fecha, tipo y número de comprobante, y al menos un insumo
     con cantidad y precio → lo valida la capa de API; aquí solo se muestra.
   - Registra base imponible, impuesto y total → desglose calculado (IGV 18 %).
   - No se admiten dos comprobantes con el mismo tipo y número del mismo
     proveedor → lo rechaza la capa de API con su mensaje.
   - La compra no modifica el stock por sí sola → letrero fijo en el formulario
     y estado «pendiente de recepción» en la lista.
   Cierra además el criterio pendiente de HU-30: el alta rápida de proveedor se
   abre desde aquí y el creado queda seleccionado en la compra en curso.
   Lienzo W6 «Compras» (estilo vidrio): la lista a la izquierda, el registro a la derecha.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useMemo, useState } from 'react'
import { useEnVivo } from '../formularios/enVivo'
import { aNumero, cantidadPositiva, minimo } from '../formularios/reglas'
import { useSearchParams } from 'react-router-dom'
import { ErrorApi, api } from '../api/cliente'
import type { Compra, ComprobanteCompra, EstadoCompra, InsumoCatalogo, LineaCompra, LineaRecepcion, Proveedor, TipoComprobante, VerificacionRecepcion } from '../api/tipos'
import { Campo, Chip, Modal, Vacio, type TonoChip } from '../ui/base'
import { Aviso, Chips, Encabezado, Pildora, Segmentado, TituloSeccion, Vidrio } from '../ui/piezas'
import { AccionesFila, TablaDatos } from '../ui/TablaDatos'
import { puedo } from '../api/permisos'
import { BuscarOpcion } from '../ui/BuscarOpcion'
import { usaApiReal } from '../api/http'
import { desvioPct, precios, type PrecioReferencia } from '../api/oficina'
import { AltaProveedor } from './Proveedores'
import { useAvisos } from '../ui/Avisos'
import { EsqueletoTabla } from '../ui/Esqueleto'

/* HU-35: el estado lo deriva el servidor de las recepciones; aquí solo se pinta */
const ESTADO_COMPRA: Record<EstadoCompra, { texto: string; tono: TonoChip }> = {
  registrada: { texto: 'Pendiente de recepción', tono: 'aviso' },
  recibida_parcial: { texto: 'Recepción parcial', tono: 'aviso' },
  recibida: { texto: 'Recibida', tono: 'bien' },
  anulada: { texto: 'Anulada', tono: 'grave' },
}

/* ── HU-35 · registrar la recepción (total por defecto, el caso habitual) ── */
function RecepcionCompra({ compra, enCerrar, enListo }: {
  compra: Compra
  enCerrar: () => void
  enListo: () => void
}) {
  const [detalle, setDetalle] = useState<LineaRecepcion[] | null>(null)
  const [cantidades, setCantidades] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  // HU-36: si con esta recepción se pasa lo requerido en el S10, se avisa ANTES de guardar y se pide el motivo
  const [exceso, setExceso] = useState<VerificacionRecepcion | null>(null)
  const [motivo, setMotivo] = useState('')

  useEffect(() => {
    setError('')
    api.detalleRecepcion(compra.id).then((d) => {
      setDetalle(d)
      // por defecto se propone la recepción TOTAL (criterio de la tarjeta)
      setCantidades(Object.fromEntries(d.map((l) => [l.insumoId, String(l.pendiente)])))
    }).catch((e) => {
      setDetalle([])
      setError(e instanceof ErrorApi ? e.message : 'No se pudo consultar el detalle de recepción.')
    })
  }, [compra.id])

  const lineasARecibir = () =>
    (detalle ?? []).map((l) => ({ insumoId: l.insumoId, cantidad: Number(String(cantidades[l.insumoId] ?? '').replace(',', '.')) || 0 }))

  const recibir = async () => {
    if (!detalle) return
    setGuardando(true)
    setError('')
    try {
      const lineas = lineasARecibir()
      if (!exceso) {
        // primero se verifica contra lo requerido: avisa, no bloquea (regla 7)
        const v = await api.verificarRecepcion(compra.id, lineas)
        if (v.hayExceso) { setExceso(v); return }
      } else if (motivo.trim().length < 3) {
        setError('Escribe el motivo del exceso: queda en el kárdex junto al ingreso.')
        return
      }
      await api.registrarRecepcion(compra.id, lineas, exceso ? motivo : undefined)
      enListo()
    } catch (e) {
      if (e instanceof ErrorApi) setError(e.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={`Recibir la compra ${compra.numeroComprobante}`} enCerrar={enCerrar}>
      {detalle === null ? (
        <EsqueletoTabla />
      ) : (
        <div className="d-flex flex-column gap-3">
          <p className="small text-body-secondary">
            {compra.proveedorRazonSocial} · {compra.fecha}. Se propone la recepción total;
            si el camión llegó con menos, corrige la cantidad: la compra queda parcial y
            admite más recepciones hasta completarse.
          </p>
          <div className="overflow-x-auto">
          <table className="table table-sm align-middle mb-0" style={{ minWidth: 380 }}>
            <thead>
              <tr className="border-bottom">
                {['Insumo', 'Pedido', 'Recibido', 'Pendiente', 'Recibir ahora'].map((h, i) => (
                  <th key={h} style={{ padding: '7px 6px', fontSize: 11, fontWeight: 700, textTransform: 'uppercase', color: 'var(--tinta-2)', letterSpacing: 0.4, textAlign: i ? 'right' : 'left' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {detalle.map((l) => (
                <tr key={l.insumoId} className="border-bottom">
                  <td className="small fw-semibold py-2 px-1" >{l.descripcion}</td>
                  <td className="n small text-body-secondary text-end py-2 px-1" >{l.pedido.toLocaleString('es-PE')} {l.unidadCompra}</td>
                  <td className="n small text-body-secondary text-end py-2 px-1" >{l.recibido.toLocaleString('es-PE')}</td>
                  <td className="n small fw-bold text-end py-2 px-1" >{l.pendiente.toLocaleString('es-PE')}</td>
                  <td className="text-end" style={{ padding: '9px 0 9px 10px', width: 90 }}>
                    <input
                      aria-label={`Cantidad a recibir de ${l.descripcion}`}
                      value={cantidades[l.insumoId] ?? ''}
                      onChange={(e) => { setExceso(null); setCantidades((c) => ({ ...c, [l.insumoId]: e.target.value })) }}
                      className="form-control text-end"
                      style={{ width: 86, height: 40 }}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
          {exceso && (
            <Aviso tono="aviso" rol="alert">
              <strong>Con esta recepción se pasa lo requerido en el S10.</strong>
              <ul className="ps-3" style={{ margin: '6px 0 8px' }}>
                {exceso.lineas.filter((x) => x.excede).map((x) => (
                  <li key={x.insumoId} className="small">
                    {x.descripcion}: requerido {x.requerido === null ? 'no está en el requerimiento' : `${x.requerido.toLocaleString('es-PE')} ${x.unidad}`},
                    {' '}con esta recepción {x.conEsta.toLocaleString('es-PE')} {x.unidad}
                    {x.exceso !== null && ` (+${x.exceso.toLocaleString('es-PE')})`}
                  </li>
                ))}
              </ul>
              <span className="small">Se puede recibir igual (el requerimiento avisa, no bloquea): indica el motivo.</span>
              <textarea
                aria-label="Motivo del exceso"
                value={motivo}
                maxLength={300}
                onChange={(e) => setMotivo(e.target.value)}
                placeholder="Ej.: reposición por merma en el vaciado de losa"
                className="form-control d-block w-100 mt-2 p-2"
                 style={{ minHeight: 60, boxSizing: 'border-box' }}
              />
            </Aviso>
          )}
          {error && <p role="alert" className="small text-danger fw-semibold">{error}</p>}
          <p className="small text-body-secondary">
            Esta recepción es la que mueve el stock: cada línea genera su ingreso en el
            kárdex (Ingresos y salidas) y recalcula el costo promedio del insumo.
          </p>
          <div className="d-flex flex-wrap gap-2 justify-content-end">
            <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
            <Pildora enClic={recibir} deshabilitado={guardando || !detalle.length}>
              {guardando ? 'Registrando…' : exceso ? 'Recibir con este motivo' : 'Registrar recepción'}
            </Pildora>
          </div>
        </div>
      )}
    </Modal>
  )
}

const soles = (n: number) =>
  n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** celda de formulario que envuelve: crece hasta su proporción y baja de fila
    cuando no cabe, en vez de desbordar la pantalla */
const celdaForm = (crecer: number, base: number): React.CSSProperties => ({
  display: 'flex', flexDirection: 'column', gap: 6,
  flex: `${crecer} 1 ${base}px`, minWidth: 0,
})

const acorta = (texto: string, max = 44) =>
  texto.length > max ? `${texto.slice(0, max - 1)}…` : texto

/** Lo que mide una etiqueta de campo (small × 1.5 + margen): los botones de una fila de campos bajan esto. */
const ALTURA_ETIQUETA = { paddingTop: 'calc(0.875rem * 1.5 + 0.5rem)' }

function Etiqueta({ htmlFor, children }: { htmlFor: string; children: string }) {
  return (
    <label htmlFor={htmlFor} className="form-label d-block small fw-semibold text-body-secondary">
      {children}
    </label>
  )
}

/* ── HU-34 · adjuntar, descargar y anular comprobantes digitales (sin borrar el archivo original) ── */
/** HU-32 · R10: anular una compra registrada por error. No se borra ni se edita: queda listada como «Anulada»
    con su motivo, y deja de contar en «Comprado» del tablero (HU-58). El servidor avisa al residente y al administrador. */
function AnularCompra({ compra, enCerrar, enListo }: { compra: Compra; enCerrar: () => void; enListo: (c: Compra) => void }) {
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const conRecepcion = compra.estado === 'recibida' || compra.estado === 'recibida_parcial'
  const v = useEnVivo({ motivo: minimo(motivo, 5, 'Escribe por qué se anula la compra (mínimo 5 caracteres).') })
  const confirmar = async () => {
    if (v.alGuardar()) return
    setGuardando(true); setError('')
    try {
      enListo(await api.anularCompra(compra.id, motivo))
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo anular la compra. Inténtalo de nuevo.')
    } finally {
      setGuardando(false)
    }
  }
  return (
    <Modal titulo={`Anular la compra ${compra.numeroComprobante}`} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <span className="small text-body-secondary">
          {compra.proveedorRazonSocial} · S/ {compra.totales.total.toFixed(2)}. La compra no se borra: queda en la lista como
          «Anulada», con tu nombre, la fecha y el motivo, y deja de sumar en «Comprado».
        </span>
        {conRecepcion && (
          <Aviso tono="aviso" rol="status">
            Esta compra ya tiene una recepción. Anularla no saca el material del almacén: si no llegó, corrige cada ingreso
            con «Corregir» en Ingresos y salidas.
          </Aviso>
        )}
        <Campo id="motivo-anular-compra" etiqueta="Motivo (mínimo 5 caracteres)" valor={motivo} enCambio={(x) => { setMotivo(x); v.cambio('motivo') }}
          marcador="p. ej. Compra de prueba registrada por error" {...v.campo('motivo')} />
        {error && <div role="alert" className="small text-danger">{error}</div>}
        <div className="d-flex gap-2 justify-content-end">
          <Pildora tipo="secundario" enClic={enCerrar} deshabilitado={guardando}>Cancelar</Pildora>
          <Pildora enClic={() => void confirmar()} deshabilitado={guardando}>{guardando ? 'Anulando…' : 'Anular la compra'}</Pildora>
        </div>
      </div>
    </Modal>
  )
}

function ComprobantesCompra({ compra, enCerrar, enCambio }: {
  compra: Compra
  enCerrar: () => void
  enCambio: () => void
}) {
  const [lista, setLista] = useState<ComprobanteCompra[] | null>(null)
  const [error, setError] = useState('')
  const avisar = useAvisos()
  const [subiendo, setSubiendo] = useState(false)
  const [anulandoId, setAnulandoId] = useState<string | null>(null)
  const [motivoAnulacion, setMotivoAnulacion] = useState('')
  const [procesandoAnulacion, setProcesandoAnulacion] = useState(false)

  const recargar = () => {
    setError('')
    api.listarComprobantes(compra.id).then(setLista).catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudieron cargar los comprobantes.'))
  }
  useEffect(recargar, [compra.id])

  const subir = async (archivo: File | null) => {
    if (!archivo) return
    setSubiendo(true); setError('')
    try {
      await api.subirComprobante(compra.id, archivo)
      avisar(`Comprobante «${archivo.name}» adjuntado correctamente.`)
      recargar()
      enCambio()
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo adjuntar el comprobante.')
    } finally {
      setSubiendo(false)
    }
  }

  const descargar = async (c: ComprobanteCompra) => {
    setError('')
    try {
      const blob = await api.descargarComprobante(compra.id, c.id)
      if (!blob) { avisar('En modo demostración solo se descargan archivos subidos en esta sesión.', 'info'); return }
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = c.nombre
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo descargar el comprobante.')
    }
  }

  const anular = async (c: ComprobanteCompra) => {
    setProcesandoAnulacion(true); setError('')
    try {
      await api.anularComprobante(compra.id, c.id, motivoAnulacion)
      setAnulandoId(null)
      setMotivoAnulacion('')
      avisar(`Comprobante «${c.nombre}» marcado como anulado (se conserva el historial).`)
      recargar()
      enCambio()
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo anular el comprobante.')
    } finally {
      setProcesandoAnulacion(false)
    }
  }

  const peso = (bytes: number) => bytes >= 1024 * 1024
    ? `${(bytes / (1024 * 1024)).toFixed(2)} MB`
    : `${Math.max(1, Math.round(bytes / 1024))} KB`

  return (
    <Modal titulo={`Comprobantes · ${compra.numeroComprobante}`} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <p className="small text-body-secondary m-0">
          {compra.proveedorRazonSocial} · {compra.fecha}. Formatos permitidos: PDF, JPG o PNG (máximo 10 MB).
        </p>

        {compra.estado !== 'anulada' && (
          <label className="d-flex flex-column gap-2 small fw-semibold text-body-secondary">
            Adjuntar archivo
            <input
              type="file"
              accept="application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png"
              disabled={subiendo}
              onChange={(e) => {
                const file = e.target.files?.[0] ?? null
                e.target.value = ''
                void subir(file)
              }}
              className="form-control form-control-sm"
            />
          </label>
        )}

        {subiendo && <span className="small text-body-secondary">Subiendo comprobante…</span>}
        {error && <Aviso tono="grave" rol="alert">{error}</Aviso>}

        {lista === null ? (
          <EsqueletoTabla />
        ) : lista.length === 0 ? (
          <Vacio titulo="Sin comprobantes adjuntos" detalle="Esta compra aún no tiene factura o boleta digital cargada." />
        ) : (
          <ul className="list-unstyled m-0 p-0 d-flex flex-column gap-2">
            {lista.map((c) => (
              <li key={c.id} className="d-flex flex-column gap-2 bg-body-tertiary rounded-4 py-2 px-2" >
                <div className="d-flex justify-content-between gap-2 align-items-center flex-wrap">
                  <strong className="small">{c.nombre}</strong>
                  <Chip tono={c.estado === 'vigente' ? 'bien' : 'grave'}>{c.estado === 'vigente' ? 'Vigente' : 'Anulado'}</Chip>
                </div>
                <div className="n small text-body-secondary">
                  {peso(c.tamanoBytes)} · {c.subidoEn.slice(0, 10)} · huella {c.sha256.slice(0, 12)}…
                </div>
                {c.estado === 'anulado' && c.motivoAnulacion && (
                  <div className="small text-danger">Motivo de anulación: {c.motivoAnulacion}</div>
                )}
                <div className="d-flex gap-2 flex-wrap justify-content-end">
                  <Pildora tipo="secundario" chica enClic={() => void descargar(c)}>Descargar</Pildora>
                  {c.estado === 'vigente' && anulandoId !== c.id && (
                    <Pildora tipo="secundario" chica enClic={() => { setAnulandoId(c.id); setMotivoAnulacion(''); setError('') }}>Anular</Pildora>
                  )}
                </div>
                {anulandoId === c.id && (
                  <div className="d-flex flex-column gap-2 pt-2 border-top" >
                    <Campo
                      id={`motivo-comp-${c.id}`}
                      etiqueta="Motivo de anulación (mínimo 5 caracteres)"
                      valor={motivoAnulacion}
                      enCambio={setMotivoAnulacion}
                      marcador="p. ej. Se subió archivo equivocado"
                    />
                    <div className="d-flex gap-2 justify-content-end">
                      <Pildora tipo="secundario" chica enClic={() => setAnulandoId(null)} deshabilitado={procesandoAnulacion}>Cancelar</Pildora>
                      <Pildora chica enClic={() => void anular(c)} deshabilitado={procesandoAnulacion}>
                        {procesandoAnulacion ? 'Anulando…' : 'Confirmar anulación'}
                      </Pildora>
                    </div>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}

export function Compras() {
  const puedeRegistrar = puedo('compra.registrar')
  // «/compras?nueva=1» (atajo del menú) abre directo el formulario
  const [params, setParams] = useSearchParams()
  const vista: 'lista' | 'nueva' = params.get('nueva') && puedeRegistrar ? 'nueva' : 'lista'
  const irA = (v: 'lista' | 'nueva') => setParams(v === 'nueva' ? { nueva: '1' } : {})
  const [filtro, setFiltro] = useState<'vigentes' | 'por_recibir' | 'anuladas' | 'todas'>('vigentes')
  const [proveedores, setProveedores] = useState<Proveedor[]>([])
  const [insumos, setInsumos] = useState<InsumoCatalogo[]>([])
  const [compras, setCompras] = useState<Compra[]>([])

  // cabecera de la compra
  const [proveedorId, setProveedorId] = useState('')
  const opcionesProveedor = useMemo(
    () => proveedores.map((p) => ({ valor: String(p.id), texto: acorta(p.razonSocial), detalle: `RUC ${p.ruc}` })), [proveedores])
  const [tipo, setTipo] = useState<TipoComprobante>('factura')
  const [numero, setNumero] = useState('')
  // HU-32: hoy en Lima. toISOString() da la fecha UTC: desde las 19:00 de Lima ya proponía el día siguiente
  const hoyLima = () => new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' })
  const [fecha, setFecha] = useState(hoyLima)

  // línea en edición
  const [insumoId, setInsumoId] = useState('')
  const [insumoTexto, setInsumoTexto] = useState('')
  const [cantidad, setCantidad] = useState('')
  const [precio, setPrecio] = useState('')
  const [lineas, setLineas] = useState<LineaCompra[]>([])
  // HU-33 · T-282/T-283: promedio pagado en la obra para comparar el precio (avisa, no bloquea: D13)
  const [ref, setRef] = useState<PrecioReferencia | null>(null)
  const [nota, setNota] = useState('')
  useEffect(() => {
    setRef(null); setNota('')
    if (!usaApiReal || !insumoId) return
    let vivo = true
    precios.referencia(insumoId).then((r) => {
      if (!vivo) return
      setRef(r)
      // se precarga el promedio (con IGV, como se escribe el precio) solo si aún no se escribió nada
      if (r.promedio !== null) setPrecio((p) => (p.trim() ? p : (r.promedio! * 1.18).toFixed(2)))
    }).catch(() => { /* sin referencia no se compara: no se inventa */ })
    return () => { vivo = false }
  }, [insumoId])
  const desvio = desvioPct(aNumero(precio), ref?.promedio ?? null)
  const fuera = desvio !== null && ref !== null && Math.abs(desvio) > ref.limitePct

  const [error, setError] = useState<string | null>(null)
  const [exito, setExito] = useState<Compra | null>(null)
  const [altaAbierta, setAltaAbierta] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [recibiendo, setRecibiendo] = useState<Compra | null>(null)
  const [comprobantesDe, setComprobantesDe] = useState<Compra | null>(null)
  const [anulando, setAnulando] = useState<Compra | null>(null)
  const [archivoComprobante, setArchivoComprobante] = useState<File | null>(null)
  const [inputArchivoClave, setInputArchivoClave] = useState(0)
  const avisar = useAvisos()

  const cargar = () => {
    const alFallar = (e: unknown) => setError(e instanceof ErrorApi ? e.message : 'No se pudieron cargar los datos de compras.')
    api.proveedoresParaComprar().then(setProveedores).catch(alFallar) // excluye a los de baja (HU-29)
    api.listarInsumos().then(setInsumos).catch(alFallar)
    api.listarCompras().then(setCompras).catch(alFallar)
  }
  useEffect(cargar, [])

  const total = useMemo(() => lineas.reduce((s, l) => s + l.cantidad * l.precioUnitario, 0), [lineas])
  const base = total / 1.18

  // validación en vivo: la línea que se arma (insumo, cantidad, precio) y la cabecera de la compra
  const vl = useEnVivo({
    insumo: insumoId ? null : insumoTexto.trim() ? 'Elige un insumo de la lista (código · nombre).' : 'Elige el insumo.',
    cantidad: cantidadPositiva(cantidad),
    precio: cantidadPositiva(precio, 'Escribe el precio unitario.'),
  })
  const vc = useEnVivo({
    proveedor: proveedorId ? null : 'Elige el proveedor (o regístralo con «+ Nuevo»).',
    numero: minimo(numero, 1, 'Escribe el número del comprobante.'),
    fecha: !fecha ? 'Elige la fecha del comprobante.' : fecha > hoyLima() ? 'La fecha no puede ser futura.' : null,
  })

  const agregarLinea = () => {
    const ins = insumos.find((i) => i.id === insumoId)
    const cant = aNumero(cantidad)
    const prec = aNumero(precio)
    if (vl.alGuardar() || !ins) return
    setError(null)
    setLineas((ls) => [...ls, {
      insumoId: ins.id, descripcion: ins.descripcion, unidadCompra: ins.unidadCompra,
      cantidad: cant, precioUnitario: prec,
      ...(fuera ? { fueraDeLoHabitual: true, notaPrecio: nota.trim() || undefined } : {}),
    }])
    setNota('')
    setInsumoId('')
    setInsumoTexto('')
    setCantidad('')
    setPrecio('')
    vl.reiniciar()
  }

  const registrar = async () => {
    if (vc.alGuardar()) return
    if (!lineas.length) { setError('Agrega al menos una línea (insumo, cantidad y precio) antes de registrar.'); return }
    setGuardando(true)
    setError(null)
    setExito(null)
    try {
      const compra = await api.registrarCompra({
        proveedorId, tipoComprobante: tipo, numeroComprobante: numero, fecha, lineas,
      })
      if (archivoComprobante) {
        try {
          await api.subirComprobante(compra.id, archivoComprobante)
          compra.adjunto = true
          compra.comprobantesVigentes = (compra.comprobantesVigentes ?? 0) + 1
        } catch (errAdj) {
          const msg = errAdj instanceof ErrorApi ? errAdj.message : 'Error al subir el archivo.'
          setError(`La compra ${compra.numeroComprobante} se registró, pero no se pudo adjuntar el comprobante: ${msg}`)
        }
      }
      setExito(compra)
      irA('lista')
      avisar(`Compra ${compra.numeroComprobante} registrada. El stock no se movió: se moverá con la recepción, desde la app.`)
      setLineas([])
      setNumero(''); vc.reiniciar()
      setArchivoComprobante(null)
      setInputArchivoClave((k) => k + 1)
      cargar()
    } catch (e) {
      if (e instanceof ErrorApi) setError(e.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <>
    <div className="d-flex flex-column gap-3 pb-4">
      <Encabezado
        titulo="Compras"
        sub="La compra no mueve el stock: lo mueve la recepción en el almacén."
      />

      <Chips>
        <Chip tono="info">Compras y recepciones: las del ambiente de pruebas · insumos del S10 real</Chip>
        <Chip tono="marca">Proveedores: los reales del cliente</Chip>
        <Chip tono="neutro">Una compra no se edita: se anula con motivo y sigue en la lista</Chip>
        <Chip tono="neutro">El IGV se guarda aparte porque no es costo del insumo (NIC 2)</Chip>
        <Chip tono="neutro">Una compra admite varias recepciones hasta completar lo comprado</Chip>
      </Chips>

      {puedeRegistrar && (
        <Segmentado etiqueta="Compras" pestanas valor={vista} enCambio={irA}
          opciones={[{ valor: 'lista', texto: `Compras registradas${compras.length ? ` · ${compras.length}` : ''}` }, { valor: 'nueva', texto: 'Registrar compra' }]} />
      )}

      {vista === 'nueva' && (
        <section className="card card-body shadow d-flex flex-column gap-3 min-w-0 rounded-3 py-3 px-4" aria-label="Registrar la compra"  style={{ flex: '999 1 560px', boxShadow: 'var(--vidrio-sombra-alta)' }}>
          <div className="d-flex flex-column gap-1">
            <TituloSeccion>Registrar la compra</TituloSeccion>
            <p className="small text-body-secondary">
              Registrar la compra deja constancia de lo adquirido. El stock se mueve recién con la recepción, desde la app del almacén.
            </p>
          </div>

          {/* cabecera · flex con envoltura: en angosto los campos bajan de fila en vez de desbordarse */}
          <div className="d-flex flex-wrap gap-3 align-items-start">
            <div style={celdaForm(3, 220)}>
              <BuscarOpcion
                id="c-prov" etiqueta="Proveedor" valor={proveedorId} enCambio={(x) => { setProveedorId(x); vc.salir('proveedor') }} error={vc.errores.proveedor}
                opciones={opcionesProveedor} marcador="Escribe la razón social o el RUC"
                sinResultados="Ningún proveedor coincide con"
              />
            </div>
            <div className="flex-shrink-0" style={ALTURA_ETIQUETA}>
              <Pildora tipo="secundario" enClic={() => setAltaAbierta(true)}>+ Nuevo</Pildora>
            </div>
            <div style={celdaForm(1, 110)}>
              <Etiqueta htmlFor="c-tipo">Tipo</Etiqueta>
              <select className="form-select" id="c-tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoComprobante)}>
                <option value="factura">Factura</option>
                <option value="boleta">Boleta</option>
              </select>
            </div>
            <div style={celdaForm(1, 150)}>
              <Campo id="c-numero" etiqueta="N.º de comprobante" valor={numero} enCambio={(x) => { setNumero(x); vc.cambio('numero') }} marcador="F001-4512" {...vc.campo('numero')} />
            </div>
            <div style={celdaForm(1, 150)}>
              <Etiqueta htmlFor="c-fecha">Fecha</Etiqueta>
              <input className={`form-control${vc.errores.fecha ? ' is-invalid' : ''}`} id="c-fecha" type="date" value={fecha} max={hoyLima()}
                onChange={(e) => { setFecha(e.target.value); vc.salir('fecha') }} aria-invalid={!!vc.errores.fecha} aria-describedby={vc.errores.fecha ? 'c-fecha-error' : undefined} />
              {vc.errores.fecha && <div id="c-fecha-error" className="invalid-feedback d-block" aria-live="polite">{vc.errores.fecha}</div>}
            </div>
          </div>

          {/* línea en edición · misma envoltura */}
          <div className="d-flex flex-wrap gap-3 align-items-start pt-3 border-top" >
            <div style={celdaForm(3, 220)}>
              <Etiqueta htmlFor="c-insumo">Insumo (busca entre los 237 del S10)</Etiqueta>
              <input className={`form-control${vl.errores.insumo ? ' is-invalid' : vl.campo('insumo').valido ? ' is-valid' : ''}`}
                id="c-insumo" aria-invalid={!!vl.errores.insumo} aria-describedby={vl.errores.insumo ? 'c-insumo-error' : undefined}
                onBlur={() => vl.salir('insumo')}
                list="catalogo-insumos"
                value={insumoTexto}
                onChange={(e) => {
                  setInsumoTexto(e.target.value)
                  const cod = e.target.value.split(' · ')[0]
                  const elegido = insumos.some((i) => i.codigo === cod)
                  setInsumoId(elegido ? cod : '')
                  if (elegido) vl.salir('insumo'); else vl.cambio('insumo') // elegir de la lista se revisa al momento
                }}
                placeholder="Escribe código o nombre…"

              />
              {vl.errores.insumo && <div id="c-insumo-error" className="invalid-feedback d-block" aria-live="polite">{vl.errores.insumo}</div>}
              <datalist id="catalogo-insumos">
                {insumos.map((i) => (
                  <option key={i.id} value={`${i.codigo} · ${i.descripcion}`} />
                ))}
              </datalist>
            </div>
            <div style={celdaForm(1, 120)}>
              <Campo id="c-cant" etiqueta={`Cantidad${insumoId ? ` (${insumos.find((i) => i.id === insumoId)?.unidadCompra})` : ''}`} valor={cantidad} enCambio={(x) => { setCantidad(x); vl.cambio('cantidad') }} marcador="0" modoTeclado="decimal" {...vl.campo('cantidad')} />
            </div>
            <div style={celdaForm(1, 150)}>
              <Campo id="c-precio" etiqueta="Precio unit. S/ (con IGV)" valor={precio} enCambio={(x) => { setPrecio(x); vl.cambio('precio') }} marcador="0.00" modoTeclado="decimal" {...vl.campo('precio')} />
            </div>
            <div className="flex-shrink-0" style={ALTURA_ETIQUETA}>
              <Pildora tipo="secundario" enClic={agregarLinea}>Agregar</Pildora>
            </div>
          </div>

          {ref && (
            <p className="small text-body-secondary m-0">
              {ref.promedio === null
                ? 'Aún no se recibió este insumo en la obra: no hay precio habitual con qué comparar.'
                : <>Promedio pagado en la obra: <strong>S/ {soles(ref.promedio)}</strong> sin IGV (≈ S/ {soles(ref.promedio * 1.18)} con IGV) por {ref.unidadCompra}
                  {desvio !== null && <> · este precio: {desvio > 0 ? '+' : ''}{desvio.toLocaleString('es-PE')} %</>}</>}
            </p>
          )}
          {fuera && ref && (
            <Aviso tono="aviso" rol="status">
              <strong>Este precio se aparta más de ±{ref.limitePct} % de lo habitual.</strong> Se puede registrar igual; la línea
              quedará marcada. Si quieres, deja una nota (opcional):
              <input
                aria-label="Nota sobre el precio (opcional)"
                value={nota}
                maxLength={300}
                onChange={(e) => setNota(e.target.value)}
                placeholder="p. ej. alza del proveedor por escasez"
                className="form-control d-block w-100 mt-2"
                style={{ height: 40, boxSizing: 'border-box' }}
              />
            </Aviso>
          )}

          {/* líneas agregadas */}
          {lineas.length > 0 && (
            <div className="d-flex flex-column">
              {lineas.map((l, i) => (
                <div key={`${l.insumoId}-${i}`} className="d-flex align-items-center gap-2 py-2 px-0 border-bottom" >
                  <span className="flex-grow-1 d-flex flex-column min-w-0" >
                    <span className="fw-bold">{l.descripcion}</span>
                    <span className="n small text-body-secondary">{l.cantidad} {l.unidadCompra} × S/ {soles(l.precioUnitario)}
                      {l.fueraDeLoHabitual && <> · <span className="text-warning-emphasis fw-bold">fuera de lo habitual</span>{l.notaPrecio ? ` («${l.notaPrecio}»)` : ''}</>}</span>
                  </span>
                  <span className="n fw-bold text-nowrap">S/ {soles(l.cantidad * l.precioUnitario)}</span>
                  <button
                    type="button"
                    aria-label={`Quitar ${l.descripcion}`}
                    onClick={() => setLineas((ls) => ls.filter((_, j) => j !== i))}
                    className="btn btn-link text-body-secondary flex-shrink-0"
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          )}

          {/* totales: base imponible, impuesto y total (criterio de HU-32) */}
          <div className="gap-2" style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
            {[['Base imponible', base], ['IGV 18 %', total - base], ['Total', total]].map(([rot, val]) => (
              <div key={rot as string} className="d-flex flex-column bg-body-tertiary rounded-4 py-2 px-2" >
                <span className="small text-body-secondary">{rot as string}</span>
                <span className="n" style={{ fontSize: rot === 'Total' ? 17 : 15, fontWeight: 800 }}>S/ {soles(val as number)}</span>
              </div>
            ))}
          </div>

          <label className="d-flex flex-column gap-2 small fw-semibold text-body-secondary">
            Comprobante digital (PDF, JPG o PNG, opcional — máx. 10 MB)
            <input
              key={inputArchivoClave}
              id="c-archivo"
              type="file"
              accept="application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png"
              onChange={(e) => setArchivoComprobante(e.target.files?.[0] ?? null)}
              className="form-control form-control-sm"
            />
          </label>

          {error && <Aviso tono="grave" rol="alert">{error}</Aviso>}
          {exito?.advertencias && exito.advertencias.length > 0 && (
            <Aviso tono="aviso" rol="status">{exito.advertencias.join(' · ')}</Aviso>
          )}

          <Pildora enClic={registrar} deshabilitado={guardando} anchoCompleto>
            {guardando ? 'Registrando…' : 'Registrar compra'}
          </Pildora>
          <span className="small text-body-secondary">
            Esto no mueve el stock: el material entra al almacén con la recepción.
          </span>
        </section>

      )}

      {vista === 'lista' && (
        <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Compras registradas">
          <TablaDatos
            etiqueta="Compras registradas"
            filas={compras.filter((c) => filtro === 'todas' ? true : filtro === 'anuladas' ? c.estado === 'anulada'
              : filtro === 'por_recibir' ? c.estado !== 'anulada' && c.estado !== 'recibida' : c.estado !== 'anulada')}
            clave={(c) => c.id}
            buscar={(c) => `${c.numeroComprobante} ${c.proveedorRazonSocial} ${c.fecha} ${c.lineas.map((l) => l.descripcion).join(' ')}`}
            marcadorBusqueda="Buscar por comprobante, proveedor o insumo…"
            ordenInicial={{ clave: 'fecha', dir: 'desc' }}
            filtros={<Segmentado etiqueta="Estado de las compras" valor={filtro} enCambio={setFiltro} opciones={[
              { valor: 'vigentes', texto: 'Vigentes' }, { valor: 'por_recibir', texto: 'Por recibir' },
              { valor: 'anuladas', texto: 'Anuladas' }, { valor: 'todas', texto: 'Todas' }]} />}
            vacio={{ titulo: 'Todavía no hay compras', detalle: puedeRegistrar ? 'Regístrala con su comprobante en «Registrar compra».' : 'Aquí aparecen las compras de la obra con su estado de recepción.' }}
            columnas={[
              { clave: 'comprobante', titulo: 'Comprobante', valor: (c) => c.numeroComprobante, celda: (c) => (
                <>
                  <strong className="n">{c.tipoComprobante === 'factura' ? 'Factura' : c.tipoComprobante === 'boleta' ? 'Boleta' : 'Comprobante'} {c.numeroComprobante}</strong>
                  <div className="small text-body-secondary text-truncate" style={{ maxWidth: 320 }} title={c.proveedorRazonSocial}>{c.proveedorRazonSocial}</div>
                  {c.estado === 'anulada' && c.motivoAnulacion && <div className="small text-danger">Anulada: {c.motivoAnulacion}</div>}
                </>
              ) },
              { clave: 'fecha', titulo: 'Fecha', numerica: true, valor: (c) => c.fecha, celda: (c) => <span className="text-nowrap">{c.fecha}</span> },
              { clave: 'insumos', titulo: 'Insumos', numerica: true, soloEscritorio: true, valor: (c) => c.lineas.length, celda: (c) => c.lineas.length },
              { clave: 'total', titulo: 'Total', numerica: true, valor: (c) => c.totales.total, celda: (c) => <strong className="text-nowrap">S/ {soles(c.totales.total)}</strong> },
              { clave: 'estado', titulo: 'Estado', valor: (c) => ESTADO_COMPRA[c.estado].texto, celda: (c) => {
                const vigentes = c.comprobantesVigentes ?? (c.adjunto ? 1 : 0)
                return (
                  <div className="d-flex flex-wrap gap-1">
                    <Chip tono={ESTADO_COMPRA[c.estado].tono}>{ESTADO_COMPRA[c.estado].texto}</Chip>
                    {vigentes === 0 && c.estado !== 'anulada' && <Chip tono="aviso">Sin comprobante</Chip>}
                    {c.proveedorEstado === 'con_alerta_sunat' && <Chip tono="aviso">{c.proveedorAlertaSunat ? `SUNAT: ${c.proveedorAlertaSunat}` : 'Alerta SUNAT'}</Chip>}
                  </div>
                )
              } },
            ]}
            acciones={(c) => (
              <AccionesFila de={`la compra ${c.numeroComprobante}`} acciones={[
                { texto: 'Comprobantes', enClic: () => setComprobantesDe(c) },
                c.estado !== 'recibida' && c.estado !== 'anulada' && { texto: 'Recibir', enClic: () => setRecibiendo(c) },
                puedeRegistrar && c.estado !== 'anulada' && { texto: 'Anular', peligro: true, enClic: () => setAnulando(c) },
              ]} />
            )}
          />
        </Vidrio>
      )}

      {recibiendo && (
        <RecepcionCompra
          compra={recibiendo}
          enCerrar={() => setRecibiendo(null)}
          enListo={() => {
            avisar(`Recepción de ${recibiendo.numeroComprobante} registrada: los ingresos ya están en el kárdex (Ingresos y salidas) y el stock se movió recién ahora.`)
            setRecibiendo(null)
            cargar()
          }}
        />
      )}
      {anulando && (
        <AnularCompra
          compra={anulando}
          enCerrar={() => setAnulando(null)}
          enListo={(c) => {
            avisar(`Compra ${c.numeroComprobante} anulada: sigue en la lista con su motivo y ya no cuenta en «Comprado».`)
            setAnulando(null)
            cargar()
          }}
        />
      )}
      {comprobantesDe && (
        <ComprobantesCompra
          compra={comprobantesDe}
          enCerrar={() => setComprobantesDe(null)}
          enCambio={cargar}
        />
      )}
      {altaAbierta && (
        <AltaProveedor
          enCerrar={() => setAltaAbierta(false)}
          enCreado={(p) => {
            // HU-30: al guardarlo queda seleccionado en la compra en curso
            const idStr = String(p.id)
            setAltaAbierta(false)
            setProveedores((ps) => [{ ...p, id: idStr }, ...ps.filter((x) => String(x.id) !== idStr)])
            setProveedorId(idStr)
          }}
        />
      )}
    </div>
    </>
  )
}
