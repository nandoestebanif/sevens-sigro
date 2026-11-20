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
import { useEffect, useState } from 'react'
import { ErrorApi, api } from '../api/cliente'
import type { Obra, Semana } from '../api/tipos'
import { Boton, Campo, Chip, Tarjeta, Titulo } from '../ui/base'
import { usaApiReal } from '../api/http'
import { plazo, type CambioPlazo } from '../api/oficina'
import { EsqueletoTabla } from '../ui/Esqueleto'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Button, Col, Form, Row } from 'react-bootstrap'
import { esquemaObra, type DatosObra } from '../formularios/esquemas'
import { useAvisos } from '../ui/Avisos'

const f = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`

const COLOR_SEMANA: Record<Semana['estado'], { fondo: string; borde: string }> = {
  cerrada: { fondo: 'var(--pista)', borde: 'var(--borde)' },
  en_curso: { fondo: 'var(--marca)', borde: 'var(--marca)' },
  por_venir: { fondo: 'var(--tarjeta)', borde: 'var(--borde)' },
}

/** las semanas como tira de casillas: se LEE el avance del plazo de un vistazo */
function TiraSemanas({ semanas }: { semanas: Semana[] }) {
  return (
    <div className="d-flex flex-wrap gap-1">
      {semanas.map((s) => (
        <span
          key={s.numero}
          title={`Semana ${s.numero} · ${f(s.inicio)} – ${f(s.fin)} · ${s.dias} día${s.dias === 1 ? '' : 's'} · ${s.estado === 'cerrada' ? 'cerrada: no admite movimientos nuevos' : s.estado === 'en_curso' ? 'en curso' : 'por venir'}`}
          style={{
            width: 16, height: 16, borderRadius: 4,
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

/* ── HU-02 · T-331: ampliar el plazo; las semanas nuevas se generan solas y las anteriores no se tocan ── */
function AmpliarPlazo({ obra, enListo }: { obra: Obra; enListo: () => void }) {
  const [abierto, setAbierto] = useState(false)
  const [fecha, setFecha] = useState('')
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [historial, setHistorial] = useState<CambioPlazo[] | null>(null)
  useEffect(() => { plazo.historial(obra.id).then(setHistorial).catch(() => setHistorial([])) }, [obra.id, obra.fin])

  const ampliar = async () => {
    setError('')
    if (!fecha || fecha <= obra.fin) { setError(`Elige una fecha posterior al fin actual (${f(obra.fin)}).`); return }
    if (motivo.trim().length < 3) { setError('Escribe el motivo de la ampliación: queda en el historial.'); return }
    setGuardando(true)
    try { await plazo.ampliar(obra.id, fecha, motivo); setAbierto(false); setFecha(''); setMotivo(''); enListo() }
    catch (e) { setError(e instanceof ErrorApi ? e.message : 'No se pudo ampliar el plazo.') }
    finally { setGuardando(false) }
  }

  return (
    <div className="mt-3 pt-3 d-flex flex-column gap-2 border-top" >
      {!abierto ? (
        <div className="d-flex align-items-center justify-content-between gap-2 flex-wrap">
          <span className="small text-body-secondary">
            ¿Se aprobó una ampliación? El plazo no se pisa: queda el anterior, el nuevo, el motivo y quién lo cambió.
          </span>
          <Boton tipo="secundario" enClic={() => setAbierto(true)}>Ampliar plazo</Boton>
        </div>
      ) : (
        <div className="d-flex flex-wrap gap-3 align-items-end">
          <div className="d-flex flex-column gap-2" style={{ flex: '0 1 170px' }}>
            <label htmlFor={`amp-${obra.id}`} className="small fw-semibold text-body-secondary">Nuevo fin</label>
            <input id={`amp-${obra.id}`} type="date" min={obra.fin} value={fecha} onChange={(e) => setFecha(e.target.value)}
              className="form-control" />
          </div>
          <div className="d-flex flex-column gap-2 min-w-0"  style={{ flex: '2 1 240px' }}>
            <Campo id={`amp-m-${obra.id}`} etiqueta="Motivo de la ampliación" valor={motivo} enCambio={setMotivo} marcador="p. ej. adenda aprobada por el cliente" />
          </div>
          <div className="d-flex gap-2">
            <Boton tipo="secundario" enClic={() => { setAbierto(false); setError('') }}>Cancelar</Boton>
            <Boton enClic={ampliar} deshabilitado={guardando}>{guardando ? 'Ampliando…' : 'Ampliar'}</Boton>
          </div>
        </div>
      )}
      {error && <p role="alert" className="small text-danger fw-semibold">{error}</p>}
      {historial && historial.length > 0 && (
        <ul className="m-0 small text-body-secondary ps-3" >
          {historial.map((h, i) => (
            <li key={i}>
              {f(h.fechaFinAnterior)} → <strong>{f(h.fechaFinNueva)}</strong> · {h.motivo} · {h.autor}, {f(h.fecha.slice(0, 10))}
            </li>
          ))}
        </ul>
      )}
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
        <div className="d-flex gap-2">
          <Chip tono="bien">en ejecución</Chip>
          {enCurso && <Chip tono="neutro">semana {enCurso.numero} de {obra.semanas.length}</Chip>}
        </div>
      </div>
      <div className="mt-3">
        <TiraSemanas semanas={obra.semanas} />
      </div>
      <div className="d-flex gap-3 mt-3 small text-body-secondary flex-wrap">
        <span>■ cerradas: {cerradas} (no admiten movimientos nuevos)</span>
        <span className="fw-semibold text-primary" >■ en curso</span>
        <span>□ por venir</span>
        {ultima.dias < 7 && <span>la última dura {ultima.dias} día{ultima.dias === 1 ? '' : 's'}: el plazo no calza en semanas exactas y el sistema no lo redondea</span>}
      </div>
      {usaApiReal && <AmpliarPlazo obra={obra} enListo={enCambio} />}
    </Tarjeta>
  )
}

export function Obras() {
  const [obras, setObras] = useState<Obra[] | null>(null)
  const avisar = useAvisos()
  // React Hook Form + Zod (esquemaObra): nombre, formato del presupuesto y «fin después del inicio»
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm<DatosObra>({
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
            <Form.Group as={Col} md={12} lg={4} controlId="o-nombre">
              <Form.Label className="small fw-semibold">Nombre de la obra</Form.Label>
              <Form.Control {...register('nombre')} placeholder="p. ej. Multifamiliar Los Cedros" isInvalid={!!errors.nombre} />
              <Form.Control.Feedback type="invalid">{errors.nombre?.message}</Form.Control.Feedback>
            </Form.Group>
            <Form.Group as={Col} sm={6} lg={3} controlId="o-pres">
              <Form.Label className="small fw-semibold">Presupuesto S10 (opcional)</Form.Label>
              <Form.Control {...register('presupuesto')} placeholder="2701006-002" isInvalid={!!errors.presupuesto} />
              <Form.Control.Feedback type="invalid">{errors.presupuesto?.message}</Form.Control.Feedback>
            </Form.Group>
            <Form.Group as={Col} xs={6} sm={3} lg={2} controlId="o-inicio">
              <Form.Label className="small fw-semibold">Inicio</Form.Label>
              <Form.Control type="date" {...register('inicio')} isInvalid={!!errors.inicio} />
              <Form.Control.Feedback type="invalid">{errors.inicio?.message}</Form.Control.Feedback>
            </Form.Group>
            <Form.Group as={Col} xs={6} sm={3} lg={2} controlId="o-fin">
              <Form.Label className="small fw-semibold">Fin</Form.Label>
              <Form.Control type="date" {...register('fin')} isInvalid={!!errors.fin} />
              <Form.Control.Feedback type="invalid">{errors.fin?.message}</Form.Control.Feedback>
            </Form.Group>
            <Col xs={12} lg={1} className="d-grid pt-lg-4">
              <Button type="submit" disabled={isSubmitting} className="mt-lg-2 text-nowrap">{isSubmitting ? 'Creando…' : 'Crear'}</Button>
            </Col>
          </Row>
        </Form>
        <p className="mt-3 small text-body-secondary">
          Al crearla, la obra nace <strong>en ejecución</strong> y sus periodos semanales
          quedan generados del inicio al fin (la última semana puede ser más corta).
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
