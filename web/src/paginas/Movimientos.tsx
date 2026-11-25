/* ─────────────────────────────────────────────────────────────────────────────
   HU-43 · Como residente de obra, quiero consultar el stock de cada insumo en
           cualquier momento, para programar la jornada sabiendo con qué cuento.
   HU-45 · Como almacenero, quiero corregir un movimiento sin borrar el
           original, para conservar el rastro de lo registrado y lo corregido.
   Lienzo W7 «Ingresos y salidas» (estilo vidrio): stock y kárdex con reverso.

   Criterios de HU-43:
   - Saldo por almacén y consolidado por proyecto.
   - Fecha y hora del último movimiento considerado (la hora del servidor).
   - Señala los insumos por debajo de su punto de reposición.
   Criterios de HU-45:
   - La corrección genera un movimiento de reverso que apunta al original.
   - El original nunca se modifica ni se elimina (anuladoPor se deriva al leer).
   - El saldo se recalcula considerando ambos movimientos (lo hace la capa API).
   - La consulta permite ver el movimiento anulado y el que lo anuló.
   ───────────────────────────────────────────────────────────────────────── */
import { puedo } from '../api/permisos'
import { diaHoraLima } from '../api/horaLima'
import { usaApiReal } from '../api/http'
import { useEffect, useState, useMemo } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { Movimiento, StockInsumo } from '../api/tipos'
import { Chip, Modal } from '../ui/base'
import { Chips, Encabezado, Pildora, TituloSeccion, Vidrio } from '../ui/piezas'
import { useAvisos } from '../ui/Avisos'
import { Termino } from '../ui/Glosario'
import { alternarOrden, ordenar, paginar, type Orden } from '../ui/tablas'
import { Paginador, ThOrden } from '../ui/Tabla'
import { EsqueletoTabla } from '../ui/Esqueleto'
import { BotonExportar } from '../ui/BotonExportar'
import { TablaDatos } from '../ui/TablaDatos'
import { Form } from 'react-bootstrap'

const n = (v: number) => v.toLocaleString('es-PE')
const hora = (iso: string) => diaHoraLima(iso) // en hora de Lima, no en la UTC del servidor

const TIPO: Record<Movimiento['tipo'], { texto: string; signo: string }> = {
  ingreso: { texto: 'Ingreso', signo: '+' },
  salida: { texto: 'Salida', signo: '−' },
  reverso: { texto: 'Reverso', signo: '↩' },
}

