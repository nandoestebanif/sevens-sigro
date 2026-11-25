/* ─────────────────────────────────────────────────────────────────────────────
   Trabajadores desde la web (fase 2 del rediseño, 25/11 · HU-62)

   - Registrar: DNI con «Buscar en RENIEC» que llena nombres y apellidos tal como
     están en el padrón; categoría, cuadrilla y fecha de ingreso.
   - Corregir: nombres, apellidos, categoría y cuadrilla. Documento e ingreso no
     (los protege la base: SIGRO-T5).
   - Dar de baja: una fecha de salida; nada se borra (regla 10).
   - Detalle: sus datos y sus horas por semana.
   Validación mientras se escribe (formularios/enVivo); el servidor vuelve a verificar.
   ───────────────────────────────────────────────────────────────────────── */
import { useState } from 'react'
import { Form } from 'react-bootstrap'
import { ErrorApi } from '../api/error'
import { manoDeObra, type Categoria, type HorasFila, type Trabajador } from '../api/manoDeObra'
import { useEnVivo } from '../formularios/enVivo'
import { Boton, Campo, Chip, Modal } from '../ui/base'
import { PanelDetalle } from '../ui/PanelDetalle'
import { CATEGORIAS, categoria, errorBaja, horas, validarTrabajador, type DatosTrabajador } from './mano-de-obra-reglas'

