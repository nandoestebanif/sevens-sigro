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
import { ErrorApi, api } from '../api/cliente'
import type { Compra, EstadoCompra, InsumoCatalogo, LineaCompra, LineaRecepcion, Proveedor, TipoComprobante } from '../api/tipos'
import { Campo, Chip, Modal, Vacio, type TonoChip } from '../ui/base'
import { Aviso, Chips, Encabezado, Fondo, Pildora, TituloSeccion, Vidrio } from '../ui/vidrio'
import { AltaProveedor } from './Proveedores'

/* HU-35: el estado lo deriva el servidor de las recepciones; aquí solo se pinta */
const ESTADO_COMPRA: Record<EstadoCompra, { texto: string; tono: TonoChip }> = {
  registrada: { texto: 'Pendiente de recepción', tono: 'aviso' },
  recibida_parcial: { texto: 'Recepción parcial', tono: 'aviso' },
  recibida: { texto: 'Recibida', tono: 'bien' },
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

  useEffect(() => {
    api.detalleRecepcion(compra.id).then((d) => {
      setDetalle(d)
      // por defecto se propone la recepción TOTAL (criterio de la tarjeta)
      setCantidades(Object.fromEntries(d.map((l) => [l.insumoId, String(l.pendiente)])))
    })
  }, [compra.id])

  const recibir = async () => {
    if (!detalle) return
    setGuardando(true)
    setError('')
    try {
      await api.registrarRecepcion(
        compra.id,
        detalle.map((l) => ({ insumoId: l.insumoId, cantidad: Number(cantidades[l.insumoId]) || 0 })),
      )
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
        <Vacio titulo="Cargando…" detalle="Consultando pedido, recibido y pendiente." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <p style={{ fontSize: 12.5, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
            {compra.proveedorRazonSocial} · {compra.fecha}. Se propone la recepción total;
            si el camión llegó con menos, corrige la cantidad: la compra queda parcial y
            admite más recepciones hasta completarse.
          </p>
          <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 380 }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--campo-borde)' }}>
                {['Insumo', 'Pedido', 'Recibido', 'Pendiente', 'Recibir ahora'].map((h, i) => (
                  <th key={h} style={{ padding: '7px 6px', fontSize: 11, fontWeight: 700, textTransform: 'uppercase', color: 'var(--tinta-2)', letterSpacing: 0.4, textAlign: i ? 'right' : 'left' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {detalle.map((l) => (
                <tr key={l.insumoId} style={{ borderBottom: '1px solid var(--separador)' }}>
                  <td style={{ padding: '9px 6px', fontSize: 12.5, fontWeight: 600 }}>{l.descripcion}</td>
                  <td className="n" style={{ padding: '9px 6px', fontSize: 12.5, color: 'var(--tinta-2)', textAlign: 'right' }}>{l.pedido.toLocaleString('es-PE')} {l.unidadCompra}</td>
                  <td className="n" style={{ padding: '9px 6px', fontSize: 12.5, color: 'var(--tinta-2)', textAlign: 'right' }}>{l.recibido.toLocaleString('es-PE')}</td>
                  <td className="n" style={{ padding: '9px 6px', fontSize: 12.5, fontWeight: 700, textAlign: 'right' }}>{l.pendiente.toLocaleString('es-PE')}</td>
                  <td style={{ padding: '9px 0 9px 10px', textAlign: 'right', width: 90 }}>
                    <input
                      aria-label={`Cantidad a recibir de ${l.descripcion}`}
                      value={cantidades[l.insumoId] ?? ''}
                      onChange={(e) => setCantidades((c) => ({ ...c, [l.insumoId]: e.target.value }))}
                      className="campo"
                      style={{ width: 86, height: 40, padding: '0 10px', fontSize: 14, textAlign: 'right' }}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
          {error && <p role="alert" style={{ fontSize: 12.5, color: 'var(--grave)', fontWeight: 600 }}>{error}</p>}
          <p style={{ fontSize: 12, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
            Esta recepción es la que mueve el stock: cada línea genera su ingreso en el
            kárdex (Ingresos y salidas) y recalcula el costo promedio del insumo.
          </p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, justifyContent: 'flex-end' }}>
            <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
            <Pildora enClic={recibir} deshabilitado={guardando}>
              {guardando ? 'Registrando…' : 'Registrar recepción'}
            </Pildora>
          </div>
        </div>
      )}
    </Modal>
  )
}

const soles = (n: number) =>
  n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const estiloCampo: React.CSSProperties = {
  height: 44, border: '1px solid var(--campo-borde)', borderRadius: 14,
  padding: '0 12px', fontSize: 14, fontFamily: 'inherit', background: 'var(--campo-fondo)',
  color: 'var(--tinta)', outlineColor: 'var(--accion)',
  // responsive: el campo ocupa su celda y jamás la empuja (un select con 626
  // razones sociales largas, sin esto, desborda toda la fila)
  width: '100%', minWidth: 0, maxWidth: '100%',
}

/** celda de formulario que envuelve: crece hasta su proporción y baja de fila
    cuando no cabe, en vez de desbordar la pantalla */
const celdaForm = (crecer: number, base: number): React.CSSProperties => ({
  display: 'flex', flexDirection: 'column', gap: 6,
  flex: `${crecer} 1 ${base}px`, minWidth: 0,
})

const acorta = (texto: string, max = 44) =>
  texto.length > max ? `${texto.slice(0, max - 1)}…` : texto

function Etiqueta({ htmlFor, children }: { htmlFor: string; children: string }) {
  return (
    <label htmlFor={htmlFor} style={{ fontSize: 13, fontWeight: 600, color: 'var(--tinta-2)' }}>
      {children}
    </label>
  )
}

export function Compras() {
  const [proveedores, setProveedores] = useState<Proveedor[]>([])
  const [insumos, setInsumos] = useState<InsumoCatalogo[]>([])
  const [compras, setCompras] = useState<Compra[]>([])

  // cabecera de la compra
  const [proveedorId, setProveedorId] = useState('')
  const [tipo, setTipo] = useState<TipoComprobante>('factura')
  const [numero, setNumero] = useState('')
  const [fecha, setFecha] = useState(() => new Date().toISOString().slice(0, 10))

  // línea en edición
  const [insumoId, setInsumoId] = useState('')
  const [insumoTexto, setInsumoTexto] = useState('')
  const [cantidad, setCantidad] = useState('')
  const [precio, setPrecio] = useState('')
  const [lineas, setLineas] = useState<LineaCompra[]>([])

  const [error, setError] = useState<string | null>(null)
  const [exito, setExito] = useState<Compra | null>(null)
  const [altaAbierta, setAltaAbierta] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [recibiendo, setRecibiendo] = useState<Compra | null>(null)
  const [avisoRecepcion, setAvisoRecepcion] = useState('')

  const cargar = () => {
    api.proveedoresParaComprar().then(setProveedores) // excluye a los de baja (HU-29)
    api.listarInsumos().then(setInsumos)
    api.listarCompras().then(setCompras)
  }
  useEffect(cargar, [])

  const total = useMemo(() => lineas.reduce((s, l) => s + l.cantidad * l.precioUnitario, 0), [lineas])
  const base = total / 1.18

  const agregarLinea = () => {
    const ins = insumos.find((i) => i.id === insumoId)
    const cant = Number(cantidad)
    const prec = Number(precio)
    if (!ins || !(cant > 0) || !(prec > 0)) {
      setError('La línea necesita insumo, cantidad y precio mayores a cero.')
      return
    }
    setError(null)
    setLineas((ls) => [...ls, {
      insumoId: ins.id, descripcion: ins.descripcion, unidadCompra: ins.unidadCompra,
      cantidad: cant, precioUnitario: prec,
    }])
    setInsumoId('')
    setInsumoTexto('')
    setCantidad('')
    setPrecio('')
  }

  const registrar = async () => {
    setGuardando(true)
    setError(null)
    setExito(null)
    try {
      const compra = await api.registrarCompra({
        proveedorId, tipoComprobante: tipo, numeroComprobante: numero, fecha, lineas,
      })
      setExito(compra)
      setLineas([])
      setNumero('')
      cargar()
    } catch (e) {
      if (e instanceof ErrorApi) setError(e.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <>
    <Fondo dibujo="compras" />
    <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <Encabezado
        titulo="Compras"
        sub="Lo que el residente compra a sus proveedores · la compra no mueve el stock: lo mueve la recepción en almacén"
      />

      <Chips>
        <Chip tono="info">Compras y recepciones: datos de ejemplo · insumos del S10 real</Chip>
        <Chip tono="marca">Proveedores: los reales del cliente</Chip>
      </Chips>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, alignItems: 'flex-start' }}>
        <Vidrio estilo={{ flex: '999 1 560px', display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Compras registradas">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
            <TituloSeccion>Compras registradas</TituloSeccion>
            <span style={{ fontSize: 13, color: 'var(--tinta-2)' }}>una compra admite varias recepciones hasta completar lo comprado</span>
          </div>
          {avisoRecepcion && <Aviso tono="bien" rol="status">{avisoRecepcion}</Aviso>}
          {compras.length === 0 ? (
            <Vacio titulo="Todavía no hay compras" detalle="Las que registres aparecerán aquí con su estado de recepción." />
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="tabla-vidrio" style={{ minWidth: 640 }}>
                <thead>
                  <tr>
                    <th>Comprobante</th><th>Fecha</th><th className="n">Insumos</th><th className="n">Total S/</th>
                    <th>Recepción</th><th aria-label="Acción" />
                  </tr>
                </thead>
                <tbody>
                  {compras.map((c) => (
                    <tr key={c.id}>
                      <td style={{ minWidth: 220 }}>
                        <div className="n" style={{ fontWeight: 700, textAlign: 'left' }}>
                          {c.tipoComprobante === 'factura' ? 'Factura' : 'Boleta'} {c.numeroComprobante}
                        </div>
                        <div style={{ fontSize: 12.5, color: 'var(--tinta-2)', marginTop: 2 }}>{c.proveedorRazonSocial}</div>
                      </td>
                      <td className="n" style={{ whiteSpace: 'nowrap' }}>{c.fecha}</td>
                      <td className="n">{c.lineas.length} {c.lineas.length === 1 ? 'insumo' : 'insumos'}</td>
                      <td className="n" style={{ fontWeight: 800, whiteSpace: 'nowrap' }}>{soles(c.totales.total)}</td>
                      <td><Chip tono={ESTADO_COMPRA[c.estado].tono}>{ESTADO_COMPRA[c.estado].texto}</Chip></td>
                      <td style={{ textAlign: 'right', width: 100 }}>
                        {c.estado !== 'recibida' && (
                          <Pildora tipo="secundario" chica enClic={() => setRecibiendo(c)}>Recibir</Pildora>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <span style={{ fontSize: 13, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
            Una compra no se edita. El IGV se guarda aparte porque no es costo del insumo (NIC 2).
          </span>
        </Vidrio>

        <aside className="vidrio-fuerte" aria-label="Registrar la compra" style={{ flex: '1 1 380px', minWidth: 0, borderRadius: 'var(--radio-vidrio)', padding: '20px 22px', display: 'flex', flexDirection: 'column', gap: 14, boxShadow: 'var(--vidrio-sombra-alta)' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <TituloSeccion>Registrar la compra</TituloSeccion>
            <p style={{ fontSize: 13, color: 'var(--tinta-2)', lineHeight: 1.5 }}>
              Registrar la compra deja constancia de lo adquirido. El stock se mueve recién con la recepción, desde la app del almacén.
            </p>
          </div>

          {/* cabecera · flex con envoltura: en angosto los campos bajan de fila en vez de desbordarse */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-end' }}>
            <div style={celdaForm(3, 220)}>
              <Etiqueta htmlFor="c-prov">Proveedor</Etiqueta>
              <select id="c-prov" value={proveedorId} onChange={(e) => setProveedorId(e.target.value)} style={estiloCampo}>
                <option value="">Elegir…</option>
                {proveedores.map((p) => (
                  <option key={p.id} value={p.id}>{acorta(p.razonSocial)} · {p.ruc}</option>
                ))}
              </select>
            </div>
            <div style={{ flex: 'none' }}>
              <Pildora tipo="secundario" enClic={() => setAltaAbierta(true)}>+ Nuevo</Pildora>
            </div>
            <div style={celdaForm(1, 110)}>
              <Etiqueta htmlFor="c-tipo">Tipo</Etiqueta>
              <select id="c-tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoComprobante)} style={estiloCampo}>
                <option value="factura">Factura</option>
                <option value="boleta">Boleta</option>
              </select>
            </div>
            <div style={celdaForm(1, 150)}>
              <Campo id="c-numero" etiqueta="N.º de comprobante" valor={numero} enCambio={setNumero} marcador="F001-4512" />
            </div>
            <div style={celdaForm(1, 150)}>
              <Etiqueta htmlFor="c-fecha">Fecha</Etiqueta>
              <input id="c-fecha" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} style={estiloCampo} />
            </div>
          </div>

          {/* línea en edición · misma envoltura */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-end', paddingTop: 14, borderTop: '1px solid var(--separador)' }}>
            <div style={celdaForm(3, 220)}>
              <Etiqueta htmlFor="c-insumo">Insumo (busca entre los 237 del S10)</Etiqueta>
              <input
                id="c-insumo"
                list="catalogo-insumos"
                value={insumoTexto}
                onChange={(e) => {
                  setInsumoTexto(e.target.value)
                  const cod = e.target.value.split(' · ')[0]
                  setInsumoId(insumos.some((i) => i.codigo === cod) ? cod : '')
                }}
                placeholder="Escribe código o nombre…"
                style={estiloCampo}
              />
              <datalist id="catalogo-insumos">
                {insumos.map((i) => (
                  <option key={i.id} value={`${i.codigo} · ${i.descripcion}`} />
                ))}
              </datalist>
            </div>
            <div style={celdaForm(1, 120)}>
              <Campo id="c-cant" etiqueta={`Cantidad${insumoId ? ` (${insumos.find((i) => i.id === insumoId)?.unidadCompra})` : ''}`} valor={cantidad} enCambio={setCantidad} marcador="0" />
            </div>
            <div style={celdaForm(1, 150)}>
              <Campo id="c-precio" etiqueta="Precio unit. S/ (con IGV)" valor={precio} enCambio={setPrecio} marcador="0.00" />
            </div>
            <div style={{ flex: 'none' }}>
              <Pildora tipo="secundario" enClic={agregarLinea}>Agregar</Pildora>
            </div>
          </div>

          {/* líneas agregadas */}
          {lineas.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {lineas.map((l, i) => (
                <div key={`${l.insumoId}-${i}`} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--separador)', fontSize: 14 }}>
                  <span style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
                    <span style={{ fontWeight: 700 }}>{l.descripcion}</span>
                    <span className="n" style={{ fontSize: 13, color: 'var(--tinta-2)' }}>{l.cantidad} {l.unidadCompra} × S/ {soles(l.precioUnitario)}</span>
                  </span>
                  <span className="n" style={{ fontWeight: 700, whiteSpace: 'nowrap' }}>S/ {soles(l.cantidad * l.precioUnitario)}</span>
                  <button
                    type="button"
                    aria-label={`Quitar ${l.descripcion}`}
                    onClick={() => setLineas((ls) => ls.filter((_, j) => j !== i))}
                    className="foco-vidrio"
                    style={{ width: 44, height: 44, borderRadius: 22, border: 0, background: 'transparent', color: 'var(--tinta-2)', cursor: 'pointer', fontSize: 15, flex: 'none' }}
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          )}

          {/* totales: base imponible, impuesto y total (criterio de HU-32) */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 8 }}>
            {[['Base imponible', base], ['IGV 18 %', total - base], ['Total', total]].map(([rot, val]) => (
              <div key={rot as string} style={{ display: 'flex', flexDirection: 'column', background: 'var(--vidrio-celda)', borderRadius: 14, padding: '8px 10px' }}>
                <span style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>{rot as string}</span>
                <span className="n" style={{ fontSize: rot === 'Total' ? 17 : 15, fontWeight: 800 }}>S/ {soles(val as number)}</span>
              </div>
            ))}
          </div>

          {error && <Aviso tono="grave" rol="alert">{error}</Aviso>}
          {exito && (
            <Aviso tono="bien" rol="status">
              Compra {exito.numeroComprobante} registrada. El stock no se movió: se moverá con la recepción, desde la app.
            </Aviso>
          )}

          <Pildora enClic={registrar} deshabilitado={guardando} anchoCompleto>
            {guardando ? 'Registrando…' : 'Registrar compra'}
          </Pildora>
          <span style={{ fontSize: 12.5, color: 'var(--tinta-2)' }}>
            Esto no mueve el stock: el material entra al almacén con la recepción.
          </span>
        </aside>
      </div>

      {recibiendo && (
        <RecepcionCompra
          compra={recibiendo}
          enCerrar={() => setRecibiendo(null)}
          enListo={() => {
            setAvisoRecepcion(`Recepción de ${recibiendo.numeroComprobante} registrada: los ingresos ya están en el kárdex (Ingresos y salidas) y el stock se movió recién ahora.`)
            setRecibiendo(null)
            cargar()
          }}
        />
      )}
      {altaAbierta && (
        <AltaProveedor
          enCerrar={() => setAltaAbierta(false)}
          enCreado={(p) => {
            // HU-30: al guardarlo queda seleccionado en la compra en curso
            setAltaAbierta(false)
            setProveedores((ps) => [p, ...ps])
            setProveedorId(p.id)
          }}
        />
      )}
    </div>
    </>
  )
}
