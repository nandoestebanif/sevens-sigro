/* ─────────────────────────────────────────────────────────────────────────────
   HU-56 · Como administrador, quiero consultar el inventario valorizado por
           obra, para saber cuánto capital está detenido en almacén.
   T-318 (pantalla) · T-319 (conexión con la API real) · diseño T-315

   Criterios de la tarjeta y dónde se cumplen:
   - Cantidad, costo unitario promedio y valor por insumo → tabla «Por insumo»,
     tal como los calcula el servidor (cantidad × promedio ponderado, NIC 2).
   - Por almacén y consolidado por proyecto → selector «Todos los almacenes» o
     uno; con «todos», además, el peso de cada almacén.
   - Indica la fecha de corte → visible arriba; se puede pedir a una fecha pasada
     (p. ej. el cierre de una semana). Si piden una futura, el servidor usa
     «ahora» y la pantalla lo dice.
   Nada se suma en la web: la pantalla solo ordena y filtra lo que llegó.
   ───────────────────────────────────────────────────────────────────────── */
import { useEffect, useState } from 'react'
import { ErrorApi } from '../api/cliente'
import { almacenesDeLaObra, fechaCorte, inventario, type Inventario as Inv } from '../api/costo'
import { usaApiReal } from '../api/http'
import { Chip, Vacio } from '../ui/base'
import { Aviso, Chips, Encabezado, Kpi, TituloSeccion, Vidrio } from '../ui/piezas'
import { GraficoReparto } from '../ui/GraficosPerezosos'
import { EsqueletoKpis } from '../ui/Esqueleto'
import { BotonExportar } from '../ui/BotonExportar'
import { TablaDatos } from '../ui/TablaDatos'

const soles = (v: number, dec = 2) => v.toLocaleString('es-PE', { minimumFractionDigits: dec, maximumFractionDigits: dec })
const cant = (v: number) => v.toLocaleString('es-PE', { maximumFractionDigits: 4 })
const hoyLima = () => new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' })   // AAAA-MM-DD

