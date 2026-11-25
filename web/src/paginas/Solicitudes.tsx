/* ─────────────────────────────────────────────────────────────────────────────
   CR-04 · Solicitud de materiales: el residente pide lo que hace falta, el
   administrador compra contra ella y se ve cuánto queda pendiente.
   - Número correlativo por obra (lo asigna la base, sin huecos).
   - «Cargar lo que falta comprar» trae el saldo por comprar de Reposición
     (requerido − comprado, en unidad de compra): el residente solo ajusta.
   - No se edita ni se borra: se anula con motivo y sigue en la lista.
   - Emitir y anular: residente (solicitud.emitir); ver: residente, admin, gerente.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api/cliente'
import { ErrorApi } from '../api/error'
import { usaApiReal } from '../api/http'
import { puedo } from '../api/permisos'
import { ESTADO_SOLICITUD, erroresSolicitud, solicitudes, type LineaNueva, type Solicitud } from '../api/solicitudes'
import type { InsumoCatalogo } from '../api/tipos'
import { Chip, Modal, Vacio } from '../ui/base'
import { BotonExportar } from '../ui/BotonExportar'
import { BuscarOpcion } from '../ui/BuscarOpcion'
import { EsqueletoTabla } from '../ui/Esqueleto'
import { Aviso, Chips, Encabezado, Pildora, Segmentado, TituloSeccion, Vidrio } from '../ui/piezas'
import { AccionesFila, TablaDatos } from '../ui/TablaDatos'
import { useAvisos } from '../ui/Avisos'

const cant = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 4 })
const dia = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`
interface Fila extends LineaNueva { clave: string; texto: string }

export function Solicitudes() {
  const puedeEmitir = puedo('solicitud.emitir')
  const [params, setParams] = useSearchParams()
  const vista: 'lista' | 'nueva' = params.get('nueva') && puedeEmitir ? 'nueva' : 'lista'
  const irA = (v: 'lista' | 'nueva') => setParams(v === 'nueva' ? { nueva: '1' } : {})
  const [xs, setXs] = useState<Solicitud[] | null>(null)
  const [error, setError] = useState('')
  const [filtro, setFiltro] = useState<'pendientes' | 'todas' | 'anuladas'>('pendientes')
  const [viendo, setViendo] = useState<Solicitud | null>(null)
  const [anulando, setAnulando] = useState<Solicitud | null>(null)
  const avisar = useAvisos()

  const cargar = () => solicitudes.listar().then(setXs).catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudieron cargar las solicitudes.'))
  useEffect(() => { if (usaApiReal) cargar() }, [])

  const filas = useMemo(() => (xs ?? []).filter((s) => filtro === 'todas' ? true
    : filtro === 'anuladas' ? s.estado === 'anulada' : s.estado === 'solicitada' || s.estado === 'comprada_en_parte'), [xs, filtro])

  return (
    <div className="d-flex flex-column gap-3 pb-4">
      <Encabezado titulo="Solicitudes de compra" sub="Lo que la obra pide comprar y cuánto queda pendiente." />
      <Chips>
        <Chip tono="neutro">El número lo asigna el servidor, correlativo por obra</Chip>
        <Chip tono="neutro">El residente emite; el administrador compra contra la solicitud</Chip>
        <Chip tono="neutro">No se edita ni se borra: se anula con motivo</Chip>
      </Chips>

      {!usaApiReal ? (
        <Vidrio><Vacio titulo="Necesita el servidor" detalle="Configura VITE_API_URL e ingresa con tu usuario: las solicitudes no se simulan." /></Vidrio>
      ) : (
        <>
          {puedeEmitir && (
            <Segmentado etiqueta="Solicitudes" pestanas valor={vista} enCambio={irA}
              opciones={[{ valor: 'lista', texto: `Solicitudes${xs ? ` · ${xs.length}` : ''}` }, { valor: 'nueva', texto: 'Nueva solicitud' }]} />
          )}
          {error && <Aviso tono="grave" rol="alert">{error}</Aviso>}

          {vista === 'nueva' ? (
            <NuevaSolicitud enListo={(s) => { avisar(`Solicitud N.º ${s.numero} emitida con ${s.lineas.length} línea(s).`); irA('lista'); cargar() }} />
          ) : (
            <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Solicitudes emitidas">
              {xs === null && !error ? <EsqueletoTabla /> : (
                <TablaDatos
                  etiqueta="Solicitudes"
                  filas={filas}
                  clave={(s) => s.id}
                  buscar={(s) => `${s.numero} ${s.lineas.map((l) => `${l.descripcion} ${l.codigo ?? ''}`).join(' ')}`}
                  marcadorBusqueda="Buscar por número o insumo…"
                  ordenInicial={{ clave: 'numero', dir: 'desc' }}
                  filtros={<Segmentado etiqueta="Estado" valor={filtro} enCambio={setFiltro}
                    opciones={[{ valor: 'pendientes', texto: 'Pendientes' }, { valor: 'anuladas', texto: 'Anuladas' }, { valor: 'todas', texto: 'Todas' }]} />}
                  vacio={{ titulo: filtro === 'pendientes' ? 'Nada pendiente' : 'Sin solicitudes', detalle: puedeEmitir ? 'Emite una en «Nueva solicitud».' : 'El residente emite las solicitudes de la obra.' }}
                  columnas={[
                    { clave: 'numero', titulo: 'N.º', numerica: true, valor: (s) => s.numero, celda: (s) => <strong>{s.numero}</strong> },
                    { clave: 'fecha', titulo: 'Fecha', numerica: true, valor: (s) => s.fecha, celda: (s) => <span className="text-nowrap">{dia(s.fecha)}</span> },
                    { clave: 'insumos', titulo: 'Insumos', valor: (s) => s.lineas.length, celda: (s) => (
                      <span className="small">{s.lineas.slice(0, 2).map((l) => l.descripcion).join(' · ')}{s.lineas.length > 2 ? ` y ${s.lineas.length - 2} más` : ''}</span>
                    ) },
                    { clave: 'avance', titulo: 'Comprado', numerica: true,
                      valor: (s) => { const t = s.lineas.reduce((a, l) => a + l.solicitada, 0); return t ? s.lineas.reduce((a, l) => a + l.comprada, 0) / t : 0 },
                      celda: (s) => { const t = s.lineas.reduce((a, l) => a + l.solicitada, 0); const c = s.lineas.reduce((a, l) => a + Math.min(l.comprada, l.solicitada), 0); return `${t ? Math.round((c / t) * 100) : 0} %` } },
                    { clave: 'estado', titulo: 'Estado', valor: (s) => ESTADO_SOLICITUD[s.estado].texto, celda: (s) => (
                      <>
                        <Chip tono={ESTADO_SOLICITUD[s.estado].tono}>{ESTADO_SOLICITUD[s.estado].texto}</Chip>
                        {s.motivoAnulacion && <div className="small text-danger mt-1">{s.motivoAnulacion}</div>}
                      </>
                    ) },
                  ]}
                  acciones={(s) => (
                    <AccionesFila de={`la solicitud ${s.numero}`} acciones={[
                      { texto: 'Ver detalle', enClic: () => setViendo(s) },
                      puedeEmitir && s.estado !== 'anulada' && s.estado !== 'comprada' && { texto: 'Anular', peligro: true, enClic: () => setAnulando(s) },
                    ]} />
                  )}
                />
              )}
            </Vidrio>
          )}
        </>
      )}

      {viendo && <DetalleSolicitud s={viendo} enCerrar={() => setViendo(null)} />}
      {anulando && <AnularSolicitud s={anulando} enCerrar={() => setAnulando(null)}
        enListo={() => { avisar(`Solicitud N.º ${anulando.numero} anulada: sigue en la lista con su motivo.`); setAnulando(null); cargar() }} />}
    </div>
  )
}

function NuevaSolicitud({ enListo }: { enListo: (s: Solicitud) => void }) {
  const [insumos, setInsumos] = useState<InsumoCatalogo[]>([])
  const [lineas, setLineas] = useState<Fila[]>([])
  const [insumoId, setInsumoId] = useState('')
  const [libre, setLibre] = useState('')
  const [unidadLibre, setUnidadLibre] = useState('und')
  const [cantidad, setCantidad] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [cargandoSaldo, setCargandoSaldo] = useState(false)

  useEffect(() => { api.listarInsumos().then(setInsumos).catch(() => setInsumos([])) }, [])
  const opciones = useMemo(() => insumos.map((i) => ({ valor: i.id, texto: i.descripcion, detalle: `${i.codigo} · ${i.unidadCompra}` })), [insumos])

  const agregar = () => {
    const q = Number(cantidad.replace(',', '.'))
    if (!(q > 0)) { setError('Escribe una cantidad mayor que cero.'); return }
    const ins = insumos.find((i) => i.id === insumoId)
    if (!ins && !libre.trim()) { setError('Elige un insumo del catálogo o escribe una descripción.'); return }
    const nueva: Fila = ins
      ? { clave: ins.codigo, insumoCodigo: ins.codigo, texto: ins.descripcion, cantidad: q, unidadCompra: ins.unidadCompra }
      : { clave: `libre-${Date.now()}`, descripcionLibre: libre.trim(), texto: libre.trim(), cantidad: q, unidadCompra: unidadLibre.trim() || 'und' }
    if (ins && lineas.some((l) => l.insumoCodigo === ins.codigo)) {
      setLineas((ls) => ls.map((l) => l.insumoCodigo === ins.codigo ? { ...l, cantidad: l.cantidad + q } : l))
    } else setLineas((ls) => [...ls, nueva])
    setInsumoId(''); setLibre(''); setCantidad(''); setError('')
  }

  // Reposición ya calcula requerido − comprado en unidad de compra: se propone tal cual, el residente ajusta
  const cargarFaltante = async () => {
    setCargandoSaldo(true); setError('')
    try {
      const saldo = (await api.saldoPorComprar()).filter((f) => f.saldoPorComprar > 0)
      setLineas((ls) => {
        const ya = new Set(ls.map((l) => l.insumoCodigo))
        return [...ls, ...saldo.filter((f) => !ya.has(f.codigo)).map((f) => ({
          clave: f.codigo, insumoCodigo: f.codigo, texto: f.descripcion, cantidad: Math.ceil(f.saldoPorComprar * 100) / 100, unidadCompra: f.unidadCompra,
        }))]
      })
    } catch (e) { setError(e instanceof ErrorApi ? e.message : 'No se pudo traer el saldo por comprar.') } finally { setCargandoSaldo(false) }
  }

  const emitir = async () => {
    const lns: LineaNueva[] = lineas.map(({ insumoCodigo, descripcionLibre, cantidad, unidadCompra }) => ({ insumoCodigo, descripcionLibre, cantidad, unidadCompra }))
    const e = erroresSolicitud(lns)
    if (e) { setError(e); return }
    setGuardando(true); setError('')
    try { enListo(await solicitudes.emitir(lns)) } catch (x) { setError(x instanceof ErrorApi ? x.message : 'No se pudo emitir la solicitud.') } finally { setGuardando(false) }
  }

  return (
    <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 14 }} etiqueta="Nueva solicitud">
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
        <TituloSeccion>Nueva solicitud</TituloSeccion>
        <Pildora tipo="secundario" enClic={cargarFaltante} deshabilitado={cargandoSaldo}>{cargandoSaldo ? 'Cargando…' : 'Cargar lo que falta comprar'}</Pildora>
      </div>
      <div className="d-flex flex-wrap gap-2 align-items-end">
        <div style={{ flex: '3 1 320px' }}>
          <BuscarOpcion id="sol-insumo" etiqueta="Insumo del catálogo" opciones={opciones} valor={insumoId} enCambio={setInsumoId} marcador="Escribe código o nombre…" />
        </div>
        <label className="d-flex flex-column gap-1 small fw-semibold" style={{ flex: '1 1 120px' }}>
          Cantidad
          <input className="form-control" inputMode="decimal" value={cantidad} onChange={(e) => setCantidad(e.target.value)} placeholder="0" />
        </label>
        <Pildora tipo="secundario" enClic={agregar}>Agregar</Pildora>
      </div>
      <details className="small">
        <summary className="text-body-secondary">¿No está en el catálogo? Agrégalo con una descripción</summary>
        <div className="d-flex flex-wrap gap-2 mt-2">
          <input className="form-control" style={{ flex: '3 1 260px' }} value={libre} onChange={(e) => { setLibre(e.target.value); setInsumoId('') }} placeholder='Ej.: disco de corte 7"' aria-label="Descripción libre" />
          <input className="form-control" style={{ flex: '1 1 90px' }} value={unidadLibre} onChange={(e) => setUnidadLibre(e.target.value)} aria-label="Unidad" />
        </div>
      </details>

      <TablaDatos
        etiqueta="Líneas de la solicitud"
        filas={lineas}
        clave={(l) => l.clave}
        vacio={{ titulo: 'Sin líneas', detalle: 'Agrega insumos o usa «Cargar lo que falta comprar».' }}
        columnas={[
          { clave: 'insumo', titulo: 'Insumo', celda: (l) => <><strong>{l.texto}</strong><div className="small text-body-secondary">{l.insumoCodigo ? `SKU ${l.insumoCodigo}` : 'Fuera del catálogo'}</div></> },
          { clave: 'cantidad', titulo: 'Cantidad', numerica: true, celda: (l) => (
            <input className="form-control form-control-sm text-end d-inline-block" style={{ width: 110 }} inputMode="decimal" aria-label={`Cantidad de ${l.texto}`}
              value={l.cantidad} onChange={(e) => { const v = Number(e.target.value.replace(',', '.')); setLineas((ls) => ls.map((x) => x.clave === l.clave ? { ...x, cantidad: Number.isFinite(v) ? v : 0 } : x)) }} />
          ) },
          { clave: 'unidad', titulo: 'Unidad', celda: (l) => l.unidadCompra },
        ]}
        acciones={(l) => <AccionesFila de={l.texto} acciones={[{ texto: 'Quitar', enClic: () => setLineas((ls) => ls.filter((x) => x.clave !== l.clave)) }]} />}
      />
      {error && <Aviso tono="grave" rol="alert">{error}</Aviso>}
      <Pildora enClic={emitir} deshabilitado={guardando || !lineas.length} anchoCompleto>
        {guardando ? 'Emitiendo…' : `Emitir solicitud${lineas.length ? ` · ${lineas.length} línea(s)` : ''}`}
      </Pildora>
    </Vidrio>
  )
}

function DetalleSolicitud({ s, enCerrar }: { s: Solicitud; enCerrar: () => void }) {
  return (
    <Modal titulo={`Solicitud N.º ${s.numero} · ${dia(s.fecha)}`} enCerrar={enCerrar} ancho={720}>
      <div className="d-flex flex-column gap-3">
        <div className="d-flex justify-content-between align-items-center gap-2">
          <Chip tono={ESTADO_SOLICITUD[s.estado].tono}>{ESTADO_SOLICITUD[s.estado].texto}</Chip>
          <BotonExportar titulo={`Solicitud de materiales N.º ${s.numero}`} subtitulo={`Fecha ${dia(s.fecha)} · ${ESTADO_SOLICITUD[s.estado].texto}`}
            filas={s.lineas} columnas={[
              { titulo: 'Código', valor: (l) => l.codigo ?? '—', ancho: 12 },
              { titulo: 'Insumo', valor: (l) => l.descripcion, ancho: 40 },
              { titulo: 'Unidad', valor: (l) => l.unidad, ancho: 8 },
              { titulo: 'Solicitado', valor: (l) => l.solicitada, formato: 'decimal' },
              { titulo: 'Comprado', valor: (l) => l.comprada, formato: 'decimal' },
              { titulo: 'Pendiente', valor: (l) => l.pendiente, formato: 'decimal' },
            ]} />
        </div>
        {s.motivoAnulacion && <Aviso tono="grave">Anulada: {s.motivoAnulacion}</Aviso>}
        <TablaDatos
          etiqueta={`Líneas de la solicitud ${s.numero}`}
          filas={s.lineas}
          clave={(l) => `${l.codigo ?? ''}${l.descripcion}`}
          vacio={{ titulo: 'Sin líneas', detalle: '' }}
          columnas={[
            { clave: 'insumo', titulo: 'Insumo', celda: (l) => <><strong>{l.descripcion}</strong><div className="small text-body-secondary">{l.codigo ? `SKU ${l.codigo}` : 'Fuera del catálogo'}</div></> },
            { clave: 'sol', titulo: 'Solicitado', numerica: true, celda: (l) => `${cant(l.solicitada)} ${l.unidad}` },
            { clave: 'com', titulo: 'Comprado', numerica: true, celda: (l) => cant(l.comprada) },
            { clave: 'pen', titulo: 'Pendiente', numerica: true, celda: (l) => <strong>{cant(l.pendiente)}</strong> },
          ]}
        />
      </div>
    </Modal>
  )
}

function AnularSolicitud({ s, enCerrar, enListo }: { s: Solicitud; enCerrar: () => void; enListo: () => void }) {
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const anular = async () => {
    if (motivo.trim().length < 5) { setError('Escribe el motivo (al menos 5 letras): queda en el registro.'); return }
    setOcupado(true); setError('')
    try { await solicitudes.anular(s.id, motivo); enListo() } catch (e) { setError(e instanceof ErrorApi ? e.message : 'No se pudo anular.'); setOcupado(false) }
  }
  return (
    <Modal titulo={`Anular la solicitud N.º ${s.numero}`} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <p className="small text-body-secondary mb-0">No se borra: queda en la lista como «Anulada», con tu nombre, la fecha y el motivo.</p>
        <label className="d-flex flex-column gap-1 small fw-semibold">
          Motivo
          <textarea className="form-control" rows={2} value={motivo} onChange={(e) => setMotivo(e.target.value)} autoFocus />
        </label>
        {error && <span role="alert" className="small text-danger">{error}</span>}
        <div className="d-flex gap-2 justify-content-end">
          <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
          <Pildora enClic={anular} deshabilitado={ocupado}>{ocupado ? 'Anulando…' : 'Anular'}</Pildora>
        </div>
      </div>
    </Modal>
  )
}
