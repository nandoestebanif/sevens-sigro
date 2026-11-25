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
import { Permisos } from './administracion/Permisos'
import { rolActual } from '../api/permisos'
import { esAdministrador } from './administracion/reglas'
import { EsqueletoTabla } from '../ui/Esqueleto'

const PESTANAS = [
  { clave: 'celulares', texto: 'Celulares' },
  { clave: 'usuarios', texto: 'Usuarios' },
  { clave: 'asignaciones', texto: 'Asignaciones' },
  { clave: 'almacenes', texto: 'Almacenes' },
  { clave: 'permisos', texto: 'Permisos' },
] as const
type Pestana = (typeof PESTANAS)[number]['clave']

export function Administracion({ obra }: { obra: { id: string; nombre: string } | null }) {
  const { seccion } = useParams()
  const ir = useNavigate()
  const activa: Pestana = PESTANAS.some((p) => p.clave === seccion) ? (seccion as Pestana) : 'celulares'
  const sesion = sesionActual()

  if (!usaApiReal) {
    return (
      <div className="d-flex flex-column gap-3 pb-4"><Tarjeta>
        <Vacio titulo="La administración necesita el servidor" detalle="Configura VITE_API_URL e ingresa con tu usuario: aquí no se muestran datos de ejemplo." />
      </Tarjeta></div>
    )
  }
  if (!obra) return <div className="d-flex flex-column gap-3 pb-4" role="status"><EsqueletoTabla /></div>
  if (!esAdministrador(sesion, obra.id)) {
    return (
      <div className="d-flex flex-column gap-3 pb-4"><Tarjeta>
        <Vacio titulo="Solo para el administrador" detalle={`No eres administrador de ${obra.nombre}. Si deberías serlo, pídeselo al administrador de la obra.`} />
      </Tarjeta></div>
    )
  }

  return (
    <div className="d-flex flex-column gap-3 pb-4">
      <Titulo sub={`${obra.nombre} · cada cambio queda con tu nombre y la fecha; nada se borra.`}>Administración</Titulo>
      <div role="tablist" aria-label="Secciones de administración" className="d-flex gap-2 flex-wrap">
        {PESTANAS.map((p) => {
          const sel = p.clave === activa
          return (
            <button className={`btn btn-sm rounded-pill ${sel ? 'btn-primary' : 'btn-outline-secondary'}`}
              key={p.clave}
              type="button"
              role="tab"
              id={`pestana-${p.clave}`}
              aria-selected={sel}
              aria-controls="panel-administracion"
              onClick={() => ir(`/administracion/${p.clave}`)}

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
        {activa === 'permisos' && <Permisos miRol={rolActual(obra.id)} />}
      </div>
    </div>
  )
}