export function Inventario() {
  const [almacenes, setAlmacenes] = useState<{ id: number; nombre: string }[]>([])
  const [almacen, setAlmacen] = useState('todos')
  const [fecha, setFecha] = useState('')             // vacío = ahora
  const [v, setV] = useState<Inv | null>(null)
  const [error, setError] = useState('')

  useEffect(() => { if (usaApiReal) almacenesDeLaObra().then(setAlmacenes).catch(() => setAlmacenes([])) }, [])
  useEffect(() => {
    if (!usaApiReal) return
    setV(null); setError('')
    inventario(almacen, fecha || undefined).then(setV)
      .catch((e) => setError(e instanceof ErrorApi && e.codigo === 'sin_permiso'
        ? 'El inventario valorizado lo consultan el residente, el administrador y el gerente. Las cantidades del almacén están en «Ingresos y salidas».'
        : e instanceof ErrorApi ? e.message : 'No se pudo calcular el inventario.'))
  }, [almacen, fecha])

  const filas = v?.porInsumo ?? []
  const nombreAlmacen = almacen === 'todos' ? 'Todos los almacenes (consolidado de la obra)' : almacenes.find((a) => String(a.id) === almacen)?.nombre ?? `Almacén ${almacen}`

  return (
    <>
      <div className="d-flex flex-column gap-3 pb-4">
        <Encabezado titulo="Inventario valorizado"
          sub="Cuánto capital está detenido en el almacén: cantidad × costo promedio ponderado de cada insumo, a una fecha de corte." />
        <Chips>
          <Chip tono="neutro">Lo calcula el servidor desde el libro de movimientos</Chip>
          <Chip tono="neutro">Costo promedio ponderado (NIC 2) · sin IGV</Chip>
        </Chips>

        {!usaApiReal ? (
          <Vidrio><Vacio titulo="Necesita el servidor" detalle="Configura VITE_API_URL e ingresa con tu usuario: el inventario valorizado no se simula." /></Vidrio>
        ) : (
          <>
            <Vidrio estilo={{ display: 'flex', flexWrap: 'wrap', gap: 14, alignItems: 'flex-end' }} etiqueta="Qué consultar">
              <label className="d-flex flex-column gap-2 small fw-semibold text-body-secondary" style={{ flex: '2 1 260px' }}>
                Almacén
                <select className="form-select" aria-label="Almacén" value={almacen} onChange={(e) => setAlmacen(e.target.value)}
                   style={{ height: 44 }}>
                  <option value="todos">Todos los almacenes (consolidado)</option>
                  {almacenes.map((a) => <option key={a.id} value={String(a.id)}>{a.nombre}</option>)}
                </select>
              </label>
              <label className="d-flex flex-column gap-2 small fw-semibold text-body-secondary" style={{ flex: '1 1 180px' }}>
                Fecha de corte
                <input type="date" className="form-control" aria-label="Fecha de corte" value={fecha} max={hoyLima()}
                  onChange={(e) => setFecha(e.target.value)}  style={{ height: 44 }} />
              </label>
              {fecha && (
                <button type="button" onClick={() => setFecha('')}
                  className="btn btn-outline-secondary">
                  Volver a «ahora»
                </button>
              )}
            </Vidrio>

            {error && <Aviso tono="grave" rol="alert">{error}</Aviso>}
            {!v && !error && <EsqueletoKpis cuantas={3} />}

            {v && (
              <>
                {v.corteAjustado && <Aviso tono="info" rol="status">Pediste una fecha futura: se usó el stock de ahora.</Aviso>}
                <div className="d-flex flex-wrap gap-3">
                  <Kpi ilustracion="grua" ayuda="capital detenido" rotulo="Capital detenido en almacén" prefijo="S/" valor={soles(v.valorTotal, 0)} pie={nombreAlmacen} />
                  <Kpi ilustracion="cemento" rotulo="Insumos con stock" valor={String(v.porInsumo.length)} pie="con saldo distinto de cero" />
                  <Kpi ilustracion="calendario" rotulo="Fecha de corte" valor={fechaCorte(v.corte).split(',')[0]} pie={`hasta las ${fechaCorte(v.corte).split(', ')[1] ?? ''} (hora de Lima)`} />
                </div>

                {almacen === 'todos' && v.porAlmacen.length > 1 && (
                  <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 10 }} etiqueta="Por almacén">
                    <TituloSeccion>Por almacén</TituloSeccion>
                    <GraficoReparto partes={v.porAlmacen.map((a) => ({ etiqueta: a.nombre, valor: a.valor }))} />
                    {v.porAlmacen.map((a) => (
                      <div key={a.id} className="d-flex flex-column gap-2">
                        <div className="d-flex flex-wrap justify-content-between gap-2">
                          <button type="button" onClick={() => setAlmacen(String(a.id))}
                            className="btn btn-link p-0 fw-bold text-start text-decoration-none">
                            {a.nombre}
                          </button>
                          <span className="n text-body-secondary"><b>S/ {soles(a.valor, 0)}</b> · {a.insumos} insumos · {a.porcentaje.toLocaleString('es-PE')} %</span>
                        </div>
                        <div aria-hidden className="rounded-1" style={{ height: 8, background: 'var(--separador)' }}>
                          <div style={{ width: `${Math.min(100, a.porcentaje)}%`, height: '100%', borderRadius: 4, background: 'var(--accion)' }} />
                        </div>
                      </div>
                    ))}
                  </Vidrio>
                )}

                <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Por insumo">
                  <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
                    <TituloSeccion>Por insumo · de mayor a menor valor</TituloSeccion>
                    <BotonExportar
                      titulo="Inventario valorizado"
                      subtitulo={`${nombreAlmacen} · corte ${fechaCorte(v.corte)} (hora de Lima)`}
                      filas={filas}
                      columnas={[
                        { titulo: 'SKU', valor: (i) => i.codigo, ancho: 12 },
                        { titulo: 'Insumo', valor: (i) => i.descripcion, ancho: 45 },
                        { titulo: 'Unidad', valor: (i) => i.unidad, ancho: 9 },
                        { titulo: 'Cantidad', valor: (i) => i.cantidad, formato: 'decimal' },
                        { titulo: 'Costo promedio (S/)', valor: (i) => i.costoPromedio, formato: 'soles' },
                        { titulo: 'Valor (S/)', valor: (i) => i.valor, formato: 'soles' },
                        { titulo: '% del total', valor: (i) => i.porcentaje, formato: 'decimal' },
                      ]}
                    />
                  </div>
                  <TablaDatos
                    etiqueta="Inventario por insumo"
                    filas={v.porInsumo}
                    clave={(i) => i.codigo}
                    buscar={(i) => `${i.descripcion} ${i.codigo}`}
                    marcadorBusqueda="Buscar por nombre o código…"
                    ordenInicial={{ clave: 'valor', dir: 'desc' }}
                    vacio={{ titulo: 'Sin stock a esa fecha', detalle: 'No hay insumos con saldo en el almacén elegido a la fecha de corte.' }}
                    columnas={[
                      { clave: 'insumo', titulo: 'Insumo', valor: (i) => i.descripcion, celda: (i) => <><strong>{i.descripcion}</strong><div className="small text-body-secondary">SKU {i.codigo}</div></> },
                      { clave: 'cantidad', titulo: 'Cantidad', numerica: true, valor: (i) => i.cantidad, celda: (i) => <span className="text-nowrap">{cant(i.cantidad)} {i.unidad}</span> },
                      { clave: 'promedio', titulo: 'Costo promedio', numerica: true, valor: (i) => i.costoPromedio, celda: (i) => i.costoPromedio === null ? '—' : `S/ ${soles(i.costoPromedio)}` },
                      { clave: 'valor', titulo: 'Valor', numerica: true, valor: (i) => i.valor, celda: (i) => <strong className="text-nowrap">S/ {soles(i.valor)}</strong> },
                      { clave: 'pct', titulo: '% del total', numerica: true, valor: (i) => i.porcentaje, celda: (i) => `${i.porcentaje.toLocaleString('es-PE')} %` },
                      { clave: 'ultimo', titulo: 'Último movimiento', numerica: true, soloEscritorio: true, valor: (i) => i.ultimoMovimiento, celda: (i) => <span className="text-nowrap">{fechaCorte(i.ultimoMovimiento).split(',')[0]}</span> },
                    ]}
                  />
                  <p className="small text-body-secondary">{v.criterio} {v.declaracion}</p>
                </Vidrio>
              </>
            )}
          </>
        )}
      </div>
    </>
  )
}
