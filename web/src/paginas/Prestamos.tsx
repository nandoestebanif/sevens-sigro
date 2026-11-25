/* SIGRO · préstamo y devolución de herramientas y equipos (dentro de «Herramientas y equipos»).
   Quién tiene cada herramienta, desde cuándo y si ya debió devolverla. Prestar y devolver: el almacenero. */
import { useEffect, useMemo, useState } from 'react'
import { ErrorApi } from '../api/cliente'
import { manoDeObra, type Trabajador } from '../api/manoDeObra'
import { puedo } from '../api/permisos'
import { prestamos, prestadoPorInsumo, TEXTO_DEVOLUCION, type EstadoDevolucion, type Prestamo } from '../api/prestamos'
import { filtrarNumero, MAX_MOTIVO } from '../formularios/reglas'
import { Boton, Campo, Chip, Modal } from '../ui/base'
import { TituloSeccion, Vidrio } from '../ui/piezas'
import { useActualizacion, EnVivo } from '../ui/Actualizacion'
import { BotonExportar } from '../ui/BotonExportar'
import type { Existencia } from './existencias-reglas'

const dia = (iso: string | null) => (iso ? new Date(iso.length === 10 ? `${iso}T12:00:00` : iso).toLocaleDateString('es-PE', { timeZone: 'America/Lima' }) : '—')
const cifra = (n: number) => n.toLocaleString('es-PE', { maximumFractionDigits: 4 })

/** Opciones para prestar: cada herramienta en cada almacén donde hay, con lo disponible (saldo − prestado). */
export function opcionesPrestamo(herramientas: readonly Existencia[], abiertos: readonly Prestamo[]) {
  const prestado = new Map<string, number>()
  for (const p of abiertos) if (!p.devueltoEn) prestado.set(`${p.insumoId}-${p.almacenId}`, (prestado.get(`${p.insumoId}-${p.almacenId}`) ?? 0) + p.cantidad)
  return herramientas.flatMap((h) => h.almacenes.filter((a) => a.cantidad > 0 && a.almacenId != null).map((a) => {
    const disponible = Math.max(0, a.cantidad - (prestado.get(`${h.insumoId}-${a.almacenId}`) ?? 0))
    return { clave: `${h.insumoId}-${a.almacenId}`, insumoId: h.insumoId, almacenId: a.almacenId as number, disponible, unidad: h.unidad,
      texto: `${h.descripcion} · ${a.almacen} · ${cifra(disponible)} ${h.unidad} disponibles` }
  }))
}

