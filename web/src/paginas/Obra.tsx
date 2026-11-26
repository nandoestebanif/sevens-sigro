/* ─────────────────────────────────────────────────────────────────────────────
   HU-01 · Como residente, quiero crear el proyecto con su nombre y su plazo,
           para acotar todo el movimiento a ese periodo.
   HU-03 · Como residente, quiero dividir el plazo en periodos semanales de
           control, para comparar lo consumido de un periodo con otro sin
           armar el corte a mano.
   (Pantalla 9 del lienzo, «Crear la obra».)

   Criterios cubiertos:
   - Nombre, inicio y fin obligatorios; fin nunca anterior al inicio (API).
   - La obra nace «en ejecución».
   - Las semanas se generan SOLAS al crear (la última puede ser más corta) y
     se ven aquí mismo: cerradas, en curso y por venir.
   - Un movimiento fuera del plazo o en semana cerrada lo rechaza el servidor;
     esta pantalla lo declara para que nadie lo descubra a la mala.
   ───────────────────────────────────────────────────────────────────────── */
import { puedo } from '../api/permisos'
import { useEffect, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { Obra, Semana } from '../api/tipos'
import { Boton, Campo, Chip, Modal, Tarjeta, Titulo } from '../ui/base'
import { obrasHttp, usaApiReal } from '../api/http'
import { plazo, type CambioPlazo } from '../api/oficina'
import { EsqueletoTabla } from '../ui/Esqueleto'
import { Controller, useForm } from 'react-hook-form'
import { PAUSA_MS, useEnVivo } from '../formularios/enVivo'
import { zodResolver } from '@hookform/resolvers/zod'
import { Button, Col, Form, Row } from 'react-bootstrap'
import { esquemaObra, type DatosObra } from '../formularios/esquemas'
import { useAvisos } from '../ui/Avisos'
import { Fecha, hoyEnLima } from '../ui/Fecha'

const f = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`

const COLOR_SEMANA: Record<Semana['estado'], { fondo: string; borde: string }> = {
  cerrada: { fondo: 'var(--pista)', borde: 'var(--borde)' },
  en_curso: { fondo: 'var(--marca)', borde: 'var(--marca)' },
  por_venir: { fondo: 'var(--tarjeta)', borde: 'var(--borde)' },
}

const DIA_MS = 86_400_000
/** Avance del plazo (días transcurridos / días totales), del calendario de Lima. Fase 3 (25/11). */
export function avancePlazo(inicio: string, fin: string, hoy: string): { pct: number; transcurridos: number; total: number; faltan: number } {
  const d = (x: string) => Date.UTC(+x.slice(0, 4), +x.slice(5, 7) - 1, +x.slice(8, 10))
  const total = Math.max(1, Math.round((d(fin) - d(inicio)) / DIA_MS) + 1)
  const transcurridos = Math.min(total, Math.max(0, Math.round((d(hoy) - d(inicio)) / DIA_MS) + 1))
  return { pct: Math.round((transcurridos / total) * 1000) / 10, transcurridos, total, faltan: total - transcurridos }
}

/** las semanas como tira de casillas: se LEE el avance del plazo de un vistazo */
function TiraSemanas({ semanas }: { semanas: Semana[] }) {
  return (
    <div className="d-flex flex-wrap gap-1 sigro-tira-semanas" role="list" aria-label="Semanas de control">
      {semanas.map((s) => (
        <span
          key={s.numero} role="listitem" className={s.numero % 4 === 1 ? 'con-numero' : undefined} data-n={s.numero}
          aria-label={`Semana ${s.numero}, ${s.estado === 'cerrada' ? 'cerrada' : s.estado === 'en_curso' ? 'en curso' : 'por venir'}`}
          title={`Semana ${s.numero} · ${f(s.inicio)} – ${f(s.fin)} · ${s.dias} día${s.dias === 1 ? '' : 's'} · ${s.estado === 'cerrada' ? 'cerrada: no admite movimientos nuevos' : s.estado === 'en_curso' ? 'en curso' : 'por venir'}`}
          style={{
            width: 18, height: 18, borderRadius: 5,
            background: COLOR_SEMANA[s.estado].fondo,
            border: `1px solid ${COLOR_SEMANA[s.estado].borde}`,
            // la última semana corta se distingue, no se esconde
            opacity: s.dias < 7 ? 0.75 : 1,
          }}
        />
      ))}
    </div>
  )
}

/* ── HU-02 · T-331: ampliar el plazo; las semanas nuevas se generan solas y las anteriores no se tocan.
      26/11 (0026): el administrador también puede RECORTARLO, con su PIN: el nuevo fin es el cierre de una semana
      que no haya pasado; las semanas siguientes quedan fuera del plazo (no se borran) y vuelven si luego se amplía. ── */
const hoyLima = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date())

function AmpliarPlazo({ obra, enListo }: { obra: Obra; enListo: () => void }) {
  const [modo, setModo] = useState<'ampliar' | 'recortar' | null>(null)
  const [fecha, setFecha] = useState('')
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [historial, setHistorial] = useState<CambioPlazo[] | null>(null)
  useEffect(() => { plazo.historial(obra.id).then(setHistorial).catch(() => setHistorial([])) }, [obra.id, obra.fin])
  const recorta = modo === 'recortar'
  // cierres de semana válidos para recortar: desde hoy, antes del fin vigente y sin semanas cerradas después
  const ultimaCerrada = Math.max(0, ...obra.semanas.filter((s) => s.estado === 'cerrada').map((s) => s.numero))
  const cortes = obra.semanas.filter((s) => s.fin >= hoyLima() && s.fin < obra.fin && s.numero >= ultimaCerrada).map((s) => s.fin)
  // fase 3: validación en vivo (como en Compras y Usuarios): al escribir, tras la pausa, y al salir
  const v = useEnVivo({
    fecha: recorta
      ? (!fecha || !cortes.includes(fecha) ? 'Elige el cierre de una semana: desde hoy y antes del fin actual.' : undefined)
      : (!fecha || fecha <= obra.fin ? `Elige una fecha posterior al fin actual (${f(obra.fin)}).` : undefined),
    motivo: motivo.trim().length < 3 ? `Escribe el motivo ${recorta ? 'del recorte' : 'de la ampliación'}: queda en el historial.` : undefined,
  })
  const cerrar = () => { setModo(null); setFecha(''); setMotivo(''); setError(''); v.reiniciar() }

  const guardar = async () => {
    setError('')
    if (v.alGuardar()) return  // true = hay errores (ya quedan a la vista)
    setGuardando(true)
    try {
      await (recorta ? plazo.recortar : plazo.ampliar)(obra.id, fecha, motivo)
      cerrar(); enListo()
    }
    catch (e) { setError(e instanceof ErrorApi ? e.message : `No se pudo ${recorta ? 'recortar' : 'ampliar'} el plazo.`) }
    finally { setGuardando(false) }
  }

  return (
    <div className="mt-3 pt-3 d-flex flex-column gap-2 border-top" >
      {!modo ? (
        <div className="d-flex align-items-center justify-content-between gap-2 flex-wrap">
          <span className="small text-body-secondary">
            ¿Cambió el plazo? No se pisa: queda el anterior, el nuevo, el motivo y quién lo cambió.
          </span>
          <div className="d-flex gap-2">
            {puedo('proyecto.recortar_plazo') && cortes.length > 0 && <Boton tipo="secundario" enClic={() => setModo('recortar')}>Recortar plazo</Boton>}
            {puedo('proyecto.ampliar_plazo') && <Boton tipo="secundario" enClic={() => setModo('ampliar')}>Ampliar plazo</Boton>}
          </div>
        </div>
      ) : (
        <div className="d-flex flex-wrap gap-3 align-items-end">
          <div className="d-flex flex-column gap-2" style={{ flex: '0 1 190px' }}>
            <label htmlFor={`amp-${obra.id}`} className="small fw-semibold text-body-secondary">{recorta ? 'Nuevo fin (cierre de semana)' : 'Nuevo fin'}</label>
            {recorta ? (
              <select id={`amp-${obra.id}`} className={`form-select${v.campo('fecha').error ? ' is-invalid' : ''}`} value={fecha}
                onChange={(e) => { setFecha(e.target.value); v.salir('fecha') }} onBlur={() => v.salir('fecha')}
                aria-describedby={v.campo('fecha').error ? `amp-${obra.id}-error` : undefined}>
                <option value="">Elige…</option>
                {cortes.map((c) => <option key={c} value={c}>{f(c)}</option>)}
              </select>
            ) : (
              <Fecha id={`amp-${obra.id}`} min={obra.fin} valor={fecha} alCambiar={(x) => { setFecha(x); v.salir('fecha') }} enSalir={() => v.salir('fecha')}
                invalido={!!v.campo('fecha').error} valido={v.campo('fecha').valido} describedBy={v.campo('fecha').error ? `amp-${obra.id}-error` : undefined} />
            )}
            {v.campo('fecha').error && <div id={`amp-${obra.id}-error`} className="invalid-feedback d-block">{v.campo('fecha').error}</div>}
          </div>
          <div className="d-flex flex-column gap-2 min-w-0"  style={{ flex: '2 1 240px' }}>
            <Campo id={`amp-m-${obra.id}`} etiqueta={recorta ? 'Motivo del recorte' : 'Motivo de la ampliación'} valor={motivo} enCambio={(x) => { setMotivo(x); v.cambio('motivo') }}
              marcador={recorta ? 'p. ej. el cliente adelantó la entrega' : 'p. ej. adenda aprobada por el cliente'} maxLargo={500} {...v.campo('motivo')} />
          </div>
          <div className="d-flex gap-2">
            <Boton tipo="secundario" enClic={cerrar}>Cancelar</Boton>
            <Boton enClic={guardar} deshabilitado={guardando}>{guardando ? (recorta ? 'Recortando…' : 'Ampliando…') : (recorta ? 'Recortar' : 'Ampliar')}</Boton>
          </div>
          {recorta && (
            <p className="small text-body-secondary m-0 w-100">
              Pide tu PIN. Las semanas después del nuevo fin quedan fuera del plazo: no admiten registros y no se borran; si luego amplías, vuelven.
            </p>
          )}
        </div>
      )}
      {error && <p role="alert" className="small text-danger fw-semibold">{error}</p>}
      {historial && historial.length > 0 && (
        <ul className="m-0 small text-body-secondary ps-3" >
          {historial.map((h, i) => (
            <li key={i}>
              {f(h.fechaFinAnterior)} → <strong>{f(h.fechaFinNueva)}</strong>{h.fechaFinNueva < h.fechaFinAnterior ? ' (recorte)' : ''} · {h.motivo} · {h.autor}, {f(h.fecha.slice(0, 10))}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** Fase 2 (25/11): en vez de «eliminar», la obra se CIERRA (regla 10: nada se borra). Es definitivo: la base no
    deja reabrirla, así que se confirma escribiendo su nombre. */
function CerrarObra({ obra, enListo }: { obra: Obra; enListo: () => void }) {
  const [abierto, setAbierto] = useState(false)
  const [nombre, setNombre] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const avisar = useAvisos()
  const coincide = nombre.trim().toLowerCase() === obra.nombre.trim().toLowerCase()
  const cerrar = async () => {
    setGuardando(true); setError('')
    try {
      await obrasHttp.cerrar(obra.id)
      setAbierto(false)
      avisar(`La obra «${obra.nombre}» quedó cerrada: sus datos se conservan y se siguen consultando.`)
      window.dispatchEvent(new Event('sigro-obras'))
      enListo()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cerrar la obra.')
    } finally { setGuardando(false) }
  }
  return (
    <>
      <Boton tipo="peligroSuave" enClic={() => { setNombre(''); setError(''); setAbierto(true) }}>Cerrar obra</Boton>
      {abierto && (
        <Modal titulo="Cerrar la obra" enCerrar={() => setAbierto(false)}>
          <div className="d-flex flex-column gap-3">
            <p className="small mb-0">
              <strong>{obra.nombre}</strong> pasará a <strong>cerrada</strong>. No se borra nada: compras, movimientos,
              tareo y reportes se conservan y se siguen consultando. Pero <strong>no se puede reabrir</strong> ni ampliarle el plazo.
            </p>
            <Campo id="cerrar-nombre" etiqueta="Escribe el nombre de la obra para confirmar" valor={nombre} enCambio={setNombre}
              marcador={obra.nombre} error={error || undefined} valido={coincide} />
            <div className="d-flex gap-2 justify-content-end">
              <Boton tipo="secundario" enClic={() => setAbierto(false)}>Cancelar</Boton>
              <Boton tipo="peligro" enClic={cerrar} deshabilitado={!coincide || guardando}>{guardando ? 'Cerrando…' : 'Cerrar definitivamente'}</Boton>
            </div>
          </div>
        </Modal>
      )}
    </>
  )
}

function AvancePlazo({ obra, cerradas }: { obra: Obra; cerradas: number }) {
  const a = avancePlazo(obra.inicio, obra.fin, hoyEnLima())
  const semanasFaltan = obra.semanas.filter((s) => s.estado === 'por_venir').length
  const conSemanas = obra.semanas.length > 0
  const datos: [string, string][] = [
    ['del plazo transcurrido', `${a.pct.toLocaleString('es-PE')} %`],
    ...(conSemanas ? [['semanas cerradas', `${cerradas} de ${obra.semanas.length}`] as [string, string]] : []),
    ['días que faltan', a.faltan.toLocaleString('es-PE')],
    ...(conSemanas ? [['semanas por venir', String(semanasFaltan)] as [string, string]] : [['días de plazo', a.total.toLocaleString('es-PE')] as [string, string]]),
  ]
  return (
    <div className="mt-3 d-flex flex-column gap-2">
      <div className="sigro-avance-plazo" role="progressbar" aria-label="Avance del plazo de la obra" aria-valuemin={0} aria-valuemax={100} aria-valuenow={a.pct}
        aria-valuetext={`${a.pct} % del plazo: ${a.transcurridos} de ${a.total} días`}>
        <span style={{ width: `${a.pct}%` }} />
      </div>
      <div className="sigro-cifras">
        {datos.map(([k, v]) => <div key={k}><strong className="n">{v}</strong><span>{k}</span></div>)}
      </div>
    </div>
  )
}

function FichaObra({ obra, enCambio }: { obra: Obra; enCambio: () => void }) {
  const cerradas = obra.semanas.filter((s) => s.estado === 'cerrada').length
  const enCurso = obra.semanas.find((s) => s.estado === 'en_curso')
  const ultima = obra.semanas[obra.semanas.length - 1]
  return (
    <Tarjeta>
      <div className="d-flex justify-content-between align-items-baseline gap-3 flex-wrap">
        <div>
          <span className="fw-bold">{obra.nombre}</span>
          <span className="n small text-body-secondary ms-2" >
            {obra.presupuesto ? `presupuesto ${obra.presupuesto} · ` : ''}{f(obra.inicio)} – {f(obra.fin)}
          </span>
        </div>
        <div className="d-flex gap-2 align-items-center flex-wrap">
          {obra.estado === 'cerrada' ? <Chip tono="neutro">cerrada · solo consulta</Chip> : <Chip tono="bien">en ejecución</Chip>}
          {enCurso && <Chip tono="neutro">semana {enCurso.numero} de {obra.semanas.length}</Chip>}
          {usaApiReal && obra.estado !== 'cerrada' && puedo('proyecto.cerrar') && <CerrarObra obra={obra} enListo={enCambio} />}
        </div>
      </div>
      <AvancePlazo obra={obra} cerradas={cerradas} />
      <div className="mt-3">
        <TiraSemanas semanas={obra.semanas} />
      </div>
      <div className="d-flex gap-3 mt-3 small text-body-secondary flex-wrap">
        <span>■ cerradas: {cerradas} (no admiten movimientos nuevos)</span>
        <span className="fw-semibold text-primary" >■ en curso</span>
        <span>□ por venir</span>
        {ultima && ultima.dias < 7 && <span>la última dura {ultima.dias} día{ultima.dias === 1 ? '' : 's'}: el plazo no calza en semanas exactas y el sistema no lo redondea</span>}
      </div>
      {usaApiReal && obra.estado !== 'cerrada' && <AmpliarPlazo obra={obra} enListo={enCambio} />}
    </Tarjeta>
  )
}

export function Obras() {
  const [obras, setObras] = useState<Obra[] | null>(null)
  const avisar = useAvisos()
  // React Hook Form + Zod (esquemaObra): nombre y «fin después del inicio» (el presupuesto S10 viene del PDF)
  const { register, handleSubmit, reset, setError, control, trigger, watch, formState: { errors, dirtyFields, isSubmitting } } = useForm<DatosObra>({
    // validación en vivo: revisa al escribir y al salir; un error nuevo espera la pausa y lo corregido se quita ya
    mode: 'all', delayError: PAUSA_MS,
    resolver: zodResolver(esquemaObra),
    defaultValues: { nombre: '', presupuesto: '', inicio: '', fin: '' },
  })

  const cargar = () => api.listarObras().then(setObras)
  useEffect(() => {
    cargar()
  }, [])

  const crear = async (d: DatosObra) => {
    try {
      await api.crearObra(d)
      reset()
      avisar(`Obra «${d.nombre}» creada en ejecución, con sus semanas de control generadas.`)
      cargar()
      // avisa a la barra lateral: la obra nueva aparece en el selector de obra activa
      window.dispatchEvent(new Event('sigro-obras'))
    } catch (e) {
      // lo que el servidor rechaza (nombre repetido, fechas fuera de regla) se marca en su campo
      if (e instanceof ErrorApi) setError(e.codigo === 'nombre' ? 'nombre' : 'fin', { message: e.message })
    }
  }

  return (
    <div className="d-flex flex-column gap-3 pb-4">
      <Titulo sub="La obra acota todo: ningún movimiento puede registrarse fuera de su plazo, y sus semanas de control se generan solas al crearla.">
        Crear la obra
      </Titulo>

      <Tarjeta>
        {/* Row/Col: la grilla de Bootstrap acomoda los campos en una fila (escritorio) o apilados (celular) */}
        <Form noValidate onSubmit={handleSubmit(crear)}>
          <Row className="g-3 align-items-start">
            <Form.Group as={Col} md={12} lg={5} controlId="o-nombre">
              <Form.Label className="small fw-semibold">Nombre de la obra</Form.Label>
              <Form.Control {...register('nombre')} placeholder="p. ej. Multifamiliar Los Cedros" isInvalid={!!errors.nombre} isValid={!!dirtyFields.nombre && !errors.nombre} />
              <Form.Control.Feedback type="invalid">{errors.nombre?.message}</Form.Control.Feedback>
            </Form.Group>
            {/* fase 3: selector de fecha propio (dd/mm/aaaa, calendario en español); el valor sigue siendo ISO */}
            <Col xs={12} sm={6} lg={3}>
              <label htmlFor="o-inicio" className="form-label small fw-semibold">Inicio</label>
              <Controller name="inicio" control={control} render={({ field }) => (
                <Fecha id="o-inicio" valor={field.value} alCambiar={(x) => { field.onChange(x); void trigger('fin') }} enSalir={field.onBlur}
                  invalido={!!errors.inicio} valido={!!dirtyFields.inicio && !errors.inicio} describedBy={errors.inicio ? 'o-inicio-error' : undefined} />
              )} />
              {errors.inicio && <div id="o-inicio-error" className="invalid-feedback d-block">{errors.inicio.message}</div>}
            </Col>
            <Col xs={12} sm={6} lg={3}>
              <label htmlFor="o-fin" className="form-label small fw-semibold">Fin</label>
              <Controller name="fin" control={control} render={({ field }) => (
                <Fecha id="o-fin" valor={field.value} min={watch('inicio') || undefined} alCambiar={field.onChange} enSalir={field.onBlur}
                  invalido={!!errors.fin} valido={!!dirtyFields.fin && !errors.fin} describedBy={errors.fin ? 'o-fin-error' : undefined} />
              )} />
              {errors.fin && <div id="o-fin-error" className="invalid-feedback d-block">{errors.fin.message}</div>}
            </Col>
            <Col xs={12} lg={1} className="d-grid pt-lg-4">
              <Button type="submit" disabled={isSubmitting} className="mt-lg-2 text-nowrap">{isSubmitting ? 'Creando…' : 'Crear'}</Button>
            </Col>
          </Row>
        </Form>
        <p className="mt-3 small text-body-secondary">
          Al crearla, la obra nace <strong>en ejecución</strong> y sus periodos semanales
          quedan generados del inicio al fin (la última semana puede ser más corta). El código del presupuesto
          del S10 no se escribe aquí: viene en el PDF de la Relación de insumos y se lee al importarlo.
          El paso siguiente es <strong>importar su Relación de insumos del S10</strong>.
        </p>
      </Tarjeta>

      {obras === null ? (
        <Tarjeta><EsqueletoTabla /></Tarjeta>
      ) : (
        obras.map((o) => <FichaObra key={o.id} obra={o} enCambio={cargar} />)
      )}
    </div>
  )
}
