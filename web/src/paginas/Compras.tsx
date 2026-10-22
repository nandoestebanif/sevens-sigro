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
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useMemo, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { Compra, EstadoCompra, InsumoCatalogo, LineaCompra, LineaRecepcion, Proveedor, TipoComprobante } from '../api/tipos'
import { Boton, Campo, Chip, Modal, Tarjeta, Titulo, Vacio, type TonoChip } from '../ui/base'
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
          <p style={{ fontSize: 12.5, color: 'var(--tinta-3)', lineHeight: 1.5 }}>
            {compra.proveedorRazonSocial} · {compra.fecha}. Se propone la recepción total;
            si el camión llegó con menos, corrige la cantidad: la compra queda parcial y
            admite más recepciones hasta completarse.
          </p>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--borde)' }}>
                {['Insumo', 'Pedido', 'Recibido', 'Pendiente', 'Recibir ahora'].map((h, i) => (
                  <th key={h} style={{ padding: '7px 6px', fontSize: 10.5, fontWeight: 600, color: 'var(--tinta-3)', letterSpacing: 0.4, textAlign: i ? 'right' : 'left' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {detalle.map((l) => (
                <tr key={l.insumoId} style={{ borderBottom: '1px solid var(--linea)' }}>
                  <td style={{ padding: '9px 6px', fontSize: 12.5, fontWeight: 600 }}>{l.descripcion}</td>
                  <td className="n" style={{ padding: '9px 6px', fontSize: 12.5, color: 'var(--tinta-2)', textAlign: 'right' }}>{l.pedido.toLocaleString('es-PE')} {l.unidadCompra}</td>
                  <td className="n" style={{ padding: '9px 6px', fontSize: 12.5, color: 'var(--tinta-2)', textAlign: 'right' }}>{l.recibido.toLocaleString('es-PE')}</td>
                  <td className="n" style={{ padding: '9px 6px', fontSize: 12.5, fontWeight: 700, textAlign: 'right' }}>{l.pendiente.toLocaleString('es-PE')}</td>
                  <td style={{ padding: '9px 0 9px 10px', textAlign: 'right', width: 90 }}>
                    <input
                      aria-label={`Cantidad a recibir de ${l.descripcion}`}
                      value={cantidades[l.insumoId] ?? ''}
                      onChange={(e) => setCantidades((c) => ({ ...c, [l.insumoId]: e.target.value }))}
                      style={{
                        width: 86, height: 36, border: '1px solid var(--borde)', borderRadius: 'var(--radio-chico)',
                        padding: '0 10px', fontSize: 13, fontFamily: 'inherit', textAlign: 'right',
                        background: 'var(--tarjeta)', color: 'var(--tinta)', outlineColor: 'var(--accion)',
                      }}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {error && <p role="alert" style={{ fontSize: 12.5, color: 'var(--grave)', fontWeight: 600 }}>{error}</p>}
          <p style={{ fontSize: 12, color: 'var(--tinta-3)', lineHeight: 1.5 }}>
            Esta recepción es la que mueve el stock: cada línea genera su ingreso en el
            kárdex (Ingresos y salidas) y recalcula el costo promedio del insumo.
          </p>
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
            <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
            <Boton enClic={recibir} deshabilitado={guardando}>
              {guardando ? 'Registrando…' : 'Registrar recepción'}
            </Boton>
          </div>
        </div>
      )}
    </Modal>
  )
}

const soles = (n: number) =>
  n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const estiloCampo: React.CSSProperties = {
  height: 42, border: '1px solid var(--borde)', borderRadius: 'var(--radio-chico)',
  padding: '0 12px', fontSize: 14, fontFamily: 'inherit', background: 'var(--tarjeta)',
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
    <label htmlFor={htmlFor} style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' }}>
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
    <div style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
      <Titulo sub="Registrar la compra deja constancia de lo adquirido. El stock se mueve recién con la recepción, desde la app del almacén.">
        Registrar la compra
      </Titulo>

      <Tarjeta>
        {/* cabecera · flex con envoltura: en pantallas angostas los campos
            bajan de fila en vez de desbordarse */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-end' }}>
          <div style={celdaForm(3, 240)}>
            <Etiqueta htmlFor="c-prov">Proveedor</Etiqueta>
            <select id="c-prov" value={proveedorId} onChange={(e) => setProveedorId(e.target.value)} style={estiloCampo}>
              <option value="">Elegir…</option>
              {proveedores.map((p) => (
                <option key={p.id} value={p.id}>{acorta(p.razonSocial)} · {p.ruc}</option>
              ))}
            </select>
          </div>
          <div style={{ flex: 'none' }}>
            <Boton tipo="secundario" enClic={() => setAltaAbierta(true)}>+ Nuevo</Boton>
          </div>
          <div style={celdaForm(0, 110)}>
            <Etiqueta htmlFor="c-tipo">Tipo</Etiqueta>
            <select id="c-tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoComprobante)} style={estiloCampo}>
              <option value="factura">Factura</option>
              <option value="boleta">Boleta</option>
            </select>
          </div>
          <div style={celdaForm(1, 150)}>
            <Campo id="c-numero" etiqueta="N.º de comprobante" valor={numero} enCambio={setNumero} marcador="F001-4512" />
          </div>
          <div style={celdaForm(0, 150)}>
            <Etiqueta htmlFor="c-fecha">Fecha</Etiqueta>
            <input id="c-fecha" type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} style={estiloCampo} />
          </div>
        </div>

        {/* línea en edición · misma envoltura */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-end', marginTop: 16, paddingTop: 16, borderTop: '1px solid var(--linea)' }}>
          <div style={celdaForm(3, 240)}>
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
          <div style={celdaForm(1, 130)}>
            <Campo id="c-cant" etiqueta={`Cantidad${insumoId ? ` (${insumos.find((i) => i.id === insumoId)?.unidadCompra})` : ''}`} valor={cantidad} enCambio={setCantidad} marcador="0" />
          </div>
          <div style={celdaForm(1, 150)}>
            <Campo id="c-precio" etiqueta="Precio unit. S/ (con IGV)" valor={precio} enCambio={setPrecio} marcador="0.00" />
          </div>
          <div style={{ flex: 'none' }}>
            <Boton tipo="secundario" enClic={agregarLinea}>Agregar</Boton>
          </div>
        </div>

        {/* líneas agregadas */}
        {lineas.length > 0 && (
          <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: 16 }}>
            <tbody>
              {lineas.map((l, i) => (
                <tr key={`${l.insumoId}-${i}`} style={{ borderTop: '1px solid var(--linea)' }}>
                  <td style={{ padding: '10px 2px', fontSize: 13.5, fontWeight: 600 }}>{l.descripcion}</td>
                  <td className="n" style={{ padding: '10px 2px', fontSize: 13, color: 'var(--tinta-2)', textAlign: 'right' }}>
                    {l.cantidad} {l.unidadCompra}
                  </td>
                  <td className="n" style={{ padding: '10px 2px', fontSize: 13, color: 'var(--tinta-2)', textAlign: 'right' }}>
                    × S/ {soles(l.precioUnitario)}
                  </td>
                  <td className="n" style={{ padding: '10px 2px', fontSize: 13.5, fontWeight: 700, textAlign: 'right' }}>
                    S/ {soles(l.cantidad * l.precioUnitario)}
                  </td>
                  <td style={{ padding: '10px 2px', textAlign: 'right', width: 40 }}>
                    <button
                      type="button"
                      aria-label={`Quitar ${l.descripcion}`}
                      onClick={() => setLineas((ls) => ls.filter((_, j) => j !== i))}
                      style={{ border: 0, background: 'transparent', color: 'var(--tinta-3)', cursor: 'pointer', fontSize: 15 }}
                    >
                      ✕
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* totales: base imponible, impuesto y total (criterio de HU-32) */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 28, marginTop: 14, paddingTop: 14, borderTop: '1px solid var(--linea)' }}>
          {[['Base imponible', base], ['IGV 18 %', total - base], ['Total', total]].map(([rot, val]) => (
            <div key={rot as string} style={{ textAlign: 'right' }}>
              <div style={{ fontSize: 11.5, color: 'var(--tinta-3)' }}>{rot as string}</div>
              <div className="n" style={{ fontSize: rot === 'Total' ? 19 : 14.5, fontWeight: 700, marginTop: 2 }}>
                S/ {soles(val as number)}
              </div>
            </div>
          ))}
        </div>

        {error && (
          <p role="alert" style={{ marginTop: 12, fontSize: 13, color: 'var(--grave)', fontWeight: 600 }}>{error}</p>
        )}
        {exito && (
          <p style={{ marginTop: 12, fontSize: 13, color: 'var(--bien)', fontWeight: 600 }}>
            Compra {exito.numeroComprobante} registrada. El stock no se movió: se moverá con la recepción, desde la app.
          </p>
        )}

        <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 14 }}>
          <Boton enClic={registrar} deshabilitado={guardando}>
            {guardando ? 'Registrando…' : 'Registrar compra'}
          </Boton>
          <span style={{ fontSize: 12.5, color: 'var(--tinta-3)' }}>
            Esto no mueve el stock: el material entra al almacén con la recepción.
          </span>
        </div>
      </Tarjeta>

      <Tarjeta relleno={false}>
        <div style={{ padding: '13px 18px', borderBottom: '1px solid var(--borde)', display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <span style={{ fontSize: 13.5, fontWeight: 700 }}>Compras registradas</span>
          <span style={{ fontSize: 11.5, color: 'var(--tinta-3)' }}>una compra admite varias recepciones hasta completar lo comprado</span>
        </div>
        {avisoRecepcion && (
          <div style={{ padding: '10px 18px', borderBottom: '1px solid var(--borde)', fontSize: 12.5, color: 'var(--bien)', fontWeight: 600 }}>
            {avisoRecepcion}
          </div>
        )}
        {compras.length === 0 ? (
          <Vacio titulo="Todavía no hay compras" detalle="Las que registres aparecerán aquí con su estado de recepción." />
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <tbody>
              {compras.map((c, i) => (
                <tr key={c.id} style={{ borderBottom: i === compras.length - 1 ? 'none' : '1px solid var(--linea)' }}>
                  <td style={{ padding: '12px 18px' }}>
                    <div className="n" style={{ fontSize: 13.5, fontWeight: 700 }}>
                      {c.tipoComprobante === 'factura' ? 'Factura' : 'Boleta'} {c.numeroComprobante}
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--tinta-3)', marginTop: 2 }}>{c.proveedorRazonSocial}</div>
                  </td>
                  <td className="n" style={{ padding: '12px 18px', fontSize: 13, color: 'var(--tinta-2)' }}>{c.fecha}</td>
                  <td className="n" style={{ padding: '12px 18px', fontSize: 13, color: 'var(--tinta-2)', textAlign: 'right' }}>
                    {c.lineas.length} {c.lineas.length === 1 ? 'insumo' : 'insumos'}
                  </td>
                  <td className="n" style={{ padding: '12px 18px', fontSize: 13.5, fontWeight: 700, textAlign: 'right' }}>
                    S/ {soles(c.totales.total)}
                  </td>
                  <td style={{ padding: '12px 18px', textAlign: 'right' }}>
                    <Chip tono={ESTADO_COMPRA[c.estado].tono}>{ESTADO_COMPRA[c.estado].texto}</Chip>
                  </td>
                  <td style={{ padding: '12px 18px', textAlign: 'right', width: 100 }}>
                    {c.estado !== 'recibida' && (
                      <button
                        type="button"
                        onClick={() => setRecibiendo(c)}
                        style={{
                          border: '1px solid var(--borde)', background: 'var(--tarjeta)',
                          color: 'var(--tinta-2)', borderRadius: 'var(--radio-pildora)',
                          padding: '5px 12px', fontSize: 12, fontWeight: 600,
                          cursor: 'pointer', fontFamily: 'inherit', whiteSpace: 'nowrap',
                        }}
                      >
                        Recibir
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Tarjeta>

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
  )
}