const dia = (iso: string | null | undefined) => (iso ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}` : '—')
const hoyLima = () => new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' })
type Campo = keyof DatosTrabajador

const textoError = (x: unknown) => {
  if (!(x instanceof ErrorApi)) return 'No se pudo guardar. Inténtalo de nuevo.'
  if (x.codigo === 'uq_trabajador_proyecto_id_documento') return 'Ese documento ya está registrado en esta obra.'
  if (x.codigo === 'sin_permiso') return 'Registran y corrigen trabajadores el almacenero, el SSOMA y el administrador.'
  return x.message
}

/** Registrar uno nuevo (`trabajador` vacío) o corregir uno existente. */
export function FormTrabajador({ trabajador, enCerrar, enGuardado }: {
  trabajador?: Trabajador
  enCerrar: () => void
  enGuardado: (t: Trabajador) => void
}) {
  const corregir = !!trabajador
  const [d, setD] = useState<DatosTrabajador>(() => ({
    nombres: trabajador?.nombres ?? (trabajador && !trabajador.apellidoPaterno ? trabajador.nombre : ''),
    apellidoPaterno: trabajador?.apellidoPaterno ?? '', apellidoMaterno: trabajador?.apellidoMaterno ?? '',
    tipoDocumento: (trabajador?.tipoDocumento as DatosTrabajador['tipoDocumento']) ?? 'DNI', documento: trabajador?.documento ?? '',
    categoria: trabajador?.categoria ?? '', cuadrilla: trabajador?.cuadrilla ?? '', fechaIngreso: trabajador?.fechaIngreso ?? hoyLima(),
  }))
  const [general, setGeneral] = useState<string | null>(null)
  const [reniec, setReniec] = useState<string | null>(null)
  const [buscando, setBuscando] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const v = useEnVivo<Campo>(validarTrabajador(d, corregir))
  const poner = (k: Campo) => (x: string) => { setD((o) => ({ ...o, [k]: x })); v.cambio(k); setGeneral(null) }

  const buscarDni = async () => {
    setBuscando(true); setReniec(null)
    try {
      const r = await manoDeObra.consultarDni(d.documento)
      setD((o) => ({ ...o, nombres: r.nombres, apellidoPaterno: r.apellidoPaterno, apellidoMaterno: r.apellidoMaterno }))
      ;(['nombres', 'apellidoPaterno'] as Campo[]).forEach((k) => v.salir(k))
      setReniec(`RENIEC: ${r.nombres} ${r.apellidoPaterno} ${r.apellidoMaterno}`.trim())
    } catch (x) {
      setReniec(x instanceof ErrorApi && x.codigo === 'reniec_sin_respuesta' ? 'RENIEC no respondió: escribe los nombres a mano.' : textoError(x))
    } finally { setBuscando(false) }
  }

  const guardar = async () => {
    if (v.alGuardar()) return
    setOcupado(true); setGeneral(null)
    try {
      const t = corregir
        ? await manoDeObra.corregir(trabajador!.id, { ...d, categoria: d.categoria as Categoria })
        : await manoDeObra.registrar({ ...d, categoria: d.categoria as Categoria })
      enGuardado(t)
    } catch (x) { setGeneral(textoError(x)); setOcupado(false) }
  }

  const dniListo = d.tipoDocumento === 'DNI' && /^\d{8}$/.test(d.documento.trim())
  return (
    <Modal titulo={corregir ? `Corregir a ${trabajador!.nombre}` : 'Registrar trabajador'} enCerrar={enCerrar}>
      <form className="d-flex flex-column gap-3" onSubmit={(e) => { e.preventDefault(); void guardar() }}>
        {corregir ? (
          <div className="small text-body-secondary">{trabajador!.tipoDocumento} {trabajador!.documento} · ingresó el {dia(trabajador!.fechaIngreso)}.
            El documento y la fecha de ingreso no se corrigen.</div>
        ) : (
          <div className="d-flex gap-2 align-items-end flex-wrap">
            <Form.Group controlId="trab-tipo" style={{ width: 130 }}>
              <Form.Label className="small fw-semibold">Documento</Form.Label>
              <Form.Select value={d.tipoDocumento} onChange={(e) => poner('tipoDocumento')(e.target.value)}>
                <option value="DNI">DNI</option><option value="CE">C. extranjería</option><option value="PASAPORTE">Pasaporte</option>
              </Form.Select>
            </Form.Group>
            <div className="flex-grow-1" style={{ minWidth: 150 }}>
              <Campo id="trab-doc" etiqueta="Número" valor={d.documento} modoTeclado={d.tipoDocumento === 'DNI' ? 'numeric' : 'text'}
                enCambio={(x) => { poner('documento')(x); setReniec(null) }} {...v.campo('documento')} />
            </div>
            {d.tipoDocumento === 'DNI' && (
              <Boton tipo="secundario" enClic={buscarDni} deshabilitado={!dniListo || buscando}>{buscando ? 'Buscando…' : 'Buscar en RENIEC'}</Boton>
            )}
          </div>
        )}
        {reniec && <span role="status" className="small text-body-secondary">{reniec}</span>}
        <Campo id="trab-nombres" etiqueta="Nombres" valor={d.nombres} enCambio={poner('nombres')} {...v.campo('nombres')} />
        <div className="d-flex gap-3 flex-wrap">
          <div className="flex-grow-1" style={{ minWidth: 160 }}><Campo id="trab-ap" etiqueta="Apellido paterno" valor={d.apellidoPaterno} enCambio={poner('apellidoPaterno')} {...v.campo('apellidoPaterno')} /></div>
          <div className="flex-grow-1" style={{ minWidth: 160 }}><Campo id="trab-am" etiqueta="Apellido materno (si tiene)" valor={d.apellidoMaterno} enCambio={poner('apellidoMaterno')} {...v.campo('apellidoMaterno')} /></div>
        </div>
        <div className="d-flex gap-3 flex-wrap">
          <Form.Group controlId="trab-cat" style={{ minWidth: 160 }}>
            <Form.Label className="small fw-semibold">Categoría</Form.Label>
            <Form.Select value={d.categoria} onChange={(e) => { poner('categoria')(e.target.value); v.salir('categoria') }}
              isInvalid={!!v.campo('categoria').error} isValid={v.campo('categoria').valido}>
              <option value="">Elige…</option>
              {Object.entries(CATEGORIAS).map(([k, t]) => <option key={k} value={k}>{t}</option>)}
            </Form.Select>
            <Form.Control.Feedback type="invalid">{v.campo('categoria').error}</Form.Control.Feedback>
          </Form.Group>
          <div className="flex-grow-1" style={{ minWidth: 160 }}><Campo id="trab-cuad" etiqueta="Cuadrilla" marcador="p. ej. Cuadrilla de concreto" valor={d.cuadrilla} enCambio={poner('cuadrilla')} {...v.campo('cuadrilla')} /></div>
          {!corregir && (
            <Form.Group controlId="trab-ingreso" style={{ minWidth: 160 }}>
              <Form.Label className="small fw-semibold">Fecha de ingreso</Form.Label>
              <Form.Control type="date" value={d.fechaIngreso} onChange={(e) => { poner('fechaIngreso')(e.target.value); v.salir('fechaIngreso') }}
                isInvalid={!!v.campo('fechaIngreso').error} />
              <Form.Control.Feedback type="invalid">{v.campo('fechaIngreso').error}</Form.Control.Feedback>
            </Form.Group>
          )}
        </div>
        {general && <span role="alert" className="small text-danger">{general}</span>}
        <div className="d-flex gap-2 justify-content-end">
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton enClic={guardar} deshabilitado={ocupado}>{ocupado ? 'Guardando…' : corregir ? 'Guardar cambios' : 'Registrar trabajador'}</Boton>
        </div>
      </form>
    </Modal>
  )
}

export function BajaTrabajador({ trabajador, enCerrar, enGuardado }: { trabajador: Trabajador; enCerrar: () => void; enGuardado: (t: Trabajador) => void }) {
  const [fecha, setFecha] = useState(hoyLima())
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const guardar = async () => {
    const e = errorBaja(trabajador, fecha)
    if (e) { setError(e); return }
    setOcupado(true)
    try { enGuardado(await manoDeObra.darDeBaja(trabajador.id, fecha)) } catch (x) { setError(textoError(x)); setOcupado(false) }
  }
  return (
    <Modal titulo={`Dar de baja a ${trabajador.nombre}`} enCerrar={enCerrar}>
      <div className="d-flex flex-column gap-3">
        <span className="small">No se borra nada: queda «de baja» desde esa fecha y sus jornadas se conservan. La baja no se cambia después.</span>
        <Form.Group controlId="baja-fecha">
          <Form.Label className="small fw-semibold">Último día en la obra</Form.Label>
          <Form.Control type="date" value={fecha} min={trabajador.fechaIngreso} onChange={(e) => { setFecha(e.target.value); setError(null) }} isInvalid={!!error} />
          <Form.Control.Feedback type="invalid">{error}</Form.Control.Feedback>
        </Form.Group>
        <div className="d-flex gap-2 justify-content-end">
          <Boton tipo="secundario" enClic={enCerrar}>Cancelar</Boton>
          <Boton tipo="peligro" enClic={guardar} deshabilitado={ocupado}>{ocupado ? 'Guardando…' : 'Dar de baja'}</Boton>
        </div>
      </div>
    </Modal>
  )
}

export function DetalleTrabajador({ trabajador: t, horasDe, activo, enCerrar, pie }: {
  trabajador: Trabajador; horasDe: HorasFila[]; activo: boolean; enCerrar: () => void; pie?: React.ReactNode
}) {
  const propias = horasDe.filter((h) => h.trabajadorId === t.id).sort((a, b) => b.periodo - a.periodo)
  const total = propias.reduce((s, h) => s + h.horasNormales, 0), extra = propias.reduce((s, h) => s + h.horasExtra, 0)
  return (
    <PanelDetalle
      titulo={t.nombre} subtitulo={`${t.tipoDocumento} ${t.documento}`}
      chips={<>{activo ? <Chip tono="bien">Activo</Chip> : <Chip tono="neutro">De baja desde {dia(t.fechaSalida)}</Chip>}<Chip tono="marca">{categoria(t.categoria)}</Chip></>}
      secciones={[
        { titulo: 'Datos', datos: [
          // los registrados antes de la 0020 solo tienen el nombre completo: «Corregir datos» lo separa
          ...(t.nombres ? [['Nombres', t.nombres], ['Apellidos', [t.apellidoPaterno, t.apellidoMaterno].filter(Boolean).join(' ') || '—']] as [string, string][] : [['Nombre completo', t.nombre]] as [string, string][]),
          ['Cuadrilla', t.cuadrilla], ['Categoría', categoria(t.categoria)], ['Ingreso', dia(t.fechaIngreso)], ['Salida', dia(t.fechaSalida)],
        ] },
        { titulo: 'Horas en la obra', datos: [['Normales', horas(total)], ['Extra', horas(extra)], ['Semanas con jornadas', String(propias.length)], ['Última jornada', dia(propias[0]?.ultimaJornada)]] },
      ]}
      enCerrar={enCerrar} pie={pie}
    >
      {propias.length > 0 && (
        <section>
          <h3 className="sigro-detalle-seccion">Por semana</h3>
          <table className="table table-sm align-middle mb-0">
            <thead><tr><th>Semana</th><th className="n">Días</th><th className="n">Normales</th><th className="n">Extra</th></tr></thead>
            <tbody>{propias.slice(0, 12).map((h) => (
              <tr key={h.periodo}><td>S{h.periodo}</td><td className="n">{h.diasPresente}</td><td className="n">{horas(h.horasNormales)}</td><td className="n">{h.horasExtra ? horas(h.horasExtra) : '—'}</td></tr>
            ))}</tbody>
          </table>
        </section>
      )}
    </PanelDetalle>
  )
}