function Reverso({ movimiento, enCerrar, enListo }: {
  movimiento: Movimiento
  enCerrar: () => void
  enListo: () => void
}) {
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)

  const corregir = async () => {
    setGuardando(true)
    setError('')
    try {
      await api.revertirMovimiento(movimiento.id, motivo)
      enListo()
    } catch (e) {
      if (e instanceof ErrorApi) setError(e.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo={`Corregir ${movimiento.id} con un reverso`} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <div className="py-2 px-3 bg-body-tertiary rounded-4">
          <div className="small text-body-secondary">
            <strong>{TIPO[movimiento.tipo].texto}</strong>{' '}
            · {movimiento.descripcion}<br />
            {n(movimiento.cantidad)} {movimiento.unidad} · S/ {movimiento.costoUnitario.toFixed(2)} c/u ·{' '}
            {movimiento.almacen} · {hora(movimiento.fechaServidor)}
          </div>
        </div>
        <div>
          <label htmlFor="rev-motivo" className="d-block small fw-semibold text-body-secondary mb-2">
            Motivo de la corrección (obligatorio)
          </label>
          <textarea
            id="rev-motivo"
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="p. ej. Se digitó 12; el conteo real fue 2"
            rows={2}
            className="form-control"
            style={{ height: 'auto', padding: '10px 14px', resize: 'vertical', borderColor: error ? 'var(--grave)' : undefined }}
          />
          {error && <div role="alert" className="small fw-semibold text-danger mt-2">{error}</div>}
        </div>
        <p className="small text-body-secondary">
          El original <strong>no se toca</strong>: queda en el libro con la marca de quién
          lo anuló. El reverso hereda la cantidad y el costo congelado, y el saldo se
          recalcula con ambos. Si además corresponde otra cantidad, se registra el
          movimiento correcto aparte.
        </p>
        <div className="d-flex flex-wrap gap-2 justify-content-end">
          <Pildora tipo="secundario" enClic={enCerrar}>Cancelar</Pildora>
          <Pildora enClic={corregir} deshabilitado={guardando}>
            {guardando ? 'Registrando…' : 'Registrar el reverso'}
          </Pildora>
        </div>
      </div>
    </Modal>
  )
}

/* orden del kárdex por columna: fuera del componente, así useMemo no depende de una función nueva en cada render */
type ColKardex = 'id' | 'fecha' | 'tipo' | 'insumo' | 'cantidad' | 'importe'
const valorKardex = (m: Movimiento, k: ColKardex): string | number => ({
  id: m.id, fecha: m.fechaServidor, tipo: m.tipo, insumo: m.descripcion, cantidad: m.cantidad,
  importe: m.importe ?? m.cantidad * m.costoUnitario,
})[k]

export function Movimientos() {
  const [stock, setStock] = useState<StockInsumo[] | null>(null)
  const [kardex, setKardex] = useState<Movimiento[] | null>(null)
  const [corrigiendo, setCorrigiendo] = useState<Movimiento | null>(null)
  const avisar = useAvisos()

  const cargar = () => {
    api.consultarStock().then(setStock)
    api.listarMovimientos().then(setKardex)
  }
  useEffect(() => {
    cargar()
  }, [])

  const corte = kardex?.[0]?.fechaServidor
  const [soloBajos, setSoloBajos] = useState(false)
  const bajos = (stock ?? []).filter((s) => s.bajoPunto).length
  const ultimo = (stock ?? []).reduce<string | null>((m, s) => (!m || s.ultimoMovimiento > m ? s.ultimoMovimiento : m), null)

  // Orden y paginación del kárdex: se ordena la lista completa y luego se corta la página.
  const [orden, setOrden] = useState<Orden<ColKardex> | null>(null)
  const [pagina, setPagina] = useState(1)
  const [tam, setTam] = useState(25)
  const pag = useMemo(() => paginar(ordenar(kardex ?? [], orden, valorKardex), pagina, tam), [kardex, orden, pagina, tam])
  const enOrdenar = (k: ColKardex) => { setOrden((o) => alternarOrden(o, k)); setPagina(1) }

  return (
    <>
    <div className="d-flex flex-column gap-3 pb-4">
      <Encabezado
        titulo="Ingresos y salidas"
        sub={`El movimiento es un libro que no se borra: corregir es registrar un reverso. ${corte ? `Último movimiento considerado: ${hora(corte)} (hora del servidor).` : ''}`}
      />

      <Chips>
        {usaApiReal
          ? <Chip tono="bien">Movimientos y stock: del servidor</Chip>
          : <Chip tono="info">Movimientos y stock: datos de ejemplo · insumos del S10 real</Chip>}
        <Chip tono="neutro">El saldo lo calcula el servidor</Chip>
        <Chip tono="neutro">Costo promedio ponderado (NIC 2)</Chip>
      </Chips>

      {/* ── HU-43 · stock por almacén y consolidado ── */}
      <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Stock al momento">
        <div className="d-flex flex-wrap justify-content-between align-items-baseline gap-2">
          <TituloSeccion>Stock al momento</TituloSeccion>
          <span className="small text-body-secondary">el saldo se deriva de los movimientos: no es un campo editable</span>
        </div>
        {stock === null ? (
          <EsqueletoTabla />
        ) : (
          <>
            {/* fase 3 (25/11): de un vistazo, antes de la tabla */}
            <div className="sigro-cifras">
              <div><strong className="n">{stock.filter((s) => s.saldoTotal > 0).length}</strong><span>insumos con saldo</span></div>
              <div className={bajos ? 'text-danger' : undefined}><strong className="n">{bajos}</strong><span>bajo el punto de reposición</span></div>
              <div><strong className="n">{new Set(stock.flatMap((s) => s.porAlmacen.map((a) => a.almacen))).size}</strong><span>almacenes con movimiento</span></div>
              <div><strong className="n">{ultimo ? hora(ultimo) : '—'}</strong><span>último movimiento</span></div>
            </div>
            <TablaDatos
              etiqueta="Stock al momento"
              filas={soloBajos ? stock.filter((s) => s.bajoPunto) : stock}
              clave={(s) => s.insumoId}
              buscar={(s) => `${s.descripcion} ${s.insumoId} ${s.porAlmacen.map((a) => a.almacen).join(' ')}`}
              marcadorBusqueda="Buscar por insumo, SKU o almacén…"
              ordenInicial={{ clave: 'insumo', dir: 'asc' }}
              filtros={<Form.Check type="switch" id="stock-bajos" label={`Solo bajo el punto · ${bajos}`} checked={soloBajos} onChange={(e) => setSoloBajos(e.target.checked)} />}
              vacio={{ titulo: soloBajos ? 'Nada bajo el punto' : 'Todavía no hay stock', detalle: soloBajos ? 'Ningún insumo está por debajo de su punto de reposición.' : 'El stock aparece con el primer ingreso al almacén.' }}
              columnas={[
                { clave: 'insumo', titulo: 'Insumo', valor: (s) => s.descripcion, celda: (s) => (
                  <>
                    <span className="d-inline-flex align-items-center gap-2 flex-wrap fw-semibold">{s.descripcion}{s.bajoPunto && <Chip tono="grave">bajo el punto</Chip>}</span>
                    <div className="n small text-body-secondary mt-1 text-start">SKU {s.insumoId}</div>
                  </>
                ) },
                { clave: 'almacenes', titulo: 'Por almacén', soloEscritorio: true, valor: (s) => s.porAlmacen.length, celda: (s) => (
                  <div className="text-body-secondary">{s.porAlmacen.map((a) => <div key={a.almacen} className="n text-start text-nowrap">{a.almacen}: <strong>{n(a.saldo)}</strong> {s.unidad}</div>)}</div>
                ) },
                { clave: 'saldo', titulo: 'Consolidado', numerica: true, valor: (s) => s.saldoTotal, celda: (s) => (
                  <><strong className="text-nowrap">{n(s.saldoTotal)} {s.unidad}</strong><div className="small text-body-secondary text-nowrap">mov. {hora(s.ultimoMovimiento)}</div></>
                ) },
                { clave: 'punto', titulo: 'Punto de reposición', numerica: true, valor: (s) => s.puntoReposicion ?? -1, celda: (s) => (
                  <span className="text-nowrap" style={{ color: s.bajoPunto ? 'var(--grave)' : 'var(--tinta-2)', fontWeight: s.bajoPunto ? 700 : 400 }}>{s.puntoReposicion === null ? '—' : `${n(s.puntoReposicion)} ${s.unidad}`}</span>
                ) },
              ]}
            />
          </>
        )}
      </Vidrio>

      {/* ── HU-45 · kárdex con reverso ── */}
      <Vidrio estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }} etiqueta="Kárdex">
        <div className="d-flex flex-wrap justify-content-between align-items-baseline gap-2">
          <TituloSeccion>Kárdex · los movimientos del libro</TituloSeccion>
          <div className="d-flex align-items-center gap-3">
            <span className="small text-body-secondary">aquí no hay editar ni eliminar: solo corregir con reverso</span>
            {/* se exporta en el orden elegido y completo (todas las páginas) */}
            <BotonExportar
              titulo="Kárdex de la obra"
              subtitulo="Libro de movimientos · costo congelado al instante de cada movimiento"
              filas={ordenar(kardex ?? [], orden, valorKardex)}
              columnas={[
                { titulo: 'Mov.', valor: (m) => m.id, ancho: 9 },
                { titulo: 'Fecha (servidor)', valor: (m) => hora(m.fechaServidor), ancho: 16 },
                { titulo: 'Tipo', valor: (m) => TIPO[m.tipo].texto, ancho: 10 },
                { titulo: 'Insumo', valor: (m) => m.descripcion, ancho: 40 },
                { titulo: 'Motivo', valor: (m) => m.motivo, ancho: 30 },
                { titulo: 'Almacén', valor: (m) => m.almacen },
                { titulo: 'Cantidad', valor: (m) => m.cantidad, formato: 'decimal' },
                { titulo: 'Unidad', valor: (m) => m.unidad, ancho: 9 },
                { titulo: 'Costo congelado (S/)', valor: (m) => m.costoUnitario, formato: 'soles' },
                { titulo: 'Importe (S/)', valor: (m) => m.importe ?? Number((m.cantidad * m.costoUnitario).toFixed(2)), formato: 'soles' },
                { titulo: 'Anulado por', valor: (m) => m.anuladoPor },
              ]}
            />
          </div>
        </div>
        {kardex === null ? (
          <EsqueletoTabla columnas={6} />
        ) : (
          <>
          <div className="overflow-x-auto">
            <table className="table table-hover align-middle mb-0" style={{ minWidth: 860 }}>
              <thead>
                <tr>
                  <ThOrden clave="id" orden={orden} enOrdenar={enOrdenar}>Mov.</ThOrden>
                  <ThOrden clave="fecha" orden={orden} enOrdenar={enOrdenar}>Fecha (servidor)</ThOrden>
                  <ThOrden clave="tipo" orden={orden} enOrdenar={enOrdenar}>Tipo</ThOrden>
                  <ThOrden clave="insumo" orden={orden} enOrdenar={enOrdenar}>Insumo y motivo</ThOrden>
                  <ThOrden clave="cantidad" orden={orden} enOrdenar={enOrdenar} className="n">Cantidad</ThOrden><th className="n"><Termino clave="costo congelado">Costo cong.</Termino></th><ThOrden clave="importe" orden={orden} enOrdenar={enOrdenar} className="n">Importe</ThOrden><th aria-label="Acción" />
                </tr>
              </thead>
              <tbody>
                {pag.items.map((m) => {
                  const anulado = m.anuladoPor !== null
                  const importe = m.importe ?? Number((m.cantidad * m.costoUnitario).toFixed(2))
                  return (
                    <tr key={m.id} style={{ background: m.tipo === 'reverso' ? 'var(--aviso-tinte)' : undefined }}>
                      <td className="n text-body-secondary text-nowrap text-start">{m.id}</td>
                      <td className="n text-body-secondary text-nowrap text-start">{hora(m.fechaServidor)}</td>
                      <td className="text-nowrap">
                        <Chip tono={m.tipo === 'ingreso' ? 'bien' : m.tipo === 'salida' ? 'marca' : 'aviso'}>
                          {TIPO[m.tipo].texto}
                        </Chip>
                      </td>
                      <td style={{ minWidth: 260 }}>
                        <div style={{ fontWeight: 600, textDecoration: anulado ? 'line-through' : 'none', color: anulado ? 'var(--tinta-2)' : 'var(--tinta)' }}>
                          {m.descripcion}
                        </div>
                        <div className="small text-body-secondary mt-1">
                          {m.motivo} · {m.almacen} · {m.autor}
                          {m.semana != null && ` · Semana ${m.semana}`}
                          {m.reversaA && <strong> · anula a {m.reversaA}</strong>}
                          {anulado && <strong className="text-warning-emphasis"> · anulado por {m.anuladoPor}</strong>}
                        </div>
                      </td>
                      <td className="n" style={{ fontWeight: 700, whiteSpace: 'nowrap', textDecoration: anulado ? 'line-through' : 'none', color: anulado ? 'var(--tinta-2)' : m.tipo === 'ingreso' ? 'var(--bien)' : m.tipo === 'salida' ? 'var(--grave)' : 'var(--tinta)' }}>
                        {TIPO[m.tipo].signo} {n(m.cantidad)} {m.unidad}
                      </td>
                      <td className="n text-nowrap">S/ {m.costoUnitario.toFixed(2)}</td>
                      <td className="n" style={{ fontWeight: 700, whiteSpace: 'nowrap', textDecoration: anulado ? 'line-through' : 'none', color: anulado ? 'var(--tinta-2)' : 'var(--tinta)' }}>
                        S/ {importe.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                      </td>
                      <td className="text-end">
                        {m.tipo !== 'reverso' && !anulado && puedo('movimiento.revertir') && (
                          <Pildora tipo="secundario" chica enClic={() => setCorrigiendo(m)}>Corregir</Pildora>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <Paginador p={pag} enPagina={setPagina} tam={tam} enTam={(t) => { setTam(t); setPagina(1) }} />
          </>
        )}
        <span className="small text-body-secondary">Cada salida congela el promedio de ese momento.</span>
      </Vidrio>

      <p className="small text-body-secondary px-1">
        El registro de ingresos y salidas en obra lo hace el almacenero desde el celular
        (con o sin señal); esta pantalla es la consulta de oficina. El costo de cada salida
        queda <strong>congelado</strong> al promedio vigente en ese instante (NIC 2): un
        ingreso posterior no lo cambia.
      </p>

      {corrigiendo && (
        <Reverso
          movimiento={corrigiendo}
          enCerrar={() => setCorrigiendo(null)}
          enListo={() => {
            avisar(`Reverso registrado: ${corrigiendo.id} queda en el libro, anulado, y el saldo ya se recalculó con ambos movimientos.`)
            setCorrigiendo(null)
            cargar()
          }}
        />
      )}
    </div>
    </>
  )
}
