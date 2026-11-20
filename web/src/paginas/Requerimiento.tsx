/* ─────────────────────────────────────────────────────────────────────────────
   HU-19 · Ajustar el requerimiento de la obra con motivo, fecha y usuario · T-339
   (defecto D11 del Sprint 1, HT-11)

   - Lo planificado viene del S10 (Importar). Ajustar una cantidad no la pisa:
     el servidor guarda la anterior, la nueva, el motivo, quién y cuándo.
   - Solo residente y administrador ajustan (lo decide el servidor, regla 9).
   - T-340: agregar a mano un insumo que el S10 no trae, con su motivo. Debe
     existir en el catálogo; si ya está planificado, se ajusta (no se duplica).
     La línea queda marcada «Agregado a mano» para siempre.
   ───────────────────────────────────────────────────────────────────────── */
import { fechaHoraLima } from '../api/horaLima'
import { useEffect, useMemo, useState } from 'react'
import { ErrorApi, api as cliente } from '../api/cliente'
import { usaApiReal } from '../api/http'
import { requerimiento as api, type Ajuste, type LineaRequerimiento } from '../api/oficina'
import type { InsumoCatalogo } from '../api/tipos'
import { Campo, Chip, Modal, Vacio } from '../ui/base'
import { Chips, Encabezado, Pildora, TituloSeccion, Vidrio } from '../ui/piezas'
import { EsqueletoTabla } from '../ui/Esqueleto'

const n = (x: number) => x.toLocaleString('es-PE', { maximumFractionDigits: 4 })

function AjustarLinea({ l, enCerrar, enListo }: { l: LineaRequerimiento; enCerrar: () => void; enListo: () => void }) {
  const [valor, setValor] = useState(String(l.cantidad))
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [ajustes, setAjustes] = useState<Ajuste[] | null>(null)
  useEffect(() => { api.ajustes(l.id).then(setAjustes).catch(() => setAjustes([])) }, [l.id])
  const nueva = Number(valor.replace(',', '.'))

  const guardar = async () => {
    setError('')
    if (!(nueva > 0)) { setError('La cantidad debe ser mayor que cero.'); return }
    if (nueva === l.cantidad) { setError('Esa ya es la cantidad planificada.'); return }
    if (motivo.trim().length < 3) { setError('Sin motivo no hay ajuste: escribe por qué cambia.'); return }
    setGuardando(true)
    try { await api.ajustar(l.id, nueva, motivo); enListo() }
    catch (e) { setError(e instanceof ErrorApi ? e.message : 'No se pudo ajustar.') }
    finally { setGuardando(false) }
  }

  return (
    <Modal titulo={`Ajustar ${l.codigo}`} enCerrar={enCerrar} ancho={520}>
      <div className="d-flex flex-column gap-3">
        <p><strong>{l.descripcion}</strong> · planificado hoy: {n(l.cantidad)} {l.unidad}</p>
        <div className="d-flex flex-wrap gap-3 align-items-end">
          <div style={{ flex: '1 1 150px' }}><Campo id="r-cant" etiqueta={`Nueva cantidad (${l.unidad})`} valor={valor} enCambio={setValor} /></div>
          <div style={{ flex: '2 1 240px' }}><Campo id="r-mot" etiqueta="Motivo" valor={motivo} enCambio={setMotivo} marcador="p. ej. metrado corregido por el residente" /></div>
        </div>
        {nueva > 0 && nueva !== l.cantidad && (
          <p className="small text-body-secondary">
            Cambio: {n(l.cantidad)} → <strong>{n(nueva)}</strong> {l.unidad} ({nueva > l.cantidad ? '+' : ''}{n(nueva - l.cantidad)})
          </p>
        )}
        {error && <p role="alert" className="small text-danger fw-semibold">{error}</p>}
        <div className="d-flex flex-wrap gap-2 justify-content-end">
          <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
          <Pildora enClic={guardar} deshabilitado={guardando}>{guardando ? 'Guardando…' : 'Guardar ajuste'}</Pildora>
        </div>
        <div>
          <span className="small fw-bold text-uppercase text-body-secondary">Ajustes anteriores{ajustes && ajustes.length > 0 ? ` · ${ajustes.length} (el más reciente primero)` : ''}</span>
          {ajustes === null ? <EsqueletoTabla filas={3} />
            : ajustes.length === 0 ? <p className="small text-body-secondary">Es la cantidad original del S10.</p>
              : (
                <ul className="small text-body-secondary ps-3 pe-1"  style={{ margin: '6px 0 0', maxHeight: 168, overflowY: 'auto' }}>
                  {ajustes.slice().reverse().map((a, k) => (
                    <li key={k}>{n(a.cantidadAnterior)} → <strong>{n(a.cantidadNueva)}</strong> · {a.motivo} · {a.ajustadoPorNombre} · {fechaHoraLima(a.ajustadoEn)}</li>
                  ))}
                </ul>
              )}
        </div>
      </div>
    </Modal>
  )
}