export function PanelPrestamos({ herramientas }: { herramientas: readonly Existencia[] }) {
  const [lista, setLista] = useState<Prestamo[] | null>(null)
  const [verTodos, setVerTodos] = useState(false)
  const [prestando, setPrestando] = useState(false)
  const [devolviendo, setDevolviendo] = useState<Prestamo | null>(null)
  const [aviso, setAviso] = useState('')
  const [error, setError] = useState('')
  const puedePrestar = puedo('herramienta.prestar')
  const vivo = useActualizacion(() => prestamos.listar('todos').then((r) => { setLista(r.prestamos); setError('') })
    .catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudieron consultar los préstamos.')))

  const abiertos = useMemo(() => (lista ?? []).filter((p) => !p.devueltoEn), [lista])
  const visibles = verTodos ? lista ?? [] : abiertos
  const atrasados = abiertos.filter((p) => p.atrasado).length
  const porInsumo = prestadoPorInsumo(abiertos)

  return (
    <Vidrio etiqueta="Préstamos de herramientas" estilo={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div className="d-flex flex-wrap justify-content-between align-items-end gap-2">
        <div>
          <TituloSeccion>Préstamos · {abiertos.length} sin devolver{atrasados ? ` · ${atrasados} atrasado${atrasados === 1 ? '' : 's'}` : ''}</TituloSeccion>
          <p className="small text-body-secondary mb-0">Quién tiene cada herramienta. Prestar no mueve el saldo: la herramienta sigue en la obra.</p>
        </div>
        <div className="d-flex flex-wrap align-items-center gap-2">
          <EnVivo {...vivo} />
          <div className="form-check form-switch mb-0">
            <input className="form-check-input" type="checkbox" id="prest-todos" checked={verTodos} onChange={(e) => setVerTodos(e.target.checked)} />
            <label className="form-check-label small" htmlFor="prest-todos">Ver también los devueltos</label>
          </div>
          <BotonExportar
            titulo="Préstamos de herramientas"
            subtitulo={`${abiertos.length} sin devolver${atrasados ? ` · ${atrasados} atrasados` : ''}`}
            filas={visibles}
            columnas={[
              { titulo: 'Código', valor: (p) => p.insumoCodigo, ancho: 14 },
              { titulo: 'Herramienta', valor: (p) => p.descripcion, ancho: 34 },
              { titulo: 'Almacén', valor: (p) => p.almacen, ancho: 18 },
              { titulo: 'Cantidad', valor: (p) => p.cantidad, formato: 'decimal' },
              { titulo: 'Quién la tiene', valor: (p) => p.responsable, ancho: 26 },
              { titulo: 'Desde', valor: (p) => dia(p.prestadoEn) },
              { titulo: 'Devolver el', valor: (p) => dia(p.devolverEl) },
              { titulo: 'Estado', valor: (p) => (p.devueltoEn ? `${TEXTO_DEVOLUCION[p.estadoDevolucion!]} (${dia(p.devueltoEn)})` : p.atrasado ? 'Atrasada' : 'Prestada'), ancho: 22 },
              { titulo: 'Nota', valor: (p) => p.notaDevolucion ?? p.nota ?? '', ancho: 30 },
            ]}
          />
          {puedePrestar && <Boton enClic={() => { setAviso(''); setPrestando(true) }}>+ Prestar herramienta</Boton>}
        </div>
      </div>
      {error && <div role="alert" className="small text-danger">{error}</div>}
      {aviso && <div role="status" className="small text-success-emphasis">{aviso}</div>}
      {porInsumo.size > 0 && (
        <div className="d-flex flex-wrap gap-2">
          {herramientas.filter((h) => porInsumo.has(h.insumoId)).map((h) => (
            <Chip key={h.insumoId} tono="info">{h.descripcion}: {cifra(porInsumo.get(h.insumoId)!)} prestadas de {cifra(h.cantidad)}</Chip>
          ))}
        </div>
      )}
      {lista && visibles.length === 0 ? (
        <p className="small text-body-secondary mb-0">{verTodos ? 'Aún no se prestó ninguna herramienta.' : 'Todas las herramientas están en el almacén.'}</p>
      ) : (
        <div className="table-responsive">
          <table className="table table-sm align-middle mb-0">
            <thead><tr>
              <th>Herramienta</th><th className="text-end">Cantidad</th><th>Quién la tiene</th><th>Desde</th><th>Devolver el</th><th>Estado</th>{puedePrestar && <th />}
            </tr></thead>
            <tbody>
              {visibles.map((p) => (
                <tr key={p.id}>
                  <td><strong>{p.descripcion}</strong><div className="small text-body-secondary">{p.insumoCodigo} · {p.almacen}</div></td>
                  <td className="text-end n">{cifra(p.cantidad)} <span className="small text-body-secondary">{p.unidad}</span></td>
                  <td>{p.responsable}{p.nota && <div className="small text-body-secondary">{p.nota}</div>}</td>
                  <td className="n small">{dia(p.prestadoEn)}</td>
                  <td className="n small">{dia(p.devolverEl)}</td>
                  <td>
                    {p.devueltoEn
                      ? <Chip tono={p.estadoDevolucion === 'bueno' ? 'bien' : 'grave'}>{TEXTO_DEVOLUCION[p.estadoDevolucion!]} · {dia(p.devueltoEn)}</Chip>
                      : p.atrasado ? <Chip tono="grave">Atrasada</Chip> : <Chip tono="aviso">Prestada</Chip>}
                    {p.notaDevolucion && <div className="small text-body-secondary">{p.notaDevolucion}</div>}
                  </td>
                  {puedePrestar && <td className="text-end">{!p.devueltoEn && <Boton tipo="secundario" enClic={() => { setAviso(''); setDevolviendo(p) }}>Devolver</Boton>}</td>}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {prestando && <ModalPrestar opciones={opcionesPrestamo(herramientas, abiertos)} enCerrar={() => setPrestando(false)}
        enHecho={(p) => { setPrestando(false); setAviso(`Prestado: ${cifra(p.cantidad)} ${p.unidad} de ${p.descripcion} a ${p.responsable}.`); vivo.actualizar() }} />}
      {devolviendo && <ModalDevolver prestamo={devolviendo} enCerrar={() => setDevolviendo(null)}
        enHecho={(texto) => { setDevolviendo(null); setAviso(texto); vivo.actualizar() }} />}
    </Vidrio>
  )
}

function ModalPrestar({ opciones, enCerrar, enHecho }: {
  opciones: ReturnType<typeof opcionesPrestamo>; enCerrar: () => void; enHecho: (p: Prestamo) => void
}) {
  const [clave, setClave] = useState(opciones.find((o) => o.disponible > 0)?.clave ?? '')
  const [cantidad, setCantidad] = useState('1')
  const [responsable, setResponsable] = useState('')
  const [trabajadores, setTrabajadores] = useState<Trabajador[]>([])
  const [devolverEl, setDevolverEl] = useState('')
  const [nota, setNota] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  useEffect(() => { manoDeObra.trabajadores().then(setTrabajadores).catch(() => setTrabajadores([])) }, [])
  const op = opciones.find((o) => o.clave === clave)
  const n = Number(cantidad.replace(',', '.'))
  const errCant = !cantidad ? 'Escribe la cantidad.' : !(n > 0) ? 'Debe ser mayor que cero.' : op && n > op.disponible ? `Solo hay ${cifra(op.disponible)} disponibles.` : null
  const errResp = responsable.trim().length < 3 ? 'Escribe quién se la lleva (mínimo 3 letras).' : null

  const guardar = () => {
    if (!op || errCant || errResp) { setError(errCant ?? errResp ?? 'Elige la herramienta.'); return }
    const t = trabajadores.find((x) => x.nombre === responsable.trim())
    setGuardando(true); setError('')
    prestamos.prestar({ insumoId: op.insumoId, almacenId: op.almacenId, cantidad, responsable: responsable.trim(), trabajadorId: t?.id, devolverEl, nota })
      .then(enHecho)
      .catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo registrar el préstamo.'))
      .finally(() => setGuardando(false))
  }

  return (
    <Modal titulo="Prestar herramienta" enCerrar={enCerrar} ancho={560}>
      {opciones.length === 0 ? <p className="mb-0">No hay herramientas con saldo en los almacenes de la obra.</p> : (
        <div className="d-flex flex-column gap-3">
          <div>
            <label htmlFor="prest-herr" className="form-label small fw-semibold">Herramienta y almacén</label>
            <select id="prest-herr" className="form-select" value={clave} onChange={(e) => setClave(e.target.value)}>
              {opciones.map((o) => <option key={o.clave} value={o.clave} disabled={o.disponible <= 0}>{o.texto}</option>)}
            </select>
          </div>
          <Campo id="prest-cant" etiqueta={`Cantidad${op ? ` (${op.unidad})` : ''}`} valor={cantidad} enCambio={(x) => setCantidad(filtrarNumero(cantidad, x))}
            modoTeclado="decimal" error={cantidad ? errCant ?? undefined : undefined} />
          <div>
            <label htmlFor="prest-resp" className="form-label small fw-semibold">Quién se la lleva</label>
            <input id="prest-resp" className="form-control" list="prest-trab" value={responsable} maxLength={120}
              onChange={(e) => setResponsable(e.target.value)} placeholder="Elige un trabajador o escribe el nombre" />
            <datalist id="prest-trab">{trabajadores.map((t) => <option key={t.id} value={t.nombre} />)}</datalist>
          </div>
          <div>
            <label htmlFor="prest-fecha" className="form-label small fw-semibold">Devolver el (opcional)</label>
            <input id="prest-fecha" type="date" className="form-control" value={devolverEl} onChange={(e) => setDevolverEl(e.target.value)} />
            <div className="form-text">Si pasa la fecha sin devolverla, se marca «Atrasada».</div>
          </div>
          <Campo id="prest-nota" etiqueta="Nota (opcional)" valor={nota} enCambio={setNota} maxLargo={MAX_MOTIVO} marcador="p. ej. para el encofrado del 3.er piso" />
          {error && <div role="alert" className="small text-danger">{error}</div>}
          <div className="d-flex justify-content-end gap-2">
            <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
            <Boton enClic={guardar} deshabilitado={guardando}>{guardando ? 'Registrando…' : 'Prestar'}</Boton>
          </div>
        </div>
      )}
    </Modal>
  )
}

function ModalDevolver({ prestamo, enCerrar, enHecho }: { prestamo: Prestamo; enCerrar: () => void; enHecho: (texto: string) => void }) {
  const [estado, setEstado] = useState<EstadoDevolucion>('bueno')
  const [nota, setNota] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const exigeNota = estado !== 'bueno'
  const guardar = () => {
    if (exigeNota && !nota.trim()) { setError('Si vuelve dañada o se perdió, escribe qué pasó: queda como constancia.'); return }
    setGuardando(true); setError('')
    prestamos.devolver(prestamo.id, estado, nota)
      .then((r) => enHecho([`Devuelta: ${prestamo.descripcion} (${TEXTO_DEVOLUCION[estado].toLowerCase()}).`, ...r.advertencias].join(' ')))
      .catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo registrar la devolución.'))
      .finally(() => setGuardando(false))
  }
  return (
    <Modal titulo={`Devolver · ${prestamo.descripcion}`} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <p className="small text-body-secondary mb-0">{cifra(prestamo.cantidad)} {prestamo.unidad} prestadas a {prestamo.responsable} el {dia(prestamo.prestadoEn)}. La devolución se registra una sola vez.</p>
        <div role="radiogroup" aria-label="¿Cómo vuelve?" className="d-flex flex-wrap gap-3">
          {(Object.keys(TEXTO_DEVOLUCION) as EstadoDevolucion[]).map((k) => (
            <div key={k} className="form-check">
              <input className="form-check-input" type="radio" name="dev-estado" id={`dev-${k}`} checked={estado === k} onChange={() => setEstado(k)} />
              <label className="form-check-label" htmlFor={`dev-${k}`}>{TEXTO_DEVOLUCION[k]}</label>
            </div>
          ))}
        </div>
        <Campo id="dev-nota" etiqueta={exigeNota ? 'Qué pasó (obligatorio)' : 'Nota (opcional)'} valor={nota} enCambio={setNota} maxLargo={MAX_MOTIVO}
          marcador={estado === 'perdido' ? 'p. ej. se perdió en el frente B' : 'p. ej. mango rajado'} />
        {error && <div role="alert" className="small text-danger">{error}</div>}
        <div className="d-flex justify-content-end gap-2">
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={guardando}>{guardando ? 'Registrando…' : 'Registrar devolución'}</Boton>
        </div>
      </div>
    </Modal>
  )
}
