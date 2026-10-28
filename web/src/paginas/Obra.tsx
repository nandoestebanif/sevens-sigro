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
import { Boton, Campo, Chip, Tarjeta, Titulo, Vacio } from '../ui/base'

const f = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`

const COLOR_SEMANA: Record<Semana['estado'], { fondo: string; borde: string }> = {
  cerrada: { fondo: 'var(--pista)', borde: 'var(--borde)' },
  en_curso: { fondo: 'var(--marca)', borde: 'var(--marca)' },
  por_venir: { fondo: 'var(--tarjeta)', borde: 'var(--borde)' },
}

/** las semanas como tira de casillas: se LEE el avance del plazo de un vistazo */
function TiraSemanas({ semanas }: { semanas: Semana[] }) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
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

function FichaObra({ obra }: { obra: Obra }) {
  const cerradas = obra.semanas.filter((s) => s.estado === 'cerrada').length
  const enCurso = obra.semanas.find((s) => s.estado === 'en_curso')
  const ultima = obra.semanas[obra.semanas.length - 1]
  return (
    <Tarjeta>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <span style={{ fontSize: 15, fontWeight: 700 }}>{obra.nombre}</span>
          <span className="n" style={{ fontSize: 12.5, color: 'var(--tinta-3)', marginLeft: 10 }}>
            {obra.presupuesto ? `presupuesto ${obra.presupuesto} · ` : ''}{f(obra.inicio)} – {f(obra.fin)}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Chip tono="bien">en ejecución</Chip>
          {enCurso && <Chip tono="neutro">semana {enCurso.numero} de {obra.semanas.length}</Chip>}
        </div>
      </div>
      <div style={{ marginTop: 14 }}>
        <TiraSemanas semanas={obra.semanas} />
      </div>
      <div style={{ display: 'flex', gap: 16, marginTop: 12, fontSize: 11.5, color: 'var(--tinta-3)', flexWrap: 'wrap' }}>
        <span>■ cerradas: {cerradas} (no admiten movimientos nuevos)</span>
        <span style={{ color: 'var(--marca)', fontWeight: 600 }}>■ en curso</span>
        <span>□ por venir</span>
        {ultima.dias < 7 && <span>la última dura {ultima.dias} día{ultima.dias === 1 ? '' : 's'}: el plazo no calza en semanas exactas y el sistema no lo redondea</span>}
      </div>
    </Tarjeta>
  )
}

export function Obras() {
  const [obras, setObras] = useState<Obra[] | null>(null)
  const [nombre, setNombre] = useState('')
  const [presupuesto, setPresupuesto] = useState('')
  const [inicio, setInicio] = useState('')
  const [fin, setFin] = useState('')
  const [error, setError] = useState<{ campo: string; texto: string } | null>(null)
  const [guardando, setGuardando] = useState(false)

  const cargar = () => api.listarObras().then(setObras)
  useEffect(() => {
    cargar()
  }, [])

  const crear = async () => {
    setGuardando(true)
    setError(null)
    try {
      await api.crearObra({ nombre, presupuesto, inicio, fin })
      setNombre(''); setPresupuesto(''); setInicio(''); setFin('')
      cargar()
      // avisa a la barra lateral: la obra nueva aparece en el selector de obra activa
      window.dispatchEvent(new Event('sigro-obras'))
    } catch (e) {
      if (e instanceof ErrorApi) {
        setError({ campo: ['nombre'].includes(e.codigo) ? 'nombre' : 'fechas', texto: e.message })
      }
    } finally {
      setGuardando(false)
    }
  }

  const campoFecha = (id: string, rotulo: string, valor: string, cambiar: (v: string) => void) => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, flex: '0 1 160px', minWidth: 0 }}>
      <label htmlFor={id} style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--tinta-2)' }}>{rotulo}</label>
      <input
        id={id} type="date" value={valor} onChange={(e) => cambiar(e.target.value)}
        style={{
          height: 42, width: '100%', border: `1px solid ${error?.campo === 'fechas' ? 'var(--grave)' : 'var(--borde)'}`,
          borderRadius: 'var(--radio-chico)', padding: '0 12px', fontSize: 14,
          fontFamily: 'inherit', background: 'var(--tarjeta)', color: 'var(--tinta)',
        }}
      />
    </div>
  )

  return (
    <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
      <Titulo sub="La obra acota todo: ningún movimiento puede registrarse fuera de su plazo, y sus semanas de control se generan solas al crearla.">
        Crear la obra
      </Titulo>

      <Tarjeta>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-end' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, flex: '2 1 220px', minWidth: 0 }}>
            <Campo id="o-nombre" etiqueta="Nombre de la obra" valor={nombre} enCambio={setNombre} marcador="p. ej. Multifamiliar Los Cedros" error={error?.campo === 'nombre' ? error.texto : undefined} />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, flex: '1 1 160px', minWidth: 0 }}>
            <Campo id="o-pres" etiqueta="Presupuesto S10 (opcional)" valor={presupuesto} enCambio={setPresupuesto} marcador="2701006-002" />
          </div>
          {campoFecha('o-inicio', 'Inicio', inicio, setInicio)}
          {campoFecha('o-fin', 'Fin', fin, setFin)}
          <div style={{ flex: 'none' }}>
            <Boton enClic={crear} deshabilitado={guardando}>
              {guardando ? 'Creando…' : 'Crear la obra'}
            </Boton>
          </div>
        </div>
        {error?.campo === 'fechas' && (
          <p role="alert" style={{ marginTop: 10, fontSize: 13, color: 'var(--grave)', fontWeight: 600 }}>{error.texto}</p>
        )}
        <p style={{ marginTop: 12, fontSize: 12.5, color: 'var(--tinta-3)', lineHeight: 1.5 }}>
          Al crearla, la obra nace <strong>en ejecución</strong> y sus periodos semanales
          quedan generados del inicio al fin (la última semana puede ser más corta).
          El paso siguiente es <strong>importar su Relación de insumos del S10</strong>.
        </p>
      </Tarjeta>

      {obras === null ? (
        <Tarjeta><Vacio titulo="Cargando…" detalle="Consultando las obras de la empresa." /></Tarjeta>
      ) : (
        obras.map((o) => <FichaObra key={o.id} obra={o} />)
      )}
    </div>
  )
}