/* ── T-340: insumo que el S10 no trae ── */
function AgregarInsumo({ yaPlanificados, enCerrar, enListo }: { yaPlanificados: Set<string>; enCerrar: () => void; enListo: () => void }) {
  const [insumos, setInsumos] = useState<InsumoCatalogo[]>([])
  const [texto, setTexto] = useState('')
  const [cantidad, setCantidad] = useState('')
  const [precio, setPrecio] = useState('')
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  useEffect(() => { cliente.listarInsumos().then(setInsumos).catch(() => setInsumos([])) }, [])
  const codigo = texto.split(' · ')[0].trim()
  const elegido = insumos.find((i) => i.codigo === codigo)
  const disponibles = useMemo(() => insumos.filter((i) => !yaPlanificados.has(i.codigo)), [insumos, yaPlanificados])
  const cant = Number(cantidad.replace(',', '.'))
  const pre = Number((precio || '0').replace(',', '.'))

  const guardar = async () => {
    setError('')
    if (!elegido) { setError('Elige un insumo del catálogo. Si no existe, regístralo primero en «Catálogo de insumos».'); return }
    if (yaPlanificados.has(elegido.codigo)) { setError('Ese insumo ya está planificado: usa «Ajustar» en su línea.'); return }
    if (!(cant > 0)) { setError('La cantidad debe ser mayor que cero.'); return }
    if (!(pre >= 0)) { setError('El precio no puede ser negativo.'); return }
    if (motivo.trim().length < 3) { setError('Sin motivo no se agrega: escribe por qué hace falta.'); return }
    setGuardando(true)
    try { await api.agregar(elegido.codigo, cant, pre, motivo); enListo() }
    catch (e) { setError(e instanceof ErrorApi ? e.message : 'No se pudo agregar.') }
    finally { setGuardando(false) }
  }

  return (
    <Modal titulo="Agregar un insumo no planificado" enCerrar={enCerrar} ancho={560}>
      <div className="d-flex flex-column gap-3">
        <p className="small text-body-secondary">
          Para lo que el S10 no trae. Queda marcado como agregado a mano, con tu nombre, la fecha y el motivo. Cantidad y precio van en la unidad del S10.
        </p>
        <div>
          <label htmlFor="r-ins" className="small fw-semibold text-body-secondary">Insumo del catálogo</label>
          <input id="r-ins" list="r-catalogo" className="form-control w-100 mt-2" value={texto} onChange={(e) => setTexto(e.target.value)}
            placeholder="Escribe código o nombre…"  style={{ height: 44 }} />
          <datalist id="r-catalogo">
            {disponibles.map((i) => <option key={i.id} value={`${i.codigo} · ${i.descripcion}`} />)}
          </datalist>
        </div>
        <div className="d-flex flex-wrap gap-3 align-items-end">
          <div style={{ flex: '1 1 140px' }}><Campo id="r-acant" etiqueta="Cantidad (unidad del S10)" valor={cantidad} enCambio={setCantidad} marcador="0" /></div>
          <div style={{ flex: '1 1 140px' }}><Campo id="r-apre" etiqueta="Precio unitario (S/)" valor={precio} enCambio={setPrecio} marcador="0.00" /></div>
        </div>
        <Campo id="r-amot" etiqueta="Motivo" valor={motivo} enCambio={setMotivo} marcador="p. ej. partida adicional aprobada por el residente" />
        {elegido && cant > 0 && pre >= 0 && (
          <p className="small text-body-secondary">
            Costo planificado: <strong>S/ {(cant * pre).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>
          </p>
        )}
        {error && <p role="alert" className="small text-danger fw-semibold">{error}</p>}
        <div className="d-flex flex-wrap gap-2 justify-content-end">
          <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
          <Pildora enClic={guardar} deshabilitado={guardando}>{guardando ? 'Agregando…' : 'Agregar al requerimiento'}</Pildora>
        </div>
      </div>
    </Modal>
  )
}

export function Requerimiento() {
  const [xs, setXs] = useState<LineaRequerimiento[] | null>(null)
  const [error, setError] = useState('')
  const [q, setQ] = useState('')
  const [editando, setEditando] = useState<LineaRequerimiento | null>(null)
  const [agregando, setAgregando] = useState(false)
  const cargar = () => api.listar().then(setXs).catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar el requerimiento.'))
  useEffect(() => { if (usaApiReal) cargar() }, [])

  const visibles = useMemo(() => {
    const t = q.trim().toLowerCase()
    return (xs ?? []).filter((l) => !t || l.descripcion.toLowerCase().includes(t) || l.codigo.toLowerCase().includes(t))
  }, [xs, q])

  return (
    <>
      <div className="d-flex flex-column gap-3 pb-4">
        <Encabezado titulo="Requerimiento de la obra" sub="Lo planificado en el S10, insumo por insumo. Ajustar no borra: queda la cantidad anterior, la nueva, el motivo y quién."
          acciones={usaApiReal ? <Pildora enClic={() => setAgregando(true)}>Agregar insumo no planificado</Pildora> : undefined} />
        <Chips>
          <Chip tono="neutro">Viene del S10 (Importar)</Chip>
          <Chip tono="neutro">Cada ajuste deja rastro</Chip>
          {xs && xs.some((l) => l.manual) && <Chip tono="aviso">{xs.filter((l) => l.manual).length} agregado(s) a mano</Chip>}
        </Chips>
        {!usaApiReal ? (
          <Vidrio><Vacio titulo="Necesita el servidor" detalle="Configura VITE_API_URL e ingresa con tu usuario: el requerimiento no se simula." /></Vidrio>
        ) : (
          <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Líneas del requerimiento">
            <TituloSeccion>Insumos planificados{xs ? ` · ${xs.length}` : ''}</TituloSeccion>
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Buscar por nombre o código…" aria-label="Buscar en el requerimiento"
              className="form-control" />
            {error && <p role="alert" className="small text-danger fw-semibold">{error}</p>}
            {xs === null && !error ? <EsqueletoTabla />
              : xs && xs.length === 0 ? <Vacio titulo="Sin requerimiento" detalle="Importa la Relación de insumos del S10 en «Importar del S10»." />
                : (
                  <div className="overflow-x-auto">
                    <table className="table table-hover align-middle mb-0">
                      <thead><tr><th>Insumo</th><th className="n">Cantidad planificada</th><th className="n">Precio (S10)</th><th aria-label="Acción" /></tr></thead>
                      <tbody>
                        {visibles.map((l) => (
                          <tr key={l.id}>
                            <td>
                              <strong>{l.descripcion}</strong>
                              <div className="small text-body-secondary">
                                SKU {l.codigo}
                                {l.manual && <> · <span title={l.motivoAlta ?? ''} className="text-warning-emphasis fw-semibold">Agregado a mano</span>: {l.motivoAlta}</>}
                              </div>
                            </td>
                            <td className="n">{n(l.cantidad)} {l.unidad}</td>
                            <td className="n">S/ {l.precio.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                            <td className="text-end"><Pildora tipo="secundario" chica etiqueta={`Ajustar ${l.descripcion}`} enClic={() => setEditando(l)}>Ajustar</Pildora></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
          </Vidrio>
        )}
        {agregando && <AgregarInsumo yaPlanificados={new Set((xs ?? []).map((l) => l.codigo))} enCerrar={() => setAgregando(false)}
          enListo={() => { setAgregando(false); cargar() }} />}
        {editando && <AjustarLinea l={editando} enCerrar={() => setEditando(null)} enListo={() => { setEditando(null); cargar() }} />}
      </div>
    </>
  )
}
