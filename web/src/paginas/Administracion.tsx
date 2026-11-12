/* ─────────────────────────────────────────────────────────────────────────────
   SIGRO · Administración de la obra activa
   HU-15 celulares · HU-13 usuarios · HU-14 asignaciones · almacenes.

   Solo el administrador de la obra activa ve esta sección en el menú. Es
   comodidad: cada petición la verifica la API (regla 9) y, si no corresponde,
   responde 403 y aquí se dice en lenguaje claro.
   Sin API real (VITE_API_URL) no hay nada que administrar: no se inventan datos.
   ───────────────────────────────────────────────────────────────────────── */
import { useParams, useNavigate } from 'react-router-dom'
import { sesionActual, usaApiReal } from '../api/http'
import { Tarjeta, Titulo, Vacio } from '../ui/base'
import { Almacenes } from './administracion/Almacenes'
import { Asignaciones } from './administracion/Asignaciones'
import { Celulares } from './administracion/Celulares'
import { Usuarios } from './administracion/Usuarios'
import { esAdministrador } from './administracion/reglas'

const PESTANAS = [
  { clave: 'celulares', texto: 'Celulares' },
  { clave: 'usuarios', texto: 'Usuarios' },
  { clave: 'asignaciones', texto: 'Asignaciones' },
  { clave: 'almacenes', texto: 'Almacenes' },
] as const
type Pestana = (typeof PESTANAS)[number]['clave']

export function Administracion({ obra }: { obra: { id: string; nombre: string } | null }) {
  const { seccion } = useParams()
  const ir = useNavigate()
  const activa: Pestana = PESTANAS.some((p) => p.clave === seccion) ? (seccion as Pestana) : 'celulares'
  const sesion = sesionActual()

  if (!usaApiReal) {
    return (
      <div className="pagina"><Tarjeta>
        <Vacio titulo="La administración necesita el servidor" detalle="Configura VITE_API_URL e ingresa con tu usuario: aquí no se muestran datos de ejemplo." />
      </Tarjeta></div>
    )
  }
  if (!obra) return <div className="pagina" role="status"><Vacio titulo="Cargando…" detalle="Consultando la obra activa." /></div>
  if (!esAdministrador(sesion, obra.id)) {
    return (
      <div className="pagina"><Tarjeta>
        <Vacio titulo="Solo para el administrador" detalle={`No eres administrador de ${obra.nombre}. Si deberías serlo, pídeselo al administrador de la obra.`} />
      </Tarjeta></div>
    )
  }

  return (
    <div className="pagina" style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
      <Titulo sub={`${obra.nombre} · todo cambio queda registrado con tu nombre y la fecha; nada se borra`}>Administración</Titulo>
      <div role="tablist" aria-label="Secciones de administración" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {PESTANAS.map((p) => {
          const sel = p.clave === activa
          return (
            <button
              key={p.clave}
              type="button"
              role="tab"
              id={`pestana-${p.clave}`}
              aria-selected={sel}
              aria-controls="panel-administracion"
              onClick={() => ir(`/administracion/${p.clave}`)}
              style={{
                height: 38, padding: '0 16px', borderRadius: 'var(--radio-pildora)', fontSize: 13.5, fontWeight: 600, cursor: 'pointer',
                fontFamily: 'inherit', border: `1px solid ${sel ? 'var(--accion)' : 'var(--borde)'}`,
                background: sel ? 'var(--marca-tinte)' : 'var(--tarjeta)', color: sel ? 'var(--marca-tinte-tinta)' : 'var(--tinta-2)',
              }}
            >
              {p.texto}
            </button>
          )
        })}
      </div>
      <div role="tabpanel" id="panel-administracion" aria-labelledby={`pestana-${activa}`}>
        {activa === 'celulares' && <Celulares obraId={obra.id} />}
        {activa === 'usuarios' && <Usuarios usuarioSesionId={sesion?.usuario.id ?? null} />}
        {activa === 'asignaciones' && <Asignaciones obraId={obra.id} obraNombre={obra.nombre} />}
        {activa === 'almacenes' && <Almacenes obraId={obra.id} obraNombre={obra.nombre} />}
      </div>
    </div>
  )
}
